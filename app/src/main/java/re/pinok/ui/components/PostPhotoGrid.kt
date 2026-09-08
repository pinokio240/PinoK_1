package re.pinok.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.data.model.Attachment
import re.pinok.data.model.PhotoSizes

/**
 * #POST-CAROUSEL-EVERYWHERE (волна 22): ОБЩИЙ рендер фото-вложений поста для ВСЕХ
 * поверхностей — лента (FeedScreen/PostCard), сообщество (CommunityScreen), профиль
 * (ProfileScreen), открытый пост (PostDetailScreen). До волны 22 карусель была только
 * в ленте (19-A), в остальных местах фото шли сеткой/по одному.
 *
 * Семантика 1:1 из FeedScreen (до этого — приватные PhotoGrid + FeedPhotoCarousel):
 *
 * - carouselEnabled == true (SovaPrefs.feedCarouselEnabled, default true — как в VK web)
 *   и фото > 1 → карусель [PostPhotoCarousel] по паттерну VK web attachmentCarousel
 *   (vkuiCarouselBase: свайп + полновысотные зоны нажатия 44dp по краям + тонкий
 *   шеврон 28dp с тенью + счётчик «n/N»; рендерится только применимая стрелка —
 *   на первом слайде только «вперёд», как в снапшоте vk.com).
 * - carouselEnabled == false → прежний вид: 1-2 фото — пейджер со счётчиком,
 *   3+ — сетка FlowRow (2 колонки до 4 фото, дальше 3).
 * - aspectRatio контейнера = ratio ТЕКУЩЕЙ страницы, coerceIn(0.5f, 2f); смена высоты
 *   плавная (animateContentSize).
 * - клик по фото → onPhotoClick(allUrls, index); клик по стрелке НЕ доходит до фото
 *   (стрелка — отдельный клик-таргет поверх пейджера).
 *
 * Флаг берётся вызывающей стороной из SovaPrefs snapshot'а (collectAsState с
 * Snapshot-initial — класс багов Fix #100/#110) и пробрасывается параметром, чтобы
 * компонент не зависел от SovaApp и был тестируемым.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PostPhotoGrid(
    photos: List<Attachment.Photo>,
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    carouselEnabled: Boolean = true,
) {
    val photosWithUrl = photos.mapNotNull { photo ->
        val size = PhotoSizes.best(photo.sizes)
        // NULL-ЯВНО: Gson-цепочка (sizes опциональны по схеме VK) — фото без
        // подходящего size пропускается (семантика прежнего FeedScreen PhotoGrid).
        val url = size?.url ?: return@mapNotNull null
        val ratio = if (size.height > 0) size.width.toFloat() / size.height.toFloat() else 1f
        Triple(photo, url, ratio)
    }
    if (photosWithUrl.isEmpty()) return
    val allUrls = photosWithUrl.map { it.second }

    // Карусель — для ЛЮБОГО числа фото >1 при включённой настройке.
    if (carouselEnabled && photosWithUrl.size > 1) {
        PostPhotoCarousel(
            photosWithUrl = photosWithUrl,
            allUrls = allUrls,
            onPhotoClick = onPhotoClick,
        )
        return
    }

    // 1-2 фото — пейджер со счётчиком N/M (как в ВК).
    if (photosWithUrl.size <= 2) {
        val pagerState = rememberPagerState(pageCount = { photosWithUrl.size })
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val (_, url, ratio) = photosWithUrl[page]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(0.5f, 2f))
                        .clickable { onPhotoClick(allUrls, page) },
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    AsyncImage(
                        model = url, contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            // Счётчик "N/M" в правом верхнем углу (если > 1 фото).
            if (photosWithUrl.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${photosWithUrl.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
    } else {
        // 3+ фото — сетка (FlowRow).
        val colCount = if (photosWithUrl.size <= 4) 2 else 3
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = colCount,
        ) {
            photosWithUrl.forEachIndexed { index, (_, url, ratio) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(0.5f, 2f))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoClick(allUrls, index) },
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    AsyncImage(
                        model = url, contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

/**
 * Карусель фото поста для ЛЮБОГО количества фото >1 (VK web photo_page_carousel /
 * attachmentCarousel). HorizontalPager + стрелки по краям (полновысотная зона нажатия
 * 44dp, arrowAreaFit — высота зоны = высота слайда) + тонкий шеврон 28dp с лёгкой
 * тенью БЕЗ кружка-фона (VK web рендерит голый шеврон) + счётчик «n/N» чипом у
 * верхнего края. Точек нет; на первом слайде монтируется только стрелка «вперёд»
 * (applicable-стрелка — ровно как в снапшоте vk.com, c45b1424).
 *
 * Вызывается гардом из [PostPhotoGrid] (всегда >1 страница). Клик по стрелке —
 * отдельный таргет, до фото не доходит; на крайних страницах соответствующая
 * стрелка скрыта (честный гвард).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostPhotoCarousel(
    photosWithUrl: List<Triple<Attachment.Photo, String, Float>>,
    allUrls: List<String>,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { photosWithUrl.size })
    val scope = rememberCoroutineScope()
    // Ratio текущей страницы (индекс всегда в диапазоне: pageCount == photosWithUrl.size).
    val currentRatio = photosWithUrl[pagerState.currentPage].third.coerceIn(0.5f, 2f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            // animateContentSize ДО aspectRatio: анимирует смену высоты,
            // которую производит aspectRatio текущей страницы.
            .animateContentSize()
            .aspectRatio(currentRatio),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val (_, url, _) = photosWithUrl[page]
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onPhotoClick(allUrls, page) },
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                AsyncImage(
                    model = url, contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // Счётчик "N/M" в правом верхнем углу — стиль прежнего пейджера сохранён.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "${pagerState.currentPage + 1}/${photosWithUrl.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        // Органы управления по паттерну VK web attachmentCarousel (снапшот Лента,
        // «Лента_ фотографии.html»: vkuiCarouselBase__arrow + vkuiScrollArrow__sizeS):
        // полновысотная зона нажатия по краю слайда (arrowAreaFit,
        // --arrow-area-height = высота слайда) + тонкий шеврон 12×16 по центру
        // по вертикали, БЕЗ кружка-фона (VK web рендерит голый шеврон);
        // рендерится только применимая стрелка (на первом слайде только «вперёд» —
        // в снапшоте ровно так: arrowStart отсутствует, есть только arrowEnd).
        // Стрелка — отдельный клик-таргет поверх пейджера: клик НЕ доходит до фото.
        if (pagerState.currentPage > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "Предыдущее фото",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(4.dp, CircleShape),
                )
            }
        }
        // Стрелка «вперёд» — по правому краю, та же VK web-геометрия.
        if (pagerState.currentPage < photosWithUrl.lastIndex) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Следующее фото",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(4.dp, CircleShape),
                )
            }
        }
    }
}
