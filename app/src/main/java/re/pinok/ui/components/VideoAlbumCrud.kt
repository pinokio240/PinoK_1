// File: ui/components/VideoAlbumCrud.kt
// VIDEO-ALBUMS-CRUD: общие UI-компоненты для управления видеоальбомами и видео
// в альбомах. Используются в CommunityScreen / ProfileScreen / UserProfileScreen
// (одинаковый UI, разница только в правах — show* флаги).
package re.pinok.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import re.pinok.data.model.Video
import re.pinok.data.model.VideoAlbum

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
