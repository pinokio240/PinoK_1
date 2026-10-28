package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.CardGiftcard
import com.google.gson.JsonObject
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import re.pinok.SovaApp
import re.pinok.data.model.Album
import re.pinok.data.model.DocFile
import re.pinok.data.model.GiftItem
import re.pinok.data.model.PhotoItem
import re.pinok.data.model.Track
import re.pinok.data.model.Video
import re.pinok.data.model.VideoAlbum
import re.pinok.util.AppLog
import re.pinok.util.toDurationString

/**
 * #ATTACH-UNIFY: табы пикера библиотеки VK. Раньше таб кодировался Int
 * (0=Музыка, 1=Видео, 2=Подарки) — с добавлением «Фото»/«Документы»
 * Int-кодирование стало хрупким (скрытые табы ломали индексы), поэтому
 * все вызывающие стороны (чат/лента/комменты/пост — всё в зоне волны)
 * переведены на enum.
 */
enum class AttachmentPickerTab {
    /** audio.get — треки пользователя. */
    Music,

    /** video.get — видео пользователя. */
    Video,

    /** gifts.getCatalog — каталог подарков (только ЛС). */
    Gifts,

    /** #ATTACH-UNIFY: photos.getAll — фото пользователя (attach без upload). */
    Photos,

    /** #ATTACH-UNIFY: docs.get — документы пользователя (attach без upload). */
    Docs,
}

/**
 * P5.3: Bottom sheet для выбора вложений из библиотеки VK —
 * Музыка / Видео / Подарки + #ATTACH-UNIFY: Фото / Документы.
 *
 * Заменяет простое меню «Фото/Файл» на расширенное, как в m.vk.ru:
 * пользователь выбирает существующий контент из своей библиотеки VK и
 * отправляет его как attachment в диалог.
 *
 * #ATTACH-UNIFY: табы «Фото» (photosGetAll → photo{owner}_{id}[_key]) и
 * «Документы» (docsGet → doc{owner}_{id}[_key]) собирают attachment-строку
 * через общий хелпер [buildVkAttachment] и отдают её вызывающей стороне
 * через onPickPhotoAttachment/onPickDocAttachment (спека §4, п.4).
 *
 * // MULTI-SELECT-ATTACH: по умолчанию picker НЕ закрывается после первого
 * выбора. Тап по элементу toggles выделение (check-overlay), а над табами
 * появляется кнопка «Готово (N)» — по тапу все выбранные элементы уходят
 * наружу через соответствующие onPickXxx колбеки (по одному вызову на
 * каждый pick), после чего picker закрывается (onDismiss). Поведение
 * совместимо со старыми вызовами: если передать `multiSelect = false`,
 * picker вернётся к legacy-режиму «pick → dismiss» (один выбор за сеанс).
 *
 * @param initialTab таб, который открыт при показе листа
 * @param multiSelect true (по умолчанию) — накопление выборов с превью;
 *                    false — legacy: pick → немедленный onDismiss.
 * @param showGiftTab показывать таб «Подарки» (по умолчанию true — совместимость;
 *                    в комментариях/постах подарки недоступны — таб не рисуется,
 *                    чтобы не оставлять dead-пункт)
 * @param showPhotoTab показывать таб «Фото» (по умолчанию false)
 * @param showDocsTab показывать таб «Документы» (по умолчанию false)
 * @param onPickAudio callback при выборе трека
 * @param onPickVideo callback при выборе видео
 * @param onPickGift  callback при выборе подарка
 * @param onPickPhotoAttachment callback при выборе фото из VK — готовая attachment-строка
 * @param onPickDocAttachment callback при выборе документа из VK — (attachment, заголовок)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    initialTab: AttachmentPickerTab = AttachmentPickerTab.Music,
    multiSelect: Boolean = true,
    showGiftTab: Boolean = true,
    showPhotoTab: Boolean = false,
    showDocsTab: Boolean = false,
    onPickAudio: (Track) -> Unit = {},
    onPickVideo: (Video) -> Unit = {},
    onPickGift: (GiftItem) -> Unit = {},
    onPickPhotoAttachment: (attachment: String, thumb: String?) -> Unit = { _, _ -> },
    onPickDocAttachment: (attachment: String, title: String) -> Unit = { _, _ -> },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val app = SovaApp.get()
    // Динамический список табов: недоступные (не включённые флагом) не рисуются —
    // никаких dead-табов (правило no-stub #ATTACH-UNIFY).
    val tabs = remember(showGiftTab, showPhotoTab, showDocsTab) {
        buildList {
            add(AttachmentPickerTab.Music)
            add(AttachmentPickerTab.Video)
            if (showGiftTab) add(AttachmentPickerTab.Gifts)
            if (showPhotoTab) add(AttachmentPickerTab.Photos)
            if (showDocsTab) add(AttachmentPickerTab.Docs)
        }
    }
    // Если вызывающая сторона попросила скрытый таб (initialTab отключён) —
    // честно открываем «Музыку», а не осиротевший таб без содержимого.
    val startTab = when (initialTab) {
        AttachmentPickerTab.Gifts -> if (showGiftTab) initialTab else AttachmentPickerTab.Music
        AttachmentPickerTab.Photos -> if (showPhotoTab) initialTab else AttachmentPickerTab.Music
        AttachmentPickerTab.Docs -> if (showDocsTab) initialTab else AttachmentPickerTab.Music
        else -> initialTab
    }
    var activeTab by remember { mutableStateOf(startTab) }

    // MULTI-SELECT-ATTACH: накопленные выборы до тапа «Готово».
    // Гетерогенный список — типы вкладок разные (Track/Video/GiftItem/…),
    // оборачиваем в sealed PendingPick с уникальным ключом (dedup по key).
    val selectedItems = remember { mutableStateListOf<PendingPick>() }
    val selectedKeys: Set<String> = selectedItems.map { it.key }.toSet()

    fun toggle(item: PendingPick) {
        val idx = selectedItems.indexOfFirst { it.key == item.key }
        if (idx >= 0) selectedItems.removeAt(idx) else selectedItems.add(item)
    }

    // MULTI-SELECT-ATTACH: «Готово (N)» — flush всех накопленных picks наружу
    // через соответствующие onPickXxx колбеки (по одному вызову на каждый
    // pick), затем закрытие picker'а. Сохраняем порядок выбора (mutableList).
    fun flushAndDismiss() {
        selectedItems.forEach { item ->
            when (item) {
                is PendingPick.Audio -> onPickAudio(item.track)
                is PendingPick.Video -> onPickVideo(item.video)
                is PendingPick.Gift -> onPickGift(item.gift)
                is PendingPick.Photo -> onPickPhotoAttachment(item.attachment, item.thumb)
                is PendingPick.Doc -> onPickDocAttachment(item.attachment, item.title)
            }
        }
        selectedItems.clear()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.85f)
        ) {
            // MULTI-SELECT-ATTACH: верхняя панель. В multi-режиме — заголовок
            // «Выбрано: N» + кнопка «Готово (N)» (enabled только при N>0). В
            // legacy-режиме — прежний заголовок «Прикрепить».
            if (multiSelect) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedItems.isEmpty()) "Выберите вложения"
                        else "Выбрано: ${selectedItems.size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        onClick = { flushAndDismiss() },
                        enabled = selectedItems.isNotEmpty(),
                    ) {
                        Text(
                            text = if (selectedItems.isEmpty()) "Готово"
                            else "Готово (${selectedItems.size})",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Text(
                    text = "Прикрепить",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            PrimaryTabRow(selectedTabIndex = tabs.indexOf(activeTab).coerceAtLeast(0)) {
                // forEach (не items!) — PrimaryTabRow не Lazy-контейнер (Fix #284).
                for (tab in tabs) {
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = { Text(tabTitle(tab)) },
                        icon = { Icon(tabIcon(tab), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            when (activeTab) {
                AttachmentPickerTab.Music -> AudioPickerTab(
                    app = app,
                    onPick = { track ->
                        // MULTI-SELECT-ATTACH: tap toggles selection; в legacy — pick+dismiss.
                        if (multiSelect) {
                            toggle(PendingPick.Audio(track))
                        } else {
                            onPickAudio(track); onDismiss()
                        }
                    },
                    multiSelect = multiSelect,
                    selectedKeys = selectedKeys,
                )
                AttachmentPickerTab.Video -> VideoPickerTab(
                    app = app,
                    onPick = { video ->
                        if (multiSelect) {
                            toggle(PendingPick.Video(video))
                        } else {
                            onPickVideo(video); onDismiss()
                        }
                    },
                    multiSelect = multiSelect,
                    selectedKeys = selectedKeys,
                )
                AttachmentPickerTab.Gifts -> GiftPickerTab(
                    app = app,
                    onPick = { gift ->
                        if (multiSelect) {
                            toggle(PendingPick.Gift(gift))
                        } else {
                            onPickGift(gift); onDismiss()
                        }
                    },
                    multiSelect = multiSelect,
                    selectedKeys = selectedKeys,
                )
                AttachmentPickerTab.Photos -> PhotoPickerTab(
                    app = app,
                    onPick = { att, thumb ->
                        if (multiSelect) {
                            toggle(PendingPick.Photo(att, thumb))
                        } else {
                            onPickPhotoAttachment(att, thumb); onDismiss()
                        }
                    },
                    multiSelect = multiSelect,
                    selectedKeys = selectedKeys,
                )
                AttachmentPickerTab.Docs -> DocsPickerTab(
                    app = app,
                    onPick = { att, title ->
                        if (multiSelect) {
                            toggle(PendingPick.Doc(att, title))
                        } else {
                            onPickDocAttachment(att, title); onDismiss()
                        }
                    },
                    multiSelect = multiSelect,
                    selectedKeys = selectedKeys,
                )
            }
        }
    }
}

/**
 * // MULTI-SELECT-ATTACH: гетерогенный wrapper выбранных в picker'е элементов.
 * Каждый вариант несёт исходный объект VK (Track/Video/GiftItem/…) и ключ
 * дедупликации — id объекта (type{owner}_{id}[_key] для photo/doc).
 */
private sealed interface PendingPick {
    val key: String
    data class Audio(val track: Track) : PendingPick {
        override val key: String = "audio_${track.ownerId}_${track.id}"
    }
    // #MULTI-SELECT-FIX: тип поля — re.pinok.data.model.Video (FQN), а НЕ
    //   голый `Video`: внутри sealed PendingPick голое имя `Video` резолвится
    //   в сам вложенный PendingPick.Video (рекурсивный тип) → onPickVideo(
    //   item.video) получал PendingPick.Video вместо model.Video (TS-mismatch
    //   на :199), toggle(PendingPick.Video(video)) ждал PendingPick.Video, а
    //   получал model.Video (:286), и `video.ownerId`/`video.id` были
    //   unresolved (:346). FQN разрывает теневание.
    data class Video(val video: re.pinok.data.model.Video) : PendingPick {
        override val key: String = "video_${video.ownerId}_${video.id}"
    }
    data class Gift(val gift: GiftItem) : PendingPick {
        override val key: String = "gift_${gift.id}"
    }
    data class Photo(val attachment: String, val thumb: String?) : PendingPick {
        override val key: String = "photo_$attachment"
    }
    data class Doc(val attachment: String, val title: String) : PendingPick {
        override val key: String = "doc_$attachment"
    }
}

/** Заголовок таба (switch-выражение вместо when по Int — #ATTACH-UNIFY). */
private fun tabTitle(tab: AttachmentPickerTab): String = when (tab) {
    AttachmentPickerTab.Music -> "Музыка"
    AttachmentPickerTab.Video -> "Видео"
    AttachmentPickerTab.Gifts -> "Подарки"
    AttachmentPickerTab.Photos -> "Фото"
    AttachmentPickerTab.Docs -> "Документы"
}

/** Иконка таба. */
private fun tabIcon(tab: AttachmentPickerTab) = when (tab) {
    AttachmentPickerTab.Music -> Icons.Outlined.MusicNote
    AttachmentPickerTab.Video -> Icons.Outlined.PlayCircle
    AttachmentPickerTab.Gifts -> Icons.Outlined.CardGiftcard
    AttachmentPickerTab.Photos -> Icons.Outlined.Image
    AttachmentPickerTab.Docs -> Icons.Outlined.Description
}

// ============================================================================
//  Tab: Музыка — список треков из audio.get библиотеки пользователя.
//  #AUDIO-PAGING-ALL (волна 38): была одна страница на 50 треков — при
//  библиотеке больше 50 до конца списка было не доскроллить. Теперь
//  серверная пагинация по offset (audioGetWithCount) с автодогрузкой
//  по скроллу до конца; стоп по неполной странице (VK total не доверяем).
// ============================================================================

@Composable
private fun AudioPickerTab(
    app: SovaApp,
    onPick: (Track) -> Unit,
    // MULTI-SELECT-ATTACH: overlay с галочкой + выделение цветом.
    multiSelect: Boolean = false,
    selectedKeys: Set<String> = emptySet(),
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverOffset by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (loadingMore || loading || !hasMore) return
        scope.launch {
            loadingMore = true
            try {
                val (total, page) = app.apiClient.audioGetWithCount(count = 50, offset = serverOffset)
                serverOffset += page.size
                val fresh = page
                    .filter { it.id > 0L && it.ownerId != 0L }
                    .filter { nv -> tracks.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                tracks = (tracks + fresh).distinctBy { "${it.ownerId}_${it.id}" }
                // #AUDIO-PAGING-HOLE (волна 41): непустая страница ≠ конец;
                // сбой (total=-1) — сохраняем hasMore для повтора по скроллу.
                hasMore = page.isNotEmpty() || total < 0
            } catch (e: Exception) {
                AppLog.e("AudioPicker", "load more failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val (total, page) = app.apiClient.audioGetWithCount(count = 50, offset = 0)
            tracks = page.filter { it.id > 0L && it.ownerId != 0L }
                .distinctBy { "${it.ownerId}_${it.id}" }
            serverOffset = page.size
            // #AUDIO-PAGING-HOLE (волна 41): непустая страница ≠ конец списка.
            hasMore = page.isNotEmpty() || total < 0
        } catch (e: Exception) {
            AppLog.e("AudioPicker", "load failed", e)
            error = e.message
        } finally {
            loading = false
        }
    }

    // Автодогрузка при скролле к концу списка.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 5) total else -1
        }
            .distinctUntilChanged()
            .collect { if (it > 0) loadMore() }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Не удалось загрузить: $error", color = MaterialTheme.colorScheme.error)
        }
        tracks.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Библиотека пуста", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(tracks, key = { "${it.ownerId}_${it.id}" }) { track ->
                // MULTI-SELECT-ATTACH: ключ для дедупа совпадает с PendingPick.Audio.key.
                val key = "audio_${track.ownerId}_${track.id}"
                AudioTrackRow(
                    track = track,
                    onClick = { onPick(track) },
                    multiSelect = multiSelect,
                    isSelected = selectedKeys.contains(key),
                )
            }
            if (hasMore) {
                item(key = "audio_picker_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioTrackRow(
    track: Track,
    onClick: () -> Unit,
    // MULTI-SELECT-ATTACH: чекбокс-индикатор справа (весит в multiSelect-режиме).
    multiSelect: Boolean = false,
    isSelected: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // MULTI-SELECT-ATTACH: подсветка выбранной строки (фон).
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail (album cover) или иконка-заглушка
        if (track.albumThumb != null) {
            AsyncImage(
                model = track.albumThumb,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = track.duration.toDurationString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        // MULTI-SELECT-ATTACH: чекбокс-индикатор справа от длительности.
        if (multiSelect) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle
                else Icons.Outlined.CheckCircle,
                contentDescription = if (isSelected) "Выбрано" else "Не выбрано",
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ============================================================================
//  Tab: Видео — список видео из video.get библиотеки пользователя.
//  // ATTACH-PICKER-ALBUMS: серверная пагинация по offset (videoGetWithCount)
//  с автодогрузкой при скролле к концу (snapshotFlow — паттерн AudioPickerTab).
//  Стоп по неполной странице (page.size < 50) или total < 0 (сбой VK total).
// ============================================================================

@Composable
private fun VideoPickerTab(
    app: SovaApp,
    onPick: (Video) -> Unit,
    // MULTI-SELECT-ATTACH: overlay с галочкой + выделение цветом.
    multiSelect: Boolean = false,
    selectedKeys: Set<String> = emptySet(),
) {
    // ATTACH-PICKER-ALBUMS: видеоальбомы + пагинация по образцу PhotoPickerTab.
    var albums by remember { mutableStateOf<List<VideoAlbum>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(true) }
    var albumsError by remember { mutableStateOf<String?>(null) }
    // null = «Все видео» (videoGetWithCount без albumId); non-null = открыт
    // конкретный альбом (videoGetWithCount(albumId)). Тап по карточке альбома
    // меняет openedAlbum → LaunchedEffect(openedAlbum) перезагружает список.
    var openedAlbum by remember { mutableStateOf<VideoAlbum?>(null) }

    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // ATTACH-PICKER-ALBUMS: серверная пагинация (videoGetWithCount).
    var serverOffset by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Загрузка альбомов — один раз при входе в таб (video.getAlbums).
    // Не блокирует видео: videoGetWithCount грузится параллельно в эффекте ниже.
    LaunchedEffect(Unit) {
        try {
            val myId = app.tokenStorage.load()?.userId ?: 0L
            val (total, list) = app.apiClient.videoGetAlbums(ownerId = myId)
            albums = list
        } catch (e: Exception) {
            AppLog.e("VideoPicker", "albums load failed", e)
            albumsError = e.message
        } finally {
            albumsLoading = false
        }
    }

    fun loadMore() {
        if (loadingMore || loading || !hasMore) return
        // Capture в локальный val — открытие другого альбома во время загрузки
        // не должно подмешать «чужую» страницу в текущий список.
        val currentAlbum = openedAlbum
        scope.launch {
            loadingMore = true
            try {
                // #MULTI-SELECT-FIX: videoGetWithCount возвращает Pair<List<Video>, Int>
                //   (page FIRST, total SECOND) — наоборот к audioGetWithCount
                //   Pair<Int, List<Track>>. Было `val (total, page)` → page: Int,
                //   page.size/filter/isNotEmpty unresolved (compiler искал
                //   Modifier.size). Меняем порядок деструктуризации.
                // ATTACH-PICKER-ALBUMS: albumId для пагинации внутри альбома.
                val (page, total) = app.apiClient.videoGetWithCount(
                    count = 50,
                    offset = serverOffset,
                    albumId = currentAlbum?.id,
                )
                // REVIEWER-3: race-guard. currentAlbum фиксирует albumId для
                // запроса, но НЕ защищает read/write videos — за время сетевого
                // вызова пользователь мог тапнуть другой альбом → LaunchedEffect
                // (openedAlbum) уже сбросил videos/serverOffset/hasMore. Без этой
                // проверки свежая страница чужого albumId примешалась бы в новый
                // список (BUILDER-комментарий про «не подмешать» иначе ложен).
                if (openedAlbum != currentAlbum) return@launch
                serverOffset += page.size
                val fresh = page
                    .filter { it.id > 0L && it.ownerId != 0L }
                    .filter { nv -> videos.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                videos = (videos + fresh).distinctBy { "${it.ownerId}_${it.id}" }
                // #AUDIO-PAGING-HOLE (волна 41): непустая страница ≠ конец;
                // сбой (total=-1) — сохраняем hasMore для повтора по скроллу.
                hasMore = page.isNotEmpty() || total < 0
            } catch (e: Exception) {
                AppLog.e("VideoPicker", "load more failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // Загрузка видео: videoGetWithCount без albumId («Все видео») или с albumId
    // (конкретный альбом). Перезапускается при смене openedAlbum — обнуляем
    // offset/hasMore/videos. Параллелен загрузке альбомов выше.
    LaunchedEffect(openedAlbum) {
        loading = true
        error = null
        serverOffset = 0
        hasMore = false
        videos = emptyList()
        try {
            // #MULTI-SELECT-FIX: Pair<List<Video>, Int> — page first (см. loadMore).
            val (page, total) = app.apiClient.videoGetWithCount(
                count = 50,
                offset = 0,
                albumId = openedAlbum?.id,
            )
            videos = page.filter { it.id > 0L && it.ownerId != 0L }
                .distinctBy { "${it.ownerId}_${it.id}" }
            serverOffset = page.size
            // #AUDIO-PAGING-HOLE (волна 41): неполная страница ≠ конец списка.
            hasMore = page.isNotEmpty() || total < 0
        } catch (e: Exception) {
            AppLog.e("VideoPicker", "load failed", e)
            error = e.message
        } finally {
            loading = false
        }
    }

    // ATTACH-PICKER-ALBUMS: автодогрузка при скролле к концу списка.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 5) total else -1
        }
            .distinctUntilChanged()
            .collect { if (it > 0) loadMore() }
    }

    when {
        // Первичная загрузка (нет ещё ни видео, ни альбомов) — полный экран-заглушка.
        loading && videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null && videos.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Не удалось загрузить: $error", color = MaterialTheme.colorScheme.error)
        }
        // Есть альбомы или открыт альбом → показываем LazyColumn даже при пустых
        // videos (чтобы был виден ряд альбомов / back-row для возврата).
        videos.isEmpty() && openedAlbum == null && albums.isEmpty() && !albumsLoading -> Box(
            Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Нет видео", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            // Back-row если открыт альбом (возврат к «Все видео»).
            if (openedAlbum != null) {
                item(key = "video_album_back") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openedAlbum = null }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            openedAlbum?.title?.ifBlank { "Без названия" } ?: "Альбом",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            // LazyRow альбомов — только в режиме «Все видео» (как в PhotoPickerTab).
            if (openedAlbum == null && albums.isNotEmpty()) {
                item(key = "video_albums_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        // «Все видео» — первый псевдо-альбом (openedAlbum == null).
                        item(key = "all_videos") {
                            AlbumChip(
                                title = "Все видео",
                                countText = null,
                                coverUrl = null,
                                isSelected = openedAlbum == null,
                                onClick = { openedAlbum = null },
                            )
                        }
                        items(albums, key = { "valbum_${it.ownerId}_${it.id}" }) { album ->
                            AlbumChip(
                                title = album.title.ifBlank { "Без названия" },
                                countText = "${album.count} видео",
                                coverUrl = album.coverUrl,
                                isSelected = openedAlbum?.id == album.id,
                                onClick = { openedAlbum = album },
                            )
                        }
                    }
                }
            } else if (openedAlbum == null && albumsLoading) {
                // Во время первой загрузки альбомов — тонкий индикатор над списком
                // (не блокируя видео — они грузятся параллельно).
                item(key = "video_albums_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
            // albumsError != null — альбомы опциональны, не блокируем видео
            // (ошибка уже залогирована в LaunchedEffect выше).

            if (videos.isEmpty() && !loading && error == null) {
                // Открыт пустой альбом — показываем «Нет видео» внутри LazyColumn,
                // сохраняя back-row выше (возврат к «Все видео»).
                item(key = "video_empty_in_album") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Нет видео", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(videos, key = { "${it.ownerId}_${it.id}" }) { video ->
                    // MULTI-SELECT-ATTACH: ключ совпадает с PendingPick.Video.key.
                    val key = "video_${video.ownerId}_${video.id}"
                    VideoRow(
                        video = video,
                        onClick = { onPick(video) },
                        multiSelect = multiSelect,
                        isSelected = selectedKeys.contains(key),
                    )
                }
                if (hasMore) {
                    item(key = "video_picker_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRow(
    video: Video,
    onClick: () -> Unit,
    // MULTI-SELECT-ATTACH: чекбокс-индикатор справа + фон.
    multiSelect: Boolean = false,
    isSelected: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // MULTI-SELECT-ATTACH: подсветка выбранной строки (фон).
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(96.dp, 54.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (video.thumbUrl != null) {
                AsyncImage(
                    model = video.thumbUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Icon(Icons.Filled.PlayCircle, contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title.ifBlank { "Видео" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${video.duration.toDurationString()} • ${video.views} просм.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // MULTI-SELECT-ATTACH: чекбокс-индикатор справа.
        if (multiSelect) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle
                else Icons.Outlined.CheckCircle,
                contentDescription = if (isSelected) "Выбрано" else "Не выбрано",
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ============================================================================
//  Tab: Подарки — сетка подарков из gifts.getCatalog.
// ============================================================================

@Composable
private fun GiftPickerTab(
    app: SovaApp,
    onPick: (GiftItem) -> Unit,
    // MULTI-SELECT-ATTACH: overlay с галочкой поверх ячейки.
    multiSelect: Boolean = false,
    selectedKeys: Set<String> = emptySet(),
) {
    var gifts by remember { mutableStateOf<List<GiftItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            gifts = app.apiClient.giftsGetCatalog()
        } catch (e: Exception) {
            AppLog.e("GiftPicker", "load failed", e)
            error = e.message
        } finally {
            loading = false
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Не удалось загрузить: $error", color = MaterialTheme.colorScheme.error)
        }
        gifts.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Нет доступных подарков", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(gifts, key = { it.id }) { gift ->
                // MULTI-SELECT-ATTACH: ключ совпадает с PendingPick.Gift.key.
                val key = "gift_${gift.id}"
                GiftCell(
                    gift = gift,
                    onClick = { onPick(gift) },
                    multiSelect = multiSelect,
                    isSelected = selectedKeys.contains(key),
                )
            }
        }
    }
}

@Composable
private fun GiftCell(
    gift: GiftItem,
    onClick: () -> Unit,
    // MULTI-SELECT-ATTACH: чекбокс-overlay в верхнем углу + рамка.
    multiSelect: Boolean = false,
    isSelected: Boolean = false,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            // MULTI-SELECT-ATTACH: фон при выборе.
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // MULTI-SELECT-ATTACH: чекбокс-overlay поверх подарка.
        Box(contentAlignment = Alignment.TopEnd) {
            Box {
                if (gift.thumbUrl != null) {
                    AsyncImage(
                        model = gift.thumbUrl,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.CardGiftcard, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (multiSelect) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle
                    else Icons.Outlined.CheckCircle,
                    contentDescription = if (isSelected) "Выбрано" else "Не выбрано",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = gift.priceText,
            style = MaterialTheme.typography.labelSmall,
            color = if (gift.isFree) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ============================================================================
//  #ATTACH-UNIFY Tab: Фото — сетка фото из photos.getAll (attach без upload).
//  // ATTACH-PICKER-ALBUMS: альбомы (photos.getAlbums) + пагинация (offset).
//  - При открытии таба параллельно грузятся photosGetAlbums(myId) и
//    photosGetAll(myId, count=50, offset=0).
//  - Над сеткой фото — горизонтальный LazyRow с карточками альбомов:
//    «Все фото» (заглушка → photosGetAll) + реальные альбомы из VK
//    (tap → photosGet(albumId)). Карточка ~108×100: обложка thumbSrc
//    (fallback — иконка Image) + название + «N фото».
//  - Кнопка «← Все фото» над сеткой появляется, когда открыт конкретный
//    альбом (openedAlbum != null) — возврат к photosGetAll.
//  - Пагинация по скроллу: при достижении конца сетки (lastVisible >=
//    totalItemsCount - 6) вызывается loadMore (offset += 50). Стоп по
//    неполной странице (page.size < 50) — VK total не всегда точен.
//  - Паттерн пагинации тот же, что в AudioPickerTab (snapshotFlow +
//    distinctUntilChanged + collect → loadMore), только для LazyVerticalGrid.
// ============================================================================

/** Фото из photos.getAll / photos.get: данные для attachment-строки + превью. */
private data class VkPhotoRef(
    val ownerId: Long,
    val id: Long,
    val accessKey: String?,
    val previewUrl: String?,
)

@Composable
private fun PhotoPickerTab(
    app: SovaApp,
    onPick: (String, String?) -> Unit,
    // MULTI-SELECT-ATTACH: overlay с галочкой + выделение цветом.
    multiSelect: Boolean = false,
    selectedKeys: Set<String> = emptySet(),
) {
    // ATTACH-PICKER-ALBUMS: альбомы + пагинация.
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(true) }
    var albumsError by remember { mutableStateOf<String?>(null) }
    // null = «Все фото» (photosGetAll); non-null = открыт конкретный альбом
    // (photosGet с albumId). Тап по карточке альбома меняет openedAlbum →
    // LaunchedEffect(openedAlbum) перезагружает сетку фото.
    var openedAlbum by remember { mutableStateOf<Album?>(null) }

    var photos by remember { mutableStateOf<List<VkPhotoRef>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverOffset by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Загрузка альбомов — один раз при входе в таб (photos.getAlbums).
    // Не блокирует фото: photosGetAll грузится параллельно в эффекте ниже.
    LaunchedEffect(Unit) {
        try {
            val myId = app.tokenStorage.load()?.userId ?: 0L
            albums = app.apiClient.photosGetAlbums(ownerId = myId)
        } catch (e: Exception) {
            AppLog.e("PhotoPicker", "albums load failed", e)
            albumsError = e.message
        } finally {
            albumsLoading = false
        }
    }

    // Загрузка фото: photosGetAll (openedAlbum == null) или photosGet(albumId).
    // Перезапускается при смене openedAlbum — обнуляем offset/hasMore/photos.
    // myId берём из токена (photos.getAll/get требуют owner_id — своя библиотека).
    LaunchedEffect(openedAlbum) {
        val currentAlbum = openedAlbum
        loading = true
        error = null
        serverOffset = 0
        hasMore = false
        photos = emptyList()
        try {
            val myId = app.tokenStorage.load()?.userId ?: 0L
            val page: List<VkPhotoRef> = if (currentAlbum == null) {
                app.apiClient.photosGetAll(ownerId = myId, count = 50, offset = 0)
                    .mapNotNull { parseVkPhotoRef(it) }
            } else {
                app.apiClient.photosGet(
                    ownerId = myId,
                    albumId = currentAlbum.id.toString(),
                    count = 50,
                    offset = 0,
                ).mapNotNull { it.toVkPhotoRef() }
            }
            photos = page
            serverOffset = page.size
            // #AUDIO-PAGING-HOLE (волна 41): неполная страница ≠ конец списка.
            hasMore = page.size >= 50
        } catch (e: Exception) {
            AppLog.e("PhotoPicker", "load failed", e)
            error = e.message
        } finally {
            loading = false
        }
    }

    fun loadMore() {
        if (loadingMore || loading || !hasMore) return
        // Capture в локальный val — открытие другого альбома во время загрузки
        // не должно подмешать «чужую» страницу в текущую сетку.
        val currentAlbum = openedAlbum
        scope.launch {
            loadingMore = true
            try {
                val myId = app.tokenStorage.load()?.userId ?: 0L
                val page: List<VkPhotoRef> = if (currentAlbum == null) {
                    app.apiClient.photosGetAll(ownerId = myId, count = 50, offset = serverOffset)
                        .mapNotNull { parseVkPhotoRef(it) }
                } else {
                    app.apiClient.photosGet(
                        ownerId = myId,
                        albumId = currentAlbum.id.toString(),
                        count = 50,
                        offset = serverOffset,
                    ).mapNotNull { it.toVkPhotoRef() }
                }
                // B-G: post-await race-guard — если пользователь переключил альбом
                // во время запроса, чужая страница не должна попасть в текущий список.
                if (openedAlbum != currentAlbum) return@launch
                serverOffset += page.size
                val fresh = page.filter { nv -> photos.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                photos = (photos + fresh).distinctBy { "${it.ownerId}_${it.id}" }
                hasMore = page.size >= 50
            } catch (e: Exception) {
                AppLog.e("PhotoPicker", "load more failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // ATTACH-PICKER-ALBUMS: автодогрузка при скролле к концу сетки фото
    // (snapshotFlow + distinctUntilChanged — паттерн AudioPickerTab).
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 6) total else -1
        }
            .distinctUntilChanged()
            .collect { if (it > 0) loadMore() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ATTACH-PICKER-ALBUMS: горизонтальная лента альбомов над сеткой фото.
        // «Все фото» — первый псевдо-альбом (openedAlbum == null → photosGetAll).
        if (albums.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item(key = "all_photos") {
                    AlbumChip(
                        title = "Все фото",
                        countText = null,
                        coverUrl = null,
                        isSelected = openedAlbum == null,
                        onClick = { openedAlbum = null },
                    )
                }
                items(albums, key = { it.id }) { album ->
                    AlbumChip(
                        title = album.title.ifBlank { "Без названия" },
                        countText = "${album.size} фото",
                        coverUrl = album.thumbSrc,
                        isSelected = openedAlbum?.id == album.id,
                        onClick = { openedAlbum = album },
                    )
                }
            }
        } else if (albumsLoading) {
            // Во время первой загрузки альбомов — тонкий индикатор над сеткой
            // (не блокируя фото — они грузятся параллельно).
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        // albumsError != null — альбомы опциональны, не блокируем фото
        // (ошибка уже залогирована в LaunchedEffect выше).

        // Кнопка «← Все фото» — возврат из альбома к photosGetAll.
        if (openedAlbum != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openedAlbum = null }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Все фото",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Сетка фото (или состояния загрузки/ошибки/пусто).
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Не удалось загрузить: $error", color = MaterialTheme.colorScheme.error)
                }
                photos.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Нет фотографий", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(photos, key = { "${it.ownerId}_${it.id}" }) { ref ->
                        // MULTI-SELECT-ATTACH: ключ совпадает с PendingPick.Photo.key
                        // (buildVkAttachment → "photo{owner}_{id}[_key]").
                        val att = buildVkAttachment("photo", ref.ownerId, ref.id, ref.accessKey)
                        val key = "photo_$att"
                        VkPhotoCell(
                            ref = ref,
                            onClick = {
                                // attachment-строка через единый хелпер (#ATTACH-UNIFY, §3.2).
                                // #COMPOSER-ATTACH-PREVIEW (волна 39): +превью-URL — композер
                                // рисует миниатюру выбранного фото вместо слепой скрепки.
                                onPick(att, ref.previewUrl)
                            },
                            multiSelect = multiSelect,
                            isSelected = selectedKeys.contains(key),
                        )
                    }
                }
            }
        }
    }
}

/**
 * ATTACH-PICKER-ALBUMS: карточка альбома в горизонтальной ленте над сеткой
 * фото. Card ~108×100: обложка (thumbSrc или fallback-иконка Image) +
 * название + «N фото». isSelected → primaryContainer tint для подсветки.
 */
@Composable
private fun AlbumChip(
    title: String,
    countText: String?,
    coverUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(108.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (countText != null) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * ATTACH-PICKER-ALBUMS: конвертация PhotoItem (photos.get) → VkPhotoRef.
 * Превью — mediumUrl (300-600px) с фолбэком на smallUrl (130px); для сетки
 * пикара этого достаточно. Для full-size в чате композер использует
 * attachment-строку (photo{owner}_{id}[_key]), не URL.
 */
private fun PhotoItem.toVkPhotoRef(): VkPhotoRef = VkPhotoRef(
    ownerId = ownerId,
    id = id,
    accessKey = accessKey,
    previewUrl = mediumUrl ?: smallUrl,
)

@Composable
private fun VkPhotoCell(
    ref: VkPhotoRef,
    onClick: () -> Unit,
    // MULTI-SELECT-ATTACH: чекбокс-overlay в верхнем углу + затемнение.
    multiSelect: Boolean = false,
    isSelected: Boolean = false,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (ref.previewUrl != null) {
            AsyncImage(
                model = ref.previewUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // MULTI-SELECT-ATTACH: затемнение + чекбокс-overlay при выборе
        // (как в системном галерейном multi-select).
        if (multiSelect && isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f)),
            )
        }
        if (multiSelect) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle
                else Icons.Outlined.CheckCircle,
                contentDescription = if (isSelected) "Выбрано" else "Не выбрано",
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp),
            )
        }
    }
}

/**
 * Парсер элемента photos.getAll (VKA возвращает сырые JsonObject —
 * парсим на UI-стороне, паттерн ProfileScreen.extractPhotoAllUrl).
 * Превью — sizes[] с максимальной площадью, фолбэк photo_807/604/130.
 */
private fun parseVkPhotoRef(photo: JsonObject): VkPhotoRef? {
    return try {
        val id = photo.get("id")?.takeIf { it.isJsonPrimitive }?.asLong ?: return null
        val ownerId = photo.get("owner_id")?.takeIf { it.isJsonPrimitive }?.asLong ?: return null
        val accessKey = photo.get("access_key")
            ?.takeIf { it.isJsonPrimitive }?.asString
        var bestUrl: String? = null
        var bestArea = 0L
        val sizes = photo.getAsJsonArray("sizes")
        if (sizes != null) {
            for (el in sizes) {
                if (!el.isJsonObject) continue
                val o = el.asJsonObject
                val urlEl = o.get("url") ?: continue
                if (!urlEl.isJsonPrimitive) continue
                val w = o.get("width")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                val h = o.get("height")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                val area = w.toLong() * h.toLong()
                if (area > bestArea) {
                    bestArea = area
                    bestUrl = urlEl.asString
                }
            }
        }
        if (bestUrl == null) {
            bestUrl = listOf("photo_807", "photo_604", "photo_130").firstNotNullOfOrNull { key ->
                photo.get(key)?.takeIf { it.isJsonPrimitive }?.asString
            }
        }
        VkPhotoRef(ownerId = ownerId, id = id, accessKey = accessKey, previewUrl = bestUrl)
    } catch (e: Exception) {
        AppLog.e("PhotoPicker", "parseVkPhotoRef error", e)
        null
    }
}

// ============================================================================
//  #ATTACH-UNIFY Tab: Документы — список документов из docs.get.
//  // ATTACH-PICKER-ALBUMS: серверная пагинация по offset (docsGetWithCount)
//  с автодогрузкой при скролле к концу (snapshotFlow — паттерн AudioPickerTab).
//  Стоп по неполной странице (page.size < 50) или total < 0 (сбой VK total).
// ============================================================================

@Composable
private fun DocsPickerTab(
    app: SovaApp,
    onPick: (attachment: String, title: String) -> Unit,
    // MULTI-SELECT-ATTACH: overlay с галочкой + выделение цветом.
    multiSelect: Boolean = false,
    selectedKeys: Set<String> = emptySet(),
) {
    var docs by remember { mutableStateOf<List<DocFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // ATTACH-PICKER-ALBUMS: серверная пагинация (docsGetWithCount).
    var serverOffset by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (loadingMore || loading || !hasMore) return
        scope.launch {
            loadingMore = true
            try {
                // #MULTI-SELECT-FIX: docsGetWithCount возвращает Pair<List<DocFile>, Int>
                //   (page first, total second) — аналогично videoGetWithCount,
                //   наоборот к audioGetWithCount. Было `val (total, page)` →
                //   page: Int, неразрешённые filter/distinctBy/isNotEmpty.
                val (page, total) = app.apiClient.docsGetWithCount(count = 50, offset = serverOffset)
                serverOffset += page.size
                val fresh = page.filter { nv -> docs.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                docs = (docs + fresh).distinctBy { "${it.ownerId}_${it.id}" }
                // #AUDIO-PAGING-HOLE (волна 41): непустая страница ≠ конец;
                // сбой (total=-1) — сохраняем hasMore для повтора по скроллу.
                hasMore = page.isNotEmpty() || total < 0
            } catch (e: Exception) {
                AppLog.e("DocsPicker", "load more failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        try {
            // #MULTI-SELECT-FIX: Pair<List<DocFile>, Int> — page first (см. loadMore).
            val (page, total) = app.apiClient.docsGetWithCount(count = 50, offset = 0)
            docs = page.distinctBy { "${it.ownerId}_${it.id}" }
            serverOffset = page.size
            // #AUDIO-PAGING-HOLE (волна 41): неполная страница ≠ конец списка.
            hasMore = page.isNotEmpty() || total < 0
        } catch (e: Exception) {
            AppLog.e("DocsPicker", "load failed", e)
            error = e.message
        } finally {
            loading = false
        }
    }

    // ATTACH-PICKER-ALBUMS: автодогрузка при скролле к концу списка.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total > 0 && lastVisible >= total - 5) total else -1
        }
            .distinctUntilChanged()
            .collect { if (it > 0) loadMore() }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Не удалось загрузить: $error", color = MaterialTheme.colorScheme.error)
        }
        docs.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Нет документов", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(docs, key = { "${it.ownerId}_${it.id}" }) { doc ->
                // MULTI-SELECT-ATTACH: ключ совпадает с PendingPick.Doc.key.
                val att = buildVkAttachment("doc", doc.ownerId, doc.id, doc.accessKey)
                val key = "doc_$att"
                DocRow(
                    doc = doc,
                    onClick = {
                        // doc{owner}_{id}[_{accessKey}] через единый хелпер (#ATTACH-UNIFY).
                        onPick(att, doc.title.ifBlank { "документ" })
                    },
                    multiSelect = multiSelect,
                    isSelected = selectedKeys.contains(key),
                )
            }
            if (hasMore) {
                item(key = "docs_picker_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocRow(
    doc: DocFile,
    onClick: () -> Unit,
    // MULTI-SELECT-ATTACH: чекбокс-индикатор справа + фон.
    multiSelect: Boolean = false,
    isSelected: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // MULTI-SELECT-ATTACH: подсветка выбранной строки (фон).
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.title.ifBlank { "документ" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${doc.ext.uppercase()} · ${formatDocSizeShort(doc.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // MULTI-SELECT-ATTACH: чекбокс-индикатор справа.
        if (multiSelect) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle
                else Icons.Outlined.CheckCircle,
                contentDescription = if (isSelected) "Выбрано" else "Не выбрано",
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Человекочитаемый размер файла (локальный хелпер таба «Документы»). */
private fun formatDocSizeShort(size: Long): String = when {
    size < 1024 -> "$size Б"
    size < 1024 * 1024 -> "${size / 1024} КБ"
    else -> String.format("%.1f МБ", size / 1024.0 / 1024.0)
}
