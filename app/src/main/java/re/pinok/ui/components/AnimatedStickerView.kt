package re.pinok.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import re.pinok.SovaApp
import re.pinok.util.AppLog
import java.util.concurrent.ConcurrentHashMap

/**
 * #STICKER-LOTTIE: рендер анимированных VK-стикеров формата TGS (gzipped Lottie JSON).
 *
 * ПРОБЛЕМА: Coil не умеет декодировать Lottie → раньше .tgs/.json пропускался в
 * Models.kt (animatedDisplayUrl возвращал null) и стикер показывался статичным
 * PNG. Теперь Models.animatedDisplayUrl отдаёт URL для всех форматов, а UI
 * различает Lottie vs GIF/WebP через isLottie и направляет Lottie сюда.
 *
 * РЕШЕНИЕ: скачиваем .tgs, gunzip, парсим LottieComposition, рендерим через
 * LottieAnimation (loop forever). Composition кешируется по URL в
 * [lottieCache] — парсинг тяжёлый (50-200ms), TGS-стикеры переиспользуются
 * (один и тот же стикер в чате и пикере, повторные показы при скролле).
 *
 * Для .json (не-gzipped Lottie) — тот же путь без gunzip (magic bytes проверка).
 *
 * НЕ блокируем UI: пока composition == null (грузится или failed), рисуем ничего.
 * Caller (ChatDetailScreen) показывает AnimatedStickerView либо поверх
 * статичного PNG-fallback (если хочет), либо сам по себе.
 *
 * @param url     URL .tgs/.json (animated_url стикера)
 * @param sizeDp  размер стороны в dp (128 — для чата, 56 — для пикера; совпадает
 *                с AsyncImage sizeDp чтобы Lottie и PNG стикеры в пикере были
 *                визуально одного размера)
 * @param modifier Modifier, применяемый к LottieAnimation (например graphicsLayer
 *                для dimAlpha при !isActive / !isPurchased)
 */
private val lottieCache = ConcurrentHashMap<String, LottieComposition?>()

@Composable
fun AnimatedStickerView(
    url: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 128,
) {
    var composition by remember(url) { mutableStateOf<LottieComposition?>(lottieCache[url]) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        // Уже в кеше (показываем сразу, без дёрганья).
        val cached = lottieCache[url]
        if (cached != null) {
            composition = cached
            return@LaunchedEffect
        }
        if (failed) return@LaunchedEffect
        try {
            val comp = withContext(Dispatchers.IO) { fetchAndParseLottie(url) }
            lottieCache[url] = comp
            composition = comp
            if (comp == null) failed = true
        } catch (e: Exception) {
            AppLog.e("AnimatedSticker", "failed to load lottie $url", e)
            failed = true
        }
    }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
    )

    if (composition != null) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier.size(sizeDp.dp),
        )
    }
    // composition == null (грузится или failed) → рисуем ничего. Не блокируем UI.
}

/**
 * Скачивает .tgs/.json, gunzip (если TGS — magic bytes 0x1f 0x8b),
 * парсит LottieComposition синхронно (caller уже на Dispatchers.IO).
 *
 * @return composition или null при HTTP-ошибке / пустом теле / ошибке парсинга.
 *         null НЕ бросает — caller кеширует null и не ретраит до перезапуска.
 */
private suspend fun fetchAndParseLottie(url: String): LottieComposition? {
    val client = try { SovaApp.getOrNull()?.httpClient } catch (e: Exception) { null } ?: return null
    return withContext(Dispatchers.IO) {
        val req = okhttp3.Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                AppLog.w("AnimatedSticker", "HTTP ${resp.code} for $url")
                return@withContext null
            }
            val bytes = resp.body?.bytes() ?: return@withContext null
            // TGS = gzip. Проверяем magic bytes (0x1f 0x8b) — устойчивее чем
            // экстракт .tgs из URL (URL может быть extension-less или .json.gz).
            val jsonStr = if (bytes.size >= 2 &&
                bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
            ) {
                java.util.zip.GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
            } else {
                String(bytes, Charsets.UTF_8)
            }
            // Синхронный парсинг (мы уже на Dispatchers.IO). fromJsonStringSync
            // возвращает LottieResult<LottieComposition?> — .value даёт composition
            // (null при ошибке JSON-парсинга).
            LottieCompositionFactory.fromJsonStringSync(jsonStr, null).value
        }
    }
}
