package re.pinok.ui.screens.calls

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.math.round
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.contracts.CallStarter
import re.pinok.contracts.ContainerRegistry
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.util.AppLog
import re.pinok.util.toChatDate
import re.pinok.util.toDayKey
import re.pinok.util.toDurationString
import re.pinok.util.toMsgTime

private const val CALL_CHAT_TAG = "CallChatScreen"

/** Страница истории messages.getHistory (страничный размер web: 50). */
private const val CALL_CHAT_HISTORY_PAGE = 50

/**
 * «Открытие вокруг непрочитанного»: offset = -floor(0.8 · 50) (реверс
 * REV-DEEP-1, IM-SPA 83836@1769954); применяется только если ответ
 * calls.getConversationByCall дал last_conversation_message_id.
 */
private const val CALL_CHAT_OPEN_OFFSET = -40

/**
 * Сколько элементов от верха списка считаем «пора грузить старые»
 * (scroll-up триггер дозагрузки; аналог LOAD_MORE_AHEAD секции «История»).
 */
private const val CALL_CHAT_LOAD_OLDER_AHEAD = 2

/**
 * #CALLS-SNAP (2026-09-05): Этап Д плана «звонки.перенос.план.md» (§4-Д, §1.6)
 * — чат звонка. Открывается полноэкранным Dialog ВНУТРИ секции (прецедент
 * CallsRecordingPlayer/CallsTranscriptViewer — маршруты не добавляются).
 *
 * Данные: calls.getConversationByCall{call_id, hall_id (опционально)} —
 * точная сигнатура члена фасада callsGetConversationByCall(String, Long?).
 * Ответ формой в снапшотах НЕ сохранился (реверс §7 даёт только request) —
 * парсинг tolerant: peer_id / peer.id / conversation.peer(.id), заголовок
 * (title/name/chat.title/conversation.chat_settings.title/groups[].name),
 * фото (photo_100/photo_200/chat.photo_100/chat_settings.photo.photo_100),
 * лента (items или messages.items). Форма ответа подтверждается живым
 * сервером (Этап И) — все кандидаты и их приоритет перечислены в KDoc
 * parseCallChatResponse; нераспознанное НЕ имитируется.
 *
 * ПЕРЕИСПОЛЬЗОВАНИЕ ChatDetailScreen (приоритет 1 задания) НЕВОЗМОЖНО
 * архитектурно: ChatDetailScreen живёт в :app (re.pinok.ui.screens.im) и
 * импортирует :app-типы (SovaApp.get(), ui.anim LocalAnimScale/
 * LocalStickerPhotoScale), а :feature:calls не может зависеть от :app
 * (цикл :app -> :feature:calls -> :app запрещён Gradle — шапка
 * feature/calls/build.gradle.kts и KDoc CallsDependencies). Колбэк из хоста
 * тоже недоступен: CallsMainScreen/SovaNavHost — запретные файлы. Провайдеры
 * CompositionLocal у ChatDetailScreen берутся из MainActivity-графа — Dialog
 * наследует локали точки создания, Т.Е. сам по себе Dialog препятствием НЕ
 * был бы; препятствие — именно модульная граница. Реализован ПРИОРИТЕТ 2:
 * минимальный РЕАЛЬНЫЙ чат на членах фасада (см. honest-состояние ниже).
 *
 * Честные ограничения (no-stub; ОБНОВЛЕНО волной-7, задание 7-c — история и
 * отправка теперь РЕАЛЬНЫЕ через новых членов фасада, ревизия-2 REV-DEEP-1):
 *  - история ленты — messages.getHistory {peer_id, [start_cmid], count, offset,
 *    extended:1, fwd_extended:1} (IM-SPA 83836@1769954): страница 50; открытие
 *    offset=0 (новейшие 50), а если ответ calls.getConversationByCall дал
 *    last_conversation_message_id — «вокруг непрочитанного»: start_cmid +
 *    offset=-floor(0.8·50)=-40. Выравнивание окна по unread_count из web
 *    (h = max(-unread, h) при inReadBy) НЕ воспроизводится — семантика
 *    корректировки в снапшоте неоднозначна (честно; живая проверка Этапа И);
 *    peer_id — ГОТОВЫЙ из conversation.peer.id (bridge@196400 setRoomChatId;
 *    арифметика +2e9 — только внутренняя арифметика IM-чанков, НЕ нужна);
 *  - отправка — messages.send {peer_id, random_id, message}; random_id =
 *    round(2e9·random) (живой пример web 252761de@13996); НЕ optimistic:
 *    успех → очистка поля + честный refetch новейшей страницы (offset=0) с
 *    дедупом по conversation_message_id; провал → Toast с lastApiError,
 *    текст СОХРАНЁН в поле;
 *  - дозагрузка старых — backward offset=-1 + start_cmid от минимального
 *    conversation_message_id списка; hasMore = (len(items) == count); дедуп
 *    по cmid защищает от дублей (живая проверка Этапа И);
 *  - вложения/стикеры/эмодзи/форматирование НЕ рендерятся (загрузчиков в
 *    фасаде нет): сообщение без текста — честный пустой пузырь со временем;
 *  - «Перейти в мессенджер» (§7.1) — навигационная граница :feature:calls →
 *    :app (SovaNavHost/Screen.kt — запретные файлы);
 *  - «Приглашение в звонок» (§7.3) отдельной карточкой НЕ рендерится — ссылка
 *    видна как текст сообщения (join-флоу — Этап Г4);
 *  - «Звонок» в шапке (fc-convo-call) — РЕАЛЬНЫЙ: CallStarter из реестра
 *    контейнеров (та же capability, что redial Этапа Б2); нет CallStarter или
 *    peer не распознан — кнопка не рендерится (graceful, как onCallClick=null
 *    в ChatDetailScreen); запуск не удался — честный Toast;
 *  - имена отправителей ленты — только из profiles[] ответа
 *    calls.getConversationByCall (messagesGetHistory фасада возвращает только
 *    items[], enrichment-запрос профилей не делается);
 *  - русские подписи сервисных action-типов — НАШИ: значения lang-ключей
 *    me_service_${type} в снапшотах НЕ сохранились (реверс REV-DEEP-1);
 *    неизвестный тип рендерится строкой type как есть; peer_id <= 0 —
 *    честное состояние «Чат недоступен: peer_id не распознан», input-row
 *    НЕ рендерится.
 *
 * #ANR-MAIN-IO: сетевой вызов — в LaunchedEffect (main-корутина без
 * блокировок), тяжёлый JSON-парсинг — withContext(Dispatchers.Default).
 * #NULL-EXPLICIT: без non-null assertion, safe-call и elvis операторов.
 *
 * @param callId   call_id записи (тип члена фасада — String; Long-параметр
 *                 терял бы синтетические desktop-id, для которых сервер
 *                 честно ответит ошибкой в диалоге);
 * @param hallId   зал звонка, если известен (в записях calls.getHistory
 *                 hall_id отсутствует — из секции приходит null, вызов идёт
 *                 без hall_id, как в вебе до открытия зала);
 * @param title    имя чата с плитки FCPanel (стартовое значение шапки;
 *                 уточняется из ответа, если тот дал данные);
 * @param photoUrl аватар чата с плитки (аналогично).
 */
@Composable
internal fun CallChatScreen(
    callId: String,
    hallId: Long?,
    title: String?,
    photoUrl: String?,
    onDismiss: () -> Unit,
) {
    val deps = LocalCallsDeps.current
    val context = LocalContext.current
    val myUid = remember { deps.getVkUid() }
    val callStarter = remember { ContainerRegistry.find<CallStarter>().firstOrNull() }
    val scope = rememberCoroutineScope()

    var loading by remember(callId) { mutableStateOf(true) }
    var errorText by remember(callId) { mutableStateOf<String?>(null) }
    var data by remember(callId) { mutableStateOf<CallChatData?>(null) }
    var retryKey by remember(callId) { mutableIntStateOf(0) }

    // История ленты (messages.getHistory, задание 7-c.1/5) и отправка (7-c.4).
    var historyItems by remember(callId) { mutableStateOf<List<CallChatMessage>>(emptyList()) }
    var historyLoading by remember(callId) { mutableStateOf(false) }
    var historyError by remember(callId) { mutableStateOf<String?>(null) }
    var historyLoaded by remember(callId) { mutableStateOf(false) }
    var hasMoreOlder by remember(callId) { mutableStateOf(false) }
    var loadingOlder by remember(callId) { mutableStateOf(false) }
    var historyRetryKey by remember(callId) { mutableIntStateOf(0) }
    var inputText by remember(callId) { mutableStateOf("") }
    var sending by remember(callId) { mutableStateOf(false) }

    LaunchedEffect(callId, hallId, retryKey) {
        loading = true
        errorText = null
        data = null
        if (callId.toLongOrNull() == null) {
            AppLog.w(
                CALL_CHAT_TAG,
                "callId не числовой (" + callId + ") — вероятен синтетический id desktop-формата; вызов всё равно выполняется",
            )
        }
        try {
            val resp = deps.apiClient.callsGetConversationByCall(callId, hallId)
            if (resp == null) {
                // NULL-ЯВНО: null без исключения = оффлайн/сетевой сбой/нет
                // response — реальная причина в lastApiError фасада.
                val apiErr = deps.apiClient.lastApiError
                if (apiErr != null && apiErr.isNotBlank()) {
                    errorText = "Сервер не вернул чат звонка: " + apiErr
                } else {
                    errorText = "Сервер не вернул чат звонка (сеть/оффлайн)"
                }
            } else {
                data = withContext(Dispatchers.Default) { parseCallChatResponse(resp, myUid) }
            }
        } catch (e: Exception) {
            AppLog.e(CALL_CHAT_TAG, "callsGetConversationByCall($callId) failed", e)
            val m = e.message
            if (m == null) {
                errorText = "Не удалось получить чат звонка"
            } else {
                errorText = m
            }
        } finally {
            loading = false
        }
    }

    // ─── История: распознанный peer и первичная загрузка (задание 7-c.1) ───
    val chatData = data
    val chatPeerId = if (chatData == null) 0L else chatData.peerId
    val chatLastCmid = if (chatData == null) null else chatData.lastCmid
    val chatSenderNames = if (chatData == null) emptyMap<Long, String>() else chatData.senderNames

    // Новейшие 50 (offset=0); если ответ calls.getConversationByCall дал
    // last_conversation_message_id — «открытие вокруг непрочитанного»:
    // start_cmid + offset=-40 (-floor(0.8·50), реверс REV-DEEP-1).
    // Выравнивание окна по unread_count из web НЕ воспроизводится (KDoc файла).
    LaunchedEffect(chatData, historyRetryKey) {
        val peer = chatPeerId
        if (peer > 0L) {
            historyLoading = true
            historyError = null
            historyLoaded = false
            try {
                val startCmid = chatLastCmid
                val offset = if (startCmid == null) 0 else CALL_CHAT_OPEN_OFFSET
                val items = deps.apiClient.messagesGetHistory(
                    peer, CALL_CHAT_HISTORY_PAGE, offset, startCmid,
                )
                val apiErr = deps.apiClient.lastApiError
                if (items.isEmpty() && apiErr != null && apiErr.isNotBlank()) {
                    // Фасад возвращает пустой список и на ошибке, и на пустом
                    // чате — различаем по lastApiError (реальная причина).
                    historyError = "messages.getHistory: " + apiErr
                } else {
                    val parsed = withContext(Dispatchers.Default) {
                        parseHistoryItems(items, myUid, chatSenderNames)
                    }
                    historyItems = mergeCallChatHistory(emptyList(), parsed)
                    hasMoreOlder = items.size >= CALL_CHAT_HISTORY_PAGE
                    historyLoaded = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(CALL_CHAT_TAG, "messagesGetHistory($peer) failed", e)
                val m = e.message
                if (m == null) {
                    historyError = "messages.getHistory не выполнен"
                } else {
                    historyError = m
                }
            } finally {
                historyLoading = false
            }
        }
    }

    // ─── Отображаемая лента (задание 7-c.2) ───
    // Первичный источник — messages.getHistory; при недоступности (peer не
    // распознан / ошибка истории) — фолбэк: лента из ответа
    // calls.getConversationByCall (существующий tolerant-парс, просмотр).
    val fallbackFeed: List<CallChatMessage> = if (chatData == null) {
        emptyList()
    } else {
        chatData.messages
    }
    val historyErr = historyError
    val feed: List<CallChatMessage> = if (chatPeerId > 0L && historyErr == null) {
        historyItems
    } else {
        fallbackFeed
    }
    val display = remember(feed) { buildCallChatListItems(feed) }
    val listState = rememberLazyListState()

    // Автоскролл вниз (задание 7-c.2): при первой загрузке и при появлении
    // нового сообщения после отправки (refetch). Ключ — «якорь низа» ленты:
    // дозагрузка старых (prepend) его не меняет — скролл не дёргается.
    val bottomAnchor = newestFeedAnchor(feed)
    LaunchedEffect(bottomAnchor) {
        if (bottomAnchor > 0L && display.isNotEmpty()) {
            listState.scrollToItem(display.size - 1)
            // добиваем до нижней кромки вьюпорта (последний пузырь у низа,
            // как в чате): delta сжимается границами списка
            val info = listState.layoutInfo
            val lastInfo = info.visibleItemsInfo.lastOrNull()
            if (lastInfo != null) {
                val gap = info.viewportEndOffset - lastInfo.offset - lastInfo.size
                if (gap > 0) listState.dispatchRawDelta(gap.toFloat())
            }
        }
    }

    // Дозагрузка старых (задание 7-c.5): scroll к верху списка →
    // messagesGetHistory(peerId, 50, offset=-1, startCmid=мин. cmid) → prepend
    // с дедупом. hasMore = (len(items) == count). Направление backward
    // offset=-1 — реверс REV-DEEP-1; если сервер вернёт те же элементы —
    // дедуп защитит от дублей, hasMore станет false (живая проверка Этапа И).
    LaunchedEffect(chatPeerId, historyLoaded, hasMoreOlder, loadingOlder, chatSenderNames) {
        if (chatPeerId <= 0L || !historyLoaded) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.collect { firstIdx ->
            if (hasMoreOlder && !loadingOlder && firstIdx <= CALL_CHAT_LOAD_OLDER_AHEAD) {
                val anchor = oldestHistoryCmid(historyItems)
                if (anchor <= 0L) {
                    // Якоря нет (все элементы без conversation_message_id) —
                    // честно прекращаем: дозагрузка без start_cmid невозможна.
                    hasMoreOlder = false
                    return@collect
                }
                loadingOlder = true
                try {
                    val older = deps.apiClient.messagesGetHistory(
                        chatPeerId, CALL_CHAT_HISTORY_PAGE, -1, anchor,
                    )
                    val apiErr = deps.apiClient.lastApiError
                    val parsed = withContext(Dispatchers.Default) {
                        parseHistoryItems(older, myUid, chatSenderNames)
                    }
                    val before = historyItems.size
                    historyItems = mergeCallChatHistory(historyItems, parsed)
                    if (older.size >= CALL_CHAT_HISTORY_PAGE && historyItems.size == before) {
                        // Полная страница, но все элементы — дубли: продолжать
                        // бессмысленно (защита от цикла на повторной выдаче).
                        hasMoreOlder = false
                    } else {
                        hasMoreOlder = older.size >= CALL_CHAT_HISTORY_PAGE
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e(CALL_CHAT_TAG, "messagesGetHistory(older, $chatPeerId) failed", e)
                    hasMoreOlder = false
                } finally {
                    loadingOlder = false
                }
            }
        }
    }

    // Отправка (задание 7-c.4): messages.send {peer_id, random_id, message};
    // random_id = round(2e9 · random) — живой пример web (252761de@13996,
    // Math.round(2e9*Math.random())). НЕ optimistic: успех → очистить поле +
    // честный refetch новейшей страницы (offset=0; замена хвоста с дедупом по
    // conversation_message_id); провал → Toast с lastApiError, текст СОХРАНЁН.
    val onSendClick = {
        val peer = chatPeerId
        val text = inputText.trim()
        if (peer > 0L && text.isNotEmpty() && !sending) {
            sending = true
            scope.launch {
                val randomId = round(Random.nextDouble() * 2e9).toLong()
                val ok = deps.apiClient.messagesSendToPeer(peer, text, randomId)
                if (ok) {
                    inputText = ""
                    try {
                        val items = deps.apiClient.messagesGetHistory(
                            peer, CALL_CHAT_HISTORY_PAGE, 0, null,
                        )
                        val apiErr = deps.apiClient.lastApiError
                        val parsed = withContext(Dispatchers.Default) {
                            parseHistoryItems(items, myUid, chatSenderNames)
                        }
                        if (items.isEmpty() && apiErr != null && apiErr.isNotBlank()) {
                            // Отправка успешна, refetch не удался — лента
                            // обновится при следующем открытии/дозагрузке.
                            AppLog.w(CALL_CHAT_TAG, "refetch после send($peer) не удался: $apiErr")
                        } else {
                            historyItems = mergeCallChatHistory(historyItems, parsed)
                            hasMoreOlder = items.size >= CALL_CHAT_HISTORY_PAGE
                            historyError = null
                            historyLoaded = true
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.w(CALL_CHAT_TAG, "refetch после send($peer) failed", e)
                    }
                } else {
                    val apiErr = deps.apiClient.lastApiError
                    if (apiErr != null && apiErr.isNotBlank()) {
                        Toast.makeText(context, "Не отправлено: " + apiErr, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Не отправлено (сеть/оффлайн)", Toast.LENGTH_SHORT).show()
                    }
                }
                sending = false
            }
        }
    }

    // Шапка: данные плитки, уточнённые ответом (если ответ дал лучше).
    var headerTitle: String? = title
    var headerPhoto: String? = photoUrl
    if (chatData != null) {
        val rt = chatData.title
        if (rt != null && rt.isNotBlank()) headerTitle = rt
        val rp = chatData.photo
        if (rp != null && rp.isNotBlank()) headerPhoto = rp
    }
    val shownTitle = headerTitle
    val shownPhoto = headerPhoto
    val titleText: String
    if (shownTitle == null || shownTitle.isBlank()) {
        titleText = "Чат звонка"
    } else {
        titleText = shownTitle
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ─── Шапка (§7.1: заголовок + «Звонок» + «Закрыть») ───
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.padding(start = 8.dp).size(36.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (shownPhoto != null) {
                            AsyncImage(
                                model = shownPhoto,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Text(titleText.take(1), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Звонок " + callId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val starter = callStarter
                    val peerId = if (chatData == null) 0L else chatData.peerId
                    if (starter != null && peerId > 0L) {
                        IconButton(
                            onClick = {
                                val ok = starter.startCall(peerId, false)
                                if (ok) {
                                    AppLog.i(CALL_CHAT_TAG, "fc-convo-call: звонок начат peerId=$peerId")
                                } else {
                                    Toast.makeText(context, "Не удалось начать звонок", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("fc_convo_call"),
                        ) {
                            Icon(
                                Icons.Filled.Call,
                                contentDescription = "Звонок",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("fc_window_close"),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().height(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                // ─── Тело: loading / ошибка / honest-состояние / лента ───
                val err = errorText
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    err != null -> Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Ошибка загрузки чата",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (err.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            onClick = { retryKey++ },
                            modifier = Modifier.testTag("call_chat_retry"),
                        ) {
                            Text("Повторить")
                        }
                    }
                    chatData == null -> CallChatHonestState("Чат звонка не получен")
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        when {
                            // peer_id не распознан (задание 7-c.6): история
                            // (messages.getHistory) и отправка (messages.send)
                            // невозможны — честное состояние; input-row НЕ
                            // рендерится. Лента из ответа
                            // calls.getConversationByCall, если была, — показана.
                            chatPeerId <= 0L -> {
                                if (fallbackFeed.isEmpty()) {
                                    CallChatHonestState(
                                        "Чат недоступен: peer_id не распознан (ответ " +
                                            "calls.getConversationByCall не содержит peer_id) — " +
                                            "история и отправка сообщений невозможны.",
                                    )
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            "peer_id не распознан — история и отправка недоступны; " +
                                                "лента из ответа calls.getConversationByCall:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                        CallChatFeedList(display, listState, loadingOlder)
                                    }
                                }
                            }
                            // История не загрузилась: честная причина + повтор;
                            // фолбэк — лента из ответа calls.getConversationByCall.
                            historyErr != null -> {
                                if (fallbackFeed.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            "История сообщений не загрузилась",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            historyErr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        TextButton(
                                            onClick = { historyRetryKey++ },
                                            modifier = Modifier.testTag("call_chat_history_retry"),
                                        ) {
                                            Text("Повторить")
                                        }
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            "История (messages.getHistory) не загрузилась: " + historyErr +
                                                " — показана лента из ответа calls.getConversationByCall " +
                                                "(без дозагрузки старых).",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                        CallChatFeedList(display, listState, loadingOlder)
                                    }
                                }
                            }
                            display.isEmpty() -> {
                                if (historyLoading || !historyLoaded) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                                } else {
                                    CallChatHonestState("Лента сообщений чата пуста (сервер не вернул сообщений).")
                                }
                            }
                            else -> CallChatFeedList(display, listState, loadingOlder)
                        }
                        // Input-row только при распознанном peer_id (задание 7-c.6).
                        if (chatPeerId > 0L) {
                            CallChatInputRow(
                                text = inputText,
                                onTextChange = { inputText = it },
                                sendEnabled = chatPeerId > 0L && inputText.isNotBlank() && !sending,
                                onSend = onSendClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Honest-состояние тела чата: реальная причина текстом (НЕ кнопка-заглушка). */
@Composable
private fun CallChatHonestState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("call_chat_state"),
        )
    }
}

/** Разделитель дня (§7.1 StickyDateSeparator: «Сегодня»/«Вчера»/дата). */
@Composable
private fun CallChatSeparatorRow(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

/**
 * Сообщение ленты: исходящее справа, входящее слева (§7.1 ConvoStack out/in);
 * сервисное звонковое — отличимой карточкой AttachFinishedCall (§7.2):
 * заголовок «Исходящий звонок»/«Входящий звонок» + подстрока
 * «Завершён · m:ss»/«Отменён»;
 * прочее сервисное (action.type, задание 7-c.3) — центрированная серая строка.
 */
@Composable
private fun CallChatMessageRow(message: CallChatMessage) {
    // Сервисное сообщение звонка (задание 7-c.3): центрированная серая строка
    // с нашей подписью (callChatServiceActionLabel); время не рендерим —
    // сервисная строка самодостаточна (как ServiceMessage в вебе).
    val actionLine = message.serviceAction
    if (actionLine != null) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                actionLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("call_chat_service_action"),
            )
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (message.out) Alignment.End else Alignment.Start,
    ) {
        val head = message.serviceHeadline
        if (head != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("call_chat_service_message"),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            head,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        val fn = message.serviceFootnote
                        if (fn != null) {
                            Text(
                                fn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        val text = message.text
        val hasBubbleText = text != null && text.isNotBlank()
        if (text != null && text.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (message.out) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.testTag("call_chat_message_text"),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(10.dp),
                )
            }
        } else {
            // Сообщение без текста (вложение/стикер/форвард): вложения НЕ
            // имитируем (загрузчиков/рендереров в фасаде нет) — честный пустой
            // пузырь со временем (задание 7-c.2); без даты — точка-плейсхолдер.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (message.out) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.testTag("call_chat_message_empty"),
            ) {
                val emptyLabel = if (message.dateSec > 0L) message.dateSec.toMsgTime() else "·"
                Text(
                    emptyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        val sender = message.senderName
        val time = if (message.dateSec > 0L) message.dateSec.toMsgTime() else null
        // Под пустым пузырём время не дублируем: мета — только у текстового
        // пузыря или сервисной звонковой карточки.
        val showMeta = (sender != null || time != null) &&
            (hasBubbleText || message.serviceHeadline != null)
        if (showMeta) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sender != null) {
                    Text(
                        sender,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (time != null) {
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Лента чата (задание 7-c.2): LazyColumn с reverseLayout=false — старые
 * сверху, новые внизу (как в веб-чате). Индикатор дозагрузки старых — первым
 * элементом списка. Ключи айтемов стабильны (callChatItemKey) — prepend
 * старых сохраняет позицию скролла (анкоринг LazyColumn по ключам).
 */
@Composable
private fun CallChatFeedList(
    display: List<CallChatListItem>,
    listState: LazyListState,
    loadingOlder: Boolean,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("call_chat_history"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (loadingOlder) {
            item(key = "call_chat_load_older") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
            }
        }
        itemsIndexed(display, key = { idx, item -> callChatItemKey(item, idx) }) { _, item ->
            if (item is CallChatSeparator) {
                CallChatSeparatorRow(item.label)
            } else if (item is CallChatEntry) {
                CallChatMessageRow(item.message)
            }
        }
    }
}

/**
 * Строка ввода (§7.1 composer, задание 7-c.4): TextField + кнопка Send
 * (Icons.AutoMirrored — иконка направления письма). Кнопка активна при
 * непустом тексте, готовом peer_id и незанятой отправке; при провале
 * отправки текст в поле СОХРАНЯЕТСЯ (очистка — только на успехе).
 */
@Composable
private fun CallChatInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit,
) {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().height(1.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Сообщение") },
            maxLines = 5,
            modifier = Modifier.weight(1f).testTag("calls_chat_input"),
        )
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onSend,
            enabled = sendEnabled,
            modifier = Modifier.testTag("calls_chat_send"),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
        }
    }
}

// ─── Модель ленты и tolerant-парсинг (чистые функции — I/O на вызывающем) ───

/** Элемент отображаемого списка ленты: разделитель дня или сообщение. */
private sealed interface CallChatListItem

private data class CallChatSeparator(val label: String) : CallChatListItem

private data class CallChatEntry(val message: CallChatMessage) : CallChatListItem

/** Сообщение ленты чата звонка (обычное или сервисное §7.2/REV-DEEP-1). */
private data class CallChatMessage(
    /** Unix-секунды (0 — дата в данных отсутствует). */
    val dateSec: Long,
    /**
     * conversation_message_id элемента ленты (0 — поле в данных отсутствует):
     * дедуп слияния страниц и якорь дозагрузки старых (задание 7-c.4/5).
     */
    val cmid: Long,
    val out: Boolean,
    /** Имя отправителя из profiles[] ответа (только для входящих). */
    val senderName: String?,
    val text: String?,
    /** «Исходящий звонок»/«Входящий звонок»/«Звонок» — для сервисных. */
    val serviceHeadline: String?,
    /** «Завершён · m:ss»/«Отменён» — для сервисных. */
    val serviceFootnote: String?,
    /**
     * Сервисное сообщение звонка (action.type, задание 7-c.3): текст
     * центрированной серой строки (наша подпись либо type как есть).
     */
    val serviceAction: String?,
)

/** Результат tolerant-парсинга ответа calls.getConversationByCall. */
private data class CallChatData(
    val peerId: Long,
    val title: String?,
    val photo: String?,
    /**
     * unread_count из ответа (conversation.unread_count, REV-DEEP-1
     * bridge@196400 SET_UNREAD_COUNT). На смещение открытия НЕ влияет:
     * web-формула выравнивания окна (h = max(-unread, h) при inReadBy) не
     * воспроизведена честно (живая проверка Этапа И) — только lastCmid.
     */
    val unreadCount: Long,
    /**
     * last_conversation_message_id из ответа (chatUpToMsgId bridge@196400):
     * задан → открытие истории «вокруг непрочитанного» (start_cmid,
     * offset=-40); null → offset=0 (новейшие 50).
     */
    val lastCmid: Long?,
    /** Имена участников из profiles[] — имена отправителей ленты. */
    val senderNames: Map<Long, String>,
    /** Лента по возрастанию времени (нормализована). */
    val messages: List<CallChatMessage>,
    /** true — массив ленты в ответе найден (items или messages.items). */
    val hasMessageFeed: Boolean,
    /** Сырые элементы ленты, которые не удалось отрендерить. */
    val skippedItems: Int,
)

/**
 * Лента по возрастанию времени с разделителями дней (toChatDate/toDayKey).
 * Отображается как есть в LazyColumn (reverseLayout=false): старые сверху,
 * новые внизу — как в веб-чате (задание 7-c.2).
 */
private fun buildCallChatListItems(messages: List<CallChatMessage>): List<CallChatListItem> {
    val out = ArrayList<CallChatListItem>()
    var lastDay = 0
    for (m in messages) {
        if (m.dateSec > 0L) {
            val day = m.dateSec.toDayKey()
            if (day != lastDay) {
                out.add(CallChatSeparator(m.dateSec.toChatDate()))
                lastDay = day
            }
        }
        out.add(CallChatEntry(m))
    }
    return out
}

/**
 * Tolerant-парсинг ответа calls.getConversationByCall (форма в снапшотах не
 * сохранилась — реверс §7 даёт только request {call_id, hall_id}). Кандидаты
 * перечислены в порядке приоритета; всё распознанное — данные ответа,
 * нераспознанное не имитируется. Выполнять на Dispatchers.Default.
 *
 * peer: peer_id | peer.id | conversation.peer_id | conversation.peer.id
 * title: title | name | peer.name/title | chat.title/name |
 *        conversation.chat_settings.title | groups[0].name
 * photo: photo | photo_100 | photo_200 | peer.photo_100 | chat.photo_100 |
 *        conversation.chat_settings.photo.photo_100 | groups[0].photo_100
 * лента: items | messages.items
 * отправители: profiles[] {id, first_name, last_name, name}
 */
private fun parseCallChatResponse(response: JsonObject, myUid: Long): CallChatData {
    var peerId = 0L
    val directPeerId = jsonLong(response, "peer_id")
    if (directPeerId != null && directPeerId > 0L) peerId = directPeerId
    if (peerId == 0L) {
        val peerObj = jsonObj(response, "peer")
        if (peerObj != null) {
            val pid = jsonLong(peerObj, "id")
            if (pid != null && pid > 0L) peerId = pid
        }
    }
    if (peerId == 0L) {
        val conv = jsonObj(response, "conversation")
        if (conv != null) {
            val convPeerId = jsonLong(conv, "peer_id")
            if (convPeerId != null && convPeerId > 0L) peerId = convPeerId
            if (peerId == 0L) {
                val convPeer = jsonObj(conv, "peer")
                if (convPeer != null) {
                    val cid = jsonLong(convPeer, "id")
                    if (cid != null && cid > 0L) peerId = cid
                }
            }
        }
    }

    // unread_count / last_conversation_message_id (REV-DEEP-1 bridge@196400:
    // SET_UNREAD_COUNT {conversation.unread_count, conversation.
    // last_conversation_message_id}) — tolerant: в корне ответа или в
    // conversation{}. lastCmid управляет «открытием вокруг непрочитанного»
    // (задание 7-c.1); unreadCount сохраняется, но на offset НЕ влияет.
    var unreadCount = 0L
    val ucTop = jsonLong(response, "unread_count")
    if (ucTop != null && ucTop > 0L) unreadCount = ucTop
    if (unreadCount == 0L) {
        val convForUnread = jsonObj(response, "conversation")
        if (convForUnread != null) {
            val ucConv = jsonLong(convForUnread, "unread_count")
            if (ucConv != null && ucConv > 0L) unreadCount = ucConv
        }
    }
    var lastCmid: Long? = jsonLong(response, "last_conversation_message_id")
    if (lastCmid == null) {
        val convForCmid = jsonObj(response, "conversation")
        if (convForCmid != null) lastCmid = jsonLong(convForCmid, "last_conversation_message_id")
    }

    var title: String? = jsonStr(response, "title")
    if (title == null) title = jsonStr(response, "name")
    if (title == null) {
        val peerObj = jsonObj(response, "peer")
        if (peerObj != null) {
            title = jsonStr(peerObj, "name")
            if (title == null) title = jsonStr(peerObj, "title")
        }
    }
    if (title == null) {
        val chat = jsonObj(response, "chat")
        if (chat != null) {
            title = jsonStr(chat, "title")
            if (title == null) title = jsonStr(chat, "name")
        }
    }
    if (title == null) {
        val conv = jsonObj(response, "conversation")
        if (conv != null) {
            val settings = jsonObj(conv, "chat_settings")
            if (settings != null) title = jsonStr(settings, "title")
        }
    }

    var photo: String? = jsonStr(response, "photo")
    if (photo == null) photo = jsonStr(response, "photo_100")
    if (photo == null) photo = jsonStr(response, "photo_200")
    if (photo == null) {
        val peerObj = jsonObj(response, "peer")
        if (peerObj != null) photo = jsonStr(peerObj, "photo_100")
    }
    if (photo == null) {
        val chat = jsonObj(response, "chat")
        if (chat != null) photo = jsonStr(chat, "photo_100")
    }
    if (photo == null) {
        val conv = jsonObj(response, "conversation")
        if (conv != null) {
            val settings = jsonObj(conv, "chat_settings")
            if (settings != null) {
                val ph = jsonObj(settings, "photo")
                if (ph != null) photo = jsonStr(ph, "photo_100")
            }
        }
    }

    // Имена отправителей из profiles[] (аватарки отправителей ленты —
    // профили приходят в ответе; отдельного запроса членов фасада нет).
    val senderNames = HashMap<Long, String>()
    val profiles = jsonArray(response, "profiles")
    if (profiles != null) {
        for (el in profiles) {
            if (!el.isJsonObject) continue
            val p = el.asJsonObject
            val id = jsonLong(p, "id")
            if (id == null || id <= 0L) continue
            val first = jsonStr(p, "first_name")
            val last = jsonStr(p, "last_name")
            val single = jsonStr(p, "name")
            var full = ""
            if (first != null) full = first
            if (last != null) {
                if (full.isEmpty()) full = last else full = full + " " + last
            }
            if (full.isEmpty() && single != null) full = single
            if (full.isNotEmpty()) senderNames[id] = full
        }
    }
    // Фолбэки заголовка/фото из groups[] (чат звонка может быть групповым).
    if (title == null || photo == null) {
        val groups = jsonArray(response, "groups")
        if (groups != null) {
            for (el in groups) {
                if (!el.isJsonObject) continue
                val g = el.asJsonObject
                if (title == null) {
                    val gn = jsonStr(g, "name")
                    if (gn != null) title = gn
                }
                if (photo == null) photo = jsonStr(g, "photo_100")
                break
            }
        }
    }

    var feed: JsonArray? = jsonArray(response, "items")
    if (feed == null) {
        val messagesObj = jsonObj(response, "messages")
        if (messagesObj != null) feed = jsonArray(messagesObj, "items")
    }

    val messages = ArrayList<CallChatMessage>()
    var skipped = 0
    val f = feed
    if (f != null) {
        for (el in f) {
            if (!el.isJsonObject) {
                skipped++
                continue
            }
            val msg = parseCallChatMessage(el.asJsonObject, myUid, senderNames, keepEmptyText = false)
            if (msg == null) {
                skipped++
            } else {
                messages.add(msg)
            }
        }
    }
    // Нормализация порядка: VK обычно отдаёт ленту от новых к старым; sortBy
    // стабилен — при равных/отсутствующих датах исходный порядок сохраняется.
    messages.sortBy { it.dateSec }
    return CallChatData(
        peerId = peerId,
        title = title,
        photo = photo,
        unreadCount = unreadCount,
        lastCmid = lastCmid,
        senderNames = senderNames,
        messages = messages,
        hasMessageFeed = feed != null,
        skippedItems = skipped,
    )
}

/**
 * Один элемент ленты (ответ calls.getConversationByCall ИЛИ items[]
 * messages.getHistory — форма общая). Сервисное звонковое —
 * action.type == "call_message" либо attachments[].type == "call"
 * (направление: out-флаг, иначе initiator_id/creator_id против моего uid —
 * deps.getVkUid(); без направления — честный нейтральный заголовок «Звонок»).
 * Подстрока по реверсу §7.2: state=canceled → «Отменён»; duration > 0 →
 * «Завершён · m:ss»; без state и duration=0/нет — «Отменён» (не состоялся).
 * Прочий action.type — сервисные сообщения звонка (задание 7-c.3): известные
 * call-типы → наши русские подписи (callChatServiceActionLabel), неизвестные —
 * строка type как есть.
 *
 * @param keepEmptyText true — элементы без текста и без сервиса НЕ
 *   отбрасываются (история messages.getHistory: честный пустой пузырь со
 *   временем — вложения не имитируются); false — отбрасываются
 *   (legacy-режим ленты calls.getConversationByCall, как до волны-7).
 */
private fun parseCallChatMessage(
    item: JsonObject,
    myUid: Long,
    senderNames: Map<Long, String>,
    keepEmptyText: Boolean,
): CallChatMessage? {
    var dateSec = 0L
    val d = jsonLong(item, "date")
    if (d != null && d > 0L) dateSec = d

    var cmid = 0L
    val cm = jsonLong(item, "conversation_message_id")
    if (cm != null && cm > 0L) cmid = cm

    val outFlag = jsonBoolish(item, "out")
    val fromId = jsonLong(item, "from_id")
    var out = false
    var outKnown = false
    if (outFlag != null) {
        out = outFlag
        outKnown = true
    } else if (fromId != null && fromId == myUid && myUid > 0L) {
        out = true
        outKnown = true
    }

    var headline: String? = null
    var footnote: String? = null
    var serviceAction: String? = null

    val action = jsonObj(item, "action")
    if (action != null) {
        val t = jsonStr(action, "type")
        if (t != null) {
            if (t == "call_message") {
                val dur = jsonLong(action, "duration")
                val initiator = jsonLong(action, "initiator_id")
                val receiver = jsonLong(action, "receiver_id")
                val state = jsonStr(action, "state")
                if (!outKnown) {
                    if (initiator != null && initiator == myUid && myUid > 0L) {
                        out = true
                        outKnown = true
                    } else if (receiver != null && receiver == myUid && myUid > 0L) {
                        out = false
                        outKnown = true
                    }
                    // myUid неизвестен — направление не угадываем: нейтральный «Звонок».
                }
                headline = serviceCallHeadline(outKnown, out)
                footnote = serviceCallFootnote(state, dur)
            } else {
                // Сервисные сообщения звонка (REV-DEEP-1, switch IM-SPA
                // 83836@1275300): известные call-типы → наши подписи,
                // неизвестные — строка type как есть (не имитируем перевод).
                serviceAction = callChatServiceActionLabel(t)
            }
        }
    }
    if (headline == null) {
        val atts = jsonArray(item, "attachments")
        if (atts != null) {
            for (el in atts) {
                if (!el.isJsonObject) continue
                val att = el.asJsonObject
                val attType = jsonStr(att, "type")
                if (attType == null || attType != "call") continue
                val call = jsonObj(att, "call")
                if (call == null) continue
                val dur = jsonLong(call, "duration")
                val state = jsonStr(call, "state")
                val creator = jsonLong(call, "creator_id")
                if (!outKnown) {
                    if (creator != null && creator == myUid && myUid > 0L) {
                        out = true
                        outKnown = true
                    } else if (creator != null && creator != myUid) {
                        out = false
                        outKnown = true
                    }
                }
                headline = serviceCallHeadline(outKnown, out)
                footnote = serviceCallFootnote(state, dur)
                break
            }
        }
    }

    val text = jsonStr(item, "text")
    if (!keepEmptyText && headline == null && serviceAction == null && (text == null || text.isBlank())) {
        return null
    }
    var senderName: String? = null
    if (!out && fromId != null) {
        val resolved = senderNames[fromId]
        if (resolved != null) senderName = resolved
    }
    return CallChatMessage(
        dateSec = dateSec,
        cmid = cmid,
        out = out,
        senderName = senderName,
        text = text,
        serviceHeadline = headline,
        serviceFootnote = footnote,
        serviceAction = serviceAction,
    )
}

/** Заголовок сервисной карточки §7.2; без направления — нейтральный «Звонок». */
private fun serviceCallHeadline(outKnown: Boolean, out: Boolean): String {
    if (!outKnown) return "Звонок"
    return if (out) "Исходящий звонок" else "Входящий звонок"
}

/** Подстрока сервисной карточки §7.2: «Завершён · m:ss» / «Отменён» / «Завершён». */
private fun serviceCallFootnote(state: String?, durationSec: Long?): String? {
    if (state != null) {
        val s = state.trim().lowercase()
        if (s == "canceled" || s == "cancelled") return "Отменён"
        if (s == "finished" || s == "completed") {
            if (durationSec != null && durationSec > 0L) {
                return "Завершён · " + durationSec.toInt().toDurationString()
            }
            return "Завершён"
        }
    }
    if (durationSec != null && durationSec > 0L) {
        return "Завершён · " + durationSec.toInt().toDurationString()
    }
    return "Отменён"
}

/**
 * Русские подписи сервисных сообщений звонка по action.type (реверс REV-DEEP-1:
 * switch IM-SPA 83836@1275300; call-типы chat_group_call_started /
 * chat_invite_user_by_call / chat_invite_user_by_call_join_link /
 * cannot_call_privacy_settings / chat_kick_user_call_block /
 * call_transcription_failed). Рус. ЗНАЧЕНИЯ lang-ключей me_service_${type} в
 * снапшотах НЕ сохранились — подписи наши (живая сверка Этапа И); неизвестный
 * тип — строка как есть (не имитируем перевод).
 */
private fun callChatServiceActionLabel(type: String): String {
    if (type == "chat_group_call_started") return "Начат групповой звонок"
    if (type == "chat_invite_user_by_call") return "Участник приглашён в звонок"
    if (type == "chat_invite_user_by_call_join_link") return "Участник присоединился по ссылке"
    if (type == "cannot_call_privacy_settings") return "Звонок не состоялся: настройки приватности"
    if (type == "chat_kick_user_call_block") return "Участник исключён из звонка"
    if (type == "call_transcription_failed") return "Не удалось создать расшифровку"
    return type
}

/**
 * Разбор СЫРЫХ items[] messages.getHistory (фасад возвращает только items[],
 * extended-профили не возвращаются — имена отправителей берутся из
 * senderNames, распарсенного из profiles[] ответа calls.getConversationByCall).
 * Каждый JSON-объект даёт сообщение: пустой текст без сервиса — ЧЕСТНЫЙ пустой
 * пузырь со временем (вложения не имитируются, задание 7-c.2). Выполнять на
 * Dispatchers.Default.
 */
private fun parseHistoryItems(
    items: List<JsonObject>,
    myUid: Long,
    senderNames: Map<Long, String>,
): List<CallChatMessage> {
    val out = ArrayList<CallChatMessage>(items.size)
    for (item in items) {
        val msg = parseCallChatMessage(item, myUid, senderNames, keepEmptyText = true)
        if (msg != null) out.add(msg)
    }
    return out
}

/**
 * Слияние страниц ленты с дедупом по conversation_message_id (задание 7-c.4/5):
 * refetch новейшей страницы «замещает» хвост, дозагрузка prepend'ит старые —
 * в обоих случаях объединение уникальных cmid с сортировкой по дате (стабильная:
 * при равных dateSec сохраняется порядок вставки). Элементы без cmid (>0) не
 * дедуплицируются (нечем) — в items[] messages.getHistory их практически не
 * бывает.
 */
private fun mergeCallChatHistory(
    existing: List<CallChatMessage>,
    fresh: List<CallChatMessage>,
): List<CallChatMessage> {
    val seen = HashSet<Long>()
    val merged = ArrayList<CallChatMessage>(existing.size + fresh.size)
    for (m in existing) {
        if (m.cmid > 0L) {
            if (seen.add(m.cmid)) merged.add(m)
        } else {
            merged.add(m)
        }
    }
    for (m in fresh) {
        if (m.cmid > 0L) {
            if (seen.add(m.cmid)) merged.add(m)
        } else {
            merged.add(m)
        }
    }
    merged.sortBy { it.dateSec }
    return merged
}

/** Минимальный conversation_message_id списка (якорь дозагрузки; 0 — нет cmid). */
private fun oldestHistoryCmid(messages: List<CallChatMessage>): Long {
    var min = 0L
    for (m in messages) {
        if (m.cmid > 0L) {
            if (min == 0L || m.cmid < min) min = m.cmid
        }
    }
    return min
}

/**
 * Идентификатор «низа» ленты для автоскролла: conversation_message_id
 * последнего сообщения, при отсутствии — его date (0 — лента пуста или без
 * опознаваемых полей: автоскролл не выполняется).
 */
private fun newestFeedAnchor(messages: List<CallChatMessage>): Long {
    val last = messages.lastOrNull()
    if (last == null) return 0L
    if (last.cmid > 0L) return last.cmid
    return last.dateSec
}

/**
 * Стабильные ключи айтемов ленты (анкоринг LazyColumn при prepend'е старых):
 * разделитель — по метке дня (метка уникальна в пределах ленты), сообщение —
 * по cmid, без cmid — по позиции (такие элементы не двигаются: prepend
 * выполняется только для cmid-страниц).
 */
private fun callChatItemKey(item: CallChatListItem, index: Int): String {
    if (item is CallChatSeparator) return "sep_" + item.label
    if (item is CallChatEntry) {
        if (item.message.cmid > 0L) return "m" + item.message.cmid
    }
    return "x" + index
}

// ─── gson-хелперы (private — только для этого файла; явные проверки типов) ───

private fun jsonObj(o: JsonObject, key: String): JsonObject? {
    val el = o.get(key)
    if (el == null) return null
    if (!el.isJsonObject) return null
    return el.asJsonObject
}

private fun jsonArray(o: JsonObject, key: String): JsonArray? {
    val el = o.get(key)
    if (el == null) return null
    if (!el.isJsonArray) return null
    return el.asJsonArray
}

private fun jsonPrim(o: JsonObject, key: String): JsonPrimitive? {
    val el = o.get(key)
    if (el == null) return null
    if (!el.isJsonPrimitive) return null
    return el.asJsonPrimitive
}

private fun jsonStr(o: JsonObject, key: String): String? {
    val p = jsonPrim(o, key)
    if (p == null) return null
    return p.asString
}

private fun jsonLong(o: JsonObject, key: String): Long? {
    val p = jsonPrim(o, key)
    if (p == null) return null
    val s = p.asString
    return s.toLongOrNull()
}

/** Булеан из true/false либо 1/0 (формат out зависит от версии API). */
private fun jsonBoolish(o: JsonObject, key: String): Boolean? {
    val p = jsonPrim(o, key)
    if (p == null) return null
    if (p.isBoolean) return p.asBoolean
    val s = p.asString
    if (s == "1") return true
    if (s == "0") return false
    return null
}
