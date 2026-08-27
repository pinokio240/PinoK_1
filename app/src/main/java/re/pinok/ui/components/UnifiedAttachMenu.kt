package re.pinok.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Единое меню «Прикрепить» — используется во ВСЕХ экранах, где можно
 * прикрепить вложение: чат, комментарий к посту, создание поста,
 * комментарий к фото/видео, и т.д.
 *
 * Меню всегда содержит один и тот же набор пунктов в одном порядке:
 *  1. Фото            — выбор из галереи
 *  2. Фото с камеры   — снимок и отправка
 *  3. Видео           — выбор из библиотеки VK (AttachmentPickerSheet)
 *  4. Музыка          — выбор трека из библиотеки VK
 *  5. Подарок         — каталог gifts.getCatalog (только для личных сообщений)
 *  6. Файл            — выбор произвольного файла как doc
 *
 * Пункты, недоступные в текущем контексте (например, подарок в комментариях),
 * скрываются через флаги `show*`. По умолчанию камера/видео/музыка/файл видны,
 * подарок скрыт (т.к. gifts.send работает только в messages.send).
 *
 * Использование:
 * ```
 * var showAttachMenu by remember { mutableStateOf(false) }
 * Box {
 *     IconButton(onClick = { showAttachMenu = true }) {
 *         Icon(Icons.Outlined.AttachFile, contentDescription = "Прикрепить")
 *     }
 *     UnifiedAttachMenu(
 *         expanded = showAttachMenu,
 *         onDismissRequest = { showAttachMenu = false },
 *         onPhoto = { ... },
 *         onCamera = { ... },
 *         onVideo = { ... },
 *         onAudio = { ... },
 *         onFile = { ... },
 *         showGift = false, // подарки недоступны в комментариях
 *     )
 * }
 * ```
 */
@Composable
fun UnifiedAttachMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPhoto: () -> Unit,
    onCamera: () -> Unit = {},
    onVideo: () -> Unit = {},
    onAudio: () -> Unit = {},
    onGift: () -> Unit = {},
    onFile: () -> Unit = {},
    showCamera: Boolean = true,
    showVideo: Boolean = true,
    showMusic: Boolean = true,
    showGift: Boolean = false,
    showFile: Boolean = true,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        // 1. Фото из галереи.
        DropdownMenuItem(
            text = { Text("Фото") },
            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
            onClick = {
                onDismissRequest()
                onPhoto()
            },
        )
        // 2. Фото с камеры.
        if (showCamera) {
            DropdownMenuItem(
                text = { Text("Фото с камеры") },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onCamera()
                },
            )
        }
        // 3. Видео из библиотеки VK.
        if (showVideo) {
            DropdownMenuItem(
                text = { Text("Видео") },
                leadingIcon = { Icon(Icons.Outlined.VideoLibrary, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onVideo()
                },
            )
        }
        // 4. Музыка из библиотеки VK.
        if (showMusic) {
            DropdownMenuItem(
                text = { Text("Музыка") },
                leadingIcon = { Icon(Icons.Outlined.MusicNote, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onAudio()
                },
            )
        }
        // 5. Подарок (только для личных сообщений).
        if (showGift) {
            DropdownMenuItem(
                text = { Text("Подарок") },
                leadingIcon = { Icon(Icons.Outlined.CardGiftcard, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onGift()
                },
            )
        }
        // 6. Произвольный файл (документ).
        if (showFile) {
            DropdownMenuItem(
                text = { Text("Файл") },
                leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onFile()
                },
            )
        }
        // Если все пункты скрыты — покажем заглушку, чтобы пользователь
        // понимал, что меню пустое, а не зависло.
        if (!showCamera && !showVideo && !showMusic && !showGift && !showFile) {
            DropdownMenuItem(
                text = {
                    Text(
                        "Вложения недоступны",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                enabled = false,
                onClick = {},
            )
        }
    }
}
