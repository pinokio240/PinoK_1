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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.CardGiftcard
import com.google.gson.JsonObject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import re.pinok.data.model.DocFile
import re.pinok.data.model.GiftItem
import re.pinok.data.model.Track
import re.pinok.data.model.Video
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
 * @param initialTab таб, который открыт при показе листа
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
    showGiftTab: Boolean = true,
    showPhotoTab: Boolean = false,
    showDocsTab: Boolean = false,
    onPickAudio: (Track) -> Unit = {},
    onPickVideo: (Video) -> Unit = {},
    onPickGift: (GiftItem) -> Unit = {},
    onPickPhotoAttachment: (String) -> Unit = {},
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
            Text(
                text = "Прикрепить",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

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
                AttachmentPickerTab.Music -> AudioPickerTab(app = app, onPick = { onPickAudio(it); onDismiss() })
                AttachmentPickerTab.Video -> VideoPickerTab(app = app, onPick = { onPickVideo(it); onDismiss() })
                AttachmentPickerTab.Gifts -> GiftPickerTab(app = app, onPick = { onPickGift(it); onDismiss() })
                AttachmentPickerTab.Photos -> PhotoPickerTab(app = app, onPick = { onPickPhotoAttachment(it); onDismiss() })
                AttachmentPickerTab.Docs -> DocsPickerTab(app = app, onPick = { att, title -> onPickDocAttachment(att, title); onDismiss() })
            }
        }
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
private fun AudioPickerTab(app: SovaApp, onPick: (Track) -> Unit) {
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
                val (_, page) = app.apiClient.audioGetWithCount(count = 50, offset = serverOffset)
                serverOffset += page.size
                val fresh = page
                    .filter { it.id > 0L && it.ownerId != 0L }
                    .filter { nv -> tracks.none { it.ownerId == nv.ownerId && it.id == nv.id } }
                tracks = (tracks + fresh).distinctBy { "${it.ownerId}_${it.id}" }
                hasMore = page.size >= 50
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
            val (_, page) = app.apiClient.audioGetWithCount(count = 50, offset = 0)
            tracks = page.filter { it.id > 0L && it.ownerId != 0L }
                .distinctBy { "${it.ownerId}_${it.id}" }
            serverOffset = page.size
            hasMore = page.size >= 50
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
                AudioTrackRow(track = track, onClick = { onPick(track) })
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
private fun AudioTrackRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}

// ============================================================================
//  Tab: Видео — список видео из video.get библиотеки пользователя.
// ============================================================================

@Composable
private fun VideoPickerTab(app: SovaApp, onPick: (Video) -> Unit) {
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            videos = app.apiClient.videoGet(count = 50)
        } catch (e: Exception) {
            AppLog.e("VideoPicker", "load failed", e)
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
        videos.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Нет видео", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(videos, key = { "${it.ownerId}_${it.id}" }) { video ->
                VideoRow(video = video, onClick = { onPick(video) })
            }
        }
    }
}

@Composable
private fun VideoRow(video: Video, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}

// ============================================================================
//  Tab: Подарки — сетка подарков из gifts.getCatalog.
// ============================================================================

@Composable
private fun GiftPickerTab(app: SovaApp, onPick: (GiftItem) -> Unit) {
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
                GiftCell(gift = gift, onClick = { onPick(gift) })
            }
        }
    }
}

@Composable
private fun GiftCell(gift: GiftItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
// ============================================================================

/** Фото из photos.getAll: данные для attachment-строки + превью. */
private data class VkPhotoRef(
    val ownerId: Long,
    val id: Long,
    val accessKey: String?,
    val previewUrl: String?,
)

@Composable
private fun PhotoPickerTab(app: SovaApp, onPick: (String) -> Unit) {
    var photos by remember { mutableStateOf<List<VkPhotoRef>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            // photos.getAll требует owner_id — берём свою библиотеку
            // (id текущего пользователя из токена, паттерн PostDetailScreen).
            val myId = app.tokenStorage.load()?.userId ?: 0L
            photos = app.apiClient.photosGetAll(ownerId = myId, count = 50)
                .mapNotNull { parseVkPhotoRef(it) }
        } catch (e: Exception) {
            AppLog.e("PhotoPicker", "load failed", e)
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
        photos.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Нет фотографий", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(photos, key = { "${it.ownerId}_${it.id}" }) { ref ->
                VkPhotoCell(ref = ref, onClick = {
                    // attachment-строка через единый хелпер (#ATTACH-UNIFY, §3.2).
                    onPick(buildVkAttachment("photo", ref.ownerId, ref.id, ref.accessKey))
                })
            }
        }
    }
}

@Composable
private fun VkPhotoCell(ref: VkPhotoRef, onClick: () -> Unit) {
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
// ============================================================================

@Composable
private fun DocsPickerTab(app: SovaApp, onPick: (attachment: String, title: String) -> Unit) {
    var docs by remember { mutableStateOf<List<DocFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            docs = app.apiClient.docsGet(count = 50)
        } catch (e: Exception) {
            AppLog.e("DocsPicker", "load failed", e)
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
        docs.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Нет документов", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(docs, key = { "${it.ownerId}_${it.id}" }) { doc ->
                DocRow(doc = doc, onClick = {
                    // doc{owner}_{id}[_{accessKey}] через единый хелпер (#ATTACH-UNIFY).
                    onPick(
                        buildVkAttachment("doc", doc.ownerId, doc.id, doc.accessKey),
                        doc.title.ifBlank { "документ" },
                    )
                })
            }
        }
    }
}

@Composable
private fun DocRow(doc: DocFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}

/** Человекочитаемый размер файла (локальный хелпер таба «Документы»). */
private fun formatDocSizeShort(size: Long): String = when {
    size < 1024 -> "$size Б"
    size < 1024 * 1024 -> "${size / 1024} КБ"
    else -> String.format("%.1f МБ", size / 1024.0 / 1024.0)
}
