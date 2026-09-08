package re.pinok.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.data.model.Video

/**
 * Fix #366 (#VIDEO-CAROUSEL-POSTS): карусель видео-вложений поста — общий
 * компонент по образцу [PostPhotoGrid] (волна 22). Семантика VK web
 * vkuiCarouselBase — свайп + полновысотные зоны нажатия 44dp по краям + тонкий
 * шеврон 28dp с тенью + счётчик «n/N» чипом у верхнего края; рендерится только
 * применимая стрелка (на первом слайде — только «вперёд»).
 *
 * - carouselEnabled == true (тот же SovaPrefs.feedCarouselEnabled, что и у
 *   PostPhotoGrid) и видео > 1 → HorizontalPager-карусель.
 * - иначе (одно видео или настройка выключена) → прежний вид: слайды 16:9
 *   стопкой в стилистике VideoThumbnail (тёмный фон превью, play-круг, чип
 *   длительности), плюс чип «Клип» для video.isClip.
 * - клик по слайду → onVideoClick(video); клик по стрелке НЕ доходит до слайда
 *   (стрелка — отдельный клик-таргет поверх пейджера).
 *
 * Флаг берётся вызывающей стороной из того же источника, что и для
 * PostPhotoGrid (collectAsState с Snapshot-initial — класс багов Fix #100/#110)
 * и пробрасывается параметром: компонент не зависит от SovaApp.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostVideoCarousel(
    videos: List<Video>,
    carouselEnabled: Boolean,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) return
    if (carouselEnabled && videos.size > 1) {
        PostVideoCarouselPager(
            videos = videos,
            onVideoClick = onVideoClick,
            modifier = modifier,
        )
    } else {
        // Одно видео (или настройка выключена) — прежний вид стопкой 16:9.
        Column(modifier = modifier.fillMaxWidth()) {
            videos.forEach { v ->
                VideoSlide(
                    video = v,
                    onClick = { onVideoClick(v) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

/**
 * Pager-карусель (вызывается гардом из [PostVideoCarousel], всегда >1 страница).
 * Геометрия 1:1 из PostPhotoCarousel: контейнер 12/4dp + clip 8dp +
 * animateContentSize + aspectRatio слайда (для видео — фикс 16:9, как у
 * VideoThumbnail), счётчик «n/N» чипом TopEnd, полновысотные стрелочные зоны
 * 44dp по краям с голым шевроном 28dp (тень вместо кружка-фона).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostVideoCarouselPager(
    videos: List<Video>,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { videos.size })
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            // animateContentSize — как в PostPhotoCarousel (плавная смена высоты).
            .animateContentSize()
            .aspectRatio(16f / 9f),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val v = videos[page]
            VideoSlide(
                video = v,
                onClick = { onVideoClick(v) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Счётчик "N/M" в правом верхнем углу — стиль PostPhotoCarousel.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "${pagerState.currentPage + 1}/${videos.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        // Стрелки — VK web vkuiCarouselBase: полновысотная зона нажатия 44dp по
        // краю слайда + голый шеврон 28dp с тенью; рендерится только применимая
        // стрелка. Стрелка — отдельный клик-таргет поверх пейджера: клик НЕ
        // доходит до слайда.
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
                    contentDescription = "Предыдущее видео",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(4.dp, CircleShape),
                )
            }
        }
        // Стрелка «вперёд» — по правому краю, та же VK web-геометрия.
        if (pagerState.currentPage < videos.lastIndex) {
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
                    contentDescription = "Следующее видео",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(4.dp, CircleShape),
                )
            }
        }
    }
}

/**
 * Слайд видео: превью video.thumbUrl (coil-стиль VideoThumbnail — тёмный фон
 * surfaceVariant + Crop), play-круг по центру, чип длительности «m:ss» внизу
 * справа (формат VideoThumbnail), чип «Клип» вверху слева для video.isClip.
 * Весь слайд кликабелен → onClick.
 */
@Composable
private fun VideoSlide(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbUrl = video.thumbUrl
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (thumbUrl != null) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        if (video.duration > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "${video.duration / 60}:${"%02d".format(video.duration % 60)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        }
        if (video.isClip) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "Клип",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
