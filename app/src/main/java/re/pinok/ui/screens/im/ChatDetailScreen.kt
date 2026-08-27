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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import re.pinok.data.model.PhotoSizes
import re.pinok.data.model.MessageReaction
import re.pinok.data.model.Track
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.ui.anim.LocalAnimScale
import re.pinok.ui.anim.LocalStickerPhotoScale
import re.pinok.ui.anim.springScaled
import re.pinok.media.VoiceRecorder
import java.text.DecimalFormat
import re.pinok.realtime.LongPollEvent
import re.pinok.ui.components.ForwardDialog
import re.pinok.ui.components.AttachmentPickerSheet
import re.pinok.ui.components.UnifiedAttachMenu
import re.pinok.util.AppLog
import re.pinok.util.toChatDate
import re.pinok.util.toDayKey
import re.pinok.util.toMsgTime
import re.pinok.util.toRecordingTimeString
import androidx.activity.compose.rememberLauncherForActivityResult
import java.io.File
import java.text.SimpleDateFormat
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
import re.pinok.ui.components.PendingPhotosBar
import re.pinok.ui.components.PendingPhoto
import re.pinok.ui.components.nextPendingPhotoId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// VK reaction IDs → emoji.
private val REACTION_EMOJIS = listOf(
    1 to "\uD83D\uDC4D",  // 👍
    2 to "\u2764\uFE0F",   // ❤️
    3 to "\uD83D\uDE02",  // 😂
    4 to "\uD83D\uDE2D",  // 😭
    5 to "\uD83D\uDE21",  // 😡
    6 to "\uD83C\uDF89",  // 🎉
    7 to "\uD83D\uDD25",  // 🔥
    8 to "\uD83D\uDE2E",  // 😮
)

// P0.1: typing indicator — VK resends typing events every ~4s while user keeps typing.
// If no new event arrives within this window, we assume the user stopped typing.
private const val TYPING_TIMEOUT_MS = 6_000L

// Fix #244: multi-select — передаём состояние выбора во вложенные Composable
// (PhotoGrid, VideoAttachmentCard, VoiceMessageBubble, AudioAttachmentRow,
// LinkAttachmentCard, DocAttachmentCard, WallAttachmentCard, ReplyBadge,
// PollAttachmentRow) через CompositionLocal, чтобы не раздувать сигнатуры.
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
    onCallClick: (peerId: Long, title: String, photo: String?) -> Unit = { _, _, _ -> },
    // Fix #132: колбэк вызывается в начале camera callback (до обработки),
    // чтобы очистить сохранённое состояние. Если камера отработала (успех или
    // отмена) — process death уже не должен возвращать в чат. Очищаем только
    // при реальном process death во время камеры (callback не успел вызваться).
    onCameraReturnConsumed: () -> Unit = {},
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
    var showAttachMenu by remember { mutableStateOf(false) }
    // Fix #200: единый триггер ➕ справа от поля — выпадающее меню вверх
    // (Смайлы / Стикеры / Прикрепить). Заменяет 3 отдельные кнопки 📎😀😐,
    // поле ввода стало шире.
    var showTriggerMenu by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    // P5.3: показ расширенного пикера вложений (Музыка/Видео/Подарки).
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var attachmentPickerTab by remember { mutableIntStateOf(0) } // 0=Музыка, 1=Видео, 2=Подарки

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
    fun reactToMessage(messageId: Long, reactionId: Int) {
        scope.launch {
            try {
                val msg = messages.firstOrNull { it.id == messageId } ?: return@launch
                val toggle = if (msg.reactions?.userReaction == reactionId) 0 else reactionId
                val ok = app.apiClient.messagesReact(peerId, messageId, toggle)
                if (ok) reloadMessages()
            } catch (e: Exception) {
                AppLog.e("ChatDetailScreen", "react error", e)
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
    fun editMessage(messageId: Long, newText: String) {
        scope.launch {
            try {
                val ok = app.apiClient.messagesEdit(peerId, messageId, newText)
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

    // Отправка (или отправка редактированного).
    fun doSend() {
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
                    errorText = "Не удалось отправить сообщение"
                    messages = messages.filterNot { it.id == optimistic.id }
                    inputText = text
                    AppLog.w("ChatDetailScreen", "send failed (id=$id) — optimistic message rolled back, text restored to input (Fix #233)")
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
    LaunchedEffect(peerId) {
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
        try {
            // #74: используем messagesGetHistoryWithProfiles — возвращает профили для аватарок
            val result = app.apiClient.messagesGetHistoryWithProfiles(peerId, count = pageSize)
            messages = result.messages.distinctBy { it.id }
            chatProfiles = result.profiles
            if (result.messages.size < pageSize) endReached = true
            if (result.messages.isEmpty()) {
                val err = app.apiClient.lastApiError
                errorText = if (err != null) "Ошибка: $err" else "Нет сообщений"
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
            errorText = "Не удалось загрузить: ${e.message}"
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
            // P3.4: канал = группа (peerId < 0) где can_write.allowed == false.
            // Только если feature-flag включён — иначе обычный режим (composer виден).
            isChannel = channelModeEnabled && chat?.isChannel == true
            // Fix #133: добиваем актуальные title/photo из того же ответа.
            // messagesGetConversationsById с extended=1 отдаёт profiles[]/groups[]
            // и сам резолвит имя/аватарку (через resolveMissingPeerInfo). Если
            // список диалогов передал «Диалог»/null — здесь шапка обновится.
            chat?.peer?.title?.takeIf { it.isNotBlank() && it != "Диалог" }?.let {
                if (it != currentTitle) currentTitle = it
            }
            chat?.peer?.photo?.takeIf { it.isNotBlank() }?.let {
                if (it != currentPhoto) currentPhoto = it
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            pinnedMessage = null
            isChannel = false
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
                val older = app.apiClient.messagesGetHistory(
                    peerId, count = pageSize, offset = messages.size,
                ).filter { np -> messages.none { it.id == np.id } }
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
    LaunchedEffect(peerId, typingEnabled) {
        if (!typingEnabled) {
            typingUsers = emptyMap()
            return@LaunchedEffect
        }
        app.longPollClient.events.collect { ev ->
            if (ev !is LongPollEvent.Typing) return@collect
            if (ev.peerId != peerId) return@collect
            // Don't show typing for yourself (shouldn't happen, but just in case).
            if (ev.userId == myUserId) return@collect
            typingUsers = typingUsers + (ev.userId to System.currentTimeMillis())
        }
    }

    // P0.1: cleanup stale typing entries (older than TYPING_TIMEOUT_MS).
    LaunchedEffect(typingEnabled, typingUsers.isNotEmpty()) {
        if (!typingEnabled || typingUsers.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(TYPING_TIMEOUT_MS / 2)
        val now = System.currentTimeMillis()
        val fresh = typingUsers.filter { (_, ts) -> now - ts < TYPING_TIMEOUT_MS }
        if (fresh.size != typingUsers.size) {
            typingUsers = fresh
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
                            if (peerId in 1..1_999_999_999L) onUserClick(peerId)
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
                        Text(
                            text = currentTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        // Fix #122: muted indicator в шапке чата — перечёркнутый
                        // колокольчик рядом с именем, чтобы пользователь сразу
                        // видел что уведомления выключены (как в нативном VK).
                        if (muted) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Outlined.NotificationsOff,
                                contentDescription = "Уведомления выключены",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                        // #60: online статус под именем.
                        // P0.1: typing indicator имеет приоритет над online статусом.
                        val la = lastActivity
                        // P0.1: resolve typing user names from chatProfiles (group chat)
                        // or just use peerTitle (DM — only one user can be typing).
                        val typingIds = typingUsers.keys.toList()
                        val typingNames = typingIds.mapNotNull { uid ->
                            chatProfiles[uid]?.fullName?.takeIf { it.isNotBlank() }
                        }
                        val statusText = when {
                            typingEnabled && typingIds.isNotEmpty() && isGroupChat && typingNames.isNotEmpty() -> {
                                // Group chat: show up to 2 names, then "+N"
                                when {
                                    typingNames.size == 1 -> "${typingNames[0]} печатает…"
                                    typingNames.size == 2 -> "${typingNames[0]} и ${typingNames[1]} печатают…"
                                    else -> "${typingNames[0]} и ещё ${typingNames.size - 1} печатают…"
                                }
                            }
                            typingEnabled && typingIds.isNotEmpty() -> "печатает…"
                            la?.online == 1 -> "онлайн"
                            la != null && la.lastSeen > 0 -> {
                                val diff = (System.currentTimeMillis() / 1000 - la.lastSeen)
                                when {
                                    diff < 60 -> "был(а) только что"
                                    diff < 3600 -> "был(а) ${diff / 60} мин назад"
                                    diff < 86400 -> "был(а) ${diff / 3600} ч назад"
                                    else -> "был(а) ${diff / 86400} д назад"
                                }
                            }
                            else -> ""
                        }
                        if (statusText.isNotBlank()) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (typingEnabled && typingIds.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else if (lastActivity?.online == 1)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // #CALLS: кнопка звонка в шапке диалога (data-testid="convo-call-menu-trigger").
                    if (peerId in 1..1_999_999_999L) {
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
                                if (chatInfoEnabled) {
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
                                    text = { Text("Поиск по сообщениям") },
                                    onClick = {
                                        showChatMenu = false
                                        showSearch = true
                                    },
                                )
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
                                // P3.2: mute/unmute chat (если флаг включён).
                                if (muteEnabled) {
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
            } else if (isChannel) {
                // P3.4: channel mode — скрываем composer, показываем footer с mute/leave.
                // Канал = broadcast-сообщество, пользователь только читает (не пишет).
                ChannelFooterBar(
                    muted = muted,
                    onToggleMute = { toggleMute() },
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
                            .padding(horizontal = 8.dp, vertical = 6.dp)
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
                                    attachmentPickerTab = 1
                                    showAttachmentPicker = true
                                },
                                onAudio = {
                                    attachmentPickerTab = 0
                                    showAttachmentPicker = true
                                },
                                onGift = {
                                    attachmentPickerTab = 2
                                    showAttachmentPicker = true
                                },
                                onFile = {
                                    multiFilePickerLauncher.launch(arrayOf("*/*"))
                                },
                                // Подарки только в личных диалогах.
                                showGift = peerId > 0 && peerId < 2_000_000_000L,
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
            if (loading && messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val err = errorText
                if (err != null && messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
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
                ) {
                    derivedStateOf {
                        buildChatListItems(
                            messages = messages,
                            groupingEnabled = groupingEnabled,
                            dateSeparatorsEnabled = dateSeparatorsEnabled,
                            unreadDividerEnabled = unreadDividerEnabled,
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
                                MessageBubble(
                                    message = msg,
                                    profiles = chatProfiles,
                                    voicePlaybackController = voicePlaybackController,
                                    onLongPress = { contextMsgId = msg.id },
                                    onDoubleClick = { reactToMessage(msg.id, 2) },
                                    onReact = { rid -> reactToMessage(msg.id, rid) },
                                    onCopy = { contextMsgId = null },
                                    onEdit = {
                                        contextMsgId = null
                                        editingMsgId = msg.id
                                        inputText = msg.text
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

    // #60: Search bar
    if (showSearch) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Поиск по сообщениям") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Введите текст…") },
                        singleLine = true,
                        trailingIcon = {
                            if (searching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (searchResults.isNotEmpty()) {
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
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { performSearch() }) {
                    Text("Найти")
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
    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onDismiss = { showAttachmentPicker = false },
            initialTab = attachmentPickerTab,
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
    // Fix #120: единый voice-плеер на чат — только одно голосовое играет за раз.
    voicePlaybackController: VoicePlaybackController,
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
                // Тот же long-press handler что у bubble Box ниже:
                // прямой вход в selection если multi-select доступен и мы
                // не в selection mode; иначе — context menu.
                if (multiSelectAvailable && !selectionMode) onSelect()
                else if (!selectionMode) onLongPress()
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
                        if (multiSelectAvailable && !selectionMode) onSelect()
                        else if (!selectionMode) onLongPress()
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
                        // Fix #244: прямой вход в selection по long-press
                        // (вариант A) — если multi-select доступен и мы не в
                        // selection mode, сразу enterSelection без промежуточного
                        // DropdownMenu. Иначе (multi-select выключен или уже в
                        // selection) — context menu как раньше.
                        if (multiSelectAvailable && !selectionMode) onSelect()
                        else if (!selectionMode) onLongPress()
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
                if (message.hasReply && message.replyMessage != null) {
                    val reply = message.replyMessage
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
                val photoAttachments = message.attachments
                    ?.filter { it.type == "photo" && it.photo != null }
                if (!photoAttachments.isNullOrEmpty()) {
                    // P5.1: список URL для полноэкранного просмотрщика (PhotoViewer).
                    val photoUrls = photoAttachments.mapNotNull { it.photo?.largestUrl }
                    val cols = if (photoAttachments.size == 1) 1 else 2
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        photoAttachments.chunked(cols).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                for (att in row) {
                                    val photo = att.photo
                                    val photoUrl = photo?.largestUrl
                                    if (photo != null && photoUrl != null) {
                                        val isSingle = cols == 1
                                        // Fix #227: стикер-фото (квадратное, ≤512px —
                                        // отправлено через messagesSendStickerAsImage)
                                        // рендерим в исходном размере, НЕ растягивая на
                                        // всю ширину бабла. Берём реальные px-размеры из
                                        // photo.sizes, конвертируем в dp (без апскейла),
                                        // капаем на 160dp чтобы очень крупные стикеры не
                                        // перекрывали экран. ContentScale.Fit сохраняет
                                        // пропорции. Обычные фото — как раньше (fillMaxWidth).
                                        val isStickerPhoto = isSingle && photo.isStickerLike
                                        if (isStickerPhoto) {
                                            val largest = photo.largestSize
                                            val density = LocalDensity.current.density
                                            // Fix #228: пользовательский масштаб увеличения
                                            // (0..40%). 0 — исходный, 40 — +40% к оригиналу.
                                            val userScalePct = LocalStickerPhotoScale.current
                                                .coerceIn(0, 40)
                                            val userScale = 1f + userScalePct / 100f
                                            // Cap поднимаем пропорционально userScale — иначе
                                            // при +40% стикер упрётся в 160dp и не увеличится.
                                            val capDp = (160f * userScale).dp
                                            val naturalWDp = if (largest != null) (largest.width / density * userScale).toInt().dp else capDp
                                            val naturalHDp = if (largest != null) (largest.height / density * userScale).toInt().dp else capDp
                                            // Не апскейлим выше natural×userScale — только
                                            // даунскейл если natural×userScale > cap.
                                            val scale = minOf(
                                                capDp.value / naturalWDp.value,
                                                capDp.value / naturalHDp.value,
                                                1f,
                                            )
                                            val dispW = (naturalWDp.value * scale).roundToInt().dp
                                            val dispH = (naturalHDp.value * scale).roundToInt().dp
                                            AsyncImage(
                                                model = photoUrl,
                                                contentDescription = "Стикер",
                                                modifier = Modifier
                                                    .size(dispW, dispH)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .combinedClickable(
                                                        onClick = {
                                                            // Fix #244: в selection mode — toggle, не открываем PhotoViewer.
                                                            if (sel != null && sel.selectionMode) sel.onToggleSelection()
                                                            else {
                                                                val idx = photoUrls.indexOf(photoUrl).coerceAtLeast(0)
                                                                onPhotoClick(photoUrls, idx)
                                                            }
                                                        },
                                                        onLongClick = { sel?.onLongPress?.invoke() },
                                                    ),
                                                contentScale = ContentScale.Fit,
                                            )
                                        } else {
                                            // Fix #225: для одиночных фото используем
                                            // ContentScale.Fit, для сетки (cols=2) — Crop.
                                            AsyncImage(
                                                model = photoUrl,
                                                contentDescription = "Фото",
                                                modifier = Modifier
                                                    .let { m -> if (isSingle) m.fillMaxWidth() else m.weight(1f) }
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .heightIn(max = 200.dp)
                                                    .combinedClickable(
                                                        onClick = {
                                                            // Fix #244: в selection mode — toggle, не открываем PhotoViewer.
                                                            if (sel != null && sel.selectionMode) sel.onToggleSelection()
                                                            else {
                                                                val idx = photoUrls.indexOf(photoUrl).coerceAtLeast(0)
                                                                onPhotoClick(photoUrls, idx)
                                                            }
                                                        },
                                                        onLongClick = { sel?.onLongPress?.invoke() },
                                                    ),
                                                contentScale = if (isSingle) ContentScale.Fit else ContentScale.Crop,
                                            )
                                        }
                                    }
                                }
                                // Заполнить пустые ячейки для ровной сетки
                                if (row.size < cols) {
                                    repeat(cols - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
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
                    ?.filter { it.type == "doc" && it.doc != null && it.doc.isVoiceMessage.not() }
                if (!docAttachments.isNullOrEmpty()) {
                    for (da in docAttachments) {
                        da.doc?.let { doc ->
                            DocAttachmentCard(doc = doc, textColor = textColor, onOpen = { onUrlClick(doc.url) })
                        }
                    }
                }
                // #59: Аудио-вложения — кликабельная строка с play.
                val audioAttachments = message.attachments
                    ?.filter { it.type == "audio" && it.audio != null }
                if (!audioAttachments.isNullOrEmpty()) {
                    for (aa in audioAttachments) {
                        aa.audio?.let { track ->
                            AudioAttachmentRow(
                                track = track,
                                textColor = textColor,
                                // P2.2: запуск трека в PlayerConnection.
                                onClick = { onAudioClick(track) },
                            )
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
                    if (message.reactions != null && message.reactions.count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reactionEmoji(message.reactions.userReaction ?: 0),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        } // Fix #244: закрытие CompositionLocalProvider (LocalAttachmentSelection)

        // ReactionBar — показ реакций под bubble.
        ReactionBar(
            reactions = message.reactions,
            isOut = isOut,
            onReact = onReact,
            onTap = { onShowReactionPicker() },
        )

        // Контекстное меню (long-press).
        Box(modifier = Modifier.fillMaxWidth()) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = onDismissContextMenu,
            ) {
                // #59: Ответить — первый пункт меню
                DropdownMenuItem(
                    text = { Text("Ответить") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null) },
                    onClick = onReply,
                )
                // P2.5: войти в режим выбора (только если флаг включён + не action-msg).
                if (multiSelectAvailable && !selectionMode && !message.isAction) {
                    DropdownMenuItem(
                        text = { Text("Выбрать") },
                        leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                        onClick = onSelect,
                    )
                }
                DropdownMenuItem(
                    text = { Text("Копировать") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("msg", message.text))
                        onDismissContextMenu()
                    },
                )
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
                DropdownMenuItem(
                    text = { Text("Переслать") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Forward, contentDescription = null) },
                    onClick = onForward,
                )
                if (isOut) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = onEdit,
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = onDelete,
                    )
                }
                DropdownMenuItem(
                    text = { Text("Реакция") },
                    leadingIcon = {
                        Text("\u2764\uFE0F", fontSize = 18.sp) // ❤️
                    },
                    onClick = {
                        onDismissContextMenu()
                        onShowReactionPicker()
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

/** Аудио-вложение — компактная строка с play кнопкой. */
@Composable
private fun AudioAttachmentRow(
    track: re.pinok.data.model.Track,
    textColor: Color,
    // P2.2: тап по аудио-вложению → запустить в PlayerConnection.
    onClick: () -> Unit = {},
) {
    // Fix #244: состояние выбора для вложения.
    val sel = LocalAttachmentSelection.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = {
                    if (sel != null && sel.selectionMode) sel.onToggleSelection()
                    else onClick()
                },
                onLongClick = { sel?.onLongPress?.invoke() },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Воспроизвести",
            tint = textColor.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val min = track.duration / 60
        val sec = track.duration % 60
        Text("$min:${"%02d".format(sec)}", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.5f))
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
) {
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
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
            tint = accentColor,
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
                        // Fix #120: столбики до progress — accentColor, после — textColor.
                        val barColor = if (isCurrent && x < size.width * progress) accentColor
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
                        val barColor = if (isCurrent && x < size.width * progress) accentColor
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
                        .background(accentColor.copy(alpha = 0.15f)),
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))
        // Duration / elapsed time.
        val elapsed = (durationSec * progress).roundToInt()
        Text(
            text = elapsed.toRecordingTimeString(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.8f),
            fontSize = 11.sp,
        )
        Text(
            text = "/",
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.4f),
            fontSize = 10.sp,
        )
        Text(
            text = durationSec.toInt().toRecordingTimeString(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.5f),
            fontSize = 11.sp,
        )
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
 *   - Иконка + текст «Вы подписаны» / «Канал заглушен»
 *   - Кнопка mute/unmute (Notifications / NotificationsOff)
 *   - Кнопка «Покинуть» (Delete) — с confirmation dialog (разрушительное действие)
 *
 * Аналог m.vk.ru: канал показывает footer «Вы подписаны на канал» без поля ввода.
 * Leave = groups.leave + messages.deleteConversation (диалог исчезает из списка).
 */
@Composable
private fun ChannelFooterBar(
    muted: Boolean,
    onToggleMute: () -> Unit,
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
                imageVector = if (muted) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (muted) "Канал заглушен" else "Вы подписаны",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // mute/unmute — переключает push_settings для канала.
            IconButton(onClick = onToggleMute) {
                Icon(
                    imageVector = if (muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                    contentDescription = if (muted) "Включить уведомления" else "Заглушить канал",
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
    private var player: MediaPlayer? = null
    private var progressJob: kotlinx.coroutines.Job? = null
    // Fix #237: альтернативный URL для fallback при ошибке воспроизведения.
    // Сохраняется в toggle(), сбрасывается после использования в onError.
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

    /** Длительность текущего сообщения в секундах (для отображения). */
    var durationSec: Float by mutableFloatStateOf(0f)
        private set

    /**
     * Начать воспроизведение сообщения [messageId] по URL [url].
     * Если [messageId] уже текущий и на паузе — resume.
     * Если уже играет — toggle на паузу.
     * Если другое сообщение — stop() старого, start() нового.
     *
     * Fix #237: [fallbackUrl] — альтернативный URL (например, OGG если
     * primary MP3, или наоборот). Если primary падает в onError,
     * контроллер автоматически пробует fallback. Нужно потому что
     * MediaPlayer на разных устройствах по-разному поддерживает OGG/Opus.
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
                    try { p.start() } catch (_: Exception) {}
                    isPlaying = true
                    startProgressTracking()
                }
                return
            }
        }

        // Другое сообщение (или то же, но player умер) → stop старого, start нового.
        releasePlayer()

        // Fix #237: сохраняем fallback для использования в onError.
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
            val mp = MediaPlayer()
            mp.setDataSource(url)
            mp.setOnPreparedListener { p ->
                durationSec = (p.duration / 1000f).coerceAtLeast(fallbackDurationSec)
                p.start()
                isPlaying = true
                progress = 0f
                startProgressTracking()
            }
            mp.setOnCompletionListener {
                isPlaying = false
                progress = 0f
                stopProgressTracking()
                // Не release — оставим player, чтобы можно было replay без reload.
            }
            mp.setOnErrorListener { _, what, extra ->
                AppLog.e("VoicePlayback", "MediaPlayer error: what=$what extra=$extra url=$url")
                releasePlayer()
                // Fix #237: пробуем fallback URL (например, OGG→MP3 или MP3→OGG).
                val fb = currentFallbackUrl
                if (fb != null) {
                    AppLog.i("VoicePlayback", "Trying fallback URL: $fb")
                    currentFallbackUrl = null  // защита от зацикливания
                    // Восстанавливаем currentMessageId после releasePlayer(),
                    // чтобы UI продолжал показывать этот messageId как активный.
                    currentMessageId = messageId
                    startPlayback(messageId, fb, fallbackDurationSec)
                }
                // else: releasePlayer уже сбросил currentMessageId = null
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            AppLog.e("VoicePlayback", "startPlayback error url=$url", e)
            releasePlayer()
        }
    }

    /**
     * Полностью остановить воспроизведение (если, например, пользователь
     * покинул чат). Освобождает MediaPlayer.
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
                    if (!p.isPlaying && progress > 0f) {
                        // Закончилось или пауза вне нашего контроля.
                        break
                    }
                    val d = p.duration.coerceAtLeast(1)
                    progress = p.currentPosition.toFloat() / d
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
            try { p.setOnCompletionListener(null); p.setOnPreparedListener(null); p.setOnErrorListener(null) } catch (_: Exception) {}
            try { p.reset() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
        player = null
        currentMessageId = null
        isPlaying = false
        progress = 0f
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
        enter = androidx.compose.animation.fadeIn() +
            androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() +
            androidx.compose.animation.shrinkVertically(),
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
