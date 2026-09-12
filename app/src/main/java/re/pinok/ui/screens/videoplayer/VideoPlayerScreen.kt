// File: ui/screens/videoplayer/VideoPlayerScreen.kt
package re.pinok.ui.screens.videoplayer

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
// Fix #140 (2026-08-03): WindowInsetsControllerCompat — замена deprecated
// systemUiVisibility / FLAG_FULLSCREEN. На API 30+ (R) и особенно на API 35+
// (Android 15, где enableEdgeToEdge обязателен) — systemUiVisibility игнорируется.
// WindowInsetsControllerCompat работает на всех API и корректно скрывает
// status bar + navigation bar в fullscreen-режиме видео-плеера.
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
// W30-2 #VIDEO-ADD-TO-MINE: «Добавить» (data-testid video_page_add_to_my_playlist)
// — AddCircle до добавления, CheckCircle после (базовые иконки).
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
// W30-2 #VIDEO-MORE-MENU: иконка «Убрать из закладки»/«В закладки» меню «Ещё».
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
// W30-2 #VIDEO-MORE-MENU: иконки пунктов меню (удалить копию / жалоба).
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
// Fix #383 #COMMUNITY-VIDEO-PARITY: иконка копирования ссылки в шторке «Поделиться».
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
// Fix #383 #COMMUNITY-VIDEO-PARITY: иконки действий (комментарии/поделиться).
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Lock
// W33-b: OpenInNew deprecated (иконка зеркалится для RTL) — AutoMirrored-версия.
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BrightnessMedium
// W30-2 #VIDEO-MORE-MENU: контурная закладка (пункт «Убрать из закладки»)
// и «Ещё» (VK more_horizontal_24 — ближайшая Material-иконка MoreHoriz,
// material-icons-extended есть в зависимостях :app).
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.MoreHoriz
// W30-2 #VIDEO-REPORT: confirm-диалог «Пожаловаться» — уровень экрана
// (урок #PIN-DIALOG-OVERLAY: оверлеи не внутри скролл-контейнеров).
import androidx.compose.material3.AlertDialog
// W30-2 #VIDEO-MORE-MENU: DropdownMenu меню «Ещё» (video_page_more_button).
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
// #COMMUNITY-VIDEO-PARITY: дубль импорта ExperimentalMaterial3Api (строки 77 и 90,
// остался от мержа волны 28) — Kotlin K2 даёт "Conflicting import … is ambiguous".
// Оставлен единственный импорт на строке 77.
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import android.net.Uri
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.OkVideoRepository
import re.pinok.data.model.Comment
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.Video
import re.pinok.data.model.VideoPlatform
import re.pinok.data.model.VideoQuality
import re.pinok.data.model.UserProfile
import re.pinok.media.PlayerConnection
import re.pinok.media.VideoDownloadManager
// W30-2 #VIDEO-BACKGROUND (контракт §2.4 плана W30): хуки контроллера фон-режима
// (создаёт W30-3: MediaSessionService + MediaStyle-уведомление без видео).
import re.pinok.service.VideoPlaybackBus
import re.pinok.util.AppLog
import re.pinok.util.HevcSupport
import re.pinok.util.VkUserAgent
import androidx.compose.material.icons.outlined.VerifiedUser

// ── AnimatedVisibility wrapper — обходит ColumnScope ambiguity ──
@Composable
private fun VKOverlayVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = EnterTransition.None,
    exit: ExitTransition = ExitTransition.None,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

// ── Quality options ────────────────────────────────────────────────────
private data class QualityOption(
    val key: String,
    val label: String,
    val url: String,
)

/**
 * VIDEO-FIX (#351): проверяет, является ли URL HTML-страницей (embed/iframe),
 * а не прямым медиа-потоком. ExoPlayer не умеет играть HTML — падает с
 * `UnrecognizedInputFormatException: None of the available extractors could
 * read the stream` (см. лог ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED).
 *
 * Раньше фильтровался только VK `video_ext.php`, но OK embed URL
 * `ok.ru/videoembed/...` и YouTube `youtube.com/embed/...` проходят старый
 * фильтр и попадают в ExoPlayer → краш. Этот helper блокирует ВСЕ известные
 * HTML embed URL, чтобы ExoPlayer не создавался с HTML-страницей.
 *
 * URL, который проходит эту проверку — считается прямым медиа-URL (.mp4/.m3u8/.mpd).
 */
private fun isHtmlEmbedUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("video_ext.php") ||        // VK player HTML
            lower.contains("ok.ru/videoembed") ||    // OK embed HTML
            lower.contains("ok.ru/video/") ||        // OK video page HTML
            lower.contains("youtube.com/embed") ||   // YouTube embed HTML
            lower.contains("youtu.be/") ||           // YouTube short link HTML
            lower.contains("/videoembed/") ||        // generic embed path
            lower.contains("/embed/") ||             // generic iframe embed
            lower.contains("player.vimeo.com") ||    // Vimeo embed
            lower.contains("rutube.ru/play/embed") || // Rutube embed
            lower.contains("dzen.ru/embed") ||       // Dzen embed
            lower.contains("/iframe")                // generic iframe marker
}

/**
 * W30-STABILITY #QUALITY-PIN: пин ABR под выбранное пользователем качество.
 *
 * На адаптивных источниках (HLS «Авто», codec-fallback на HLS) ExoPlayer сам
 * прыгает по вариантам битрейта/высоты — на длинных сессиях это «скачки
 * качества». Ручной выбор конкретного качества (mp4_XXX) пинит трек-селекцию
 * через setMaxVideoSize(∞, высота). Высота честно выводится из ключа VK:
 * VideoQuality.ORDER кодирует высоту в суффиксе (mp4_720 → 720). Ключ без
 * числового суффикса ("hls" = «Авто») или null — пин СНИМАЕТСЯ (дефолтные
 * ограничения ∞×∞), т.к. «Авто» по определению адаптивный выбор.
 *
 * Не влияет на codec-fallback логику: пин фильтрует только rendition-ы
 * адаптивного потока, прогрессивные mp4-источники (один вариант) играют как раньше.
 */
private fun applyQualityPin(player: ExoPlayer, qualityKey: String?) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (qualityKey != null) {
        val height = qualityKey.substringAfter("_").toIntOrNull()
        if (height != null) {
            // Выбрано конкретное качество — не даём ABR уходить от него.
            builder.setMaxVideoSize(Int.MAX_VALUE, height)
        } else {
            // «Авто»/HLS — адаптивный поток, пин снят.
            builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        }
    } else {
        // Нет ключа (пустой список качеств) — пин снят.
        builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
    }
    player.trackSelectionParameters = builder.build()
}

private val PLAYBACK_RATES = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

// #VIDEO-QUALITY-TEMPLATE: порядок/метки качеств и логика выбора индекса вынесены
// в единый [re.pinok.data.model.VideoQuality] (§52.2.4, «общие паттерны») —
// раньше здесь были private QUALITY_ORDER + computeInitialQualityIndex, которые
// дублировали inline-список в Video.playUrlForQuality.

// ── VK Colors ─────────────────────────────────────────────────────────
private val VK_BLACK = Color(0xFF000000)
private val VK_WHITE = Color(0xFFFFFFFF)
// #VIDEO-TEXT-GRAY: цвет ТЕКСТА на панели управления видео (time display,
// метки seek/quality/brightness). Раньше был VK_WHITE — слишком ярко на тёмном
// видео. Серый читается мягче и не утомляет глаза. Иконки остаются VK_WHITE
// (нужен контраст на тёмном фоне).
private val VK_CONTROL_TEXT = Color(0xFFB8B8BC)
private val VK_RED = Color(0xFFFF3347)
private val VK_TEXT_SECONDARY = Color(0xFFA8A8AA)
private val VK_SETTINGS_BG = Color(0xB8000000) // rgba(0,0,0,.72)
private val VK_SETTINGS_HOVER = Color(0x14FFFFFF) // hsla(0,0%,100%,.08)
private val VK_NOTIFICATION_BG = Color(0xFF2C2D2E)
private val VK_GREEN = Color(0xFF4CAF50)
private val VK_SLIDER_BG = Color(0x66FFFFFF) // hsla(0,0%,100%,.4)

/**
 * Полноэкранный видеоплеер — VK Video стиль.
 *
 * FIX: useController=false — убран дублирующий Media3 контроллер,
 * который накладывался на кастомные кнопки (баг "два плеера").
 * Теперь все контролы рисуются Compose-оверлеем поверх PlayerView.
 *
 * VK VP классы из VK_VP_API.MD:
 *   vk-vp-root → VideoPlayerScreen (Scaffold)
 *   player-wrapper → Box(fillMaxSize)
 *   video-container → AndroidView(PlayerView)
 *   wrapper-bottom → VKControlsOverlay
 *   controls → VKControlsBar (Row, 40dp)
 *   controls-left → VKControlsLeft
 *   controls-right → VKControlsRight
 *   timeline → VKTimeline (custom slider)
 *   settings-menu → VKSettingsPopup
 *   thumb-timer → VKThumbTimer (PiP)
 *   notification → VKSlowNotification
 *   double-forward-label → VKSeekIndicator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun VideoPlayerScreen(
    video: Video,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()

    val downloads by VideoDownloadManager.downloads.collectAsState()
    val videoKey = VideoDownloadManager.videoKey(video.ownerId, video.id)
    val downloadState = downloads[videoKey]
    val isDownloaded = downloadState?.isCompleted == true

    // ── Player state ───────────────────────────────────────────────
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }
    var resolvedVideo by remember(video) { mutableStateOf(video) }
    var isLoadingVideo by remember(video) { mutableStateOf(false) }
    var fetchError by remember(video) { mutableStateOf<String?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    // OK-IMPL-1 (Stage 3b): метаданные OK-видео, извлечённые через OkVideoRepository.
    // null если видео не OK или метаданные не получены (fallback на WebView / video.get).
    var okMetadata by remember(video) { mutableStateOf<OkVideoRepository.OkVideoMetadata?>(null) }
    // P2 #VIDEO-SESSION-HOLD: true когда video.get вернул null при НЕвалидном
    // токене (error 5/1117) — показываем inline «Перезайти» вместо мёртвого экрана.
    var sessionExpired by remember { mutableStateOf(false) }

    // ── Fix #383 #COMMUNITY-VIDEO-PARITY ──
    // Состояние лайка поднято на уровень экрана: ОДИН источник правды для
    // портретного action-row и фуллскрин-стрип (раньше состояние жило внутри
    // VideoActionBar — при появлении второго места действий пришлось бы
    // дублировать). Ключ — resolvedVideo: после videoGetById-обновления
    // состояние честно синхронизируется со свежими likes из API.
    var videoLiked by remember(resolvedVideo) { mutableStateOf(resolvedVideo.isLiked) }
    var videoLikeCount by remember(resolvedVideo) { mutableStateOf(resolvedVideo.likesCount) }
    // Шторки действий — доступны и в портрете, и в fullscreen/landscape.
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    // ── W30-2 #VIDEO-ADD-TO-MINE / #VIDEO-MORE-MENU (паритет футера VK web) ──
    // Мой userId — паттерн #AUDIO-TOGGLE-OWNING (AudioPlayerScreen :553):
    // exchangeAuthRepository.userId(), 0 = идентификатор не получен.
    val myUserId = app.exchangeAuthRepository.userId()
    // Состояние «Добавить себе» (video_page_add_to_my_playlist). Начальное
    // значение честное: видео, владелец которого — я сам, уже у меня.
    var addedToMine by remember(resolvedVideo.id) {
        mutableStateOf(myUserId != 0L && resolvedVideo.ownerId == myUserId)
    }
    // «Ещё» → «В закладки»: is_favorite из video.get (extended) — честный
    // начальный стейт, VK возвращает признак закладки вместе с видео.
    var inBookmarks by remember(resolvedVideo.id) {
        mutableStateOf(resolvedVideo.isFavorite == 1)
    }
    // «Ещё» → «Пожаловаться»: confirm-диалог живёт на уровне экрана
    // (урок #PIN-DIALOG-OVERLAY — оверлеи не внутри скролл-контейнеров).
    var showReportConfirm by remember { mutableStateOf(false) }

    // P2 #VIDEO-SESSION-HOLD: видео — долгая сессия. Превентивно освежаем токен
    // при входе (как ChatDetailScreen) и поддерживаем rolling suppress-окно, чтобы
    // тик инвалидации во время просмотра НЕ перекрывал плеер окном авторизации —
    // silent refresh (Path 1.5/5) отработает в фоне, а video.get retry подхватит.
    LaunchedEffect(Unit) {
        try {
            app.exchangeAuthRepository.keepAlive()
        } catch (e: Exception) {
            AppLog.w(TAG, "keepAlive at video start failed: ${e.message}")
        }
        while (isActive) {
            app.suppressAuthRelaunchFor(60_000L)
            delay(45_000L)
        }
    }

    // Gesture state (swipe brightness/volume)
    var gestureType by remember { mutableStateOf<String?>(null) } // "brightness" | "volume" | null
    var brightnessLevel by remember { mutableFloatStateOf(-1f) } // -1 = not set (use system)
    var gestureValue by remember { mutableFloatStateOf(0f) } // 0..1 for overlay indicator

    // Controls visibility (VK: tap to toggle, auto-hide 3s)
    var controlsVisible by remember { mutableStateOf(true) }
    var hasStarted by remember { mutableStateOf(false) }

    // Fullscreen state
    var isFullscreen by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // #VIDEO-INSETS: immersive = fullscreen ИЛИ landscape. В landscape телефон
    // сам перевернулся — видео тоже должно занять весь экран без системных панелей,
    // иначе «разворачивается под системные панели».
    val immersive = isFullscreen || isLandscape

    // Rotation lock (fullscreen only)
    var rotationLocked by remember { mutableStateOf(false) }

    // Settings menu
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsSubmenu by remember { mutableStateOf<String?>(null) } // "quality" | "speed" | null
    var playbackRate by remember { mutableFloatStateOf(1f) }

    // Seek indicator (double-tap)
    var seekLabel by remember { mutableStateOf<String?>(null) }

    // Timeline
    var currentPositionMs by remember { mutableFloatStateOf(0f) }
    var bufferedPositionMs by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }

    // ── Fallback: video.get ────────────────────────────────────────
    // FIX: player URL от VK — это HTML-страница (video_ext.php), а не прямой .mp4.
    // ExoPlayer не может воспроизвести HTML. Поэтому video.get вызывается ВСЕГДА,
    // когда нет прямых файлов (files), даже если player URL присутствует.
    //
    // VIDEO-FIX (#351): для OK-видео fetchMetadata вызывается ВСЕГДА, даже если
    // `files` не пустой. VK возвращает OK-crossposted видео с `files`, где лежит
    // EMBED URL (ok.ru/videoembed/...) как placeholder — это НЕ прямой медиа-URL.
    // Раньше ранний выход `if (hasFiles) return` пропускал fetchMetadata →
    // okMetadata оставался null → ExoPlayer создавался с embed URL →
    // ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED. Теперь OK-видео идёт в
    // fetchMetadata независимо от hasFiles.
    // VIDEO-FIX (#353): LaunchedEffect(resolvedVideo) вместо LaunchedEffect(video).
    // Раньше эффект зависел от оригинального `video`, поэтому после video.get
    // (когда fresh получает platform=OK и externalId из player URL) эффект НЕ
    // перезапускался → fetchMetadata не вызывался → OK-видео не играло в разделах
    // где VK API не вернул player URL в исходном ответе (feed, notifications, etc.).
    // Теперь эффект перезапускается при обновлении resolvedVideo → если fresh стал
    // OK, fetchMetadata вызывается автоматически.
    LaunchedEffect(resolvedVideo) {
        val localFile = VideoDownloadManager.getLocalFile(resolvedVideo.ownerId, resolvedVideo.id)
        val files = resolvedVideo.files
        val hasFiles = files?.isNotEmpty() == true
        // VIDEO-FIX (#351): OK-видео игнорирует hasFiles — VK files для OK это placeholder
        // (VK кладёт embed URL ok.ru/videoembed/... в files, это НЕ прямой медиа-URL).
        // isOkVideo использует явный `extId != null` (не isNullOrBlank) — это даёт
        // compiler smart-cast extId к String внутри `if (isOkVideo)`, без
        // non-null assertion (по #NULL-EXPLICIT она запрещена).
        val extId = resolvedVideo.externalId
        val isOkVideo = extId != null && extId.isNotBlank() &&
                resolvedVideo.videoPlatform == VideoPlatform.OK
        if (localFile != null || (hasFiles && !isOkVideo)) return@LaunchedEffect

        // OK-IMPL-1 (Stage 3b): OK-crossposted видео — пробуем нативный ExoPlayer
        // через парсинг OK метаданных. Если удалось — qualityOptions строятся
        // из metadata.videos[] (без JS/Adman → без рекламы by design).
        // См. OK_PLAYER_REVERSE.md §3213-3305 (Ad SDK analysis), §3150-3160 (metadata).
        // VIDEO-FIX (#351): isOkVideo уже гарантирует extId != null && isNotBlank()
        // (явная проверка выше) — явные проверки в if убраны, smart cast делает
        // extId non-null здесь. fetchMetadata(extId) принимает String.
        // VIDEO-FIX (#353): для OK-видео НЕ делаем video.get fallback после fetchMetadata
        // — это предотвращает зацикливание (resolvedVideo обновляется → эффект
        // перезапускается → снова video.get). Если fetchMetadata упал — показываем
        // ошибку, без повторного video.get.
        if (isOkVideo) {
            if (hasFiles) {
                // files smart-cast к Map<String,String> (non-null) внутри if(hasFiles),
                // т.к. hasFiles = files?.isNotEmpty() == true → files != null.
                AppLog.d(TAG, "OK video has files (keys=${files.keys}) — treating as embed placeholder, calling fetchMetadata")
            }
            AppLog.i(TAG, "OK video — пробуем OkVideoRepository.fetchMetadata(movieId=$extId)")
            isLoadingVideo = true
            fetchError = null
            try {
                val meta = OkVideoRepository.fetchMetadata(extId)
                if (meta != null && (meta.videos.isNotEmpty() || !meta.hlsManifestUrl.isNullOrBlank())) {
                    okMetadata = meta
                    AppLog.i(TAG, "OK metadata: ${meta.videos.size} qualities, hls=${meta.hlsManifestUrl != null}, showAd=${meta.showAd}")
                } else {
                    AppLog.w(TAG, "OK metadata пуста — WebView fallback недоступен, показываем ошибку")
                    fetchError = "OK-видео недоступно, откройте в браузере"
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "OkVideoRepository упал: ${e.message}")
                fetchError = "OK-видео недоступно, откройте в браузере"
            } finally {
                isLoadingVideo = false
            }
            // VIDEO-FIX (#353): return для OK-видео — не делаем video.get fallback.
            // Если fetchMetadata упал, повторный video.get не поможет (VK для OK
            // возвращает только embed URL, не прямые ссылки).
            return@LaunchedEffect
        }

        // UNKNOWN/VK path: нет files → video.get для получения прямых URL.
        // VIDEO-FIX (#353): если fresh получает platform=OK (после withDetectedPlatform),
        // resolvedVideo обновляется → LaunchedEffect перезапускается → OK path выше
        // вызовет fetchMetadata. Защита от зацикливания: обновляем resolvedVideo
        // только если fresh имеет files ИЛИ player (иначе — ошибка, без перезапуска).
        AppLog.i(TAG, "files==null, пробуем video.get для video #${resolvedVideo.id} (platform=${resolvedVideo.videoPlatform})")
        isLoadingVideo = true
        fetchError = null
        sessionExpired = false
        try {
            val fresh = app.apiClient.videoGetById(resolvedVideo.ownerId, resolvedVideo.id, resolvedVideo.accessKey)
            if (fresh != null && (fresh.files?.isNotEmpty() == true || !fresh.player.isNullOrBlank())) {
                AppLog.i(TAG, "video.get вернул видео: files=${fresh.files?.keys}, platform=${fresh.videoPlatform}, externalId=${fresh.externalId}, player=${fresh.player?.take(60)}")
                resolvedVideo = fresh  // перезапускает LaunchedEffect(resolvedVideo)
            } else {
                fetchError = "Видео недоступно (нет прямых ссылок)"
                // P2 #VIDEO-SESSION-HOLD: null + невалидный токен = сессия истекла
                // (error 5/1117). Показываем inline «Перезайти», а не глобальный popup.
                sessionExpired = !app.tokenStorage.hasValidToken()
            }
        } catch (e: Exception) {
            fetchError = "Ошибка загрузки: ${e.message}"
            sessionExpired = !app.tokenStorage.hasValidToken()
            AppLog.e(TAG, "video.get fallback ошибка", e)
        } finally {
            isLoadingVideo = false
        }
    }

    // ── Quality options & URL resolution ────────────────────────────
    val localFile = remember(resolvedVideo) {
        VideoDownloadManager.getLocalFile(resolvedVideo.ownerId, resolvedVideo.id)
    }
    val isLocalPlayback = localFile != null

    val qualityOptions = remember(resolvedVideo, okMetadata) {
        // OK-IMPL-1 (Stage 3b): если есть OK metadata — qualityOptions строятся
        // из metadata.videos[] (прогрессивные MP4 от OK CDN). Ключи OK
        // (mobile/lowest/low/sd/hd/full/quad/ultra) маппятся в VK-style
        // (mp4_144/mp4_240/.../mp4_2160) через OkVideoRepository.okKeyToVkKey,
        // чтобы переиспользовать существующий UI (QUALITY_ORDER, VKSettingsPopup).
        val meta = okMetadata
        if (meta != null && meta.videos.isNotEmpty()) {
            val okByVkKey = meta.videos.associateBy { OkVideoRepository.okKeyToVkKey(it.key) }
            val allOptions = VideoQuality.ORDER.mapNotNull { (key, label) ->
                // NULLSAFE-1: replaced okByVkKey[key]?.let { QualityOption(...) } with smart cast
                val q = okByVkKey[key]
                if (q != null) QualityOption(key, label, q.url) else null
            }
            // Fix #341: OK's full/quad/ultra (1080p/1440p/2160p) — обычно HEVC.
            // Фильтруем аналогично VK's mp4_1080/1440/2160.
            val filtered = if (HevcSupport.isSupported()) {
                allOptions
            } else {
                allOptions.filter { it.key !in HevcSupport.HEVC_LIKELY_KEYS }
            }
            if (filtered.isEmpty()) allOptions else filtered
        } else {
            val files = resolvedVideo.files
            if (files == null) {
                emptyList()
            } else {
                val allOptions = VideoQuality.ORDER.mapNotNull { (key, label) ->
                    val url = files[key]
                    if (url != null) QualityOption(key, label, url) else null
                }
                // Fix #341: если устройство не поддерживает HEVC — отфильтровываем
                // HEVC-likely качества (mp4_2160/1440/1080) ДО создания ExoPlayer.
                // Раньше на устройствах без HEVC (MediaTek) каждое длинное видео
                // падало с DECODING_FAILED → Fix #338 fallback (1-2 сек чёрного экрана
                // + «Кодек не поддерживается»). Теперь ExoPlayer стартует сразу с AVC.
                // Fallback #338 остаётся как страховка (VK может сменить кодек).
                val filtered = if (HevcSupport.isSupported()) {
                    allOptions
                } else {
                    allOptions.filter { it.key !in HevcSupport.HEVC_LIKELY_KEYS }
                }
                // Edge case: если после фильтрации пусто (видео имеет только HEVC mp4
                // и нет HLS) — возвращаем исходный список. Пусть DECODING_FAILED
                // fallback (#338) попытается, лучше чем "No video URL — ExoPlayer
                // not created". Если есть HLS — ExoPlayer создастся с HLS (строка
                // ниже по коду: qualityOptions.getOrNull(idx)?.url ?: hls_ondemand).
                if (filtered.isEmpty()) allOptions else filtered
            }
        }
    }

    // Fix #336: читаем preferredQuality СИНХРОННО из кэша SovaApp.prefsSnapshot
    // (раньше был async produceState — ExoPlayer создавался с firstOrNull()=max
    // качества ДО того, как pref загружался, и игнорировал выбор пользователя).
    // remember(resolvedVideo) перечитывает pref при смене видео. Cold-start
    // fallback "auto" = максимальное доступное (прежнее поведение).
    val preferredQuality = remember(resolvedVideo) {
        app.prefsSnapshot?.videoPreferredQuality ?: "auto"
    }
    // #VIDEO-AUTOPLAY: читаем синхронно из prefsSnapshot. Default true.
    // При false: ExoPlayer создаётся с playWhenReady=false и LifecycleStartEffect
    // не форсирует play — пользователь жмёт play сам.
    val autoplayEnabled = remember(resolvedVideo) {
        app.prefsSnapshot?.videoAutoplay ?: true
    }

    // Fix #334/#336: начальный индекс = лучшее доступное качество ≤ preferred.
    // Ключ ТОЛЬКО resolvedVideo — ручной выбор пользователя не сбрасывается при
    // доезжании pref (преf теперь синхронный, гонки нет).
    var selectedQualityIndex by remember(resolvedVideo) {
        mutableIntStateOf(VideoQuality.selectIndex(qualityOptions.map { it.key }, preferredQuality))
    }
    val showQualitySelector = qualityOptions.size >= 2 && !isLocalPlayback

    // Fix #337: после DECODING_FAILED fallback selectedQualityIndex оставался на
    // упавшем (HEVC) качестве → меню качества подсвечивало нерабочий пункт, а
    // повторный выбор HEVC-качества снова падал и откатывался к fallback
    // (зацикливание = "невозможно выбрать качество"). Решение:
    //  - failedQualities: mp4-ключи, упавшие с DECODING_FAILED (HEVC не поддерживается
    //    устройством) — блокируем их повторный выбор в меню.
    //  - hlsOption: настоящий HLS (m3u8) как отдельный выбираемый пункт "Авто".
    //  - selectedHls: выбран/играет ли сейчас HLS — синхронизируется после fallback.
    var failedQualities by remember(resolvedVideo) { mutableStateOf(emptySet<String>()) }
    val hlsUrl = remember(resolvedVideo, okMetadata) {
        // OK-IMPL-1 (Stage 3b): OK HLS — это metadata.hlsManifestUrl (signed URL
        // на ok8-8.vkuser.net, 1-6 часов живёт). ExoPlayer играет его через
        // DefaultHttpDataSource с VK UA + Referer.
        val metaHls = okMetadata
        if (metaHls != null) {
            // NULLSAFE-1: replaced metaHls.hlsManifestUrl?.takeIf { ... } with explicit null check
            val hls = metaHls.hlsManifestUrl
            if (hls != null && hls.contains("m3u8", ignoreCase = true)) hls else null
        } else {
            val files = resolvedVideo.files
            if (files == null) null
            else {
                // NULLSAFE-1: replaced firstNotNullOfOrNull { ... }?.takeIf { ... } with explicit null check
                val hlsCandidate = listOf("hls_ondemand", "hls").firstNotNullOfOrNull { files[it] }
                if (hlsCandidate != null && hlsCandidate.contains("m3u8", ignoreCase = true)) hlsCandidate else null
            }
        }
    }
    val hlsOption = hlsUrl?.let { QualityOption("hls", "Авто", it) }
    var selectedHls by remember(resolvedVideo) { mutableStateOf(false) }

    // FIX: player URL от VK — это HTML-страница (video_ext.php), а не прямой видеофайл.
    // ExoPlayer не может воспроизвести HTML. Используем player URL ТОЛЬКО как
    // абсолютный last-resort, и только если нет прямой .mp4/.m3u8 ссылки.
    // Пока isLoadingVideo=true (ждём video.get), НЕ используем player URL вообще.
    //
    // VIDEO-FIX (#351): расширенный фильтр [isHtmlEmbedUrl] — блокирует ВСЕ
    // HTML embed URL (VK video_ext.php, OK ok.ru/videoembed, YouTube embed,
    // generic /embed/), а не только VK. Раньше OK embed URL проходил фильтр
    // → ExoPlayer крашился на HTML-странице.
    val playerUrlDirect = resolvedVideo.player?.takeIf { !isHtmlEmbedUrl(it) }
    // NULLSAFE-1: извлекаем OK HLS URL заранее через явный null-check (вместо
    // `okMetadata?.hlsManifestUrl?.takeIf { it.isNotBlank() }` в цепочке `?:`).
    // Используется в currentQualityUrl и exoPlayer (ниже).
    val okHlsForFallback: String? = run {
        val okMeta = okMetadata
        if (okMeta != null) {
            val hls = okMeta.hlsManifestUrl
            if (hls != null && hls.isNotBlank()) hls else null
        } else null
    }
    val currentQualityUrl: String? = if (isLocalPlayback) {
        remember(localFile) { "file://${localFile.absolutePath}" }
    } else if (isLoadingVideo) {
        // Не создаём плеер пока ждём video.get с прямыми URL
        null
    } else {
        qualityOptions.getOrNull(selectedQualityIndex)?.url
            // OK-IMPL-1 (Stage 3b): fallback на OK HLS если нет прямых mp4.
            ?: okHlsForFallback
            ?: run {
                val files = resolvedVideo.files
                if (files != null) listOf("hls_ondemand", "hls", "dash_ondemand", "dash", "dash_sep").firstNotNullOfOrNull { files[it] } else null
            }
            ?: playerUrlDirect
    }

    var isSwitchingQuality by remember { mutableStateOf(false) }

    LaunchedEffect(video) { retryCount = 0 }

    fun retryWithFreshUrl() {
        if (retryCount >= 3) {
            playerError = "Не удалось воспроизвести видео после 3 попыток"
            return
        }
        retryCount++
        playerError = null
        isLoadingVideo = true
        fetchError = null
        sessionExpired = false
        scope.launch {
            try {
                val fresh = app.apiClient.videoGetById(
                    resolvedVideo.ownerId, resolvedVideo.id, resolvedVideo.accessKey
                )
                if (fresh != null && (fresh.files?.isNotEmpty() == true || !fresh.player.isNullOrBlank())) {
                    resolvedVideo = fresh
                } else {
                    playerError = "Видео недоступно (нет прямых ссылок)"
                    sessionExpired = !app.tokenStorage.hasValidToken()
                }
            } catch (e: Exception) {
                playerError = "Ошибка: ${e.message}"
                sessionExpired = !app.tokenStorage.hasValidToken()
                AppLog.e(TAG, "Retry video.get ошибка", e)
            } finally {
                isLoadingVideo = false
            }
        }
    }

    // ── ExoPlayer ──────────────────────────────────────────────────
    // Fix #336: создаём плеер с URL из selectedQualityIndex (учитывает pref
    // пользователя), а НЕ с firstOrNull() (всегда максимальное качество).
    // selectedQualityIndex синхронно вычислен выше из prefsSnapshot, поэтому
    // к моменту remember(resolvedVideo) индекс уже корректен. Ручное переключение
    // качества идёт через switchQuality() (setMediaItem), а не через пересоздание
    // плеера — поэтому ключ только resolvedVideo.
    val exoPlayer = remember(resolvedVideo, okMetadata) {
        // #PIP-PAUSE-ON-NEW-VIDEO: открываем новое видео — приостанавливаем
        // активный PiP-плеер, чтобы не шли два потока одновременно.
        re.pinok.ui.videoplayer.VideoPipActivity.pauseActivePip()
        val url = if (isLocalPlayback) {
            "file://${localFile.absolutePath}"
        } else {
            qualityOptions.getOrNull(selectedQualityIndex)?.url
                // OK-IMPL-1 (Stage 3b): OK HLS как fallback если нет прямых mp4.
                ?: okHlsForFallback
                ?: run {
                    val files = resolvedVideo.files
                    if (files != null) listOf("hls_ondemand", "hls", "dash_ondemand", "dash", "dash_sep").firstNotNullOfOrNull { files[it] } else null
                }
                // VIDEO-FIX (#351): расширенный фильтр [isHtmlEmbedUrl] — не даём
                // ExoPlayer'у HTML-страницу (OK/YouTube/VK embed). Если прямых
                // медиа-URL нет — ExoPlayer не создаётся (null), UI покажет
                // fallback «Открыть в браузере» для OK-видео.
                ?: resolvedVideo.player?.takeIf { !isHtmlEmbedUrl(it) }
        }
        if (url == null) {
            AppLog.w(TAG, "No video URL — ExoPlayer not created (platform=${resolvedVideo.videoPlatform}, player=${resolvedVideo.player?.take(60)})")
            null
        } else {
            val uri = Uri.parse(url)
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            if (url.contains("m3u8", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            val vkUa = VkUserAgent.get(context.applicationContext as android.app.Application)
            // #37: DefaultHttpDataSource.Factory() умеет ТОЛЬКО http:// и https://.
            // Для локальных file:// URI (скачанные видео) нужен DefaultDataSource.Factory
            // — он делегирует FileDataSource для file://, ContentDataSource для content://
            // и DefaultHttpDataSource для http(s)://. Без этого скачанные видео падали с
            // "FileURLConnection cannot be cast to java.net.HttpURLConnection".
            // OK-IMPL-1 (Stage 3b): OK CDN (ok8-8.vkuser.net) требует Referer: https://m.vk.com/
            // — иначе 403. VK CDN (vk.ru) Referer игнорирует, но не вредит.
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(vkUa)
                .setDefaultRequestProperties(mapOf("Referer" to "https://m.vk.com/"))
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build().apply {
                    val self = this // non-null ref for lambdas inside apply
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .setUsage(C.USAGE_MEDIA)
                            .build(),
                        true
                    )
                    setMediaItem(mediaItemBuilder.build())
                    prepare()
                    // #VIDEO-AUTOPLAY: по умолчанию true (автостарт при открытии).
                    // При false — плеер готов, но ждёт нажатия play.
                    playWhenReady = autoplayEnabled
                    // W30-STABILITY: (пере)создание плеера — reapplied сохранённый
                    // rate. remember(resolvedVideo, okMetadata) пересоздаёт ExoPlayer
                    // после video.get-обновления/прихода OK-метаданных, и без этого
                    // скорость сбрасывалась в 1.0 («скачки скорости»).
                    setPlaybackSpeed(playbackRate)
                    // W30-STABILITY #QUALITY-PIN: начальное качество выбрано по pref
                    // пользователя — пиним ABR к его высоте (явный if по #NULL-ЯВНО).
                    val initOption = qualityOptions.getOrNull(selectedQualityIndex)
                    if (initOption != null) {
                        applyQualityPin(this, initOption.key)
                    } else {
                        applyQualityPin(this, null)
                    }
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            AppLog.e(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
                            val isDecodingFailed = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                            if (retryCount >= 5) {
                                playerError = "Не удалось воспроизвести видео (кодек не поддерживается)"
                            } else if (isDecodingFailed) {
                                playerError = "Кодек не поддерживается. Пробую другой формат…"
                                val files = resolvedVideo.files
                                // Fix #337: отмечаем упавшее mp4-качество (HEVC не
                                // поддерживается устройством) — блокируем его повторный
                                // выбор в меню. Если падал сам HLS — mp4 не отмечаем.
                                if (!selectedHls) {
                                    val failedKey = qualityOptions.getOrNull(selectedQualityIndex)?.key
                                    if (failedKey != null) {
                                        failedQualities = failedQualities + failedKey
                                        AppLog.w(TAG, "Marked quality '$failedKey' as failed (codec unsupported)")
                                    }
                                }
                                // Сбрасываем индикатор переключения — дальше идёт
                                // fallback, он не должен висеть в "switching".
                                isSwitchingQuality = false
                                // FIX: VK "hls_ondemand" часто возвращает тот же raw URL (не .m3u8),
                                // а не настоящий HLS-плейлист. Поэтому на DECODING_FAILED пробуем:
                                // 1) Настоящий HLS (m3u8 URL)
                                // 2) Самое низкое mp4_ качество (обычно AVC, не HEVC 10-bit)
                                val realHlsUrl = files?.entries?.firstOrNull { (key, url) ->
                                    key in listOf("hls_ondemand", "hls") && url.contains("m3u8", ignoreCase = true)
                                }?.value
                                if (realHlsUrl != null) {
                                    val savedPosition = self.currentPosition
                                    // W30-STABILITY: скорость читаем ДО перезагрузки
                                    // источника (инстанс тот же, но reapplied явно).
                                    val savedSpeed = self.playbackParameters.speed
                                    val mediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(realHlsUrl))
                                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                                        .build()
                                    self.setMediaItem(mediaItem)
                                    self.prepare()
                                    self.seekTo(savedPosition)
                                    // W30-STABILITY: скорость выживает fallback —
                                    // «скачки скорости» при DECODING_FAILED недопустимы.
                                    self.setPlaybackSpeed(savedSpeed)
                                    // W30-STABILITY #QUALITY-PIN: пин пользователя
                                    // сохраняем — HLS адаптируется в пределах выбранной
                                    // высоты, а не по всему диапазону.
                                    val pinnedKey = qualityOptions.getOrNull(selectedQualityIndex)
                                    if (pinnedKey != null) {
                                        applyQualityPin(self, pinnedKey.key)
                                    } else {
                                        applyQualityPin(self, null)
                                    }
                                    retryCount++
                                    // Fix #337: синхронизируем выбор — теперь играет HLS.
                                    selectedHls = true
                                    AppLog.i(TAG, "DECODING_FAILED → real HLS: ${realHlsUrl.take(80)}")
                                } else {
                                    // Пробуем mp4 качества от самого низкого к самому высокому.
                                    // Низкие качества (240p, 360p) обычно используют AVC (H.264),
                                    // который поддерживается на всех устройствах, включая MediaTek.
                                    val fallbackEntry = files?.entries
                                        ?.filter { (key, _) -> key.startsWith("mp4_") }
                                        ?.sortedByDescending { (key, _) ->
                                            // Сортируем от самого низкого качества к высокому
                                            VideoQuality.KEYS.indexOf(key).takeIf { it >= 0 }
                                                ?: Int.MAX_VALUE
                                        }
                                        ?.firstOrNull()
                                    if (fallbackEntry != null) {
                                        val fallbackUrl = fallbackEntry.value
                                        val savedPosition = self.currentPosition
                                        // W30-STABILITY: скорость читаем ДО перезагрузки
                                        // источника — reapplied после prepare (см. ниже).
                                        val savedSpeed = self.playbackParameters.speed
                                        val mediaItem = MediaItem.Builder()
                                            .setUri(Uri.parse(fallbackUrl))
                                            .build()
                                        self.setMediaItem(mediaItem)
                                        self.prepare()
                                        self.seekTo(savedPosition)
                                        // W30-STABILITY: reapplied после fallback на mp4.
                                        self.setPlaybackSpeed(savedSpeed)
                                        retryCount++
                                        // Fix #337: синхронизируем индекс с играющим
                                        // fallback, чтобы меню показывало реальное качество.
                                        val fbIdx = qualityOptions.indexOfFirst { it.key == fallbackEntry.key }
                                        if (fbIdx >= 0) {
                                            selectedQualityIndex = fbIdx
                                            selectedHls = false
                                        }
                                        AppLog.i(TAG, "DECODING_FAILED → mp4 fallback: ${fallbackUrl.take(80)}")
                                    } else {
                                        retryWithFreshUrl()
                                    }
                                }
                            } else {
                                playerError = "Ошибка видео: ${error.errorCodeName}. Повторная попытка…"
                                retryWithFreshUrl()
                            }
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                playerError = null
                                hasStarted = true
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                            if (playing) hasStarted = true
                        }
                    })
                    AppLog.i(TAG, "ExoPlayer created for video #${resolvedVideo.id} url=$url")
                }
        }
    }

    // #39 C2: restore + save playback position for video.
    var positionRestored by remember(resolvedVideo) { mutableStateOf(false) }
    var lastVideoSaveTs by remember { mutableLongStateOf(0L) }
    val videoPosKey = remember(resolvedVideo, okMetadata) {
        // OK-IMPL-1 (Stage 3b): для OK-видео ключ позиции — "ok_<movieId>",
        // т.к. ownerId/videoId из VK не соответствуют реальному OK movie.
        val metaPos = okMetadata
        if (metaPos != null) "ok_${metaPos.movieId}"
        else re.pinok.media.PlaybackPositionStore.videoKey(resolvedVideo.ownerId, resolvedVideo.id)
    }

    // Update position/state for custom controls + save/restore playback position (#39 C2)
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        // W30-2 #VIDEO-BACKGROUND (контракт §2.4): плеер создан и настроен —
        // регистрируем его в VideoPlaybackBus (W30-3 строит MediaSession +
        // lock-screen уведомление поверх этого инстанса). Заголовок — title
        // видео или честный дефолт «Видео».
        VideoPlaybackBus.onPlayerReady(
            context,
            exoPlayer,
            if (resolvedVideo.title.isBlank()) "Видео" else resolvedVideo.title,
        )
        while (true) {
            if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                currentPositionMs = exoPlayer.currentPosition.toFloat()
                bufferedPositionMs = exoPlayer.bufferedPosition.toFloat()
                durationMs = exoPlayer.duration.toFloat()
                isPlaying = exoPlayer.isPlaying

                // #39 C2: restore saved position once after STATE_READY.
                if (!positionRestored && exoPlayer.playbackState == Player.STATE_READY && durationMs > 0) {
                    val saved = re.pinok.media.PlaybackPositionStore.getPosition(videoPosKey)
                    if (saved > 3000L && saved < (durationMs * 0.95f).toLong()) {
                        exoPlayer.seekTo(saved)
                        AppLog.i(TAG, "Restored video position: ${saved}ms")
                    }
                    positionRestored = true
                }

                // #39 C2: save position every 5s while playing.
                val now = System.currentTimeMillis()
                if (isPlaying && now - lastVideoSaveTs > 5000L && currentPositionMs > 3000f) {
                    re.pinok.media.PlaybackPositionStore.savePosition(videoPosKey, currentPositionMs.toLong())
                    lastVideoSaveTs = now
                }
            }
            delay(200)
        }
    }

    fun switchQuality(newIndex: Int) {
        val player = exoPlayer ?: return
        val option = qualityOptions.getOrNull(newIndex) ?: return
        // Fix #337: не пытаемся переключиться на качество, которое уже упало
        // с DECODING_FAILED (HEVC не поддерживается устройством) — иначе
        // зацикливание: setMediaItem → fail → fallback → снова. Пункт в меню
        // тоже залочен (disabled), но это защита на случай прямого вызова.
        if (option.key in failedQualities) {
            AppLog.w(TAG, "Skip switch to ${option.key} — already failed (codec unsupported)")
            return
        }
        // Сначала фиксируем выбор (даже если URL совпадает — подсветка должна
        // соответствовать нажатому пункту), затем сбрасываем HLS-флаг.
        selectedQualityIndex = newIndex
        selectedHls = false
        val newUrl = option.url
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == newUrl) return

        AppLog.i(TAG, "Switching quality → ${option.label}")
        val savedPosition = player.currentPosition
        val wasPlaying = player.playWhenReady
        isSwitchingQuality = true

        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(newUrl))
        if (newUrl.contains("m3u8", ignoreCase = true)) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()

        // W30-STABILITY: перезагрузка источника — reapplied сохранённый rate,
        // иначе скорость сбрасывается в 1.0 при каждой смене качества.
        player.setPlaybackSpeed(playbackRate)
        // W30-STABILITY #QUALITY-PIN: ручной выбор конкретного качества пинит
        // ABR — между ручными сменами плеер не должен сам прыгать по вариантам.
        applyQualityPin(player, option.key)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    player.seekTo(savedPosition)
                    player.playWhenReady = wasPlaying
                    player.removeListener(this)
                    isSwitchingQuality = false
                    AppLog.i(TAG, "Quality switch complete → ${option.label}")
                }
            }
        })
    }

    // Fix #337: явное переключение на HLS-адаптивный поток ("Авто"). Даёт
    // пользователю рабочий выбор после DECODING_FAILED fallback — раньше HLS
    // был только внутренним fallback и его нельзя было выбрать вручную.
    fun switchToHls() {
        val player = exoPlayer ?: return
        val url = hlsUrl ?: return
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == url) {
            // Уже играет HLS — просто синхронизируем подсветку.
            selectedHls = true
            return
        }
        AppLog.i(TAG, "Switching → HLS (auto)")
        val savedPosition = player.currentPosition
        val wasPlaying = player.playWhenReady
        isSwitchingQuality = true
        selectedHls = true

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()

        // W30-STABILITY: reapplied сохранённый rate и при смене на HLS.
        player.setPlaybackSpeed(playbackRate)
        // W30-STABILITY #QUALITY-PIN: «Авто» (HLS) — пользователь явно выбрал
        // адаптивный поток, пин ABR снимается (дефолтные ограничения).
        applyQualityPin(player, null)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    player.seekTo(savedPosition)
                    player.playWhenReady = wasPlaying
                    player.removeListener(this)
                    isSwitchingQuality = false
                    AppLog.i(TAG, "HLS switch complete")
                }
            }
        })
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            // #39 C2: save final video position before release.
            exoPlayer?.let { player ->
                if (player.playbackState == Player.STATE_READY) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    if (dur > 0 && pos >= dur * 0.95) {
                        // Видео досмотрено — очищаем позицию.
                        re.pinok.media.PlaybackPositionStore.clearPosition(videoPosKey)
                        AppLog.i(TAG, "Video finished — cleared position")
                    } else if (pos > 3000L) {
                        re.pinok.media.PlaybackPositionStore.savePosition(videoPosKey, pos)
                        re.pinok.media.PlaybackPositionStore.flush()
                        AppLog.i(TAG, "Saved final video position: ${pos}ms")
                    }
                }
            }
            // W30-2 #VIDEO-BACKGROUND (контракт §2.4): плеер релизится — сначала
            // отцепляем MediaSession/сервис (stopSelf + release session),
            // затем release самого инстанса.
            VideoPlaybackBus.onPlayerReleased()
            exoPlayer?.release()
            if (exoPlayer != null) AppLog.i(TAG, "ExoPlayer освобождён")
        }
    }

    DisposableEffect(Unit) {
        val wasAudioPlaying = PlayerConnection.pauseIfPlaying()
        if (wasAudioPlaying) AppLog.i(TAG, "Аудиоплеер поставлен на паузу")
        onDispose {
            if (wasAudioPlaying) {
                // #PIP-AUDIO-PAUSE: если PiP активен (пользователь свернул видео в PiP
                // и ушёл с экрана плеера) — не возобновляем аудио здесь; PiP сам
                // возобновит при закрытии (resumeAudioOnClose).
                if (re.pinok.ui.videoplayer.VideoPipActivity.isActive) {
                    re.pinok.ui.videoplayer.VideoPipActivity.resumeAudioOnClose = true
                    AppLog.i(TAG, "Аудио остаётся на паузе — активен PiP, возобновим после закрытия PiP")
                } else {
                    PlayerConnection.resumeIfWasPlaying()
                    AppLog.i(TAG, "Аудиоплеер возобновлён")
                }
            }
        }
    }

    if (exoPlayer != null) {
        // W30-2 #VIDEO-BACKGROUND (контракт §2.4): возврат на экран — снимаем
        // foreground-режим сервиса (НЕ убивая воспроизведение).
        // FIX #VIDEO-FG-TIME-CALLSITE (краш 2026-09-10, logcat 12.465→12.589):
        // раньше был LifecycleStartEffect + onStopOrDispose — ВЕТКА DISPOSE
        // стартовала foreground-сервис при уходе С ЭКРАНА (back-навигация):
        // onBackgrounded → startForegroundService, и через ~120мс onPlayerReleased
        // → stopService — сервис умирал БЕЗ startForeground → системный FATAL
        // ForegroundServiceDidNotStartInTimeException. Теперь onBackgrounded
        // срабатывает ТОЛЬКО на реальный ON_STOP активити (Home/сворачивание),
        // где экран жив и плеер продолжает играть; dispose (выход с экрана)
        // сервис не стартует вовсе — релиз плеера (onPlayerReleased) сам погасит
        // живущий сервис. #VIDEO-AUTOPLAY и восстановление скорости — как прежде,
        // на реальном ON_START.
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, exoPlayer) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        VideoPlaybackBus.onForegrounded()
                        // #VIDEO-AUTOPLAY: только если включено в настройках. Иначе
                        // возврат из фона не должен форсировать play — пользователь
                        // сам ставил на паузу.
                        if (autoplayEnabled) {
                            exoPlayer.playWhenReady = true
                        }
                        // W30-STABILITY: восстановление после STOP — reapplied
                        // сохранённый rate (на некоторых устройствах пересборка
                        // источника после onStop сбрасывала скорость в 1.0).
                        exoPlayer.setPlaybackSpeed(playbackRate)
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        // W30-2 #VIDEO-BACKGROUND: при уходе из активности НЕ ПАУЗИМ
                        // — звук продолжается, W30-3 запускает foreground-сервис с
                        // MediaStyle-уведомлением (lock-screen плеер).
                        VideoPlaybackBus.onBackgrounded(context)
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // #PIP-VIDEO-ONLY: PiP ушёл в отдельную VideoPipActivity (кнопка в контролах
    // запускает её). Здесь осталась только очистка яркости при выходе с экрана.
    DisposableEffect(Unit) {
        onDispose {
            // Restore system brightness
            val act = context as? android.app.Activity
            if (act != null) {
                act.window.attributes = act.window.attributes.also {
                    it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // #61: Prevent screen from turning off while video is playing.
    // Uses Window.addFlags(FLAG_KEEP_SCREEN_ON) — removed when paused or
    // when the composable leaves the composition (DisposableEffect).
    val activity = context as? android.app.Activity
    DisposableEffect(isPlaying) {
        val window = activity?.window
        if (isPlaying && window != null) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            AppLog.d(TAG, "FLAG_KEEP_SCREEN_ON added — screen will stay on while playing")
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            AppLog.d(TAG, "FLAG_KEEP_SCREEN_ON cleared")
        }
    }

    // Auto-hide controls after 3s
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Format time
    fun formatTime(ms: Float): String {
        if (ms <= 0) return "0:00"
        val totalSec = (ms / 1000).toLong()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%d:%02d", m, s)
        }
    }

    // Fullscreen toggle helper
    fun toggleFullscreen() {
        val act = (context as? android.app.Activity)
        if (act != null) {
            isFullscreen = !isFullscreen
            if (isFullscreen && !rotationLocked) {
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            } else if (!isFullscreen) {
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // Fix #383 #COMMUNITY-VIDEO-PARITY: лайк видео через likes.add/likes.delete
    // (type=video, реальные ownerId/videoId из video.get). Оптимистичный апдейт
    // UI + честный откат, если API вернул ошибку (новое количество < 0).
    // access_key пробрасываем — приватные видео без него дают ошибку likes.add.
    fun toggleVideoLike() {
        val v = resolvedVideo
        if (v.id <= 0L || v.ownerId == 0L) return
        val newLiked = !videoLiked
        videoLiked = newLiked
        videoLikeCount = (videoLikeCount + (if (newLiked) 1 else -1)).coerceAtLeast(0)
        scope.launch {
            val newCount = if (newLiked) {
                app.apiClient.likesAdd("video", v.ownerId, v.id, accessKey = v.accessKey)
            } else {
                app.apiClient.likesDelete("video", v.ownerId, v.id, accessKey = v.accessKey)
            }
            if (newCount >= 0) {
                videoLikeCount = newCount
            } else {
                // Ошибка API — откат оптимистичного состояния (честное UI).
                videoLiked = !newLiked
                videoLikeCount = (videoLikeCount + (if (newLiked) -1 else 1)).coerceAtLeast(0)
            }
        }
    }

    // ── W30-2: действия «Добавить» и меню «Ещё» (паритет data-testid VK web) ──
    // Локальный helper Toast — единый стиль уведомлений новых действий.
    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    // #VIDEO-ADD-TO-MINE: «Добавить себе» — video.add создаёт МОЮ копию видео
    // (VK: video_id + owner_id исходника + access_key для приватных; сверь
    // VKApiClient.videoAdd :4122 — сигнатура (videoId, ownerId, accessKey?)).
    // Optimistic-иконка AddCircle→CheckCircle; при ошибке — откат + Toast.
    // Обратное действие — ТОЛЬКО «Ещё» → «Удалить у себя» (removeMineCopy):
    // video.delete удаляет копию с ЕЁ собственным video_id.
    fun addToMine() {
        val v = resolvedVideo
        if (v.id <= 0L || v.ownerId == 0L) return
        if (addedToMine) return // уже у себя; снятие — через меню «Ещё»
        addedToMine = true
        scope.launch {
            val ok = app.apiClient.videoAdd(v.id, v.ownerId, v.accessKey)
            if (ok) {
                toast("Добавлено к себе")
            } else {
                addedToMine = false
                toast("Не удалось добавить")
            }
        }
    }

    // #VIDEO-REMOVE-MINE: «Ещё» → «Удалить у себя». Честная реализация:
    // 1. video.get БЕЗ owner_id — VK берёт owner из токена и возвращает МОЮ
    //    библиотеку (тот же вызов, что VideoScreen :208 для «Мои видео»);
    // 2. ищем копию по title (равенство или startsWith) и длительности ±2с,
    //    предпочитая явную копию под моим id (паттерн #AUDIO-TOGGLE-OWNING);
    // 3. videoDelete(copy.id, copy.ownerId) — у копии СВОИ id/ownerId.
    // Для собственного видео (ownerId == мой id) матчем будет оно само —
    // удалится честно, как «Удалить» в VK web.
    fun removeMineCopy() {
        val v = resolvedVideo
        if (v.id <= 0L || v.ownerId == 0L) return
        scope.launch {
            val mine = try {
                app.apiClient.videoGet(count = 200)
            } catch (e: Exception) {
                AppLog.w(TAG, "videoGet(mine) для поиска копии упал: ${e.message}")
                null
            }
            if (mine == null) {
                toast("Не удалось получить ваши видео")
                return@launch
            }
            var exact: Video? = null
            var anyMatch: Video? = null
            for (c in mine) {
                // Пустой title исходника не матчится через startsWith("") —
                // иначе ложные срабатывания на «безымянных» видео.
                val sameTitle = if (v.title.isBlank()) {
                    c.title.isBlank()
                } else {
                    c.title == v.title || c.title.startsWith(v.title)
                }
                if (!sameTitle) continue
                if (kotlin.math.abs(c.duration - v.duration) > 2) continue
                if (myUserId != 0L && c.ownerId == myUserId) {
                    exact = c
                    break
                }
                if (anyMatch == null) anyMatch = c
            }
            // NULL-ЯВНО: явный выбор цели без elvis.
            val exactFound = exact
            val anyFound = anyMatch
            val target: Video?
            if (exactFound != null) {
                target = exactFound
            } else {
                target = anyFound
            }
            if (target == null) {
                toast("Копия у себя не найдена")
                return@launch
            }
            val ok = app.apiClient.videoDelete(target.id, target.ownerId)
            if (ok) {
                addedToMine = false
                toast("Удалено у себя")
            } else {
                toast("Не удалось удалить")
            }
        }
    }

    // #VIDEO-BOOKMARK: «Ещё» → «В закладки / Убрать из закладки».
    // faveAdd("video", ownerId, itemId) / faveRemove("video", ownerId, itemId)
    // — VKApiClient сам маппит в fave.addVideo/fave.removeVideo для web-токена
    // (см. #FAVE-WEB-TOKEN :6062). Optimistic + откат + Toast.
    fun toggleVideoBookmark() {
        val v = resolvedVideo
        if (v.id <= 0L || v.ownerId == 0L) return
        val newFav = !inBookmarks
        inBookmarks = newFav
        scope.launch {
            val ok = if (newFav) {
                // #BOOKMARKS-FIX: access_key для чужих видео (лента/поиск) —
                // без него VK отвечает ошибкой доступа, «закладки не работают».
                app.apiClient.faveAdd("video", v.ownerId, v.id, v.accessKey)
            } else {
                app.apiClient.faveRemove("video", v.ownerId, v.id, v.accessKey)
            }
            if (ok) {
                if (newFav) toast("Добавлено в закладки") else toast("Удалено из закладок")
            } else {
                inBookmarks = !newFav
                toast("Не удалось обновить закладки")
            }
        }
    }

    // #VIDEO-MORE-DOWNLOAD: «Ещё» → «Скачать» — тот же VideoDownloadManager
    // .enqueueDownload, что в TopAppBar и VideoScreen (:497). Честные
    // предупреждения: без прямой mp4-ссылки менеджер молча откажется —
    // предупреждаем заранее; повторный тап не дублирует загрузку.
    fun downloadFromMenu() {
        val ds = downloadState
        if (ds != null && ds.isCompleted) {
            toast("Уже скачано — доступно офлайн")
            return
        }
        if (ds != null && ds.isInProgress) {
            toast("Загрузка уже идёт")
            return
        }
        val files = resolvedVideo.files
        val hasDirect = files != null && files.keys.any { it.startsWith("mp4_") }
        if (!hasDirect) {
            toast("Нет прямой ссылки — скачивание недоступно")
            return
        }
        VideoDownloadManager.enqueueDownload(resolvedVideo)
        toast("Загрузка началась")
    }

    // #VIDEO-REPORT: «Ещё» → «Пожаловаться» — video.report (W30-API),
    // reason=5 — дефолт метода (единственное честно известное значение,
    // список причин не показываем). Вызывается из confirm-диалога.
    fun sendVideoReport() {
        val v = resolvedVideo
        if (v.id <= 0L || v.ownerId == 0L) return
        scope.launch {
            val ok = app.apiClient.videoReport(v.id, v.ownerId)
            if (ok) toast("Жалоба отправлена") else toast("Не удалось отправить")
        }
    }

    // #VIDEO-COPY-LINK: «Ещё» → «Копировать ссылку» — паттерн VideoShareSheet
    // (ClipboardManager + Toast). Ссылка: страница видео из video.get (player),
    // fallback — канонический формат https://vk.ru/video{ownerId}_{id}.
    fun copyVideoLink() {
        val rawPlayer = resolvedVideo.player
        val link: String = if (rawPlayer != null && rawPlayer.isNotBlank()) {
            rawPlayer
        } else {
            "https://vk.ru/video${resolvedVideo.ownerId}_${resolvedVideo.id}"
        }
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("VK Video", link))
            toast("Ссылка скопирована")
        } else {
            toast("Не удалось скопировать ссылку")
        }
    }

    // #VIDEO-INSETS: единая точка управления системными панелями. Раньше логика
    // hide/show была размазана по toggleFullscreen + DisposableEffect и не учитывала
    // landscape (видео уходило под status/navigation bar при автоповороте телефона).
    // Теперь: immersive (fullscreen ИЛИ landscape) → прячем панели; иначе — показываем.
    LaunchedEffect(immersive) {
        val act = (context as? android.app.Activity) ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (immersive) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Выйти из fullscreen + unlock rotation при уходе
    DisposableEffect(Unit) {
        onDispose {
            val act = (context as? android.app.Activity)
            if (act != null) {
                // #VIDEO-INSETS: показываем system bars обратно при уходе с экрана.
                val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Scaffold(
        containerColor = VK_BLACK,
        topBar = {
            if (!immersive) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDownloaded) {
                            Box(
                                modifier = Modifier
                                    .background(VK_GREEN, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("Офлайн", color = VK_WHITE, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = resolvedVideo.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = VK_WHITE,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = VK_WHITE)
                    }
                },
                actions = {
                    if (currentQualityUrl != null) {
                        IconButton(onClick = {
                            if (downloadState != null && downloadState.status != DownloadStatus.FAILED) {
                                VideoDownloadManager.removeDownload(resolvedVideo.ownerId, resolvedVideo.id)
                            } else {
                                VideoDownloadManager.enqueueDownload(resolvedVideo)
                            }
                        }) {
                            when {
                                downloadState == null || downloadState.status == DownloadStatus.FAILED ->
                                    Icon(Icons.Filled.Download, "Скачать", tint = VK_WHITE)
                                downloadState.isCompleted ->
                                    Icon(Icons.Filled.DownloadDone, "Удалить", tint = VK_GREEN)
                                else ->
                                    Icon(Icons.Filled.Download, "Загрузка…", tint = VK_WHITE.copy(alpha = 0.5f))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VK_BLACK),
            )
            }
        },
    ) { innerPadding ->
        val contentPadding = if (immersive) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding
        // Fix #VIDEO-INSETS: в immersive режиме (fullscreen/landscape) видео рисуется
        // edge-to-edge (contentPadding=0), и системные панели могут быть прозрачными
        // ПОВЕРХ контента. Overlay-элементы (бейдж сверху, панель управления снизу)
        // должны иметь явные insets, чтобы не уходить под status/navigation bar.
        // В non-immersive инсеты уже даёт Scaffold innerPadding.
        val overlayTopInset = if (immersive) Modifier.statusBarsPadding() else Modifier
        val overlayBottomInset = if (immersive) Modifier.navigationBarsPadding() else Modifier
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VK_BLACK)
                .padding(contentPadding),
        ) {
            // ── Video container (video-container.s-e + wrapper-bottom.s-18) ─
            val useFillMax = immersive
            Box(
                modifier = Modifier
                    .then(if (useFillMax) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .background(VK_BLACK),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoadingVideo -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = VK_WHITE)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Загрузка видео…", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
                        }
                    }
                    currentQualityUrl == null || exoPlayer == null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CloudOff, null, tint = VK_TEXT_SECONDARY, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = fetchError ?: "Видео недоступно для воспроизведения",
                                color = VK_WHITE, fontSize = 14.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // P2 #VIDEO-SESSION-HOLD: сессия истекла во время просмотра.
                            // Даём inline «Перезайти» — вместо глобального окна авторизации,
                            // которое было подавлено suppress-окном плеера.
                            if (sessionExpired) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .background(VK_RED, RoundedCornerShape(8.dp))
                                        .clickable {
                                            app.clearSuppressAuthRelaunch()
                                            app.notifyTokenInvalidated()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Text("Перезайти", color = VK_WHITE, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Сессия истекла — после входа видео откроется заново",
                                    color = VK_TEXT_SECONDARY, fontSize = 12.sp,
                                )
                            }
                            // OK-IMPL-1 (Stage 3b): OK-видео недоступно нативно
                            // (metadata fetch failed) — даём пользователю кнопку
                            // «Открыть в браузере» (откроет ok.ru/videoembed/...).
                            if (video.videoPlatform == VideoPlatform.OK && !video.player.isNullOrBlank()) {
                                Text(
                                    "OK-видео не удалось разобрать. Попробуйте открыть в браузере.",
                                    color = VK_TEXT_SECONDARY, fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .background(VK_RED, RoundedCornerShape(8.dp))
                                        .clickable {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(video.player),
                                            )
                                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                AppLog.w(TAG, "No browser available: ${e.message}")
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Text("Открыть в браузере", color = VK_WHITE, fontSize = 13.sp)
                                }
                            } else {
                                Text("VK не вернул прямой URL файла", color = VK_TEXT_SECONDARY, fontSize = 12.sp)
                            }
                        }
                    }
                    playerError != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isLoadingVideo) {
                                CircularProgressIndicator(color = VK_WHITE)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Повторная попытка…", color = VK_TEXT_SECONDARY, fontSize = 14.sp)
                            } else {
                                Icon(Icons.Filled.CloudOff, null, tint = Color(0xFFEF5350), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(playerError ?: "Ошибка", color = VK_WHITE, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                IconButton(onClick = { retryWithFreshUrl() }) {
                                    Icon(Icons.Filled.Refresh, "Повторить", tint = VK_WHITE)
                                }
                            }
                        }
                    }
                    else -> {
                        // ── Player view (useController=false!) ──
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        useController = false // FIX: убираем дублирующий контроллер
                                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                        player = exoPlayer
                                    }
                                },
                                update = { view ->
                                    // Fix #233 (P1-9): factory вызывается только при первом
                                    // создании view. При смене resolvedVideo (video.get
                                    // fallback) remember(resolvedVideo) создаёт НОВЫЙ
                                    // ExoPlayer, но PlayerView держал СТАРЫЙ уже released
                                    // → чёрный экран/краш. update перевязывает актуальный
                                    // player на каждой recomposition.
                                    view.player = exoPlayer
                                },
                                modifier = Modifier.fillMaxSize(),
                            )

                            // OK-IMPL-1 (Stage 7): бейдж «Без рекламы» для VK и OK видео.
                            // ExoPlayer не грузит Adman JS (только iframe/WebView его грузит)
                            // → реклама отсутствует by design. Зелёный щит в правом верхнем
                            // углу, виден только когда controlsVisible (иначе перекрывает тач-зону).
                            if (controlsVisible && hasStarted && (
                                resolvedVideo.videoPlatform == VideoPlatform.VK ||
                                resolvedVideo.videoPlatform == VideoPlatform.OK ||
                                okMetadata != null)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .then(overlayTopInset)
                                        .padding(8.dp)
                                        .background(Color(0xCC1B5E20), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.VerifiedUser,
                                        contentDescription = "Без рекламы",
                                        tint = VK_GREEN,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        "Без рекламы",
                                        color = Color(0xFFA5D6A7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }

                            // ── Тап/свайп зона: ПЕРВЫЙ слой в Box (ниже по z-order). ──
                            // Обрабатывает: тап (toggle controls), двойной тап (seek ±10с),
                            // вертикальный свайп (лево=яркость, право=громкость).
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        var lastTapTime = 0L
                                        val touchSlop = viewConfiguration.touchSlop

                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val startPos = down.position
                                            var isDragging = false
                                            var settledGestureType: String? = null
                                            var startBrightness = brightnessLevel
                                                .takeIf { it >= 0 }
                                                ?: run {
                                                    val act = context as? android.app.Activity
                                                    val win = act?.window
                                                    val cur = win?.attributes?.screenBrightness
                                                    if (cur != null && cur >= 0) cur else 0.5f
                                                }
                                            var startVolume = if (isMuted) 0f else volume

                                            do {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull() ?: break
                                                if (!change.pressed) {
                                                    change.consume()
                                                    break
                                                }

                                                val dx = change.position.x - startPos.x
                                                val dy = change.position.y - startPos.y

                                                if (!isDragging) {
                                                    if (kotlin.math.abs(dy) > touchSlop && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                                        isDragging = true
                                                        settledGestureType = if (startPos.x < size.width / 2f) "brightness" else "volume"
                                                        gestureType = settledGestureType
                                                        change.consume()
                                                    }
                                                } else {
                                                    change.consume()
                                                    val totalDy = change.position.y - startPos.y
                                                    val fraction = (totalDy / size.height).coerceIn(-1f, 1f)

                                                    if (settledGestureType == "brightness") {
                                                        val newBrightness = (startBrightness - fraction).coerceIn(0f, 1f)
                                                        brightnessLevel = newBrightness
                                                        gestureValue = newBrightness
                                                        val act = context as? android.app.Activity
                                                        if (act != null) {
                                                            act.window.attributes = act.window.attributes.also {
                                                                it.screenBrightness = newBrightness
                                                            }
                                                        }
                                                    } else {
                                                        val newVolume = (startVolume - fraction).coerceIn(0f, 1f)
                                                        volume = newVolume
                                                        gestureValue = newVolume
                                                        isMuted = newVolume < 0.01f
                                                        exoPlayer.volume = newVolume
                                                    }
                                                }
                                            } while (true)

                                            // Если не был свайп — это тап
                                            if (!isDragging) {
                                                val now = System.currentTimeMillis()
                                                if (now - lastTapTime < 300) {
                                                    // Двойной тап: лево = -10с, право = +10с
                                                    val isLeft = startPos.x < size.width / 2f
                                                    val maxPos = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
                                                    if (isLeft) {
                                                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0))
                                                        seekLabel = "-10 сек"
                                                    } else {
                                                        exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(maxPos))
                                                        seekLabel = "+10 сек"
                                                    }
                                                    scope.launch {
                                                        delay(600)
                                                        seekLabel = null
                                                    }
                                                } else {
                                                    controlsVisible = !controlsVisible
                                                }
                                                lastTapTime = now
                                            }

                                            // Скрыть индикатор жеста
                                            if (isDragging) {
                                                scope.launch {
                                                    delay(400)
                                                    gestureType = null
                                                }
                                            }
                                        }
                                    },
                            )

                            // ── Big play button (playButton.s-a) — visible before start ──
                            // #VIDEO-PLAY-BUTTON: по центру и больше по размеру.
                            // Раньше: size(96.dp), icon 38.dp, БЕЗ .align() → TopStart.
                            // Теперь: size(120.dp), icon 56.dp, .align(Alignment.Center).
                            if (!hasStarted) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x80000000))
                                        .clickable {
                                            hasStarted = true
                                            exoPlayer.playWhenReady = true
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow, "Смотреть",
                                        tint = VK_WHITE, modifier = Modifier.size(56.dp),
                                    )
                                }
                            }

                            // ── Seek indicator (double-tap circle like YouTube) ──
                            VKOverlayVisibility(
                                visible = seekLabel != null,
                                enter = fadeIn(tween(150)),
                                exit = fadeOut(tween(300)),
                                modifier = Modifier.align(Alignment.Center).offset(y = (-60).dp),
                            ) {
                                val isSeekForward = seekLabel?.startsWith("+") == true
                                Box(
                                    modifier = Modifier.size(64.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Background circle
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x99333333)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            if (isSeekForward) Icons.Filled.PlayArrow else Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                            tint = VK_WHITE,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                                Text(
                                    seekLabel ?: "",
                                    color = VK_CONTROL_TEXT,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 28.dp),
                                )
                            }

                            // ── Gesture overlay (brightness/volume swipe) ──
                            VKOverlayVisibility(
                                visible = gestureType != null,
                                enter = fadeIn(tween(100)),
                                exit = fadeOut(tween(300)),
                                modifier = Modifier.align(
                                    if (gestureType == "brightness") Alignment.CenterStart else Alignment.CenterEnd,
                                ).padding(horizontal = 20.dp),
                            ) {
                                val isBrightness = gestureType == "brightness"
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        if (isBrightness) Icons.Outlined.BrightnessMedium
                                        else if (gestureValue < 0.01f) Icons.AutoMirrored.Outlined.VolumeOff
                                        else Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = null,
                                        tint = VK_WHITE,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0x4DFFFFFF)),
                                        contentAlignment = Alignment.BottomCenter,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(gestureValue.coerceIn(0f, 1f))
                                                .background(VK_WHITE),
                                        )
                                    }
                                    Text(
                                        text = "${(gestureValue * 100).toInt()}%",
                                        color = VK_CONTROL_TEXT, fontSize = 11.sp,
                                    )
                                }
                            }

                            // ── Quality switch indicator ──
                            if (isSwitchingQuality) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = VK_WHITE, strokeWidth = 2.dp,
                                    )
                                    Text(
                                        // Fix #337: при переключении на HLS показываем "Авто".
                                        if (selectedHls) "Авто"
                                        else qualityOptions.getOrNull(selectedQualityIndex)?.label ?: "",
                                        color = VK_CONTROL_TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    )
                                }
                            }

                            // ── VK Controls overlay (wrapper-bottom.s-18) ──
                            // Находится ПОСЛЕ pointerInput Box → ВЫШЕ по z-order → получает тапы.
                            VKOverlayVisibility(
                                visible = controlsVisible && hasStarted,
                                enter = fadeIn(tween(250)),
                                exit = fadeOut(tween(250)),
                                modifier = Modifier.align(Alignment.BottomCenter).then(overlayBottomInset),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // ── Timeline (timeline.s-b + timeline-slider.s-1m) ──
                                    VKTimeline(
                                        currentPositionMs = currentPositionMs,
                                        bufferedPositionMs = bufferedPositionMs,
                                        durationMs = durationMs,
                                        onSeek = { fraction ->
                                            val targetMs = (fraction * durationMs).toLong()
                                            exoPlayer.seekTo(targetMs)
                                        },
                                    )

                                    // ── Controls bar (controls.s-18) ──
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        // controls-left.s-18
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Play/Pause
                                            VKControlButton(
                                                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (isPlaying) "Пауза" else "Смотреть",
                                                onClick = {
                                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                                },
                                            )

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Time display (time.s-1n)
                                            // #VIDEO-TEXT-GRAY: серый вместо белого.
                                            Text(
                                                text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                                                color = VK_CONTROL_TEXT,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                            )
                                        }

                                        // controls-right.s-18
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Mute/Volume
                                            VKControlButton(
                                                icon = if (isMuted || volume < 0.01f) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                                                contentDescription = if (isMuted) "Включить звук" else "Выключить звук",
                                                onClick = {
                                                    isMuted = !isMuted
                                                    exoPlayer.volume = if (isMuted) 0f else volume
                                                },
                                            )

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Speed
                                            VKControlButton(
                                                icon = Icons.Outlined.Speed,
                                                contentDescription = "Скорость",
                                                onClick = {
                                                    settingsSubmenu = "speed"
                                                    settingsOpen = true
                                                },
                                            )

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Settings (gear) — opens quality/speed menu
                                            VKControlButton(
                                                icon = Icons.Filled.Settings,
                                                contentDescription = "Настройки",
                                                onClick = {
                                                    settingsSubmenu = "quality"
                                                    settingsOpen = true
                                                },
                                            )

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Fullscreen
                                            VKControlButton(
                                                icon = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                                contentDescription = if (isFullscreen) "Выйти из полноэкранного" else "На весь экран",
                                                onClick = { toggleFullscreen() },
                                            )

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // PiP
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                VKControlButton(
                                                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                                                    contentDescription = "Картинка в картинке",
                                                    onClick = {
                                                        // #PIP-VIDEO-ONLY: запускаем отдельную PiP-активность
                                                        // с текущим URL + позицией; основной плеер на паузе,
                                                        // чтобы не шли два потока одновременно.
                                                        val p = exoPlayer
                                                        val url = p.currentMediaItem?.localConfiguration?.uri?.toString()
                                                        if (url != null) {
                                                            p.playWhenReady = false
                                                            val act = context as? android.app.Activity
                                                            if (act != null) {
                                                                act.startActivity(
                                                                    re.pinok.ui.videoplayer.VideoPipActivity.intent(
                                                                        act,
                                                                        url,
                                                                        resolvedVideo.title,
                                                                        p.currentPosition,
                                                                        resolvedVideo.ownerId,
                                                                        resolvedVideo.id,
                                                                        // #PIP-INHERIT-SETTINGS: скорость наследуется от видеоплеера.
                                                                        playbackRate,
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    },
                                                )
                                            }

                                            // Rotation lock (fullscreen only)
                                            if (isFullscreen) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                VKControlButton(
                                                    icon = Icons.Outlined.Lock,
                                                    contentDescription = if (rotationLocked) "Разблокировать поворот" else "Заблокировать поворот",
                                                    onClick = {
                                                        rotationLocked = !rotationLocked
                                                        val act = context as? android.app.Activity
                                                        if (act != null) {
                                                            act.requestedOrientation = if (rotationLocked) {
                                                                if (isLandscape) {
                                                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                                } else {
                                                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                                                }
                                                            } else {
                                                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                                                            }
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Settings menu popup (settings-menu.s-p) ──
                            if (settingsOpen && hasStarted) {
                                VKSettingsPopup(
                                    showQualitySelector = showQualitySelector,
                                    qualityOptions = qualityOptions,
                                    selectedQualityIndex = selectedQualityIndex,
                                    hlsOption = hlsOption,
                                    selectedHls = selectedHls,
                                    failedQualities = failedQualities,
                                    playbackRates = PLAYBACK_RATES,
                                    currentPlaybackRate = playbackRate,
                                    submenu = settingsSubmenu,
                                    onDismiss = {
                                        settingsOpen = false
                                        settingsSubmenu = null
                                    },
                                    onQualitySelected = { idx ->
                                        // Fix #337: switchQuality сам валидирует (failed-качества
                                        // заблокированы) и устанавливает selectedQualityIndex/
                                        // selectedHls — не делаем этого здесь, чтобы подсветка
                                        // не съезжала на заблокированный пункт.
                                        switchQuality(idx)
                                        settingsOpen = false
                                        settingsSubmenu = null
                                    },
                                    onHlsSelected = {
                                        switchToHls()
                                        settingsOpen = false
                                        settingsSubmenu = null
                                    },
                                    onPlaybackRateSelected = { rate ->
                                        playbackRate = rate
                                        exoPlayer.setPlaybackSpeed(rate)
                                    },
                                    onSubmenuOpen = { menu ->
                                        settingsSubmenu = menu
                                    },
                                )
                            }

                            // ── Fix #383 #COMMUNITY-VIDEO-PARITY: фуллскрин-стрип действий ──
                            // В immersive (fullscreen/landscape) портретный блок под
                            // плеером скрыт (useFillMax) — именно из-за этого при просмотре
                            // видео сообществ «пропадали» ВСЕ действия: лайк, комментарии,
                            // поделиться, просмотры. Теперь вертикальный VK-стиль стрип
                            // справа виден вместе с контролами (controlsVisible && hasStarted),
                            // тач-таргеты ≥44dp. Для внешних видео без ownerId/id
                            // (YouTube/iframe) VK-действия неприменимы — стрип не показываем.
                            if (resolvedVideo.id > 0L && resolvedVideo.ownerId != 0L) {
                                VKOverlayVisibility(
                                    visible = controlsVisible && hasStarted,
                                    enter = fadeIn(tween(250)),
                                    exit = fadeOut(tween(250)),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 4.dp),
                                ) {
                                    ImmersiveVideoActionsColumn(
                                        video = resolvedVideo,
                                        isLiked = videoLiked,
                                        likeCount = videoLikeCount,
                                        // W30-2: паритет «Добавить»/«Ещё» в immersive-стрипе.
                                        addedToMine = addedToMine,
                                        inBookmarks = inBookmarks,
                                        onToggleLike = { toggleVideoLike() },
                                        onOpenComments = { showCommentsSheet = true },
                                        onShare = { showShareSheet = true },
                                        onAddToMine = { addToMine() },
                                        onToggleBookmark = { toggleVideoBookmark() },
                                        onDownload = { downloadFromMenu() },
                                        onReport = { showReportConfirm = true },
                                        onCopyLink = { copyVideoLink() },
                                        onRemoveMine = { removeMineCopy() },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!useFillMax) {
            /**
             * #VIDEO-TEXT-EXPAND (волна 18-η): пагинация текста под открытым видео
             * (портретный режим, useFillMax == false). Паттерн Fix #345
             * (CommunityScreen): свёрнутый текст с maxLines + Ellipsis, детект
             * РЕАЛЬНОГО переполнения через TextLayoutResult.hasVisualOverflow
             * (onTextLayout), разворот по тапу. Задокументированные решения:
             * 1. Заголовок — maxLines 2; тап по переполненному разворачивает,
             *    повторный тап по развёрнутому СВОРАЧИВАЕТ (VK web не возвращает,
             *    но честный toggle удобнее — разрешено ТЗ; без overflow текст
             *    некликабелен — нет ложной клик-зоны).
             * 2. Описание — maxLines 4; текстовая кнопка «Показать ещё»/«Свернуть»
             *    (акцентный синий Fix #345, 14sp) + тап по самому тексту тоже
             *    разворачивает/сворачивает.
             * 3. Состояния разворота — rememberSaveable (переживают recreation
             *    и process death), ключ resolvedVideo.id — смена видео сбрасывает.
             * 4. Флаг overflow синхронизируется с фактическим лэйаутом на каждой
             *    перекомпоновке (свёрнуто — hasVisualOverflow, развёрнуто — false):
             *    кнопка появляется только когда текст реально не влез, и исчезает,
             *    если текст обновился на короткий.
             * 5. Fix #350 (20-A, скрин юзера 08.09.2026 16:45 «нет пагинации»):
             *    инфо-блок занимает ОСТАТОК экрана (weight(1f) внутри внешнего
             *    Column, плеер — выше с aspectRatio 16:9) и ПРОКРУЧИВАЕТСЯ
             *    (verticalScroll). До фикса колонка росла вниз без скролла:
             *    развёрнутый текст (тап «Показать ещё») обрезался нижней
             *    границей экрана, «Свернуть» оставалась за вьюпортом — текст
             *    дальше был недостижим. Та же обрезка грозила и свёрнутому
             *    состоянию на малых экранах (2 строки титула + 4 описания +
             *    действия > остаток экрана).
             * 6. Порядок — как в приложении VK на странице видео: заголовок →
             *    просмотры → строка действий (VideoActionBar) → описание.
             *    VideoActionBar поднят НАД описанием: лайк/шеринг/скачивание
             *    доступны сразу, без прокрутки через развёрнутый текст (раньше
             *    стоял под ним и уезжал за экран вместе с ним).
             * 7. Клик-зоны не пересекаются: clickable висит ТОЛЬКО на своём
             *    тексте/кнопке. Просмотры — без изменений. Immersive/
             *    landscape-ветку (useFillMax == true) не трогаем.
             */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                // ── Заголовок: свёрнут до 2 строк, тап-toggle при переполнении ──
                var titleExpanded by rememberSaveable(resolvedVideo.id) { mutableStateOf(false) }
                var titleOverflowed by rememberSaveable(resolvedVideo.id) { mutableStateOf(false) }
                Text(
                    text = resolvedVideo.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = VK_WHITE,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    modifier = if (titleOverflowed || titleExpanded) {
                        Modifier.clickable { titleExpanded = !titleExpanded }
                    } else {
                        Modifier
                    },
                    onTextLayout = { result ->
                        titleOverflowed = !titleExpanded && result.hasVisualOverflow
                    },
                    maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (resolvedVideo.views > 0) "${resolvedVideo.views} просмотров" else "Нет просмотров",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VK_TEXT_SECONDARY,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 20-A: строка действий ПЕРЕД описанием (порядок VK; была под ним
                // и уезжала за экран вместе с развёрнутым текстом — см. KDoc п.6).
                // Fix #383 #COMMUNITY-VIDEO-PARITY: VideoActionBar → VideoActionsRow
                // (лайк + комментарии + поделиться + просмотры; состояние и API —
                // toggleVideoLike/videoLiked/videoLikeCount, шторки Comments/Share).
                VideoActionsRow(
                    video = resolvedVideo,
                    subTextColor = VK_TEXT_SECONDARY,
                    isLiked = videoLiked,
                    likeCount = videoLikeCount,
                    // W30-2: паритет «Добавить»/«Ещё» (data-testid
                    // video_page_add_to_my_playlist / video_page_more_button).
                    addedToMine = addedToMine,
                    inBookmarks = inBookmarks,
                    onToggleLike = { toggleVideoLike() },
                    onOpenComments = { showCommentsSheet = true },
                    onShare = { showShareSheet = true },
                    onAddToMine = { addToMine() },
                    onToggleBookmark = { toggleVideoBookmark() },
                    onDownload = { downloadFromMenu() },
                    onReport = { showReportConfirm = true },
                    onCopyLink = { copyVideoLink() },
                    onRemoveMine = { removeMineCopy() },
                )
                val desc = resolvedVideo.description
                if (!desc.isNullOrBlank()) {
                    // ── Описание: свёрнуто до 4 строк, тап-toggle + кнопка-текст ──
                    var descExpanded by rememberSaveable(resolvedVideo.id) { mutableStateOf(false) }
                    var descOverflowed by rememberSaveable(resolvedVideo.id) { mutableStateOf(false) }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VK_WHITE.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        modifier = if (descOverflowed || descExpanded) {
                            Modifier.clickable { descExpanded = !descExpanded }
                        } else {
                            Modifier
                        },
                        onTextLayout = { result ->
                            descOverflowed = !descExpanded && result.hasVisualOverflow
                        },
                        maxLines = if (descExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // «Показать ещё»/«Свернуть» — только при реальном hasVisualOverflow
                    // (Fix #345: акцентный цвет, bodyMedium = 14sp).
                    if (descOverflowed || descExpanded) {
                        Text(
                            text = if (descExpanded) "Свернуть" else "Показать ещё",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1976D2),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { descExpanded = !descExpanded },
                        )
                    }
                }
            }
            }
        }
    }

    // ── Fix #383 #COMMUNITY-VIDEO-PARITY: шторки действий плеера ──
    // ModalBottomSheet рендерится в собственном окне — расположение в композиции
    // не влияет на вид; держим рядом с плеером для единого состояния. Сами шторки
    // работают на реальном API (video.getComments/video.createComment,
    // messages.send/wall.post с video-attachment) — см. реализации ниже.
    if (showCommentsSheet) {
        VideoCommentsSheet(
            video = resolvedVideo,
            onDismiss = { showCommentsSheet = false },
        )
    }
    if (showShareSheet) {
        VideoShareSheet(
            video = resolvedVideo,
            onDismiss = { showShareSheet = false },
        )
    }

    // W30-2 #VIDEO-REPORT: confirm-диалог «Пожаловаться» — на уровне композабла
    // экрана, не внутри скролл-контейнеров/меню (урок #PIN-DIALOG-OVERLAY).
    // Причина НЕ выбирается (не знаем честно): video.report с reason=5 —
    // дефолт метода («прочее»), см. VKApiClient :4148.
    if (showReportConfirm) {
        AlertDialog(
            onDismissRequest = { showReportConfirm = false },
            title = { Text("Пожаловаться") },
            text = { Text("Отправить жалобу на видео?") },
            confirmButton = {
                TextButton(onClick = {
                    showReportConfirm = false
                    sendVideoReport()
                }) {
                    Text("Отправить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportConfirm = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

// ── VK-style timeline slider (timeline-slider.s-1m) ──────────────────
@Composable
private fun VKTimeline(
    currentPositionMs: Float,
    bufferedPositionMs: Float,
    durationMs: Float,
    onSeek: (Float) -> Unit,
) {
    val progress = if (durationMs > 0) currentPositionMs / durationMs else 0f
    val buffered = if (durationMs > 0) bufferedPositionMs / durationMs else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(0.dp)),
        ) {
            // Background bar (bars.s-1m)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(VK_SLIDER_BG),
            )
            // Buffered (loaded.s-1m)
            Box(
                modifier = Modifier
                    .fillMaxWidth(buffered)
                    .fillMaxHeight()
                    .background(VK_SLIDER_BG),
            )
            // Played (filled.s-1m)
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(VK_WHITE),
            )
        }

        // Invisible slider for interaction
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .offset(y = (-14).dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

// ── VK control button (btn-container.s-1p > btn.s-27) ────────────────
@Composable
private fun VKControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            icon, contentDescription,
            tint = VK_WHITE,
            modifier = Modifier.size(24.dp),
        )
    }
}

// ── VK Settings popup (settings-menu-container.s-p) ──────────────────
@Composable
private fun VKSettingsPopup(
    showQualitySelector: Boolean,
    qualityOptions: List<QualityOption>,
    selectedQualityIndex: Int,
    hlsOption: QualityOption?,
    selectedHls: Boolean,
    failedQualities: Set<String>,
    playbackRates: List<Float>,
    currentPlaybackRate: Float,
    submenu: String?,
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit,
    onHlsSelected: () -> Unit,
    onPlaybackRateSelected: (Float) -> Unit,
    onSubmenuOpen: (String?) -> Unit,
) {
    // Simple popup overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // Menu container
        Column(
            modifier = Modifier
                .padding(end = 62.dp, bottom = 57.dp) // Смещено левее чтобы не перекрывать fullscreen кнопку
                .widthIn(max = 300.dp)
                .heightIn(max = 350.dp) // Ограничение высоты — ниже пойдёт скролл
                .clip(RoundedCornerShape(8.dp))
                .background(VK_SETTINGS_BG)
                .verticalScroll(rememberScrollState())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // consume click
                ),
        ) {
            if (submenu == null || submenu == "quality") {
                if (submenu == "quality") {
                    VKSettingsHeader(
                        title = "Качество",
                        onBack = { onSubmenuOpen(null) },
                        onClose = onDismiss,
                    )
                    // Quality items (only in quality sub-menu)
                    if (showQualitySelector && qualityOptions.isNotEmpty()) {
                        // Fix #337: HLS-адаптивный поток ("Авто") — рабочий выбор
                        // для устройств, не поддерживающих HEVC. Показываем первым,
                        // если VK отдал настоящий m3u8-плейлист.
                        if (hlsOption != null) {
                            VKSettingsItem(
                                label = hlsOption.label,
                                sublabel = "адаптивное",
                                selected = selectedHls,
                                onClick = onHlsSelected,
                            )
                            VKSettingsDivider()
                        }
                        qualityOptions.forEachIndexed { index, option ->
                            val isFailed = option.key in failedQualities
                            VKSettingsItem(
                                label = option.label,
                                // Fix #337: упавшие (HEVC) качества — disabled,
                                // подсветка только если выбрано и не failed.
                                selected = !selectedHls && index == selectedQualityIndex && !isFailed,
                                enabled = !isFailed,
                                sublabel = if (isFailed) "недоступно" else null,
                                onClick = { onQualitySelected(index) },
                            )
                        }
                    }
                } else {
                    VKSettingsHeader(
                        title = "Настройки",
                        onClose = onDismiss,
                    )
                }

                if (submenu == null) {
                    // Speed
                    VKSettingsItem(
                        label = "Скорость",
                        sublabel = formatPlaybackRate(currentPlaybackRate),
                        onClick = { onSubmenuOpen("speed") },
                    )
                }
            }

            if (submenu == "speed") {
                VKSettingsHeader(
                    title = "Скорость",
                    onBack = { onSubmenuOpen(null) },
                    onClose = onDismiss,
                )
                playbackRates.forEach { rate ->
                    VKSettingsItem(
                        label = if (rate == 1f) "Обычная" else "${rate}x",
                        selected = rate == currentPlaybackRate,
                        onClick = { onPlaybackRateSelected(rate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VKSettingsHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Text(
                "← ",
                color = VK_WHITE,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onBack),
            )
        }
        Text(
            title,
            color = VK_WHITE,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            "✕",
            color = VK_WHITE,
            fontSize = 13.sp,
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onClose),
            textAlign = TextAlign.Center,
        )
    }
    VKSettingsDivider()
}

@Composable
private fun VKSettingsItem(
    label: String,
    sublabel: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // Fix #337: disabled-пункты (упавшие HEVC-качества) — серый текст и
    // отсутствие кликабельности, чтобы пользователь видел, что качество
    // недоступно на устройстве, а не пытался его выбрать вхолостую.
    val labelColor = if (enabled) VK_WHITE else VK_WHITE.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Spacer(modifier = Modifier.width(28.dp)) // space for check icon
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = labelColor,
                fontSize = 13.sp,
            )
            if (sublabel != null) {
                Text(
                    sublabel,
                    color = VK_WHITE.copy(alpha = if (enabled) 0.5f else 0.3f),
                    fontSize = 12.sp,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check, "Выбрано",
                tint = VK_GREEN,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun VKSettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(1.dp)
            .background(Color(0x3DFFFFFF)), // white-alpha-24
    )
}

private fun formatPlaybackRate(rate: Float): String {
    return if (rate == 1f) "Обычная" else "${rate}x"
}

/**
 * Sprint 2, P1-2 (#89): Action bar для видео — лайк (кликабельный) + просмотры.
 *
 * Fix #383 #COMMUNITY-VIDEO-PARITY: добавлены «Комментарии» (шторка со списком
 * из video.getComments) и «Поделиться» (шторка: чат / своя стена / ссылка).
 * Состояние лайка поднято на уровень VideoPlayerScreen — портретный row и
 * фуллскрин-стрип используют одно состояние и не расходятся. Тач-таргеты ≥44dp
 * (heightIn(min = 44.dp) на кликабельных группах).
 *
 * W30-2 (паритет футера страницы видео VK web): добавлены
 *  - «Добавить» (data-testid video_page_add_to_my_playlist): video.add копию
 *    себе; после успеха — CheckCircle, снятие только через меню «Ещё»;
 *  - «Ещё» (data-testid video_page_more_button): DropdownMenu с закладками
 *    (fave.add/removeVideo), скачиванием (VideoDownloadManager), жалобой
 *    (video.report, confirm-диалог на уровне экрана), копированием ссылки и
 *    «Удалить у себя» (videoDelete моей копии) — общий набор VideoMoreMenuItems.
 */
@Composable
private fun VideoActionsRow(
    video: Video,
    subTextColor: Color,
    isLiked: Boolean,
    likeCount: Int,
    addedToMine: Boolean,
    inBookmarks: Boolean,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onAddToMine: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDownload: () -> Unit,
    onReport: () -> Unit,
    onCopyLink: () -> Unit,
    onRemoveMine: () -> Unit,
) {
    // W30-2: локальный флаг раскрытия меню «Ещё» (DropdownMenu — popup,
    // не диспоузится скроллом: урок #PIN-DIALOG-OVERLAY здесь не применим,
    // но confirm-диалог жалобы всё равно живёт на уровне экрана).
    var moreOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Лайк — реальный API likes.add/likes.delete (type=video), см. toggleVideoLike.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onToggleLike)
                .padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
            Icon(
                if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                "Лайк",
                modifier = Modifier.size(20.dp),
                tint = if (isLiked) Color(0xFFE53935) else subTextColor,
            )
            if (likeCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = likeCount.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLiked) Color(0xFFE53935) else subTextColor,
                    fontSize = 14.sp,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        // Комментарии — video.getComments/video.createComment (Fix #383). Счётчик
        // из video.get (extended): если VK не вернул счётчик — показываем иконку
        // без числа (честно: список всё равно откроется и покажет реальное).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onOpenComments)
                .padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                "Комментарии",
                modifier = Modifier.size(20.dp),
                tint = subTextColor,
            )
            if (video.commentsCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = video.commentsCount.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = subTextColor,
                    fontSize = 14.sp,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        // Поделиться — шторка: отправить в чат (video-attachment) / на свою стену /
        // скопировать ссылку (Fix #383).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onShare)
                .padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
            Icon(
                Icons.Filled.Share,
                "Поделиться",
                modifier = Modifier.size(20.dp),
                tint = subTextColor,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        // W30-2 #VIDEO-ADD-TO-MINE: «Добавить» (data-testid
        // video_page_add_to_my_playlist) — video.add копию себе; после успеха
        // CheckCircle (+VK_GREEN), снятие — только через меню «Ещё» →
        // «Удалить у себя». Явный if вместо elvis/!! по #NULL-ЯВНО.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .then(if (addedToMine) Modifier else Modifier.clickable(onClick = onAddToMine))
                .padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
            Icon(
                if (addedToMine) Icons.Filled.CheckCircle else Icons.Filled.AddCircle,
                if (addedToMine) "Добавлено" else "Добавить себе",
                modifier = Modifier.size(20.dp),
                tint = if (addedToMine) VK_GREEN else subTextColor,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        // W30-2 #VIDEO-MORE-MENU: «Ещё» (data-testid video_page_more_button) —
        // DropdownMenu (popup, поверх скролл-контейнера) с общим набором
        // VideoMoreMenuItems: закладки / скачать / пожаловаться / ссылка /
        // удалить у себя.
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable { moreOpen = true }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    "Ещё",
                    modifier = Modifier.size(20.dp),
                    tint = subTextColor,
                )
            }
            DropdownMenu(
                expanded = moreOpen,
                onDismissRequest = { moreOpen = false },
            ) {
                VideoMoreMenuItems(
                    inBookmarks = inBookmarks,
                    onToggleBookmark = onToggleBookmark,
                    onDownload = onDownload,
                    onReport = onReport,
                    onCopyLink = onCopyLink,
                    onRemoveMine = onRemoveMine,
                    onDismiss = { moreOpen = false },
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        // Просмотры — поле views из video.get (extended), только счётчик.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Visibility,
                null,
                modifier = Modifier.size(20.dp),
                tint = subTextColor,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = video.views.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = subTextColor,
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * Fix #383 #COMMUNITY-VIDEO-PARITY: вертикальный VK-стиль стрип действий справа
 * для immersive-режима плеера (fullscreen/landscape). Лайк — кликабельный,
 * комментарии и «Поделиться» открывают шторки, просмотры — счётчик без клика.
 *
 * W30-2: паритет футера VK web в immersive — «Добавить»
 * (video_page_add_to_my_playlist) и «Ещё» (video_page_more_button) с тем же
 * набором VideoMoreMenuItems, что и в портретном VideoActionsRow (единое
 * состояние на уровне экрана — addedToMine/inBookmarks).
 */
@Composable
private fun ImmersiveVideoActionsColumn(
    video: Video,
    isLiked: Boolean,
    likeCount: Int,
    addedToMine: Boolean,
    inBookmarks: Boolean,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onAddToMine: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDownload: () -> Unit,
    onReport: () -> Unit,
    onCopyLink: () -> Unit,
    onRemoveMine: () -> Unit,
) {
    // W30-2: локальный флаг раскрытия меню «Ещё» стрипа.
    var stripMoreOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x66000000))
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VideoStripAction(
            icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            label = if (likeCount > 0) likeCount.toString() else "",
            tint = if (isLiked) Color(0xFFE53935) else VK_WHITE,
            contentDescription = "Лайк",
            onClick = onToggleLike,
        )
        // Счётчик комментариев — из video.get; 0/отсутствие = иконка без числа.
        VideoStripAction(
            icon = Icons.Outlined.ChatBubbleOutline,
            label = if (video.commentsCount > 0) video.commentsCount.toString() else "",
            tint = VK_WHITE,
            contentDescription = "Комментарии",
            onClick = onOpenComments,
        )
        VideoStripAction(
            icon = Icons.Filled.Share,
            label = "",
            tint = VK_WHITE,
            contentDescription = "Поделиться",
            onClick = onShare,
        )
        // W30-2 #VIDEO-ADD-TO-MINE: «Добавить» — тот же стейт, что в портретном
        // row. После успеха CheckCircle/VK_GREEN и БЕЗ клика (снятие — через
        // «Ещё» → «Удалить у себя»); кликабельность честно отключается.
        VideoStripAction(
            icon = if (addedToMine) Icons.Filled.CheckCircle else Icons.Filled.AddCircle,
            label = "",
            tint = if (addedToMine) VK_GREEN else VK_WHITE,
            contentDescription = if (addedToMine) "Добавлено" else "Добавить себе",
            onClick = if (addedToMine) null else onAddToMine,
        )
        // W30-2 #VIDEO-MORE-MENU: «Ещё» открывает тот же набор, что и в
        // портретном row (DropdownMenu — popup, якорится на Box стрипа).
        Box {
            VideoStripAction(
                icon = Icons.Outlined.MoreHoriz,
                label = "",
                tint = VK_WHITE,
                contentDescription = "Ещё",
                onClick = { stripMoreOpen = true },
            )
            DropdownMenu(
                expanded = stripMoreOpen,
                onDismissRequest = { stripMoreOpen = false },
            ) {
                VideoMoreMenuItems(
                    inBookmarks = inBookmarks,
                    onToggleBookmark = onToggleBookmark,
                    onDownload = onDownload,
                    onReport = onReport,
                    onCopyLink = onCopyLink,
                    onRemoveMine = onRemoveMine,
                    onDismiss = { stripMoreOpen = false },
                )
            }
        }
        VideoStripAction(
            icon = Icons.Outlined.Visibility,
            label = video.views.toString(),
            tint = VK_WHITE,
            contentDescription = "Просмотры",
            onClick = null,
        )
    }
}

/**
 * Fix #383 #COMMUNITY-VIDEO-PARITY: одна кнопка фуллскрин-стрипа — 44dp тач-таргет
 * + подпись-счётчик под иконкой. onClick == null → только счётчик (просмотры),
 * кликабельность не имитируем.
 */
@Composable
private fun VideoStripAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    contentDescription: String,
    onClick: (() -> Unit)?,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Явная проверка вместо elvis: кликабельность — отдельный modifier.
        val clickMod = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .then(clickMod),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = VK_WHITE.copy(alpha = 0.85f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/**
 * W30-2 #VIDEO-MORE-MENU: содержимое меню «Ещё» (data-testid
 * video_page_more_button) — общий набор для портретного VideoActionsRow и
 * immersive-стрипа. Все действия — реальный API без заглушек:
 *  - «В закладки / Убрать из закладки»: faveAdd/faveRemove type="video"
 *    (VKApiClient маппит в fave.addVideo/fave.removeVideo — #FAVE-WEB-TOKEN);
 *  - «Скачать»: VideoDownloadManager.enqueueDownload (паттерн VideoScreen :497);
 *  - «Пожаловаться»: confirm-диалог на уровне экрана → video.report (W30-API);
 *  - «Копировать ссылку»: ClipboardManager (паттерн VideoShareSheet);
 *  - «Удалить у себя»: videoDelete моей копии (removeMineCopy).
 * Каждый пункт закрывает меню перед действием (onDismiss), чтобы шторки/диалоги
 * не открывались из-под открытого popup.
 */
@Composable
private fun VideoMoreMenuItems(
    inBookmarks: Boolean,
    onToggleBookmark: () -> Unit,
    onDownload: () -> Unit,
    onReport: () -> Unit,
    onCopyLink: () -> Unit,
    onRemoveMine: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(if (inBookmarks) "Убрать из закладки" else "В закладки") },
        leadingIcon = {
            Icon(
                if (inBookmarks) Icons.Outlined.BookmarkBorder else Icons.Filled.Bookmark,
                contentDescription = null,
            )
        },
        onClick = {
            onDismiss()
            onToggleBookmark()
        },
    )
    DropdownMenuItem(
        text = { Text("Скачать") },
        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
        onClick = {
            onDismiss()
            onDownload()
        },
    )
    DropdownMenuItem(
        text = { Text("Пожаловаться") },
        leadingIcon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        onClick = {
            onDismiss()
            onReport()
        },
    )
    DropdownMenuItem(
        text = { Text("Копировать ссылку") },
        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
        onClick = {
            onDismiss()
            onCopyLink()
        },
    )
    DropdownMenuItem(
        text = { Text("Удалить у себя") },
        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        onClick = {
            onDismiss()
            onRemoveMine()
        },
    )
}

/**
 * Fix #383 #COMMUNITY-VIDEO-PARITY: шторка комментариев видео.
 *
 * Реальный API, без заглушек:
 *  - video.getComments (extended=1) — список + авторы (юзеры из profiles[],
 *    сообщества из groups[]);
 *  - video.createComment — отправка нового комментария;
 *  - ошибка API → честный текст ошибки + кнопка «Повторить» (никаких крашей);
 *  - can_comment == 0 (автор отключил комментарии) → поле ввода скрыто с
 *    пояснением; список при этом всё равно показывается.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoCommentsSheet(
    video: Video,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var authors by remember { mutableStateOf<Map<Long, UserProfile>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var newComment by remember { mutableStateOf("") }

    // can_comment: 0 = комментарии отключены; null = API не отдал поле — ввод
    // НЕ блокируем (VK вернёт ошибку на createComment, покажем её честно).
    val commentsDisabled = video.canComment == 0

    fun loadComments() {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                val r = app.apiClient.videoGetComments(video.ownerId, video.id, count = 50)
                comments = r.comments
                authors = r.profiles
            } catch (e: Exception) {
                AppLog.e(TAG, "videoGetComments error for ${video.ownerId}_${video.id}", e)
                errorMsg = "Не удалось загрузить комментарии: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(video.ownerId, video.id) { loadComments() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Заголовок шторки (паттерн ClipCommentsSheet).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Комментарии · ${video.commentsCount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
            HorizontalDivider()
            // Честные состояния: загрузка / ошибка / пусто / список.
            val err = errorMsg
            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                err != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { loadComments() }) { Text("Повторить") }
                        }
                    }
                }
                comments.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Пока нет комментариев. Будьте первым!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        items(comments, key = { c -> c.id }) { comment ->
                            VideoCommentItem(comment = comment, authors = authors)
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            // Поле ввода — если автор не отключил комментарии.
            if (commentsDisabled) {
                Text(
                    text = "Автор отключил комментарии к этому видео.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newComment,
                        onValueChange = { newComment = it },
                        placeholder = { Text("Ваш комментарий…", fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        enabled = !sending,
                        maxLines = 3,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val text = newComment.trim()
                            if (text.isBlank() || sending) return@IconButton
                            scope.launch {
                                sending = true
                                try {
                                    // Реальный video.createComment (ownerId может быть
                                    // отрицательным — видео сообщества).
                                    val cid = app.apiClient.videoCreateComment(
                                        ownerId = video.ownerId,
                                        videoId = video.id,
                                        message = text,
                                    )
                                    if (cid > 0) {
                                        newComment = ""
                                        loadComments() // перезагружаем список с сервера
                                        android.widget.Toast.makeText(
                                            context, "Комментарий добавлен", android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context, "Не удалось добавить комментарий", android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    AppLog.e(TAG, "videoCreateComment error", e)
                                    android.widget.Toast.makeText(
                                        context, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                } finally {
                                    sending = false
                                }
                            }
                        },
                        enabled = newComment.isNotBlank() && !sending,
                    ) {
                        if (sending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Отпр.",
                                color = if (newComment.isNotBlank() && !sending) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fix #383 #COMMUNITY-VIDEO-PARITY: элемент списка комментариев видео —
 * автор (юзер или сообщество из единой карты authors), время, текст.
 */
@Composable
private fun VideoCommentItem(
    comment: Comment,
    authors: Map<Long, UserProfile>,
) {
    val author = authors[comment.fromId]
    val name = when {
        comment.fromId > 0L -> {
            if (author != null) author.fullName.trim() else "id${comment.fromId}"
        }
        comment.fromId < 0L -> {
            if (author != null && author.firstName.isNotBlank()) author.firstName else "Сообщество"
        }
        else -> "Гость"
    }
    val timeText = if (comment.date > 0) {
        try {
            java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(comment.date * 1000L))
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val photo = if (author != null) author.photo100 else null
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (timeText != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Fix #383 #COMMUNITY-VIDEO-PARITY: шторка «Поделиться» для видео.
 *
 * Реальные механизмы (переиспользованы существующие API-хелперы):
 *  - «В чат» — messages.send с attachment "video{ownerId}_{id}[_accessKey]"
 *    (sendVideoToChat);
 *  - «На свою стену» — wall.post с video-attachment (wallPostWithAttachments),
 *    тот же путь, что и для клипов;
 *  - «Ссылка» — реальный URL: player из video.get (страница видео VK), fallback
 *    на канонический vk.com/video{ownerId}_{id}; копируется в буфер обмена.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoShareSheet(
    video: Video,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var chats by remember { mutableStateOf<List<re.pinok.data.model.Chat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // video-attachment для messages.send / wall.post — с access_key для
    // приватных видео (тот же формат, что и для клипов в SovaNavHost).
    val videoAttachment = buildString {
        append("video").append(video.ownerId).append("_").append(video.id)
        val ak = video.accessKey
        if (!ak.isNullOrBlank()) {
            append("_").append(ak)
        }
    }
    // Реальная ссылка на видео: player-URL из video.get — страница видео VK
    // (всегда есть у VK-видео); для прочих — канонический формат ссылки.
    val videoLink = if (!video.player.isNullOrBlank()) {
        video.player
    } else {
        "https://vk.com/video${video.ownerId}_${video.id}"
    }

    LaunchedEffect(video.ownerId, video.id) {
        loading = true
        try {
            chats = app.apiClient.messagesGetConversations(count = 20)
        } catch (e: Exception) {
            AppLog.e(TAG, "messagesGetConversations error", e)
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Поделиться видео",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            // Быстрые действия.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VideoQuickShareButton(
                    icon = Icons.Filled.Share,
                    label = "На стену",
                    modifier = Modifier.weight(1f),
                ) {
                    scope.launch {
                        try {
                            // wall.post с video-attachment — как у клипов (#37.12 #322).
                            val postId = app.apiClient.wallPostWithAttachments(
                                attachments = videoAttachment,
                                message = "",
                            )
                            android.widget.Toast.makeText(
                                context,
                                if (postId > 0) "Опубликовано на стене" else "Не удалось опубликовать",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } catch (e: Exception) {
                            AppLog.e(TAG, "wallPostWithAttachments error", e)
                            android.widget.Toast.makeText(
                                context, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        onDismiss()
                    }
                }
                VideoQuickShareButton(
                    icon = Icons.Filled.ContentCopy,
                    label = "Ссылка",
                    modifier = Modifier.weight(1f),
                ) {
                    // Реальная ссылка на видео — в буфер обмена.
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("VK Video", videoLink))
                        android.widget.Toast.makeText(
                            context, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context, "Не удалось скопировать ссылку", android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                    onDismiss()
                }
            }
            HorizontalDivider()
            Text(
                text = "Отправить в чат",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (chats.isEmpty()) {
                Text(
                    text = "Нет чатов",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    items(chats, key = { c -> c.peer.id }) { chat ->
                        // NULL-ЯВНО: chat.peer.title — String? из другого модуля, smart-cast
                        // по свойству чужого класса невозможен (ошибки :2946/:2954).
                        // Локальный val с явной проверкой даёт не-null String вниз по коду.
                        val peerTitle: String? = chat.peer.title
                        val title: String = if (peerTitle != null && peerTitle.isNotBlank()) {
                            peerTitle
                        } else {
                            "id${chat.peer.id}"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable {
                                    scope.launch {
                                        try {
                                            // Реальная отправка video-attachment в диалог.
                                            val mid = app.apiClient.sendVideoToChat(chat.peer.id, video)
                                            if (mid > 0) {
                                                android.widget.Toast.makeText(
                                                    context, "Отправлено в «$title»", android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context, "Не удалось отправить", android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            AppLog.e(TAG, "sendVideoToChat error", e)
                                            android.widget.Toast.makeText(
                                                context, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                val peerPhoto = chat.peer.photo
                                if (peerPhoto != null) {
                                    AsyncImage(
                                        model = peerPhoto,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Text(
                                        text = title.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Fix #383 #COMMUNITY-VIDEO-PARITY: кнопка быстрого действия шторки «Поделиться». */
@Composable
private fun VideoQuickShareButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val TAG = "VideoPlayerScreen"