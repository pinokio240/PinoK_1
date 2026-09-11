package re.pinok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import re.pinok.data.model.Attachment
import re.pinok.data.model.PhotoSizes
import re.pinok.data.model.Track
import re.pinok.data.model.Video

/**
 * #COMMENT-ATTACH-EVERYWHERE (волна 22): ОБЩИЙ компактный рендер вложений комментария.
 * До волны 22 существовал только приватный CommentAttachments в PostDetailScreen
 * (Fix #237), а инлайн-комментарии ленты (CommentsBottomSheet) не показывали вложения
 * ВООБЩЕ — юзер: «содержимое комментариев (например видео или стикер) не отображается».
 *
 * Поддерживаемые типы (как VK web в комментариях):
 *  - photo  — компактная сетка (1 → крупно 200dp, 2 → 2 колонки, 3+ → 3 колонки);
 *  - video  — превью 16:9 с кнопкой play, названием и длительностью;
 *  - audio  — ВОСПРОИЗВОДИМЫЙ список треков комментария (#AUDIO-COMMENTS, волна 37):
 *    аудио собираются в блок AudioAttachmentList (тап = play/pause, список треков
 *    комментария = очередь; трек без URL резолвится через audioGetById + al_audio).
 *    Раньше здесь была статичная строка CommentAudioRow без click-обработчика —
 *    тап ничего не делал (жалоба пользователя 2026-09-11: «Аудио треки в
 *    комментариях не воспроизводятся»);
 *  - doc    — чип документа (название.ext, размер); голосовое (audio_msg) → voice-чип;
 *  - audio_message — voice-чип (длительность);
 *  - link   — карточка ссылки (заголовок/хост/описание);
 *  - sticker — СТИКЕР: у wall-комментариев VK отдаёт {sticker_id, product_id} БЕЗ
 *    images[] (в отличие от messages.*), поэтому URL строится по CDN-паттерну
 *    VK web, доказанному дампом снапшота (upload/snap2308, promoted_stickers_urls):
 *    "https://vk.ru/sticker/1-{sticker_id}-{size}" (реальные примеры: 1-1909-64,
 *    1-1913-64; бакеты 64/128/256/512). Если images[] всё же есть (разные методы) —
 *    предпочитаем renderUrl модели (Fix #229).
 *
 * Честные отклонения (KDoc вместо заглушек): poll/graffiti/gift/wall-репост в
 * комментариях не рендерятся (низкая встречаемость, отдельные UI-сущности) —
 * попадают в `else` без визуального следа, как и в VK web-упрощении.
 */
@Composable
fun CommentAttachmentsView(
    attachments: List<Attachment>,
    onVideoClick: (Video) -> Unit = {},
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
) {
    if (attachments.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Фото: все фото-вложения в единую компактную сетку.
        val photoUrls: MutableList<String> = MutableList(0) { "" }
        for (att in attachments) {
            // NULL-ЯВНО: Gson-цепочка (attachment.photo опционален по схеме VK).
            val photo = att.photo
            if (att.type == "photo" && photo != null) {
                // NULL-ЯВНО: bestUrl может не найти подходящего size — фото пропускаем.
                val url = PhotoSizes.bestUrl(photo.sizes)
                if (url != null) photoUrls.add(url)
            }
        }
        if (photoUrls.isNotEmpty()) {
            CommentPhotosGrid(urls = photoUrls, onClick = { idx -> onPhotoClick(photoUrls, idx) })
        }

        // #AUDIO-COMMENTS (волна 37): аудио-вложения комментария собираются в список
        // и рендерятся единым блоком AudioAttachmentList (тап = play/pause, треки
        // комментария = очередь, резолв URL внутри). Раньше аудио рендерилось
        // статичной CommentAudioRow без click-обработчика — тап ничего не делал
        // (жалоба 2026-09-11: «Аудио треки в комментариях не воспроизводятся»).
        val audioTracks = mutableListOf<Track>()
        for (att in attachments) {
            // NULL-ЯВНО: Gson-цепочка (attachment.audio опционален по схеме VK).
            val audio = att.audio
            if (att.type == "audio" && audio != null) audioTracks.add(audio)
        }
        if (audioTracks.isNotEmpty()) {
            AudioAttachmentList(tracks = audioTracks)
        }

        // Остальные вложения по порядку следования в комментарии.
        for (att in attachments) {
            when (att.type) {
                "photo" -> { /* уже в сетке выше */ }
                "video" -> {
                    // NULL-ЯВНО: Gson-цепочка.
                    val video = att.video
                    if (video != null) {
                        CommentVideoThumb(video = video, onClick = { onVideoClick(video) })
                    }
                }
                "audio" -> { /* уже в блоке AudioAttachmentList выше (#AUDIO-COMMENTS) */ }
                "doc" -> {
                    // NULL-ЯВНО: Gson-цепочка; isVoiceMessage не смарт-кастит audioMsg
                    // (это getter другого класса) — явная проверка.
                    val doc = att.doc
                    if (doc != null) {
                        val voice = doc.audioMsg
                        if (voice != null) {
                            CommentVoiceChip(durationSec = voice.duration)
                        } else {
                            CommentDocChip(title = doc.title, ext = doc.ext, sizeBytes = doc.size)
                        }
                    }
                }
                "audio_message" -> {
                    // NULL-ЯВНО: Gson-цепочка.
                    val am = att.audioMessage
                    if (am != null) CommentVoiceChip(durationSec = am.duration)
                }
                "link" -> {
                    // NULL-ЯВНО: Gson-цепочка.
                    val link = att.link
                    if (link != null) CommentLinkCard(title = link.title, url = link.url, description = link.description)
                }
                "sticker" -> {
                    // NULL-ЯВНО: Gson-цепочка.
                    val sticker = att.sticker
                    if (sticker != null && sticker.stickerId > 0) {
                        // renderUrl (Fix #229) если images[] пришли; иначе CDN-паттерн VK web.
                        // NULL-ЯВНО: renderUrl опционален по определению модели.
                        val url = sticker.renderUrl ?: stickerCdnUrl(sticker.stickerId)
                        AsyncImage(
                            model = url,
                            contentDescription = "Стикер",
                            modifier = Modifier.size(128.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                else -> { /* poll / graffiti / gift / wall — см. KDoc выше */ }
            }
        }
    }
}

/**
 * CDN-URL стикера VK по id (доказательство — дамп VK web в upload/snap2308,
 * promoted_stickers_urls: {"1909":"https://vk.ru/sticker/1-1909-64", ...}).
 * Бакеты размеров: 64/128/256/512; для комментариев берём 256 (компактно и чётко).
 */
internal fun stickerCdnUrl(stickerId: Int, size: Int = 256): String =
    "https://vk.ru/sticker/1-" + stickerId + "-" + size

/** Формат длительности секунды → «m:ss» (аудио/видео/голосовые в комментариях). */
internal fun formatCommentDuration(totalSec: Int): String {
    val safe = if (totalSec > 0) totalSec else 0
    val m = safe / 60
    val s = safe % 60
    val ss = if (s < 10) "0" + s else "" + s
    return "" + m + ":" + ss
}

/** Компактная сетка фото комментария (1 — крупно, 2 — в ряд, 3+ — 3 колонки). */
@Composable
private fun CommentPhotosGrid(urls: List<String>, onClick: (Int) -> Unit) {
    val cols = when {
        urls.size == 1 -> 1
        urls.size == 2 -> 2
        else -> 3
    }
    val cellSize = if (cols == 1) 200.dp else 96.dp
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val rows = (urls.size + cols - 1) / cols
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until cols) {
                    val idx = row * cols + col
                    if (idx < urls.size) {
                        AsyncImage(
                            model = urls[idx],
                            contentDescription = "Фото из комментария",
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onClick(idx) },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

/** Превью видео из комментария: 16:9, play по центру, снизу название + длительность. */
@Composable
private fun CommentVideoThumb(video: Video, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Воспроизвести видео",
            tint = Color.White,
            modifier = Modifier.size(40.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatCommentDuration(video.duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

// CommentAudioRow УДАЛЕН (#AUDIO-COMMENTS, волна 37): был статичной строкой без
// click-обработчика («без плеера — как VK web»), тап ничего не делал. Аудио
// комментария теперь рендерится воспроизводимым блоком AudioAttachmentList
// (см. сбор audioTracks в CommentAttachmentsView выше).

/** Чип документа из комментария: «название.ext · размер». */
@Composable
private fun CommentDocChip(title: String, ext: String, sizeBytes: Long) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = "Документ",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title + "." + ext + " · " + formatBytesComment(sizeBytes),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/** Чип голосового сообщения: длительность (воспроизведение — вне волны 22). */
@Composable
private fun CommentVoiceChip(durationSec: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Outlined.Mic,
            contentDescription = "Голосовое сообщение",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = formatCommentDuration(durationSec),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
        )
    }
}

/** Карточка ссылки из комментария: заголовок / хост / описание (до 2 строк). */
@Composable
private fun CommentLinkCard(title: String?, url: String, description: String?) {
    // NULL-ЯВНО: Gson-поля опциональны — явная развязка вместо элвиса.
    val safeTitle: String = title ?: url
    val safeDescription: String = description ?: ""
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Outlined.Link,
                contentDescription = "Ссылка",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = safeTitle,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
        Text(
            text = url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        if (safeDescription.isNotBlank()) {
            Text(
                text = safeDescription,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** Человекочитаемый размер файла для чипа документа (Б/КБ/МБ/ГБ). */
internal fun formatBytesComment(bytes: Long): String {
    val safe = if (bytes > 0) bytes else 0L
    val kb = safe / 1024
    if (kb < 1) return "" + safe + " Б"
    val mb = kb / 1024
    if (mb < 1) return "" + kb + " КБ"
    val gb = mb / 1024
    if (gb < 1) return "" + mb + " МБ"
    return "" + gb + " ГБ"
}
