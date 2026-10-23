package re.pinok.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.data.model.PhotoItem
import re.pinok.media.ImageSaver
import re.pinok.util.toRelativeTime

/**
 * Sprint 2, P1-1 (#88): Полноэкранный просмотрщик фото с pinch-to-zoom и swipe.
 *
 * Особенности:
 *  — HorizontalPager: свайп между фото (как галерея VK/Android).
 *  — Pinch-to-zoom через detectTransformGestures (масштаб + pan в zoom-режиме).
 *  — Двойной тап: переключение 1x ↔ 2.5x (как Google Photos).
 *  — Одиночный тап: показать/скрыть top bar (counter + close).
 *  — Чёрный фон во весь экран, поверх всего приложения.
 *
 * Реализован как [Dialog] с [DialogProperties.usePlatformDefaultWidth] = false,
 * чтобы контент занимал весь экран, а не дефолтный dialog window. Альтернатива —
 * Box-overlay — но тогда требуется, чтобы каждый caller оборачивал свой экран
 * в Box, что хрупко. Dialog сам по себе отдельное окно → гарантированно поверх.
 *
 * Не требует новых зависимостей — используется только Foundation (Pager, gestures).
 *
 * @param photos   Список URL для отображения.
 * @param initial  Индекс стартового фото (какой thumbnail нажали).
 * @param onDismiss Закрытие просмотрщика.
 * @param onShare      Тап «Поделиться» — callback получает URL текущего фото.
 *                     Default {} — реализация на стороне вызова (Intent.ACTION_SEND и т.п.).
 * @param onSendToChat Тап «Отправить» — callback получает URL. Default {}.
 * @param onBookmark   Тап «В закладки» — callback получает URL. Default {}.
 * @param onFavorite   Тап «В избранное» — callback получает URL. Default {}.
 */
@Composable
fun PhotoViewer(
    photos: List<String>,
    initial: Int = 0,
    onDismiss: () -> Unit,
    // PHOTO-MENU (PHOTO-MENU-ALBUMS): контекстное «⋮»-меню просмотрщика.
    // Все коллбеки опциональны — дефолт {}, вызов решает что делать (share intent,
    // ShareToChatSheet, fave.add …). URL текущего фото передаётся как String —
    // caller при необходимости мапит URL → (ownerId, photoId) через свой контекст.
    onShare: (String) -> Unit = {},
    onSendToChat: (String) -> Unit = {},
    onBookmark: (String) -> Unit = {},
    onFavorite: (String) -> Unit = {},
) {
    PhotoViewerIntern(
        photos = photos,
        captions = null,
        initial = initial,
        onDismiss = onDismiss,
        onShare = onShare,
        onSendToChat = onSendToChat,
        onBookmark = onBookmark,
        onFavorite = onFavorite,
    )
}

/**
 * Перегрузка просмотрщика по списку [PhotoItem] (#PHOTO-FEED 2026-10-03, Этап 3):
 * под каждым фото показываются подпись (text) и дата (date → toRelativeTime)
 * при controlsVisible. Обратно совместим с базовым [PhotoViewer] по [List]<String>.
 */
@Composable
fun PhotoViewerPhotoItems(
    photos: List<PhotoItem>,
    initial: Int = 0,
    onDismiss: () -> Unit,
    // PHOTO-MENU (PHOTO-MENU-ALBUMS): те же 4 коллбека «⋮»-меню — проброс в Intern.
    onShare: (String) -> Unit = {},
    onSendToChat: (String) -> Unit = {},
    onBookmark: (String) -> Unit = {},
    onFavorite: (String) -> Unit = {},
) {
    val urls = photos.mapNotNull { it.largestUrl }
    // Подписи по индексу URL (не по индексу PhotoItem — допускаем null-URL):
    // для каждого непустого URL берём text/дату объединённой строкой.
    val captions = urls.map { url ->
        photos.firstOrNull { it.largestUrl == url }
            ?.let { photo ->
                buildString {
                    val text = photo.text
                    if (!text.isNullOrBlank()) append(text)
                    if (photo.date > 0) {
                        if (text.isNullOrBlank()) append(photo.date.toRelativeTime())
                        else {
                            if (isNotEmpty()) append("\n")
                            append(photo.date.toRelativeTime())
                        }
                    }
                }.takeIf { it.isNotBlank() }
            }
    }
    PhotoViewerIntern(
        photos = urls,
        captions = captions,
        initial = initial,
        onDismiss = onDismiss,
        onShare = onShare,
        onSendToChat = onSendToChat,
        onBookmark = onBookmark,
        onFavorite = onFavorite,
    )
}

@Composable
private fun PhotoViewerIntern(
    photos: List<String>,
    captions: List<String?>?,
    initial: Int,
    onDismiss: () -> Unit,
    // PHOTO-MENU (PHOTO-MENU-ALBUMS): 4 коллбека «⋮»-меню — пробрасываются из
    // PhotoViewer / PhotoViewerPhotoItems. Default {} — вызовы без меню совместимы.
    onShare: (String) -> Unit = {},
    onSendToChat: (String) -> Unit = {},
    onBookmark: (String) -> Unit = {},
    onFavorite: (String) -> Unit = {},
) {
    if (photos.isEmpty()) {
        onDismiss()
        return
    }
    val safeInitial = initial.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitial) { photos.size }
    var controlsVisible by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    // PHOTO-MENU: развёрнуто ли «⋮»-меню. Видно только когда controlsVisible.
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomablePhoto(
                    url = photos[page],
                    onTap = { controlsVisible = !controlsVisible },
                )
            }

            // Top bar: back button + counter "1 / 5"
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .statusBarsPadding(),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Закрыть",
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 14.dp),
                    )
                    // PHOTO-MENU (PHOTO-MENU-ALBUMS): правый верхний угол теперь
                    // содержит ДВЕ кнопки — «Сохранить» (download) и «⋮» (more).
                    // Row у правого края: [Save] [⋮ → DropdownMenu].
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Кнопка «Сохранить оригинал».
                        IconButton(
                            onClick = {
                                val url = photos[pagerState.currentPage]
                                scope.launch {
                                    if (saving) return@launch
                                    saving = true
                                    val result = ImageSaver.save(context, ImageSaver.toMaxSize(url))
                                    val msg = when (result) {
                                        is ImageSaver.Result.Ok -> "Сохранено в галерею"
                                        is ImageSaver.Result.Err -> "Ошибка: ${result.message}"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    saving = false
                                }
                            },
                            enabled = !saving,
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Сохранить",
                                tint = Color.White,
                            )
                        }
                        // «⋮» — контекстное меню фото (share/send/bookmark/favorite).
                        // Box-обёртка нужна, чтобы DropdownMenu привязался к IconButton.
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Ещё",
                                    tint = Color.White,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                val currentUrl = photos[pagerState.currentPage]
                                DropdownMenuItem(
                                    text = { Text("Поделиться") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Share, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onShare(currentUrl)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Отправить") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onSendToChat(currentUrl)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("В закладки") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.BookmarkBorder, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onBookmark(currentUrl)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("В избранное") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onFavorite(currentUrl)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // #PHOTO-FEED Этап 3: подпись + дата внизу при controlsVisible.
            val caption = captions?.getOrNull(pagerState.currentPage)
            if (!caption.isNullOrBlank()) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = caption,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Одиночное фото с pinch-to-zoom и double-tap-to-zoom.
 *
 * Реализация: graphicsLayer применяет scale + translation к AsyncImage.
 * detectTransformGestures обновляет offset/scale только когда scale > 1
 * (при scale == 1 pan блокируется — жест уходит в HorizontalPager для swipe).
 *
 * Двойной тап переключает 1x ↔ 2.5x.
 */
@Composable
private fun ZoomablePhoto(
    url: String,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(url) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Разрешаем pan только когда уже зазумлены (иначе конфликт со swipe).
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    if (newScale > 1f) {
                        scale = newScale
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            // Сброс до 1x.
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            // Зум до 2.5x.
                            scale = 2.5f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
            contentScale = ContentScale.Fit,
        )
    }
}
