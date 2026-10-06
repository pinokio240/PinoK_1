package re.pinok.ui.screens.im

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
// #IM-SEARCH (Fix #394): scrim+slide паттерн правой панели (образец FeedRightPanel)
// для «Поиск по постам» в канальном режиме.
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
// #IM-SEARCH (Fix #394): лупа в шапке диалога/канала (снапшот 29-a:
// search_outline_24 «Поиск по каналу» / поиск по сообщениям).
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Image
// W30-1 #IM-UNREAD-MENU: иконка «непрочитанным/прочитанным» — та же, что в
// long-press меню списка диалогов (MessagesScreen:37, Fix #274) — паритет UX.
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.automirrored.outlined.Article
// #CHANNEL-POST-UI (2026-10-04): иконки статуса канального поста — «глаз»
// просмотров (снапшот web vkuiIcon view_12), иконка комментариев
// (vkuiIcon comment_outline_16) и шеврон комментариев
// (ChannelPostPrimaryComments__content: подпись + chevron_16).
// Порядок футера канального поста «как в ВК» (снапшот ChannelPostPrimary__footer):
// реакционные чипы (ReactionChip: эмодзи + число) → мета просмотров
// (ChannelPostMeta__views: «глаз» + «1,3K») → строка комментариев
// (ChannelPostPrimaryComments: comment_outline_16 + «N комментарий» + chevron_16).
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
// #CHANNEL-WALL-MODE (Fix #393): карточки постов канала (баннер закрепа).
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
// #IM-SEARCH (Fix #394): разделители результатов панели «Поиск по постам».
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
// #CHANNEL-WALL-MODE (Fix #393): optimistic-карты лайков постов канала.
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.blur
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Attachment
import re.pinok.data.model.GiftItem
import re.pinok.data.model.Message
// #CHANNEL-WALL-MODE (Fix #393): посты канала = посты сообщества (wall.get).
import re.pinok.data.model.Post
import re.pinok.data.model.PhotoSizes
// #CHANNEL-PHOTOS (2026-10-03): альбомы/фото канала (photos.getAlbums/photos.get).
import re.pinok.data.model.Album
import re.pinok.data.model.PhotoItem
import re.pinok.data.model.PhotosFeedResult
import re.pinok.data.model.DocFile
import re.pinok.data.model.ReactionItem
import re.pinok.data.model.MessageReaction
import re.pinok.data.model.Track
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.data.model.VideoMessage
import re.pinok.feature.audio.AudioInlineRenderer
import re.pinok.feature.photos.InlinePhotoItem
import re.pinok.feature.photos.PhotosInlineRenderer
import re.pinok.ui.anim.LocalAnimScale
import re.pinok.ui.anim.LocalStickerPhotoScale
import re.pinok.ui.anim.springScaled
import re.pinok.ui.anim.tweenScaled
import re.pinok.ui.theme.UiScale
import re.pinok.media.VoiceRecorder
import java.text.DecimalFormat
import re.pinok.realtime.LongPollEvent
import re.pinok.ui.components.ForwardDialog
import re.pinok.ui.components.AttachmentPickerSheet
import re.pinok.ui.components.AttachmentPickerTab
import re.pinok.ui.components.UnifiedAttachMenu
import re.pinok.ui.components.VideoMessageShapes
import re.pinok.util.AppLog
import re.pinok.util.toChatDate
import re.pinok.util.toDayKey
import re.pinok.util.toMsgTime
import re.pinok.util.toRecordingTimeString
import androidx.activity.compose.rememberLauncherForActivityResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import re.pinok.ui.components.PhotoViewer
// #CHANNEL-WALL-MODE (Fix #393): «Поделиться» поста канала — существующий
// компонент (тот же, что в CommunityScreen/UserProfileScreen).
import re.pinok.ui.components.ShareSheet
import re.pinok.ui.components.PendingPhotosBar
import re.pinok.ui.components.PendingPhoto
import re.pinok.ui.components.nextPendingPhotoId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
// #CHANNEL-WALL-MODE (Fix #393): переиспользование пост-компонента стены профиля
// (public, второй вызов UserProfileScreen:768) вместо копипасты + PostHolder для
// имени сообщества в PostDetailScreen.
import re.pinok.ui.screens.profile.WallPostCard
import re.pinok.ui.screens.profile.VideoThumbnail
import re.pinok.ui.components.AudioAttachmentList
import re.pinok.ui.navigation.PostHolder

// VK reaction IDs → emoji.
// #REACTION-WEB-MAP (волна 32): VK web словарь 909189 (снапшот
// Мессенджер_меню_сообщения, lang0_2.js me_message_reaction_text_*):
// 1=Сердце 2=Огонь 3=Смеюсь до слёз 4=Большой палец вверх 5=Неординарно(💩)
// 6=Вопросы(❓) 7=Плачу. Старая карта (1=👍…) промахивалась мимо серверных id —
// double-click «❤️» серверно ставил 🔥. Слоты 8+ НЕ заполняются: id→эмодзи за
// пределами 1-7 достоверно неизвестен (словарь даёт только имена) — не выдумывать
// (долг: полная сетка через messages.getReactionsAssets).
private val REACTION_EMOJIS = listOf(
    1 to "\u2764\uFE0F",   // ❤️ Сердце
    2 to "\uD83D\uDD25",   // 🔥 Огонь
    3 to "\uD83D\uDE02",   // 😂 Смеюсь до слёз
    4 to "\uD83D\uDC4D",   // 👍 Большой палец вверх
    5 to "\uD83D\uDCA9",   // 💩 Неординарно
    6 to "\u2753",         // ❓ Вопросы
    7 to "\uD83D\uDE2D",   // 😭 Плачу
)

// P0.1: typing indicator — VK resends typing events every ~4s while user keeps typing.
// If no new event arrives within this window, we assume the user stopped typing.
// #TYPING-FIX: 6с → 5с — VK web паттерн: индикатор живёт ~5 секунд без продления
// (VK присылает событие каждые ~4с, окно 5с не мигает между событиями и гаснет
// через 5с после последнего).
private const val TYPING_TIMEOUT_MS = 5_000L

// 18-θ (#TYPING-SEND): троттлинг исходящего messages.setActivity (type=typing).
// VK ограничивает частоту вызова; индикатор оппонента живёт ~5с, поэтому троттлинг
// 3с держит индикатор живым при непрерывном наборе и без спама запросами.
private const val TYPING_SEND_THROTTLE_MS = 3_000L

// Fix #244: multi-select — передаём состояние выбора во вложенные Composable
// (PhotoGrid, VideoAttachmentCard, VoiceMessageBubble, LinkAttachmentCard,
// DocAttachmentCard, WallAttachmentCard, ReplyBadge, PollAttachmentRow) через
// CompositionLocal, чтобы не раздувать сигнатуры.
// #ARCH-CONTAINERS (1.5-а/1.5-б): фото/аудио-вложения делегируются контейнерам
// (PhotosInlineRenderer/AudioInlineRenderer) — контейнер CompositionLocal хоста
// не знает, selection решает ХОСТ: колбэки onOpen/onPlay/onLongPress, которые
// ветка host-маппинга собирает с sel (тот же контракт — поведение прежнее).
//
// До фикса: каждое вложение имело .clickable { ... } без проверки selectionMode
// и без onLongPress. Child clickable поглощал DOWN-событие → parent bubble
// combinedClickable.onLongClick не срабатывал по площади вложения. Результат:
// (1) long-press по фото/видео/голосовому не открывал context menu и не входил
//     в selection;
// (2) в selection mode тап по вложению открывал контент (PhotoViewer/плеер/
//     браузер) вместо toggle выделения.
//
// Теперь каждое вложение читает LocalAttachmentSelection и:
// - в selection mode → onToggleSelection() вместо открытия контента;
// - long-press → onLongPress() (тот же callback что у parent bubble) —
//   прямой вход в selection или context menu.
data class AttachmentSelectionState(
    val selectionMode: Boolean,
    val onToggleSelection: () -> Unit,
    val onLongPress: () -> Unit,
)
val LocalAttachmentSelection = staticCompositionLocalOf<AttachmentSelectionState?> { null }
private fun reactionEmoji(id: Int): String =
    REACTION_EMOJIS.firstOrNull { it.first == id }?.second ?: "\u2753" // ❓

// ═══ #ARCH-CONTAINERS (Этап 1.4): делегирование рендера вложений контейнеру ═══

/**
 * Спрашивает реестр: не рендерит ли какой-нибудь контейнер вложение такого типа
 * (контракт [re.pinok.contracts.AttachmentRenderer], хост спрашивает canHandle
 * по mime/типу). С Этапа 1.5-а рендерер публикует контейнер :feature:photos
 * (PhotosAttachmentRenderer: kind="photo", mime image-семейство) → хост
 * делегирует рендер по rendererKey (см. [hostRendererComposable]). Контейнера
 * нет (или ключ неизвестен) → заглушка «скачать файл» — graceful-деградация
 * по плану «Правило владения UI», ядро от контейнера не зависит.
 */
private fun attachmentRendererFor(kind: String, mimeType: String): re.pinok.contracts.AttachmentRenderer? {
    val renderers = re.pinok.contracts.ContainerRegistry
        .find<re.pinok.contracts.AttachmentRenderer>()
        .sortedBy { it.order }
    return renderers.firstOrNull { it.canHandle(mimeType, kind) }
}

/**
 * host-маппинг rendererKey → компосабл (тот же паттерн, что NavEntry.route →
 * destination в SovaNavHost): контейнер compose-типов не знает, компосабл
 * получает ГОТОВЫЕ данные вложения. rendererKey, неизвестный хосту → null →
 * заглушка «скачать файл» (не падаем).
 *
 * #ARCH-CONTAINERS (Этап 1.5-а): первая запись — "photos_inline" →
 * [PhotosInlineRenderer] из :feature:photos (инлайн-рендер фото-сетки перенесён
 * туда; ветка «контейнера нет» — заглушка PhotoAttachmentsStub, осознанная
 * деградация по плану). Контекст хоста прокидывается параметрами/колбэками —
 * контейнер хост-типов не знает: selection-режим (Fix #244), PhotoViewer
 * (onPhotoClick), пользовательский масштаб стикер-фото (Fix #228,
 * LocalStickerPhotoScale → Int) остаются в хосте.
 */
private fun hostRendererComposable(
    rendererKey: String,
    stickerScalePct: Int,
    onPhotoOpen: (url: String) -> Unit,
    onLongPress: () -> Unit,
): (@Composable (List<InlinePhotoItem>) -> Unit)? =
    when (rendererKey) {
        "photos_inline" -> { items ->
            PhotosInlineRenderer(
                items = items,
                stickerScalePct = stickerScalePct,
                onOpen = onPhotoOpen,
                onLongPress = onLongPress,
            )
        }
        else -> null
    }

/**
 * #ARCH-CONTAINERS (Этап 1.5-б): host-маппинг rendererKey → компосабл для
 * АУДИО-вложений — вторая запись реестра рендереров, форма данных другая
 * (одно вложение = одна строка, не список) → отдельная функция по образцу
 * [hostRendererComposable]. "audio_inline" → [AudioInlineRenderer] из
 * :feature:audio (инлайн-рендер строки аудио-вложения #59 перенесён туда;
 * ветка «контейнера нет» — заглушка AudioAttachmentsStub, осознанная
 * деградация по плану — воспроизведение при этом живо: тап → onAudioClick,
 * т.е. PlayerConnection хоста, рендерер только UI). Контекст хоста
 * прокидывается параметрами/колбэками — контейнер хост-типов не знает:
 * selection-режим (Fix #244), запуск трека (onAudioClick → PlayerConnection)
 * остаются в хосте; данные передаются примитивами (title/artist/durationSec —
 * поля data.model.Track, распакованные хостом).
 */
private fun hostAudioRendererComposable(
    rendererKey: String,
    textColor: Color,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
): (@Composable (title: String, artist: String, durationSec: Int) -> Unit)? =
    when (rendererKey) {
        "audio_inline" -> { title, artist, durationSec ->
            AudioInlineRenderer(
                title = title,
                artist = artist,
                durationSec = durationSec,
                textColor = textColor,
                onPlay = onPlay,
                onLongPress = onLongPress,
            )
        }
        else -> null
    }

/**
 * Fix #296: проверяет, что сообщение [msg] входит в диапазон «прочитанных
 * до [upToCmid]» для VK LongPoll code 6/7.
 *
 * VK LP code 6 (ReadInbox) и code 7 (ReadOutbox) возвращают в ev[2]
 * conversation_message_id (cmid) — локальный счётчик диалога. Поэтому
 * приоритетно сравниваем по [Message.conversationMessageId].
 *
 * Fallback на [Message.id] (message_id) — для старых сообщений без cmid
 * (action-сообщения, сообщения до миграции API 5.x).
 *
 * @param msg       проверяемое сообщение
 * @param upToCmid  cmid из LP event (ev[2])
 * @return true если сообщение считается прочитанным
 */
private fun isReadUpTo(msg: Message, upToCmid: Long): Boolean {
    val cmid = msg.conversationMessageId
    return if (cmid != null && cmid > 0) {
        cmid <= upToCmid
    } else {
        // Fallback: message_id. Положительные id — реальные серверные.
        // Отрицательные (optimistic id = -System.currentTimeMillis())
        // никогда не считаем прочитанными — ждём серверного подтверждения.
        msg.id > 0 && msg.id <= upToCmid
    }
}

/**
 * P1.1: Элемент списка чата — sealed class для унифицированного рендера
 * сообщений, date-separator'ов и unread-divider'а в одном LazyColumn.
 *
 * Список строится в порядке reverseLayout (индекс 0 = новейшее = внизу экрана).
 * DateSeparator вставляется ПОСЛЕ последнего сообщения дня группы (т.е. выше
 * визуально, что правильно для sticky-header паттерна).
 * UnreadDivider вставляется ПОСЛЕ последнего непрочитанного (разделяет
 * непрочитанные снизу от прочитанных сверху).
 */
sealed class ChatListItem {
    /** Дата-сепаратор: «Сегодня», «Вчера», «12 июля». */
    data class DateSeparator(val dayKey: Int, val label: String) : ChatListItem()
    /** Разделитель «Непрочитанные сообщения». */
    object UnreadDivider : ChatListItem()
    /** Сообщение с pre-computed isGrouped флагом. */
    data class MessageRow(val message: Message, val isGrouped: Boolean) : ChatListItem()
}

/**
 * P1.1: Строит список [ChatListItem] из messages с учётом feature-flags.
 *
 * @param messages список сообщений (newest-first, соответствует reverseLayout)
 * @param groupingEnabled если true — вычисляется isGrouped для каждого сообщения
 * @param dateSeparatorsEnabled если true — вставляются DateSeparator между днями
 * @param unreadDividerEnabled если true — вставляется UnreadDivider перед
 *        группой прочитанных (после последнего непрочитанного входящего)
 */
private fun buildChatListItems(
    messages: List<Message>,
    groupingEnabled: Boolean,
    dateSeparatorsEnabled: Boolean,
    unreadDividerEnabled: Boolean,
): List<ChatListItem> {
    if (messages.isEmpty()) return emptyList()
    val result = ArrayList<ChatListItem>(messages.size + 8)

    // P1.1: находим индекс последнего непрочитанного входящего сообщения.
    // Это граница между непрочитанными (newer) и прочитанными (older).
    var lastUnreadIdx = -1
    for (i in messages.indices) {
        val m = messages[i]
        if (!m.isOut && m.readState == 0) lastUnreadIdx = i
    }

    for (i in messages.indices) {
        val msg = messages[i]
        // P1.3: вычисляем isGrouped (группируется с предыдущим = более новым).
        val isGrouped = groupingEnabled && i > 0 && run {
            val newer = messages[i - 1]
            val sameSender = newer.fromId == msg.fromId && newer.isOut == msg.isOut
            val timeGapSec = abs(newer.date - msg.date)
            val withinWindow = timeGapSec < 300L
            val neitherAction = !newer.isAction && !msg.isAction
            val neitherSpecial = !newer.hasReply && !newer.hasForwarded &&
                !msg.hasReply && !msg.hasForwarded
            sameSender && withinWindow && neitherAction && neitherSpecial
        }
        result.add(ChatListItem.MessageRow(msg, isGrouped))

        // P1.1: UnreadDivider — после последнего непрочитанного.
        if (unreadDividerEnabled && i == lastUnreadIdx && i < messages.lastIndex) {
            result.add(ChatListItem.UnreadDivider)
        }

        // P1.1: DateSeparator — после последнего сообщения дня группы.
        if (dateSeparatorsEnabled) {
            val isLastInDay = i == messages.lastIndex ||
                messages[i + 1].date.toDayKey() != msg.date.toDayKey()
            if (isLastInDay) {
                result.add(ChatListItem.DateSeparator(
                    dayKey = msg.date.toDayKey(),
                    label = msg.date.toChatDate(),
                ))
            }
        }
    }
    return result
}

/**
 * Экран диалога — история сообщений + отправка.
 *
 * Sprint 3, P1-6 (#9): Реакции на сообщения.
 *   - Long-press → контекстное меню (Копировать, Переслать, Реакция, Редактировать, Удалить).
 *   - Quick-react: двойной тап → ❤️.
 *   - ReactionBar под bubble отображает реакции.
 *   - ReactionPicker — панель эмодзи.
 *
 * Sprint 3, P1-7 (#10): Пересылка сообщений.
 * Sprint 3, P1-8 (#11): Редактирование / удаление сообщений.
 */

/** P3.6: лимит длины текста сообщения (VK API limit). */
private const val MSG_TEXT_LIMIT = 4096

/** P3.6: состояние dual send/mic button. */
private enum class SendButtonState { SUBMIT, MIC, EDIT, LOADING, LIMIT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    peerId: Long,
    peerTitle: String,
    peerPhoto: String?,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit = {},
    // P2.4: тап по wall-вложению → открыть пост в PostDetailScreen.
    onPostClick: (re.pinok.data.model.Post) -> Unit = {},
    // P2.1: тап по video-вложению → открыть в VideoPlayer.
    onVideoClick: (Video) -> Unit = {},
    // P2.2: тап по audio-вложению → запустить в PlayerConnection.
    onAudioClick: (re.pinok.data.model.Track) -> Unit = {},
    // P2.3: голосование в опросе — вызывается из PollAttachmentRow.
    onPollVote: (re.pinok.data.model.Poll, List<Long>) -> Unit = { _, _ -> },
    // P3.1: тап по «Информация о чате» → открыть ChatInfoScreen.
    onInfoClick: (Long) -> Unit = {},
    // P5.1: открыть URL во внутреннем браузере (WebView). Внешний браузер
    // обрабатывается внутри ChatDetailScreen через ACTION_VIEW — навигация
    // нужна только для внутреннего режима.
    onOpenUrlInternal: (String) -> Unit = {},
    // Fix #132: колбэк вызывается перед запуском камеры, чтобы SovaNavHost
    // сохранил peerId/title/photo чата в rememberSaveable. При process death
    // во время камеры SovaNavHost восстановит chat_detail по этим данным.
    onCameraLaunch: (peerId: Long, title: String, photo: String?) -> Unit = { _, _, _ -> },
    // #CALLS: кнопка «Позвонить» в шапке диалога.
    // #ARCH-CONTAINERS (Этап 1.4): nullable — хост передаёт колбэк ТОЛЬКО если
    // в реестре есть CallStarter (контейнер звонков). null → кнопка НЕ рендерится
    // (условие композиции, graceful-деградация без контейнера).
    onCallClick: ((peerId: Long, title: String, photo: String?) -> Unit)? = null,
    // Fix #132: колбэк вызывается в начале camera callback (до обработки),
    // чтобы очистить сохранённое состояние. Если камера отработала (успех или
    // отмена) — process death уже не должен возвращать в чат. Очищаем только
    // при реальном process death во время камеры (callback не успел вызваться).
    onCameraReturnConsumed: () -> Unit = {},
    // #VM-3 волна 3: видео-сообщение («кружок») — открыть рекордер
    // VideoMessageCreateScreen для текущего чата. Хост (SovaNavHost) передаёт
    // навигацию с peerId; дефолт — no-op (пункт не появляется в старых вызовах).
    onVideoMessage: (peerId: Long) -> Unit = {},
) {
    val app = SovaApp.get()
    // Fix #133: peerTitle/peerPhoto приходят из nav arguments (передаются из
    // списка диалогов). Если список не смог зарезолвить имя/аватарку (VK не
    // отдал profiles[]/groups[] для этого пира, а resolveMissingPeerInfo тоже
    // не нашёл) — параметры приходят как «Диалог»/null, и шапка чата навсегда
    // оставалась без имени/аватарки. Делаем их mutable и обновляем из
    // messagesGetConversationsById ниже (тот же запрос, что для pinned/mute).
    var currentTitle by remember(peerTitle) { mutableStateOf(peerTitle) }
    var currentPhoto by remember(peerPhoto) { mutableStateOf(peerPhoto) }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    // #74: профили отправителей для аватарок в чате
    var chatProfiles by remember { mutableStateOf<Map<Long, UserProfile>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // #IM-EMPTY-HONEST (волна 36): счётчик повторов первичной загрузки истории.
    // Кнопка «Повторить» в пустом состоянии (ошибка + нет сообщений) инкрементит
    // его → LaunchedEffect(peerId, historyReload) перезапускает загрузку целиком
    // (раньше ошибку первичной загрузки можно было вылечить только выходом из
    // чата и повторным входом).
    var historyReload by remember { mutableStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pageSize = 50
    var loadingOlder by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    // Пользователь у низу (новые сообщения). reverseLayout=true: индекс 0 = внизу.
    var isPinnedToNewest by remember { mutableStateOf(true) }
    // Sprint 3: ID сообщения для контекстного меню.
    var contextMsgId by remember { mutableStateOf<Long?>(null) }
    // Sprint 3: Показывать ли пикер реакций.
    var showReactionPicker by remember { mutableStateOf<Long?>(null) }
    // Sprint 3: режим редактирования.
    var editingMsgId by remember { mutableStateOf<Long?>(null) }
    // #IM-EDIT-ATTACH (HAR CHAT-EDIT-ATTACH-HAR-2026-10-01): вложения
    // редактируемого сообщения. Хранится отдельно от editingMsgId, чтобы:
    //  - при правке только текста передать на сервер полный список и СОХРАНИТЬ
    //    вложения (messages.edit переписывает attachment целиком);
    //  - при удалении файла (×) — убрать его из списка → снять на сервере;
    //  - при удалении ВСЕХ (список стал пустым) — не шлём attachment → VK снимает.
    var editingAttachments by remember { mutableStateOf<List<Attachment>?>(null) }
    // #IM-EDIT-ATTACH: attachment-токены ВНОВЬ добавленных при редактировании файлов
    // (uploadDocForMessage). Объединяются с существующими в editMessage.
    var editingAddedAttach by remember { mutableStateOf<List<String>>(emptyList()) }
    // #IM-EDIT-ATTACH: флаг «идёт загрузка добавленного файла» — show spinner в кнопке.
    var editingAddingFile by remember { mutableStateOf(false) }

    // 18-θ (#TYPING-SEND): исходящий «печатает» — messages.setActivity(type=typing,
    // peer_id) через VKA setActivity (преадд оркестратора волны 19). Паттерн VK web:
    // (1) мгновенная отправка на ПЕРВОМ изменении текста после паузы (сейчас - lastTypingSentAt
    // >= троттлинга); (2) дальше не чаще раза в 3с — VK ограничивает частоту, а индикатор
    // оппонента живёт ~5с (TYPING_TIMEOUT_MS), так что пауза индикатора не наступает;
    // (3) при остановке набора отправки прекращаются — индикатор гаснет сам за ~5с.
    // Редактирование (editingMsgId != null) — НЕ набор сообщения: сигнал не шлём
    // (VK web так же). Каналы (поле ввода скрыто, can_write.allowed=false) сюда не
    // доходят — inputText не меняется. Сбой индикации НЕ блокирует чат и НЕ показывает
    // тост (решение: потеря «печатает» у оппонента несущественна, честно в лог).
    var lastTypingSentAt by remember(peerId) { mutableStateOf(0L) }
    LaunchedEffect(peerId, inputText, editingMsgId) {
        if (inputText.isBlank()) return@LaunchedEffect
        if (editingMsgId != null) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < TYPING_SEND_THROTTLE_MS) return@LaunchedEffect
        lastTypingSentAt = now
        val ok = app.apiClient.setActivity(peerId)
        AppLog.d("ChatDetailScreen", "#TYPING-SEND setActivity peer=$peerId ok=$ok")
    }
    // #59: reply state — сообщение на которое отвечаем
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    // Fix #206: клик по плашке ответа.
    //   highlightedMsgId — id сообщения, к которому только что проскроллили (подсветка).
    //     Сбрасывается через 1.5с через LaunchedEffect (без анимации — просто смена фона).
    //   replyPreviewMsg — исходное сообщение, на которое ответили, если его НЕТ в
    //     загруженной истории. Показываем AlertDialog с текстом + кнопкой «показать в чате»
    //     (догрузка старой истории вверх до нахождения cmid).
    var highlightedMsgId by remember { mutableStateOf<Long?>(null) }
    var replyPreviewMsg by remember { mutableStateOf<Message?>(null) }
    var loadingReplyTarget by remember { mutableStateOf(false) }
    // Sprint 3: диалог пересылки.
    var showForwardDialog by remember { mutableStateOf(false) }
    var forwardMsgIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    // Fix #295: cmid-список для пересылки. VK API 5.221+ требует
    // conversation_message_ids в `forward` JSON — legacy message_id
    // (forwardMsgIds) больше не переносит вложения/файлы.
    var forwardMsgCmids by remember { mutableStateOf<List<Long>>(emptyList()) }
    // Вложения: отправка фото/файлов.
    var uploading by remember { mutableStateOf(false) }
    // Fix #232: предпросмотр файла перед отправкой.
    // Пользователь выбирает файл → он копируется в temp + показывается
    // превью-бар над полем ввода (иконка/миниатюра + имя + размер + ×).
    // Send кнопка отправляет файл (+ опциональный текст-подпись).
    // Fix #235 (multi-file): список выбранных файлов, ждущих отправки.
    // Юзер может выбрать несколько файлов за раз (OpenMultipleDocument maxItems=10),
    // плюс добирать ещё — суммарно до 10. Каждый показывается в PendingFilesBar
    // над полем ввода (иконка + имя + размер + ×). Send грузит батч.
    var pendingFiles by remember { mutableStateOf<List<PendingFileAttachment>>(emptyList()) }
    // Fix #234 (multi-photo preview): список выбранных фото, ждущих отправки.
    // Fix #235: обёрнуты в PendingPhoto с уникальным id (а не List<Uri>) — даёт
    // стабильные уникальные ключи для LazyRow даже при повторном выборе того же
    // фото. Раньше key="$i-$u" с экранированным $ → все ключи одинаковые → crash.
    var pendingPhotos by remember { mutableStateOf<List<PendingPhoto>>(emptyList()) }
    // Индекс фото в pendingPhotos, открытого в полноэкранном просмотрщике (null = закрыт).
    var previewPhotoIndex by remember { mutableStateOf<Int?>(null) }
    // Sprint 3 #12: голосовые сообщения — запись.
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var recordingAmplitude by remember { mutableFloatStateOf(0f) }
    // Sprint 3 #12 → Fix #115: история амплитуд для waveform как в VK Web
    // (VoiceRecording__svg — 200 столбиков 0..1). Храним до 300 семплов
    // (по ~50мс = 15с записи, достаточно для визуализации).
    val voiceAmplitudes = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    // Play-before-send: после stopRecording файл сохраняется для предпрослушивания.
    // null = нет отложенного голосового (обычное состояние).
    var pendingVoiceFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingVoiceDuration by remember { mutableIntStateOf(0) }
    var isPreviewingVoice by remember { mutableStateOf(false) }
    var previewProgress by remember { mutableFloatStateOf(0f) }
    var previewPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    // Fix #120: единый контроллер воспроизведения голосовых на весь чат.
    // Только одно голосовое играет одновременно — клик по другому останавливает текущее.
    val voicePlaybackController = remember { VoicePlaybackController() }
    // Sprint 3 #13 + Fix #201: единая панель эмодзи+стикеров с двумя вкладками.
    // Раньше было 2 отдельных панели (showEmojiPicker / showStickerPicker),
    // теперь одна с табами. tab=0 → эмодзи, tab=1 → стикеры.
    var showEmojiStickerPanel by remember { mutableStateOf(false) }
    var emojiStickerTab by rememberSaveable { mutableIntStateOf(0) }
    // #60: search mode
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<re.pinok.api.VKApiClient.MessageSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // #60: last activity (online status)
    var lastActivity by remember { mutableStateOf<re.pinok.api.VKApiClient.LastActivity?>(null) }
    // #60: deleted message (for undo/restore)
    var lastDeletedMsg by remember { mutableStateOf<Long?>(null) }
    var stickerPacks by remember { mutableStateOf<List<re.pinok.data.model.StickerPack>>(emptyList()) }
    var stickerLoading by remember { mutableStateOf(false) }
    var selectedStickerPack by remember { mutableIntStateOf(0) }
    // Sprint 3 #14: управление групповыми чатами.
    val isGroupChat = peerId >= 2_000_000_000L
    val localChatId = if (isGroupChat) peerId - 2000000000L else 0L
    var showChatMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var chatMembers by remember { mutableStateOf<List<re.pinok.api.VKApiClient.ChatMember>>(emptyList()) }
    var loadingMembers by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf(peerTitle) }

    // P0.1: typing indicator.
    // Map of typing userId -> timestamp (ms) when last typing event arrived.
    // Cleared per-user after TYPING_TIMEOUT_MS of inactivity.
    var typingUsers by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    val typingEnabled by app.prefs.data
        .map { it.msgTypingIndicator }
        .collectAsState(initial = true)

    // P0.3: pinned message bar.
    // Загружается через messagesGetConversationsById при открытии чата.
    // Только для group chats (peer.type == "chat"); для DM игнорируется.
    var pinnedMessage by remember { mutableStateOf<Message?>(null) }
    val pinBarEnabled by app.prefs.data
        .map { it.msgPinBar }
        .collectAsState(initial = true)

    // P1.3: message grouping — объединение последовательных сообщений от одного
    // отправителя в пределах 5 минут. Скрывает аватарку/имя у сгруппированных,
    // делает top corner radius плоским для визуального объединения.
    val groupingEnabled by app.prefs.data
        .map { it.msgGrouping }
        .collectAsState(initial = true)

    // P1.1: date separators + unread divider + scroll-to-bottom FAB.
    val dateSeparatorsEnabled by app.prefs.data
        .map { it.msgDateSeparators }
        .collectAsState(initial = true)
    val unreadDividerEnabled by app.prefs.data
        .map { it.msgUnreadDivider }
        .collectAsState(initial = true)
    val scrollFabEnabled by app.prefs.data
        .map { it.msgScrollFab }
        .collectAsState(initial = true)
    // P1.2: reply via swipe — свайп для ответа на сообщение.
    val swipeReplyEnabled by app.prefs.data
        .map { it.msgSwipeReply }
        .collectAsState(initial = true)
    // P2.6: read receipts (✓/✓✓) — статус прочтения исходящих.
    val readReceiptsEnabled by app.prefs.data
        .map { it.msgReadReceipts }
        .collectAsState(initial = true)

    // Fix #228: масштаб стикер-фото (0..40, % увеличения от оригинала).
    // Провайдится через LocalStickerPhotoScale в MessageBubble → применяется
    // к dispW/dispH стикер-фото (isStickerLike). 0 = исходный размер.
    val stickerPhotoScale by app.prefs.data
        .map { it.stickerPhotoScale }
        .collectAsState(initial = 0)

    // P2.5: multi-select mode — long-press → «Выбрать» → выделение нескольких
    // сообщений для массового Delete/Forward. Opt-in (default false).
    val multiSelectEnabled by app.prefs.data
        .map { it.msgMultiSelect }
        .collectAsState(initial = false)
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Fix #137: inline "session expired" dialog (instead of AuthActivity hiding the chat).
    // Shown when uploadAndSendPhoto fails due to access_token invalidation (VK API error
    // 5/1117) — MainActivity's AuthActivity launch is suppressed via
    // SovaApp.suppressNextAuthRelaunch, and the user is offered "Перезайти"/"Остаться"
    // without leaving the conversation.
    var showSessionExpiredDialog by remember { mutableStateOf(false) }

    // Fix #218 (P1.3): observe tokenInvalidationTicks — если suppress активен
    // (AuthActivity не запустится автоматически), показываем inline dialog вместо
    // overlay. Это покрывает все API-вызовы (LongPoll, messages, photo upload),
    // а не только photo upload как Fix #137.
    val tokenInvalidationTick by app.tokenInvalidationTicks.collectAsState()
    var lastHandledInvalidationTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tokenInvalidationTick) {
        if (tokenInvalidationTick <= lastHandledInvalidationTick) return@LaunchedEffect
        lastHandledInvalidationTick = tokenInvalidationTick
        // Показываем inline dialog ТОЛЬКО если suppress активен (т.е. MainActivity
        // НЕ запустит AuthActivity автоматически). Если suppress не активен —
        // AuthActivity запустится, диалог не нужен (пользователь увидит AuthActivity).
        val nowMs = System.currentTimeMillis()
        val suppressActive = app.suppressAuthRelaunchUntilMs > 0L &&
                             nowMs < app.suppressAuthRelaunchUntilMs
        if (suppressActive) {
            AppLog.i("ChatDetailScreen", "Token invalidated (tick=$tokenInvalidationTick) " +
                "while suppressAuthRelaunch active — showing inline session-expired dialog (Fix #218)")
            showSessionExpiredDialog = true
        }
        // Если suppress НЕ активен — MainActivity сам запустит AuthActivity,
        // мы ничего не делаем (старый flow).
    }

    // P3.5: multi-file upload — выбор до 10 фото за раз (PickMultipleVisualMedia).
    val multiFileEnabled by app.prefs.data
        .map { it.msgMultiFile }
        .collectAsState(initial = true)
    // P3.6: dual send/mic button — state machine (EDIT/LOADING/LIMIT/MIC/SUBMIT).
    val dualButtonEnabled by app.prefs.data
        .map { it.msgDualButton }
        .collectAsState(initial = false)
    // P3.2: mute/unmute chat — toggle уведомлений.
    val muteEnabled by app.prefs.data
        .map { it.msgMute }
        .collectAsState(initial = true)
    var muted by remember { mutableStateOf(false) }
    // W30-1 #IM-UNREAD-MENU: локальная метка «непрочитанный» для меню шапки чата
    // (паритет VK web — в меню чата есть «Отметить непрочитанным»). chat-стейта с
    // unreadCount в этом экране нет (chat — локальная val внутри LaunchedEffect),
    // поэтому стартовое значение заполняется из серверного unread_count при
    // загрузке chat info (messagesGetConversationsById, см. ниже).
    var hasUnreadMark by remember { mutableStateOf(false) }
    // P3.1: ChatInfo screen — отдельный экран информации о чате.
    val chatInfoEnabled by app.prefs.data
        .map { it.msgChatInfo }
        .collectAsState(initial = true)
    // P3.4: channel mode — отдельный UX для каналов (broadcast-сообщества).
    // Если диалог — канал (peerId < 0 && can_write.allowed == false), скрываем
    // composer и показываем ChannelFooterBar с mute/leave действиями.
    val channelModeEnabled by app.prefs.data
        .map { it.msgChannelMode }
        .collectAsState(initial = true)
    // P3.4: определяется при загрузке chat info (messagesGetConversationsById).
    var isChannel by remember { mutableStateOf(false) }
    // #IM-CHANNEL-FIX (56-b-3): can_write-гейт — «canWrite известен и запрещён».
    // Задаётся из chat state (messagesGetConversationsById) в обоих местах его
    // резолва (ранний канальный блок + поздний P0.3-блок LaunchedEffect).
    // Композер должен скрываться ВСЕГДА когда писать нельзя — НЕ зависимо от
    // тумблера channelModeEnabled (админ канала с allowed=true сюда не попадает).
    var channelWriteDenied by remember { mutableStateOf(false) }
    // #IM-CHANNEL-FIX (56-b-5): уведомления канала — реактивно из кэша SovaPrefs.
    // Канал по умолчанию МОЛЧИТ (дефолт VK web, снапшот 55) → initial=false,
    // true появляется только из кэша (юзер включил тумблером / сервер отдал
    // is_enabled=true при первичном merge списка).
    val channelNotifEnabled by app.prefs.data
        .map { it.channelNotifEnabledIds.contains(peerId) }
        .collectAsState(initial = false)

    // ═══ #CHANNEL-WALL-MODE (Fix #393) ═══════════════════════════════════
    // Root-cause «каналы — диалоги не открываются, ошибки»: контент канала
    // (peer = -<group_id>, can_write.allowed == false) живёт в wall.get
    // (посты сообщества, снапшот 29-a: blog-бэкенд веба), а НЕ в
    // messages.getHistory — VK для таких пиров возвращает ошибку/пусто,
    // и экран показывал generic-ошибку. Теперь канал определяется ДО
    // загрузки messages-истории (см. LaunchedEffect(peerId)) и рендерится
    // wall-режим: посты WallPostCard + баннер закрепа + футер-уведомления.
    var channelPosts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var channelPostsLoading by remember { mutableStateOf(false) }
    var channelPostsError by remember { mutableStateOf<String?>(null) }
    var channelPostsEnd by remember { mutableStateOf(false) }
    var channelPostsLoadingMore by remember { mutableStateOf(false) }
    // Fix #394 #CHANNEL-WALL-FALLBACK: wall.get для канала может быть ЗАКРЫТ
    // навсегда (баг-репорт 14.09: «диалоги каналов не открываются» —
    // wall.get err 15 «Access denied: wall is disabled» у сообщества
    // -236041950 «Время Перемен. Новости» с удалённой/отключённой стеной;
    // кнопка «Повторить» в этом случае бесполезна — ошибка постоянная).
    // channelWallFallback=true → экран переключается на СТАНДАРТНЫЙ
    // messages-режим (история диалога канала через messages.getHistory —
    // сообщения канала в диалоге есть, иначе бы бейдж/LP-события не приходили);
    // read-only футер канала и шапка сохраняются (isChannel не сбрасываем).
    // channelFallbackTried — попытка фолбэка ОДИН раз за открытие экрана:
    // «Повторить» wall после неудачи истории не зацикливает запросы.
    var channelWallFallback by remember { mutableStateOf(false) }
    var channelFallbackTried by remember { mutableStateOf(false) }
    // Task 67 #CHANNELS-HIST: контент канала загружен через channels.getHistory
    // (стандартный messages-режим, channelWallFallback=true). Пагинация «старее»
    // обязана идти тем же методом (start_cmid=minCmid): messages.getHistory для
    // канальных пиров пуст (лог 14.09 22:17), wall.get — err 15.
    var channelHistoryMode by remember { mutableStateOf(false) }
    // #CHANNEL-FILTERS (Task #CHANNELS-UI): активный фильтр ленты канала —
    // значение фильтра channels.getHistory: «donut»/«photo»/«video»/«audio»/
    // «doc». null = «Всё» (весь поток). Только для channelHistoryMode=канал.
    var channelFeedFilter by remember { mutableStateOf<String?>(null) }
    val channelListState = rememberLazyListState()
    // Подписчики в шапке канала (снапшот 29-a: «название + N подписчиков»).
    // -1 = ещё не получены (subtitle не рисуем).
    var channelSubscribers by remember { mutableStateOf(-1) }
    // GroupInfo сообщества-канала — для PostHolder.lastGroups (имя сообщества
    // в PostDetailScreen, паритет CommunityScreen).
    var channelGroup by remember { mutableStateOf<re.pinok.api.VKApiClient.GroupInfo?>(null) }
    // P0.7 (2026-10-04): админ канала имеет canWrite.allowed=true → Chat.isChannel
    // (Models.kt:758) = false → UI-панели канала (peerId<0 && isChannel) не
    // открывались, клик по шапке — no-op без else. Локально расширяем: канал
    // для UI = peerId<0 И (isChannel ИЛИ channelGroup != null). channelGroup
    // != null означает, что это group-чат с известной GroupInfo — то ли это
    // настоящий канал-подписчик (isChannel=true), то ли админ канала (isChannel=false,
    // но channelGroup загружена). Для обоих показываем панель канала. Все gated
    // UI-условия используют isChannelUi вместо isChannel (см. ChatDetailScreen.kt:
    // 3618, 5731, 5798, 5813, 5823, 5832, 5833). Логику send/block (channelWriteDenied,
    // 2541/2858) НЕ трогаем — там isChannel/peerId<0 корректны.
    val isChannelUi = isChannel || (peerId < 0 && channelGroup != null)
    // Optimistic-состояния лайков постов канала (паттерн ProfileScreen:
    // key "ownerId_id" → (isLiked, count) + in-flight guard).
    val channelLikeStates = remember { mutableStateMapOf<String, Pair<Boolean, Int>>() }
    val channelLikeInFlight = remember { mutableStateMapOf<String, Boolean>() }
    // «Поделиться» постом канала → существующий ShareSheet.
    var channelSharePost by remember { mutableStateOf<Post?>(null) }
    // Панель «Поиск по постам» (Fix #394 #IM-SEARCH, scrim+slide как FeedRightPanel).
    var showChannelSearch by remember { mutableStateOf(false) }
    // #CHANNEL-INFO-PANEL (2026-10-03): панель канала — аватар/название/подписчики,
    // «Открыть сообщество»/«Написать», ссылка и разделы Фото/Видео/Музыка/Файлы.
    // Открывается тапом по шапке канала (peerId<0 && isChannel). Аддитивно.
    var showChannelInfoPanel by remember { mutableStateOf(false) }
    // #CHANNELS-UI2 (Task 2): индекс активного закрепа в баннере-карусели стены.
    // Список закрепов — channelPosts.filter { isPinned == 1 }; при >1 показываем
    // точки/стрелки для переключения, при 1 — прежний одиночный баннер.
    var channelPinIndex by remember { mutableStateOf(0) }
    // #CHANNELS-COMMENTS (Task #CHANNELS-COMMENTS): таргет панели комментариев
    // канального поста. Pair<channelId(peerId), cmid/postId>; null = панель
    // закрыта. Только канальный режим (peerId<0), аддитивно.
    var channelCommentsTarget by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    // #CHANNEL-PHOTOS (2026-10-03): панель «Фотографии» канала — список альбомов
    // группы и сетка фото выбранного альбома. Открывается из панели канала
    // (showChannelInfoPanel) при клике на раздел «Фотографии»; показывается
    // только в канальном режиме (peerId<0 && isChannel). Аддитивно: обычные
    // диалоги/чаты не затрагиваются.
    var showChannelPhotos by remember { mutableStateOf(false) }
    // #CHANNEL-SECTIONS (2026-10-04): панели «Видео»/«Музыка»/«Файлы» канала.
    // Открываются из панели канала по соответствующим разделам; только канал.
    var showChannelVideo by remember { mutableStateOf(false) }
    var showChannelAudio by remember { mutableStateOf(false) }
    var showChannelFiles by remember { mutableStateOf(false) }
    // #CHANNEL-CLIPS (P0.5, 2026-10-04): панель «Клипы» канала —
    // shortVideo.getOwnerVideos(ownerId=-abs(peerId)). Открывается из панели
    // канала по разделу «Клипы»; только канал. Аддитивно: обычные диалоги не
    // затрагиваются.
    var showChannelClips by remember { mutableStateOf(false) }
    // ══════════════════════════════════════════════════════════════════════

    // P3.7: bubble-less дизайн — flat layout (без Card/bubble), как m.vk.ru.
    // Передаётся в MessageBubble для выбора стиля рендеринга.
    val bubblelessEnabled by app.prefs.data
        .map { it.msgBubbleless }
        .collectAsState(initial = false)

    // Context для кэша и т.д.
    val ctx = LocalContext.current
    // P5.1: открытие ссылок из чата во внутреннем браузере (WebView).
    val openLinksInternal by app.prefs.data
        .map { it.openLinksInInternalBrowser }
        .collectAsState(initial = false)
    // P5.1: состояние полноэкранного просмотрщика фото (список URL + начальный индекс).
    var photoViewerState by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // P5.1: единый обработчик клика по ссылке. Внешний браузер — ACTION_VIEW,
    // внутренний — навигация на InternalBrowserScreen (через onOpenUrlInternal).
    val onUrlClick: (String) -> Unit = { url ->
        if (openLinksInternal) {
            onOpenUrlInternal(url)
        } else {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "open url failed: ${e.message}")
            }
        }
    }

    fun startVoiceRecording() {
        try {
            // Если есть pendingVoiceFile (режим review) — продолжаем запись в тот же файл.
            val file = pendingVoiceFile ?: File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
            VoiceRecorder.startRecording(file)
            isRecording = true
            recordingSeconds = pendingVoiceDuration
            recordingAmplitude = 0f
            // Не очищаем voiceAmplitudes при resume — продолжаем историю.
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "startVoiceRecording error", e)
        }
    }

    // Permission launcher для RECORD_AUDIO.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoiceRecording() else {
            AppLog.w("ChatDetailScreen", "RECORD_AUDIO permission denied")
        }
    }

    fun reloadMessages() {
        scope.launch {
            try {
                val targetCount = maxOf(messages.size, pageSize)
                val fresh = app.apiClient.messagesGetHistory(peerId, count = targetCount)
                    .distinctBy { it.id }
                if (fresh.isNotEmpty()) {
                    messages = fresh
                    errorText = null
                }
            } catch (e: Exception) {
                AppLog.w("ChatDetailScreen", "reload error: ${e.message}")
            }
        }
    }

    // P0.25 #VOICE-ASR-FETCH: запрос transcript (расшифровки ASR) для voice-сообщений.
    // VK LongPoll НЕ возвращает transcript/transcript_state — VK готовит ASR на сервере
    // 5-30 сек после отправки. Запрашиваем messages.getById для свежих голосовых без
    // transcript, обновляем attachments в messages.
    // P0.25 #VOICE-ASR-FETCH: запрос transcript (расшифровки ASR) для voice-сообщений.
    // VK LongPoll НЕ возвращает transcript/transcript_state — VK готовит ASR на сервере
    // 5-30 сек после отправки. Запрашиваем messages.getById для свежих голосовых без
    // transcript, обновляем attachments в messages.
    // P0.25d: suspend — caller ждёт completion перед следующей проверкой hasPendingVoice.
    suspend fun fetchVoiceTranscripts() {
        // Фильтруем voice-сообщения без transcript (transcript==null ИЛИ state != "done").
        val voiceMsgIds = messages.filter { m ->
            m.attachments?.any { att ->
                val am = att.doc?.audioMsg ?: att.audioMessage
                am != null && (am.transcript.isNullOrBlank() || am.transcriptState != "done")
            } == true
        }.map { it.id }
        AppLog.i("ChatDetailScreen", "fetchVoiceTranscripts: found ${voiceMsgIds.size} pending voice messages (total ${messages.size})")
        if (voiceMsgIds.isEmpty()) return
        AppLog.i("ChatDetailScreen", "fetchVoiceTranscripts: requesting getById for ids=$voiceMsgIds")
        try {
            val resp = app.apiClient.messagesGetById(voiceMsgIds.take(50)) ?: run {
                AppLog.w("ChatDetailScreen", "fetchVoiceTranscripts: messagesGetById returned null")
                return
            }
            AppLog.i("ChatDetailScreen", "fetchVoiceTranscripts: response keys=${resp.keySet()}")
            val items = resp.getAsJsonArray("items") ?: run {
                AppLog.w("ChatDetailScreen", "fetchVoiceTranscripts: no items[] in response, raw=${resp.toString().take(300)}")
                return
            }
            AppLog.i("ChatDetailScreen", "fetchVoiceTranscripts: got ${items.size()} items")
            // Map messageId → updated audio_message transcript.
            val updates = mutableMapOf<Long, Pair<String?, String?>>()
            for (item in items) {
                if (!item.isJsonObject) continue
                val msgObj = item.asJsonObject
                val msgId = msgObj.get("id")?.takeIf { !it.isJsonNull }?.asLong ?: continue
                val atts = msgObj.getAsJsonArray("attachments") ?: continue
                for (att in atts) {
                    if (!att.isJsonObject) continue
                    val attObj = att.asJsonObject
                    // audio_message может быть внутри type="audio_message" или type="doc" (legacy).
                    val amObj = attObj.getAsJsonObject("audio_message")
                        ?: attObj.getAsJsonObject("doc")?.getAsJsonObject("audio_msg")
                    if (amObj == null) continue
                    val transcript = amObj.get("transcript")?.takeIf { !it.isJsonNull }?.asString
                    val state = amObj.get("transcript_state")?.takeIf { !it.isJsonNull }?.asString
                    AppLog.i("ChatDetailScreen", "fetchVoiceTranscripts: msg=$msgId transcript=${transcript?.take(40) ?: "null"} state=$state")
                    updates[msgId] = transcript to state
                }
            }
            if (updates.isEmpty()) {
                AppLog.w("ChatDetailScreen", "fetchVoiceTranscripts: no transcript updates found in response")
                return
            }
            // Apply updates to messages.
            messages = messages.map { m ->
                val upd = updates[m.id] ?: return@map m
                val newAtts = m.attachments?.map { att ->
                    val am = att.doc?.audioMsg ?: att.audioMessage
                    if (am != null) {
                        val newAm = am.copy(transcript = upd.first ?: am.transcript,
                                            transcriptState = upd.second ?: am.transcriptState)
                        val doc = att.doc
                        if (doc != null && doc.audioMsg != null) {
                            att.copy(doc = doc.copy(audioMsg = newAm))
                        } else {
                            att.copy(audioMessage = newAm)
                        }
                    } else att
                }
                if (newAtts != m.attachments) m.copy(attachments = newAtts) else m
            }
            AppLog.i("ChatDetailScreen", "fetchVoiceTranscripts: updated ${updates.size} voice messages")
        } catch (e: Exception) {
            AppLog.w("ChatDetailScreen", "fetchVoiceTranscripts error: ${e.message}")
        }
    }

    /**
     * Остановить запись и перейти в режим review (play-before-send).
     * Файл сохраняется в [pendingVoiceFile], отправка — через [sendPendingVoice].
     * Это соответствует VK Web: после stop видны кнопки resume / play / send.
     */
    fun stopVoiceRecordingForReview() {
        val file = VoiceRecorder.stopRecording() ?: return
        isRecording = false
        pendingVoiceFile = file
        pendingVoiceDuration = recordingSeconds
    }

    /**
     * Остановить и сразу отправить (без review) — для кнопки send во время записи.
     */
    fun stopAndSendVoice() {
        val file = VoiceRecorder.stopRecording() ?: return
        isRecording = false
        pendingVoiceFile = null
        pendingVoiceDuration = 0
        sending = true
        // FIX: запуск upload в appScope (процесс-живущий), иначе при уходе с
        // экрана чата rememberCoroutineScope отменял загрузку и голосовое
        // терялось на полпути (LeftCompositionCancellationException).
        app.appScope.launch {
            try {
                app.apiClient.sendVoiceMessage(peerId, file)
                // UI-обновление — в композиционном scope (no-op если экран закрыт).
                scope.launch {
                    reloadMessages()
                    listState.animateScrollToItem(0)
                }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "sendVoice error", e)
                scope.launch { errorText = "Ошибка отправки голосового" }
            } finally {
                file.delete()
                voiceAmplitudes.clear()
                scope.launch { sending = false }
            }
        }
    }

    /**
     * Отправить отложенный голосовой файл (из режима review).
     */
    fun sendPendingVoice() {
        val file = pendingVoiceFile ?: return
        pendingVoiceFile = null
        val dur = pendingVoiceDuration
        pendingVoiceDuration = 0
        isPreviewingVoice = false
        // Fix #118: останавливаем preview-плеер при отправке, иначе он
        // продолжит играть фоном после отправки сообщения.
        previewPlayer?.let { p ->
            try { p.setOnCompletionListener(null); p.setOnPreparedListener(null) } catch (_: Exception) {}
            try { p.reset() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
        previewPlayer = null
        sending = true
        app.appScope.launch {
            try {
                app.apiClient.sendVoiceMessage(peerId, file)
                scope.launch {
                    reloadMessages()
                    listState.animateScrollToItem(0)
                }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "sendPendingVoice error", e)
                scope.launch { errorText = "Ошибка отправки голосового" }
            } finally {
                file.delete()
                voiceAmplitudes.clear()
                scope.launch { sending = false }
            }
        }
    }

    /**
     * Предпрослушать отложенный голосовой файл (play-before-send).
     * Использует MediaPlayer; прогресс обновляется через LaunchedEffect.
     */
    fun togglePreviewPendingVoice() {
        val file = pendingVoiceFile ?: return
        if (isPreviewingVoice) {
            // Fix #118: reset() перед release() очищает внутреннее состояние
            // MediaPlayer, иначе pending events → "mediaplayer went away with
            // unhandled events" в logcat. Также сбрасываем listeners.
            previewPlayer?.let { p ->
                try { p.setOnCompletionListener(null); p.setOnPreparedListener(null) } catch (_: Exception) {}
                try { p.reset() } catch (_: Exception) {}
                try { p.release() } catch (_: Exception) {}
            }
            previewPlayer = null
            isPreviewingVoice = false
            previewProgress = 0f
            return
        }
        try {
            previewPlayer?.let { p ->
                try { p.setOnCompletionListener(null); p.setOnPreparedListener(null) } catch (_: Exception) {}
                try { p.reset() } catch (_: Exception) {}
                try { p.release() } catch (_: Exception) {}
            }
            val player = android.media.MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { p ->
                p.start()
                isPreviewingVoice = true
                previewProgress = 0f
            }
            player.setOnCompletionListener {
                // Fix #118: НЕ вызываем release() здесь — он вызывает pending
                // events → "went away with unhandled events". Только состояние.
                // Release произойдёт в onDispose или при следующем toggle.
                isPreviewingVoice = false
                previewProgress = 0f
            }
            player.prepareAsync()
            previewPlayer = player
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "togglePreviewPendingVoice error", e)
        }
    }

    fun cancelVoiceRecording() {
        VoiceRecorder.cancelRecording()
        isRecording = false
        // Fix #118: освобождаем preview-плеер при cancel, иначе продолжит играть.
        previewPlayer?.let { p ->
            try { p.setOnCompletionListener(null); p.setOnPreparedListener(null) } catch (_: Exception) {}
            try { p.reset() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
        previewPlayer = null
        pendingVoiceFile?.let { it.delete() }
        pendingVoiceFile = null
        pendingVoiceDuration = 0
        isPreviewingVoice = false
        voiceAmplitudes.clear()
    }

    // Sprint 3 #13: загрузка стикеров.
    fun loadStickers() {
        if (stickerPacks.isNotEmpty() || stickerLoading) return
        stickerLoading = true
        scope.launch {
            try {
                // Fix #221: загружаем купленные + каталог (featured).
                // Каталог содержит рекомендуемые/популярные паки, в т.ч. не купленные.
                // Сливаем: сначала купленные (purchased + active), потом не купленные
                // из каталога (purchased=false) — они будут с затемнением + 🔒.
                val purchased = app.apiClient.storeGetStickerPacks()
                val purchasedIds = purchased.map { it.id }.toHashSet()
                val catalog = app.apiClient.storeGetStickerCatalog()
                val unpurchased = catalog.filter { it.id !in purchasedIds }
                if (unpurchased.isNotEmpty()) {
                    AppLog.i("ChatDetailScreen", "loadStickers: ${purchased.size} purchased + ${unpurchased.size} catalog (locked) = ${purchased.size + unpurchased.size} total")
                }
                stickerPacks = purchased + unpurchased
                // Fix #233 (sticker-enrich): заполняем глобальный кеш animation_url
                // по stickerId. Используется в MessageBubble для enrichment —
                // стикеры в сообщениях получат анимацию даже если VK не вернул
                // animation_url в attachment.
                StickerAnimationCache.populate(purchased + unpurchased)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Fix #252: корректная отмена (пользователь ушёл со экрана)
                // — НЕ показываем как ошибку, просто пробрасываем дальше.
                throw e
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "loadStickers error", e)
            } finally {
                stickerLoading = false
            }
        }
    }

    fun sendSticker(stickerId: Int) {
        // #STICKER-MULTI-SEND: НЕ закрываем панель — пользователь может отправить
        // несколько стикеров подряд. Раньше showEmojiStickerPanel = false закрывало
        // панель после каждого тапа → приходилось открывать заново.
        // Также НЕ используем глобальный `sending` флаг — он блокировал кнопку ➕
        // (enabled = !sending) и текстовые/фото отправки на время отправки стикера.
        // Теперь стикер отправляется fire-and-forget, reloadMessages() — в отдельной
        // корутине, не блокирующей UI.
        // Fix #223: не опираемся на флаг active пака — он ненадёжен (VK помечает
        // active=1, но отклоняет стикер err=100 "not available"). Вместо этого
        // всегда пробуем messagesSendSticker, а VKApiClient сам перехватит err=100
        // и отправит как картинку (download PNG → upload → photo attachment).
        //   purchased=false → блокируем (платный стикер, нет права отправки)
        //   purchased=true  → messagesSendSticker(fallbackImageUrl=...)
        //                       ├─ успех → готово
        //                       └─ err=100 "not available" → messagesSendStickerAsImage
        var foundPack: re.pinok.data.model.StickerPack? = null
        var foundSticker: re.pinok.data.model.StickerItem? = null
        for (pack in stickerPacks) {
            val s = pack.stickers?.firstOrNull { it.stickerId == stickerId }
            if (s != null) { foundPack = pack; foundSticker = s; break }
        }
        val isPurchased = foundPack?.purchased != false
        // Fix #225: sendImageUrl — прозрачные images (без фона), 256px+.
        val imageUrl = foundSticker?.sendImageUrl ?: foundSticker?.displayUrl
        scope.launch {
            try {
                if (!isPurchased) {
                    // Платный стикер — нельзя отправить. Подсказка юзеру.
                    Toast.makeText(ctx, "Платный стикер — купите пак в VK, чтобы отправить", Toast.LENGTH_LONG).show()
                    return@launch
                }
                // Fix #223: fallbackImageUrl передаётся всегда — VKApiClient
                // перехватит err=100 "not available" и отправит как картинку.
                val msgId = app.apiClient.messagesSendSticker(peerId, stickerId, fallbackImageUrl = imageUrl)
                if (msgId > 0) {
                    // #STICKER-MULTI-SEND: reloadMessages() в отдельной корутине —
                    // не блокируем отправку следующих стикеров. LongPoll подтолкнёт
                    // новое сообщение, reload просто синхронизирует.
                    scope.launch {
                        reloadMessages()
                        listState.animateScrollToItem(0)
                    }
                } else {
                    AppLog.w("ChatDetailScreen", "sendSticker failed (msgId=$msgId) for stickerId=$stickerId")
                    Toast.makeText(ctx, "Не удалось отправить стикер", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "sendSticker error", e)
                Toast.makeText(ctx, "Ошибка отправки стикера", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Sprint 3 #14: управление чатами.
    fun renameChat(newTitle: String) {
        if (localChatId <= 0) return
        scope.launch {
            try {
                app.apiClient.messagesEditChat(localChatId, newTitle)
                showRenameDialog = false
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "renameChat error", e)
            }
        }
    }

    fun loadMembers() {
        if (localChatId <= 0) return
        loadingMembers = true
        showMembersDialog = true
        scope.launch {
            try {
                chatMembers = app.apiClient.messagesGetConversationMembers(peerId)
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "loadMembers error", e)
            } finally {
                loadingMembers = false
            }
        }
    }

    fun leaveChat() {
        if (localChatId <= 0) return
        scope.launch {
            try {
                app.apiClient.messagesRemoveChatUser(localChatId)
                showChatMenu = false
                onBack()
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "leaveChat error", e)
            }
        }
    }

    fun kickMember(memberId: Long) {
        if (localChatId <= 0) return
        scope.launch {
            try {
                app.apiClient.messagesRemoveChatUser(localChatId, memberId)
                chatMembers = chatMembers.filter { it.memberId != memberId }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "kickMember error", e)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        // Fix #234 (multi-photo preview): НЕ отправляем фото сразу при выборе.
        // Добавляем в pendingPhotos → над полем ввода появится миниатюра.
        // Send кнопка загрузит и отправит батч.
        // Fix #235: оборачиваем Uri в PendingPhoto(id, uri) — уникальный id для
        // стабильного ключа LazyRow (раньше ключ дублировался → crash).
        uri ?: return@rememberLauncherForActivityResult
        pendingPhotos = (pendingPhotos + PendingPhoto(nextPendingPhotoId(), uri)).take(10)
    }
    // Fix #234 (multi-photo preview): multi-photo picker — до 10 фото за раз.
    // НЕ отправляем сразу, добавляем все URI в pendingPhotos для предпросмотра.
    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // Ограничиваем суммарно 10 фото (даже если уже были выбраны ранее).
        val combined = (pendingPhotos + uris.map { PendingPhoto(nextPendingPhotoId(), it) }).take(10)
        pendingPhotos = combined
    }
    // Fix #235 (multi-file): выбор НЕСКОЛЬКИХ файлов за раз (до 10).
    // Каждый URI копируется в temp-файл с правильным именем (Fix #232),
    // оборачивается в PendingFileAttachment и добавляется в pendingFiles.
    // Суммарный лимит — 10 (VK messages.send принимает до 10 attachments).
    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val newFiles = mutableListOf<PendingFileAttachment>()
        for (uri in uris) {
            try {
                val inputStream = ctx.contentResolver.openInputStream(uri) ?: run {
                    AppLog.e("ChatDetailScreen", "filePicker: cannot open input stream for $uri")
                    continue
                }
                var nameFromResolver: String? = null
                try {
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIdx >= 0) {
                            nameFromResolver = cursor.getString(nameIdx)
                        }
                    }
                } catch (_: Exception) { }
                val mimeFromResolver = ctx.contentResolver.getType(uri)
                val rawName = nameFromResolver
                    ?: uri.lastPathSegment?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    ?: "file"
                val displayName = rawName.replace(Regex("[^a-zA-Z0-9._\\- а-яА-ЯёЁ()\\[\\]]"), "_")
                    .ifBlank { "file_${System.currentTimeMillis()}" }
                val isImage = mimeFromResolver?.startsWith("image/") == true
                // Fix #297: видеофайлы определяем по MIME или расширению.
                val isVideo = mimeFromResolver?.startsWith("video/") == true
                    || displayName.substringAfterLast('.', "").lowercase() in setOf(
                        "mp4", "avi", "mov", "mkv", "webm", "flv", "wmv", "m4v", "3gp", "mpg", "mpeg", "ts", "vob",
                    )
                val safeTempName = "attach_${System.currentTimeMillis()}_${newFiles.size}_$displayName"
                val tempFile = File(ctx.cacheDir, safeTempName)
                tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                inputStream.close()
                // Fix #297: для видео генерируем миниатюру первого кадра +
                // длительность через MediaMetadataRetriever. На ошибку — null
                // (UI покажет play-icon поверх иконки вложения).
                var thumbPath: String? = null
                var durationSec = 0L
                if (isVideo) {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(ctx, uri)
                        val durMs = retriever.extractMetadata(
                            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                        )?.toLongOrNull() ?: 0L
                        durationSec = (durMs / 1000).coerceAtLeast(0L)
                        val bmp = retriever.getFrameAtTime(
                            0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (bmp != null) {
                            val thumbFile = File(ctx.cacheDir, "thumb_${System.currentTimeMillis()}_${newFiles.size}.jpg")
                            thumbFile.outputStream().use { out ->
                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
                            }
                            thumbPath = thumbFile.absolutePath
                            bmp.recycle()
                        }
                        retriever.release()
                        AppLog.i("ChatDetailScreen", "video thumb: $displayName dur=${durationSec}s thumb=${thumbPath != null}")
                    } catch (e: Exception) {
                        AppLog.w("ChatDetailScreen", "video thumb failed for $displayName: ${e.message}")
                    }
                }
                newFiles += PendingFileAttachment(
                    id = nextPendingFileId(),
                    file = tempFile,
                    displayName = displayName,
                    sizeBytes = tempFile.length(),
                    mime = mimeFromResolver,
                    isImage = isImage,
                    isVideo = isVideo,
                    thumbPath = thumbPath,
                    durationSec = durationSec,
                )
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "filePicker: copy uri→file error for $uri", e)
            }
        }
        if (newFiles.isNotEmpty()) {
            // Суммарно не больше 10 (VK messages.send лимит). Лишние (старые) — оставляем.
            pendingFiles = (pendingFiles + newFiles).take(10)
            AppLog.i("ChatDetailScreen", "filePicker: added ${newFiles.size} files, total pending=${pendingFiles.size}")
        }
    }
    // #IM-EDIT-ATTACH: выбор ОДНОГО файла для добавления при редактировании сообщения.
    // URI копируется в temp-файл (uploadDocForMessage требует File) и загружается как
    // документ → токен добавляется в editingAddedAttach. Паттерн = multiFilePickerLauncher,
    // но для одного файла (GetContent) и загрузка сразу (не в pendingFiles).
    val editFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val u = uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (editingAddingFile) return@launch
            editingAddingFile = true
            var inFile: java.io.File? = null
            try {
                inFile = kotlin.io.path.createTempFile(
                    prefix = "edit_attach_",
                    suffix = ".bin",
                    directory = ctx.cacheDir.toPath(),
                ).toFile()
                val ins = ctx.contentResolver.openInputStream(u)
                if (ins == null) {
                    AppLog.w("ChatDetailScreen", "editFilePicker: cannot open stream for $u")
                    return@launch
                }
                ins.use { i -> inFile.outputStream().use { out -> i.copyTo(out) } }
                val mime = ctx.contentResolver.getType(u)
                val token = app.apiClient.uploadDocForMessage(inFile, mime)
                if (token != null) {
                    editingAddedAttach = editingAddedAttach + token
                    AppLog.i("ChatDetailScreen", "editFilePicker: uploaded $token")
                } else {
                    AppLog.w("ChatDetailScreen", "editFilePicker: upload failed for $u")
                }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "editFilePicker: upload error", e)
            } finally {
                inFile?.delete()
            }
        }
    }
    var showAttachMenu by remember { mutableStateOf(false) }
    // Fix #200: единый триггер ➕ справа от поля — выпадающее меню вверх
    // (Смайлы / Стикеры / Прикрепить). Заменяет 3 отдельные кнопки 📎😀😐,
    // поле ввода стало шире.
    var showTriggerMenu by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    // P5.3: показ расширенного пикера вложений (Музыка/Видео/Подарки).
    // #ATTACH-UNIFY: таб заменён с Int на enum (в пикере добавились
    // табы «Фото»/«Документы» — Int-индексы стали хрупкими).
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var attachmentPickerTab by remember { mutableStateOf(AttachmentPickerTab.Music) }

    // P5.3: камера — снимок фото. После снимка URI добавляется в pendingPhotos
    // (превью над полем ввода), отправка идёт через doSend() → uploadPhotoForMessage
    // (photos-путь, батчем с возможностью подписи и отмены). См. #CAMERA-PREVIEW.
    // URI должен быть FileProvider-based, чтобы камера могла записать результат.
    //
    // Fix #126: rememberSaveable вместо remember. При открытии камеры Android ОС
    // может убить процесс приложения (low memory). Когда пользователь делает фото
    // и возвращается — процесс пересоздаётся, remember теряет state, cameraImageUri=null,
    // callback получает ok=true но uri=null → фото теряется ("не прикрепляется").
    // rememberSaveable хранит URI как String в Bundle → переживает process death.
    var cameraImageUri by rememberSaveable(stateSaver = UriSaver) { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { ok ->
        // Fix #132: камера отработала (успех или отмена) — очищаем saved state,
        // чтобы при будущем process death не возвращать в чат без причины.
        onCameraReturnConsumed()
        val uri = cameraImageUri
        cameraImageUri = null
        if (!ok || uri == null) return@rememberLauncherForActivityResult
        // #CAMERA-PREVIEW (2026-08-01): НЕ отправляем фото сразу. Добавляем
        // в pendingPhotos (как photo-picker) → над полем ввода появится
        // миниатюра с кнопкой × (удалить) и тапом открыть полноэкранный просмотр.
        // Send-кнопка загрузит и отправит батч через uploadPhotoForMessage
        // (photos-путь, тот же что для галереи). Это даёт:
        //   (1) превью перед отправкой — пользователь видит что отправляет;
        //   (2) возможность отменить прикрепление (× на миниатюре);
        //   (3) возможность добавить подпись (inputText) к фото;
        //   (4) возможность сделать ещё фото / выбрать из галереи и отправить
        //       батчем (до 10 фото в одном сообщении).
        // Раньше cameraLauncher звал uploadAndSendPhoto напрямую — фото уходило
        // сразу, без превью и без отмены. Лог: "camera photo uploadAndSendPhoto sent".
        pendingPhotos = (pendingPhotos + PendingPhoto(nextPendingPhotoId(), uri)).take(10)
        AppLog.i("ChatDetailScreen", "camera photo added to pendingPhotos (preview) — ${pendingPhotos.size} pending")
    }
    // P5.3: permission launcher для камеры (переиспользуем общий паттерн).
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // Запускаем камеру после выдачи разрешения.
            val uri = createCameraImageUri(ctx)
            if (uri != null) {
                cameraImageUri = uri
                // Fix #132: сохраняем параметры чата для восстановления после
                // process death (камера может убить процесс приложения).
                onCameraLaunch(peerId, currentTitle, currentPhoto)
                cameraLauncher.launch(uri)
            } else {
                AppLog.w("ChatDetailScreen", "createCameraImageUri returned null")
            }
        } else {
            AppLog.w("ChatDetailScreen", "CAMERA permission denied")
        }
    }

    // Sprint 3 #12 → Fix #115: таймер записи + амплитуда + история для waveform.
    // VK Web: VoiceRecording__svg — 200 столбиков, обновляются каждые ~50мс.
    // Здесь собираем до 300 семплов (15с), потом старые затираются.
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        val baseSeconds = pendingVoiceDuration
        val startTime = System.currentTimeMillis()
        while (true) {
            withFrameMillis { }
            if (!VoiceRecorder.isRecording()) break
            recordingSeconds = baseSeconds + ((System.currentTimeMillis() - startTime) / 1000).toInt()
            val amp = VoiceRecorder.getAmplitude().toFloat()
            val norm = if (amp > 0) (amp / 32767f).coerceIn(0f, 1f) else 0f
            recordingAmplitude = norm
            voiceAmplitudes.add(norm)
            if (voiceAmplitudes.size > 300) voiceAmplitudes.removeAt(0)
        }
    }

    // Прогресс предпрослушивания (play-before-send).
    LaunchedEffect(isPreviewingVoice) {
        if (!isPreviewingVoice) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            val p = previewPlayer ?: break
            try {
                if (!p.isPlaying) { isPreviewingVoice = false; break }
                val d = p.duration.coerceAtLeast(1)
                previewProgress = p.currentPosition.toFloat() / d
            } catch (_: Exception) { break }
        }
    }

    // Очистка записи при выходе с экрана.
    DisposableEffect(Unit) {
        onDispose {
            if (VoiceRecorder.isRecording()) VoiceRecorder.cancelRecording()
            // Fix #118: reset() перед release() + сброс listeners, иначе
            // "mediaplayer went away with unhandled events" (logcat 18:26:16).
            previewPlayer?.let { p ->
                try { p.setOnCompletionListener(null); p.setOnPreparedListener(null) } catch (_: Exception) {}
                try { p.reset() } catch (_: Exception) {}
                try { p.release() } catch (_: Exception) {}
            }
            previewPlayer = null
            pendingVoiceFile?.let { it.delete() }
            // Fix #120: освободить единый voice-плеер, иначе продолжит играть фоном.
            voicePlaybackController.dispose()
        }
    }

    // Реакция на сообщение.
    // #REACTION-WEB-API (волна 32): web-методы messages.sendReaction /
    // messages.deleteReaction (parity VK web, параметры сверены по бандлу
    // снапшота: {cmid, peer_id, reaction_id} / {cmid, peer_id}) вместо legacy
    // messages.react. Тап по реакции — toggle: если своя реакция совпадает
    // (reactions.user_reaction == id) — снимаем, иначе ставим.
    // Оптимистичный UI: локальный стейт сообщения обновляется сразу; при
    // сбое/ошибке — сброс перезагрузкой истории. На успех НЕ перезагружаем
    // (иначе лагающий getHistory мог бы «мигнуть» реакцию обратно).
    fun reactToMessage(messageId: Long, reactionId: Int) {
        val msg = messages.firstOrNull { it.id == messageId }
        if (msg == null) {
            AppLog.w("ChatDetailScreen", "#IM-REACTION skipped: msg id=$messageId not in loaded history")
            return
        }
        val msgCmid = msg.conversationMessageId
        // cmid — правильный идентификатор для messages.* (семантика Audit #40 /
        // Fix #207); для записей без cmid — честный фолбэк на message_id.
        val ref = if (msgCmid != null && msgCmid > 0) msgCmid else messageId
        if (msgCmid == null || msgCmid <= 0) {
            AppLog.w("ChatDetailScreen", "#IM-REACTION cmid missing for msg id=$messageId, fallback to message_id")
        }
        val msgReactions = msg.reactions
        val myReaction = if (msgReactions != null) msgReactions.userReaction else null
        val isToggleOff = myReaction != null && myReaction != 0 && myReaction == reactionId
        // Оптимистичное обновление (лайк-паттерн: тап → сразу в списке).
        // #CHANNEL-POST-UI: вместе с count/userReaction пересчитываем и
        // разбивку items (эмодзи-чипы VK) — чипы не «мигают» назад при тоггле.
        messages = messages.map { m ->
            if (m.id != messageId) m else {
                val old = m.reactions
                val oldCount = if (old != null) old.count else 0
                val oldUser = if (old != null) old.userReaction else null
                val hadUser = oldUser != null && oldUser != 0
                val currentItems = if (old != null) old.items else emptyList()
                val newItems: List<ReactionItem> = if (isToggleOff) {
                    // Снимаем: уменьшаем счётчик своего чипа, при 0 — убираем чип.
                    currentItems.mapNotNull { item ->
                        if (item.id == reactionId) {
                            if (item.count > 1) item.copy(count = item.count - 1) else null
                        } else item
                    }
                } else {
                    // Ставим/меняем: при замене другой своей реакции снимаем её с
                    // чипа, затем инкремент (или добавление) целевого чипа.
                    val afterRemove = if (oldUser != null && oldUser != reactionId) {
                        currentItems.mapNotNull { item ->
                            if (item.id == oldUser) {
                                if (item.count > 1) item.copy(count = item.count - 1) else null
                            } else item
                        }
                    } else currentItems
                    val found = afterRemove.any { it.id == reactionId }
                    if (found) {
                        afterRemove.map { if (it.id == reactionId) it.copy(count = it.count + 1) else it }
                    } else {
                        afterRemove + ReactionItem(id = reactionId, count = 1)
                    }
                }
                val newReaction = MessageReaction(
                    count = if (isToggleOff) {
                        if (oldCount > 0) oldCount - 1 else 0
                    } else {
                        if (hadUser) oldCount else oldCount + 1
                    },
                    userReaction = if (isToggleOff) null else reactionId,
                    items = newItems,
                )
                m.copy(reactions = newReaction)
            }
        }
        scope.launch {
            try {
                if (isToggleOff) {
                    AppLog.i("ChatDetailScreen", "#IM-REACTION delete cmid=$ref")
                    val ok = app.apiClient.messagesDeleteReaction(peerId, ref)
                    if (!ok) {
                        AppLog.w("ChatDetailScreen", "#IM-REACTION delete failed: peer=$peerId cmid=$ref — rollback")
                        reloadMessages()
                    }
                } else {
                    AppLog.i("ChatDetailScreen", "#IM-REACTION send id=$reactionId cmid=$ref")
                    val ok = app.apiClient.messagesSendReaction(peerId, ref, reactionId)
                    if (!ok) {
                        AppLog.w("ChatDetailScreen", "#IM-REACTION send failed: peer=$peerId cmid=$ref id=$reactionId — rollback")
                        reloadMessages()
                    }
                }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "#IM-REACTION error — rollback", e)
                reloadMessages()
            }
        }
    }

    // Удаление сообщения.
    fun deleteMessage(message: re.pinok.data.model.Message) {
        val cmid = message.conversationMessageId
        val msgId = message.id
        scope.launch {
            try {
                // Fix #207: VK API 5.221+ — удаление по conversation_message_id
                // (cmid). Старый message_id не работает для чатов → сообщение
                // «висит» после удаления и появляется снова при перезаходе.
                // Если cmid=null (редкий случай — service-сообщения) — fallback
                // на старый messagesDelete по message_id.
                val ok = if (cmid != null && cmid > 0) {
                    app.apiClient.messagesDeleteByCmid(peerId, cmid, deleteForAll = true)
                } else {
                    AppLog.w("ChatDetailScreen", "deleteMessage: cmid is null for msg id=$msgId, fallback to message_id")
                    app.apiClient.messagesDelete(msgId, deleteForAll = true)
                }
                if (ok) {
                    messages = messages.filter { it.id != msgId }
                } else {
                    AppLog.w("ChatDetailScreen", "delete failed for msg id=$msgId cmid=$cmid")
                }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "delete error", e)
            }
        }
    }

    // Редактирование сообщения.
    // #IM-EDIT-CMID (волна 32): web-parity — messages.edit по conversation_message_id
    // (cmid, параметр сверен по бандлу снапшота: {cmid, peer_id, message,
    // keep_forward_messages}); по message_id современный gateway может отказывать
    // (класс Fix #207). Для старых записей без cmid — фолбэк на message_id.
    //
    // #IM-EDIT-ATTACH (HAR CHAT-EDIT-ATTACH-HAR-2026-10-01): веб-VK правит текст и
    // вложения одним messages.edit, где attachment ПОЛНОСТЬЮ переписывает список.
    // Поэтому чтобы НЕ снять вложения при правке текста, их нужно передать заново.
    // Логика (по [editingAttachments]):
    //   - null           → берём текущие вложения сообщения (фолбэк, сохраняем);
    //   - непустой список → передаём строку (сохраняем/заменяем/учитываем удалённые ×);
    //   - пустой список   → attachment не пишем (VK снимает все вложения).
    //
    // Локальная функция-помощник (должна быть объявлена ДО использования в
    // editMessage — в local scope Kotlin не разрешает forward reference).
    // #IM-EDIT-ATTACH (HAR CHAT-EDIT-ATTACH-HAR-2026-10-01): собрать attachment-строку
    // (для messages.edit/messages.send) из списка вложений сообщения.
    // Формат (VK web): список через запятую, каждый = {type}{owner}_{id}[_{accessKey}],
    // ссылка = сырой URL. Поддерживаемые типы: doc, photo, video, audio, link
    // (+ audio_message как doc-ссылка). Встретился НЕподдерживаемый тип
    // (sticker/poll/gift…) → возвращаем null, чтобы НЕ снести вложения, которые
    // не умеем перечислить (консервативно).
    fun buildAttachmentString(atts: List<Attachment>): String? {
        val tokens = mutableListOf<String>()
        for (a in atts) {
            val t = when (a.type) {
                "doc" -> {
                    val d = a.doc ?: return null
                    buildString {
                        append("doc").append(d.ownerId).append('_').append(d.id)
                        if (!d.accessKey.isNullOrEmpty()) append('_').append(d.accessKey)
                    }
                }
                "audio" -> {
                    val t0 = a.audio ?: return null
                    buildString {
                        append("audio").append(t0.ownerId).append('_').append(t0.id)
                        if (!t0.accessKey.isNullOrEmpty()) append('_').append(t0.accessKey)
                    }
                }
                "photo" -> {
                    val p = a.photo ?: return null
                    "photo${p.ownerId}_${p.id}"
                }
                "video" -> {
                    val v = a.video ?: return null
                    buildString {
                        append("video").append(v.ownerId).append('_').append(v.id)
                        if (!v.accessKey.isNullOrEmpty()) append('_').append(v.accessKey)
                    }
                }
                "link" -> a.link?.url ?: return null
                // Голосовое — это doc с audio_msg; для сохранения опираемся на doc.
                "audio_message" -> a.doc?.let { "doc${it.ownerId}_${it.id}" } ?: return null
                else -> return null   // неподдерживаемый тип — не трогаем список
            }
            tokens += t
        }
        return if (tokens.isEmpty()) null else tokens.joinToString(",")
    }

    fun editMessage(messageId: Long, newText: String) {
        scope.launch {
            try {
                val msg = messages.firstOrNull { it.id == messageId }
                val msgCmid = if (msg != null) msg.conversationMessageId else null
                val editRef = if (msgCmid != null && msgCmid > 0) msgCmid else messageId
                if (msgCmid == null || msgCmid <= 0) {
                    AppLog.w("ChatDetailScreen", "editMessage: cmid missing for msg id=$messageId, fallback to message_id")
                }
                // Список вложений, который уйдёт на сервер (null = не передавать).
                val atts = editingAttachments ?: msg?.attachments
                val base = if (atts.isNullOrEmpty()) null else buildAttachmentString(atts)
                val tokens = mutableListOf<String>()
                base?.split(",")?.filter { it.isNotBlank() }?.let { tokens.addAll(it) }
                tokens.addAll(editingAddedAttach)
                // #IM-EDIT-ATTACH-FIX: пустой итоговый список ≠ «не трогать». Различаем:
                //  - список непустой        → отправляем полный новый список (VK переписывает
                //                            attachment целиком → удалённый × файл уходит);
                //  - пользователь убрал ВСЕ (editingAttachments = emptyList) и ничего не добавил
                //                            → шлём ПУСТУЮ строку "": иначе VK, не получив
                //                            attachment, ОСТАВЛЯЕТ старые вложения (баг: файл
                //                            не откреплялся);
                //  - иначе (null / остались только неподдерживаемые buildAttachmentString типы,
                //    напр. стикер)         → консервативно НЕ трогаем список (не сносим то,
                //                            что не умеем перечислить).
                val distinctTokens = tokens.distinct()
                val attachStr: String? = when {
                    distinctTokens.isNotEmpty() -> distinctTokens.joinToString(",")
                    editingAttachments != null && editingAttachments.orEmpty().isEmpty() &&
                        editingAddedAttach.isEmpty() -> ""
                    else -> null
                }
                val ok = app.apiClient.messagesEdit(
                    peerId = peerId,
                    cmid = editRef,
                    message = newText,
                    attachment = attachStr,
                )
                if (ok) reloadMessages()
                else AppLog.w("ChatDetailScreen", "edit failed for $messageId")
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "edit error", e)
            }
        }
    }

    // Отмена редактирования.
    fun cancelEdit() {
        editingMsgId = null
        inputText = ""
        editingAttachments = null
        editingAddedAttach = emptyList()
        editingAddingFile = false
    }

    // #IM-IMPORTANT (волна 32): «Отметить как важное» — messages.markAsImportant
    // (parity VK web, снапшот §1.1: peer_id + cmids + important=1). НЕ путать с
    // messagesMarkAsImportantConversation — тот ставит флажок ДИАЛОГУ (Fix #274).
    // isImportant на сообщении от сервера в getHistory не приходит — пункт меню
    // всегда ставит important=1; повторная отметка идемпотентна сервером.
    fun markMessageImportant(message: re.pinok.data.model.Message) {
        val cmid = message.conversationMessageId
        if (cmid == null || cmid <= 0) {
            AppLog.w("ChatDetailScreen", "#IM-IMPORTANT skipped: cmid missing for msg id=${message.id}")
            Toast.makeText(ctx, "Это сообщение нельзя отметить как важное", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            try {
                val ok = app.apiClient.messagesMarkAsImportant(peerId, cmid, important = true)
                AppLog.i("ChatDetailScreen", "#IM-IMPORTANT peer=$peerId cmid=$cmid ok=$ok")
                if (ok) {
                    Toast.makeText(ctx, "Отмечено как важное", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Не удалось отметить как важное", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "#IM-IMPORTANT error", e)
                Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Пересылка сообщений.
    // Fix #295: фактический API-вызов теперь делает ForwardDialog сам
    // (с sourcePeerId + cmids). Эта функция оставлена как тонкая обёртка
    // на случай прямого программного вызова — использует cmid-путь.
    fun forwardMessages(targetPeerId: Long, ids: List<Long>) {
        // ids трактуем как cmids (приоритетный путь VK API 5.221+).
        scope.launch {
            try {
                app.apiClient.messagesForward(targetPeerId, peerId, ids)
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "forward error", e)
            }
        }
    }

    // P2.5: multi-select helpers.
    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    fun enterSelection(id: Long) {
        selectionMode = true
        selectedIds = setOf(id)
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    // Fix #244: системный Back выходит из режима выбора (если активен),
    // иначе — стандартное поведение (назад к списку чатов). Раньше Back
    // не обрабатывался в selection mode — выйти можно было только тапом по
    // кнопке Close в TopAppBar.
    BackHandler(enabled = selectionMode) {
        exitSelection()
    }

    fun deleteSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        // Fix #207: для каждого выбранного сообщения ищем его cmid в загруженном
        // списке messages. Если cmid есть — удаляем через messagesDeleteByCmid
        // (VK API 5.221+). Иначе fallback на messagesDelete по message_id.
        val msgsById = messages.associateBy { it.id }
        scope.launch {
            var failed = 0
            for (id in ids) {
                try {
                    val msg = msgsById[id]
                    val cmid = msg?.conversationMessageId
                    val ok = if (cmid != null && cmid > 0) {
                        app.apiClient.messagesDeleteByCmid(peerId, cmid, deleteForAll = true)
                    } else {
                        AppLog.w("ChatDetailScreen", "bulk delete: cmid null for id=$id, fallback to message_id")
                        app.apiClient.messagesDelete(id, deleteForAll = true)
                    }
                    if (!ok) failed++
                } catch (e: Exception) {
                    failed++
                    AppLog.e("ChatDetailScreen", "bulk delete error id=$id", e)
                }
            }
            val idSet = ids.toSet()
            messages = messages.filter { it.id !in idSet }
            if (failed > 0) {
                AppLog.w("ChatDetailScreen", "bulk delete: $failed failed of ${ids.size}")
            }
            exitSelection()
        }
    }

    fun forwardSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        // Fix #295: собираем cmid выбранных сообщений — только они
        // поддерживают пересылку вложений/файлов через `forward` JSON.
        val selected = messages.filter { it.id in ids }
        forwardMsgIds = ids
        forwardMsgCmids = selected.mapNotNull { it.conversationMessageId }
        if (forwardMsgCmids.isEmpty()) {
            Toast.makeText(ctx, "У выбранных сообщений нет cmid — пересылка невозможна", Toast.LENGTH_SHORT).show()
            return
        }
        showForwardDialog = true
    }

    // P3.2 + Fix #122: mute/unmute chat — optimistic toggle, Toast feedback,
    // обновление state из ответа API (а не только из optimistic update).
    // Если API вернул PushSettings — используем их (точное состояние сервера).
    // Если API вернул null — откатываем optimistic update + Toast об ошибке.
    fun toggleMute() {
        val newState = !muted
        muted = newState
        scope.launch {
            try {
                val newSettings = app.apiClient.messagesSetConversationPushSettings(peerId, disabled = newState)
                if (newSettings != null) {
                    // API успех + вернул настройки — обновляем state из ответа
                    // (точное состояние сервера, включая disabled_forever/no_sound).
                    muted = newSettings.isMuted()
                    AppLog.i("ChatDetailScreen", "mute toggled: $newState (server-confirmed: ${muted})")
                    Toast.makeText(
                        ctx,
                        if (muted) "Уведомления выключены" else "Уведомления включены",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    // API вернул null — ошибка (token invalid, network, etc.)
                    muted = !newState
                    AppLog.w("ChatDetailScreen", "mute toggle failed (api returned null)")
                    Toast.makeText(
                        ctx,
                        if (newState) "Не удалось выключить уведомления" else "Не удалось включить уведомления",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                muted = !newState
                AppLog.e("ChatDetailScreen", "mute toggle error", e)
                Toast.makeText(
                    ctx,
                    "Ошибка: ${e.message ?: "network error"}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // ═══ #IM-CHANNEL-FIX (56-b-5): уведомления канала ═════════════════════
    // ЗАМЕНА toggleMute для канальных пиров (peerId<0): push_settings-механизм
    // (messages.setConversationPushSettings) для каналов не доказан, зато есть
    // готовые обёртки messagesAllowFromGroup/messagesDenyFromGroup
    // (group_id = -peerId) — ровно то, чем VK web включает/выключает пуш канала
    // (снапшот 55: футер «Включить уведомления»). Для обычных чатов peerId>0
    // toggleMute выше НЕ тронут.
    //
    // Порядок: (1) оптимистично пишем кэш SovaPrefs — он же источник реактивного
    // стейта футера (channelNotifEnabled) и гейта пуша в SovaApp; (2) синхронизируем
    // кэш MessageNotifier (паттерн Fix #285 — иначе после выключения следующий
    // пост канала прошёл бы через cached.muted=false); (3) серверный вызов, при
    // отказе — откат (1)+(2) и честный Toast.
    fun toggleChannelNotifications() {
        val newState = !channelNotifEnabled
        scope.launch {
            // (1) Оптимистичный локальный стейт (NULL-ЯВНО: сбой DataStore не роняет UI).
            try {
                app.prefs.setChannelNotifEnabled(peerId, newState)
            } catch (e: Exception) {
                AppLog.w("ChatDetailScreen",
                    "#IM-CHANNEL-FIX: channel notif cache write failed: ${e.message}")
            }
            // (2) Кэш нотифаера: выключили канал → активный пуш-контекст глушится.
            re.pinok.realtime.MessageNotifier.setMuted(peerId, !newState)
            // (3) Сервер: #CHANNELS-NOTIF-MODE (П3) — в первую очередь канальный
            // channels.setNotificationMode (mode=enabled/disabled); при его провале
            // (вернул false/исключение) — fallback на прежний allow/deny по group_id
            // (peerId отрицательный). Формально канальный метод — штатный путь VK web,
            // allow/deny остаётся страховкой для устаревших шлюзов.
            try {
                val groupId = -peerId
                val notifOk = try {
                    app.apiClient.channelsSetNotificationMode(peerId, newState)
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    AppLog.w("ChatDetailScreen",
                        "#CHANNELS-NOTIF-MODE: channels.setNotificationMode threw — fallback: ${e.message}")
                    false
                }
                val ok = if (notifOk) {
                    true
                } else {
                    // #CHANNELS-NOTIF-MODE: fallback на прежний механизм allow/deny.
                    val fb = if (newState) app.apiClient.messagesAllowFromGroup(groupId)
                    else app.apiClient.messagesDenyFromGroup(groupId)
                    if (!fb) {
                        AppLog.w("ChatDetailScreen",
                            "#CHANNELS-NOTIF-MODE: fallback allow/deny also failed peer=$peerId")
                    }
                    fb
                }
                if (ok) {
                    AppLog.i("ChatDetailScreen",
                        "#IM-CHANNEL-FIX: channel notifications ${if (newState) "ENABLED" else "DISABLED"} " +
                            "peer=$peerId groupId=$groupId")
                    Toast.makeText(
                        ctx,
                        if (newState) "Уведомления канала включены" else "Уведомления канала выключены",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    // Сервер отклонил — откат оптимистичных (1)+(2).
                    try {
                        app.prefs.setChannelNotifEnabled(peerId, !newState)
                    } catch (e2: Exception) {
                        AppLog.w("ChatDetailScreen",
                            "#IM-CHANNEL-FIX: channel notif cache revert failed: ${e2.message}")
                    }
                    re.pinok.realtime.MessageNotifier.setMuted(peerId, newState)
                    val err = app.apiClient.lastApiError
                    AppLog.w("ChatDetailScreen",
                        "#IM-CHANNEL-FIX: channel notif toggle rejected (err=$err) peer=$peerId")
                    Toast.makeText(
                        ctx,
                        if (err.isNullOrBlank()) "Не удалось изменить уведомления канала" else "Ошибка: $err",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                // Сетевая ошибка — откат оптимистичных (1)+(2) + честный Toast.
                try {
                    app.prefs.setChannelNotifEnabled(peerId, !newState)
                } catch (e2: Exception) {
                    AppLog.w("ChatDetailScreen",
                        "#IM-CHANNEL-FIX: channel notif cache revert failed: ${e2.message}")
                }
                re.pinok.realtime.MessageNotifier.setMuted(peerId, newState)
                AppLog.e("ChatDetailScreen", "#IM-CHANNEL-FIX: channel notif toggle error", e)
                Toast.makeText(
                    ctx,
                    "Ошибка: ${e.message ?: "network error"}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // W30-1 #IM-UNREAD-MENU: «Отметить непрочитанным/прочитанным» из меню шапки
    // чата — семантика 1:1 со списком диалогов (MessagesScreen.onToggleUnread,
    // Fix #274 + #MARK-READ-REVERT):
    //  - unread=true → messages.markAsUnreadConversation ставит «метку
    //    непрочитанного» (жирный шрифт в списке, БЕЗ числового бейджа);
    //  - unread=false → markAsUnreadConversation снимает ТОЛЬКО метку, серверный
    //    unread_count реально чистит messages.markAsRead(start_message_id) с
    //    force=true (явное действие юзера — игнорирует DNR-режим).
    // Оптимистичного апдейта списка здесь нет (диалоги обновит refresh списка при
    // возврате), поэтому — API-подтверждение, Toast и локальный флаг.
    fun toggleUnreadMark() {
        val newState = !hasUnreadMark
        scope.launch {
            try {
                val ok = app.apiClient.messagesMarkAsUnreadConversation(peerId, newState)
                if (ok) {
                    hasUnreadMark = newState
                    if (!newState) {
                        // #MARK-READ-REVERT: чистим серверный счётчик по последнему
                        // загруженному сообщению. maxOf по id (а не lastOrNull) —
                        // история приходит newest-first, порядок не гарантирован.
                        if (messages.isNotEmpty()) {
                            val newestId = messages.maxOf { it.id }
                            val readOk = app.apiClient.messagesMarkAsRead(peerId, newestId, force = true)
                            AppLog.i("ChatDetailScreen",
                                "markAsRead (force) after unread-toggle: peer=$peerId upTo=$newestId ok=$readOk")
                        }
                    }
                    Toast.makeText(
                        ctx,
                        if (newState) "Отмечено непрочитанным" else "Отмечено прочитанным",
                        Toast.LENGTH_SHORT,
                    ).show()
                    AppLog.i("ChatDetailScreen", "unread mark toggled: peer=$peerId unread=$newState")
                } else {
                    AppLog.w("ChatDetailScreen", "unread mark toggle failed (api returned false) peer=$peerId")
                    Toast.makeText(
                        ctx,
                        if (newState) "Не удалось отметить непрочитанным" else "Не удалось отметить прочитанным",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                // Fix #151: rememberCoroutineScope отменяется при уходе с экрана —
                // нормальный lifecycle, пробрасываем (не маскируем под ошибку).
                throw ce
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "unread mark toggle error", e)
                Toast.makeText(ctx, "Не удалось изменить отметку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // P3.4: покинуть канал (отписка от сообщества + очистка диалога).
    // Для канала peerId < 0 → groupId = -peerId.
    // groups.leave отписывает от сообщества, messages.deleteConversation убирает диалог из списка.
    fun leaveChannel() {
        scope.launch {
            try {
                val groupId = -peerId
                val ok = app.apiClient.groupsLeave(groupId)
                if (ok) {
                    // Очищаем диалог из списка (иначе он останется как «прочитанный»).
                    try { app.apiClient.messagesDeleteConversation(peerId) } catch (_: Exception) {}
                    AppLog.i("ChatDetailScreen", "left channel: groupId=$groupId peerId=$peerId")
                    Toast.makeText(ctx, "Вы отписались от канала", Toast.LENGTH_SHORT).show()
                    onBack()
                } else {
                    AppLog.w("ChatDetailScreen", "groupsLeave failed")
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        ctx,
                        if (err.isNullOrBlank()) "Не удалось отписаться от канала" else "Ошибка: $err",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "leaveChannel error", e)
                Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ═══ #CHANNEL-WALL-MODE (Fix #393): функции канального режима ════════

    /**
     * #IM-CHANNEL-FIX (56-b-4): метаданные канала в шапку — подписчики, имя,
     * аватар (groupsGetById(-peerId), снапшот 29-a: «название + N подписчиков»)
     * + GroupInfo для PostHolder.lastGroups (паритет CommunityScreen).
     *
     * Раньше блок жил ИНЛАЙНОМ только в основном isChannel-пути LaunchedEffect —
     * probe/empty-history/error ветки wall-режима оставались без подписчиков.
     * Теперь это общая fun, вызываемая из ВСЕХ wall-веток. Асинхронно
     * (scope.launch): посты канала грузятся параллельно, шапка доворачивается
     * при приходе ответа.
     */
    fun loadChannelMeta() {
        scope.launch {
            try {
                val g = app.apiClient.groupsGetById(listOf(-peerId)).firstOrNull()
                if (g != null) {
                    channelGroup = g
                    // Fix #394: удалённое сообщество отдаёт name="DELETED" — НЕ
                    // затираем исходное имя из карточки диалога (баг-репорт 14.09:
                    // шапка «Время Перемен. Новости» превращалась в «DELETED»).
                    if (g.name.isNotBlank() && g.name != "DELETED" && g.name != currentTitle) currentTitle = g.name
                    // NULL-ЯВНО: photo200 может отсутствовать — фолбэк на
                    // photo100 (паттерн рендера аватарок всего проекта).
                    // #CHANNEL-AVATAR: для КАНАЛА VK Web нет photo_200/100 —
                    // только группы photo_base (ресайз-URL): приоритет на неё.
                    val gPhoto = g.photoBase ?: g.photo200 ?: g.photo100
                    if (gPhoto != null && gPhoto.isNotBlank() && gPhoto != currentPhoto) {
                        currentPhoto = gPhoto
                    }
                    if (g.membersCount > 0) channelSubscribers = g.membersCount
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                // Уход с экрана — нормальный lifecycle, не ошибка (паттерн Fix #151).
                throw ce
            } catch (e: Exception) {
                AppLog.w("ChatDetailScreen", "#IM-CHANNEL-FIX loadChannelMeta failed: ${e.message}")
            }
        }
    }

    /**
     * #IM-CHANNEL-FIX (56-b-1): сброс непрочитанного бейджа канала при открытии.
     *
     * Тап по карточке канала в списке уже чистит бейдж (MessagesScreen.onMarkAsRead),
     * но при открытии из пуша/deep-link список не участвует, а wall-ветки
     * LaunchedEffect выходили до markAsRead обычного чата → бейдж канала висел до
     * следующего refresh. Маркер: start_message_id НЕ передаётся (upToMessageId=0
     * → VK помечает всю беседу — у wall-постов другой id-пространства, cmid
     * беседы канала у нас недоступен; см. messagesMarkAsRead в VKApiClient).
     * DNR-режим уважается внутри messagesMarkAsRead (suppressRead).
     */
    fun markChannelConversationAsRead() {
        if (peerId >= 0) return
        scope.launch {
            try {
                // #CHANNELS-FILTER (П2/П3): точный канальный mark-as-read через
                // channels.markAsRead(last_read_cmid). last cmid берём у НОВЕЙШЕГО
                // поста истории канала (id == cmid, индекс 0 — sortedByDescending);
                // если cmid неизвестен (канал ещё не загружен / пуст) — fallback
                // на прежний messagesMarkAsRead(peer, 0), который чистит беседу.
                val lastCmid: Long? = when {
                    channelHistoryMode -> messages.firstOrNull()?.id
                    else -> channelPosts.firstOrNull()?.id
                }
                val ok = if (lastCmid != null && lastCmid > 0L) {
                    val typed = app.apiClient.channelsMarkAsRead(peerId, lastCmid)
                    AppLog.d("ChatDetailScreen",
                        "#IM-CHANNEL-FIX markChannelConversationAsRead: channels.markAsRead " +
                            "peer=$peerId lastCmid=$lastCmid ok=$typed")
                    typed
                } else {
                    val typed = app.apiClient.messagesMarkAsRead(peerId, 0L)
                    AppLog.d("ChatDetailScreen",
                        "#IM-CHANNEL-FIX markChannelConversationAsRead: messagesMarkAsRead " +
                            "peer=$peerId ok=$typed")
                    typed
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                AppLog.w("ChatDetailScreen",
                    "#IM-CHANNEL-FIX markChannelConversationAsRead failed: ${e.message}")
            }
        }
    }

    /**
     * Fix #394 #CHANNEL-WALL-FALLBACK: wall.get недоступен (err 15 «wall is
     * disabled» у канала / сообщество удалено / сеть) — пробуем открыть диалог
     * канала как ОБЫЧНЫЙ чат: messages.getHistory (сообщения канала приходят
     * в диалог — иначе бейдж/LP-события не приходили бы). При непустой истории
     * (или пустой БЕЗ ошибки) переключаем рендер на стандартный messages-режим
     * (channelWallFallback=true): LazyColumn истории + канальный read-only футер
     * и шапка сохраняются. Пустая история С ошибкой VK — остаёмся в wall-error
     * (честный «Повторить»; фолбэк больше не пытается — channelFallbackTried).
     *
     * История грузится тем же messagesGetHistoryWithProfiles(count = pageSize),
     * что и стандартный путь LaunchedEffect — состояние messages/chatProfiles/
     * endReached совместимо с обычным рендером без ветвлений.
     *
     * ВАЖНО (порядок объявления): локальная fun объявлена ДО loadChannelPosts —
     * Kotlin запрещает опережающие ссылки на локальные функции (Unresolved
     * reference при сборке, см. баг-репорт тестера 14.09).
     */
    fun attemptWallFallbackToHistory() {
        if (channelWallFallback || channelFallbackTried) return
        channelFallbackTried = true
        scope.launch {
            try {
                val result = app.apiClient.messagesGetHistoryWithProfiles(peerId, count = pageSize)
                val fresh = result.messages.distinctBy { it.id }
                if (fresh.isNotEmpty() || result.failure == null) {
                    messages = fresh
                    chatProfiles = result.profiles
                    if (fresh.size < pageSize) endReached = true
                    channelWallFallback = true
                    channelPostsError = null
                    // Канал фактически ОТКРЫТ юзером — сбрасываем бейдж
                    // (тот же контракт, что у успешного wall-режима, 56-b-1).
                    markChannelConversationAsRead()
                    AppLog.i("ChatDetailScreen",
                        "#CHANNEL-WALL-FALLBACK: wall недоступен → показана messages-история " +
                            "(${fresh.size} сообщений) peerId=$peerId")
                } else {
                    AppLog.w("ChatDetailScreen",
                        "#CHANNEL-WALL-FALLBACK: messages-история пуста и с ошибкой — остаёмся в wall-error peerId=$peerId")
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                AppLog.w("ChatDetailScreen",
                    "#CHANNEL-WALL-FALLBACK: getHistory failed (non-fatal): ${e.message}")
            }
        }
    }

    /**
     * Загрузка постов канала через wall.get (ownerId = peerId — посты
     * сообщества и есть контент канала, снапшот 29-a). [preloaded] — уже
     * полученная страница (probe-вызов wallGet при недоступном chat state),
     * чтобы не дёргать API дважды.
     *
     * Честные состояния: loading / channelPostsError («Повторить») /
     * пустой канал («В канале пока нет записей»).
     */
    fun loadChannelPosts(initial: Boolean, preloaded: List<Post>? = null) {
        if (channelPostsLoading) return
        // Task 67: диагностический #CHANNELS-PROBE убран — channels.getHistory
        // теперь РАБОЧИЙ путь загрузки (см. channels-first ветку ниже).
        scope.launch {
            channelPostsLoading = true
            channelPostsError = null
            if (initial) {
                channelPosts = emptyList()
                channelPostsEnd = false
            }
            try {
                if (preloaded != null) {
                    channelPosts = preloaded
                    if (preloaded.size < 30) channelPostsEnd = true
                    // #IM-CHANNEL-FIX (56-b-1): probe-ветка — канал успешно открыт,
                    // сбрасываем бейдж канала (см. markChannelConversationAsRead).
                    if (initial) markChannelConversationAsRead()
                } else {
                    // ══ Task 67 #CHANNELS-HIST: канонический источник контента канала ══
                    // Пробник v2 (лог 14.09 22:17) доказал: контент канала живёт
                    // ТОЛЬКО в channels.getHistory и только со start_cmid из
                    // getById.last_message (без него err=100). wall.get у канала
                    // — err 15 «wall is disabled», messages.getHistory — пусто.
                    // Успех → стандартный messages-режим (channelWallFallback=true):
                    // LazyColumn-история + read-only футер, вложения/аватары из
                    // groups[] рендерятся штатно.
                    // #CHANNEL-FILTERS (Task #CHANNELS-UI): фильтр ленты передаётся
                    // в channels.getHistory. null = «Всё» — прежний контракт.
                    val ch = app.apiClient.channelsGetHistory(
                        peerId, count = pageSize, filter = channelFeedFilter,
                    )
                    AppLog.i("ChatDetailScreen",
                        "#CHANNELS-HIST screen: msgs=${ch.messages.size} " +
                            "failure=${ch.failure} peerId=$peerId filter=$channelFeedFilter")
                    if (ch.messages.isNotEmpty()) {
                        messages = ch.messages
                        chatProfiles = ch.profiles
                        if (ch.messages.size < pageSize) endReached = true
                        channelHistoryMode = true
                        channelWallFallback = true
                        channelPostsError = null
                        // #CHANNELS-COUNTERS (П2): батч-дозагрузка views/comments
                        // для постов канала (best-effort). Канальные счётчики из
                        // channels.getHistory НЕ всегда заполнены (null) — добираем
                        // их отдельным channels.getChannelMessagesCounters и точечно
                        // заполняем только нулевые поля (не перетираем уже известные).
                        // Ошибка/пусто — не критично, лог и тихий пропуск.
                        try {
                            val cmids = ch.messages.mapNotNull { it.id.takeIf { id -> id > 0L } }
                            if (cmids.isNotEmpty()) {
                                val counters = app.apiClient
                                    .channelsGetChannelMessagesCounters(peerId, cmids)
                                if (counters.isNotEmpty()) {
                                    val merged = ch.messages.map { m ->
                                        val c = counters[m.id]
                                        val needViews = c != null && m.viewsCount == null && c.views != null
                                        val needComments = c != null && m.commentsCount == null && c.comments != null
                                        if (needViews || needComments) {
                                            m.copy(
                                                viewsCount = if (needViews) c.views else m.viewsCount,
                                                commentsCount = if (needComments) c.comments else m.commentsCount,
                                            )
                                        } else m
                                    }
                                    messages = merged
                                    AppLog.i("ChatDetailScreen",
                                        "#CHANNELS-COUNTERS: filled ${counters.size}/$cmids for peer=$peerId")
                                }
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            AppLog.w("ChatDetailScreen",
                                "#CHANNELS-COUNTERS best-effort failed: ${e.message}")
                        }
                        // Канал фактически ОТКРЫТ юзером — сбрасываем бейдж
                        // (тот же контракт, что у wall-режима, 56-b-1).
                        markChannelConversationAsRead()
                    } else {
                        // Каналы-исключения (контент в wall, getHistory пуст/ошибка) —
                        // прежний wall-путь с честным «Повторить».
                        val posts = app.apiClient.wallGet(ownerId = peerId, count = 30, offset = 0)
                        // #IM-CHANNEL-OPEN: успех/пустота wall.get — ключевой пункт
                        // трассировки (пусто + err → честный «Повторить» на экране).
                        AppLog.i("ChatDetailScreen",
                            "#IM-CHANNEL-OPEN wallGet initial: posts=${posts.size} peerId=$peerId")
                        channelPosts = posts
                        if (posts.size < 30) channelPostsEnd = true
                        if (posts.isEmpty()) {
                            // wallGet глотает детали ошибки в emptyList — честно
                            // показываем «Повторить» только при реальной ошибке API;
                            // без ошибки — канал просто пуст («нет записей»). При
                            // ошибке сразу пробуем фолбэк на messages-историю —
                            // err 15 «wall is disabled» постоянна, «Повторить» не
                            // поможет (Fix #394 #CHANNEL-WALL-FALLBACK).
                            val err = app.apiClient.lastApiError
                            if (err != null) {
                                channelPostsError = "Не удалось загрузить канал: $err"
                                attemptWallFallbackToHistory()
                            }
                        }
                        // #IM-CHANNEL-FIX (56-b-1): загрузка УСПЕШНА (посты получены либо
                        // канал честно пуст) — сбрасываем бейдж канала на сервере
                        // (открытие из пуша/deep-link, тап-путь из списка уже умеет).
                        if (initial && channelPostsError == null) markChannelConversationAsRead()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "#CHANNEL-WALL-MODE wallGet failed", e)
                channelPostsError = "Не удалось загрузить канал: ${e.message}"
                // Fix #394 #CHANNEL-WALL-FALLBACK: сетевой/API-провал wall.get —
                // тоже пробуем messages-историю (сюда попадают IOException/
                // таймауты — история может быть доступна, когда лента закрыта).
                attemptWallFallbackToHistory()
            } finally {
                channelPostsLoading = false
            }
        }
    }

    /** Offset-догрузка постов канала — паттерн CommunityScreen.loadMoreWall. */
    fun loadMoreChannelPosts() {
        if (channelPostsLoading || channelPostsLoadingMore || channelPostsEnd) return
        if (channelPosts.isEmpty()) return
        scope.launch {
            channelPostsLoadingMore = true
            try {
                val more = app.apiClient.wallGet(ownerId = peerId, count = 30, offset = channelPosts.size)
                val fresh = more.filter { p -> channelPosts.none { it.id == p.id } }
                if (fresh.isEmpty()) {
                    channelPostsEnd = true
                } else {
                    channelPosts = channelPosts + fresh
                    if (fresh.size < 30) channelPostsEnd = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "#CHANNEL-WALL-MODE loadMore failed", e)
            } finally {
                channelPostsLoadingMore = false
            }
        }
    }

    /**
     * Лайк поста канала — likes.add/likes.delete (type=post), optimistic-
     * состояние поверх серверных post.likes (паттерн ProfileScreen
     * toggleWallPostLike, NULL-ЯВНО без elvis).
     */
    fun toggleChannelPostLike(clicked: Post) {
        val key = "${clicked.ownerId}_${clicked.id}"
        if (channelLikeInFlight.containsKey(key)) return
        val stored = channelLikeStates[key]
        val current: Pair<Boolean, Int> = if (stored != null) stored else {
            val likes = clicked.likes
            val liked = if (likes != null && likes.userLikes == 1) true else false
            val baseCount = if (likes != null) likes.count else 0
            liked to baseCount
        }
        val newLiked = !current.first
        val newCount = (current.second + (if (newLiked) 1 else -1)).coerceAtLeast(0)
        channelLikeStates[key] = newLiked to newCount
        channelLikeInFlight[key] = true
        scope.launch {
            val serverCount = try {
                if (newLiked) {
                    // Fix #393: reactionId/accessKey/trackCode не нужны (NULL-ЯВНО —
                    // обязательные nullable-параметры передаются явно).
                    app.apiClient.likesAdd("post", clicked.ownerId, clicked.id, null, null, null)
                } else {
                    app.apiClient.likesDelete("post", clicked.ownerId, clicked.id, null, null)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "#CHANNEL-WALL-MODE likes.add/delete failed", e)
                -1
            }
            channelLikeInFlight.remove(key)
            if (serverCount >= 0) {
                // VK подтвердил — фиксируем точное значение счётчика.
                channelLikeStates[key] = newLiked to serverCount
            } else {
                // Откат optimistic + честный тост с реальной ошибкой VK.
                channelLikeStates[key] = current
                val err = app.apiClient.lastApiError
                Toast.makeText(
                    ctx,
                    if (err != null) err else "Не удалось оценить запись",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /** Открыть пост канала в PostDetailScreen (через экран-параметр onPostClick). */
    fun openChannelPost(post: Post) {
        val g = channelGroup
        if (g != null) {
            // Имя сообщества в PostDetailScreen — паритет CommunityScreen
            // (PostHolder.lastGroups; ключ = положительный group id).
            PostHolder.lastGroups = mapOf(-peerId to g)
        }
        onPostClick(post)
    }

    /**
     * Скролл ленты канала к посту (по id, если пост загружен; иначе — no-op).
     * Баннер закрепа занимает item 0 (когда закреп есть) → сдвиг на 1.
     */
    fun scrollChannelToPost(post: Post) {
        val idx = channelPosts.indexOfFirst { it.id == post.id }
        if (idx < 0) return
        val hasBanner = channelPosts.firstOrNull { it.isPinned == 1 } != null
        val shift = if (hasBanner) 1 else 0
        scope.launch { channelListState.animateScrollToItem(idx + shift) }
    }

    // Отправка (или отправка редактированного).
    fun doSend() {
        // #IM-CHANNEL-FIX (56-b-3): ранний guard — в канал нельзя писать.
        // Срабатывает ДО любых веток (текст/фото/файлы/стикеры через композер):
        // peerId<0 и (wall-режим канала ИЛИ can_write известен и запрещён).
        // Админ канала (allowed=true) сюда не попадает — обычный композер.
        // Toast вместо errorText: в wall-режиме errorText не рендерится
        // (контент канала = wall-лента), а при пустых messages errorText
        // подменял бы экран ошибкой с «Повторить» (перезагрузка истории).
        if (peerId < 0 && (isChannel || channelWriteDenied)) {
            AppLog.i("ChatDetailScreen",
                "#IM-CHANNEL-FIX: send blocked for channel peer=$peerId (isChannel=$isChannel writeDenied=$channelWriteDenied)")
            Toast.makeText(
                ctx,
                "В этот канал нельзя писать — доступны только чтение и реакции",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val text = inputText.trim()
        // Fix #234 (multi-photo preview): если есть pendingPhotos — отправляем
        // батч фото. Каждое фото: copy URI → temp-file → uploadPhotoForMessage
        // (photos-путь, не docs!) → собираем attachment-строки через запятую.
        // Если есть текст-подпись — она уходит с ПЕРВЫМ сообщением (VK не умеет
        // caption к batch, поэтому фото группируются в одно сообщение с N
        // attachments + текстом).
        val photos = pendingPhotos
        if (photos.isNotEmpty() && editingMsgId == null) {
            if (uploading || sending) return
            scope.launch {
                uploading = true
                sending = true
                // Прячем превью и текст сразу — optimistic UX.
                pendingPhotos = emptyList()
                val caption = text
                inputText = ""
                try {
                    // Fix #137: suppress auth re-launch на время batch-загрузки.
                    val tickBefore = app.tokenInvalidationTicks.value
                    app.suppressNextAuthRelaunch = true
                    // §44 #ATTACH-SUPPRESS-WINDOW (2026-08-03): 60s → 120s.
                    // При auth-cascade (silent refresh падал каждые ~30s) uploads >30s
                    // обрывались на authFailed → «Session expired» → user терял файл.
                    // 120s покрывает large video upload + retry. После FIX-A
                    // (§44 silentRefreshViaRemixsid multi-strategy) cascade не должен
                    // возникать — это belt-and-suspenders.
                    app.suppressAuthRelaunchFor(120_000L)
                    val attachments = mutableListOf<String>()
                    var authFailed = false
                    for (p in photos) {
                        val uri = p.uri
                        // Копируем URI в temp-файл (photos-сервер multipart требует File).
                        val inFile = kotlin.io.path.createTempFile(
                            prefix = "send_photo_",
                            suffix = ".jpg",
                            directory = ctx.cacheDir.toPath(),
                        ).toFile()
                        try {
                            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                                inFile.outputStream().use { out -> ins.copyTo(out) }
                            } ?: run {
                                AppLog.w("ChatDetailScreen", "batch photo: cannot open stream for $uri — skip")
                                continue
                            }
                            val att = app.apiClient.uploadPhotoForMessage(peerId, inFile, "image/*")
                            if (att != null) {
                                attachments += att
                                AppLog.i("ChatDetailScreen", "batch photo uploaded: $att")
                            } else {
                                // Fix #137: token invalidated mid-batch → стоп и inline-диалог.
                                if (app.tokenInvalidationTicks.value != tickBefore) {
                                    AppLog.w("ChatDetailScreen", "batch: token invalidated mid-upload — stop")
                                    authFailed = true
                                    scope.launch { showSessionExpiredDialog = true }
                                    break
                                }
                            }
                        } finally {
                            inFile.delete()
                        }
                    }
                    if (app.tokenInvalidationTicks.value == tickBefore) {
                        app.suppressNextAuthRelaunch = false
                    }
                    if (attachments.isNotEmpty() && !authFailed) {
                        val attachmentStr = attachments.joinToString(",")
                        val msgId = app.apiClient.sendWithAttachment(peerId, attachmentStr, caption)
                        if (msgId > 0) {
                            AppLog.i("ChatDetailScreen", "batch photos sent: ${attachments.size} photos (msgId=$msgId)")
                            reloadMessages()
                        } else {
                            AppLog.w("ChatDetailScreen", "batch photos send failed (msgId=$msgId)")
                            errorText = "Не удалось отправить фото"
                            // Возвращаем фото в превью, чтобы юзер мог повторить.
                            pendingPhotos = photos
                            inputText = caption
                        }
                    } else if (!authFailed) {
                        AppLog.w("ChatDetailScreen", "batch photos: no attachments uploaded")
                        errorText = "Не удалось загрузить фото (VK отклонил)"
                        pendingPhotos = photos
                        inputText = caption
                    }
                } catch (e: Exception) {
                    AppLog.e("ChatDetailScreen", "batch photo send error", e)
                    errorText = "Ошибка отправки фото: ${e.message}"
                    // Возвращаем фото в превью при ошибке — юзер не теряет выбор.
                    pendingPhotos = photos
                    inputText = caption
                } finally {
                    uploading = false
                    sending = false
                }
            }
            return
        }
        // Fix #235 (multi-file) + Fix #297 (видео с прогресс-баром):
        // Батч файлов. Каждый файл грузится отдельно:
        //  - image → photos-путь
        //  - video → video.save pipeline с прогресс-колбэком (Fix #297)
        //  - остальное → docs-путь
        // затем все attachment-строки склеиваются через запятую и уходят одним
        // messages.send (VK принимает до 10 attachment за раз).
        // Текст-подпись уходит с этим же сообщением.
        //
        // Fix #297: НЕ прячем pendingFiles сразу — оставляем chips видимыми
        // во время upload и показываем прогресс-бар на каждом (обновляем
        // pendingFiles[id].progress). Только после успешной send — очищаем.
        val pfiles = pendingFiles
        if (pfiles.isNotEmpty() && editingMsgId == null) {
            if (uploading || sending) return
            scope.launch {
                uploading = true
                sending = true
                val caption = text
                inputText = ""
                try {
                    val tickBefore = app.tokenInvalidationTicks.value
                    app.suppressNextAuthRelaunch = true
                    // §44 #ATTACH-SUPPRESS-WINDOW: 60s → 120s (см. фото-батч выше).
                    app.suppressAuthRelaunchFor(120_000L)
                    val attachments = mutableListOf<String>()
                    var authFailed = false
                    for (pf in pfiles) {
                        // Fix #297: помечаем файл как «загружается» (progress=0.01 → UI сразу показывает бар).
                        pendingFiles = pendingFiles.map { if (it.id == pf.id) it.copy(progress = 0.01f) else it }
                        val att = if (pf.isImage) {
                            app.apiClient.uploadPhotoForMessage(peerId, pf.file, pf.mime)
                        } else if (pf.isVideo) {
                            // Fix #297: видео — отдельный pipeline с прогрессом.
                            // uploadAndSendVideo сам отправляет сообщение (т.к. video_id
                            // нужно привязать сразу), поэтому НЕ добавляем в attachments[].
                            val msgId = app.apiClient.uploadAndSendVideo(
                                peerId = peerId,
                                file = pf.file,
                                displayName = pf.displayName,
                            ) { bytesWritten, totalBytes, fraction ->
                                // Обновляем прогресс на chip (throttle в ProgressRequestBody ~80ms).
                                val pct = 0.01f + fraction * 0.99f
                                pendingFiles = pendingFiles.map {
                                    if (it.id == pf.id) it.copy(progress = pct) else it
                                }
                            }
                            if (msgId > 0) {
                                AppLog.i("ChatDetailScreen", "video sent as separate message: ${pf.displayName} (msgId=$msgId)")
                                // видео уходит отдельным сообщением — caption не прикрепляем сюда
                                // (он уйдёт со следующим messages.send для остальных файлов).
                                pendingFiles = pendingFiles.map { if (it.id == pf.id) it.copy(progress = 1f) else it }
                            } else {
                                AppLog.w("ChatDetailScreen", "video upload failed for ${pf.displayName}")
                                if (app.tokenInvalidationTicks.value != tickBefore) {
                                    authFailed = true
                                    scope.launch { showSessionExpiredDialog = true }
                                    break
                                }
                            }
                            null // видео уже отправлено отдельным сообщением
                        } else {
                            app.apiClient.uploadDocForMessage(pf.file, pf.mime)
                        }
                        if (att != null) {
                            attachments += att
                            pendingFiles = pendingFiles.map { if (it.id == pf.id) it.copy(progress = 1f) else it }
                            AppLog.i("ChatDetailScreen", "batch file uploaded: ${pf.displayName} → $att")
                        } else if (pf.isVideo) {
                            // уже обработано выше (отдельное сообщение или ошибка)
                        } else {
                            if (app.tokenInvalidationTicks.value != tickBefore) {
                                AppLog.w("ChatDetailScreen", "batch file: token invalidated mid-upload — stop")
                                authFailed = true
                                scope.launch { showSessionExpiredDialog = true }
                                break
                            }
                            AppLog.w("ChatDetailScreen", "batch file: upload failed for ${pf.displayName} — skip")
                        }
                    }
                    if (app.tokenInvalidationTicks.value == tickBefore) {
                        app.suppressNextAuthRelaunch = false
                    }
                    if (attachments.isNotEmpty() && !authFailed) {
                        val attachmentStr = attachments.joinToString(",")
                        val msgId = if (caption.isNotBlank()) {
                            app.apiClient.sendWithAttachment(peerId, attachmentStr, caption)
                        } else {
                            app.apiClient.sendWithAttachment(peerId, attachmentStr)
                        }
                        if (msgId > 0) {
                            AppLog.i("ChatDetailScreen", "batch files sent: ${attachments.size} attachments (msgId=$msgId)")
                            // Чистим temp-файлы только после успешной отправки.
                            pfiles.forEach { it.file.delete() }
                            pendingFiles = emptyList()
                            reloadMessages()
                        } else {
                            AppLog.w("ChatDetailScreen", "batch files send failed (msgId=$msgId)")
                            errorText = "Не удалось отправить файлы"
                            pendingFiles = pfiles.map { it.copy(progress = 0f) }
                            inputText = caption
                        }
                    } else if (!authFailed && pfiles.all { it.isVideo }) {
                        // Все файлы были видео — уже отправлены отдельными сообщениями.
                        // Если есть caption — отправляем его отдельным текстовым сообщением.
                        if (caption.isNotBlank()) {
                            app.apiClient.messagesSend(peerId, caption)
                        }
                        pfiles.forEach { it.file.delete() }
                        pendingFiles = emptyList()
                        reloadMessages()
                    } else if (!authFailed) {
                        AppLog.w("ChatDetailScreen", "batch files: no attachments uploaded")
                        errorText = "Не удалось загрузить файлы (VK отклонил)"
                        pendingFiles = pfiles.map { it.copy(progress = 0f) }
                        inputText = caption
                    }
                } catch (e: Exception) {
                    AppLog.e("ChatDetailScreen", "batch file send error", e)
                    errorText = "Ошибка отправки файлов: ${e.message}"
                    pendingFiles = pfiles
                    inputText = caption
                } finally {
                    uploading = false
                    sending = false
                }
            }
            return
        }
        if (text.isBlank() || sending) return
        val editId = editingMsgId
        if (editId != null) {
            // Редактирование.
            scope.launch {
                sending = true
                try {
                    editMessage(editId, text)
                    editingMsgId = null
                    inputText = ""
                } finally {
                    sending = false
                }
            }
            return
        }
        // Обычная отправка.
        // Fix #202: если есть replyingTo, но у него нет conversation_message_id
        // — нельзя ответить (VK API 5.221+ требует cmid, reply_to deprecated).
        // Вариант B (по решению юзера): не отправлять, показать ошибку.
        // Случай редкий (action-сообщения, service-сообщения), но случается.
        //
        // Fix #137b: улучшено сообщение — раньше было «Нельзя ответить на это
        // сообщение» (пользователь не понимал почему). Теперь объясняем причину
        // и предлагаем решение (перезайти в чат / обновить историю). Fix #134
        // (расширенный парсинг LongPoll) уже решил основную причину — свежие
        // входящие сообщения теперь приходят с cmid сразу.
        val replyTarget = replyingTo
        if (replyTarget != null && replyTarget.conversationMessageId == null) {
            errorText = "Не удалось сослаться на это сообщение (устаревший формат). " +
                "Попробуйте обновить чат: потяните вниз для загрузки."
            AppLog.w("ChatDetailScreen", "reply skipped: cmid is null for msg id=${replyTarget.id}")
            return
        }
        scope.launch {
            sending = true
            // Fix #137 (2026-XX): РАНЬШЕ optimistic Message создавался БЕЗ
            // replyMessage — даже когда пользователь отвечал на сообщение,
            // оптимистичный баббл показывался как обычное текстовое сообщение
            // без reply-бейджа. Reply-бейдж появлялся только после LongPoll
            // re-fetch через messagesGetHistory (через 200-500мс). Пользователь
            // жаловался: «отсутствует ответ в чатах» — он не видел визуального
            // подтверждения что его reply ушел.
            //
            // Теперь кладём replyMessage = replyTarget в optimistic — бейдж
            // виден сразу. После re-fetch оптимистичное сообщение заменяется
            // полноценным (с тем же replyMessage, уже от VK API).
            val optimistic = Message(
                id = -System.currentTimeMillis(),
                peerId = peerId, fromId = 0,
                date = System.currentTimeMillis() / 1000,
                text = text, out = 1, readState = 0,
                // Fix #137: пробрасываем replyMessage для немедленного бейджа.
                replyMessage = replyTarget,
            )
            messages = listOf(optimistic) + messages
            inputText = ""
            listState.animateScrollToItem(0) // индекс 0 = внизу (новое сообщение)
            try {
                // Fix #203c: передаём cmid (conversation_message_id). Внутри
                // messagesSend он кладётся в параметр `forward` с JSON
                // {peer_id, conversation_message_ids:[cmid], is_reply:true} —
                // это единственный рабочий механизм reply в VK API 5.221+
                // (reply_to полностью deprecated → error 100).
                val replyCmid = replyTarget?.conversationMessageId
                val id = app.apiClient.messagesSend(peerId, text, replyCmid = replyCmid)
                if (id > 0) {
                    replyingTo = null  // сбрасываем reply после отправки
                } else {
                    // Fix #233 (P1-5): откатываем optimistic message при ошибке.
                    // Раньше баббл с id=-curTime оставался в списке навсегда —
                    // пользователь видел «отправленное» сообщение, которое
                    // на самом деле не ушло. Восстанавливаем текст в поле ввода
                    // чтобы пользователь мог попробовать снова.
                    // #IM-CHANNEL-FIX (56-b-3): VK отклонил отправку кодом 901/902
                    // («нет права писать» — для каналов peer<0 это «вы не админ»)
                    // — показываем понятный текст вместо голого «Не удалось
                    // отправить сообщение». lastApiErrorCode живёт ОДИН вызов
                    // (#STALE-ERR-FIX) → после messagesSend это код ИМЕННО этого
                    // отклонения. Для peer>0 (приватность юзера) текст не трогаем.
                    val sendErrCode = app.apiClient.lastApiErrorCode
                    val isChannelWriteDeny = peerId < 0 && (sendErrCode == 901 || sendErrCode == 902)
                    errorText = if (isChannelWriteDeny) {
                        "Писать в этот канал нельзя (вы не админ)"
                    } else {
                        "Не удалось отправить сообщение"
                    }
                    // #IM-CHANNEL-FIX (56-b-3): в wall-режиме errorText не рендерится
                    // (контент = wall-лента) — дублируем честный текст Toast'ом,
                    // иначе отказ канала остаётся невидимым.
                    if (isChannelWriteDeny) {
                        Toast.makeText(ctx, errorText, Toast.LENGTH_LONG).show()
                    }
                    messages = messages.filterNot { it.id == optimistic.id }
                    inputText = text
                    AppLog.w("ChatDetailScreen", "send failed (id=$id, errCode=$sendErrCode) — optimistic message rolled back, text restored to input (Fix #233)")
                }
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "send error", e)
                errorText = "Ошибка отправки: ${e.message}"
                // Fix #233 (P1-5): откатываем optimistic и при exception.
                messages = messages.filterNot { it.id == optimistic.id }
                inputText = text
                AppLog.w("ChatDetailScreen", "send exception — optimistic message rolled back, text restored (Fix #233)")
            } finally {
                sending = false
            }
        }
    }

    // Первичная загрузка истории.
    // #IM-EMPTY-HONEST: historyReload в ключах — «Повторить» в пустом состоянии
    // перезапускает всю загрузку без выхода из чата.
    LaunchedEffect(peerId, historyReload) {
        // FIX: используем корутину LaunchedEffect напрямую вместо scope.launch,
        // чтобы избежать ForgottenCoroutineScopeException при пересоздании Activity.
        loading = true
        endReached = false
        isPinnedToNewest = true
        errorText = null
        // P0.2: отменяем системное уведомление для этого диалога — пользователь
        // открыл чат и видит сообщения, уведомление больше не нужно.
        re.pinok.realtime.MessageNotifier.cancelNotification(ctx, peerId)
        // Fix #216 (P1.1): proactive keepAlive перед загрузкой истории чата.
        // Если токен истекает в ближайшие 5 минут — обновим его сейчас, чтобы
        // messagesGetHistoryWithProfiles не получил error 5/1117 и не запустил
        // AuthActivity overlay поверх чата. keepAlive вызывает silentAuth
        // который теперь умеет silent refresh через remixsid (Path 1.5).
        // Это особенно важно при возврате в чат после долгого простоя.
        try {
            app.exchangeAuthRepository.keepAlive()
        } catch (e: Exception) {
            // ignore — keepAlive failure не блокирует загрузку чата,
            // ensureFreshToken в callInternal всё равно сработает.
        }
        // ═══ #CHANNEL-WALL-MODE (Fix #393): определение канала ДО messages-истории ═══
        // Root-cause «каналы — диалоги не открываются, ошибки»: контент канала
        // (peer = -<group_id>, can_write.allowed == false) — это ПОСТЫ сообщества
        // (wall.get; снапшот 29-a: web-канал = blog-бэкенд, НЕ messages.getHistory),
        // а messages.getHistory для таких пиров возвращает ошибку/пусто → юзер видел
        // generic-ошибку вместо канала (isChannel раньше определялся ПОСЛЕ истории).
        // Для peerId < 0 сначала спрашиваем chat state; обычные диалоги сообществ
        // (can_write разрешён) идут дальше по обычному пути — эта ветка их не трогает.
        var chatInfoResolved = false
        // #IM-CHANNEL-OPEN: знает ли chat state поле can_write. VK в редких
        // ответах НЕ присылает can_write для группового пира → chat.isChannel
        // (peer.id<0 && canWrite?.allowed==false) = false даже для КАНАЛА —
        // и без этой метки экран уходил в messages.getHistory с generic-ошибкой.
        var chatCanWriteKnown = false
        AppLog.i("ChatDetailScreen",
            "#IM-CHANNEL-OPEN enter: peerId=$peerId channelModeEnabled=$channelModeEnabled title='$currentTitle'")
        if (peerId < 0 && channelModeEnabled) {
            try {
                val chats = app.apiClient.messagesGetConversationsById(listOf(peerId))
                val chat = chats.firstOrNull()
                if (chat != null) {
                    chatInfoResolved = true
                    val cw = chat.canWrite
                    chatCanWriteKnown = cw != null
                    // #IM-CHANNEL-FIX (56-b-3): фиксируем can_write-гейт (независимо
                    // от тумблера channelModeEnabled — см. bottomBar/doSend).
                    if (cw != null) {
                        channelWriteDenied = !cw.allowed
                    }
                    val push = chat.pushSettings
                    muted = if (push != null) push.isMuted() else false
                    isChannel = chat.isChannel
                    AppLog.i("ChatDetailScreen",
                        "#IM-CHANNEL-OPEN chat state resolved: isChannel=${chat.isChannel} canWriteKnown=$chatCanWriteKnown")
                    val t = chat.peer.title
                    if (t != null && t.isNotBlank() && t != "Диалог" && t != "DELETED" && t != currentTitle) {
                        currentTitle = t
                    }
                    val ph = chat.peer.photo
                    // #CHANNEL-AVATAR: chat.peer.photo — это photo_100, мелкий фолбэк.
                    // Не даём ему перезаписать уже установленный аватар канала
                    // (photo_base из loadChannelMeta groups.getById) — берём только
                    // пока currentPhoto пуст.
                    if (currentPhoto.isNullOrBlank() && ph != null && ph.isNotBlank() && ph != currentPhoto) {
                        currentPhoto = ph
                    }
                } else {
                    // #IM-CHANNEL-OPEN: state пуст (offline/err=запрос отклонён) —
                    // дальше сработает wallGet-проба; крошка для следующего логката.
                    AppLog.w("ChatDetailScreen",
                        "#IM-CHANNEL-OPEN chat state EMPTY (peerId=$peerId, errSet=${app.apiClient.lastApiError != null})")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("ChatDetailScreen", "#CHANNEL-WALL-MODE conversationsById failed: ${e.message}")
            }
            if (isChannel) {
                AppLog.i("ChatDetailScreen",
                    "#IM-CHANNEL-OPEN wall-mode ON (chat state = channel): peerId=$peerId")
                // Шапка канала: «N подписчиков» + имя/аватар/GroupInfo —
                // #IM-CHANNEL-FIX (56-b-4): общая fun (та же вызывается из
                // probe/empty-history/error веток — см. loadChannelMeta).
                loadChannelMeta()
                // wall-режим: messages-история для канала не запрашивается вовсе.
                loadChannelPosts(initial = true)
                loading = false
                return@LaunchedEffect
            }
            // #IM-CHANNEL-OPEN: пробуем wallGet первично не только при недоступном
            // chat state (!chatInfoResolved), но и когда state РЕЗОЛВНУЛСЯ БЕЗ
            // can_write (!chatCanWriteKnown) — иначе канал с таким ответом VK
            // уходил в messages.getHistory и показывал generic-ошибку (симптом
            // «диалог канала не открывается»). Если стена читается — это канал;
            // generic-ошибку history для негативного пира не показываем никогда.
            if (!chatInfoResolved || !chatCanWriteKnown) {
                val probe = app.apiClient.wallGet(ownerId = peerId, count = 30, offset = 0)
                AppLog.i("ChatDetailScreen",
                    "#IM-CHANNEL-OPEN probe wallGet: posts=${probe.size} " +
                        "apiErr=${app.apiClient.lastApiError != null} resolved=$chatInfoResolved canWriteKnown=$chatCanWriteKnown")
                if (probe.isNotEmpty() || app.apiClient.lastApiError == null) {
                    AppLog.i("ChatDetailScreen",
                        "#IM-CHANNEL-OPEN wall-mode ON (probe): peerId=$peerId posts=${probe.size}")
                    isChannel = true
                    // #IM-CHANNEL-FIX (56-b-4): подписчики/имя/аватар в шапку —
                    // раньше в probe-ветке groupsGetById не вызывался вовсе.
                    loadChannelMeta()
                    loadChannelPosts(initial = true, preloaded = probe)
                    loading = false
                    return@LaunchedEffect
                }
                AppLog.w("ChatDetailScreen",
                    "#IM-CHANNEL-OPEN probe failed — fallback to messages.getHistory: peerId=$peerId")
            }
        }
        try {
            // #74: используем messagesGetHistoryWithProfiles — возвращает профили для аватарок
            val result = app.apiClient.messagesGetHistoryWithProfiles(peerId, count = pageSize)
            messages = result.messages.distinctBy { it.id }
            chatProfiles = result.profiles
            if (result.messages.size < pageSize) endReached = true
            if (result.messages.isEmpty()) {
                // #IM-EMPTY-HONEST (волна 36): failure от VKApiClient имеет
                // приоритет — «Нет сообщений» теперь ТОЛЬКО когда сервер
                // реально ответил успехом с пустой историей. Раньше офлайн-гейт/
                // нет токена/битый ответ/парсинг молча превращались в лживое
                // «Нет сообщений» (жалоба: «открывал диалог — "нет сообщений",
                // но они есть»).
                val err = result.failure ?: app.apiClient.lastApiError
                if (peerId < 0 && channelModeEnabled && (!chatInfoResolved || !chatCanWriteKnown)) {
                    // Fix #393 + #IM-CHANNEL-OPEN: канал вернул пустую messages-историю
                    // (или can_write неизвестен) — контент в wall-режиме, generic-ошибку
                    // не показываем.
                    AppLog.i("ChatDetailScreen",
                        "#IM-CHANNEL-OPEN history empty → wall-mode: peerId=$peerId resolved=$chatInfoResolved canWriteKnown=$chatCanWriteKnown")
                    // #IM-CHANNEL-FIX (56-b-4): метаданные канала и в этой ветке
                    // (раньше тут шапка оставалась без подписчиков).
                    loadChannelMeta()
                    loadChannelPosts(initial = true)
                } else {
                    errorText = when {
                        result.failure != null -> "Ошибка: ${result.failure}"
                        err != null -> "Ошибка: $err"
                        else -> "Нет сообщений"
                    }
                }
            }
            // FIX (P5.2): помечаем загруженные сообщения как прочитанные.
            // Safety-net: клик по чату в MessagesScreen уже вызывает markAsRead,
            // но при открытии через deep-link (из уведомления) этого не происходит.
            // DNR (Do Not Read) мод проверяется внутри messagesMarkAsRead.
            if (messages.isNotEmpty()) {
                val newestId = messages.maxOf { it.id }
                scope.launch {
                    try {
                        val ok = app.apiClient.messagesMarkAsRead(peerId, newestId)
                        AppLog.d("ChatDetailScreen",
                            "markAsRead on open: peer=$peerId upTo=$newestId ok=$ok")
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Fix #151: rememberCoroutineScope отменяется при уходе экрана /
                        // рекомпозиции — нормальный lifecycle, не ошибка. Раньше ловилось
                        // в catch(Exception) и логировалось как "markAsRead on open failed:
                        // rememberCoroutineScope left the composition". Пробрасываем отмену.
                        throw e
                    } catch (e: Exception) {
                        AppLog.w("ChatDetailScreen", "markAsRead on open failed: ${e.message}")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Fix #114: LeftCompositionCancellationException — нормальная отмена
            // корутины (composition left, user navigated away). НЕ логируем как
            // ошибку и НЕ продолжаем выполнение — иначе корутина-зомби продолжает
            // делать API вызовы (messagesGetLastActivity, messagesGetConversationsById)
            // на мёртвой composition, засоряя логи и расходуя токен.
            throw e
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "Failed to load history", e)
            if (peerId < 0 && channelModeEnabled && (!chatInfoResolved || !chatCanWriteKnown)) {
                // Fix #393 + #IM-CHANNEL-OPEN: messages-история канала упала —
                // переключаемся на wall-режим (контент канала = посты сообщества)
                // вместо generic-ошибки. Если wallGet тоже упадёт — честный
                // channelPostsError с «Повторить» (честное состояние).
                AppLog.i("ChatDetailScreen",
                    "#IM-CHANNEL-OPEN history failed → wall-mode: peerId=$peerId resolved=$chatInfoResolved canWriteKnown=$chatCanWriteKnown")
                // #IM-CHANNEL-FIX (56-b-4): метаданные канала и в error-ветке
                // (раньше тут шапка оставалась без подписчиков).
                loadChannelMeta()
                loadChannelPosts(initial = true)
            } else {
                errorText = "Не удалось загрузить: ${e.message}"
            }
        } finally {
            loading = false
        }
        // Fix #233 (sticker-enrich): eager load стикер-паков для заполнения
        // StickerAnimationCache. Без этого стикеры в сообщениях рендерятся
        // статично (VK не возвращает animation_url в attachments). Кеш
        // заполняется асинхронно — не блокирует UI. Если уже загружены —
        // loadStickers() сразу вернётся (stickerPacks.isNotEmpty() check).
        if (stickerPacks.isEmpty() && !stickerLoading) {
            loadStickers()
        }
        // #60: загружаем last activity (online статус собеседника)
        if (peerId > 0 && peerId < 2000000000L) {
            try {
                lastActivity = app.apiClient.messagesGetLastActivity(peerId)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Exception) {}
        }
        // P0.3 + P3.2 + P3.4: загружаем pinned message (group chats) + push settings
        // (mute state) + can_write (channel detection).
        // messages.getConversationsById возвращает chat.pinned_message + push_settings + can_write.
        try {
            val chats = app.apiClient.messagesGetConversationsById(listOf(peerId))
            val chat = chats.firstOrNull()
            pinnedMessage = if (isGroupChat) chat?.pinnedMessage else null
            // Fix #122: используем единый isMuted() helper (учитывает no_sound,
            // disabled_forever, disabled_until).
            muted = chat?.pushSettings?.isMuted() == true
            // W30-1 #IM-UNREAD-MENU: стартовое состояние метки — серверный
            // unread_count того же ответа. chat — локальная val, явная if-проверка
            // (NULL-ЯВНО: без ?. чейнинг в новом коде).
            hasUnreadMark = chat != null && chat.unreadCount > 0
            // P3.4: канал = группа (peerId < 0) где can_write.allowed == false.
            // Только если feature-flag включён — иначе обычный режим (composer виден).
            // #IM-CHANNEL-OPEN: НЕ затираем wall-режим, уже включённый ранее в этом
            // же LaunchedEffect (probe / пустая история): этот второй запрос идёт
            // ПОСЛЕ fallback-переключения, и state без can_write вернул бы здесь
            // isChannel=false — «убил» бы уже включённую wall-ленту канала.
            if (!isChannel) {
                isChannel = channelModeEnabled && chat?.isChannel == true
            }
            // #IM-CHANNEL-FIX (56-b-3): can_write-гейт из ЭТОГО резолва chat state —
            // он выполняется ВСЕГДА (в т.ч. при выключенном тумблере channelModeEnabled,
            // когда ранний канальный блок пропущен). «canWrite известен и запрещён»
            // → read-only футер + doSend-гейт независимо от тумблера. allowed=true
            // (админ/диалог сообщества) сбрасывает гейт — семантика не тронута.
            val chatForGate = chat
            val gateCw = chatForGate?.canWrite
            if (gateCw != null) {
                channelWriteDenied = !gateCw.allowed
            }
            // Fix #133: добиваем актуальные title/photo из того же ответа.
            // messagesGetConversationsById с extended=1 отдаёт profiles[]/groups[]
            // и сам резолвит имя/аватарку (через resolveMissingPeerInfo). Если
            // список диалогов передал «Диалог»/null — здесь шапка обновится.
            chat?.peer?.title?.takeIf { it.isNotBlank() && it != "Диалог" }?.let {
                if (it != currentTitle) currentTitle = it
            }
            // #CHANNEL-AVATAR: chat.peer.photo = photo_100, мелкий фолбэк. Не даём
            // перезаписать уже установленный канальный аватар (photo_base) —
            // используем только пока currentPhoto пуст.
            if (currentPhoto.isNullOrBlank()) {
                chat?.peer?.photo?.takeIf { it.isNotBlank() }?.let {
                    if (it != currentPhoto) currentPhoto = it
                }
            }
            // #CHANNEL-DELETED-TITLE (2026-09-08): VK в messages.getConversationsById
            // для КАНАЛЬНЫх диалогов (peerId = -gid) иногда отдаёт peer.title =
            // «DELETED» (peer-запись без привязки к groups[]) — шапка чата
            // перескакивала с нормального имени (из списка диалогов) на DELETED.
            // Фолбэк: для отрицательного peerId с битым/пустым титулом резолвим
            // имя/фото через groups.getById(-peerId) — тот же источник, что и
            // список диалогов. Пустой ответ (канал недоступен/удалён) честно
            // оставляет текущие значения.
            if (peerId < 0) {
                val badTitle = currentTitle.isBlank() ||
                    currentTitle == "Диалог" || currentTitle == "DELETED"
                if (badTitle) {
                    try {
                        val g = app.apiClient.groupsGetById(listOf(-peerId)).firstOrNull()
                        if (g != null && g.name.isNotBlank()) {
                            if (g.name != currentTitle) currentTitle = g.name
                            // NULL-ЯВНО: elvis на nullable-модели GroupInfo
                            // (photo200 может отсутствовать — фолбэк на photo100,
                            // паттерн рендера аватарок всего проекта).
                            // #CHANNEL-AVATAR: канальный приоритет photo_base → photo200 → photo100.
                            val gPhoto = g.photoBase ?: g.photo200 ?: g.photo100
                            if (gPhoto != null && gPhoto.isNotBlank() && gPhoto != currentPhoto) {
                                currentPhoto = gPhoto
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.w("ChatDetailScreen", "#CHANNEL-DELETED-TITLE groupsGetById failed: ${e.message}")
                    }
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            pinnedMessage = null
            // #IM-CHANNEL-OPEN: сброс только если wall-режим ещё НЕ включён
            // (см. комментарий у присвоения выше — не убиваем включённый канал).
            if (!isChannel) {
                isChannel = false
            }
        }
    }

    // #60: поиск по сообщениям
    fun performSearch() {
        if (searchQuery.isBlank()) return
        scope.launch {
            searching = true
            try {
                searchResults = app.apiClient.messagesSearch(searchQuery, peerId)
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "search failed", e)
            } finally {
                searching = false
            }
        }
    }

    // #IM-SEARCH (Fix #394): скролл к сообщению в истории по id. Возвращает
    // false, если сообщение не входит в загруженный диапазон (вызывающая
    // сторона показывает preview-диалог с догрузкой loadUntilFoundAndScroll).
    // Индекс считается по chatListItems (сообщения + дата-сепараторы + unread
    // divider), а не по messages — иначе скролл промахивался бы при включённых
    // сепараторах.
    fun scrollToLoadedMessage(msgId: Long): Boolean {
        val rowIdx = buildChatListItems(
            messages = messages,
            groupingEnabled = groupingEnabled,
            dateSeparatorsEnabled = dateSeparatorsEnabled,
            unreadDividerEnabled = unreadDividerEnabled,
        ).indexOfFirst { item ->
            item is ChatListItem.MessageRow && item.message.id == msgId
        }
        if (rowIdx < 0) return false
        scope.launch {
            listState.animateScrollToItem(rowIdx)
            highlightedMsgId = msgId
        }
        return true
    }

    LaunchedEffect(loading) {
        if (!loading && messages.isNotEmpty()) {
            listState.scrollToItem(0) // скролл к новейшему (внизу при reverseLayout=true)
        }
    }

    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val firstVisible = info.visibleItemsInfo.firstOrNull()?.index ?: -1
            messages.isNotEmpty() && (firstVisible <= 1 || messages.size <= 3)
        }
        .distinctUntilChanged()
        .collect { isPinnedToNewest = it }
    }

    fun loadOlder() {
        if (loadingOlder || endReached || messages.isEmpty()) return
        scope.launch {
            loadingOlder = true
            val firstIdx = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            try {
                val older: List<Message> = if (channelHistoryMode) {
                    // Task 67 #CHANNELS-HIST: старая страница канала —
                    // channels.getHistory со start_cmid = минимальный cmid текущей
                    // (граница «старее этого»; дедуп по id покрывает и
                    // inclusive-семантику start_cmid). messages.getHistory для
                    // каналов пуст — обычная offset-пагинация дала бы пустоту.
                    val oldest = messages.minOf { it.id }
                    // #CHANNEL-FILTERS (Task #CHANNELS-UI): пагинация «старее»
                    // сохраняет активный фильтр ленты (иначе подмешались бы
                    // посты других типов). null = «Всё».
                    app.apiClient.channelsGetHistory(
                        peerId,
                        count = pageSize,
                        startCmid = oldest,
                        filter = channelFeedFilter,
                    ).messages
                } else {
                    app.apiClient.messagesGetHistory(
                        peerId, count = pageSize, offset = messages.size,
                    )
                }.filter { np -> messages.none { it.id == np.id } }
                if (older.isEmpty()) {
                    endReached = true
                } else {
                    // Старые сообщения добавляем в конец (высокий индекс = вверху при reverseLayout).
                    messages = (messages + older).distinctBy { it.id }
                    if (older.size < pageSize) endReached = true
                }
            } catch (e: Exception) {
                AppLog.w("ChatDetailScreen", "loadOlder failed: ${e.message}")
            } finally {
                loadingOlder = false
            }
        }
    }

    /**
     * #CHANNEL-FILTERS (Task #CHANNELS-UI): смена фильтра ленты канала.
     * Только для канального history-режима (channelHistoryMode). Сбрасывает
     * историю к первой странице с новым фильтром и скроллит к новейшему.
     * Аддитивно: обычные (неканальные) чаты и стену-wal не трогает.
     */
    fun applyChannelFeedFilter(f: String?) {
        if (!channelHistoryMode) return
        if (channelFeedFilter == f) return
        channelFeedFilter = f
        scope.launch {
            loading = true
            messages = emptyList()
            channelHistoryMode = true
            channelWallFallback = true
            endReached = false
            try {
                val ch = app.apiClient.channelsGetHistory(
                    peerId, count = pageSize, filter = channelFeedFilter,
                )
                if (ch.messages.isNotEmpty()) {
                    messages = ch.messages
                    chatProfiles = ch.profiles
                    if (ch.messages.size < pageSize) endReached = true
                    channelPostsError = null
                } else if (ch.failure == null) {
                    // Честная пустота под фильтр (нет донат-постов и т.п.).
                    messages = emptyList()
                    endReached = true
                } else {
                    errorText = ch.failure
                }
                if (listState.layoutInfo.totalItemsCount > 0) {
                    listState.scrollToItem(0)
                }
            } catch (e: Exception) {
                AppLog.w("ChatDetailScreen", "applyChannelFeedFilter failed: ${e.message}")
            } finally {
                loading = false
            }
        }
    }

    // Пагинация при скролле вверх (к старым сообщениям).
    // При reverseLayout=true высокий индекс = визуально наверху.
    LaunchedEffect(listState, messages.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            messages.isNotEmpty() && lastVisible >= messages.size - 2
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadOlder() }
    }

    // Fix #206: авто-сброс подсветки целевого сообщения через 1.5с.
    // БЕЗ анимации (пользователь просил минимум анимаций) — просто убираем id.
    LaunchedEffect(highlightedMsgId) {
        if (highlightedMsgId != null) {
            kotlinx.coroutines.delay(1500L)
            highlightedMsgId = null
        }
    }

    // Fix #206: обработчик клика по плашке ответа.
    //   1. Ищем исходное сообщение в загруженной истории (по cmid, fallback по id).
    //   2. Найдено → скролл + подсветка (без анимации).
    //   3. Не найдено → открываем AlertDialog с содержимым replyMessage + кнопкой
    //      «показать в чате» (догрузка старой истории вверх до нахождения cmid).
    val onReplyBadgeClick: (Message) -> Unit = { reply ->
        val targetCmid = reply.conversationMessageId
        val idx = messages.indexOfFirst { m ->
            // Приоритет: совпадение по conversation_message_id (надёжнее, т.к.
            // replyMessage.id может отличаться от id в текущей истории у некоторых
            // edge-cases с fwd/действиями). Fallback — по id.
            (targetCmid != null && m.conversationMessageId == targetCmid) || m.id == reply.id
        }
        if (idx >= 0) {
            val target = messages[idx]
            scope.launch {
                listState.animateScrollToItem(idx)
                highlightedMsgId = target.id
            }
        } else {
            // Цель вне загруженной истории — показываем preview-диалог.
            replyPreviewMsg = reply
        }
    }

    // Fix #206: догрузка старой истории вверх, пока не найдём целевое сообщение
    // (по cmid или id). Вызывается из кнопки «показать в чате» в preview-диалоге.
    // Лимит итераций — защита от бесконечного цикла (например, сообщение удалено).
    fun loadUntilFoundAndScroll(target: Message) {
        if (loadingReplyTarget) return
        scope.launch {
            loadingReplyTarget = true
            try {
                val targetCmid = target.conversationMessageId
                var iterations = 0
                val maxIterations = 20  // ~20 * pageSize сообщений = достаточно для любого чата
                var found: Message? = null
                var reachedEnd = endReached
                while (found == null && !reachedEnd && iterations < maxIterations) {
                    iterations++
                    val offset = messages.size
                    val older = app.apiClient.messagesGetHistory(
                        peerId, count = pageSize, offset = offset,
                    ).filter { np -> messages.none { it.id == np.id } }
                    if (older.isEmpty()) {
                        reachedEnd = true
                        endReached = true
                        break
                    }
                    // Найдено в новой порции?
                    found = older.firstOrNull { m ->
                        (targetCmid != null && m.conversationMessageId == targetCmid) ||
                            m.id == target.id
                    }
                    messages = (messages + older).distinctBy { it.id }
                    if (older.size < pageSize) {
                        reachedEnd = true
                        endReached = true
                    }
                }
                if (found != null) {
                    // Закрываем preview, скроллим, подсвечиваем.
                    replyPreviewMsg = null
                    val idx = messages.indexOfFirst { m ->
                        (targetCmid != null && m.conversationMessageId == targetCmid) ||
                            m.id == target.id
                    }
                    if (idx >= 0) {
                        listState.animateScrollToItem(idx)
                        highlightedMsgId = messages[idx].id
                    }
                } else {
                    // Не нашли даже после догрузки — оставляем preview открытым,
                    // показываем Toast (сообщение могло быть удалено).
                    Toast.makeText(
                        ctx,
                        "Сообщение не найдено (возможно, удалено)",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.w("ChatDetailScreen", "loadUntilFound failed: ${e.message}")
                Toast.makeText(
                    ctx,
                    "Ошибка загрузки: ${e.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                loadingReplyTarget = false
            }
        }
    }

    // LongPoll real-time.
    LaunchedEffect(peerId) {
        app.longPollClient.events.collect { ev ->
            val relevant = when (ev) {
                is LongPollEvent.NewMessage -> ev.peerId == peerId
                is LongPollEvent.EditMessage -> ev.peerId == peerId
                is LongPollEvent.ReadInbox -> ev.peerId == peerId
                is LongPollEvent.ReadOutbox -> ev.peerId == peerId
                LongPollEvent.Reset -> true
                else -> false
            }
            if (!relevant) return@collect
            // #IM-CHANNEL-OPEN: у канала (peerId<0, wall-режим) НЕТ messages-истории —
            // LP-события (Reset/«сообщение» канала) не должны дёргать
            // messages.getHistory для негативного пира (VK вернёт ошибку:
            // мусорные вызовы + error-спам). Контент канала — только wall.get.
            if (isChannel) return@collect
            if (loading || loadingOlder) return@collect
            // P2.6 + Fix #296: ReadOutbox/ReadInbox — обновляем readState локально
            // без re-fetch. Это даёт мгновенное обновление ✓→✓✓ в UI.
            //
            // Fix #296 («нет двух галочек когда сообщение просмотрено»):
            // VK LongPoll code 7 (ReadOutbox) и code 6 (ReadInbox) возвращают
            // в ev[2] conversation_message_id (cmid), НЕ message_id. Раньше
            // сравнение было `msg.id <= ev.upToMsgId` — msg.id это message_id
            // (глобальный счётчик), а ev.upToMsgId это cmid (локальный для
            // диалога). В 1-1 диалогах message_id ≠ cmid (разные счётчики),
            // в групповых чатах — тоже. Сравнение никогда не срабатывало →
            // readState оставался 0 → ✓✓ не появлялись (только ✓).
            // Теперь сравниваем по cmid (приоритет), с fallback на msg.id для
            // старых сообщений без cmid.
            when (ev) {
                is LongPollEvent.ReadOutbox -> {
                    AppLog.d("ChatDetailScreen", "LP ReadOutbox: peer=${ev.peerId} upTo(cmid)=${ev.upToMsgId}")
                    messages = messages.map { msg ->
                        if (msg.isOut && isReadUpTo(msg, ev.upToMsgId)) msg.copy(readState = 1) else msg
                    }
                    return@collect
                }
                is LongPollEvent.ReadInbox -> {
                    AppLog.d("ChatDetailScreen", "LP ReadInbox: peer=${ev.peerId} upTo(cmid)=${ev.upToMsgId}")
                    messages = messages.map { msg ->
                        if (!msg.isOut && isReadUpTo(msg, ev.upToMsgId)) msg.copy(readState = 1) else msg
                    }
                    return@collect
                }
                else -> {}
            }
            val shouldFetch = when (ev) {
                is LongPollEvent.NewMessage -> isPinnedToNewest
                else -> true
            }
            if (!shouldFetch) return@collect
            scope.launch {
                try {
                    val targetCount = maxOf(messages.size, pageSize)
                    val fresh = app.apiClient.messagesGetHistory(peerId, count = targetCount)
                        .distinctBy { it.id }
                    if (fresh.isNotEmpty()) {
                        messages = fresh
                        errorText = null
                        if (isPinnedToNewest) {
                            listState.animateScrollToItem(0)
                            // FIX (P5.2): помечаем новые сообщения как прочитанные,
                            // пока пользователь в чате и видит последние сообщения.
                            // Если пользователь прокрутил вверх (не pinned to newest) —
                            // НЕ помечаем, чтобы он сам увидел непрочитанные при возврате.
                            val newestId = fresh.maxOf { it.id }
                            try {
                                val ok = app.apiClient.messagesMarkAsRead(peerId, newestId)
                                AppLog.d("ChatDetailScreen",
                                    "markAsRead on LP new msg: peer=$peerId upTo=$newestId ok=$ok")
                            } catch (e: Exception) {
                                AppLog.w("ChatDetailScreen",
                                    "markAsRead on LP failed: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w("ChatDetailScreen", "LongPoll re-fetch error: ${e.message}")
                }
            }
        }
    }

    // P0.1: typing indicator — collect Typing events for this peer.
    // LongPoll codes: 61 (DM typing), 62 (chat typing). See LongPollClient.kt.
    // VK resends typing events every ~4s while user keeps typing; we treat
    // any event within TYPING_TIMEOUT_MS as "still typing".
    val myUserId = remember { app.exchangeAuthRepository.userId() }

    // P0.25 #VOICE-ASR-FETCH: авто-запрос transcript для голосовых без расшифровки.
    // VK LongPoll НЕ возвращает transcript — ASR готовится на сервере 5-30 сек.
    // Запускаем периодический poll: каждые 10 сек проверяем есть ли voice без
    // done-transcript. Если есть — запрашиваем getById. Max 6 попыток (60 сек).
    // После 6 попыток прекращаем — если VK не подготовил ASR за 60 сек, вероятно
    // голосовое слишком короткое/шумное и ASR невозможен.
    LaunchedEffect(peerId) {
        var attempt = 0
        while (attempt < 6) {
            kotlinx.coroutines.delay(10000L)
            val hasPendingVoice = messages.any { m ->
                m.attachments?.any { att ->
                    val am = att.doc?.audioMsg ?: att.audioMessage
                    am != null && am.transcriptState != "done"
                } == true
            }
            if (!hasPendingVoice) break
            fetchVoiceTranscripts()
            attempt++
        }
    }

    LaunchedEffect(peerId, typingEnabled) {
        if (!typingEnabled) {
            typingUsers = emptyMap()
            return@LaunchedEffect
        }
        // #TYPING-FIX: сброс стейта при входе в эффект — смена peerId (та же
        // composition) или toggle настройки не должны оставлять typing-записи
        // предыдущего чата активными в новом.
        typingUsers = emptyMap()
        app.longPollClient.events.collect { ev ->
            if (ev !is LongPollEvent.Typing) return@collect
            if (ev.peerId != peerId) return@collect
            // Don't show typing for yourself (shouldn't happen, but just in case).
            if (ev.userId == myUserId) return@collect
            typingUsers = typingUsers + (ev.userId to System.currentTimeMillis())
            AppLog.d("ChatDetailScreen",
                "#TYPING-FIX: typing accepted peer=${ev.peerId} user=${ev.userId} isChat=${ev.isChat}")
        }
    }

    // P0.1: cleanup stale typing entries (older than TYPING_TIMEOUT_MS).
    // #TYPING-FIX (баг залипания): БЫЛО — ключ LaunchedEffect был
    // (typingEnabled, typingUsers.isNotEmpty()), т.е. эффект перезапускался
    // только при переходе пусто↔непусто. Второе typing-событие (VK шлёт их
    // каждые ~4с) обновляло НЕПУСТУЮ карту — ключ не менялся, запущенный
    // таймер не перезапускался, его фильтр работал по устаревшему снапшоту
    // и завершался без записи → новая просрочка оставалась навсегда, индикатор
    // «печатает…» зависал до выхода с экрана. ТЕПЕРЬ — ключ typingUsers: любое
    // изменение карты перезапускает таймер, индикатор гаснет ровно через
    // TYPING_TIMEOUT_MS после последнего события (VK web ~5с).
    LaunchedEffect(typingEnabled, typingUsers) {
        val current = typingUsers
        if (!typingEnabled || current.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(TYPING_TIMEOUT_MS)
        val now = System.currentTimeMillis()
        val fresh = current.filterValues { ts -> now - ts < TYPING_TIMEOUT_MS }
        if (fresh.size != current.size) {
            typingUsers = fresh
            AppLog.d("ChatDetailScreen", "#TYPING-FIX: typing expired, remaining=${fresh.size}")
        }
    }

    CompositionLocalProvider(LocalStickerPhotoScale provides stickerPhotoScale) {
    Scaffold(
        topBar = {
            if (selectionMode) {
                // P2.5: selection-mode TopAppBar — «Выбрано: N» + Forward/Delete/Close.
                TopAppBar(
                    title = { Text("Выбрано: ${selectedIds.size}") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Отменить выбор")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { forwardSelected() },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Forward, contentDescription = "Переслать")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                        }
                    },
                )
            } else {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            // #CHANNEL-INFO-PANEL (2026-10-03): тап по шапке канала
                            // (peerId<0) открывает панель канала. Обычные диалоги
                            // (позитивный peerId) — по-прежнему профиль собеседника.
                            // P0.7 (2026-10-04): isChannelUi (а не isChannel) — чтобы
                            // панель открывалась и для АДМИНА канала (canWrite.allowed=true
                            // → Chat.isChannel=false, но channelGroup != null → isChannelUi=true).
                            // else if (peerId<0) — fallback если channelGroup ещё не
                            // загружена (открываем панель, она подгрузится позже).
                            if (peerId < 0 && isChannelUi) {
                                showChannelInfoPanel = true
                            } else if (peerId in 1..1_999_999_999L) {
                                onUserClick(peerId)
                            } else if (peerId < 0) {
                                // Админ канала до загрузки channelGroup или обычная
                                // группа/chat — открываем панель канала (контент сообщества).
                                showChannelInfoPanel = true
                            }
                        },
                    ) {
                        if (currentPhoto != null) {
                            AsyncImage(
                                model = currentPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        // 32-b: имя + статус в Column — статус («онлайн»/«был(а) …»/
                        // «печатает…») теперь ПОД именем, а не рядом с ним в Row.
                        Column {
                            // Fix #122: muted indicator остаётся В РЯДУ с именем —
                            // перечёркнутый колокольчик, чтобы пользователь сразу
                            // видел что уведомления выключены (как в нативном VK).
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    // weight(fill=false): имя занимает свободную ширину
                                    // строки, но колокольчик не выдавливается за край —
                                    // при длинном имени усекается само имя.
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (muted) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Outlined.NotificationsOff,
                                        contentDescription = "Уведомления выключены",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            // #60: online статус под именем.
                            // P0.1: typing indicator имеет приоритет над online статусом.
                            val la = lastActivity
                            // P0.1: resolve typing user names from chatProfiles (group chat)
                            // or just use peerTitle (DM — only one user can be typing).
                            val typingIds = typingUsers.keys.toList()
                            val typingNames = typingIds.mapNotNull { uid ->
                                val profile = chatProfiles[uid]
                                if (profile == null) {
                                    null
                                } else {
                                    val fullName = profile.fullName
                                    if (fullName.isNotBlank()) fullName else null
                                }
                            }
                            val statusText = when {
                                // #CHANNEL-WALL-MODE (Fix #393): в шапке канала — количество
                                // подписчиков (снапшот 29-a: заголовок + «N подписчиков»);
                                // typing/online для пиров-каналов не приходят.
                                isChannel && channelSubscribers >= 0 -> subscribersLabel(channelSubscribers)
                                // #IM-CHANNEL-FIX (56-b-4): подписчики ещё не загружены или
                                // groupsGetById не удался (channelSubscribers=-1) — вместо
                                // пустого подзаголовка честно показываем род канала
                                // (VK web в этот момент показывает «Загружается...»).
                                isChannel -> "Канал"
                                typingEnabled && typingIds.isNotEmpty() && isGroupChat && typingNames.isNotEmpty() -> {
                                    // Group chat: show up to 2 names, then "+N"
                                    when {
                                        typingNames.size == 1 -> "${typingNames[0]} печатает…"
                                        typingNames.size == 2 -> "${typingNames[0]} и ${typingNames[1]} печатают…"
                                        else -> "${typingNames[0]} и ещё ${typingNames.size - 1} печатают…"
                                    }
                                }
                                typingEnabled && typingIds.isNotEmpty() -> "печатает…"
                                la != null && la.online == 1 -> "онлайн"
                                // 32-b: до 15 минут — относительное «был(а) N мин назад»,
                                // дальше время/дата (формат formatLastSeenExtended ниже).
                                la != null && la.lastSeen > 0 ->
                                    formatLastSeenExtended(la.lastSeen, System.currentTimeMillis() / 1000)
                                else -> ""
                            }
                            if (statusText.isNotBlank()) {
                                Text(
                                    text = statusText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (typingEnabled && typingIds.isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (la != null && la.online == 1) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // #IM-SEARCH (Fix #394): лупа в шапке диалога/канала (снапшот 29-a:
                    // search_outline_24 «Поиск по каналу» / поиск по сообщениям).
                    // Тап: канал → панель «Поиск по постам» (wall.search), диалог →
                    // поиск по сообщениям (локально + messages.search).
                    IconButton(onClick = {
                        if (isChannel) showChannelSearch = true else showSearch = true
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "Поиск")
                    }
                    // #CALLS: кнопка звонка в шапке диалога (data-testid="convo-call-menu-trigger").
                    // #ARCH-CONTAINERS (Этап 1.4): рисуем только при живом CallStarter
                    // (onCallClick != null) — без контейнера звонков кнопки нет.
                    if (peerId in 1..1_999_999_999L && onCallClick != null) {
                        IconButton(onClick = { onCallClick(peerId, currentTitle, currentPhoto) }) {
                            Icon(Icons.Filled.Call, contentDescription = "Позвонить")
                        }
                    }
                    // #59: меню показываем для ВСЕХ диалогов (не только групповых).
                    Box {
                        IconButton(onClick = { showChatMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Управление диалогом")
                        }
                            DropdownMenu(
                                expanded = showChatMenu,
                                onDismissRequest = { showChatMenu = false },
                            ) {
                                // P3.1: информация о чате → ChatInfoScreen (если флаг включён).
                                // #IM-CHANNEL-FIX (56-b-6): для канала СКРЫТ — ChatInfoScreen
                                // построен вокруг участников/ACL бесед, у канала их нет.
                                if (chatInfoEnabled && !isChannel) {
                                    DropdownMenuItem(
                                        text = { Text("Информация о чате") },
                                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                        onClick = {
                                            showChatMenu = false
                                            onInfoClick(peerId)
                                        },
                                    )
                                }
                                if (isGroupChat) {
                                    DropdownMenuItem(
                                        text = { Text("Переименовать") },
                                        onClick = {
                                            showChatMenu = false
                                            renameTitle = currentTitle
                                            showRenameDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Участники") },
                                        onClick = {
                                            showChatMenu = false
                                            loadMembers()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Выйти из чата") },
                                        onClick = { leaveChat() },
                                    )
                                }
                                // #59: общие действия для всех диалогов
                                DropdownMenuItem(
                                    // #IM-SEARCH (Fix #394): для канала — панель «Поиск
                                    // по постам» (wall.search), для диалога — поиск по
                                    // сообщениям.
                                    text = { Text(if (isChannel) "Поиск по постам" else "Поиск по сообщениям") },
                                    onClick = {
                                        showChatMenu = false
                                        if (isChannel) showChannelSearch = true else showSearch = true
                                    },
                                )
                                // #IM-CHANNEL-FIX (56-b-6): «Очистить историю» СКРЫТА для канала —
                                // у канала нет messages-истории (контент = wall.get),
                                // messages.deleteConversation для канала удаляет сам диалог,
                                // что в VK web делается отдельным осознанным «Покинуть».
                                if (!isChannel) {
                                DropdownMenuItem(
                                    text = { Text("Очистить историю") },
                                    onClick = {
                                        showChatMenu = false
                                        scope.launch {
                                            try {
                                                app.apiClient.messagesDeleteConversation(peerId)
                                                messages = emptyList()
                                                AppLog.i("ChatDetailScreen", "Conversation cleared")
                                            } catch (e: Exception) {
                                                AppLog.e("ChatDetailScreen", "deleteConversation failed", e)
                                            }
                                        }
                                    },
                                )
                                }
                                // P3.2: mute/unmute chat (если флаг включён).
                                // #IM-CHANNEL-FIX (56-b-5): для канала — пункт управления
                                // уведомлениями канала (allow/denyMessagesFromGroup, см.
                                // toggleChannelNotifications), для обычных диалогов — прежний
                                // toggleMute (push_settings) без изменений.
                                if (muteEnabled || isChannel) {
                                    if (isChannel) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (channelNotifEnabled) "Выключить уведомления"
                                                    else "Включить уведомления"
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (channelNotifEnabled) Icons.Outlined.NotificationsOff
                                                    else Icons.Outlined.Notifications,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                showChatMenu = false
                                                toggleChannelNotifications()
                                            },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(if (muted) "Включить уведомления" else "Заглушить") },
                                            leadingIcon = {
                                                Icon(
                                                    if (muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                showChatMenu = false
                                                toggleMute()
                                            },
                                        )
                                    }
                                }
                                // W30-1 #IM-UNREAD-MENU: «Отметить непрочитанным/
                                // прочитанным» — после mute, паритет VK web (в меню
                                // чата веба пункт есть, у нас отсутствовал). Тот же
                                // API-флоу, что в long-press меню списка диалогов.
                                // #IM-CHANNEL-FIX (56-b-6): для канала СКРЫТ — у канала нет
                                // messages-непрочитанного в нашем UI (контент = wall.get,
                                // бейдж чистится при открытии — 56-b-1).
                                if (!isChannel) {
                                DropdownMenuItem(
                                    text = { Text(if (hasUnreadMark) "Отметить прочитанным" else "Отметить непрочитанным") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.MarkChatUnread,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        showChatMenu = false
                                        toggleUnreadMark()
                                    },
                                )
                                }
                                // P0.3: stub «Закрепить сообщение» удалён — теперь pin
                                // доступен через long-press на конкретном сообщении
                                // (context menu → «Закрепить» / «Открепить»).
                            }
                        }
                    },
            )
            }  // P2.5: closes else (not selection mode)
        },
        bottomBar = {
            if (selectionMode) {
                // P2.5: hint bar в режиме выбора (вместо панели ввода).
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Выбрано: ${selectedIds.size} — тапайте сообщения",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            } else if (isChannel || channelWriteDenied) {
                // P3.4: channel mode — скрываем composer, показываем footer с mute/leave.
                // Канал = broadcast-сообщество, пользователь только читает (не пишет).
                // #IM-CHANNEL-FIX (56-b-3): read-only футер рисуется ВСЕГДА когда
                // peerId<0 и canWrite известен и запрещён (channelWriteDenied) —
                // НЕ зависимо от тумблера channelModeEnabled (раньше при выключенном
                // тумблере рисовался обычный композер → messages.send давал err 901).
                // Админ канала (allowed=true) получает обычный композер — семантика
                // не тронута (channelWriteDenied=false при allowed=true).
                // #IM-CHANNEL-FIX (56-b-5): тумблер mute заменён на уведомления канала
                // (allow/denyMessagesFromGroup) — см. toggleChannelNotifications.
                ChannelFooterBar(
                    notificationsEnabled = channelNotifEnabled,
                    onToggleNotifications = { toggleChannelNotifications() },
                    onLeave = { leaveChannel() },
                )
            } else {
            Column {
                // #59: панель ответа (reply) — показывает текст сообщения на которое отвечаем.
                if (replyingTo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ответ на сообщение",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = replyingTo?.text?.take(60) ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = { replyingTo = null }) {
                            Text("Отмена")
                        }
                    }
                }
                // Sprint 3: панель редактирования.
                if (editingMsgId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Редактирование",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        androidx.compose.material3.TextButton(onClick = { cancelEdit() }) {
                            Text("Отмена", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    // #IM-EDIT-ATTACH (HAR CHAT-EDIT-ATTACH-HAR-2026-10-01):
                    // чипы документов редактируемого сообщения с кнопкой × —
                    // удаление файла из editingAttachments (при сохранении
                    // messages.edit перепишет attachment заново → файл снимется).
                    val docAtts = editingAttachments.orEmpty().filter { it.type == "doc" && it.doc != null }
                    if (docAtts.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 12.dp, top = 0.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            docAtts.forEach { att ->
                                val d = att.doc ?: return@forEach
                                val label = if (d.title.isNullOrBlank()) d.ext.ifBlank { "Файл" } else d.title
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(
                                        onClick = {
                                            editingAttachments = editingAttachments.orEmpty().filterNot {
                                                it.type == "doc" && it.doc?.id == d.id && it.doc?.ownerId == d.ownerId
                                            }
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Удалить файл",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { editingAttachments = emptyList() }) {
                                Text("Удалить файлы", color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    } else if (editingAttachments != null && editingAttachments.orEmpty().isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Все вложения будут удалены",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    // #IM-EDIT-ATTACH: прикрепить НОВЫЙ файл к редактируемому сообщению.
                    // Видна всегда в панели редактирования (и когда docAtts пуст). Клик —
                    // выбор одного файла → uploadDocForMessage → токен в editingAddedAttach
                    // (уйдёт на сервер в editMessage). editingAddingFile — spinner во время загрузки.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { editFilePickerLauncher.launch("*/*") },
                            enabled = !editingAddingFile,
                        ) {
                            if (editingAddingFile) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Загрузка…")
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = "Прикрепить файл",
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Прикрепить файл", color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
                // Fix #200/#201: единая панель эмодзи+стикеров — ВНИЗУ, над
                // панелью ввода (внутри Column bottomBar). Раньше рисовалась
                // overlay сверху и перекрывала сообщения. Показывается только
                // в обычном режиме (не во время записи/просмотра голосового).
                if (showEmojiStickerPanel && !isRecording && pendingVoiceFile == null) {
                    EmojiStickerPanel(
                        tab = emojiStickerTab,
                        onTabChange = { newTab ->
                            emojiStickerTab = newTab
                            // Auto-load стикеров при переключении на таб стикеров.
                            if (newTab == 1 && stickerPacks.isEmpty()) loadStickers()
                        },
                        emojis = EMOJI_LIST,
                        emojiOnClick = { emoji -> inputText += emoji },
                        stickerPacks = stickerPacks,
                        stickerLoading = stickerLoading,
                        selectedStickerPack = selectedStickerPack,
                        onSelectStickerPack = { selectedStickerPack = it },
                        onStickerClick = { sendSticker(it) },
                        onDismiss = { showEmojiStickerPanel = false },
                        onStickerDisplayed = { stickerId, imageUrl ->
                            // Fix #222: предзагрузка стикера в офлайн-кеш при отображении в пикере.
                            // Срабатывает когда стикер становится видимым в сетке. Если стикер уже
                            // в кеше — preloadStickerToCache быстро вернёт true (только exists() проверка).
                            // Если miss — скачивает в фоне. Не блокирует UI.
                            scope.launch {
                                app.apiClient.preloadStickerToCache(stickerId, imageUrl)
                            }
                        },
                    )
                }
                // Sprint 3 #12 → Fix #115: запись голосового — VK Web-style панель.
                // 2 режима: isRecording (активная запись) и pendingVoiceFile!=null (review).
                if (isRecording) {
                    VoiceRecordingToolbar(
                        seconds = recordingSeconds,
                        amplitudes = voiceAmplitudes,
                        onCancel = { cancelVoiceRecording() },
                        onStop = { stopVoiceRecordingForReview() },
                        onSend = { stopAndSendVoice() },
                    )
                } else if (pendingVoiceFile != null) {
                    VoiceReviewToolbar(
                        seconds = pendingVoiceDuration,
                        amplitudes = voiceAmplitudes,
                        isPlaying = isPreviewingVoice,
                        progress = previewProgress,
                        onCancel = { cancelVoiceRecording() },
                        onResume = { startVoiceRecording() },
                        onPlay = { togglePreviewPendingVoice() },
                        onSend = { sendPendingVoice() },
                    )
                } else {
                    // Fix #234 (multi-photo preview): бар миниатюр выбранных фото.
                    // Появляется анимированно над полем ввода (выше pendingFiles bar).
                    // Каждая миниатюра кликабельна → полноэкранный просмотр через
                    // PhotoViewer (с pinch-zoom и swipe между фото).
                    if (pendingPhotos.isNotEmpty()) {
                        PendingPhotosBar(
                            photos = pendingPhotos,
                            onRemove = { idx ->
                                pendingPhotos = pendingPhotos.toMutableList().also { it.removeAt(idx) }
                            },
                            onPreview = { idx -> previewPhotoIndex = idx },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                    // Fix #235 (multi-file): бар выбранных файлов над полем ввода.
                    // Горизонтальный список: иконка + имя + размер + × для каждого.
                    // Появляется анимированно когда pendingFiles не пуст.
                    if (pendingFiles.isNotEmpty()) {
                        PendingFilesBar(
                            files = pendingFiles,
                            onRemove = { idx ->
                                val removed = pendingFiles[idx]
                                pendingFiles = pendingFiles.toMutableList().also { it.removeAt(idx) }
                                removed.file.delete()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = UiScale.scaled(8.dp), vertical = UiScale.scaled(6.dp))
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Fix #200: поле ввода расширено — убраны 3 отдельные
                        // кнопки (📎😀😐) слева от поля, заменены единым триггером
                        // ➕ справа. Поле теперь занимает больше места (weight 1f
                        // без конкуренции с 3 IconButton слева).
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (editingMsgId != null) "Редактирование…" else "Сообщение…"
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = { doSend() },
                            ),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Fix #200: единый триггер ➕ справа от поля — выпадающее
                        // меню ВВЕРХ с 3 пунктами: Смайлы / Стикеры / Прикрепить.
                        // При выборе Смайлы/Стикеры клавиатура скрывается
                        // (keyboardController?.hide()), панель показывается ВМЕСТО
                        // неё — снизу экрана, над панелью ввода. Закрыть панель —
                        // кнопка «Закрыть» внутри самой панели.
                        Box {
                            IconButton(
                                onClick = { showTriggerMenu = !showTriggerMenu },
                                enabled = !sending && !uploading,
                            ) {
                                if (uploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.Add, contentDescription = "Смайлы, стикеры, вложения")
                                }
                            }
                            DropdownMenu(
                                expanded = showTriggerMenu,
                                onDismissRequest = { showTriggerMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("😀  Смайлы и стикеры") },
                                    onClick = {
                                        keyboardController?.hide()
                                        // Fix #201: единая панель с табами. Если
                                        // панель уже открыта — toggle (закрыть).
                                        // Иначе открыть на текущей вкладке
                                        // (emojiStickerTab сохраняется между сессиями).
                                        if (showEmojiStickerPanel) {
                                            showEmojiStickerPanel = false
                                        } else {
                                            // Auto-load стикеров, если открываем на табе стикеров.
                                            if (emojiStickerTab == 1 && stickerPacks.isEmpty()) {
                                                loadStickers()
                                            }
                                            showEmojiStickerPanel = true
                                        }
                                        showTriggerMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("📎  Прикрепить файл") },
                                    onClick = {
                                        showAttachMenu = true
                                        showTriggerMenu = false
                                    },
                                )
                            }
                            // Единое меню «Прикрепить» — открывается из пункта
                            // «Прикрепить файл» в триггер-меню ➕. Тот же компонент,
                            // что в комментариях к постам и при создании поста.
                            // Подарки доступны только в личных диалогах (peerId > 0
                            // и < 2_000_000_000L) — gifts.send не работает в чатах.
                            UnifiedAttachMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false },
                                onPhoto = {
                                    val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    if (multiFileEnabled) {
                                        multiPhotoPickerLauncher.launch(req)
                                    } else {
                                        photoPickerLauncher.launch(req)
                                    }
                                },
                                onCamera = {
                                    val permission = Manifest.permission.CAMERA
                                    if (ContextCompat.checkSelfPermission(ctx, permission) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        val uri = createCameraImageUri(ctx)
                                        if (uri != null) {
                                            cameraImageUri = uri
                                            // Fix #132: сохраняем параметры чата для
                                            // восстановления после process death.
                                            onCameraLaunch(peerId, currentTitle, currentPhoto)
                                            cameraLauncher.launch(uri)
                                        }
                                    } else {
                                        cameraPermissionLauncher.launch(permission)
                                    }
                                },
                                onVideo = {
                                    attachmentPickerTab = AttachmentPickerTab.Video
                                    showAttachmentPicker = true
                                },
                                onAudio = {
                                    attachmentPickerTab = AttachmentPickerTab.Music
                                    showAttachmentPicker = true
                                },
                                onGift = {
                                    attachmentPickerTab = AttachmentPickerTab.Gifts
                                    showAttachmentPicker = true
                                },
                                onFile = {
                                    multiFilePickerLauncher.launch(arrayOf("*/*"))
                                },
                                // #ATTACH-UNIFY (P1.5): фото/файл «Из VK» — табы пикера;
                                // выбор уходит сообщением через sendWithAttachment.
                                showPhotoFromVk = true,
                                onPhotoFromVk = {
                                    attachmentPickerTab = AttachmentPickerTab.Photos
                                    showAttachmentPicker = true
                                },
                                showFileFromVk = true,
                                onFileFromVk = {
                                    attachmentPickerTab = AttachmentPickerTab.Docs
                                    showAttachmentPicker = true
                                },
                                // Подарки только в личных диалогах.
                                showGift = peerId > 0 && peerId < 2_000_000_000L,
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // #VM-3 волна 3: ПРЯМАЯ кнопка видео-сообщения («кружок»)
                        // рядом с микрофоном — открывает рекордер VideoMessageCreateScreen.
                        // Видима всегда (не только в режиме mic), следует флагу enabled
                        // кнопок ввода (same как mic: !sending && !uploading).
                        IconButton(
                            onClick = { onVideoMessage(peerId) },
                            enabled = !sending && !uploading,
                        ) {
                            Icon(
                                Icons.Outlined.VideoFile,
                                contentDescription = "Видеосообщение (кружок)",
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // Sprint 3 #12: mic ↔ send toggle.
                        // P3.6: dual button state machine (opt-in) — EDIT/LOADING/LIMIT/MIC/SUBMIT.
                        if (dualButtonEnabled) {
                            val sendState = when {
                                editingMsgId != null -> SendButtonState.EDIT
                                sending || uploading -> SendButtonState.LOADING
                                inputText.length > MSG_TEXT_LIMIT -> SendButtonState.LIMIT
                                inputText.isNotBlank() || pendingFiles.isNotEmpty() || pendingPhotos.isNotEmpty() -> SendButtonState.SUBMIT
                                else -> SendButtonState.MIC
                            }
                            when (sendState) {
                                SendButtonState.EDIT -> IconButton(onClick = { doSend() }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Сохранить")
                                }
                                SendButtonState.LOADING -> IconButton(onClick = {}, enabled = false) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                                    )
                                }
                                SendButtonState.LIMIT -> IconButton(onClick = {}) {
                                    Icon(
                                        Icons.Outlined.Warning,
                                        contentDescription = "Сообщение слишком длинное (${MSG_TEXT_LIMIT} символов макс.)",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                                SendButtonState.SUBMIT -> IconButton(
                                    onClick = { doSend() },
                                    enabled = !sending,
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Отправить")
                                }
                                SendButtonState.MIC -> IconButton(
                                    onClick = {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            ctx, Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) startVoiceRecording()
                                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    enabled = !sending && !uploading,
                                ) {
                                    Icon(Icons.Filled.Mic, contentDescription = "Голосовое сообщение",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else if (inputText.isNotBlank() || editingMsgId != null || pendingFiles.isNotEmpty() || pendingPhotos.isNotEmpty()) {
                            IconButton(
                                onClick = { doSend() },
                                enabled = !sending && !uploading,
                            ) {
                                if (sending || uploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Отправить")
                                }
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        ctx, Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) startVoiceRecording()
                                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                enabled = !sending && !uploading,
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = "Голосовое сообщение",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            }  // P2.5: closes else (not selection mode)
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (isChannel && !channelWallFallback) {
                // ═══ #CHANNEL-WALL-MODE (Fix #393): контент канала ═══════════
                // Посты сообщества (wall.get) карточками WallPostCard — ТОТ ЖЕ
                // компонент, что на стене профиля (переиспользование, не копипаста):
                // лайк (likes.add/delete), комментарий/тап → PostDetailScreen,
                // «Поделиться» → ShareSheet. Композер скрыт (bottomBar →
                // ChannelFooterBar с тумблером уведомлений).
                // Fix #394 #CHANNEL-WALL-FALLBACK: при недоступной стене
                // (channelWallFallback=true) контент рендерит стандартный
                // messages-режим ниже (история диалога канала).
                Box(modifier = Modifier.fillMaxSize()) {
                    val chErr = channelPostsError
                    when {
                        channelPostsLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        chErr != null && channelPosts.isEmpty() -> {
                            // Честная ошибка wall.get + «Повторить».
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = chErr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = { loadChannelPosts(initial = true) }) {
                                    Text("Повторить")
                                }
                            }
                        }
                        channelPosts.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "В канале пока нет записей",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> {
                            // Баннер «Закреплённый пост» над лентой (снапшот 29-a:
                            // MultiplePins-баннер). #CHANNELS-UI2 (Task 2): если у
                            // постов канала >1 закреп (isPinned==1) — карусель с
                            // точками/стрелками и переключением по индексу; при одном
                            // закрепе — прежний одиночный баннер. Сам пост остаётся
                            // в ленте, клик по баннеру скроллит к нему.
                            val pinnedPosts = channelPosts.filter { it.isPinned == 1 }
                            val effectivePinIndex =
                                channelPinIndex.coerceIn(0, (pinnedPosts.size - 1).coerceAtLeast(0))
                            val pinnedPost = pinnedPosts.getOrNull(effectivePinIndex)
                            LazyColumn(
                                state = channelListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                            ) {
                                if (pinnedPosts.isNotEmpty() && pinnedPost != null) {
                                    item(key = "channel_pinned_banner") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { scrollChannelToPost(pinnedPost) },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                ),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.PushPin,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = if (pinnedPosts.size > 1)
                                                                "Закреплённый пост (${effectivePinIndex + 1}/${pinnedPosts.size})"
                                                            else "Закреплённый пост",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Medium,
                                                        )
                                                        if (pinnedPost.text.isNotBlank()) {
                                                            Text(
                                                                text = pinnedPost.text.take(80),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            if (pinnedPosts.size > 1) {
                                                // Стрелки переключения между закрепами —
                                                // БЕЗ вложенного клика (вне clickable Card).
                                                IconButton(
                                                    onClick = {
                                                        channelPinIndex =
                                                            (effectivePinIndex - 1 + pinnedPosts.size) % pinnedPosts.size
                                                    },
                                                    enabled = effectivePinIndex > 0,
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Предыдущий закреп",
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        channelPinIndex =
                                                            (effectivePinIndex + 1) % pinnedPosts.size
                                                    },
                                                    enabled = effectivePinIndex < pinnedPosts.size - 1,
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Outlined.Forward,
                                                        contentDescription = "Следующий закреп",
                                                    )
                                                }
                                            }
                                        }
                                        // Точки-индикаторы текущего закрепа (при >1).
                                        if (pinnedPosts.size > 1) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 2.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                pinnedPosts.indices.forEach { idx ->
                                                    val active = idx == effectivePinIndex
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(horizontal = 3.dp)
                                                            .size(if (active) 8.dp else 6.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (active) MaterialTheme.colorScheme.primary
                                                                else MaterialTheme.colorScheme.outlineVariant,
                                                            ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                items(channelPosts, key = { "post_${it.ownerId}_${it.id}" }) { post ->
                                    val likeKey = "${post.ownerId}_${post.id}"
                                    WallPostCard(
                                        post = post,
                                        authorName = currentTitle,
                                        authorPhoto = currentPhoto,
                                        onVideoClick = onVideoClick,
                                        onPostClick = { openChannelPost(it) },
                                        onPhotoClick = { urls, idx -> photoViewerState = urls to idx },
                                        onRepostClick = { channelSharePost = it },
                                        onCommentClick = { openChannelPost(it) },
                                        likesState = channelLikeStates,
                                        likePending = channelLikeInFlight.containsKey(likeKey),
                                        onLikeToggle = { toggleChannelPostLike(it) },
                                    )
                                    // #CHANNELS-UI2 (Task 1): в wall-режиме, как и в
                                    // history-режиме, под карточкой поста выводим строку
                                    // счётчиков просмотров/комментариев и донат-плашку
                                    // (ChannelPostWallInfoRow для модели Post). Рисуется
                                    // только когда у поста есть поля (счётчики/донат);
                                    // обычные посты не затрагиваются (в wall-режиме канала
                                    // все посты — канальные). Полностью аддитивно.
                                    ChannelPostWallInfoRow(
                                        post = post,
                                        onPaywallClick = {
                                            val slugId = -peerId
                                            val payUrl = "https://vk.ru/club$slugId" +
                                                "?source=donut_post_channel&w=donut_payment-${post.id}"
                                            onUrlClick(payUrl)
                                        },
                                        // #CHANNELS-COMMENTS: открыть комментарии поста канала.
                                        // target = peerId(канал) + post.id (cmid поста).
                                        onCommentsClick = { channelCommentsTarget = peerId to post.id },
                                    )
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(1.dp)
                                            .padding(horizontal = 16.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    )
                                }
                                item(key = "channel_pagination_footer") {
                                    when {
                                        channelPostsLoadingMore -> {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                        channelPostsEnd -> {
                                            Text(
                                                text = "Это все записи",
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                            // Пагинация постов канала — догрузка при приближении к концу
                            // (паттерн CommunityScreen.loadMoreWall / Fix #85).
                            LaunchedEffect(channelListState, channelPosts.size) {
                                snapshotFlow {
                                    val info = channelListState.layoutInfo
                                    // NULL-ЯВНО: последний видимый item — через
                                    // явную проверку пустоты (без ?. и ?:).
                                    val lastVisible = if (info.visibleItemsInfo.isEmpty()) 0
                                    else info.visibleItemsInfo.last().index
                                    val total = info.totalItemsCount
                                    total > 0 && lastVisible >= total - 3
                                }
                                    .distinctUntilChanged()
                                    .filter { it }
                                    .collect { loadMoreChannelPosts() }
                            }
                        }
                    }
                }
                // #IM-SEARCH (Fix #394): панель «Поиск по постам» — scrim+slide
                // справа (образец FeedRightPanel), серверный поиск wall.search.
                ChannelSearchPanel(
                    visible = showChannelSearch,
                    ownerId = peerId,
                    channelTitle = currentTitle,
                    channelPhoto = currentPhoto,
                    onDismiss = { showChannelSearch = false },
                    onPostOpen = { p ->
                        showChannelSearch = false
                        scrollChannelToPost(p)
                    },
                    onVideoClick = onVideoClick,
                    onPhotoClick = { urls, idx -> photoViewerState = urls to idx },
                    onSharePost = { channelSharePost = it },
                    onCommentClick = { openChannelPost(it) },
                    likesState = channelLikeStates,
                    likeInFlight = channelLikeInFlight,
                    onLikeToggle = { toggleChannelPostLike(it) },
                )
            } else if (loading && messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val err = errorText
                if (err != null && messages.isEmpty()) {
                    // #IM-EMPTY-HONEST: честный текст причины + «Повторить» —
                    // перезапуск первичной загрузки без выхода из чата. Раньше
                    // «Нет сообщений»/ошибка висели мёртвым текстом.
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { historyReload++ }, enabled = !loading) {
                                Text("Повторить")
                            }
                        }
                    }
                } else {
                // P0.3: pinned message bar (только для group chats + если включён флаг).
                val pinned = pinnedMessage
                if (pinBarEnabled && isGroupChat && pinned != null) {
                    PinnedMessageBar(
                        message = pinned,
                        onUnpin = {
                            scope.launch {
                                val ok = app.apiClient.messagesUnpin(peerId)
                                if (ok) {
                                    pinnedMessage = null
                                    AppLog.i("ChatDetailScreen", "Message unpinned: peer=$peerId")
                                } else {
                                    AppLog.w("ChatDetailScreen", "messagesUnpin failed")
                                }
                            }
                        },
                        onClick = {
                            // Скролл к pinned сообщению в списке (по id).
                            val idx = messages.indexOfFirst { it.id == pinned.id }
                            if (idx >= 0) {
                                scope.launch { listState.animateScrollToItem(idx) }
                            }
                        },
                    )
                }
                // P1.1: pre-compute chat list items (messages + date separators + unread divider).
                val chatListItems by remember(
                    messages, groupingEnabled, dateSeparatorsEnabled, unreadDividerEnabled,
                    channelHistoryMode,
                ) {
                    derivedStateOf {
                        // #CHANNEL-POST-CARD: в канальном history-режиме лента — это
                        // посты-карточки, а не чат, поэтому дата-разделители и
                        // unread-divider не показываем (обычные чаты не затрагиваются:
                        // channelHistoryMode=false у них).
                        val chan = channelHistoryMode
                        buildChatListItems(
                            messages = messages,
                            groupingEnabled = groupingEnabled,
                            dateSeparatorsEnabled = dateSeparatorsEnabled && !chan,
                            unreadDividerEnabled = unreadDividerEnabled && !chan,
                        )
                    }
                }
                // P1.1: показываем scroll-to-bottom FAB если пользователь проскроллил вверх.
                // reverseLayout=true: firstVisibleItemIndex=0 → пользователь внизу (новые).
                val showScrollFab by remember {
                    derivedStateOf {
                        scrollFabEnabled && messages.isNotEmpty() &&
                            (listState.firstVisibleItemIndex > 0 ||
                                listState.firstVisibleItemScrollOffset > 200)
                    }
                }
                // P1.1: количество непрочитанных входящих — для badge на FAB.
                val unreadCount by remember(messages) {
                    derivedStateOf {
                        messages.count { !it.isOut && it.readState == 0 }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    reverseLayout = true,
                ) {
                    // reverseLayout=true: индекс 0 (новое) — внизу, конец (старое) — наверху.
                    // P1.1: единый items() с ChatListItem sealed class — поддерживает
                    // date separators, unread divider и messages в одном списке.
                    items(chatListItems, key = { item ->
                        when (item) {
                            is ChatListItem.MessageRow -> "msg_${item.message.id}"
                            is ChatListItem.DateSeparator -> "date_${item.dayKey}"
                            ChatListItem.UnreadDivider -> "unread_divider"
                        }
                    }) { item ->
                        when (item) {
                            is ChatListItem.DateSeparator -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            ChatListItem.UnreadDivider -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    )
                                    Text(
                                        text = "Непрочитанные",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    )
                                }
                            }
                            is ChatListItem.MessageRow -> {
                                val msg = item.message
                                if (peerId < 0 && channelHistoryMode) {
                                    // #CHANNEL-POST-CARD: канальный пост в history-режиме
                                    // рисуем карточкой поста сообщества VK (как в wall-режиме),
                                    // а не IM-пузырём. Обычные чаты (peerId>0) не затрагиваются
                                    // — они идут ниже через MessageBubble.
                                    val channelPost = messageToChannelPost(msg)
                                    val likeKey = "${channelPost.ownerId}_${channelPost.id}"
                                    Column {
                                        WallPostCard(
                                            post = channelPost,
                                            authorName = currentTitle,
                                            authorPhoto = currentPhoto,
                                            onVideoClick = onVideoClick,
                                            onPostClick = { openChannelPost(it) },
                                            onPhotoClick = { urls, idx -> photoViewerState = urls to idx },
                                            onRepostClick = { channelSharePost = it },
                                            onCommentClick = {
                                                channelCommentsTarget =
                                                    channelPost.ownerId to channelPost.id
                                            },
                                            likesState = channelLikeStates,
                                            likePending = channelLikeInFlight.containsKey(likeKey),
                                            onLikeToggle = { toggleChannelPostLike(it) },
                                        )
                                        ChannelPostWallInfoRow(
                                            post = channelPost,
                                            onPaywallClick = {
                                                val slugId = if (peerId < 0) -peerId else peerId
                                                val payUrl = "https://vk.ru/club$slugId" +
                                                    "?source=donut_post_channel&w=donut_payment-${channelPost.id}"
                                                onUrlClick(payUrl)
                                            },
                                            onCommentsClick = {
                                                channelCommentsTarget =
                                                    channelPost.ownerId to channelPost.id
                                            },
                                        )
                                        // #CHANNEL-POST-CARD: реакционные чипы VK (эмодзи + число)
                                        // из message.reactions.items — логика та же, что в
                                        // ChannelPostInfoRow. Непустые → рисуем под карточкой.
                                        val chipItems = (msg.reactions?.items ?: emptyList())
                                            .filter { it.count > 0 }
                                            .sortedByDescending { it.count }
                                            .take(6)
                                        if (chipItems.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                for (chip in chipItems) {
                                                    val active = msg.reactions?.userReactionActive(chip.id) == true
                                                    val pillBg = if (active) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                                    }
                                                    val pillFg = if (active) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = pillBg,
                                                        contentColor = pillFg,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable { reactToMessage(msg.id, chip.id) },
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        ) {
                                                            Text(text = reactionEmoji(chip.id), fontSize = 12.sp)
                                                            Text(
                                                                text = channelCounterString(chip.count),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontSize = 10.sp,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                MessageBubble(
                                    message = msg,
                                    profiles = chatProfiles,
                                    voicePlaybackController = voicePlaybackController,
                                    // P0.25 #VOICE-ASR-FETCH: передаём callback для запроса transcript.
                                    onFetchVoiceTranscripts = { scope.launch { fetchVoiceTranscripts() } },
                                    onLongPress = { contextMsgId = msg.id },
                                    // #REACTION-WEB-MAP: double-click = ❤️ = id 1
                                    // (web-карта; раньше id 2 был ❤️, фактически ставился 🔥).
                                    onDoubleClick = { reactToMessage(msg.id, 1) },
                                    onReact = { rid -> reactToMessage(msg.id, rid) },
                                    onCopy = { contextMsgId = null },
                                    onEdit = {
                                        contextMsgId = null
                                        editingMsgId = msg.id
                                        inputText = msg.text
                                        // #IM-EDIT-ATTACH (HAR CHAT-EDIT-ATTACH-HAR-2026-10-01):
                                        // запоминаем вложения, чтобы правка текста их не сняла
                                        // (messages.edit переписывает attachment целиком).
                                        editingAttachments = msg.attachments
                                    },
                                    onDelete = {
                                        contextMsgId = null
                                        deleteMessage(msg)
                                    },
                                    onForward = {
                                        contextMsgId = null
                                        forwardMsgIds = listOf(msg.id)
                                        // Fix #295: cmid — именно он переносит
                                        // вложения/файлы при пересылке.
                                        val cmid = msg.conversationMessageId
                                        if (cmid == null) {
                                            Toast.makeText(ctx, "Это сообщение нельзя переслать (нет cmid)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            forwardMsgCmids = listOf(cmid)
                                            showForwardDialog = true
                                        }
                                    },
                                    // #IM-IMPORTANT (волна 32): «Отметить как важное» —
                                    // messages.markAsImportant. Показ — только НЕ канал
                                    // (peerId > 0: лички и групповые чаты; peer<0 —
                                    // каналы/сообщества).
                                    onMarkImportant = {
                                        contextMsgId = null
                                        markMessageImportant(msg)
                                    },
                                    canMarkImportant = peerId > 0,
                                    // #FAVE-MSG: «В избранное» — пересылка в self-chat
                                    // одним тапом (peer_id = myUserId), без ForwardDialog.
                                    onSaveToSelf = {
                                        contextMsgId = null
                                        val cmid = msg.conversationMessageId
                                        if (cmid == null) {
                                            Toast.makeText(ctx, "Это сообщение нельзя сохранить (нет cmid)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            scope.launch {
                                                try {
                                                    val target = app.exchangeAuthRepository.userId()
                                                    val msgId = app.apiClient.messagesForward(target, peerId, listOf(cmid))
                                                    val toast = if (msgId > 0) "Сохранено в избранное" else "Не удалось сохранить (код $msgId)"
                                                    Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    AppLog.e("ChatDetailScreen", "saveToSelf error", e)
                                                    Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    onReply = {
                                        contextMsgId = null
                                        replyingTo = msg
                                    },
                                    onMarkAnswered = {
                                        contextMsgId = null
                                        scope.launch {
                                            app.apiClient.messagesMarkAsAnswered(peerId, listOf(msg.id))
                                        }
                                    },
                                    onRestore = {
                                        contextMsgId = null
                                        scope.launch {
                                            app.apiClient.messagesRestore(msg.id)
                                            val list = app.apiClient.messagesGetHistory(peerId, count = pageSize)
                                            messages = list.distinctBy { it.id }
                                        }
                                    },
                                    onPin = if (isGroupChat) {
                                        {
                                            contextMsgId = null
                                            scope.launch {
                                                val ok = app.apiClient.messagesPin(peerId, msg.id)
                                                if (ok) {
                                                    pinnedMessage = msg
                                                }
                                            }
                                        }
                                    } else null,
                                    isPinned = pinnedMessage?.id == msg.id,
                                    showReactionPicker = showReactionPicker == msg.id,
                                    onShowReactionPicker = { showReactionPicker = msg.id },
                                    onHideReactionPicker = { showReactionPicker = null },
                                    showContextMenu = contextMsgId == msg.id,
                                    onDismissContextMenu = { contextMsgId = null },
                                    // P2.5: multi-select.
                                    multiSelectAvailable = multiSelectEnabled,
                                    selectionMode = selectionMode,
                                    selected = selectedIds.contains(msg.id),
                                    onToggleSelection = { toggleSelection(msg.id) },
                                    onSelect = {
                                        contextMsgId = null
                                        enterSelection(msg.id)
                                    },
                                    onWallClick = onPostClick,
                                    onVideoClick = onVideoClick,
                                    onAudioClick = onAudioClick,
                                    onPollVote = onPollVote,
                                    // P5.1: ссылки + фото-просмотрщик.
                                    onUrlClick = onUrlClick,
                                    // #CHANNELS-COMMENTS: открыть комментарии канального
                                    // поста (только каналы: peerId<0; cmid = msg.id в
                                    // history-режиме channels.getHistory).
                                    onCommentsClick = { channelCommentsTarget = msg.peerId to msg.id },
                                    onPhotoClick = { urls, idx -> photoViewerState = urls to idx },
                                    isGrouped = item.isGrouped,
                                    // P1.2: swipe-to-reply (отключён в режиме выбора).
                                    swipeEnabled = swipeReplyEnabled && !selectionMode,
                                    // P2.6: read receipts.
                                    showReadReceipts = readReceiptsEnabled,
                                    // P3.7: bubble-less дизайн (flat layout).
                                    bubbleless = bubblelessEnabled,
                                    // Fix #206: клик по плашке ответа + подсветка цели.
                                    onReplyBadgeClick = onReplyBadgeClick,
                                    highlighted = highlightedMsgId == msg.id,
                                )
                                }
                            }
                        }
                    }
                    // Footer-элементы для пагинации (наверху списка при reverseLayout).
                    if (loadingOlder) {
                        item(key = "footer_loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                    if (endReached && messages.isNotEmpty() && !loadingOlder) {
                        item(key = "footer_end") {
                            Text(
                                text = "Начало переписки",
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    // #CHANNEL-FILTERS (Task #CHANNELS-UI): ряд фильтров ленты
                    // канала — ВСЁ/Донат/Фото/Видео/Аудио/Документы. Рисуется
                    // только в канальном history-режиме (channelHistoryMode)
                    // самым верхним item'ом (reverseLayout → высокий индекс =
                    // визуально сверху, над старыми постами). Обычные чаты и
                    // wall-каналы не затрагиваются.
                    if (channelHistoryMode) {
                        item(key = "channel_feed_filters") {
                            ChannelFeedFilterRow(
                                current = channelFeedFilter,
                                onSelect = { applyChannelFeedFilter(it) },
                            )
                        }
                    }
                }
                // P1.1: scroll-to-bottom FAB — появляется при прокрутке вверх.
                // Badge показывает количество непрочитанных входящих.
                if (showScrollFab) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge {
                                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "К новым сообщениям")
                        }
                    }
                }
            }
        }
    }
    }  // closes else (loading/error/lazy)

    // Sprint 3 #14: Rename dialog для группового чата.
    if (showRenameDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Переименовать чат") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { renameChat(renameTitle.trim()) }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    // Sprint 3 #14: Members dialog.
    if (showMembersDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMembersDialog = false },
            title = { Text("Участники (${chatMembers.size})") },
            text = {
                if (loadingMembers) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(chatMembers, key = { it.memberId }) { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (member.photo100 != null) {
                                        AsyncImage(
                                            model = member.photo100,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${member.firstName} ${member.lastName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        if (member.isOwner) {
                                            Text("Создатель",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary)
                                        } else if (member.isAdmin) {
                                            Text("Админ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    if (!member.isOwner && isGroupChat) {
                                        IconButton(onClick = { kickMember(member.memberId) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Исключить",
                                                tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMembersDialog = false }) {
                    Text("Закрыть")
                }
            },
        )
    }

    // Fix #200: StickerPicker/EmojiPicker панели перенесены в bottomBar
    // (Column над панелью ввода) — раньше они рисовались как overlay сверху
    // и перекрывали сообщения. Теперь они снизу, над полем ввода.

    // #60 + #IM-SEARCH (Fix #394): поиск по сообщениям (лупа в шапке диалога).
    // Результаты: (1) ЛОКАЛЬНАЯ фильтрация уже загруженной истории (живой фильтр
    // по message.text); (2) при пустом локальном результате — серверный
    // messages.search (API есть в VKApiClient, peer_id поддерживается) с
    // дебаунсом 600мс. Тап по результату: сообщение в загруженной истории →
    // скролл + подсветка; вне загруженного диапазона → существующий
    // preview-диалог с догрузкой истории («Показать в чате», механизм Fix #206).
    if (showSearch) {
        val q = searchQuery.trim()
        // Локальная выдача по загруженной истории (newest-first, как в чате).
        val localResults = if (q.isBlank()) {
            emptyList()
        } else {
            messages.filter { it.text.contains(q, ignoreCase = true) }
        }
        // Дебаунс-автопоиск: локально пусто → серверный messages.search.
        // Очистка searchResults на каждый ввод — старая выдача не должна
        // показываться под новым запросом.
        LaunchedEffect(searchQuery) {
            if (q.isBlank()) {
                searchResults = emptyList()
                return@LaunchedEffect
            }
            searchResults = emptyList()
            kotlinx.coroutines.delay(600)
            if (localResults.isEmpty() && !searching) performSearch()
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Поиск по сообщениям") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск по сообщениям") },
                        singleLine = true,
                        trailingIcon = {
                            if (searching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        localResults.isNotEmpty() -> {
                            Text(
                                text = "В загруженных сообщениях (${localResults.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            LazyColumn(
                                modifier = Modifier.height(300.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(localResults) { m ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable {
                                                // Тап НЕ заглушка: скролл к сообщению
                                                // по cmid-индексу в списке чата.
                                                showSearch = false
                                                scrollToLoadedMessage(m.id)
                                            }
                                            .padding(8.dp),
                                    ) {
                                        Text(
                                            text = m.text.take(100),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.getDefault())
                                                .format(java.util.Date(m.date * 1000)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                        searchResults.isNotEmpty() -> {
                            Text(
                                text = "Найдено на сервере (${searchResults.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            LazyColumn(
                                modifier = Modifier.height(300.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(searchResults) { result ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable {
                                                showSearch = false
                                                val scrolled = scrollToLoadedMessage(result.messageId)
                                                if (!scrolled) {
                                                    // Сообщение вне загруженной истории —
                                                    // preview-диалог с догрузкой («Показать
                                                    // в чате», механизм Fix #206).
                                                    replyPreviewMsg = Message(
                                                        id = result.messageId,
                                                        peerId = peerId,
                                                        fromId = result.fromId,
                                                        date = result.date,
                                                        text = result.text,
                                                    )
                                                }
                                            }
                                            .padding(8.dp),
                                    ) {
                                        Text(
                                            text = result.text.take(100),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.getDefault())
                                                .format(java.util.Date(result.date * 1000)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                        searching -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                        q.isNotBlank() -> {
                            Text(
                                text = "Ничего не найдено",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            Text(
                                text = "Введите текст для поиска",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { performSearch() }) {
                    Text("Найти на сервере")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSearch = false }) {
                    Text("Закрыть")
                }
            },
        )
    }

    // ForwardDialog.
    if (showForwardDialog) {
        ForwardDialog(
            currentPeerId = peerId,
            sourcePeerId = peerId,
            cmids = forwardMsgCmids,
            onDismiss = {
                showForwardDialog = false
                forwardMsgIds = emptyList()
                forwardMsgCmids = emptyList()
                if (selectionMode) exitSelection()
            },
            onForward = { _ ->
                // Fix #295: ForwardDialog уже выполнил API-вызов (с sourcePeerId +
                // cmids) и показал Toast. Раньше здесь был ВТОРОЙ вызов
                // forwardMessages() → дублирующая пересылка. Теперь только
                // закрываем диалог и выходим из режима выбора.
                showForwardDialog = false
                forwardMsgIds = emptyList()
                forwardMsgCmids = emptyList()
                if (selectionMode) exitSelection()
            },
        )
    }

    // P5.3: AttachmentPickerSheet — выбор музыки/видео/подарков из библиотеки VK.
    // #ATTACH-UNIFY (P1.5): + табы «Фото»/«Документы» — существующий объект VK
    // уходит сообщением через sendWithAttachment(peerId, attachment) (messages.send
    // с attachment-строкой из библиотеки, upload не нужен).
    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentPicker = false },
            initialTab = attachmentPickerTab,
            showPhotoTab = true,
            showDocsTab = true,
            onPickPhotoAttachment = { att, _ ->  // _ = thumb (#COMPOSER-ATTACH-PREVIEW): превью тут не нужно, только attachment-строка
                scope.launch {
                    uploading = true
                    try {
                        val mid = app.apiClient.sendWithAttachment(peerId, att)
                        if (mid > 0) reloadMessages() else {
                            AppLog.w("ChatDetailScreen", "sendWithAttachment(photo) returned $mid")
                        }
                    } catch (e: Exception) {
                        AppLog.e("ChatDetailScreen", "send photo-from-vk error", e)
                    } finally {
                        uploading = false
                    }
                }
            },
            onPickDocAttachment = { att, _ ->
                scope.launch {
                    uploading = true
                    try {
                        val mid = app.apiClient.sendWithAttachment(peerId, att)
                        if (mid > 0) reloadMessages() else {
                            AppLog.w("ChatDetailScreen", "sendWithAttachment(doc) returned $mid")
                        }
                    } catch (e: Exception) {
                        AppLog.e("ChatDetailScreen", "send doc-from-vk error", e)
                    } finally {
                        uploading = false
                    }
                }
            },
            onPickAudio = { track ->
                scope.launch {
                    uploading = true
                    try {
                        val mid = app.apiClient.sendAudioToChat(
                            peerId, track.ownerId, track.id, track.accessKey,
                        )
                        if (mid > 0) reloadMessages() else {
                            AppLog.w("ChatDetailScreen",
                                "sendAudioToChat returned $mid for ${track.ownerId}_${track.id}")
                        }
                    } catch (e: Exception) {
                        AppLog.e("ChatDetailScreen", "send audio error", e)
                    } finally {
                        uploading = false
                    }
                }
            },
            onPickVideo = { video ->
                scope.launch {
                    uploading = true
                    try {
                        val mid = app.apiClient.sendVideoToChat(peerId, video)
                        if (mid > 0) reloadMessages() else {
                            AppLog.w("ChatDetailScreen",
                                "sendVideoToChat returned $mid for ${video.ownerId}_${video.id}")
                        }
                    } catch (e: Exception) {
                        AppLog.e("ChatDetailScreen", "send video error", e)
                    } finally {
                        uploading = false
                    }
                }
            },
            onPickGift = { gift ->
                // Подарки отправляются через gifts.send(user_id, gift_id).
                // Для диалогов peer_id = user_id. Для групповых чатов подарки
                // не поддерживаются VK API — покажем предупреждение.
                scope.launch {
                    uploading = true
                    try {
                        if (peerId > 0 && peerId < 2_000_000_000L) {
                            val ok = app.apiClient.giftsSend(peerId, gift.id)
                            if (ok > 0) {
                                AppLog.i("ChatDetailScreen",
                                    "gift sent: giftId=${gift.id} to userId=$peerId")
                            } else {
                                AppLog.w("ChatDetailScreen",
                                    "giftsSend returned $ok for giftId=${gift.id}")
                            }
                        } else {
                            AppLog.w("ChatDetailScreen",
                                "gifts not supported for peerId=$peerId (groups/chats)")
                        }
                    } catch (e: Exception) {
                        AppLog.e("ChatDetailScreen", "send gift error", e)
                    } finally {
                        uploading = false
                    }
                }
            },
        )
    }

    // P2.5: bulk delete confirmation.
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить сообщения?") },
            text = {
                Text(
                    "Выбрано: ${selectedIds.size}. " +
                        "Сообщения будут удалены для всех участников.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    deleteSelected()
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    // Fix #206: preview-диалог для reply, когда исходное сообщение не в загруженной
    // истории. Показывает автора + текст/вложения replyMessage. Кнопка «показать в
    // чате» → догрузка старой истории вверх до нахождения cmid, потом скролл+подсветка.
    val preview = replyPreviewMsg
    if (preview != null) {
        val previewAuthor = chatProfiles[preview.fromId]
        val previewAuthorName = previewAuthor?.let {
            "${it.firstName} ${it.lastName}".trim().ifBlank { null }
        } ?: "Сообщение"
        val previewBody = preview.text.ifBlank {
            preview.attachments?.firstOrNull()?.let { att ->
                when {
                    att.type == "sticker" -> "Стикер"
                    att.type == "photo" -> "Фото"
                    att.type == "video" -> "Видео"
                    att.type == "audio" -> "Аудиозапись"
                    att.type == "audio_message" -> "Голосовое сообщение"
                    att.type == "doc" -> "Документ"
                    att.type == "wall" -> "Запись на стене"
                    att.type == "poll" -> "Опрос"
                    att.type == "gift" -> "Подарок"
                    else -> "Вложение"
                }
            } ?: if (preview.hasForwarded) "Пересланное сообщение" else "Пустое сообщение"
        }
        AlertDialog(
            onDismissRequest = {
                if (!loadingReplyTarget) replyPreviewMsg = null
            },
            title = { Text(previewAuthorName) },
            text = {
                Column {
                    Text(
                        text = previewBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (loadingReplyTarget) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Загрузка истории…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { loadUntilFoundAndScroll(preview) },
                    enabled = !loadingReplyTarget,
                ) {
                    Text("Показать в чате")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { replyPreviewMsg = null },
                    enabled = !loadingReplyTarget,
                ) {
                    Text("Закрыть")
                }
            },
        )
    }

    // Fix #137 / Fix #218 (P1.3): inline "session expired" dialog — shown when
    // access_token is invalidated (VK API error 5/1117) AND suppressAuthRelaunch
    // is active (т.е. AuthActivity не запустится автоматически). Replaces the old
    // behavior where AuthActivity would launch over the chat ("выбивает из диалога").
    // The user can re-login ("Перезайти") or stay in the chat ("Остаться") — the
    // conversation remains visible either way.
    //
    // Срабатывает из двух мест:
    // 1. Photo upload fails (Fix #137) — tickBefore != tokenInvalidationTicks.value
    // 2. Любой API error 5/1117 пока suppressAuthRelaunchUntilMs активен (Fix #218)
    // Fix #234 (multi-photo preview): полноэкранный просмотрщик фото.
    // Открывается по тапу на миниатюру в PendingPhotosBar.
    // PhotoViewer (из ui/components) — pinch-zoom + swipe между фото.
    val pvi = previewPhotoIndex
    if (pvi != null && pendingPhotos.isNotEmpty()) {
        PhotoViewer(
            photos = pendingPhotos.map { it.uri.toString() },
            initial = pvi.coerceIn(0, pendingPhotos.lastIndex),
            onDismiss = { previewPhotoIndex = null },
        )
    }
    if (showSessionExpiredDialog) {
        AlertDialog(
            onDismissRequest = { showSessionExpiredDialog = false },
            title = { Text("Сессия истекла") },
            text = {
                Text(
                    "Access_token больше не валиден (VK API error 5/1117). " +
                        "Перезайдите, чтобы продолжить. Диалог останется открытым.\n\n" +
                        "Если доступен silent refresh (remixsid/trusted_hash), " +
                        "попробуйте сначала «Остаться» — фоновое обновление может " +
                        "восстановить токен автоматически.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSessionExpiredDialog = false
                    // Manually trigger AuthActivity — same logic as MainActivity's
                    // LaunchedEffect(tokenInvalidationTick). Use silent mode if a
                    // remixsid cookie is available (Fix #107) so the user doesn't
                    // see the login form when silent re-login is possible.
                    val hasRemixsid = !app.exchangeAuthRepository.remixsid().isNullOrBlank()
                    val intent = Intent(ctx, re.pinok.auth.AuthActivity::class.java).apply {
                        if (hasRemixsid) {
                            putExtra(re.pinok.auth.AuthActivity.EXTRA_SILENT_MODE, true)
                        }
                    }
                    ctx.startActivity(intent)
                }) { Text("Перезайти") }
            },
            dismissButton = {
                TextButton(onClick = { showSessionExpiredDialog = false }) {
                    Text("Остаться")
                }
            },
        )
    }

    // P5.1: полноэкранный просмотрщик фото (zoom/pan/swipe) — переиспользуется
    // из 6 других экранов (FeedScreen, PhotosScreen, …). Состояние photoViewerState.
    photoViewerState?.let { (urls, idx) ->
        PhotoViewer(
            photos = urls,
            initial = idx,
            onDismiss = { photoViewerState = null },
        )
    }

    // #CHANNELS-COMMENTS (Task #CHANNELS-COMMENTS): панель комментариев
    // канального поста. Открывается из ChannelPostInfoRow / ChannelPostWallInfoRow
    // (только канальные посты, peerId<0). Самодостаточный диалог: грузит
    // channels.getComments, создаёт/редактирует комментарии. Аддитивно — обычные
    // диалоги не затрагиваются.
    channelCommentsTarget?.let { (targetPeer, targetCmid) ->
        ChannelCommentsDialog(
            apiClient = app.apiClient,
            channelId = targetPeer,
            cmid = targetCmid,
            myUserId = myUserId,
            onDismiss = { channelCommentsTarget = null },
        )
    }

    // #CHANNEL-INFO-PANEL (2026-10-03): панель канала — аватар, название,
    // подписчики, кнопки «Открыть сообщество»/«Написать», ссылка на канал и
    // разделы Фото/Видео/Музыка/Файлы. Открывается тапом по шапке канала
    // (peerId<0 && isChannel). ModalBottomSheet — рендерится поверх Scaffold
    // в корне CompositionLocalProvider, поэтому работает в любом суб-режиме
    // канала (wall/история). Аддитивно: обычные диалоги не затрагиваются,
    // разделы показывают Toast (групповых маршрутов Screen.* нет).
    if (peerId < 0 && isChannelUi && showChannelInfoPanel) {
        ModalBottomSheet(
            onDismissRequest = { showChannelInfoPanel = false },
        ) {
            ChannelInfoPanelContent(
                group = channelGroup,
                peerId = peerId,
                channelTitle = currentTitle,
                channelPhoto = currentPhoto,
                subscribers = channelSubscribers,
                onOpenCommunity = {
                    val url = channelCommunityUrl(channelGroup, peerId)
                    showChannelInfoPanel = false
                    onUrlClick(url)
                },
                onWrite = {
                    showChannelInfoPanel = false
                    val canMsg = channelGroup?.canMessage == 1
                    Toast.makeText(
                        ctx,
                        if (canMsg) "Вы уже в диалоге канала" else "Сообщения канала недоступны",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onCopyLink = { url ->
                    try {
                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("channel_link", url))
                        Toast.makeText(ctx, "Ссылка на канал скопирована", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        AppLog.e("ChatDetailScreen", "copy channel link failed: ${e.message}")
                    }
                },
                onSectionClick = { section ->
                    when (section) {
                        // #CHANNEL-PHOTOS: «Фотографии» — фото канала
                        // (photosGet albumId="wall", ownerId=-abs(peerId)); P0.6
                        // заменил photosPhotoFeedList (личная лента) на photosGet
                        // (фото сообщества) — см. ChannelPhotoFeedDialog.
                        "Фотографии" -> {
                            showChannelInfoPanel = false
                            showChannelPhotos = true
                        }
                        // #CHANNEL-SECTIONS (2026-10-04): «Видео/Музыка/Файлы» —
                        // собственные панели канала (videoGet/audioGetWithCount /
                        // docsGet с ownerId=-abs(peerId)).
                        "Видео" -> {
                            showChannelInfoPanel = false
                            showChannelVideo = true
                        }
                        "Музыка" -> {
                            showChannelInfoPanel = false
                            showChannelAudio = true
                        }
                        "Файлы" -> {
                            showChannelInfoPanel = false
                            showChannelFiles = true
                        }
                        // #CHANNEL-CLIPS (P0.5, 2026-10-04): «Клипы» —
                        // shortVideo.getOwnerVideos(ownerId=-abs(peerId)),
                        // вертикальные 9:16 постеры в ChannelClipsDialog.
                        "Клипы" -> {
                            showChannelInfoPanel = false
                            showChannelClips = true
                        }
                        // P0.9 (2026-10-04): defensive-else — если в
                        // ChannelInfoSectionsGrid добавят новую кнопку без
                        // обновления when, тап не молчит, а логируется
                        // (раньше был silent no-op, см. Task 2 аудит).
                        else -> {
                            AppLog.w("ChannelPanel", "Unknown section: $section")
                        }
                    }
                },
            )
        }
    }
    // #CHANNEL-PHOTOS (P0.6, 2026-10-04): панель «Фотографии» канала — фото
    // сообщества (photosGet albumId="wall", ownerId=-abs(peerId)) с
    // offset-пагинацией; P0.6 заменил photosPhotoFeedList (личная фотолента
    // photos.photoFeedGet, работала только для owner_id>0) на photosGet
    // (фото сообщества, как в CommunityScreen.kt:363). Тап по фото → PhotoViewer.
    // Аддитивно: только канальный режим.
    if (peerId < 0 && isChannelUi && showChannelPhotos) {
        ChannelPhotoFeedDialog(
            apiClient = app.apiClient,
            ownerId = -abs(peerId),
            onDismiss = { showChannelPhotos = false },
            onPhotoClick = { urls, idx ->
                // Закрываем панель и открываем существующий полноэкранный
                // просмотрщик (photoViewerState) — верхнеуровневый PhotoViewer.
                showChannelPhotos = false
                photoViewerState = urls to idx
            },
        )
    }
    // #CHANNEL-SECTIONS (2026-10-04): панель «Видео» канала (videoGet с
    // ownerId=-abs(peerId)); тап по видео → onVideoClick (воспроизведение).
    if (peerId < 0 && isChannelUi && showChannelVideo) {
        ChannelVideoDialog(
            apiClient = app.apiClient,
            ownerId = -abs(peerId),
            onDismiss = { showChannelVideo = false },
            onVideoClick = onVideoClick,
        )
    }
    // #CHANNEL-SECTIONS (2026-10-04): панель «Музыка» канала (audioGetWithCount
    // с ownerId=-abs(peerId)); рендер AudioAttachmentList (сам запускает плеер).
    if (peerId < 0 && isChannelUi && showChannelAudio) {
        ChannelAudioDialog(
            apiClient = app.apiClient,
            ownerId = -abs(peerId),
            onDismiss = { showChannelAudio = false },
        )
    }
    // #CHANNEL-SECTIONS (2026-10-04): панель «Файлы» канала (docsGet с
    // ownerId=-abs(peerId)); тап/кнопка скачивает документ (DownloadManager).
    if (peerId < 0 && isChannelUi && showChannelFiles) {
        ChannelFilesDialog(
            apiClient = app.apiClient,
            ownerId = -abs(peerId),
            onDismiss = { showChannelFiles = false },
        )
    }
    // #CHANNEL-CLIPS (P0.5, 2026-10-04): панель «Клипы» канала
    // (shortVideo.getOwnerVideos, ownerId=-abs(peerId)); вертикальные 9:16
    // постеры в ChannelClipsDialog. Тап → onVideoClick (воспроизведение клипа
    // через VideoHolder.open, как в CommunityScreen). Аддитивно: только канал.
    if (peerId < 0 && isChannelUi && showChannelClips) {
        ChannelClipsDialog(
            apiClient = app.apiClient,
            ownerId = -abs(peerId),
            onDismiss = { showChannelClips = false },
            onVideoClick = onVideoClick,
        )
    }
    }  // Fix #228: closes CompositionLocalProvider(LocalStickerPhotoScale)
}

// ---- Context menu state holder ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    profiles: Map<Long, UserProfile> = emptyMap(),
    groups: Map<Long, re.pinok.api.VKApiClient.GroupInfo> = emptyMap(),
    onLongPress: () -> Unit,
    onDoubleClick: () -> Unit,
    onReact: (Int) -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    // #IM-IMPORTANT (волна 32): «Отметить как важное» — messages.markAsImportant.
    // Показ пункта решает ХОСТ (canMarkImportant: peerId > 0 — не канал).
    onMarkImportant: () -> Unit = {},
    canMarkImportant: Boolean = false,
    // Fix #120: единый voice-плеер на чат — только одно голосовое играет за раз.
    voicePlaybackController: VoicePlaybackController,
    // P0.25 #VOICE-ASR-FETCH: запрос transcript (messages.getById) при тапе ASR кнопки.
    onFetchVoiceTranscripts: () -> Unit = {},
    // #59: ответ на сообщение
    onReply: () -> Unit = {},
    // #60: markAsAnswered + restore
    onMarkAnswered: () -> Unit = {},
    onRestore: () -> Unit = {},
    // #FAVE-MSG: сохранить в избранное (переслать себе).
    onSaveToSelf: () -> Unit = {},
    // P0.3: pin/unpin message (group chats only).
    onPin: (() -> Unit)? = null,
    isPinned: Boolean = false,
    // Fix #99: клик по wall-вложению → открыть пост.
    onWallClick: (re.pinok.data.model.Post) -> Unit = {},
    // P2.1: клик по video-вложению → открыть в VideoPlayer.
    onVideoClick: (Video) -> Unit = {},
    // P2.2: клик по audio-вложению → запустить в PlayerConnection.
    onAudioClick: (re.pinok.data.model.Track) -> Unit = {},
    // P2.3: голосование в опросе (pollId, ownerId есть в Poll, передаём answerIds).
    onPollVote: (re.pinok.data.model.Poll, List<Long>) -> Unit = { _, _ -> },
    // P5.1: клик по ссылке в тексте/вложении → открыть во внутреннем или внешнем браузере.
    onUrlClick: (String) -> Unit = {},
    // #CHANNELS-COMMENTS: клик по «N комментариев» канального поста →
    // открыть панель комментариев. Только канальные посты (peerId<0).
    onCommentsClick: () -> Unit = {},
    // P5.1: клик по фото-вложению → полноэкранный просмотр (PhotoViewer).
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    showReactionPicker: Boolean,
    onShowReactionPicker: () -> Unit,
    onHideReactionPicker: () -> Unit,
    showContextMenu: Boolean,
    onDismissContextMenu: () -> Unit,
    // P1.3: message grouping — текущее сообщение группируется с предыдущим
    // (более новым, индекс i-1 при reverseLayout). Если true:
    //  - скрыть аватарку + имя отправителя (они показаны у первого в группе)
    //  - сделать top corner radius плоским (визуальное объединение)
    //  - уменьшить top padding Column (сообщения ближе друг к другу)
    isGrouped: Boolean = false,
    // P1.2: reply via swipe — если true, свайп в сторону ответа активирует onReply.
    swipeEnabled: Boolean = false,
    // P2.6: read receipts (✓/✓✓) — показывать статус прочтения для исходящих.
    showReadReceipts: Boolean = false,
    // P2.5: multi-select — feature flag + current selection state + callbacks.
    multiSelectAvailable: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onSelect: () -> Unit = {},
    // P3.7: bubble-less режим — flat layout без Card/bubble (как m.vk.ru).
    bubbleless: Boolean = false,
    // Fix #206: клик по плашке ответа → скролл к исходному сообщению (+подсветка),
    // либо открытие preview-диалога если цель вне загруженной истории.
    onReplyBadgeClick: (Message) -> Unit = {},
    // Fix #206: подсветка целевого сообщения (статичная, без анимации).
    highlighted: Boolean = false,
) {
    val context = LocalContext.current
    // Fix #224: масштаб скорости анимаций (для swipe-reply spring).
    val animScale = LocalAnimScale.current
    // Fix #244: состояние выбора для вложений внутри bubble (reply badge,
    // photo grid). Отдельные Composable-вложения (Wall/Video/Link/Doc/Audio/
    // Poll/Voice) читают LocalAttachmentSelection сами.
    val sel = LocalAttachmentSelection.current
    val isOut = message.isOut
    val bubbleColor = if (isOut) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    // P3.7: в bubble-less режиме текст всегда onSurface (нет яркого bubble-фона).
    val textColor = if (bubbleless) {
        MaterialTheme.colorScheme.onSurface
    } else if (isOut) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val time = message.date.toMsgTime()
    var lastClickTime by remember { mutableStateOf(0L) }

    // P1.2: swipe-to-reply state.
    // Incoming (left-aligned): swipe RIGHT (positive offset) → reply.
    // Outgoing (right-aligned): swipe LEFT (negative offset) → reply.
    // Threshold: 200px → trigger onReply. Spring animation returns to 0.
    val swipeOffsetX = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    var replyTriggered by remember { mutableStateOf(false) }
    val swipeThreshold = 200f  // px — порог активации reply

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (swipeEnabled && !message.isAction) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { replyTriggered = false },
                            onDragEnd = {
                                swipeScope.launch {
                                    swipeOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = springScaled<Float>(
                                            scale = animScale,
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                }
                            },
                            onDragCancel = {
                                swipeScope.launch {
                                    swipeOffsetX.animateTo(0f, springScaled<Float>(animScale))
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = swipeOffsetX.value + dragAmount
                                // Ограничиваем направление: входящие — только вправо (positive),
                                // исходящие — только влево (negative).
                                val allowed = if (isOut) {
                                    minOf(newOffset, 0f)
                                } else {
                                    maxOf(newOffset, 0f)
                                }
                                swipeScope.launch { swipeOffsetX.snapTo(allowed) }
                                // Триггер reply при превышении порога (один раз за жест).
                                if (!replyTriggered && abs(allowed) > swipeThreshold) {
                                    replyTriggered = true
                                    onReply()
                                    // Немедленно возвращаем на место после триггера.
                                    swipeScope.launch {
                                        swipeOffsetX.animateTo(0f, springScaled<Float>(
                                            scale = animScale,
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                        ))
                                    }
                                }
                            },
                        )
                    }
                } else Modifier
            )
    ) {
        // P1.2: иконка Reply позади bubble — видна при смещении.
        val showReplyIcon = abs(swipeOffsetX.value) > 20f
        if (showReplyIcon) {
            Box(
                modifier = Modifier
                    .align(if (isOut) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = "Ответить",
                    tint = MaterialTheme.colorScheme.primary.copy(
                        alpha = minOf(abs(swipeOffsetX.value) / swipeThreshold, 1f),
                    ),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Fix #244: передаём состояние выбора во вложенные Composable (фото,
        // видео, голосовые, ссылки, и т.д.) через CompositionLocal. Вложения
        // читают LocalAttachmentSelection и в selection mode вызывают
        // onToggleSelection вместо открытия контента, а long-press —
        // onLongPress (прямой вход в selection или context menu).
        val attachmentSelection = AttachmentSelectionState(
            selectionMode = selectionMode,
            onToggleSelection = onToggleSelection,
            onLongPress = {
                // Тот же long-press handler что у bubble Box ниже.
                // #IM-MENU-LONGPRESS-FIX (волна 32): вне selection-режима
                // long-press ВСЕГДА открывает контекстное меню — перехват
                // multi-select удалён (pref msgMultiSelect управляет только
                // пунктом «Выбрать» в меню, а не грабит long-press).
                // В selection-режиме long-press ничего не делает (VK web:
                // тап = toggle выделения).
                if (!selectionMode) onLongPress()
            },
        )
        CompositionLocalProvider(LocalAttachmentSelection provides attachmentSelection) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffsetX.value.toInt(), 0) }
                // Fix #244: расширяем touch target на всю строку (включая
                // пустые поля вне bubble). В selection mode тап по пустому
                // полю = toggle выделения. Long-press по пустому полю =
                // вход в selection / context menu. Тап по bubble по-прежнему
                // обрабатывается bubble combinedClickable ниже (double-click).
                .combinedClickable(
                    onClick = {
                        if (selectionMode) onToggleSelection()
                        // В обычном режиме — ничего (пусть bubble обработает
                        // тап для double-click detect, если тап по bubble).
                    },
                    onLongClick = {
                        // #IM-MENU-LONGPRESS-FIX: меню всегда вне selection-режима
                        // (см. комментарий у attachmentSelection выше).
                        if (!selectionMode) onLongPress()
                    },
                ),
            horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
        ) {
        // #74: аватарка отправителя (только для входящих сообщений).
        // P1.3: скрываем если isGrouped=true (показываем только у первого в группе).
        if (!isOut && message.fromId != 0L && !isGrouped) {
            val senderProfile = profiles[message.fromId]
            val senderPhoto = senderProfile?.photo100 ?: senderProfile?.photo200
            val senderName = senderProfile?.let { "${it.firstName} ${it.lastName}".trim() } ?: ""
            Row(
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (senderPhoto != null) {
                        AsyncImage(
                            model = senderPhoto,
                            contentDescription = senderName,
                            modifier = Modifier.size(24.dp).clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = senderName.take(1).ifBlank { "?" }.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (senderName.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // P1.3: если isGrouped — прижимаем сообщение к предыдущему (часть группы).
        // ВАЖНО: Compose Modifier.padding() бросает IllegalArgumentException при
        // отрицательных значениях («Padding must be non-negative»). Используем
        // offset(y = -2.dp) — он разрешает отрицательные и визуально даёт тот же
        // эффект «сдвига вверх». Audit #S5-fix3.
        val groupTopOffset = if (isGrouped) (-2).dp else 0.dp
        // P1.3 + P2.5: форма bubble (плоские top corners при группировке).
        val bubbleShape = RoundedCornerShape(
            topStart = if (isGrouped) 4.dp else 16.dp,
            topEnd = if (isGrouped) 4.dp else 16.dp,
            bottomEnd = if (isOut) 4.dp else 16.dp,
            bottomStart = if (isOut) 16.dp else 4.dp,
        )
        // P3.7: bubble-less режим — flat layout (без Card/bubble), как m.vk.ru.
        // В bubble-less: нет clip/rounded, нет background для incoming, subtle tint для outgoing.
        val bubblelessShape = RoundedCornerShape(4.dp)
        val bubblelessBg = if (isOut) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
        val msgShape = if (bubbleless) bubblelessShape else bubbleShape
        val msgBg = if (bubbleless) bubblelessBg else bubbleColor
        val msgMaxWidth = if (bubbleless) 320.dp else 280.dp
        val msgHPadding = if (bubbleless) 10.dp else 12.dp
        val msgVPadding = if (bubbleless) 6.dp else 8.dp
        Box(
            modifier = Modifier
                .widthIn(max = msgMaxWidth)
                .offset(y = groupTopOffset)
                .combinedClickable(
                    onClick = {
                        // P2.5: в режиме выбора — toggle выделения вместо double-click.
                        if (selectionMode) {
                            onToggleSelection()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime < 300L) {
                                onDoubleClick()
                            }
                            lastClickTime = now
                        }
                    },
                    onLongClick = {
                        // #IM-MENU-LONGPRESS-FIX (волна 32): раньше здесь был перехват
                        // `if (multiSelectAvailable && !selectionMode) onSelect()` —
                        // при включённом pref msgMultiSelect меню становилось
                        // недостижимым ВООБЩЕ (root-cause «пропало меню», волна 32).
                        // Теперь вне selection-режима long-press всегда открывает
                        // контекстное меню; вход в selection — пункт «Выбрать».
                        if (!selectionMode) onLongPress()
                    },
                )
                .clip(msgShape)
                .background(msgBg)
                .then(
                    // Fix #206: подсветка целевого сообщения после скролла к нему
                    // (клик по плашке ответа). БЕЗ анимации — просто оверлей-фон.
                    if (highlighted) Modifier.background(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f),
                    ) else Modifier
                )
                .then(
                    // P2.5: подсветка выбранного сообщения — primary border.
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, msgShape)
                    else Modifier
                )
                .padding(horizontal = msgHPadding, vertical = msgVPadding),
        ) {
            // P2.5: selection indicator (checkmark circle) в верхнем углу bubble.
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Column {
                // #60: Reply — ответ на сообщение (reply_message)
                // Fix #206: плашка кликабельна → скролл к исходному сообщению
                // (+подсветка), либо preview-диалог если цель вне загруженной истории.
                // #ARCH-CONTAINERS 3.7-1: replyMessage в :core:data — захват ДО проверки.
                val reply = message.replyMessage
                if (message.hasReply && reply != null) {
                    // Имя автора ответа (если есть в загруженных профилях).
                    val replyAuthor = profiles[reply.fromId]
                    val replyAuthorName = replyAuthor?.let {
                        "${it.firstName} ${it.lastName}".trim().ifBlank { null }
                    }
                    // Preview-текст: если text пустой (ответ на стикер/фото/голосовое),
                    // показываем человекочитаемую подпись вложения.
                    val replyPreviewText = reply.text.take(60).ifBlank {
                        reply.attachments?.firstOrNull()?.let { att ->
                            when {
                                att.type == "sticker" -> "Стикер"
                                att.type == "photo" -> "Фото"
                                att.type == "video" -> "Видео"
                                att.type == "audio" -> "Аудиозапись"
                                att.type == "audio_message" -> "Голосовое сообщение"
                                att.type == "doc" -> "Документ"
                                att.type == "wall" -> "Запись на стене"
                                att.type == "poll" -> "Опрос"
                                att.type == "gift" -> "Подарок"
                                else -> "Вложение"
                            }
                        } ?: if (reply.hasForwarded) "Пересланное сообщение" else "Пустое сообщение"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(textColor.copy(alpha = 0.1f))
                            .combinedClickable(
                                onClick = {
                                    // Fix #244: в selection mode — toggle, не открываем цель ответа.
                                    if (sel != null && sel.selectionMode) sel.onToggleSelection()
                                    else onReplyBadgeClick(reply)
                                },
                                onLongClick = { sel?.onLongPress?.invoke() },
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(24.dp)
                                .background(textColor.copy(alpha = 0.5f))
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = replyAuthorName ?: "Ответ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = textColor.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = replyPreviewText,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // #60: Forwarded messages (fwd_messages)
                // Fix #295 (round 2): ранее рендерилось только `fwd.text.take(80)`
                // — без имени отправителя, без вложений. Если пересылаемое
                // сообщение состояло только из фото/файла/голосового — пузырь
                // показывал лишь метку «Пересланное сообщение» и ничего внутри
                // («содержимого в нём не видно»). Теперь рендерим:
                //   1) имя автора (из profiles/groups) + дату,
                //   2) полный текст (до 8 строк),
                //   3) превью всех вложений (фото, видео, голосовые, файлы,
                //      аудио, ссылки, стикеры, посты, опросы).
                if (message.hasForwarded) {
                    message.fwdMessages?.forEach { fwd ->
                        ForwardedMessageBlock(
                            fwd = fwd,
                            profiles = profiles,
                            groups = groups,
                            textColor = textColor,
                            onPhotoClick = onPhotoClick,
                            onWallClick = onWallClick,
                            onUrlClick = onUrlClick,
                            onVideoClick = onVideoClick,
                        )
                    }
                }
                // #60: Action messages (chat_create, chat_title_update, etc.)
                if (message.isAction) {
                    Text(
                        text = message.actionText ?: message.action ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.75f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
                if (message.text.isNotBlank()) {
                    // P5.1: текст с кликабельными ссылками (LinkAnnotation.Clickable).
                    // linkColor: на исходящем цветном bubble — onPrimary (видно),
                    // на входящем/bubbleless — primary (accent).
                    val linkColor = if (isOut && !bubbleless) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Text(
                        text = re.pinok.util.linkifyVkText(message.text, linkColor, onUrlClick),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                    )
                }
                // Fix #99: рендер вложений (wall-посты с миниатюрами).
                val wallAttachments = message.attachments
                    ?.filter { it.type == "wall" && it.wall != null }
                    ?.mapNotNull { it.wall }
                if (wallAttachments != null) {
                    for (wallPost in wallAttachments) {
                        WallAttachmentCard(
                            post = wallPost,
                            profiles = profiles,
                            groups = groups,
                            textColor = textColor,
                            onClick = { onWallClick(wallPost) },
                        )
                    }
                }
                // Sprint 3 #12: голосовые сообщения в пузыре.
                // Fix #114: VK шлёт голосовые двумя способами:
                //   1. type="doc" + doc.audio_msg  (старый формат, редко)
                //   2. type="audio_message" + audio_message  (новый формат, стандарт)
                // Раньше проверялся только (1) → большинство голосовых не рендерилось.
                val voiceAttachments = message.attachments
                    ?.filter {
                        (it.type == "doc" && it.doc?.isVoiceMessage == true) ||
                        (it.type == "audio_message" && it.audioMessage != null)
                    }
                if (voiceAttachments != null) {
                    for (va in voiceAttachments) {
                        // Унифицируем: если type="audio_message", конвертируем AudioMsg
                        // в Doc для VoiceMessageBubble (она принимает Attachment.Doc).
                        // Fix #237: приоритет MP3 над OGG (см. VoiceMessageBubble).
                        val doc = va.doc ?: va.audioMessage?.let { am ->
                            Attachment.Doc(
                                id = 0L,
                                ownerId = 0L,
                                title = "Голосовое сообщение",
                                ext = "mp3",
                                url = am.linkMp3 ?: am.linkOgg ?: "",
                                size = 0L,
                                accessKey = null,
                                audioMsg = am,
                            )
                        }
                        doc?.let {
                            VoiceMessageBubble(
                                doc = it,
                                textColor = textColor,
                                accentColor = if (isOut) textColor else MaterialTheme.colorScheme.primary,
                                messageId = message.id,
                                controller = voicePlaybackController,
                                // P0.25 #VOICE-ASR-FETCH: запрос transcript при тапе ASR кнопки.
                                onRequestTranscript = onFetchVoiceTranscripts,
                            )
                        }
                    }
                }
                // Sprint 3 #13: стикеры (рендерятся вместо bubble-обёртки).
                val stickerAtt = message.attachments
                    ?.firstOrNull { it.type == "sticker" && it.sticker != null }
                if (stickerAtt != null) {
                    val sticker = stickerAtt.sticker
                    if (sticker == null) return@Box
                    // Fix #233 (sticker-enrich): VK message attachments часто НЕ
                    // возвращают animation_url. Если у стикера его нет — смотрим в
                    // глобальный кеш StickerAnimationCache (заполняется при открытии
                    // стикер-панели). Если находим — рендерим анимированную версию.
                    // Fix #234: убран избыточный null-check (предупреждение компилятора
                    // "Condition is always 'true'") — isAnimatedSticker уже включает
                    // enrichedAnimUrl != null. Логику вычисления playable URL вынесли
                    // в let-цепочку: resolvedAnimUrl != null означает, что URL есть и
                    // это НЕ Lottie (.json/.tgs), который Coil не проиграет.
                    val enrichedAnimUrl = sticker.animationUrl
                        ?: StickerAnimationCache.get(sticker.stickerId)
                    val resolvedAnimUrl: String? = enrichedAnimUrl?.let { animUrl ->
                        val lower = animUrl.substringBefore('?').substringAfterLast('/').lowercase()
                        if (lower.endsWith(".json") || lower.endsWith(".tgs")) null else animUrl
                    }
                    val isAnimatedSticker = resolvedAnimUrl != null
                    // Fix #229: renderUrl отдаёт animatedDisplayUrl (GIF/WebP) если есть,
                    // иначе статичный displayUrl. Coil с GifDecoder/AnimatedWebPDecoder
                    // (зарегистрированы в SovaApp.newImageLoader) проиграет анимацию.
                    val url = resolvedAnimUrl ?: sticker.renderUrl
                    if (url != null) {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Стикер",
                                modifier = Modifier.size(120.dp),
                            )
                            // Fix #233 (sticker-badge): ▶ индикатор на анимированных
                            // стикерах в чате — видно что стикер должен анимироваться,
                            // даже если картинка ещё грузится или не загрузилась (сеть).
                            if (isAnimatedSticker) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                            shape = RoundedCornerShape(50),
                                        )
                                        .padding(2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Анимированный стикер",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                // Фото-вложения (полученные от других клиентов).
                // #ARCH-CONTAINERS (Этап 1.5-а): инлайн-рендер фото-сетки перенесён
                // в :feature:photos (PhotosInlineRenderer, rendererKey "photos_inline").
                // Сначала реестр: контейнер есть И rendererKey известен хосту →
                // делегируем (поведение 1:1 с прежним inline-рендером); иначе —
                // осознанная деградация по плану: заглушка «скачать файл»
                // (PhotoAttachmentsStub: тап → хостовый PhotoViewer, сохранение
                // в галерею — ImageSaver'ом в просмотрщике). mime у VK-фото
                // отсутствует — передаём семейство "image/*" и kind="photo".
                val photoAttachments = run {
                    // #NULL-EXPLICIT: явная проверка вместо safe-call (вложения могут
                    // отсутствовать у message — nullable по модели).
                    val atts = message.attachments
                    if (atts != null) atts.filter { it.type == "photo" && it.photo != null } else null
                }
                if (!photoAttachments.isNullOrEmpty()) {
                    // P5.1: список URL для полноэкранного просмотрщика (PhotoViewer).
                    val photoUrls = photoAttachments.mapNotNull { att ->
                        val p = att.photo
                        if (p != null) p.largestUrl else null
                    }
                    val photoRenderer = attachmentRendererFor(kind = "photo", mimeType = "image/*")
                    // #NULL-EXPLICIT: явная проверка вместо safe-call — делегат строится
                    // только когда реестр отдал рендерер; иначе заглушка ниже.
                    val photoDelegate = if (photoRenderer != null) {
                        hostRendererComposable(
                            rendererKey = photoRenderer.rendererKey,
                            // Fix #228: масштаб стикер-фото — настройка хоста
                            // (LocalStickerPhotoScale), контейнеру передаём числом.
                            stickerScalePct = LocalStickerPhotoScale.current.coerceIn(0, 40),
                            onPhotoOpen = { url ->
                                // Fix #244: в selection mode — toggle, не открываем PhotoViewer.
                                if (sel != null && sel.selectionMode) sel.onToggleSelection()
                                else onPhotoClick(photoUrls, photoUrls.indexOf(url).coerceAtLeast(0))
                            },
                            onLongPress = { if (sel != null) sel.onLongPress() },
                        )
                    } else {
                        null
                    }
                    if (photoDelegate != null) {
                        // Данные для рендера — только примитивы (url/флаги/размеры).
                        // «Пустые слоты» (largestUrl == null) передаются как есть —
                        // паритет с прежним рендером (битое вложение держит место
                        // в строке сетки, не отрисовываясь).
                        photoDelegate(
                            photoAttachments.map { att ->
                                val p = att.photo
                                if (p != null) {
                                    val size = p.largestSize
                                    InlinePhotoItem(
                                        url = p.largestUrl,
                                        isStickerLike = p.isStickerLike,
                                        naturalWidthPx = if (size != null) size.width else 0,
                                        naturalHeightPx = if (size != null) size.height else 0,
                                    )
                                } else {
                                    // Пустой слот (фото нет) — держит место в сетке,
                                    // не отрисовываясь (паритет с прежним рендером).
                                    InlinePhotoItem(
                                        url = null,
                                        isStickerLike = false,
                                        naturalWidthPx = 0,
                                        naturalHeightPx = 0,
                                    )
                                }
                            }
                        )
                    } else {
                        PhotoAttachmentsStub(
                            count = photoUrls.size,
                            textColor = textColor,
                            onClick = {
                                if (sel != null && sel.selectionMode) sel.onToggleSelection()
                                else onPhotoClick(photoUrls, 0)
                            },
                            onLongPress = { if (sel != null) sel.onLongPress() },
                        )
                    }
                }
                // Видео-вложения (превью + иконка Play).
                val videoAttachments = message.attachments
                    ?.filter { it.type == "video" && it.video != null }
                if (!videoAttachments.isNullOrEmpty()) {
                    for (va in videoAttachments) {
                        va.video?.let {
                            VideoAttachmentCard(
                                video = it,
                                textColor = textColor,
                                onClick = { onVideoClick(it) },
                            )
                        }
                    }
                }
                // Видео-сообщения («кружок», type="video_message") — бабл по форме.
                val vmAttachments = message.attachments
                    ?.filter { it.type == "video_message" && it.videoMessage != null }
                if (!vmAttachments.isNullOrEmpty()) {
                    for (vmAtt in vmAttachments) {
                        vmAtt.videoMessage?.let { vm ->
                            VideoMessageBubble(
                                videoMessage = vm,
                                isOutgoing = message.isOut,
                            )
                        }
                    }
                }
                // Ссылки.
                val linkAttachments = message.attachments
                    ?.filter { it.type == "link" && it.link != null }
                if (!linkAttachments.isNullOrEmpty()) {
                    for (la in linkAttachments) {
                        la.link?.let { LinkAttachmentCard(link = it, textColor = textColor, onOpen = { onUrlClick(it.url) }) }
                    }
                }
                // Обычные документы (не голосовые).
                val docAttachments = message.attachments
                    // #ARCH-CONTAINERS 3.7-1: doc в :core:data — захват внутри лямбды.
                    ?.filter { att ->
                        val doc = att.doc
                        att.type == "doc" && doc != null && doc.isVoiceMessage.not()
                    }
                if (!docAttachments.isNullOrEmpty()) {
                    for (da in docAttachments) {
                        da.doc?.let { doc ->
                            DocAttachmentCard(doc = doc, textColor = textColor, onOpen = { onUrlClick(doc.url) })
                        }
                    }
                }
                // #59: Аудио-вложения — кликабельная строка с play.
                // #ARCH-CONTAINERS (Этап 1.5-б): инлайн-рендер аудио-вложений
                // перенесён в :feature:audio (AudioInlineRenderer, rendererKey
                // "audio_inline"). Сначала реестр: контейнер есть И rendererKey
                // известен хосту → делегируем (поведение 1:1 с прежним
                // AudioAttachmentRow); иначе — осознанная деградация по плану:
                // заглушка AudioAttachmentsStub (тап → onAudioClick — хостовый
                // плеер PlayerConnection, воспроизведение не зависит от
                // рендерера). mime у VK-аудио отсутствует — передаём семейство
                // "audio/*" и kind="audio" (в коде — ок; в block-комментариях
                // литерал слэш-звёздочки запрещён — вложенный комментарий).
                val audioAttachments = run {
                    // #NULL-EXPLICIT: явная проверка вместо safe-call (вложения могут
                    // отсутствовать у message — nullable по модели).
                    val atts = message.attachments
                    if (atts != null) atts.filter { it.type == "audio" && it.audio != null } else null
                }
                if (!audioAttachments.isNullOrEmpty()) {
                    val audioRenderer = attachmentRendererFor(kind = "audio", mimeType = "audio/*")
                    for (aa in audioAttachments) {
                        // #NULL-EXPLICIT: явная проверка вместо safe-call на вложении.
                        val track = aa.audio
                        if (track != null) {
                            // #NULL-EXPLICIT: явная проверка вместо safe-call — делегат
                            // строится только когда реестр отдал рендерер; иначе
                            // заглушка AudioAttachmentsStub ниже.
                            val audioDelegate = if (audioRenderer != null) {
                                hostAudioRendererComposable(
                                    rendererKey = audioRenderer.rendererKey,
                                    textColor = textColor,
                                    onPlay = {
                                        // Fix #244: в selection mode — toggle, не запускаем плеер.
                                        if (sel != null && sel.selectionMode) sel.onToggleSelection()
                                        else onAudioClick(track)
                                    },
                                    onLongPress = { if (sel != null) sel.onLongPress() },
                                )
                            } else {
                                null
                            }
                            if (audioDelegate != null) {
                                // Данные для рендера — только примитивы
                                // (поля Track распаковывает хост).
                                audioDelegate(track.title, track.artist, track.duration)
                            } else {
                                AudioAttachmentsStub(
                                    title = track.title,
                                    textColor = textColor,
                                    onClick = {
                                        if (sel != null && sel.selectionMode) sel.onToggleSelection()
                                        else onAudioClick(track)
                                    },
                                    onLongPress = { if (sel != null) sel.onLongPress() },
                                )
                            }
                        }
                    }
                }
                // #59: Gift — изображение подарка.
                val giftAttachments = message.attachments
                    ?.filter { it.type == "gift" }
                if (!giftAttachments.isNullOrEmpty()) {
                    for (ga in giftAttachments) {
                        GiftAttachmentCard(textColor = textColor)
                    }
                }
                // #59: Graffiti — анимированное изображение.
                val graffitiAttachments = message.attachments
                    ?.filter { it.type == "graffiti" }
                if (!graffitiAttachments.isNullOrEmpty()) {
                    GraffitiAttachmentCard(textColor = textColor)
                }
                // #59: Poll — карточка опроса.
                val pollAttachments = message.attachments
                    ?.filter { it.type == "poll" && it.poll != null }
                if (!pollAttachments.isNullOrEmpty()) {
                    for (pa in pollAttachments) {
                        pa.poll?.let { poll ->
                            PollAttachmentRow(
                                poll = poll,
                                textColor = textColor,
                                // P2.3: голосование через polls.addVote.
                                onVote = { answerIds -> onPollVote(poll, answerIds) },
                            )
                        }
                    }
                }
                // #59: Map — местоположение.
                val mapAttachments = message.attachments
                    ?.filter { it.type == "map" }
                if (!mapAttachments.isNullOrEmpty()) {
                    MapAttachmentCard(textColor = textColor)
                }
                // #59: Money — перевод.
                val moneyAttachments = message.attachments
                    ?.filter { it.type == "money" }
                if (!moneyAttachments.isNullOrEmpty()) {
                    Text(
                        text = "💰 Перевод денег",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                // #59: Call — информация о звонке.
                val callAttachments = message.attachments
                    ?.filter { it.type == "call" }
                if (!callAttachments.isNullOrEmpty()) {
                    Text(
                        text = "📞 Звонок",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                // #59: Story — превью истории.
                val storyAttachments = message.attachments
                    ?.filter { it.type == "story" }
                if (!storyAttachments.isNullOrEmpty()) {
                    Text(
                        text = "📸 История",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                // #59: Article — статья.
                val articleAttachments = message.attachments
                    ?.filter { it.type == "article" }
                if (!articleAttachments.isNullOrEmpty()) {
                    Text(
                        text = "📄 Статья",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                // #59: Market — товар.
                val marketAttachments = message.attachments
                    ?.filter { it.type == "market" }
                if (!marketAttachments.isNullOrEmpty()) {
                    Text(
                        text = "🛍 Товар",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = time + if (message.isEdited) " · изменено" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                    )
                    // P2.6: read receipts — ✓ (отправлено) / ✓✓ (прочитано) для исходящих.
                    if (isOut && showReadReceipts && !message.isAction) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (message.isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = if (message.isRead) "Прочитано" else "Отправлено",
                            modifier = Modifier.size(14.dp),
                            tint = textColor.copy(alpha = 0.7f),
                        )
                    }
                    // #ARCH-CONTAINERS 3.7-1: reactions в :core:data — захват ДО проверки.
                    val reactions = message.reactions
                    if (reactions != null && reactions.count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reactionEmoji(reactions.userReaction ?: 0),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        } // Fix #244: закрытие CompositionLocalProvider (LocalAttachmentSelection)

        // #CHANNEL-POST-UI: у канального поста с разбивкой реакций по эмодзи
        // (items) чипы рисуются в футере ChannelPostInfoRow — отдельный
        // ReactionBar под bubble НЕ дублируем (иначе реакции покажутся дважды:
        // и чипами, и агрегированным баром). Для обычных сообщений и канальных
        // постов без items (нет разбивки) ReactionBar остаётся (агрегированный
        // счётчик + тап по picker). Чипы в футере сами кликабельны (toggle).
        val channelChipsRender = message.peerId < 0 &&
            (message.reactions?.items?.isNotEmpty() == true)
        if (!channelChipsRender) {
            // ReactionBar — показ реакций под bubble.
            ReactionBar(
                reactions = message.reactions,
                isOut = isOut,
                onReact = onReact,
                onTap = { onShowReactionPicker() },
            )
        }

        // #CHANNEL-DONUT (Task 67 #CHANNELS-API/#CHANNEL-WALL-MODE): счётчики
        // просмотров/комментариев и донат-paywall показываем ТОЛЬКО для постов
        // канала (peerId<0) в fallback-режиме (channels.getHistory →
        // parseChannelHistoryItem заполняет viewsCount/commentsCount/isDonut).
        // Обычные (не канальные) сообщения имеют эти поля null/false — блок
        // для них не рисуется вовсе. #CHANNELS-COMMENTS: блок рисуется для
        // ЛЮБОГО канального поста (кнопка комментариев нужна даже без счётчиков).
        if (message.peerId < 0) {
            ChannelPostInfoRow(
                message = message,
                isOut = isOut,
                textColor = textColor,
                // #CHANNELS-UI (Task #CHANNELS-UI): тап по «Открыть за N ₽/мес» —
                // payment-ссылка VK доната (открытие через onUrlClick: внутренний
                // браузер или внешний — как остальные ссылки в чате). Стена канала
                // тут гарантированно peerId<0 → slug club<id>, пост — message.id.
                onPaywallClick = {
                    val slugId = -message.peerId
                    val payUrl = "https://vk.ru/club$slugId" +
                        "?source=donut_post_channel&w=donut_payment-${message.id}"
                    onUrlClick(payUrl)
                },
                // #CHANNELS-COMMENTS: кнопка комментариев канального поста.
                onCommentsClick = onCommentsClick,
                // #CHANNEL-POST-UI: тап по реакционному чипу футера → toggle
                // (та же отправка messages.sendReaction через onReact).
                onReact = onReact,
            )
        }

        // Контекстное меню (long-press).
        // #IM-MENU-ITEMS (волна 32): структура по снапшоту VK web
        // (Мессенджер_меню_сообщения, §1.1 плана W32): пиалет реакций НАД
        // списком пунктов → Ответить / Переслать / Отметить как важное /
        // Копировать текст / Редактировать / Удалить / Выбрать.
        // Legacy-пункты PinoK (#FAVE-MSG, закреп, «отмечено отвеченным»,
        // восстановление) сохранены в хвосте меню; пункт «Реакция» заменён
        // пиалетом (ReactionBar-тап по-прежнему открывает ReactionPicker).
        Box(modifier = Modifier.fillMaxWidth()) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = onDismissContextMenu,
            ) {
                // Пиалет реакций (VK web MessageReactionPickerExtended):
                // 7 web-реакций (#REACTION-WEB-MAP), тап — toggle
                // (#REACTION-WEB-API: своя → снять, иначе поставить).
                // Тач-таргеты 34dp, стиль — MaterialTheme как у соседних пунктов.
                val bubbleReactions = message.reactions
                val myReactionId = if (bubbleReactions != null) bubbleReactions.userReaction else null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for ((rid, emoji) in REACTION_EMOJIS) {
                        val isMine = myReactionId != null && myReactionId == rid
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMine) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable {
                                    onDismissContextMenu()
                                    onReact(rid)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
                // Снапшот: vkme_messages_action_reply.
                DropdownMenuItem(
                    text = { Text("Ответить") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null) },
                    onClick = onReply,
                )
                // Снапшот: vkme_messages_action_forward (подменю выбора чата —
                // долг волны 32; здесь существующий ForwardDialog).
                DropdownMenuItem(
                    text = { Text("Переслать") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Forward, contentDescription = null) },
                    onClick = onForward,
                )
                // Снапшот: vkme_messages_action_mark_important — новый API
                // messagesMarkAsImportant (волна 32). Показ — только НЕ канал
                // (canMarkImportant решает хост: peerId > 0). Иконки нет:
                // правило волны — новых иконок не добавлять, пункт текстовый.
                if (canMarkImportant && !message.isAction) {
                    DropdownMenuItem(
                        text = { Text("Отметить как важное") },
                        onClick = {
                            onDismissContextMenu()
                            onMarkImportant()
                        },
                    )
                }
                // Снапшот: vkme_channel_post_action_copy_text — «Копировать текст»,
                // только для сообщений с непустым текстом.
                if (message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Копировать текст") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("msg", message.text))
                            onDismissContextMenu()
                        },
                    )
                }
                // Снапшот: vkme_messages_action_edit — только своё (isOut).
                if (isOut) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = onEdit,
                    )
                }
                // Снапшот: vkme_messages_action_delete — для любых сообщений
                // (не только своих; существующий deleteMessage-флоу).
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = onDelete,
                )
                // Снапшот: vkme_messages_action_select — вход в selection.
                // #IM-MENU-LONGPRESS-FIX: единственная точка входа в selection —
                // этот пункт (pref msgMultiSelect управляет ДОСТУПНОСТЬЮ selection,
                // но long-press меню больше не грабит).
                if (multiSelectAvailable && !selectionMode && !message.isAction) {
                    DropdownMenuItem(
                        text = { Text("Выбрать") },
                        leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                        onClick = onSelect,
                    )
                }
                // ---- Legacy-пункты PinoK (вне снапшота — функциональность сохранена) ----
                // #FAVE-MSG: «В избранное» — пересылает сообщение в self-chat
                // (peer_id = myUserId). Один тап, без ForwardDialog.
                DropdownMenuItem(
                    text = { Text("В избранное") },
                    leadingIcon = { Icon(Icons.Outlined.Bookmark, contentDescription = null) },
                    onClick = {
                        onDismissContextMenu()
                        onSaveToSelf()
                    },
                )
                // P0.3: pin/unpin message (group chats only, onPin != null).
                if (onPin != null) {
                    DropdownMenuItem(
                        text = { Text(if (isPinned) "Открепить" else "Закрепить") },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                        onClick = {
                            onDismissContextMenu()
                            onPin()
                        },
                    )
                }
                // #60: Отметить как отвеченное
                DropdownMenuItem(
                    text = { Text("Отметить отвеченным") },
                    leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                    onClick = onMarkAnswered,
                )
                // #60: Восстановить (если сообщение удалено)
                if (message.isDeleted) {
                    DropdownMenuItem(
                        text = { Text("Восстановить") },
                        leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                        onClick = onRestore,
                    )
                }
            }
        }

        // ReactionPicker — панель эмодзи.
        if (showReactionPicker) {
            ReactionPicker(
                currentReaction = message.reactions?.userReaction,
                onReact = onReact,
                onDismiss = onHideReactionPicker,
            )
        }
    }  // closes Column (with swipe offset)
    }  // closes Box (swipe wrapper)
}

/** Fix #99: мини-карточка wall-вложения в сообщении (превью поста). */
@Composable
private fun WallAttachmentCard(
    post: re.pinok.data.model.Post,
    profiles: Map<Long, UserProfile>,
    groups: Map<Long, re.pinok.api.VKApiClient.GroupInfo>,
    textColor: Color,
    onClick: () -> Unit,
) {
    // Fix #244: состояние выбора для вложения (toggle в selection mode,
    // long-press → вход в selection / context menu).
    val sel = LocalAttachmentSelection.current
    val authorName = if (post.fromId > 0) {
        val p = profiles[post.fromId]
        p?.let { "${it.firstName} ${it.lastName}" } ?: "id${post.fromId}"
    } else {
        val g = groups[-post.fromId]
        g?.name ?: "Сообщество"
    }
    val thumbUrl = PhotoSizes.bestUrl(
        post.attachments?.firstOrNull { it.type == "photo" && it.photo != null }?.photo?.sizes,
    )
    val truncatedText = post.text.take(120).let { if (post.text.length > 120) "$it..." else it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.12f))
            .combinedClickable(
                onClick = {
                    if (sel != null && sel.selectionMode) sel.onToggleSelection()
                    else onClick()
                },
                onLongClick = { sel?.onLongPress?.invoke() },
            )
            .padding(8.dp),
    ) {
        Text(
            text = authorName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (truncatedText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = truncatedText,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.95f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (thumbUrl != null) {
            Spacer(modifier = Modifier.height(6.dp))
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Отображение реакций под сообщением. */
@Composable
private fun ReactionBar(
    reactions: MessageReaction?,
    isOut: Boolean,
    onReact: (Int) -> Unit,
    onTap: () -> Unit,
) {
    if (reactions == null || reactions.count <= 0) return
    val bg = if (isOut)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer
    val fg = if (isOut)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    Row(
        modifier = Modifier
            .padding(top = 2.dp, start = if (isOut) 0.dp else 4.dp, end = if (isOut) 4.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
        .background(bg)
        .clickable { onTap() }
        .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Показываем основные эмодзи из recent_reactions (макс. 3).
        val recentIds = (reactions.recentReactions ?: emptyList())
            .map { it.reactionId }
            .distinct()
            .take(3)
        val displayEmojis = if (recentIds.isNotEmpty()) recentIds
        else listOfNotNull(reactions.userReaction)
        for (rid in displayEmojis) {
            Text(text = reactionEmoji(rid), fontSize = 14.sp)
        }
        if (reactions.count > 0) {
            Text(
                text = reactions.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontSize = 12.sp,
            )
        }
    }
}

/** #CHANNEL-FILTERS (Task #CHANNELS-UI): опции фильтра ленты канала —
 *  label → значение channels.getHistory filter (null = «Всё»). */
private val CHANNEL_FEED_FILTERS: List<Pair<String, String?>> = listOf(
    "Всё" to null,
    "Донат" to "donut",
    "Фото" to "photo",
    "Видео" to "video",
    "Аудио" to "audio",
    "Документы" to "doc",
)

/** #CHANNEL-FILTERS (Task #CHANNELS-UI): горизонтальный ряд tab-чипов фильтра
 *  ленты канала (снапшот: web табы фильтров истории). Текущий фильтр подсвечен
 *  primaryContainer; тап — onSelect с новым значением. Локален к каналу:
 *  обычные чаты этот композер не вызывают. */
@Composable
private fun ChannelFeedFilterRow(
    current: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((label, value) in CHANNEL_FEED_FILTERS) {
            val selected = current == value
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.clickable { onSelect(value) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** #CHANNEL-DONUT / #CHANNEL-POST-UI: футер канального поста «как в ВК».
 *  Вызывается из MessageBubble только для канальных постов (peerId<0):
 *  fields viewsCount/commentsCount/isDonut/paywall* заполнены
 *  parseChannelHistoryItem в VKApiClient. Обычные сообщения не затрагиваются.
 *
 *  Раскладка по снапшоту (ChannelPostPrimary__footer, Мессенджер_каналы_дилог):
 *   1) ChannelPostPrimary__reactions — ряд чипов ReactionChip: эмодзи + счётчик
 *      (ReactionChip__counter--numbers). Источник — message.reactions.items
 *      (id → count); при пустом items чипы не рисуются (агрегированный
 *      ReactionBar под bubble отдаёт их отдельным рядом — см. MessageBubble).
 *   2) ChannelPostMeta__views — «глаз» (vkuiIcon view_12) + «1,3K».
 *   3) ChannelPostPrimaryComments — comment_outline_16 + «N комментарий» +
 *      chevron_16; тап → ChannelCommentsDialog (channels.getComments).
 *
 *  #CHANNELS-UI (Task #CHANNELS-UI): кнопка paywall кликабельна — onPaywallClick
 *  открывает payment-ссылку (через onUrlClick вызывающего). #CHANNELS-COMMENTS:
 *  строка комментариев рисуется для ЛЮБОГО канального поста (кнопка нужна даже
 *  без счётчика). #CHANNEL-POST-UI: тап по чипу реакции — toggle (onReact). */
@Composable
private fun ChannelPostInfoRow(
    message: Message,
    isOut: Boolean,
    textColor: Color,
    onPaywallClick: (() -> Unit)? = null,
    // #CHANNELS-COMMENTS: тап по кнопке комментариев → открыть панель.
    onCommentsClick: () -> Unit = {},
    // #CHANNEL-POST-UI: тап по реакционному чипу → toggle (messages.sendReaction).
    onReact: (Int) -> Unit = {},
) {
    val captionColor = textColor.copy(alpha = 0.7f)
    val views = message.viewsCount?.takeIf { it > 0 }
    val comments = message.commentsCount?.takeIf { it > 0 }
    val reactionCost = message.reactionCost?.takeIf { it > 0 }
    // Строка счётчиков (просмотры/платная реакция) рисуется, только если есть
    // сами данные; комментарии выводятся отдельной строкой ниже — всегда.
    val showCounters = views != null || reactionCost != null
    val donutText = message.paywallPlaceholder
        ?: message.paywallSnippet
        ?: "Донат-контент"
    val msgReactions = message.reactions

    Column(
        modifier = Modifier
            .padding(
                top = 4.dp,
                start = if (isOut) 0.dp else 4.dp,
                end = if (isOut) 4.dp else 0.dp,
            ),
    ) {
        // #CHANNEL-POST-UI: реакционные чипы VK (ReactionChip: эмодзи + число).
        // Разбивка message.reactions.items (id → count), сортировка по убыванию
        // счётчика (как в снапшоте — крупные реакции первыми), максимум 6 чипов.
        val chipItems = (msgReactions?.items ?: emptyList())
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
            .take(6)
        if (chipItems.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (chip in chipItems) {
                    val active = msgReactions?.userReactionActive(chip.id) == true
                    val pillBg = if (active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        textColor.copy(alpha = 0.1f)
                    }
                    val pillFg = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        textColor.copy(alpha = 0.85f)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = pillBg,
                        contentColor = pillFg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onReact(chip.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(text = reactionEmoji(chip.id), fontSize = 12.sp)
                            Text(
                                text = channelCounterString(chip.count),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
        // #CHANNEL-POST-UI: счётчики просмотров/комментариев и (если есть)
        // цена платной реакции. Порядок и иконки «как в ВК»: «глаз» view_12
        // перед числом просмотров; комментарии с comment_outline_16 и шевроном
        // (ChannelPostPrimaryComments__content) в кликабельной строке ниже.
        if (showCounters) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (views != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = captionColor,
                        )
                        Text(
                            text = channelCounterString(views),
                            style = MaterialTheme.typography.labelSmall,
                            color = captionColor,
                            fontSize = 11.sp,
                        )
                    }
                }
                // Счётчик комментариев в этой строке не дублируем — он выводится
                // в отдельной кликабельной строке комментариев ниже («как в ВК»:
                // ChannelPostPrimaryComments → comment_outline_16 + «N комментарий»).
                // #CHANNELS-UI (Task #CHANNELS-UI): платные реакции — рядом с
                // остальными счётчиками текст «Реакция · N ₽» (reactionCost).
                if (reactionCost != null && (msgReactions?.count ?: 0) > 0) {
                    Text(
                        text = "Реакция · $reactionCost ₽",
                        style = MaterialTheme.typography.labelSmall,
                        color = captionColor,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        // #CHANNEL-POST-UI / #CHANNELS-COMMENTS: строка комментариев канального
        // поста «как в ВК» (ChannelPostPrimaryComments: comment_outline_16 +
        // «N комментарий» + chevron_16) → открывает панель комментариев
        // (channels.getComments). Рисуем для ЛЮБОГО канального поста (даже без
        // счётчика) — у постов канала комментарии почти всегда есть. Только
        // действие; обычные диалоги не затрагиваются.
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Transparent,
            contentColor = captionColor,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { onCommentsClick() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = captionColor,
                )
                Text(
                    // Счётчик в строке — при его отсутствии просто «Комментарии».
                    text = if (comments != null) "Комментарии · ${channelCounterString(comments)}"
                    else "Комментарии",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = captionColor,
                )
            }
        }
        // Донат-paywall: заглушка «Поддержать автора / Открыть за N ₽/мес».
        // #CHANNELS-UI: кнопка кликабельна — onPaywallClick открывает
        // payment-ссылку (пользователь переходит к оформлению доната).
        if (message.isDonut) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = donutText,
                    style = MaterialTheme.typography.labelMedium,
                    color = captionColor,
                )
                val btn = message.paywallButton
                if (!btn.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(enabled = onPaywallClick != null) {
                                onPaywallClick?.invoke()
                            },
                    ) {
                        Text(
                            text = btn,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** #CHANNELS-UI2 (Task 1) / #CHANNEL-POST-UI: счётчики просмотров/комментариев
 *  и донат-paywall для ПОСТА стены канала в wall-режиме (модель Post). Аналог
 *  ChannelPostInfoRow (история), но читает поля Post: views.count /
 *  comments.count / donut (вместо message.viewsCount/commentsCount/isDonut).
 *  Вызывается ТОЛЬКО в wall-режиме канала под карточкой WallPostCard —
 *  полностью аддитивно, обычные посты не затрагиваются. Платные реакции
 *  (reactionCost) в модели Post отсутствуют → строка «Реакция · N ₽» здесь
 *  не выводится (отложено, требует дозагрузки стоимости доната отдельно).
 *  Реакционные чипы (ReactionChip) в wall-режиме НЕ выводятся: у модели Post
 *  reactions — только count/userReacted, разбивки по эмодзи items нет
 *  (в отличие от MessageReaction канальной истории) → отложено до дозагрузки. */
@Composable
private fun ChannelPostWallInfoRow(
    post: Post,
    onPaywallClick: (() -> Unit)? = null,
    // #CHANNELS-COMMENTS: тап по кнопке комментариев → открыть панель.
    onCommentsClick: () -> Unit = {},
) {
    val captionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val views = post.views?.count?.takeIf { it > 0 }
    val comments = post.comments?.count?.takeIf { it > 0 }
    val donutText = post.donut?.placeholder ?: "Донат-контент"

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        // #CHANNEL-POST-UI: счётчик просмотров «как в ВК» (ChannelPostMeta__views:
        // «глаз» view_12 + «1,3K»). Счётчик комментариев здесь не дублируем — он
        // в отдельной кликабельной строке ниже.
        if (views != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = captionColor,
                )
                Text(
                    text = channelCounterString(views),
                    style = MaterialTheme.typography.labelSmall,
                    color = captionColor,
                    fontSize = 11.sp,
                )
            }
        }
        // #CHANNELS-COMMENTS (Task #CHANNELS-COMMENTS) / #CHANNEL-POST-UI: строка
        // «N комментария» поста стены канала «как в ВК» (ChannelPostPrimaryComments:
        // comment_outline_16 + подпись + chevron_16) → открывает панель комментариев.
        // У модели Post счётчик комментариев из post.comments.count (может быть
        // пуст — тогда просто «Комментарии»). Аддитивно, только для канальных постов.
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Transparent,
            contentColor = captionColor,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { onCommentsClick() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = captionColor,
                )
                Text(
                    text = if (comments != null) "Комментарии · ${channelCounterString(comments)}"
                    else "Комментарии",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = captionColor,
                )
            }
        }
        // Донат-paywall: плашка-заглушка «Поддержать автора / Открыть по подписке».
        // У модели Post нет paywallButton — кнопка фиксированная (для канала),
        // контент — placeholder доната поста (или generic-заглушка).
        if (post.isDonut) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = donutText,
                    style = MaterialTheme.typography.labelMedium,
                    color = captionColor,
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(enabled = onPaywallClick != null) {
                            onPaywallClick?.invoke()
                        },
                ) {
                    Text(
                        text = "Открыть по подписке",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * #CHANNEL-POST-CARD: конвертер канального Message (history-режим,
 * channels.getHistory) в Post для переиспользования WallPostCard +
 * ChannelPostWallInfoRow. У Message нет likes/reposts/Post.Reactions —
 * эти поля остаются в дефолте/null (WallPostCard без лайк-счётчика сервера
 * полагается на optimistic-состояние channelLikeStates). Только `?.`/`?:`.
 */
private fun messageToChannelPost(msg: Message): Post {
    val ownerId = msg.peerId
    val vc = msg.viewsCount
    val cc = msg.commentsCount
    return Post(
        id = msg.id,
        ownerId = ownerId,
        fromId = if (msg.fromId != 0L) msg.fromId else ownerId,
        date = msg.date,
        text = msg.text,
        attachments = msg.attachments,
        views = if (vc != null) Post.Views(vc) else null,
        comments = if (cc != null) Post.Comments(cc) else null,
        donut = if (msg.isDonut) {
            Post.Donut(
                isDonut = true,
                placeholder = msg.paywallPlaceholder ?: msg.paywallSnippet,
                canPublishFreeCopy = false,
            )
        } else null,
    )
}

/**
 * #CHANNELS-COMMENTS (Task #CHANNELS-COMMENTS): диалог комментариев к посту
 * канала. Загружает channels.getComments, показывает список (автор/текст/дата),
 * позволяет добавить комментарий (channels.createComment) и отредактировать
 * СВОЙ комментарий (channels.editComment, если canEdit из ответа API). Вся
 * логика самодостаточна — вызывается ТОЛЬКО из канального режима (peerId<0),
 * обычные диалоги не затрагиваются. Стиль: только `?.`/`?:`, без `!!`.
 */
@Composable
private fun ChannelCommentsDialog(
    apiClient: re.pinok.api.VKApiClient,
    channelId: Long,
    cmid: Long,
    onDismiss: () -> Unit,
    myUserId: Long = 0L,
) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<re.pinok.api.VKApiClient.ChannelComment>>(emptyList()) }
    var profiles by remember { mutableStateOf<Map<Long, re.pinok.data.model.UserProfile>>(emptyMap()) }
    var groups by remember { mutableStateOf<Map<Long, re.pinok.data.model.Group>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // Редактирование своего комментария: целевой комментарий + текст (не null = диалог редактирования).
    var editingComment by remember { mutableStateOf<re.pinok.api.VKApiClient.ChannelComment?>(null) }
    var editText by remember { mutableStateOf("") }
    var savingEdit by remember { mutableStateOf(false) }
    // Удаление своего комментария (canDelete): на подтверждение + стек вызова.
    var deletingComment by remember { mutableStateOf<re.pinok.api.VKApiClient.ChannelComment?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    fun loadComments() {
        loading = true
        loadError = null
        scope.launch {
            try {
                val data = apiClient.channelsGetComments(channelId, cmid)
                comments = data.comments
                profiles = data.profiles
                groups = data.groups
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = "Не удалось загрузить комментарии: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun authorName(fromId: Long): String {
        if (fromId > 0) {
            val p = profiles[fromId]
            if (p != null) {
                val n = (p.firstName + " " + p.lastName).trim()
                return if (n.isBlank()) "Пользователь" else n
            }
        } else if (fromId < 0) {
            val g = groups[-fromId]
            if (g != null) return g.name
        }
        return "Пользователь"
    }

    fun formatCommentDate(epoch: Long): String {
        if (epoch <= 0L) return ""
        return try {
            SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(epoch * 1000L))
        } catch (e: Exception) {
            ""
        }
    }

    LaunchedEffect(channelId, cmid) { loadComments() }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Комментарии") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                when {
                    loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    loadError != null -> {
                        Text(
                            text = loadError ?: "Ошибка",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { loadComments() }) { Text("Повторить") }
                    }
                    comments.isEmpty() -> {
                        Text(
                            text = "Пока нет комментариев",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(min = 120.dp, max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
items(comments, key = { it.id }) { c ->
                                val canEdit = c.canEdit
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Кружок-аватарка из инициалов (без картинок — нет
                                        // сетевого лоадера в диалоге; имя — из profiles/groups).
                                        val name = authorName(c.fromId)
                                        val initial = name.take(1).uppercase()
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = initial,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val dateStr = formatCommentDate(c.date)
                                        if (dateStr.isNotEmpty()) {
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        // Свои действия: редактирование (canEdit) и
                                        // удаление (canDelete) — свой комментарий (fromId==myUserId)
                                        // всегда редактируется/удаляется, даже если сервер не
                                        // прислал can_edit/can_delete (channels.getComments).
                                        val isOwn = c.fromId != 0L && c.fromId == myUserId
                                        if (canEdit || isOwn) {
                                            IconButton(
                                                onClick = {
                                                    editingComment = c
                                                    editText = c.text
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = "Редактировать",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (c.canDelete || isOwn) {
                                            IconButton(
                                                enabled = !deleting,
                                                onClick = { deletingComment = c },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = "Удалить",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = c.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(start = 36.dp, top = 2.dp),
                                    )
                                }
HorizontalDivider(
                                        modifier = Modifier.padding(top = 6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

                // Редактирование своего комментария (поле + сохранить/отмена).
                val editing = editingComment
                if (editing != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Изменить комментарий…") },
                            maxLines = 3,
                        )
                        TextButton(
                            enabled = editText.isNotBlank() && !savingEdit,
                            onClick = {
                                val newText = editText.trim()
                                if (newText.isEmpty()) return@TextButton
                                savingEdit = true
                                scope.launch {
                                    try {
                                        val ok = apiClient.channelsEditComment(channelId, editing.id, newText)
                                        if (ok) {
                                            editingComment = null
                                            editText = ""
                                            loadComments()
                                        } else {
                                            loadError = "Не удалось сохранить комментарий"
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        loadError = "Ошибка сохранения: ${e.message}"
                                    } finally {
                                        savingEdit = false
                                    }
                                }
                            },
                        ) { Text("Сохранить") }
                        TextButton(
                            enabled = !savingEdit,
                            onClick = {
                                editingComment = null
                                editText = ""
                            },
                        ) { Text("Отмена") }
                    }
                } else {
                    // Ввод нового комментария.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Написать комментарий…") },
                            maxLines = 3,
                        )
                        IconButton(
                            enabled = inputText.isNotBlank() && !sending,
                            onClick = {
                                val text = inputText.trim()
                                if (text.isEmpty()) return@IconButton
                                sending = true
                                scope.launch {
                                    try {
                                        val ok = apiClient.channelsCreateComment(channelId, cmid, text)
                                        if (ok) {
                                            inputText = ""
                                            loadComments()
                                        } else {
                                            loadError = "Не удалось отправить комментарий"
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        loadError = "Ошибка отправки: ${e.message}"
                                    } finally {
                                        sending = false
                                    }
                                }
                            },
                        ) {
                            if (sending) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = "Отправить",
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) { Text("Закрыть") }
        },
    )

    // Подтверждение удаления своего комментария (canDelete).
    val target = deletingComment
    if (target != null) {
        AlertDialog(
            onDismissRequest = {
                if (!deleting) deletingComment = null
            },
            title = { Text("Удалить комментарий?") },
            text = { Text("Комментарий будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            try {
                                val ok = apiClient.channelsDeleteComment(channelId, target.id)
                                if (ok) {
                                    deletingComment = null
                                    loadComments()
                                } else {
                                    deletingComment = null
                                    Toast.makeText(ctx, "Не удалось удалить комментарий", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                deletingComment = null
                                Toast.makeText(ctx, "Ошибка удаления: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                deleting = false
                            }
                        }
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = { deletingComment = null },
                ) { Text("Отмена") }
            },
        )
    }
}

/** #CHANNEL-DONUT: компактный формат счётчика канального поста — как web-снапшот:
 *  round тысячи → «80K», неточные → «1,2K» (запятая), <1000 → «999», млн → «1,5M». */
private fun channelCounterString(n: Int): String = when {
    n >= 1_000_000 -> compactMetric(n / 1_000_000.0, "M")
    n >= 1_000 -> compactMetric(n / 1_000.0, "K")
    else -> n.toString()
}

/** Одна значащая цифра после запятой; целые тысячи/миллионы — без хвоста (80K),
 *  дробные — с запятой (1,2K). */
private fun compactMetric(value: Double, suffix: String): String {
    val s = String.format(java.util.Locale.US, "%.1f", value)
    val trimmed = if (s.endsWith(".0")) s.dropLast(2) else s
    return trimmed.replace('.', ',') + suffix
}

/** Пикер реакций — горизонтальная панель эмодзи. */
@Composable
private fun ReactionPicker(
    currentReaction: Int?,
    onReact: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((id, emoji) in REACTION_EMOJIS) {
            val isHighlighted = currentReaction == id
            val bgColor = if (isHighlighted)
                MaterialTheme.colorScheme.primaryContainer
            else Color.Transparent
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable {
                        onReact(id)
                        onDismiss()
                    }
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }
        }
    }
}


/**
 * #ARCH-CONTAINERS (Этап 1.5-а): заглушка фото-вложений, когда контейнер
 * :feature:photos НЕ зарегистрирован (или rendererKey хосту неизвестен) —
 * осознанная деградация по плану («чат при удалённом photos показывает
 * заглушку «скачать файл»»). Инлайн-превью нет; тап открывает хостовый
 * PhotoViewer (onPhotoClick), где доступно сохранение в галерею (ImageSaver).
 * Selection-режим (Fix #244) сохранён: тап в selection — toggle, long-press —
 * как у обычных вложений.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoAttachmentsStub(
    count: Int,
    textColor: Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.08f))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = textColor.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = if (count > 1) "Фото ($count)" else "Фото",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
            Text(
                text = "Нажмите, чтобы открыть — сохранить можно в просмотрщике",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
            )
        }
    }
}

/** Карточка видео-вложения в сообщении: превью + ▶ + длительность. */
@Composable
private fun VideoAttachmentCard(
    video: Video,
    textColor: Color,
    // P2.1: тап по видео-вложению → открыть в VideoPlayer.
    onClick: () -> Unit = {},
) {
    // Fix #244: состояние выбора для вложения.
    val sel = LocalAttachmentSelection.current
    // #VIDEO-FRAME-FIX: единый thumbUrl (max размер), а не firstOrNull (самый маленький).
    val thumbUrl = video.thumbUrl
    // #29 (build fix): Video.duration — non-nullable Int, elvis ?: 0 избыточен
    val durationSec = video.duration
    val durationStr = if (durationSec > 0) {
        val min = durationSec / 60
        val sec = durationSec % 60
        "%d:%02d".format(min, sec)
    } else ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {
                    if (sel != null && sel.selectionMode) sel.onToggleSelection()
                    else onClick()
                },
                onLongClick = { sel?.onLongPress?.invoke() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbUrl != null) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = "Видео",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Видео", color = Color.White.copy(alpha = 0.7f))
            }
        }
        // Оверлей: ▶ + длительность
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Воспроизвести",
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
        if (durationStr.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(durationStr, color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Вшивает SVG-контур формы (viewBox 216×216) в Path, масштабируя/сдвигая путь ТАК,
 * чтобы он точно занимал контейнер sizePx. Трансформация вшивается в сам путь
 * (p.transform), поэтому НЕ зависит от pivot DrawTransform (нет pivot-бага) и
 * безопасна при применении как clip на любом контейнере.
 */
private fun fitShapePath(d: String, sizePx: Size): Path {
    val s = minOf(sizePx.width, sizePx.height) / 216f
    val tx = (sizePx.width - 216f * s) / 2f
    val ty = (sizePx.height - 216f * s) / 2f
    val p = PathParser().parsePathString(VideoMessageShapes.normalizeSvgPath(d)).toPath()
    p.fillType = PathFillType.EvenOdd
    p.transform(Matrix().apply {
        scale(s, s)
        translate(tx / s, ty / s)
    })
    return p
}

/**
 * Shape из SVG-контура формы: обрезает контейнер (Box) по выбранной маске кружка.
 * Через Modifier.clip(Shape) клип применяется на уровне отрисовки контейнера,
 * поэтому и превью, и ExoPlayer (AndroidView), и все оверлеи обрезаются формой
 * и не вылезают за неё.
 */
private class VideoMessageShape(private val d: String?) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = d?.let { runCatching { fitShapePath(it, size) }.getOrNull() }
        return if (path != null) Outline.Generic(path)
        else Outline.Rectangle(Rect(Offset.Zero, size))
    }
}

/**
 * Базовое видео-сообщение («кружок» type="video_message"): квадратный бабл 216dp
 * по выбранной форме (VideoMessageShapes). Контейнер клипится формой (превью и
 * оверлеи обрезаются маской). При наличии playbackUrl воспроизводится INLINE в
 * бабле через ExoPlayer (Media3): loop + muted по умолчанию, кнопка play/pause,
 * лоадер, датчик громкости и плашка длительности. Без URL — статичное превью и Toast.
 */
@Composable
private fun VideoMessageBubble(
    videoMessage: VideoMessage,
    isOutgoing: Boolean,
) {
    // Fix #244: состояние выбора для вложения.
    val sel = LocalAttachmentSelection.current
    val context = LocalContext.current

    val d = VideoMessageShapes.PATHS.getOrNull(videoMessage.shapeId - VideoMessageShapes.SHAPE_ID_BASE)
        ?: VideoMessageShapes.PATHS.firstOrNull()
    // Форма контейнера: Shape из вшитого пути (без pivot-бага DrawTransform).
    val clipShape = remember(d) { d?.let { VideoMessageShape(it) } }

    val previewUrl = videoMessage.previewUrl
    val playbackUrl = videoMessage.playbackUrl
    val durationSec = videoMessage.duration

    // Состояние inline-воспроизведения.
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(playbackUrl != null) }
    var isMuted by remember { mutableStateOf(true) }
    // Счётчик retry при PlaybackException (не зацикливаемся): до 2 попыток.
    var retryCount by remember(playbackUrl) { mutableStateOf(0) }

    // ExoPlayer: создаётся только при наличии playbackUrl, loop + muted по умолчанию.
    val player = playbackUrl?.let { url ->
        remember(url) {
            ExoPlayer.Builder(context)
                .setLoadControl(re.pinok.media.VideoPlayerConfig.defaultLoadControl())
                .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
            }
        }
    }

    // Подписка на состояние плеера (play/pause + loading/error).
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = when (playbackState) {
                    Player.STATE_READY, Player.STATE_ENDED -> false
                    else -> true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
                isPlaying = false
                // Guarded retry: переподготовка до 2 раз при сетевой/ошибке
                // воспроизведения, чтобы не зацикливаться на постоянном сбое.
                val p = player
                if (p != null && retryCount < 2) {
                    retryCount++
                    isLoading = true
                    try {
                        p.prepare()
                        p.playWhenReady = true
                    } catch (_: Exception) {
                        isLoading = false
                    }
                }
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    // Освобождение плеера (без утечек).
    DisposableEffect(player) {
        onDispose { try { player?.release() } catch (_: Exception) {} }
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun toggleMute() {
        val p = player ?: return
        isMuted = !isMuted
        p.volume = if (isMuted) 0f else 1f
    }

    Box(
        modifier = Modifier
            .size(216.dp)
            .aspectRatio(1f)
            .then(if (clipShape != null) Modifier.clip(clipShape) else Modifier)
            .background(
                if (isOutgoing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .combinedClickable(
                onClick = {
                    if (sel != null && sel.selectionMode) sel.onToggleSelection()
                    else {
                        if (playbackUrl == null) {
                            Toast.makeText(context, "Видео недоступно", Toast.LENGTH_SHORT).show()
                        } else {
                            togglePlayPause()
                        }
                    }
                },
                onLongClick = { sel?.onLongPress?.invoke() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Превью-кадр с лёгким blur как подложка (веб-blur до загрузки видео).
        if (previewUrl != null) {
            AsyncImage(
                model = previewUrl,
                contentDescription = "Видео-сообщение",
                modifier = Modifier
                    .matchParentSize()
                    .blur(12.dp),
                contentScale = ContentScale.Crop,
            )
        }

        // Инлайн-видео: рендерится поверх превью, когда активное воспроизведение.
        if (isPlaying && player != null) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { it.player = player },
            )
        }

        // Кнопка-центр: play ⟷ pause (toggle).
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = playbackUrl != null) { togglePlayPause() },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 3.dp,
                    color = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                    tint = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // Датчик громкости (внизу-по-центру): toggle mute.
        if (playbackUrl != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                    .clickable { toggleMute() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Включить звук" else "Выключить звук",
                    tint = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Плашка длительности (слева-внизу).
        if (durationSec > 0) {
            val min = durationSec / 60
            val sec = durationSec % 60
            val durStr = "%d:%02d".format(min, sec)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    durStr,
                    color = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** Карточка ссылки в сообщении: превью + title + domain. */
@Composable
private fun LinkAttachmentCard(
    link: Attachment.Link,
    textColor: Color,
    // P5.1: открытие через onUrlClick (внутренний/внешний браузер по настройке).
    onOpen: () -> Unit = {},
) {
    // Fix #244: состояние выбора для вложения.
    val sel = LocalAttachmentSelection.current
    val thumbUrl = link.photo?.largestUrl
    val domain = try { java.net.URL(link.url).host.removePrefix("www.") } catch (_: Exception) { link.url }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = {
                    if (sel != null && sel.selectionMode) sel.onToggleSelection()
                    else onOpen()
                },
                onLongClick = { sel?.onLongPress?.invoke() },
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbUrl != null) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.title ?: domain,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            link.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = domain,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Карточка документа (не голосовое): иконка + имя + размер. */
@Composable
private fun DocAttachmentCard(
    doc: Attachment.Doc,
    textColor: Color,
    // P5.1: открытие через onUrlClick (внутренний/внешний браузер по настройке).
    onOpen: () -> Unit = {},
) {
    // Fix #244: состояние выбора для вложения.
    val sel = LocalAttachmentSelection.current
    val sizeStr = formatFileSize(doc.size)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.08f))
            .padding(8.dp)
            .combinedClickable(
                onClick = {
                    if (sel != null && sel.selectionMode) sel.onToggleSelection()
                    else onOpen()
                },
                onLongClick = { sel?.onLongPress?.invoke() },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.AttachFile,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.title,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${doc.ext.uppercase()} · $sizeStr",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    if (bytes < 1024 * 1024) return DecimalFormat("#.#").format(bytes / 1024.0) + " КБ"
    return DecimalFormat("#.#").format(bytes / (1024.0 * 1024.0)) + " МБ"
}

/**
 * Fix #295 (round 2): блок «Пересланное сообщение» с полным содержимым.
 *
 * Ранее fwd_messages рендерились как одна строка `fwd.text.take(80)` — без
 * имени автора и без вложений. Если исходное сообщение состояло только из
 * фото / файла / голосового / видео — в пузыре было видно лишь слово
 * «Пересланное сообщение», а само содержимое пропадало.
 *
 * Теперь блок показывает:
 *   • шапку: имя автора (из profiles/groups) + «Пересланное сообщение» + время,
 *   • полный текст (до 8 строк, с кликабельными ссылками),
 *   • превью вложений:
 *       - photo   → сетка миниатюр (до 4 шт, 80dp),
 *       - video   → миниатюра + ▶ + длительность,
 *       - audio_message / doc.audio_msg → иконка 🎤 + длительность,
 *       - doc (файл) → иконка 📄 + title + размер,
 *       - audio → иконка ♫ + title + artist,
 *       - link  → иконка 🔗 + title,
 *       - sticker → картинка стикера,
 *       - wall  → компактная WallAttachmentCard,
 *       - poll  → иконка 📊 + вопрос,
 *       - прочее → подпись типа («Вложение», «Подарок»…).
 *
 * Стиль — ненавязчивая плашка с левой полосой, как и раньше, но просторнее.
 */
@Composable
private fun ForwardedMessageBlock(
    fwd: Message,
    profiles: Map<Long, UserProfile>,
    groups: Map<Long, re.pinok.api.VKApiClient.GroupInfo>,
    textColor: Color,
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    onWallClick: (re.pinok.data.model.Post) -> Unit = {},
    onUrlClick: (String) -> Unit = {},
    onVideoClick: (Video) -> Unit = {},
) {
    val sel = LocalAttachmentSelection.current
    // Имя автора: если fromId > 0 — профиль пользователя, иначе — сообщество.
    val authorName = if (fwd.fromId > 0) {
        val p = profiles[fwd.fromId]
        p?.let { "${it.firstName} ${it.lastName}".trim() }
            ?.takeIf { it.isNotBlank() }
            ?: "id${fwd.fromId}"
    } else if (fwd.fromId < 0) {
        val g = groups[-fwd.fromId]
        g?.name?.takeIf { it.isNotBlank() } ?: "Сообщество"
    } else {
        null
    }
    // Дата в формате HH:mm (если есть).
    val timeText = if (fwd.date > 0) {
        try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(fwd.date * 1000L))
        } catch (_: Exception) { null }
    } else null

    // Список вложений (для компактного превью).
    val attachments = fwd.attachments.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.08f))
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        // Шапка: вертикальная полоска + имя автора + метка + время.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(28.dp)
                    .background(textColor.copy(alpha = 0.45f))
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = authorName ?: "Пересланное сообщение",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    if (authorName != null) append("Пересланное сообщение")
                    if (timeText != null) {
                        if (isNotEmpty()) append(" · ")
                        append(timeText)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
        }

        // Полный текст (до 8 строк, с кликабельными ссылками).
        if (fwd.text.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            // Ссылки в пересланном сообщении тоже кликабельны — используем
            // тот же linkifyVkText, что и для основного bubble.
            Text(
                text = re.pinok.util.linkifyVkText(fwd.text, textColor, onUrlClick),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.95f),
            )
        }

        // Превью вложений.
        if (attachments.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))

            // 1) Стикер — рендерим картинкой (поверх всего, как в основном bubble).
            val stickerAtt = attachments.firstOrNull { it.type == "sticker" && it.sticker != null }
            if (stickerAtt != null) {
                val sticker = stickerAtt.sticker
                val sUrl = sticker?.renderUrl
                if (sUrl != null) {
                    AsyncImage(
                        model = sUrl,
                        contentDescription = "Стикер",
                        modifier = Modifier.size(96.dp),
                    )
                }
            }

            // 2) Фото — сетка миниатюр (до 4 шт).
            val photoAttachments = attachments.filter { it.type == "photo" && it.photo != null }
            if (photoAttachments.isNotEmpty()) {
                val photoUrls = photoAttachments.mapNotNull { it.photo?.largestUrl }
                val cols = if (photoAttachments.size == 1) 1 else 2
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    photoAttachments.chunked(cols).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            for (att in row) {
                                val photo = att.photo
                                val url = photo?.largestUrl
                                val isSingle = cols == 1
                                if (photo != null && url != null) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Фото",
                                        modifier = Modifier
                                            .let { m -> if (isSingle) m.fillMaxWidth() else m.weight(1f) }
                                            .clip(RoundedCornerShape(6.dp))
                                            .heightIn(max = 140.dp)
                                            .combinedClickable(
                                                onClick = {
                                                    if (sel != null && sel.selectionMode) sel.onToggleSelection()
                                                    else {
                                                        val idx = photoUrls.indexOf(url).coerceAtLeast(0)
                                                        onPhotoClick(photoUrls, idx)
                                                    }
                                                },
                                                onLongClick = { sel?.onLongPress?.invoke() },
                                            ),
                                        contentScale = if (isSingle) ContentScale.Fit else ContentScale.Crop,
                                    )
                                } else {
                                    // Пустышка чтобы вес сохранился.
                                    if (!isSingle) Box(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 3) Видео — миниатюра с ▶ и длительностью.
            val videoAttachments = attachments.filter { it.type == "video" && it.video != null }
            for (vAtt in videoAttachments) {
                val video = vAtt.video ?: continue
                val thumb = video.image?.maxByOrNull { it.width * it.height }?.url
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(textColor.copy(alpha = 0.06f))
                        .clickable { onVideoClick(video) }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(textColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (thumb != null) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = "Видео",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title.ifBlank { "Видео" },
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.95f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val min = video.duration / 60
                        val sec = video.duration % 60
                        Text(
                            text = "$min:${"%02d".format(sec)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // 4) Голосовые сообщения (audio_message или doc.audio_msg).
            val voiceAttachments = attachments.filter {
                (it.type == "audio_message" && it.audioMessage != null) ||
                (it.type == "doc" && it.doc?.isVoiceMessage == true)
            }
            for (vAtt in voiceAttachments) {
                val am = vAtt.audioMessage ?: vAtt.doc?.audioMsg ?: continue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(textColor.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Голосовое сообщение",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.95f),
                        modifier = Modifier.weight(1f),
                    )
                    val min = am.duration / 60
                    val sec = am.duration % 60
                    Text(
                        text = "$min:${"%02d".format(sec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                    )
                }
            }

            // 5) Документы-файлы (без голосовых).
            val docAttachments = attachments.filter {
                it.type == "doc" && it.doc?.isVoiceMessage == false
            }
            for (dAtt in docAttachments) {
                val doc = dAtt.doc ?: continue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(textColor.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.title.ifBlank { "Документ" },
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.95f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = doc.ext.uppercase() + " · " + formatFileSize(doc.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                }
            }

            // 6) Аудиозаписи.
            val audioAttachments = attachments.filter { it.type == "audio" && it.audio != null }
            for (aAtt in audioAttachments) {
                val track = aAtt.audio ?: continue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(textColor.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title.ifBlank { "Аудиозапись" },
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.95f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val min = track.duration / 60
                    val sec = track.duration % 60
                    Text(
                        text = "$min:${"%02d".format(sec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                    )
                }
            }

            // 7) Ссылки.
            val linkAttachments = attachments.filter { it.type == "link" && it.link != null }
            for (lAtt in linkAttachments) {
                val link = lAtt.link ?: continue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(textColor.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Link,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = link.title?.takeIf { it.isNotBlank() } ?: link.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.95f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 8) Опросы.
            val pollAttachments = attachments.filter { it.type == "poll" && it.poll != null }
            for (pAtt in pollAttachments) {
                val poll = pAtt.poll ?: continue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(textColor.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = poll.question.ifBlank { "Опрос" },
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.95f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 9) Записи на стене (wall) — компактная WallAttachmentCard.
            val wallAttachments = attachments.filter { it.type == "wall" && it.wall != null }
            for (wAtt in wallAttachments) {
                val wallPost = wAtt.wall ?: continue
                WallAttachmentCard(
                    post = wallPost,
                    profiles = profiles,
                    groups = groups,
                    textColor = textColor,
                    onClick = { onWallClick(wallPost) },
                )
            }

            // 10) Прочие вложения — подпись типа («Подарок», «Денежный перевод»…).
            val knownTypes = setOf(
                "sticker", "photo", "video", "audio_message", "doc", "audio", "link", "poll", "wall",
            )
            val otherAttachments = attachments.filter { it.type !in knownTypes }
            if (otherAttachments.isNotEmpty()) {
                val labels = otherAttachments.map { att ->
                    when (att.type) {
                        "gift" -> "Подарок"
                        "money_transfer" -> "Денежный перевод"
                        "audio_playlist" -> "Плейлист"
                        "story" -> "История"
                        "market" -> "Товар"
                        "graffiti" -> "Граффити"
                        else -> "Вложение"
                    }
                }.distinct()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(textColor.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Article,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = labels.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.95f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } // конец блока вложений
    }
}

// Fix #204: linkify вынесен в re.pinok.util.linkifyVkText (единый для ленты,
// просмотра поста и чата). Поддерживает VK inline-токен [#alias|display|url]
// + обычные http(s):// и www. URL. Старая linkifyMessageText удалена.

// ══════════════════════════════════════════════════════════════════════
// #59: Дополнительные типы вложений в сообщениях
// ══════════════════════════════════════════════════════════════════════

/**
 * Заглушка аудио-вложения (fallback без контейнера :feature:audio, Этап 1.5-б)
 * по образцу PhotoAttachmentsStub: компактная строка «Аудио: название» с ▶.
 * Воспроизведение живо и без рендерера: тап → onAudioClick (хостовый
 * PlayerConnection, P2.2). Selection-режим (Fix #244) сохранён: тап в
 * selection — toggle, long-press — как у обычных вложений.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioAttachmentsStub(
    title: String,
    textColor: Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.08f))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = textColor.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "Аудио: $title",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Нажмите, чтобы воспроизвести",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
            )
        }
    }
}

/** Подарок — эмодзи + текст. */
@Composable
private fun GiftAttachmentCard(textColor: Color) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎁", fontSize = 36.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Подарок",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.7f),
        )
    }
}

/** Граффити — заглушка с иконкой. */
@Composable
private fun GraffitiAttachmentCard(textColor: Color) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎨", fontSize = 36.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Граффити",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.7f),
        )
    }
}

/**
 * Опрос — компактная карточка.
 *
 * P2.3: если пользователь ещё не голосовал (poll.answerId == null) — варианты
 * кликабельны. После голосования или если уже голосовал — показываем проценты.
 */
@Composable
private fun PollAttachmentRow(
    poll: re.pinok.data.model.Poll,
    textColor: Color,
    // P2.3: callback для голосования. Передаёт list of answer IDs.
    onVote: (List<Long>) -> Unit = {},
) {
    // Fix #244: состояние выбора для вложения.
    val sel = LocalAttachmentSelection.current
    val hasVoted = poll.isVoted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.08f))
            .padding(10.dp),
    ) {
        Text(
            text = "📊 ${poll.question}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        poll.answers.take(6).forEach { answer ->
            val isSelected = poll.answerId == answer.id
            val rateText = if (hasVoted && poll.votes > 0) {
                val pct = (answer.votes * 100.0 / poll.votes).toInt()
                "$pct%"
            } else null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .let { m ->
                        // P2.3: clickable только если не голосовал и опрос не закрыт.
                        // Fix #244: в selection mode — toggle вместо голосования.
                        if (!hasVoted && poll.closed == 0) {
                            m.clip(RoundedCornerShape(4.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (sel != null && sel.selectionMode) sel.onToggleSelection()
                                        else onVote(listOf(answer.id))
                                    },
                                    onLongClick = { sel?.onLongPress?.invoke() },
                                )
                        } else m
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isSelected) "✓ " else "• ",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = answer.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = if (isSelected) 1f else 0.7f),
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (rateText != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = rateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${answer.votes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val metaText = buildString {
            append("Всего голосов: ${poll.votes}")
            if (poll.isAnonymous) append(" · Анонимный")
            if (poll.closed == 1) append(" · Закрыт")
            if (poll.multiple == 1) append(" · Множественный выбор")
        }
        Text(
            text = metaText,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.5f),
        )
    }
}

/** Местоположение — заглушка. */
@Composable
private fun MapAttachmentCard(textColor: Color) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📍", fontSize = 24.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Местоположение",
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.7f),
        )
    }
}

/**
 * Fix #115: Панель активной записи голосового — VK Web-style.
 * Соответствует `ConvoComposer__voice` + `VoiceRecording` (CSS-grid 'icon track duration'):
 * красный круглый mic-stop (как `ConvoComposer__buttonIcon--startRecording` 24×24),
 * waveform-canvas (как `VoiceRecording__svg` 21dp), duration "0:04".
 *
 * Кнопки: Cancel (delete) | waveform + duration | Stop (→ review) | Send (→ сразу отправить).
 */
@Composable
private fun VoiceRecordingToolbar(
    seconds: Int,
    amplitudes: List<Float>,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cancel — иконка корзины/cancel как в VK (cancel_outline_24).
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Отменить запись",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        // Waveform canvas + duration (grid-area: track + duration).
        Row(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceWaveformCanvas(
                amplitudes = amplitudes,
                progress = 1f, // весь waveform виден при записи
                accentColor = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                modifier = Modifier.weight(1f).height(21.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Duration — tabular figures чтобы не дрожало.
            Text(
                text = seconds.toRecordingTimeString(),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.error,
            )
        }
        // Stop → перейти в режим review (play-before-send).
        // Красный круглый как VK ConvoComposer__buttonIcon--stopRecording.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onError),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        // Send — сразу отправить без review.
        IconButton(onClick = onSend) {
            Icon(
                Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Отправить голосовое",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Fix #115: Панель review (после stop, до send) — VK Web-style.
 * Соответствует стейту «Прослушать перед отправкой»:
 * `ConvoComposer__buttonIcon--startRecording` (resume, microphone_16) +
 * `VoiceRecording__play--withMargin` (play_16) + waveform + duration + send.
 */
@Composable
private fun VoiceReviewToolbar(
    seconds: Int,
    amplitudes: List<Float>,
    isPlaying: Boolean,
    progress: Float,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onPlay: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cancel (delete отложенный файл).
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Удалить запись",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        // Resume запись (продолжить с того же места).
        IconButton(onClick = onResume) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Продолжить запись",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        // Play/Pause preview.
        IconButton(onClick = onPlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Прослушать",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        // Waveform + duration.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceWaveformCanvas(
                amplitudes = amplitudes,
                progress = if (isPlaying) progress else 0f,
                accentColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                modifier = Modifier.weight(1f).height(21.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = seconds.toRecordingTimeString(),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Send.
        IconButton(onClick = onSend) {
            Icon(
                Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Отправить голосовое",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Fix #115: Waveform-canvas как VK `VoiceRecording__svg`.
 * Рисует вертикальные столбики из списка амплитуд (0..1).
 * Прогресс (0..1) обрезает waveform слева-направо через clipRect.
 *
 * @param amplitudes список 0..1 (новые в конце)
 * @param progress   0..1 — доля waveform, окрашенная в accentColor (остальное в trackColor)
 */
@Composable
private fun VoiceWaveformCanvas(
    amplitudes: List<Float>,
    progress: Float,
    accentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (amplitudes.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val barWidth = 2.dp.toPx()
        val gap = 1.5.dp.toPx()
        val step = barWidth + gap
        val maxBars = (w / step).toInt().coerceAtLeast(1)
        // Берём последние maxBars семплов (свежие справа).
        val startIdx = (amplitudes.size - maxBars).coerceAtLeast(0)
        val visible = amplitudes.subList(startIdx, amplitudes.size)
        val centerY = h / 2f
        val progressX = w * progress.coerceIn(0f, 1f)
        var x = 0f
        for (amp in visible) {
            val barH = (h * (0.1f + amp * 0.9f)).coerceAtLeast(2.dp.toPx())
            val color = if (x <= progressX) accentColor else trackColor
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, centerY - barH / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
            )
            x += step
            if (x + barWidth > w) break
        }
    }
}

/**
 * Sprint 3 #12: Пузырь голосового сообщения — waveform + play/pause + duration.
 *
 * Fix #120: Воспроизведение делегировано в [VoicePlaybackController] (единый
 * на весь чат). Теперь только одно голосовое играет одновременно — клик по
 * другому сообщению останавливает текущее. Состояние (isPlaying, progress)
 * читается из контроллера реактивно.
 */
@Composable
private fun VoiceMessageBubble(
    doc: Attachment.Doc,
    textColor: Color,
    accentColor: Color,
    messageId: Long,
    controller: VoicePlaybackController,
    // P0.25 #VOICE-ASR-FETCH: callback для запроса transcript (messages.getById).
    // Вызывается при тапе на ASR-кнопку, если transcript ещё не готов.
    onRequestTranscript: () -> Unit = {},
) {
    // P0.22 #VOICE-COLOR (2026-10): аудио-сообщения ВСЕГДА в цвете VK Modern #0077FF.
    // Раньше: accentColor = MaterialTheme.colorScheme.primary (для входящих) или textColor
    // (для исходящих — белый/чёрный). Пользователь захотел единый VK-акцент для всех голосовых.
    // Источник: VK Brand Guidelines — #0077FF = primary accent (vk.design).
    val voiceColor = Color(0xFF0077FF)
    // Fix #244: состояние выбора для вложения.
    val sel = LocalAttachmentSelection.current
    val audioMsg = doc.audioMsg ?: return
    // Fix #237 (voice playback): приоритет MP3 над OGG. MediaPlayer на Android
    // НЕ поддерживает OGG/Opus (формат VK voice) на большинстве устройств
    // (особенно MediaTek/старые API). VK отдаёт link_mp3 именно как fallback
    // для платформ без Opus. Раньше было linkOgg ?: linkMp3 → на несовместимых
    // устройствах prepareAsync молча падал в onError → тишина.
    // Теперь: MP3 (универсально) → OGG (для устройств с Opus-поддержкой) → doc.url.
    val url = audioMsg.linkMp3 ?: audioMsg.linkOgg ?: doc.url
    // Альтернативный URL для fallback при ошибке воспроизведения.
    val fallbackUrl = if (url == audioMsg.linkMp3) audioMsg.linkOgg else audioMsg.linkMp3

    // Состояние из единого контроллера.
    val isCurrent = controller.isCurrent(messageId)
    val isPlaying = isCurrent && controller.isPlaying
    val progress = if (isCurrent) controller.progress else 0f
    // Длительность: если это текущее — из контроллера (может быть уточнена после
    // prepare), иначе — из метаданных VK.
    val durationSec = if (isCurrent && controller.durationSec > 0f) controller.durationSec
                      else audioMsg.duration.toFloat()
    // P0.24 #VOICE-TIMER-FIX: elapsed из реальной позиции MediaPlayer (мс), НЕ через
    // progress * durationSec. Раньше рассинхрон если MediaPlayer.duration != VK metadata.
    val elapsedSec = if (isCurrent) (controller.currentPositionMs / 1000f).toInt()
                     else 0

    // P0.22 #VOICE-TRANSCRIPT: расшифровка ASR. Кнопка-шеврон показывается ВСЕГДА
    // для голосовых (как в VK web — AttachVoice__asrButton). Содержимое transcript
    // зависит от transcript_state:
    //   - "done" + text → показываем текст расшифровки.
    //   - "in_progress" → "Расшифровка готовится..." (stub).
    //   - "error" → "Ошибка расшифровки" (stub).
    //   - null/empty → "Расшифровка недоступна" (stub).
    // VK web: me_voice_asr_status_empty / _in_progress / _error + AttachVoice__transcriptStub.
    val transcriptText = audioMsg.transcript
    val transcriptState = audioMsg.transcriptState
    val transcriptContent: String? = when {
        !transcriptText.isNullOrBlank() && transcriptState == "done" -> transcriptText
        transcriptState == "in_progress" -> "Расшифровка готовится…"
        transcriptState == "error" -> "Ошибка расшифровки"
        transcriptState == "done" && transcriptText.isNullOrBlank() -> "Расшифровка недоступна"
        // P0.25b: transcriptState==null → VK ещё не готовил ASR (свежее голосовое).
        // Показываем "Расшифровка готовится…" — retry-fetch обновит через 10-30 сек.
        transcriptState == null -> "Расшифровка готовится…"
        else -> "Расшифровка недоступна"
    }
    // Кнопка ASR показывается всегда (даже без transcript — пользователь может нажать,
    // увидит stub). Исключение: если transcript отсутствует вовсе (null) — тоже показываем
    // кнопку, при тапе пользователь увидит "Расшифровка недоступна".
    val showAsrButton = true
    var transcriptExpanded by remember(messageId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        // Fix #244: в selection mode — toggle, не запускаем воспроизведение.
                        if (sel != null && sel.selectionMode) sel.onToggleSelection()
                        else {
                            // Единственный toggle — контроллер сам решает play/pause/switch.
                            // Fix #237: передаём fallbackUrl — если primary упадёт (например,
                            // OGG/Opus не поддерживается), контроллер попробует альтернативный.
                            controller.toggle(messageId, url, audioMsg.duration.toFloat(), fallbackUrl)
                        }
                    },
                    onLongClick = { sel?.onLongPress?.invoke() },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play/Pause icon.
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                tint = voiceColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Waveform + progress.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Waveform bars.
                val waveform = audioMsg.waveform
                if (waveform != null && waveform.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barCount = minOf(waveform.size, 32)
                        val step = waveform.size.toFloat() / barCount
                        val barW = 2.dp.toPx()
                        val gap = 1.5.dp.toPx()
                        val totalWidth = barCount * (barW + gap) - gap
                        val startX = (size.width - totalWidth) / 2
                        val maxH = size.height * 0.8f
                        for (i in 0 until barCount) {
                            val sample = waveform[(i * step).toInt()].coerceIn(0, 255)
                            val h = (sample / 255f) * maxH
                            val x = startX + i * (barW + gap)
                            val y = (size.height - h) / 2
                            // P0.22: voiceColor (#0077FF) до progress, серый после.
                            val barColor = if (isCurrent && x < size.width * progress) voiceColor
                                           else textColor.copy(alpha = 0.4f)
                            drawRoundRect(
                                color = barColor,
                                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(barW, h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                            )
                        }
                    }
                } else {
                    // Fallback: simple waveform bars.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barCount = 24
                        val barW = 2.dp.toPx()
                        val gap = 1.5.dp.toPx()
                        val totalWidth = barCount * (barW + gap) - gap
                        val startX = (size.width - totalWidth) / 2
                        for (i in 0 until barCount) {
                            val h = size.height * (0.3f + 0.5f * abs((i - barCount / 2f) / (barCount / 2f)))
                            val x = startX + i * (barW + gap)
                            val y = (size.height - h) / 2
                            val barColor = if (isCurrent && x < size.width * progress) voiceColor
                                           else textColor.copy(alpha = 0.4f)
                            drawRoundRect(
                                color = barColor,
                                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(barW, h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                            )
                        }
                    }
                }
                // Progress overlay.
                if (isCurrent && progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(28.dp)
                            .background(voiceColor.copy(alpha = 0.15f)),
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            // P0.24 #VOICE-TIMER-FIX: таймер — ОДНО число (как в VK web AttachVoice__duration).
            // Раньше: "elapsed / total" (0:03 / 0:08) — громоздко и некорректно при рассинхроне.
            // Теперь: если playing → elapsed (меняется в реальном времени), иначе → total.
            // Источник elapsed: controller.currentPositionMs (прямая позиция MediaPlayer, мс).
            val displaySec = if (isPlaying) elapsedSec else durationSec.toInt()
            Text(
                text = displaySec.toRecordingTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = voiceColor,
                fontSize = 11.sp,
            )

            // P0.22 #VOICE-TRANSCRIPT: кнопка-шеврон для показа расшифровки ASR.
            // VK web: AttachVoice__asrButton (chevron_up/down_outline_20).
            // Показывается ВСЕГДА для голосовых (showAsrButton=true).
            if (showAsrButton) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        // P0.25 #VOICE-ASR-FETCH: если transcript ещё не готов — запрашиваем
                        // через messages.getById (VK готовит ASR 5-30 сек после отправки).
                        // Если уже готов — просто toggle видимости.
                        if (transcriptContent == null) {
                            onRequestTranscript()
                        }
                        transcriptExpanded = !transcriptExpanded
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (transcriptExpanded) Icons.Filled.KeyboardArrowUp
                                      else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (transcriptExpanded) "Свернуть расшифровку"
                                             else "Показать расшифровку",
                        tint = voiceColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // P0.22 #VOICE-TRANSCRIPT: расшифровка ASR (как в VK web AttachVoice__transcript).
        // Левая вертикальная линия-сепаратор (2dp, voiceColor alpha 0.24) + текст/stub.
        // Показывается когда expanded — содержит либо текст, либо stub-сообщение.
        if (transcriptExpanded) {
            val displayText = transcriptContent ?: "Расшифровка недоступна"
            // P0.25b: stub = всё кроме реального текста (done + non-blank text).
            val isStub = transcriptState != "done" || transcriptText.isNullOrBlank()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(IntrinsicSize.Min)
                        .background(voiceColor.copy(alpha = if (isStub) 0.12f else 0.24f)),
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStub) textColor.copy(alpha = 0.5f) else textColor,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Fix #233 (sticker-enrich): Глобальный кеш animation_url по stickerId.
 *
 * VK message attachments (messages.getHistory / LongPoll) часто НЕ возвращают
 * поле animation_url для стикеров — только images / images_with_background.
 * Из-за этого стикеры в чате рендерятся как СТАТИЧНЫЕ даже если пак анимированный.
 *
 * Когда пользователь открывает стикер-панель (loadStickers → store.getStickerPacks),
 * мы заполняем этот кеш: stickerId → animationUrl. Затем в MessageBubble при
 * рендере стикера из сообщения, если у attachment нет animationUrl, смотрим в кеш.
 * Если находим — используем animatedDisplayUrl (Coil проиграет анимацию).
 */
object StickerAnimationCache {
    // Fix #234: @Volatile неприменим к val. Ссылка на карту не меняется —
    // меняется только содержимое, поэтому используем ConcurrentHashMap
    // для потокобезопасного чтения из композиции и записи из populate().
    private val cache: MutableMap<Int, String> = java.util.concurrent.ConcurrentHashMap()

    fun populate(packs: List<re.pinok.data.model.StickerPack>) {
        for (pack in packs) {
            for (sticker in pack.stickers ?: emptyList()) {
                val animUrl = sticker.animationUrl ?: continue
                // Lottie (.json/.tgs) не декодируется Coil — пропускаем.
                val lower = animUrl.substringBefore('?').substringAfterLast('/').lowercase()
                if (lower.endsWith(".json") || lower.endsWith(".tgs")) continue
                cache[sticker.stickerId] = animUrl
            }
        }
    }

    fun get(stickerId: Int): String? = cache[stickerId]
}

/**
 * Fix #201: Список эмодзи — вынесен на top-level чтобы не пересоздавать
 * listOf при каждой рекомпозиции EmojiStickerPanel.
 */
private val EMOJI_LIST: List<String> = listOf(
    "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇",
    "🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚",
    "😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🥸",
    "🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️",
    "😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡",
    "🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓",
    "🤗","🤔","🤭","🤫","🤥","😶","😐","😑","😬","🙄",
    "😯","😦","😧","😮","😲","🥱","😴","🤤","😪","😵",
    "🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕","🤑","🤠",
    "👿","👹","👺","🤡","💩","👻","💀","☠️","👽","👾",
    "🤖","🎃","😺","😸","😹","😻","😼","😽","🙀","😿",
    "😾","❤️","🧡","💛","💚","💙","💜","🤎","🖤","🤍",
    "❣️","💕","💞","💓","💗","💖","💘","💝","💟","💔",
    "👍","👎","👏","🙌","🤝","🙏","✌️","🤞","🤟","🤘",
    "👌","🤌","🤏","👈","👉","👆","👇","☝️","✋","🤚",
    "🖐️","🖖","👋","🤙","💪","🦾","🖕","✍️","👏","🤳",
    "🎉","🎊","🎈","🎂","🎁","🎀","🎄","🎃","🎆","🎇",
    "🧨","🎉","🎊","🎋","🎍","🎎","🎏","🎐","🎑","🧧",
    "🌟","⭐","✨","⚡","🔥","💥","💫","☀️","🌙","⛅",
    "☁️","🌧️","⛈️","🌨️","🌩️","🌪️","🌫️","🌈","☔","❄️",
    "☕","🍵","🍶","🍾","🍷","🍸","🍹","🍺","🍻","🥂",
)

/**
 * Fix #201: Единая панель эмодзи + стикеров с двумя вкладками (чипами).
 * Заменяет отдельные EmojiPickerPanel и StickerPickerPanel.
 *
 * Структура:
 * - Шапка: 2 чипа-таба (😀 Смайлы / 😐 Стикеры) слева + «Закрыть» справа
 * - Тело:
 *   - tab=0 → сетка эмодзи (8 колонок)
 *   - tab=1 → pack-tabs (горизонтальный скролл) + сетка стикеров (5 колонок)
 *
 * Высота фиксированная 280dp + navigationBarsPadding (вместо клавиатуры).
 * Чипы — кастомные (не TabRow), компактнее, как просил пользователь.
 */
@Composable
private fun EmojiStickerPanel(
    tab: Int,
    onTabChange: (Int) -> Unit,
    emojis: List<String>,
    emojiOnClick: (String) -> Unit,
    stickerPacks: List<re.pinok.data.model.StickerPack>,
    stickerLoading: Boolean,
    selectedStickerPack: Int,
    onSelectStickerPack: (Int) -> Unit,
    onStickerClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    onStickerDisplayed: (Int, String?) -> Unit = { _, _ -> },
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        tonalElevation = 3.dp,
    ) {
        Column {
            // Шапка: чипы-табы слева + «Закрыть» справа.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiStickerTabChip(
                    label = "Смайлы",
                    emoji = "😀",
                    selected = tab == 0,
                    onClick = { onTabChange(0) },
                )
                Spacer(Modifier.width(6.dp))
                EmojiStickerTabChip(
                    label = "Стикеры",
                    emoji = "😐",
                    selected = tab == 1,
                    onClick = { onTabChange(1) },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }

            when (tab) {
                0 -> {
                    // Сетка эмодзи (8 колонок).
                    LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    ) {
                        gridItems(emojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { emojiOnClick(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
                1 -> {
                    // Стикеры: pack-tabs (горизонтальный скролл) + сетка.
                    val currentStickers = stickerPacks.getOrNull(selectedStickerPack)?.stickers ?: emptyList()
                    if (stickerPacks.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            stickerPacks.forEachIndexed { idx, pack ->
                                val iconUrl = pack.icon?.url
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (idx == selectedStickerPack)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable { onSelectStickerPack(idx) }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (iconUrl != null) {
                                        AsyncImage(model = iconUrl, contentDescription = pack.title,
                                            modifier = Modifier.size(24.dp))
                                    } else {
                                        Text(text = pack.title.take(1),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (stickerLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (currentStickers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Нет стикеров",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            gridItems(currentStickers, key = { it.stickerId }) { sticker ->
                                val url = sticker.displayUrl
                                // Fix #229: предпочтительно анимированный URL (GIF/WebP). Coil с
                                // GifDecoder/AnimatedWebPDecoder (зарегистрированы в SovaApp)
                                // проиграет анимацию. Lottie (.json/.tgs) отфильтрован в модели
                                // (animatedDisplayUrl вернёт null) → fallback на статичный url.
                                val renderUrl = sticker.animatedDisplayUrl ?: url
                                // Fix #222/#225: предзагрузка стикера в офлайн-кеш при отображении.
                                // Кешируем sendImageUrl (прозрачный PNG, без фона) — именно он
                                // используется при отправке как картинка. displayUrl (с фоном)
                                // используется только для отображения в пикере через AsyncImage.
                                // Срабатывает один раз на каждый стикер (LaunchedEffect keyed by stickerId).
                                val preloadUrl = sticker.sendImageUrl ?: url
                                LaunchedEffect(sticker.stickerId, preloadUrl) {
                                    onStickerDisplayed(sticker.stickerId, preloadUrl)
                                }
                                if (renderUrl != null) {
                                    // Fix #221: визуальная индикация состояния стикера.
                                    //   active=true, purchased=true → обычный стикер
                                    //   active=false (деактивирован VK) → alpha 0.55 + badge 📷
                                    //     (будет отправлен как картинка)
                                    //   purchased=false (не куплен) → alpha 0.4 + badge 🔒
                                    //     (нельзя отправить, только посмотреть)
                                    val currentPack = stickerPacks.getOrNull(selectedStickerPack)
                                    val isActive = currentPack?.active != false
                                    val isPurchased = currentPack?.purchased != false
                                    val dimAlpha = when {
                                        !isPurchased -> 0.4f
                                        !isActive -> 0.55f
                                        else -> 1f
                                    }
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onStickerClick(sticker.stickerId) }
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AsyncImage(
                                            model = renderUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .graphicsLayer(alpha = dimAlpha),
                                        )
                                        // Fix #229: бейдж ▶ для анимированных стикеров
                                        // (видно, что стикер заиграет при отправке/в чате).
                                        // Fix #233 (sticker-badge): ранее Text("▶", fontSize=8.sp) —
                                        // 8.sp ≈ 17px на телефоне, почти невидно. Теперь Icon(PlayArrow)
                                        // 14.dp в цветном круге — чётко виден.
                                        if (sticker.isAnimated && isActive && isPurchased) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                                        shape = RoundedCornerShape(50),
                                                    )
                                                    .padding(2.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Анимированный стикер",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                            }
                                        }
                                        // Badge: 📷 (отправится как картинка) или 🔒 (платный).
                                        if (!isActive || !isPurchased) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                        shape = RoundedCornerShape(4.dp),
                                                    )
                                                    .padding(horizontal = 3.dp, vertical = 1.dp),
                                            ) {
                                                Text(
                                                    text = if (!isPurchased) "🔒" else "📷",
                                                    fontSize = 9.sp,
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
    }
}

/**
 * Fix #201: кастомный чип-таб для переключения между Смайлами и Стикерами.
 * Не TabRow — компактнее (одна строка, маленькая высота), как просил юзер.
 * Selected → primaryContainer/onPrimaryContainer, иначе surfaceVariant.
 */
@Composable
private fun EmojiStickerTabChip(
    label: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// P0.3: PinnedMessageBar — bar над message list показывает закреплённое сообщение.
// ══════════════════════════════════════════════════════════════════════

/**
 * Bar над сообщениями — показывает закреплённое сообщение.
 *
 * - Текст сообщения (truncated до 1 строки)
 * - Аватар отправителя (если есть в [profiles] — но здесь не передаём,
 *   показываем просто 📌 icon)
 * - Кнопка X (открепить)
 * - Тап по bar → скролл к закреплённому сообщению в списке
 *
 * Аналог m.vk.ru: `<div class="pinnedMessage__root">` — flat layout, без bubble.
 */
@Composable
private fun PinnedMessageBar(
    message: Message,
    onUnpin: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Закреплённое сообщение",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                val preview = if (message.text.isNotBlank()) {
                    message.text
                } else {
                    // Для сообщений без текста (только вложения) — показать тип вложения.
                    val att = message.attachments?.firstOrNull()
                    when (att?.type) {
                        "photo" -> "📷 Фото"
                        "video" -> "🎥 Видео"
                        "audio" -> "🎵 Аудио"
                        "doc" -> "📄 Документ"
                        "sticker" -> "🎨 Стикер"
                        "wall" -> "📝 Запись"
                        "gift" -> "🎁 Подарок"
                        "link" -> "🔗 Ссылка"
                        else -> "Вложение"
                    }
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onUnpin,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Открепить",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * P3.4: ChannelFooterBar — нижняя панель для каналов (broadcast-сообществ).
 *
 * Канал = диалог где пользователь не может писать (conversation.can_write.allowed == false).
 * Это происходит в сообществах с отключёнными сообщениями или где пользователь не админ.
 * Вместо composer показывается:
 *   - Иконка + текст состояния подписки/уведомлений канала
 *   - Кнопка «Включить/Выключить уведомления» (#IM-CHANNEL-FIX 56-b-5:
 *     messages.allowMessagesFromGroup / messages.denyMessagesFromGroup —
 *     заменили прежний toggleMute/messages.setConversationPushSettings,
 *     применимость которого к каналам не доказана; семантика VK web —
 *     снапшот 55: футер vkme_channel_footer_enable_notifications)
 *   - Кнопка «Покинуть» (Delete) — с confirmation dialog (разрушительное действие)
 *
 * Аналог m.vk.ru: канал показывает footer «Вы подписаны на канал» без поля ввода.
 * Leave = groups.leave + messages.deleteConversation (диалог исчезает из списка).
 */
@Composable
private fun ChannelFooterBar(
    notificationsEnabled: Boolean,
    onToggleNotifications: () -> Unit,
    onLeave: () -> Unit,
) {
    var showLeaveDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (notificationsEnabled) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (notificationsEnabled) "Вы подписаны — уведомления включены" else "Вы подписаны — уведомления выключены",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // #IM-CHANNEL-FIX (56-b-5): включить/выключить уведомления канала
            // (вместо прежнего mute-тумблера). Обработчик — toggleChannelNotifications
            // в ChatDetailScreen (allow/denyMessagesFromGroup + кэш SovaPrefs +
            // синхронизация MessageNotifier).
            IconButton(onClick = onToggleNotifications) {
                Icon(
                    imageVector = if (notificationsEnabled) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                    contentDescription = if (notificationsEnabled) "Выключить уведомления" else "Включить уведомления",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // leave — отписка от сообщества + удаление диалога (с подтверждением).
            IconButton(onClick = { showLeaveDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Покинуть канал",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Покинуть канал?") },
            text = {
                Text(
                    "Вы отпишетесь от сообщества и диалог исчезнет из списка. " +
                        "Вы сможете снова подписаться позже.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        onLeave()
                    },
                ) {
                    Text("Покинуть", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

/**
 * Fix #393 #CHANNEL-WALL-MODE: «N подписчиков» в статусе шапки канала
 * (снапшот 29-a: заголовок канала + число подписчиков). Русская плюрализация.
 */
private fun subscribersLabel(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "подписчик"
        mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> "подписчика"
        else -> "подписчиков"
    }
    return "$count $word"
}

/**
 * #CHANNEL-INFO-PANEL (2026-10-03): построение публичной ссылки на канал.
 * Если у сообщества есть screen_name (уже в GroupInfo.screenName из
 * groupsGetById) — используем его: «vk.ru/имя». Иначе фолбэк на числовой
 * идентификатор: «vk.ru/club<abs(peerId)>» (peerId канала отрицательный).
 */
private fun channelCommunityUrl(group: re.pinok.api.VKApiClient.GroupInfo?, peerId: Long): String {
    val name = group?.screenName
    return if (!name.isNullOrBlank()) "vk.ru/$name" else "vk.ru/club${kotlin.math.abs(peerId)}"
}

/**
 * #CHANNEL-INFO-PANEL (2026-10-03): контент панели канала (аватар, название,
 * подписчики, «Открыть сообщество»/«Написать», ссылка и разделы
 * Фото/Видео/Музыка/Файлы). Рендерится внутри ModalBottomSheet.
 *
 * Разделы: «Фотографии» — фото-лента канала (ChannelPhotoFeedDialog,
 * photosPhotoFeedList, #CHANNEL-PHOTOS-FEED). «Видео» — ChannelVideoDialog
 * (videoGet с ownerId), «Музыка» — ChannelAudioDialog (audioGetWithCount),
 * «Файлы» — ChannelFilesDialog (docsGet с ownerId) — все #CHANNEL-SECTIONS
 * (2026-10-04). Клик по разделу вызывает onSectionClick, а вызывающая
 * сторона открывает соответствующую канальную панель (ownerId=-abs(peerId)).
 *
 * @param group         метаданные канала из loadChannelMeta (может быть null,
 *                      если groupsGetById ещё не ответил или не удался)
 * @param peerId        отрицательный peerId канала
 * @param channelTitle  имя канала (currentTitle)
 * @param channelPhoto  аватар канала (currentPhoto)
 * @param subscribers   число подписчиков (-1 = ещё не получены)
 * @param onOpenCommunity открыть сообщество (через onUrlClick во внутреннем
 *                        или внешнем браузере по vk.ru/<…>)
 * @param onWrite       «Написать» — фокусировка диалога канала
 * @param onCopyLink    скопировать ссылку на канал в буфер
 * @param onSectionClick клик по разделу (Toast «Раздел … скоро»)
 */
@Composable
private fun ChannelInfoPanelContent(
    group: re.pinok.api.VKApiClient.GroupInfo?,
    peerId: Long,
    channelTitle: String,
    channelPhoto: String?,
    subscribers: Int,
    onOpenCommunity: () -> Unit,
    onWrite: () -> Unit,
    onCopyLink: (String) -> Unit,
    onSectionClick: (String) -> Unit,
) {
    val linkUrl = channelCommunityUrl(group, peerId)
    val ava = channelPhoto ?: group?.photo200 ?: group?.photo100

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Аватар (круг) + название + подписчики.
        if (ava != null) {
            AsyncImage(
                model = ava,
                contentDescription = channelTitle,
                modifier = Modifier.size(72.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = channelTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        if (subscribers >= 0) {
            Text(
                text = subscribersLabel(subscribers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(2.dp))
        }
        // Ссылка на канал (клик — копирование в буфер).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onCopyLink(linkUrl) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = linkUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Кнопки «Открыть сообщество» / «Написать».
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onOpenCommunity,
                modifier = Modifier.weight(1f),
            ) {
                Text("Открыть сообщество")
            }
            Button(
                onClick = onWrite,
                modifier = Modifier.weight(1f),
            ) {
                Text("Написать")
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Разделы: Фото/Видео/Музыка/Файлы.
        Text(
            text = "Разделы",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        ChannelInfoSectionsGrid(
            onSectionClick = onSectionClick,
        )
    }
}

/** #CHANNEL-INFO-PANEL: элемент раздела канала (Фото/Видео/Музыка/Файлы). */
@Composable
private fun ChannelInfoSectionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** #CHANNEL-INFO-PANEL: сетка разделов канала 2×2 + третья строка «Клипы»
 *  (Фото/Видео/Музыка/Файлы/Клипы). P0.4 (2026-10-04): добавлена кнопка
 *  «Клипы» (Icons.Filled.PlayCircle) → onSectionClick("Клипы"), роутится в
 *  ChannelClipsDialog через when(section) (P0.5).
 */
@Composable
private fun ChannelInfoSectionsGrid(
    onSectionClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChannelInfoSectionItem(
                label = "Фотографии",
                icon = Icons.Outlined.Image,
                modifier = Modifier.weight(1f),
                onClick = { onSectionClick("Фотографии") },
            )
            ChannelInfoSectionItem(
                label = "Видео",
                icon = Icons.Outlined.VideoFile,
                modifier = Modifier.weight(1f),
                onClick = { onSectionClick("Видео") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChannelInfoSectionItem(
                label = "Музыка",
                icon = Icons.Outlined.MusicNote,
                modifier = Modifier.weight(1f),
                onClick = { onSectionClick("Музыка") },
            )
            ChannelInfoSectionItem(
                label = "Файлы",
                icon = Icons.Outlined.Description,
                modifier = Modifier.weight(1f),
                onClick = { onSectionClick("Файлы") },
            )
        }
        // P0.4 (2026-10-04): «Клипы» — shortVideo.getOwnerVideos(ownerId=-groupId).
        // Иконка PlayCircle — фрейм видео, как в CommunityScreen.ClipThumbnail.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChannelInfoSectionItem(
                label = "Клипы",
                icon = Icons.Filled.PlayCircle,
                modifier = Modifier.weight(1f),
                onClick = { onSectionClick("Клипы") },
            )
        }
    }
}

/** #CHANNEL-PHOTOS (2026-10-03): панель «Фотографии» канала — список альбомов
 *  группы и сетка фото выбранного альбома. Открывается по клику на раздел
 *  «Фотографии» панели канала. ModalBottomSheet (как и панель канала).
 *
 *  Уровни:
 *   1. Список альбомов (photosGetAlbums): обложка (thumbSrc) / title / N фото.
 *   2. По клику на альбом — сетка фото (photosGet, 3 колонки, миниатюры
 *      mediumUrl); тап по фото → onPhotoClick(список URL альбома, индекс) —
 *      вызывающая сторона открывает существующий PhotoViewer.
 *
 * @param apiClient    VKApiClient (photosGetAlbums / photosGet)
 * @param ownerId      владелец альбомов (=-group_id, отрицательный id канала)
 * @param onDismiss    закрыть панель (кнопка «×» / системный back)
 * @param onPhotoClick тап по фото — [список URL, индекс] для PhotoViewer
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChannelPhotosDialog(
    apiClient: re.pinok.api.VKApiClient,
    ownerId: Long,
    onDismiss: () -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    // Системный back закрывает панель (паттерн ChannelSearchPanel).
    BackHandler { onDismiss() }

    // Состояние уровней: selectedAlbum=null → список альбомов, иначе сетка фото.
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(true) }
    var albumsError by remember { mutableStateOf<String?>(null) }
    // Счётчики для «Повторить»: переключение перезапускает LaunchedEffect.
    var albumsSeq by remember { mutableStateOf(0) }
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var photosLoading by remember { mutableStateOf(false) }
    var photosError by remember { mutableStateOf<String?>(null) }
    var photosSeq by remember { mutableStateOf(0) }

    // Загрузка списка альбомов (один раз при открытии + по «Повторить»).
    LaunchedEffect(ownerId, albumsSeq) {
        albumsLoading = true
        albumsError = null
        try {
            albums = apiClient.photosGetAlbums(ownerId = ownerId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "#CHANNEL-PHOTOS photosGetAlbums failed", e)
            albumsError = apiClient.lastApiError ?: e.message
        } finally {
            albumsLoading = false
        }
    }

    // Загрузка фото выбранного альбома (перезапуск при смене альбома / «Повторить»).
    LaunchedEffect(selectedAlbum, photosSeq) {
        val album = selectedAlbum ?: return@LaunchedEffect
        photosLoading = true
        photosError = null
        try {
            photos = apiClient.photosGet(
                ownerId = ownerId,
                albumId = album.id.toString(),
                count = 200,
                offset = 0,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "#CHANNEL-PHOTOS photosGet failed", e)
            photosError = apiClient.lastApiError ?: e.message
        } finally {
            photosLoading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val album = selectedAlbum
        when {
            // ← Загрузка альбомов.
            albumsLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            // ← Ошибка загрузки альбомов.
            albumsError != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                Text("Не удалось загрузить альбомы", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = albumsError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { albumsSeq++ }) { Text("Повторить") }
                Spacer(Modifier.height(16.dp))
            }
            // ← Выбран альбом: сетка фото.
            album != null -> ChannelPhotosAlbumContent(
                album = album,
                photos = photos,
                photosLoading = photosLoading,
                photosError = photosError,
                onBack = { selectedAlbum = null },
                onPhotoClick = onPhotoClick,
                onRetry = { photosSeq++ },
            )
            // ← Список альбомов.
            else -> ChannelPhotosAlbumsList(
                albums = albums,
                onAlbumClick = { selectedAlbum = it },
                onDismiss = onDismiss,
            )
        }
    }
}

/** #CHANNEL-PHOTOS: список альбомов группы (заголовок + строки альбомов). */
@Composable
private fun ChannelPhotosAlbumsList(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        // Заголовок «Фотографии» + «×» (закрыть).
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Фотографии",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть")
            }
        }
        if (albums.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "В сообществе нет альбомов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(albums, key = { "album_${it.id}" }) { album ->
                    ChannelPhotosAlbumRow(album = album, onClick = { onAlbumClick(album) })
                }
            }
        }
    }
}

/** #CHANNEL-PHOTOS: строка альбома (обложка / заголовок / N фото). */
@Composable
private fun ChannelPhotosAlbumRow(
    album: Album,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = album.thumbSrc
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = album.title,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = channelPhotoCountLabel(album.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** #CHANNEL-PHOTOS: контент выбранного альбома — «← назад» + сетка фото. */
@Composable
private fun ChannelPhotosAlbumContent(
    album: Album,
    photos: List<PhotoItem>,
    photosLoading: Boolean,
    photosError: String?,
    onBack: () -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        // Заголовок: «←» возврат к альбомам + название альбома.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад к альбомам")
            }
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            photosLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            photosError != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Не удалось загрузить фото", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = photosError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
            photos.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "В альбоме нет фотографий",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            else -> {
                // Полный список URL для просмотрщика (PhotoViewer).
                val urls = photos.mapNotNull { it.largestUrl }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    gridItems(photos, key = { "channel_photo_${it.id}" }) { photo ->
                        val url = photo.mediumUrl
                        if (url != null) {
                            ChannelPhotoGridItem(
                                url = url,
                                onClick = {
                                    val idx = photos.indexOf(photo).coerceAtLeast(0)
                                    onPhotoClick(urls, idx)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** #CHANNEL-PHOTOS: миниатюра фото в сетке (1:1, кроп). */
@Composable
private fun ChannelPhotoGridItem(
    url: String,
    onClick: () -> Unit,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop,
    )
}

/** #CHANNEL-PHOTOS: русская плюрализация числа фото в альбоме. */
private fun channelPhotoCountLabel(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "$count фотография"
        mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> "$count фотографии"
        else -> "$count фотографий"
    }
}

/** #CHANNEL-PHOTOS-FEED (2026-10-04, P0.6): панель «Фотографии» канала —
 *  фото сообщества через photosGet(albumId="wall", ownerId=-abs(peerId)),
 *  offset-пагинация. Сетка 3 колонки, тап по фото → onPhotoClick(список URL,
 *  индекс) для PhotoViewer. ModalBottomSheet.
 *
 *  P0.6 (2026-10-04): БЫЛО photosPhotoFeedList (photos.photoFeedGet — ЛИЧНАЯ
 *  фотолента пользователя, работает только для owner_id>0; для канала
 *  ownerId<0 VK возвращал пусто → пользователь видел «В сообществе нет
 *  фотографий» даже когда фото есть). СТАЛО photosGet(albumId="wall") — фото
 *  сообщества (стена), как в CommunityScreen.kt:363 и мёртвом ChannelPhotosDialog
 *  (теперь redundant). Источник: VKApiClient.photosGet (9865).
 *
 * @param apiClient    VKApiClient (photosGet)
 * @param ownerId      владелец фото (=-group_id, отрицательный id канала)
 * @param onDismiss    закрыть панель (кнопка «×» / системный back)
 * @param onPhotoClick тап по фото — [список URL, индекс] для PhotoViewer
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChannelPhotoFeedDialog(
    apiClient: re.pinok.api.VKApiClient,
    ownerId: Long,
    onDismiss: () -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    BackHandler { onDismiss() }

    // P0.6: photosGet возвращает List<PhotoItem> (без cursor), пагинация offset'ом.
    // hasMore=true пока VK отдаёт полную страницу (pageSize) — как только меньше,
    // это последняя страница. offset сдвигается на размер полученной страницы.
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var offset by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Счётчик для «Повторить»: переключение перезапускает начальный LaunchedEffect.
    var seq by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val pageSize = 40

    fun loadFirst() {
        scope.launch {
            loading = true
            error = null
            try {
                val page = apiClient.photosGet(
                    ownerId = ownerId,
                    albumId = "wall",
                    count = pageSize,
                    offset = 0,
                )
                photos = page.distinctBy { "${it.ownerId}_${it.id}" }
                offset = page.size
                hasMore = page.size == pageSize
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "#CHANNEL-PHOTOS-FEED photosGet failed", e)
                error = apiClient.lastApiError ?: e.message
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = apiClient.photosGet(
                    ownerId = ownerId,
                    albumId = "wall",
                    count = pageSize,
                    offset = offset,
                )
                val distinct = page.distinctBy { "${it.ownerId}_${it.id}" }
                photos = (photos + distinct).distinctBy { "${it.ownerId}_${it.id}" }
                offset += page.size
                hasMore = page.size == pageSize
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "#CHANNEL-PHOTOS-FEED load more failed", e)
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(ownerId, seq) { loadFirst() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Заголовок «Фотографии» + «×» (закрыть).
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Фотографии",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                }
            }
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Не удалось загрузить фотографии", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { seq++ }) { Text("Повторить") }
                }
                photos.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "В сообществе нет фотографий",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                else -> {
                    // Полный список URL для просмотрщика (PhotoViewer).
                    val urls = photos.mapNotNull { it.largestUrl }
                    LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        gridItems(photos, key = { "chfeed_${it.ownerId}_${it.id}" }) { photo ->
                            val url = photo.mediumUrl
                            if (url != null) {
                                ChannelPhotoGridItem(
                                    url = url,
                                    onClick = {
                                        val idx = photos.indexOf(photo).coerceAtLeast(0)
                                        onPhotoClick(urls, idx)
                                    },
                                )
                            }
                        }
                        // Футер-пагинация: автоматически тянет следующую страницу.
                        if (hasMore || loadingMore) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                LaunchedEffect(hasMore, offset) {
                                    loadMore()
                                }
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** #CHANNEL-VIDEO (2026-10-04): панель «Видео» канала — videoGet(ownerId),
 *  вертикальный список VideoThumbnail (переиспользуется public-компонент
 *  ProfileScreen.VideoThumbnail). Тап по видео → onVideoClick (воспроизведение).
 *  ModalBottomSheet, системный back закрывает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelVideoDialog(
    apiClient: re.pinok.api.VKApiClient,
    ownerId: Long,
    onDismiss: () -> Unit,
    onVideoClick: (Video) -> Unit,
) {
    BackHandler { onDismiss() }

    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var seq by remember { mutableStateOf(0) }

    LaunchedEffect(ownerId, seq) {
        loading = true
        error = null
        try {
            videos = apiClient.videoGet(ownerId = ownerId, count = 30, offset = 0)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "#CHANNEL-VIDEO videoGet failed", e)
            error = apiClient.lastApiError ?: e.message
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Заголовок «Видео» + «×» (закрыть).
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Видео",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                }
            }
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Не удалось загрузить видео", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { seq++ }) { Text("Повторить") }
                }
                videos.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "В сообществе нет видео",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(videos, key = { "${it.ownerId}_${it.id}" }) { video ->
                        // Reuse public ProfileScreen.VideoThumbnail (16:9 карточка).
                        VideoThumbnail(video = video, onClick = onVideoClick)
                    }
                }
            }
        }
    }
}

/** #CHANNEL-CLIPS (P0.5, 2026-10-04): панель «Клипы» канала — shortVideo.
 *  getOwnerVideos(ownerId=-abs(peerId)), вертикальные 9:16 постеры в
 *  горизонтальном LazyRow (как лента клипов). Тап → onVideoClick (воспроизведение
 *  клипа через VideoHolder.open / VideoPlatformRouter — тот же путь, что для
 *  обычного видео CommunityScreen). ModalBottomSheet, системный back закрывает.
 *
 *  Образец: ChannelVideoDialog (videoGet + VideoThumbnail 16:9). Здесь —
 *  shortVideoGetOwnerVideos + ChannelClipThumbnail 9:16 (как CommunityScreen:
 *  ClipThumbnail, но локально, т.к. CommunityScreen.ClipThumbnail private).
 *
 * @param apiClient   VKApiClient (shortVideoGetOwnerVideos, VKApiClient.kt:18093)
 * @param ownerId     владелец клипов (=-group_id, отрицательный id канала)
 * @param onDismiss   закрыть панель (кнопка «×» / системный back)
 * @param onVideoClick тап по клипу → onVideoClick(Video)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelClipsDialog(
    apiClient: re.pinok.api.VKApiClient,
    ownerId: Long,
    onDismiss: () -> Unit,
    onVideoClick: (Video) -> Unit,
) {
    BackHandler { onDismiss() }

    var clips by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Счётчик для «Повторить»: переключение перезапускает LaunchedEffect.
    var seq by remember { mutableStateOf(0) }

    LaunchedEffect(ownerId, seq) {
        loading = true
        error = null
        try {
            clips = apiClient.shortVideoGetOwnerVideos(ownerId = ownerId, count = 30)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "#CHANNEL-CLIPS shortVideoGetOwnerVideos failed", e)
            error = apiClient.lastApiError ?: e.message
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Заголовок «Клипы» + «×» (закрыть).
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Клипы",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                }
            }
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Не удалось загрузить клипы", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { seq++ }) { Text("Повторить") }
                }
                clips.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "В сообществе нет клипов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                // Горизонтальная лента 9:16 постеров (как VK web/clips-app).
                else -> LazyRow(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(clips, key = { "chclip_${it.ownerId}_${it.id}" }) { clip ->
                        ChannelClipThumbnail(video = clip, onClick = onVideoClick)
                    }
                }
            }
        }
    }
}

/** #CHANNEL-CLIPS: вертикальный 9:16 постер клипа (clipPosterUrl → thumbUrl
 *  fallback), кнопка-play overlay + название клипа снизу. Тап → onClick(video)
 *  → onVideoClick → VideoHolder.open (нативный плеер с fallback к videoGetById
 *  при пустых files). Уменьшенная копия CommunityScreen.ClipThumbnail
 *  (CommunityScreen.kt:1881, private там — копируем локально).
 */
@Composable
private fun ChannelClipThumbnail(
    video: Video,
    onClick: (Video) -> Unit,
) {
    val thumbUrl = video.clipPosterUrl ?: video.thumbUrl
    Card(
        modifier = Modifier
            .width(140.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(video) },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbUrl != null) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Play-button overlay.
            Box(
                modifier = Modifier.size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            // Название клипа снизу (как в clips-сетке VK).
            if (video.title.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** #CHANNEL-AUDIO (2026-10-04): панель «Музыка» канала — audioGetWithCount
 *  (ownerId), рендер переиспользуемым AudioAttachmentList (сам запускает
 *  PlayerConnection.playTrackList по тапу). ModalBottomSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelAudioDialog(
    apiClient: re.pinok.api.VKApiClient,
    ownerId: Long,
    onDismiss: () -> Unit,
) {
    BackHandler { onDismiss() }

    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var seq by remember { mutableStateOf(0) }

    LaunchedEffect(ownerId, seq) {
        loading = true
        error = null
        try {
            val (_, list) = apiClient.audioGetWithCount(count = 100, offset = 0, ownerId = ownerId)
            tracks = list
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "#CHANNEL-AUDIO audioGetWithCount failed", e)
            error = apiClient.lastApiError ?: e.message
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Заголовок «Музыка» + «×» (закрыть).
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Музыка",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                }
            }
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Не удалось загрузить музыку", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { seq++ }) { Text("Повторить") }
                }
                tracks.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "В сообществе нет музыки",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                ) {
                    // Reuse public AudioAttachmentList (список треков, тап → плеер).
                    item { AudioAttachmentList(tracks = tracks) }
                }
            }
        }
    }
}

/** #CHANNEL-FILES (2026-10-04): панель «Файлы» канала — docsGet(ownerId),
 *  список документов (заголовок / EXT • размер • тип), тап или кнопка →
 *  скачивание через системный DownloadManager (как DocumentsScreen.DocRow).
 *  ModalBottomSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelFilesDialog(
    apiClient: re.pinok.api.VKApiClient,
    ownerId: Long,
    onDismiss: () -> Unit,
) {
    BackHandler { onDismiss() }

    var docs by remember { mutableStateOf<List<DocFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var seq by remember { mutableStateOf(0) }

    LaunchedEffect(ownerId, seq) {
        loading = true
        error = null
        try {
            docs = apiClient.docsGet(count = 100, offset = 0, ownerId = ownerId)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "#CHANNEL-FILES docsGet failed", e)
            error = apiClient.lastApiError ?: e.message
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Заголовок «Файлы» + «×» (закрыть).
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Файлы",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                }
            }
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Не удалось загрузить документы", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { seq++ }) { Text("Повторить") }
                }
                docs.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "В сообществе нет документов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(docs, key = { "chdoc_${it.ownerId}_${it.id}" }) { doc ->
                        ChannelFileRow(doc = doc)
                    }
                }
            }
        }
    }
}

/** #CHANNEL-FILES: строка документа (иконка по типу / заголовок / «EXT • размер» /
 *  тап или кнопка → DownloadManager). Уменьшенная копия DocumentsScreen.DocRow —
 *  маленький локальный ряд без большого дублирования.
 */
@Composable
private fun ChannelFileRow(doc: DocFile) {
    val context = LocalContext.current

    fun downloadDoc() {
        val url = doc.url
        if (url.isBlank()) {
            Toast.makeText(context, "Ссылка на файл недоступна — обновите список", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val safeTitle = doc.title.ifBlank { "vk_doc_${doc.id}" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$safeTitle.${doc.ext}"
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Документы VK")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "VK/$fileName")
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            if (dm == null) {
                Toast.makeText(context, "Загрузка недоступна на этом устройстве", Toast.LENGTH_SHORT).show()
                return
            }
            dm.enqueue(request)
            Toast.makeText(context, "Загрузка началась: $fileName", Toast.LENGTH_SHORT).show()
            AppLog.i("ChatDetailScreen", "Channel doc download enqueued: $fileName (${doc.sizeLabel})")
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "Channel doc download failed", e)
            Toast.makeText(context, "Не удалось скачать: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { downloadDoc() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    doc.isImage -> Icons.Outlined.Image
                    doc.isGif -> Icons.Outlined.Image
                    doc.ext.lowercase() == "pdf" -> Icons.Outlined.PictureAsPdf
                    doc.type == 6 -> Icons.Outlined.Movie
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${doc.ext.uppercase()} • ${doc.sizeLabel} • ${doc.typeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = { downloadDoc() }) {
            Icon(Icons.Outlined.Download, contentDescription = "Скачать")
        }
    }
}

/**
 * 32-b: расширенный формат «был(а) …» для шапки диалога — до 15 минут
 * относительное время, дальше время и дата (как у VK web):
 *   < 60 сек        → «был(а) только что»
 *   < 15 мин        → «был(а) N мин назад» (N = 1..14)
 *   сегодня         → «был(а) в HH:mm»
 *   вчера           → «был(а) вчера в HH:mm»
 *   иначе           → «был(а) DD.MM.YYYY в HH:mm»
 * lastSeen/now — unix-секунды; форматирование локальной таймзоной
 * устройства. SimpleDateFormat+Calendar, а не java.time: minSdk 24
 * (java.time недоступен без coreLibraryDesugaring).
 */
private fun formatLastSeenExtended(lastSeenSec: Long, nowSec: Long): String {
    val diff = nowSec - lastSeenSec
    if (diff < 60) return "был(а) только что"
    if (diff < 15 * 60) return "был(а) ${diff / 60} мин назад"

    val seenCal = Calendar.getInstance()
    seenCal.timeInMillis = lastSeenSec * 1000
    val nowCal = Calendar.getInstance()
    nowCal.timeInMillis = nowSec * 1000

    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val time = timeFmt.format(Date(lastSeenSec * 1000))

    val sameDay = seenCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
        seenCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "был(а) в $time"

    // «Вчера» — календарный день перед сегодняшним; Calendar.add сдвигает
    // дату целиком, переходы месяца/года корректны (1 янв → 31 дек).
    val yesterdayCal = Calendar.getInstance()
    yesterdayCal.timeInMillis = nowSec * 1000
    yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = seenCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
        seenCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "был(а) вчера в $time"

    val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val date = dateFmt.format(Date(lastSeenSec * 1000))
    return "был(а) $date в $time"
}

/**
 * Fix #394 #IM-SEARCH: правая панель «Поиск по постам» канального режима
 * (снапшот 29-a: ChannelSearch__container, лупа «Поиск по каналу» в шапке,
 * поле «Поиск по истории записей», результаты SearchPostResult). Оверлей
 * scrim+slide — образец FeedRightPanel (#FEED-MENU-VKWEB).
 *
 * Серверный поиск: VKApiClient.wallSearch(ownerId, query) (реальный VK API
 * wall.search; посты сообщества = контент канала, см. #CHANNEL-WALL-MODE).
 * Результаты — полные WallPostCard (лайк/комментарий/поделиться работают
 * как в ленте канала). Тап по карточке → onPostOpen (скролл к посту).
 *
 * Честные состояния: hint (пустой запрос) / загрузка / ошибка + «Повторить» /
 * «Ничего не найдено». История запросов (аналог localStorage
 * reforged-storage-db-v1-*-search-channel-posts-requests) — out of scope,
 * честно не реализована (не заглушка — просто отсутствует).
 *
 * @param visible      открыта ли панель (state ChatDetailScreen.showChannelSearch)
 * @param ownerId      peerId канала (=-group_id) → wall.search owner_id
 * @param channelTitle имя канала (подзаголовок панели + authorName карточек)
 * @param channelPhoto аватар канала (authorPhoto карточек)
 * @param onDismiss    закрыть панель (Scrim / «×» / системный back)
 * @param onPostOpen   тап по результату — вызывающая сторона закрывает панель
 *                     и скроллит ленту к посту (scrollChannelToPost)
 * @param likesState/likeInFlight/onLikeToggle — optimistic-лайки экрана канала
 *                     (общие с лентой, чтобы состояние не расходилось)
 */
@Composable
private fun ChannelSearchPanel(
    visible: Boolean,
    ownerId: Long,
    channelTitle: String,
    channelPhoto: String?,
    onDismiss: () -> Unit,
    onPostOpen: (Post) -> Unit,
    onVideoClick: (Video) -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,
    onSharePost: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    likesState: Map<String, Pair<Boolean, Int>>,
    likeInFlight: Map<String, Boolean>,
    onLikeToggle: (Post) -> Unit,
) {
    // Системный back закрывает панель (паттерн FeedRightPanel).
    BackHandler(enabled = visible) { onDismiss() }

    // Состояние поиска живёт внутри панели: переоткрытие сбрасывает выдачу
    // (remember(visible) — новая сессия поиска на каждое открытие).
    var searchQuery by remember(visible) { mutableStateOf("") }
    var results by remember(visible) { mutableStateOf<List<Post>>(emptyList()) }
    var searching by remember(visible) { mutableStateOf(false) }
    var errorMsg by remember(visible) { mutableStateOf<String?>(null) }
    // Счётчик запросов: на каждый новый ввод LaunchedEffect перезапускается;
    // ответ «старой» попытки отбрасывается (race-guard без elvis).
    var searchSeq by remember(visible) { mutableStateOf(0) }
    val app = SovaApp.get()

    // Дебаунс 600мс — как в диалоге поиска сообщений (#IM-SEARCH).
    LaunchedEffect(searchQuery) {
        val seq = ++searchSeq
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            results = emptyList()
            errorMsg = null
            searching = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(600)
        if (seq != searchSeq) return@LaunchedEffect
        searching = true
        errorMsg = null
        try {
            val found = app.apiClient.wallSearch(ownerId = ownerId, query = q, count = 30)
            if (seq == searchSeq) results = found
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("ChatDetailScreen", "#IM-SEARCH wall.search failed", e)
            if (seq == searchSeq) {
                val apiErr = app.apiClient.lastApiError
                errorMsg = if (apiErr != null) "Ошибка поиска: $apiErr" else "Ошибка поиска: ${e.message}"
            }
        } finally {
            if (seq == searchSeq) searching = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim: тап вне панели закрывает (паттерн FeedRightPanel).
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    // Заголовок панели: «Поиск по постам» + канал + «×»
                    // (снапшот 29-a: h3 «Поиск по постам», крестик «Закрыть»).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Поиск по постам",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (channelTitle.isNotBlank()) {
                                Text(
                                    text = channelTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть поиск")
                        }
                    }
                    // Поле поиска (снапшот 29-a: «Поиск по истории записей»).
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Поиск по истории записей") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Очистить")
                                }
                            } else {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            }
                        },
                    )
                    // ── Выдача ──
                    when {
                        searching -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        errorMsg != null -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val err = errorMsg
                                if (err != null) {
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = { searchSeq++ }) {
                                    Text("Повторить")
                                }
                            }
                        }
                        searchQuery.isNotBlank() && results.isEmpty() && !searching -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Ничего не найдено",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        results.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            ) {
                                items(results, key = { "search_${it.ownerId}_${it.id}" }) { post ->
                                    WallPostCard(
                                        post = post,
                                        authorName = channelTitle,
                                        authorPhoto = channelPhoto,
                                        onVideoClick = onVideoClick,
                                        onPostClick = onPostOpen,
                                        onPhotoClick = onPhotoClick,
                                        onRepostClick = onSharePost,
                                        onCommentClick = onCommentClick,
                                        likesState = likesState,
                                        likePending = likeInFlight.containsKey("${post.ownerId}_${post.id}"),
                                        onLikeToggle = onLikeToggle,
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                        // Пустой запрос → hint (снапшот 29-a: панель открывается
                        // с пустым полем и без выдачи).
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Введите текст для поиска по записям канала",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * P5.3: Создаёт временный URI для сохранения фото с камеры через FileProvider.
 * Возвращает null если FileProvider не настроен или нет кеш-директории.
 */
private fun createCameraImageUri(ctx: android.content.Context): android.net.Uri? {
    return try {
        val photoFile = java.io.File(ctx.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            photoFile,
        )
    } catch (e: Exception) {
        AppLog.e("ChatDetailScreen", "createCameraImageUri failed", e)
        null
    }
}

/**
 * Fix #126: Saver для Uri — позволяет rememberSaveable хранить Uri в Bundle.
 *
 * Без этого rememberSaveable не умеет сериализовать Uri → при process death
 * (когда камера убивает процесс приложения) cameraImageUri теряется и фото
 * не прикрепляется. Saver конвертирует Uri ↔ String.
 */
private val UriSaver: Saver<android.net.Uri?, String> = Saver(
    save = { it?.toString() ?: "" },
    restore = { saved -> if (saved.isBlank()) null else android.net.Uri.parse(saved) },
)

/**
 * Fix #120: Единый контроллер воспроизведения голосовых на весь чат.
 *
 * Раньше каждый VoiceMessageBubble имел свой собственный MediaPlayer (local
 * remember) → можно было запустить 5 голосовых одновременно, и они все играли
 * параллельно. "Утонуть в диалогах".
 *
 * Теперь один MediaPlayer на весь чат. Контроллер отслеживает currentMessageId.
 * При play(newId) автоматически stop() предыдущего. Только одно голосовое
 * играет в любой момент — как в нативном VK и VK Web.
 *
 * Состояние (currentMessageId, isPlaying, progress) — через Compose state,
 * чтобы все VoiceMessageBubble перерисовывались реактивно.
 */
private class VoicePlaybackController {
    // P0.26 #VOICE-EXOPLAYER (2026-10): ExoPlayer вместо MediaPlayer.
    // MediaPlayer НЕ умеет отправлять cookies → VK voice CDN (psv4.vkuserphoto.ru)
    // возвращает 403 → onPrepared не вызывается → таймер 0:00, не считает.
    // ExoPlayer + OkHttpDataSource.Factory(SovaApp.httpClient) = cookies + VK UA.
    private var player: ExoPlayer? = null
    private var progressJob: kotlinx.coroutines.Job? = null
    // Fix #237: альтернативный URL для fallback при ошибке воспроизведения.
    private var currentFallbackUrl: String? = null
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
    )

    /** ID сообщения, которое сейчас загружено/играет (или null). */
    var currentMessageId: Long? by mutableStateOf(null)
        private set

    /** true если currentMessageId активно воспроизводится (не на паузе). */
    var isPlaying: Boolean by mutableStateOf(false)
        private set

    /** 0..1 прогресс воспроизведения текущего сообщения. */
    var progress: Float by mutableFloatStateOf(0f)
        private set

    // P0.24 #VOICE-TIMER-FIX: currentPosition в мс напрямую из плеера.
    var currentPositionMs: Long by mutableLongStateOf(0L)
        private set

    /** Длительность текущего сообщения в секундах (для отображения). */
    var durationSec: Float by mutableFloatStateOf(0f)
        private set

    /**
     * Toggle play/pause/switch для сообщения [messageId] по URL [url].
     * Fix #237: [fallbackUrl] — альтернативный URL (OGG↔MP3).
     */
    fun toggle(
        messageId: Long,
        url: String,
        fallbackDurationSec: Float,
        fallbackUrl: String? = null,
    ) {
        // Тот же messageId → toggle play/pause.
        if (currentMessageId == messageId) {
            val p = player
            if (p != null) {
                if (isPlaying) {
                    try { p.pause() } catch (_: Exception) {}
                    isPlaying = false
                    stopProgressTracking()
                } else {
                    try { p.play() } catch (_: Exception) {}
                    isPlaying = true
                    startProgressTracking()
                }
                return
            }
        }

        // Другое сообщение (или то же, но player умер) → stop старого, start нового.
        releasePlayer()

        currentFallbackUrl = fallbackUrl?.takeIf { it.isNotBlank() && it != url }
        currentMessageId = messageId
        durationSec = fallbackDurationSec

        startPlayback(messageId, url, fallbackDurationSec)
    }

    /**
     * Fix #237: запуск воспроизведения по конкретному URL. Вынесено в
     * отдельный метод чтобы можно было переиспользовать при fallback.
     */
    private fun startPlayback(messageId: Long, url: String, fallbackDurationSec: Float) {
        try {
            val ctx = SovaApp.getOrNull()?.applicationContext
                ?: throw IllegalStateException("SovaApp not initialized")
            val vkUa = re.pinok.util.VkUserAgent.get(ctx as android.app.Application)
            // P0.10 #VIDEO-CDN-COOKIES: OkHttpDataSource с SovaApp.httpClient
            // (включает VkCookieJar → cookies для VK CDN). Fallback на
            // DefaultHttpDataSource если SovaApp null.
            val app = ctx as? SovaApp
            val httpFactory = if (app != null) {
                try {
                    OkHttpDataSource.Factory(app.httpClient)
                        .setUserAgent(vkUa)
                        .setDefaultRequestProperties(mapOf("Referer" to "https://m.vk.ru/"))
                } catch (e: Exception) {
                    AppLog.w("VoicePlayback", "OkHttpDataSource failed, fallback Default: ${e.message}")
                    DefaultHttpDataSource.Factory().setUserAgent(vkUa)
                }
            } else {
                DefaultHttpDataSource.Factory().setUserAgent(vkUa)
            }
            val dataSourceFactory = DefaultDataSource.Factory(ctx, httpFactory)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

            val ep = ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
            ep.setMediaItem(MediaItem.fromUri(url))
            ep.repeatMode = Player.REPEAT_MODE_OFF
            ep.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            // Длительность известна после prepare.
                            val dMs = ep.duration.coerceAtLeast(0L)
                            if (dMs > 0) {
                                durationSec = (dMs / 1000f).coerceAtLeast(fallbackDurationSec)
                            }
                        }
                        Player.STATE_ENDED -> {
                            isPlaying = false
                            progress = 0f
                            currentPositionMs = 0L
                            stopProgressTracking()
                        }
                        else -> {}
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing) {
                        progress = 0f
                        currentPositionMs = 0L
                        startProgressTracking()
                    } else {
                        stopProgressTracking()
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    AppLog.e("VoicePlayback", "ExoPlayer error url=$url: ${error.message}", error)
                    releasePlayer()
                    // Fix #237: пробуем fallback URL.
                    val fb = currentFallbackUrl
                    if (fb != null) {
                        AppLog.i("VoicePlayback", "Trying fallback URL: $fb")
                        currentFallbackUrl = null
                        currentMessageId = messageId
                        startPlayback(messageId, fb, fallbackDurationSec)
                    }
                }
            })
            ep.prepare()
            ep.playWhenReady = true
            player = ep
        } catch (e: Exception) {
            AppLog.e("VoicePlayback", "startPlayback error url=$url", e)
            releasePlayer()
        }
    }

    /**
     * Полностью остановить воспроизведение. Освобождает ExoPlayer.
     */
    fun stop() {
        releasePlayer()
    }

    /** true если [messageId] — текущее активное сообщение. */
    fun isCurrent(messageId: Long): Boolean = currentMessageId == messageId

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(50)
                val p = player ?: break
                try {
                    val d = p.duration.coerceAtLeast(1L)
                    val pos = p.currentPosition.coerceIn(0L, p.duration)
                    currentPositionMs = pos
                    progress = (pos.toFloat() / d.toFloat()).coerceIn(0f, 1f)
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releasePlayer() {
        stopProgressTracking()
        player?.let { p ->
            try { p.release() } catch (_: Exception) {}
        }
        player = null
        currentMessageId = null
        isPlaying = false
        progress = 0f
        currentPositionMs = 0L
        durationSec = 0f
    }

    /** Вызывать при выходе с экрана чата. */
    fun dispose() {
        releasePlayer()
        scope.cancel()
    }
}

// ============================================================================
// Fix #232: File attachment preview
// ============================================================================

/**
 * Fix #232: Данные о выбранном файле, ожидающем отправки.
 * Fix #235 (multi-file): добавлено поле [id] — уникальный стабильный
 * идентификатор для ключа LazyRow в [PendingFilesBar]. Без него при
 * удалении файла из середины композаблы смешивались (общая проблема
 * LazyColumn/Row без уникальных ключей).
 */
data class PendingFileAttachment(
    val id: Long,
    val file: java.io.File,
    val displayName: String,
    val sizeBytes: Long,
    val mime: String?,
    val isImage: Boolean,
    /** Fix #297: видеофайлы идут через video.save pipeline, не через docs. */
    val isVideo: Boolean = false,
    /** Fix #297: путь к миниатюре (первый кадр) для видео-превью. null для не-видео или если не удалось. */
    val thumbPath: String? = null,
    /** Fix #297: прогресс загрузки 0..1. 0 = ещё не начали, 1 = загружено. Обновляется во время upload. */
    val progress: Float = 0f,
    /** Fix #297: длительность видео в секундах (для overlay-метки). */
    val durationSec: Long = 0L,
)

/** Генератор уникальных id для [PendingFileAttachment]. */
private val fileIdCounter = java.util.concurrent.atomic.AtomicLong(0)
fun nextPendingFileId(): Long = fileIdCounter.incrementAndGet()

/**
 * Fix #235 (multi-file): бар выбранных файлов над полем ввода.
 * Горизонтальный LazyRow: для каждого файла — карточка с миниатюрой
 * (для картинок) или иконкой-закрепкой, именем, размером и кнопкой ×.
 * Анимированно появляется/исчезает. Слева — счётчик «N файлов».
 * Заменяет старый [PendingFilePreviewBar] (одиночный).
 */
@Composable
private fun PendingFilesBar(
    files: List<PendingFileAttachment>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = files.isNotEmpty(),
        enter = androidx.compose.animation.fadeIn(re.pinok.ui.anim.tweenScaled<Float>(LocalAnimScale.current, 300)) +
            androidx.compose.animation.expandVertically(re.pinok.ui.anim.tweenScaled<androidx.compose.ui.unit.IntSize>(LocalAnimScale.current, 300)),
        exit = androidx.compose.animation.fadeOut(re.pinok.ui.anim.tweenScaled<Float>(LocalAnimScale.current, 300)) +
            androidx.compose.animation.shrinkVertically(re.pinok.ui.anim.tweenScaled<androidx.compose.ui.unit.IntSize>(LocalAnimScale.current, 300)),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "📎 ${files.size} файл(ов)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "× — убрать",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    items(
                        items = files,
                        key = { it.id },
                    ) { pf ->
                        val index = files.indexOf(pf)
                        PendingFileChip(
                            file = pf,
                            onRemove = { onRemove(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingFileChip(
    file: PendingFileAttachment,
    onRemove: () -> Unit,
) {
    val isUploading = file.progress in 0.001f..0.999f
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.width(200.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Миниатюра для картинок, превью кадра для видео, иконка для остальных.
                if (file.isImage) {
                    AsyncImage(
                        model = file.file,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else if (file.isVideo && file.thumbPath != null) {
                    // Fix #297: превью первого кадра видео + play-icon overlay.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = file.thumbPath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        // тёмный виньетка для контраста play-icon
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                        )
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = "Видео",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else if (file.isVideo) {
                    // видео без миниатюры — иконка фильма
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VideoFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Имя + размер/длительность (занимают остаток ширины карточки).
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                    val meta = buildString {
                        append(formatFileSize(file.sizeBytes))
                        if (file.isVideo && file.durationSec > 0) {
                            append(" · ")
                            val m = file.durationSec / 60
                            val s = file.durationSec % 60
                            append(if (m > 0) "${m}:${s.toString().padStart(2, '0')}" else "${s}с")
                        }
                    }
                    Text(
                        text = if (isUploading) "Загрузка… ${(file.progress * 100).toInt()}%" else meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUploading) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        fontWeight = if (isUploading) FontWeight.Medium else FontWeight.Normal,
                    )
                }
                // Кнопка отмены (×) — скрывается во время upload.
                if (!isUploading) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(onClick = onRemove),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Убрать файл",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(24.dp))
                }
            }
            // Fix #297: прогресс-бар под карточкой во время upload.
            if (isUploading) {
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { file.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

// formatFileSize уже определена выше (используется DocAttachmentRow и др.).
// Fix #232 переиспользует её для PendingFilesBar — дубль удалён.
