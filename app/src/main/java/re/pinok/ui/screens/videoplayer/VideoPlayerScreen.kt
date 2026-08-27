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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.OkVideoRepository
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.Video
import re.pinok.data.model.VideoPlatform
import re.pinok.data.model.VideoQuality
import re.pinok.media.PlayerConnection
import re.pinok.media.VideoDownloadManager
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
        // compiler smart-cast extId к String внутри `if (isOkVideo)`, без `!!`.
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
                                    val mediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(realHlsUrl))
                                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                                        .build()
                                    self.setMediaItem(mediaItem)
                                    self.prepare()
                                    self.seekTo(savedPosition)
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
                                        val mediaItem = MediaItem.Builder()
                                            .setUri(Uri.parse(fallbackUrl))
                                            .build()
                                        self.setMediaItem(mediaItem)
                                        self.prepare()
                                        self.seekTo(savedPosition)
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
        LifecycleStartEffect(exoPlayer) {
            // #VIDEO-AUTOPLAY: только если включено в настройках. Иначе возврат
            // из фона не должен форсировать play — пользователь сам ставил на паузу.
            if (autoplayEnabled) {
                exoPlayer.playWhenReady = true
            }
            onStopOrDispose { exoPlayer.playWhenReady = false }
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
                                                    icon = Icons.Outlined.OpenInNew,
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
                        }
                    }
                }
            }

            if (!useFillMax) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = resolvedVideo.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = VK_WHITE,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (resolvedVideo.views > 0) "${resolvedVideo.views} просмотров" else "Нет просмотров",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VK_TEXT_SECONDARY,
                    fontSize = 14.sp,
                )
                val desc = resolvedVideo.description
                if (!desc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VK_WHITE.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                VideoActionBar(video = resolvedVideo, subTextColor = VK_TEXT_SECONDARY)
            }
            }
        }
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
 */
@Composable
private fun VideoActionBar(video: Video, subTextColor: Color) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var isLiked by remember(video.id) { mutableStateOf(video.isLiked) }
    var likeCount by remember(video.id) { mutableStateOf(video.likesCount) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    if (video.id <= 0 || video.ownerId == 0L) return@clickable
                    val newLiked = !isLiked
                    isLiked = newLiked
                    likeCount = (likeCount + (if (newLiked) 1 else -1)).coerceAtLeast(0)
                    scope.launch {
                        val newCount = if (newLiked) {
                            app.apiClient.likesAdd("video", video.ownerId, video.id)
                        } else {
                            app.apiClient.likesDelete("video", video.ownerId, video.id)
                        }
                        if (newCount >= 0) {
                            likeCount = newCount
                        } else {
                            isLiked = !newLiked
                            likeCount = (likeCount + (if (newLiked) -1 else 1)).coerceAtLeast(0)
                        }
                    }
                }
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
        Spacer(modifier = Modifier.width(24.dp))
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

private const val TAG = "VideoPlayerScreen"