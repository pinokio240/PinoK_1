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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Dispatchers
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
 * Честные ограничения (no-stub, перечислены и в отчёте Этапа Д):
 *  - история ленты — только если она ПРИШЛА в ответе getConversationByCall;
 *    члена фасада для messages.getHistory НЕТ — иначе текстовое состояние с
 *    реальной причиной (НЕ кнопка-заглушка);
 *  - отправка/файлы/эмодзи/стикеры/форматирование НЕ рендерятся: в CallsApi
 *    нет messages.send и загрузчиков вложений (волна-5);
 *  - «Перейти в мессенджер» (§7.1) НЕ рендерится: навигация в :app-мессенджер
 *    недоступна из :feature:calls (SovaNavHost/Screen.kt — запретные файлы);
 *  - «Приглашение в звонок» (§7.3) отдельной карточкой НЕ рендерится — ссылка
 *    видна как текст сообщения (join-флоу — Этап Г4);
 *  - «Звонок» в шапке (fc-convo-call) — РЕАЛЬНЫЙ: CallStarter из реестра
 *    контейнеров (та же capability, что redial Этапа Б2); нет CallStarter или
 *    peer не распознан — кнопка не рендерится (graceful, как onCallClick=null
 *    в ChatDetailScreen); запуск не удался — честный Toast.
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

    var loading by remember(callId) { mutableStateOf(true) }
    var errorText by remember(callId) { mutableStateOf<String?>(null) }
    var data by remember(callId) { mutableStateOf<CallChatData?>(null) }
    var retryKey by remember(callId) { mutableIntStateOf(0) }

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

    // Шапка: данные плитки, уточнённые ответом (если ответ дал лучше).
    val d = data
    var headerTitle: String? = title
    var headerPhoto: String? = photoUrl
    if (d != null) {
        val rt = d.title
        if (rt != null && rt.isNotBlank()) headerTitle = rt
        val rp = d.photo
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
                    val peerId = if (d == null) 0L else d.peerId
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
                    d == null -> CallChatHonestState("Чат звонка не получен")
                    !d.hasMessageFeed -> {
                        var stateText = "История сообщений недоступна через фасад звонков: ответ " +
                            "calls.getConversationByCall не содержит ленты сообщений, а в CallsApi нет " +
                            "членов для загрузки истории и отправки (messages.getHistory / messages.send) " +
                            "— расширение фасада запланировано волной-5. Доступно: метаданные чата в шапке " +
                            "и звонок из шапки."
                        if (d.peerId > 0L) {
                            stateText = stateText + "\npeer_id: " + d.peerId
                        }
                        CallChatHonestState(stateText)
                    }
                    d.messages.isEmpty() -> {
                        if (d.skippedItems == 0) {
                            CallChatHonestState("Лента сообщений чата пуста (сервер не вернул сообщений).")
                        } else {
                            CallChatHonestState(
                                "Лента получена, но все " + d.skippedItems +
                                    " элементов без текста и без звонкового сервиса " +
                                    "(медиа-вложения не отображаются).",
                            )
                        }
                    }
                    else -> {
                        val display = remember(d) { buildCallChatListItems(d.messages) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("call_chat_history"),
                            reverseLayout = true,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(display.asReversed()) { item ->
                                if (item is CallChatSeparator) {
                                    CallChatSeparatorRow(item.label)
                                } else if (item is CallChatEntry) {
                                    CallChatMessageRow(item.message)
                                }
                            }
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
 * «Завершён · m:ss»/«Отменён».
 */
@Composable
private fun CallChatMessageRow(message: CallChatMessage) {
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
        }
        val sender = message.senderName
        val time = if (message.dateSec > 0L) message.dateSec.toMsgTime() else null
        if (sender != null || time != null) {
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

// ─── Модель ленты и tolerant-парсинг (чистые функции — I/O на вызывающем) ───

/** Элемент отображаемого списка ленты: разделитель дня или сообщение. */
private sealed interface CallChatListItem

private data class CallChatSeparator(val label: String) : CallChatListItem

private data class CallChatEntry(val message: CallChatMessage) : CallChatListItem

/** Сообщение ленты чата звонка (обычное или сервисное §7.2). */
private data class CallChatMessage(
    /** Unix-секунды (0 — дата в данных отсутствует). */
    val dateSec: Long,
    val out: Boolean,
    /** Имя отправителя из profiles[] ответа (только для входящих). */
    val senderName: String?,
    val text: String?,
    /** «Исходящий звонок»/«Входящий звонок»/«Звонок» — для сервисных. */
    val serviceHeadline: String?,
    /** «Завершён · m:ss»/«Отменён» — для сервисных. */
    val serviceFootnote: String?,
)

/** Результат tolerant-парсинга ответа calls.getConversationByCall. */
private data class CallChatData(
    val peerId: Long,
    val title: String?,
    val photo: String?,
    /** Лента по возрастанию времени (нормализована). */
    val messages: List<CallChatMessage>,
    /** true — массив ленты в ответе найден (items или messages.items). */
    val hasMessageFeed: Boolean,
    /** Сырые элементы ленты, которые не удалось отрендерить. */
    val skippedItems: Int,
)

/**
 * Лента по возрастанию времени с разделителями дней (toChatDate/toDayKey).
 * Направление построения — старые сверху; отображение инвертируется
 * reverseLayout LazyColumn (новые внизу, как в чате).
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
            val msg = parseCallChatMessage(el.asJsonObject, myUid, senderNames)
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
        messages = messages,
        hasMessageFeed = feed != null,
        skippedItems = skipped,
    )
}

/**
 * Один элемент ленты. Сервисное звонковое — action.type == "call_message"
 * либо attachments[].type == "call" (направление: out-флаг, иначе
 * initiator_id/creator_id против моего uid — deps.getVkUid(); без направления
 * — честный нейтральный заголовок «Звонок»). Подстрока по реверсу §7.2:
 * state=canceled → «Отменён»; duration > 0 → «Завершён · m:ss»;
 * без state и duration=0/нет — «Отменён» (не состоялся). Сообщение без
 * сервиса и без текста (стикеры/медиа) — null (не имитируем рендер).
 */
private fun parseCallChatMessage(
    item: JsonObject,
    myUid: Long,
    senderNames: Map<Long, String>,
): CallChatMessage? {
    var dateSec = 0L
    val d = jsonLong(item, "date")
    if (d != null && d > 0L) dateSec = d

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

    val action = jsonObj(item, "action")
    if (action != null) {
        val t = jsonStr(action, "type")
        if (t != null && t == "call_message") {
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
    if (headline == null && (text == null || text.isBlank())) {
        return null
    }
    var senderName: String? = null
    if (!out && fromId != null) {
        val resolved = senderNames[fromId]
        if (resolved != null) senderName = resolved
    }
    return CallChatMessage(
        dateSec = dateSec,
        out = out,
        senderName = senderName,
        text = text,
        serviceHeadline = headline,
        serviceFootnote = footnote,
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
