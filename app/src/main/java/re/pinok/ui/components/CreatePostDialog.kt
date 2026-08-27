package re.pinok.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Диалог создания нового поста — текстовое поле + photo picker + галочка «только для друзей».
 *
 * Вынесен из FeedScreen.kt в общий компонент, чтобы переиспользовать
 * в ProfileScreen.kt (по запросу пользователя 2026-07-12: кнопка
 * «Создать пост» перенесена из ленты в профиль, как в оригинальном VK).
 *
 * VK API: `wall.post` (message, attachments, friends_only, owner_id, publish_date).
 * Фото-флоу: `photos.getWallUploadServer` → upload → `photos.saveWallPhoto` → attachments.
 * См. VK_IMPORT_API.MD §1.1.
 *
 * Меню «Прикрепить» — общий компонент [UnifiedAttachMenu], тот же, что в чате
 * и в комментариях. По умолчанию для постов включён только пункт «Фото»
 * (showCamera/showVideo/showMusic/showFile = false), но API позволяет расширить
 * набор в будущем без изменения сигнатуры — достаточно передать true для
 * нужного флага и соответствующий колбэк.
 *
 * @param onDismiss Вызывается при закрытии диалога без публикации.
 * @param onSubmit Вызывается с (message, friendsOnly) при нажатии «Опубликовать».
 *                 Вызывающий код ответственен за загрузку фото через `selectedPhotoUri`.
 * @param selectedPhotoUri URI выбранного фото (или null).
 * @param onPickPhoto Колбэк для запуска фото-пикера.
 * @param onRemovePhoto Колбэк для удаления выбранного фото.
 */
@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit,
    onSubmit: (message: String, friendsOnly: Boolean) -> Unit,
    // Sprint 2, P1-4 (#91): photo picker integration.
    selectedPhotoUri: Uri? = null,
    onPickPhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }
    var friendsOnly by remember { mutableStateOf(false) }
    // Единое меню «Прикрепить» — тот же UI, что в чате и комментариях.
    var showAttachMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый пост") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Что у вас нового?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10,
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Sprint 2, P1-4 (#91): photo picker button + selected thumbnail.
                if (selectedPhotoUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "Выбранное фото",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        // Remove button — top-right corner.
                        IconButton(
                            onClick = onRemovePhoto,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "✕",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    // "Прикрепить" — единое меню (UnifiedAttachMenu), тот же UI что
                    // в чате и комментариях. Для постов пока включён только пункт
                    // «Фото» — Video/Music/File будут добавлены отдельным шагом.
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showAttachMenu = true }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Прикрепить",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        UnifiedAttachMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false },
                            onPhoto = onPickPhoto,
                            // Для постов пока доступен только пункт «Фото».
                            // Video/Music/File можно включить, расширив API диалога.
                            showCamera = false,
                            showVideo = false,
                            showMusic = false,
                            showGift = false,
                            showFile = false,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = friendsOnly,
                        onCheckedChange = { friendsOnly = it },
                    )
                    Text(
                        text = "Только для друзей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Можно опубликовать с фото даже без текста, или текст без фото.
                    if (text.isNotBlank() || selectedPhotoUri != null) {
                        onSubmit(text.trim(), friendsOnly)
                    }
                },
                enabled = text.isNotBlank() || selectedPhotoUri != null,
            ) { Text("Опубликовать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
