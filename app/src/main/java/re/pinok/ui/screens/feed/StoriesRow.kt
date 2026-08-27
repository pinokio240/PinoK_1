package re.pinok.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import re.pinok.util.AppLog
import re.pinok.SovaApp
import re.pinok.data.model.StoryGroup
import re.pinok.ui.navigation.StoriesHolder

/**
 * Горизонтальный ряд историй (Stories), как в VK —
 * аватарки с градиентным кольцом для непросмотренных.
 * Тап по аватарке → StoryViewerScreen (vkitStoriesGallery).
 *
 * VK_IMPORT_API.MD §15.3: блок stories ВСЕГДА виден в ленте VK,
 * даже когда нет историй от друзей — отображается «Моя история»
 * (creator-button с «+»), как в `data-testid="stories_creator"`.
 * См. скриншот 2026-07-12_17-52-06.png (оригинальный VK).
 *
 * Fix #52-B: истории обновляются при изменениях в сообществах.
 * Раньше `LaunchedEffect(Unit)` грузил истории один раз — при возврате
 * из CommunityScreen (после subscribe/unsubscribe) истории не обновлялись.
 * Теперь: StoriesHolder.dirtyKey инкрементируется при любом событии
 * требующем обновление → LaunchedEffect(dirtyKey) перезагружает.
 * Кэш в StoriesHolder переживает навигацию (как FeedDataHolder).
 *
 * Fix #98: вся панель уменьшена на ~10% (пропорционально ×0.9):
 * аватарки 64→58, 68→61, 58→52, иконка «+» 28→25, шрифт подписи 11→10sp,
 * contentPadding 12→11 / 8→7, spacedBy 12→11. Высота панели ~102dp → ~92dp.
 *
 * Fix #99: дополнительное уменьшение ещё на ~5% (×0.95 от текущих):
 * аватарки 58→55, 61→58, 52→49, иконка «+» 25→24,
 * contentPadding 11→10 / 7→6, spacedBy 11→10. Высота панели ~92dp → ~87dp.
 * Шрифт подписи 10sp оставлен (9.5sp нецелое, 10sp минимально читаемый).
 * Суммарно от оригинала: ~102dp → ~87dp (−14.7%).
 */
@Composable
fun StoriesRow(
    modifier: Modifier = Modifier,
    onStoryClick: (List<StoryGroup>, Int) -> Unit = { _, _ -> },
) {
    val app = SovaApp.get()
    // Audit #40: удалён rememberCoroutineScope — не используется после упрощения LaunchedEffect.
    var storyGroups by remember { mutableStateOf<List<StoryGroup>>(StoriesHolder.storyGroups ?: emptyList()) }
    var loading by remember { mutableStateOf(StoriesHolder.storyGroups == null) }

    // Fix #52-B: перезагрузка историй при изменении dirtyKey.
    // dirtyKey инкрементируется: (1) SovaNavHost при возврате на Feed из
    // другого экрана, (2) CommunityScreen после groupsJoin/groupsLeave,
    // (3) StoriesHolder.clear() при pull-to-refresh ленты.
    // Первая загрузка: dirtyKey=0 → срабатывает сразу. Последующие: только
    // при явном markDirty() — не на каждый recomposition.
    //
    // Fix #88: StoriesHolder.dirtyKey теперь StateFlow. Раньше @Volatile var
    // НЕ триггерил recomposition → LaunchedEffect(dirtyKey) не перезапускался
    // при markDirty(). Теперь collectAsState() подписан на поток.
    val dirtyKey by StoriesHolder.dirtyKey.collectAsState()
    LaunchedEffect(dirtyKey) {
        try {
            AppLog.d("StoriesRow", "Loading stories (dirtyKey=$dirtyKey)")
            val groups = app.apiClient.storiesGet(count = 20)
            storyGroups = groups
            StoriesHolder.snapshot(groups)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Fix #252: корректная отмена (пользователь ушёл со экрана)
            throw e
        } catch (e: Exception) {
            AppLog.e("StoriesRow", "Failed to load stories (dirtyKey=$dirtyKey)", e)
        } finally {
            loading = false
        }
    }

    if (loading && storyGroups.isEmpty()) {
        // Скелетон — серые кружки.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(6) {
                    Box(
                        modifier = Modifier
                            .size(55.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
        return
    }

    // ВАЖНО: даже когда storyGroups.isEmpty() (нет историй от друзей / ошибка
    // загрузки), ряд НЕ скрывается — отображается «Моя история» (creator-button),
    // точно как в оригинальном VK. См. user request 2026-07-12.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Кнопка "Моя история" — синий кружок с "+".
            item(key = "my_story") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(55.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(55.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Создать историю",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "История",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        // Истории от пользователей/сообществ.
        items(storyGroups, key = { it.ownerId }) { group ->
            val ringColor = if (!group.isSeen) {
                // Градиент ВК — голубой → фиолетовый.
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0077FF),
                        Color(0xFF7B61FF),
                    ),
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
            val groupIndex = storyGroups.indexOf(group)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(55.dp)
                    .clickable { onStoryClick(storyGroups, groupIndex) },
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(ringColor)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (group.photo100 != null) {
                        AsyncImage(
                            model = group.photo100,
                            contentDescription = group.name ?: "",
                            modifier = Modifier
                                .size(49.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(49.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (group.name ?: "?").take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = group.name ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    }
}