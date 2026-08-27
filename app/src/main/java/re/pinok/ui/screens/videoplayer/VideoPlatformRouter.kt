// File: ui/screens/videoplayer/VideoPlatformRouter.kt
package re.pinok.ui.screens.videoplayer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import re.pinok.SovaApp
import re.pinok.data.model.Video
import re.pinok.data.model.VideoPlatform
import re.pinok.util.AppLog

/**
 * OK-IMPL-1 (Stage 5): единая точка входа в видеоплеер с диспетчеризацией по платформе.
 *
 * Заменяет прямые вызовы [VideoPlayerScreen] в [re.pinok.ui.navigation.SovaNavHost]
 * и [re.pinok.ui.MainActivity]. Определяет платформу видео через
 * [Video.videoPlatform] (вычисляется в `VKApiClient` через [Video.detectPlatform])
 * и направляет в соответствующий плеер.
 *
 * ## Маршрутизация:
 *
 * | Platform          | externalVideosEnabled | Player              | Примечание                              |
 * |-------------------|-----------------------|---------------------|-----------------------------------------|
 * | VK                | (не важно)            | [VideoPlayerScreen] | Нативный ExoPlayer, files из VK API.    |
 * | UNKNOWN           | (не важно)            | [VideoPlayerScreen] | Пробует VK path (существующее поведение).|
 * | OK                | true                  | [VideoPlayerScreen] | Сначала [OkVideoRepository] (нативный), |
 * |                   |                       |                     | при неудаче — fallback на WebView.      |
 * | OK                | false                 | DisabledPlaceholder | FEED-FIX-1 (#346): пользователь         |
 * |                   |                       |                     | отключил OK/внешние видео.              |
 * | YOUTUBE           | true                  | [OkWebViewPlayer]   | WebView + ad-blocking.                  |
 * | YOUTUBE           | false                 | DisabledPlaceholder |                                         |
 * | EXTERNAL_IFRAME   | true                  | [OkWebViewPlayer]   | WebView generic iframe.                 |
 * | EXTERNAL_IFRAME   | false                 | DisabledPlaceholder |                                         |
 *
 * ## Кнопка «Скачать» — Fix #141 (2026-08-03):
 *
 * Все плееры теперь имеют кнопку «Скачать»:
 *  - **VideoPlayerScreen** (VK/OK): [VideoDownloadManager.enqueueDownload] через
 *    прямые URL из `video.files` (mp4_1080/720/480/...). Кнопка в top-bar.
 *  - **OkWebViewPlayer** (YouTube/EXTERNAL_IFRAME): [VideoDownloadManager.enqueueUrlDownload]
 *    через URL извлечённый из `<video>.currentSrc` в WebView (JS-инъекция).
 *    Кнопка в top-bar рядом с бейджем «Без рекламы».
 *  - **ClipsFeedScreen**: [StoryDownloadButton] (Fix #140 — navigationBarsPadding).
 *  - **StoryViewerScreen**: [StoryDownloadButton] (Fix #140).
 *  - **ClipCreateScreen / StoryOffline / ClipOffline**: кнопки в нижнем блоке.
 *
 * Synthetic-ключи для внешних видео: если video.id<=0 или ownerId==0,
 * `enqueueUrlDownload` использует `ownerId=-2_000_000_000` + `id=abs(url.hashCode())`.
 * Это гарантирует что разные URL → разные файлы, а один URL → один файл.
 *
 * ## externalVideosEnabled pref (Stage 7 + FEED-FIX-4 #349):
 *
 * Default true. Пользователь явно запросил «тумблер включён по умолчанию».
 * VK-видео играет нативно всегда — флаг на VK НЕ влияет.
 * При выключении OK/YouTube/external показывается placeholder
 * «Внешние видео отключены» с кнопкой «Открыть в браузере».
 *
 * Безопасность: OkVideoRepository (нативный OK path) НЕ использует WebView
 * вообще — только plain HTTP к ok.ru/api.mycdn.me → НЕ влияет на авторизацию.
 * WebView включается только для YouTube/EXTERNAL_IFRAME.
 *
 * ## Единый плеер для всех платформ:
 *
 * Этот Composable и есть «единый плеер» — единая точка входа. Внутри он
 * маршрутизирует в platform-specific реализации (нативный ExoPlayer vs WebView),
 * но снаружи API одинаков для всех: `VideoPlatformRouter(video, onBack)`.
 * Дальнейшая унификация (общий контрол-bar, общие жесты) — P2, пока не требуется.
 *
 * См. OK_VIDEO_PLAN.md §Этап 5.
 * См. OK_PLAYER_REVERSE.md §3308-3313 (cross-platform support).
 */
@Composable
fun VideoPlatformRouter(
    video: Video,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = SovaApp.getOrNull()
    // NULLSAFE-1 + FEED-FIX-4 (#349): prefsSnapshot — mutable property,
    // smart cast на нём невозможен (compiler error: «could be mutated
    // concurrently»). Захватываем в локальный val для null-check + smart cast.
    // Default true — пользователь запросил «тумблер включён по умолчанию».
    val prefsSnap = if (app != null) app.prefsSnapshot else null
    val externalEnabled = if (prefsSnap != null) {
        prefsSnap.externalVideosEnabled
    } else {
        true
    }

    AppLog.d(TAG, "Routing video #${video.id} platform=${video.videoPlatform} externalId=${video.externalId} externalEnabled=$externalEnabled")

    when (video.videoPlatform) {
        // Нативный путь: VK API files. UNKNOWN fallback тоже сюда —
        // VideoPlayerScreen сам попытается videoGetById и в случае неудачи покажет
        // понятную ошибку. Флаг externalVideosEnabled на VK/UNKNOWN НЕ влияет.
        VideoPlatform.VK,
        VideoPlatform.UNKNOWN -> VideoPlayerScreen(video = video, onBack = onBack)

        // FEED-FIX-4 (#349): OK-видео зависит от externalVideosEnabled (default true).
        // При externalEnabled=true: нативный путь (OkVideoRepository → ExoPlayer,
        // без рекламы, без WebView — НЕ влияет на авторизацию).
        // При externalEnabled=false: placeholder «Внешние видео отключены».
        // OkVideoRepository.fetchMetadata() делает plain HTTP к ok.ru и НЕ трогает
        // CookieManager/токены → побочный эффект с потерей авторизации устранён.
        VideoPlatform.OK -> {
            if (externalEnabled) {
                VideoPlayerScreen(video = video, onBack = onBack)
            } else {
                AppLog.i(TAG, "OK video #${video.id} blocked (externalVideosEnabled=false) — placeholder")
                ExternalVideosDisabledPlaceholder(
                    video = video,
                    onBack = onBack,
                    onOpenInBrowser = {
                        val url = video.player ?: return@ExternalVideosDisabledPlaceholder
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            AppLog.w(TAG, "No browser available: ${e.message}")
                        }
                    },
                )
            }
        }

        // WebView-путь: YouTube + external iframe. Если externalVideosEnabled=false,
        // показываем placeholder вместо WebView (пользователь явно отключил внешние).
        VideoPlatform.YOUTUBE,
        VideoPlatform.EXTERNAL_IFRAME -> {
            if (externalEnabled) {
                OkWebViewPlayer(video = video, onBack = onBack)
            } else {
                ExternalVideosDisabledPlaceholder(
                    video = video,
                    onBack = onBack,
                    onOpenInBrowser = {
                        val url = video.player ?: return@ExternalVideosDisabledPlaceholder
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            AppLog.w(TAG, "No browser available: ${e.message}")
                        }
                    },
                )
            }
        }

        // Fix #142 (2026-08-03): Instagram — отдельная ветка от EXTERNAL_IFRAME,
        // т.к. Instagram блокирует generic iframe (X-Frame-Options: SAMEORIGIN).
        // OkWebViewPlayer строит специальный embed URL `instagram.com/reel/<id>/embed`
        // (см. OkWebViewPlayer.embedUrl для VideoPlatform.INSTAGRAM).
        // Этот endpoint публичный и НЕ блокирует iframe-встраивание.
        VideoPlatform.INSTAGRAM -> {
            if (externalEnabled) {
                OkWebViewPlayer(video = video, onBack = onBack)
            } else {
                ExternalVideosDisabledPlaceholder(
                    video = video,
                    onBack = onBack,
                    onOpenInBrowser = {
                        val url = video.player ?: return@ExternalVideosDisabledPlaceholder
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            AppLog.w(TAG, "No browser available: ${e.message}")
                        }
                    },
                )
            }
        }
    }
}

/**
 * OK-IMPL-1 (Stage 7): placeholder «Внешние видео отключены».
 *
 * Показывается когда пользователь выключил [SovaPrefs.externalVideosEnabled]
 * и пытается открыть YOUTUBE/EXTERNAL_IFRAME видео. Без блокировки навигации
 * (кнопка «Назад»), но с возможностью открыть в системном браузере.
 */
@Composable
private fun ExternalVideosDisabledPlaceholder(
    video: Video,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // Back button (top-left, semi-transparent)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color(0x80000000), RoundedCornerShape(8.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Filled.OpenInBrowser,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(8.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Внешние видео отключены",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Видео «${video.title.ifBlank { "без названия" }}» открыто во внешнем плеере. " +
                    "Включите «Внешние видео» в настройках или откройте в браузере.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (!video.player.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2196F3), RoundedCornerShape(8.dp))
                        .clickable { onOpenInBrowser() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Открыть в браузере", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

private const val TAG = "VideoPlatformRouter"
