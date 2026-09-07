package re.pinok.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * #ATTACH-UNIFY: единая точка сборки attachment-строки VK —
 * «type{ownerId}_{id}» (+ «_{accessKey}», если VK выдал ключ).
 *
 * Раньше строка собиралась руками в 4+ местах (PostDetail, Feed, Chat,
 * VKApiClient) — расхождения формата приводили к P0-багу «фото как doc»
 * в ленте. Все новые контуры прикреплений обязаны собирать строку здесь.
 *
 * @param type тип вложения: "photo" / "video" / "audio" / "doc" / "poll" и т.п.
 * @param ownerId владелец объекта (положительный для юзера, отрицательный для группы)
 * @param id идентификатор объекта
 * @param accessKey ключ доступа (нужен для чужих объектов; может быть null)
 */
internal fun buildVkAttachment(
    type: String,
    ownerId: Long,
    id: Long,
    accessKey: String? = null,
): String {
    val base = "$type${ownerId}_$id"
    return if (accessKey.isNullOrBlank()) base else "${base}_$accessKey"
}

/**
 * Единое меню «Прикрепить» — используется во ВСЕХ экранах, где можно
 * прикрепить вложение: чат, комментарий к посту, создание поста.
 *
 * #ATTACH-UNIFY (спека «вложения.веб-изучение.унификация.md» §3): порядок
 * пунктов фиксирован и повторяет пост-композер vk.com —
 *  1. Фото               — с устройства (photo picker)
 *  2. Фото с камеры      — снимок (TakePicture + FileProvider)
 *  3. Фото из VK         — альбомы пользователя (photosGetAll, attach без upload)
 *  4. Видео              — из библиотеки VK (AttachmentPickerSheet)
 *  5. Видео с устройства — video.save(isClips=false) + videoUploadFile (только пост)
 *  6. Видео по ссылке    — video.save(link_url=…) (только пост, VK web семантика)
 *  7. Музыка             — из библиотеки VK (audioGet)
 *  8. Файл               — с устройства (OpenDocument → docs pipeline)
 *  9. Файл из VK         — документы пользователя (docsGet, attach без upload)
 * 10. Подарок            — каталог gifts.getCatalog (только личные сообщения)
 * 11. Опрос              — конструктор polls.add (только пост)
 *
 * Пункты, недоступные в текущем контексте, скрываются флагами `show*`.
 * Новые пункты (из VK / видео-устройство / ссылка / опрос) по умолчанию
 * ВЫКЛЮЧЕНЫ — существующие вызовы продолжают работать без изменений
 * (расширение сигнатуры только дефолтными параметрами, см. §3.2 спеки).
 *
 * Правило no-stub: пункт рисуется только если вызывающая сторона передала
 * и рабочий колбэк, и wire-обработку — мёртвых пунктов быть не должно.
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
    onPhotoFromVk: () -> Unit = {},
    onVideo: () -> Unit = {},
    onVideoFromDevice: () -> Unit = {},
    onVideoLink: () -> Unit = {},
    onAudio: () -> Unit = {},
    onFile: () -> Unit = {},
    onFileFromVk: () -> Unit = {},
    onGift: () -> Unit = {},
    onPoll: () -> Unit = {},
    showCamera: Boolean = true,
    showPhotoFromVk: Boolean = false,
    showVideo: Boolean = true,
    showVideoFromDevice: Boolean = false,
    showVideoLink: Boolean = false,
    showMusic: Boolean = true,
    showFile: Boolean = true,
    showFileFromVk: Boolean = false,
    showGift: Boolean = false,
    showPoll: Boolean = false,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        // 1. Фото с устройства (photo picker).
        DropdownMenuItem(
            text = { Text("Фото") },
            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
            onClick = {
                onDismissRequest()
                onPhoto()
            },
        )
        // 2. Фото с камеры (TakePicture-контракт вызывающей стороны).
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
        // 3. Фото из VK (альбомы пользователя через photosGetAll — attach без upload).
        if (showPhotoFromVk) {
            DropdownMenuItem(
                text = { Text("Фото из VK") },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onPhotoFromVk()
                },
            )
        }
        // 4. Видео из библиотеки VK.
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
        // 5. Видео с устройства (video.save isClips=false + videoUploadFile — только пост).
        if (showVideoFromDevice) {
            DropdownMenuItem(
                text = { Text("Видео с устройства") },
                leadingIcon = { Icon(Icons.Outlined.Videocam, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onVideoFromDevice()
                },
            )
        }
        // 6. Видео по ссылке (video.save link_url — только пост, как в VK web).
        if (showVideoLink) {
            DropdownMenuItem(
                text = { Text("Видео по ссылке") },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onVideoLink()
                },
            )
        }
        // 7. Музыка из библиотеки VK.
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
        // 8. Файл с устройства (docs-пайплайн вызывающей стороны).
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
        // 9. Файл из VK (документы пользователя через docsGet — attach без upload).
        if (showFileFromVk) {
            DropdownMenuItem(
                text = { Text("Файл из VK") },
                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onFileFromVk()
                },
            )
        }
        // 10. Подарок (только для личных сообщений).
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
        // 11. Опрос (конструктор polls.add — только пост-композер, как в VK web).
        if (showPoll) {
            DropdownMenuItem(
                text = { Text("Опрос") },
                leadingIcon = { Icon(Icons.Outlined.Poll, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onPoll()
                },
            )
        }
        // Если все пункты скрыты — покажем заглушку, чтобы пользователь
        // понимал, что меню пустое, а не зависло.
        if (!showCamera && !showPhotoFromVk && !showVideo && !showVideoFromDevice &&
            !showVideoLink && !showMusic && !showFile && !showFileFromVk &&
            !showGift && !showPoll
        ) {
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
