// File: ui/components/VideoAlbumCrud.kt
// VIDEO-ALBUMS-CRUD: общие UI-компоненты для управления видеоальбомами и видео
// в альбомах. Используются в CommunityScreen / ProfileScreen / UserProfileScreen
// (одинаковый UI, разница только в правах — show* флаги).
package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Video
import re.pinok.data.model.VideoAlbum
import re.pinok.util.toDurationString

/**
 * VIDEO-ALBUMS-CRUD: контекстное меню long-press на карточке видео.
 *
 * Универсальный dropdown с 4 опциями (показываются только разрешённые):
 * - «Добавить в альбом» (B3: видео в «Все видео»)
 * - «Добавить себе» (B3/B4: чужое видео)
 * - «Убрать из альбома» (B4: видео внутри альбома)
 * - «Удалить видео» (B4: своё видео в своём/админском альбоме)
 *
 * Anchored к родительскому Box — позиционируется на карточке, на которой
 * сработал long-press. Каждый вызов — отдельный dropdown; expands=true только
 * когда [state] == [target] (один активный элемент на экран).
 *
 * @param target видео, к которому привязан этот dropdown
 * @param state текущий активный target на экране (null = скрыт)
 * @param onDismiss скрыть меню
 */
@Composable
fun VideoContextMenuDropdown(
    target: Video,
    state: Video?,
    onDismiss: () -> Unit,
    onAddToAlbum: () -> Unit = {},
    onAddToSelf: () -> Unit = {},
    onRemoveFromAlbum: () -> Unit = {},
    onDeleteVideo: () -> Unit = {},
    showAddToAlbum: Boolean = false,
    showAddToSelf: Boolean = false,
    showRemoveFromAlbum: Boolean = false,
    showDeleteVideo: Boolean = false,
) {
    DropdownMenu(
        expanded = state == target,
        onDismissRequest = onDismiss,
    ) {
        if (showAddToAlbum) {
            DropdownMenuItem(
                text = { Text("Добавить в альбом") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null) },
                onClick = { onDismiss(); onAddToAlbum() },
            )
        }
        if (showAddToSelf) {
            DropdownMenuItem(
                text = { Text("Добавить себе") },
                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                onClick = { onDismiss(); onAddToSelf() },
            )
        }
        if (showRemoveFromAlbum) {
            DropdownMenuItem(
                text = { Text("Убрать из альбома") },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = { onDismiss(); onRemoveFromAlbum() },
            )
        }
        if (showDeleteVideo) {
            DropdownMenuItem(
                text = { Text("Удалить видео") },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { onDismiss(); onDeleteVideo() },
            )
        }
    }
}

/**
 * VIDEO-ALBUMS-CRUD (B2): контекстное меню long-press на карточке альбома.
 * 2 пункта: «Переименовать» и «Удалить». Anchored к родительскому Box.
 *
 * @param target альбом, к которому привязан этот dropdown
 * @param state текущий активный target на экране (null = скрыт)
 * @param onDismiss скрыть меню
 */
@Composable
fun AlbumContextMenuDropdown(
    target: VideoAlbum,
    state: VideoAlbum?,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = state == target,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("Переименовать") },
            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
            onClick = { onDismiss(); onRename() },
        )
        DropdownMenuItem(
            text = { Text("Удалить") },
            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() },
        )
    }
}

/**
 * VIDEO-ALBUMS-CRUD (B1): карточка-кнопка «+ Создать альбом» в начале LazyRow
 * видеоальбомов. Card-стиль как у обычной карточки альбома (140dp для Community,
 * 120dp для Profile — передаётся через [modifier] / [widthDp] не нужно: берём
 * фиксированную ширину 120dp по ProfileVideoAlbumCard, но для Community — 140dp).
 * Реализация: квадратная плитка с иконкой Add + подпись «Создать альбом».
 *
 * @param widthDp ширина карточки (140 для Community, 120 для Profile).
 */
@Composable
fun CreateVideoAlbumCard(
    onClick: () -> Unit,
    widthDp: androidx.compose.ui.unit.Dp = 120.dp,
) {
    Card(
        modifier = Modifier
            .width(widthDp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Создать альбом",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp),
                maxLines = 1,
            )
        }
    }
}

/**
 * VIDEO-ALBUMS-CRUD (B1): диалог создания альбома. Поле названия +
 * выбор приватности (radio: «Все» / «Только я» — упрощение по спеке,
 * VK поддерживает ещё «Друзья»/«Друзья и друзья друзей», пропускаем).
 *
 * Privacy: 0 = все (VK default), 1 = только я.
 */
@Composable
fun CreateVideoAlbumDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, privacy: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый видеоальбом") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.fillMaxWidth().heightIn(min = 8.dp))
                PrivacyRadioRow(label = "Все", selected = privacy == 0) { privacy = 0 }
                PrivacyRadioRow(label = "Только я", selected = privacy == 1) { privacy = 1 }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), privacy) },
                enabled = title.isNotBlank(),
            ) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun PrivacyRadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * VIDEO-ALBUMS-CRUD (B2): диалог переименования альбома. Поле предзаполнено
 * текущим названием; кнопка «Сохранить» доступна только если название
 * изменилось и непустое.
 */
@Composable
fun RenameVideoAlbumDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (newTitle: String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать альбом") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim()) },
                enabled = title.isNotBlank() && title.trim() != initialTitle,
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * VIDEO-ALBUMS-CRUD (B2): подтверждение удаления альбома.
 * VK: «Все видео в нём останутся, но альбом будет удалён».
 */
@Composable
fun DeleteVideoAlbumConfirmDialog(
    albumTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить альбом?") },
        text = {
            Text(
                "Удалить альбом «$albumTitle»? Все видео в нём останутся, но альбом будет удалён.",
                overflow = TextOverflow.Visible,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Удалить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * VIDEO-ALBUMS-CRUD (B3): пикер альбома для добавления видео.
 * Если список альбомов пуст — предлагает создать новый (onCreateAlbum).
 */
@Composable
fun AddVideoToAlbumDialog(
    albums: List<VideoAlbum>,
    onDismiss: () -> Unit,
    onPick: (VideoAlbum) -> Unit,
    onCreateAlbum: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в альбом") },
        text = {
            if (albums.isEmpty()) {
                Column {
                    Text("У вас пока нет видеоальбомов.")
                    Spacer(Modifier.fillMaxWidth().heightIn(min = 8.dp))
                    Text(
                        "Создайте альбом, чтобы собрать видео вместе.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(albums, key = { "pick_${it.ownerId}_${it.id}" }) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = false, onClick = { onPick(album) })
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = album.title.ifBlank { "Без названия" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${album.count} видео",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (albums.isEmpty()) {
                TextButton(onClick = onCreateAlbum) { Text("Создать альбом") }
            } else {
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
        dismissButton = {
            if (albums.isNotEmpty()) {
                // Когда альбомов нет — есть только «Создать», иначе «Отмена» выше.
                TextButton(onClick = onCreateAlbum) { Text("Новый альбом") }
            }
        },
    )
}

/**
 * VIDEO-ALBUMS-CRUD (B4): подтверждение удаления видео (своего/админского).
 */
@Composable
fun DeleteVideoConfirmDialog(
    videoTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить видео?") },
        text = {
            Text(
                "Удалить видео «$videoTitle»? Действие нельзя отменить.",
                overflow = TextOverflow.Visible,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Удалить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * VIDEO-ALBUMS-CRUD (B-DIALOG-SEARCH): album-centric диалог «Добавить видео в альбом»
 * по UX из VK web SPA (HAR: открыть альбом → «Добавить видео» → поиск → выбор).
 *
 * Полноэкранный Dialog (fillMaxHeight(0.85f)) с полем поиска + списком результатов
 * (превью + название + duration + views) → тап «Добавить» на видео →
 * [re.pinok.api.VKApiClient.videoAddToAlbum] → optimistic ✓ (addedIds).
 *
 * Переиспользует:
 *  - [re.pinok.api.VKApiClient.videoSearch] (с параметром ownerId — scoped/глобальный;
 *    B-SEARCH-OWNER добавил ownerId в сигнатуру videoSearch).
 *  - [re.pinok.api.VKApiClient.videoAddToAlbum] (albumId + video + ownerId).
 *
 * Фиксированная сигнатура (НЕ менять — используют 3 параллельных интегратора в волне 2):
 *
 * @param album альбом-цель, в который добавляем видео.
 * @param ownerId Owner для scoped-поиска: null=глобальный поиск VK-wide (свой профиль),
 *  -groupId=только видео сообщества (для CommunityScreen). ТАКЖЕ передаётся в
 *  videoAddToAlbum как ownerId альбома (target_id).
 * @param onDismiss закрытие диалога.
 * @param onAdded вызывается после успешного добавления видео в альбом — интегратор
 *  делает optimistic update (albumVideos += video, count++).
 */
@Composable
fun AddVideoToAlbumSearchDialog(
    album: VideoAlbum,
    ownerId: Long?,
    onDismiss: () -> Unit,
    onAdded: (video: Video) -> Unit,
    /**
     * ADD-TARGET-FIX: владелец АЛЬБОМА для videoAddToAlbum (target_id).
     * По умолчанию = ownerId (backwards-compatible: ProfileScreen/VideoScreen
     * где поиск и альбом принадлежат одному owner).
     * Для CommunityScreen: ownerId=null (глобальный поиск),
     * albumOwnerId=-groupId (альбом сообщества — VK требует target_id).
     */
    albumOwnerId: Long? = ownerId,
) {
    val ctx = LocalContext.current
    // SovaApp не в сигнатуре (фиксированная) — получаем через applicationContext.
    val app = ctx.applicationContext as? SovaApp
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // "ownerId_id" уже добавленных видео (optimistic, локально в рамках диалога).
    var addedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(query) {
        // Debounce 400мс — LaunchedEffect cancellит предыдущую корутину при смене query.
        delay(400)
        // Пустой запрос + нет scoped owner → не ищем (глобальный пустой поиск бессмысленен).
        if (query.isBlank() && ownerId == null) {
            results = emptyList()
            loading = false
            error = null
            return@LaunchedEffect
        }
        val a = app
        if (a == null) {
            error = "Приложение не готово"
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            // Для группы (ownerId=-groupId) с пустым q — VK вернёт все видео сообщества,
            // НО videoSearch имеет внутренний guard `query.isBlank() → emptyList()`.
            // См. риск в worklog: для пустого q при scoped owner список может быть пуст
            // до фикса videoSearch. При непустом q — scoped-поиск работает корректно.
            results = a.apiClient.videoSearch(query = query, count = 30, ownerId = ownerId)
        } catch (e: Exception) {
            error = e.message ?: "Ошибка поиска"
            results = emptyList()
        } finally {
            loading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header: «Добавить видео в «{album.title}»» + Close
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Добавить видео в «${album.title.ifBlank { "альбом" }}»",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                    }
                }
                // Search field
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = {
                        Text(if (ownerId != null) "Поиск видео..." else "Поиск видео по VK...")
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                // Results
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        loading && results.isEmpty() -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        error != null -> {
                            Text(
                                text = "Ошибка: $error",
                                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        results.isEmpty() -> {
                            Text(
                                text = if (query.isBlank()) "Введите запрос для поиска" else "Ничего не найдено",
                                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(results, key = { "${it.ownerId}_${it.id}" }) { video ->
                                    VideoSearchResultRow(
                                        video = video,
                                        isAdded = addedIds.contains("${video.ownerId}_${video.id}"),
                                        onAdd = {
                                            scope.launch {
                                                val a = app
                                                if (a == null) {
                                                    Toast.makeText(ctx, "Приложение не готово", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                                try {
                                                    val ok = a.apiClient.videoAddToAlbum(
                                                        albumId = album.id,
                                                        video = video,
                                                        ownerId = albumOwnerId,  // ADD-TARGET-FIX: target_id = владелец альбома
                                                    )
                                                    if (ok) {
                                                        addedIds = addedIds + "${video.ownerId}_${video.id}"
                                                        onAdded(video)
                                                        Toast.makeText(ctx, "Добавлено в альбом", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(ctx, "Не удалось добавить", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    )
                                }
                                if (loading) {
                                    item {
                                        CircularProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Строка результата поиска видео для [AddVideoToAlbumSearchDialog].
 * Превью 80dp (AsyncImage или иконка-заглушка) + название + duration + views +
 * кнопка «Добавить» (или ✓ если уже добавлено в рамках текущей сессии диалога).
 */
@Composable
private fun VideoSearchResultRow(
    video: Video,
    isAdded: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Превью 80dp
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            video.thumbUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } ?: Icon(
                Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        // Title + duration + views
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title.ifBlank { "Видео" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${video.duration.toDurationString()} • ${video.views} просм.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Кнопка «Добавить» / ✓ «Добавлено»
        if (isAdded) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Добавлено",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            TextButton(onClick = onAdd) { Text("Добавить") }
        }
    }
}
