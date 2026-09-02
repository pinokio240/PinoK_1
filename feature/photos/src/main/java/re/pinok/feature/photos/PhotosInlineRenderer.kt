package re.pinok.feature.photos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * Данные одного фото-вложения для инлайн-рендера в чате — ТОЛЬКО примитивы:
 * контейнер compose-контекста и моделей хоста (:app) не знает.
 *
 * @param url            URL изображения (largestUrl). null = «пустой слот» сетки
 *                       (битое вложение без sizes): место в строке занимает,
 *                       картинка не рендерится — точный паритет с прежним
 *                       host-рендером ChatDetailScreen.
 * @param isStickerLike  стикер-фото (Fix #227: квадрат ≤512px, отправлен через
 *                       messagesSendStickerAsImage) — рендер в исходном размере,
 *                       не на всю ширину бабла. Флаг считает ХОСТ (эвристика по
 *                       размерам фото), контейнер моделей VK не знает.
 * @param naturalWidthPx  ширина наибольшего размера в px (для стикер-фото);
 *                        0 = неизвестно (тогда дисплейный кап 160dp×userScale).
 * @param naturalHeightPx высота наибольшего размера в px (для стикер-фото).
 */
data class InlinePhotoItem(
    val url: String?,
    val isStickerLike: Boolean = false,
    val naturalWidthPx: Int = 0,
    val naturalHeightPx: Int = 0,
)

/**
 * #ARCH-CONTAINERS (Этап 1.5-а): инлайн-рендер фото-вложений чата — перенос
 * фото-ветки из ChatDetailScreen (MessageBubble) в контейнер :feature:photos.
 * Хост находит рендерер через реестр (AttachmentRenderer, rendererKey
 * "photos_inline") и вызывает этот компосабл, прокидывая:
 *  - [items]    — данные вложений (только примитивы, см. [InlinePhotoItem]);
 *  - [stickerScalePct] — пользовательский масштаб стикер-фото 0..40 (Fix #228;
 *    источник — настройка хоста SovaPrefs.stickerPhotoScale, CompositionLocal
 *    LocalStickerPhotoScale остаётся в :app — хост читает и передаёт числом);
 *  - [onOpen]   — тап по фото (URL): хост решает — PhotoViewer / toggle selection
 *    (Fix #244), контейнер навигации и selection-состояния не знает;
 *  - [onLongPress] — long-press (selection/context-menu хоста).
 *
 * Поведение = прежний host-рендер 1:1:
 *  - сетка: 1 колонка для одиночного фото, иначе 2; межклеточные отступы 4dp;
 *  - стикер-фото (одиночное): исходный размер из px (density хоста), без апскейла,
 *    кап 160dp × пользовательский масштаб, ContentScale.Fit;
 *  - обычные: одиночное — fillMaxWidth + heightIn(max=200dp) + Fit; в сетке —
 *    weight(1f) + Crop; скругление 8dp;
 *  - «пустые слоты» (url=null) не рендерятся, добор пустых Spacer'ов до ровной
 *    строки — как раньше.
 *
 * Coil: AsyncImage берёт ГЛОБАЛЬНЫЙ ImageLoader (строит хост в SovaApp.newImageLoader:
 * OkHttp с cookie-jar + GIF/animated-WebP декодеры) — анимированные картинки
 * работают без изменений, модуль декодеры не регистрирует.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosInlineRenderer(
    items: List<InlinePhotoItem>,
    stickerScalePct: Int,
    onOpen: (url: String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cols = if (items.size == 1) 1 else 2
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.chunked(cols).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (item in row) {
                    val url = item.url
                    if (url != null) {
                        val isSingle = cols == 1
                        if (isSingle && item.isStickerLike) {
                            // Fix #227/#228: стикер-фото — исходный размер (px → dp,
                            // без апскейла), кап 160dp, поднимаемый пользовательским
                            // масштабом (0..40%). ContentScale.Fit сохраняет пропорции.
                            val density = LocalDensity.current.density
                            val userScalePct = stickerScalePct.coerceIn(0, 40)
                            val userScale = 1f + userScalePct / 100f
                            // Cap поднимаем пропорционально userScale — иначе
                            // при +40% стикер упрётся в 160dp и не увеличится.
                            val capDp = (160f * userScale).dp
                            val naturalWDp = if (item.naturalWidthPx > 0) (item.naturalWidthPx / density * userScale).toInt().dp else capDp
                            val naturalHDp = if (item.naturalHeightPx > 0) (item.naturalHeightPx / density * userScale).toInt().dp else capDp
                            // Не апскейлим выше natural×userScale — только
                            // даунскейл если natural×userScale > cap.
                            val scale = minOf(
                                capDp.value / naturalWDp.value,
                                capDp.value / naturalHDp.value,
                                1f,
                            )
                            val dispW = (naturalWDp.value * scale).roundToInt().dp
                            val dispH = (naturalHDp.value * scale).roundToInt().dp
                            AsyncImage(
                                model = url,
                                contentDescription = "Стикер",
                                modifier = Modifier
                                    .size(dispW, dispH)
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = { onOpen(url) },
                                        onLongClick = onLongPress,
                                    ),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            // Fix #225: для одиночных фото ContentScale.Fit,
                            // для сетки (cols=2) — Crop.
                            AsyncImage(
                                model = url,
                                contentDescription = "Фото",
                                modifier = Modifier
                                    .let { m -> if (isSingle) m.fillMaxWidth() else m.weight(1f) }
                                    .clip(RoundedCornerShape(8.dp))
                                    .heightIn(max = 200.dp)
                                    .combinedClickable(
                                        onClick = { onOpen(url) },
                                        onLongClick = onLongPress,
                                    ),
                                contentScale = if (isSingle) ContentScale.Fit else ContentScale.Crop,
                            )
                        }
                    }
                }
                // Заполнить пустые ячейки для ровной сетки
                if (row.size < cols) {
                    repeat(cols - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
