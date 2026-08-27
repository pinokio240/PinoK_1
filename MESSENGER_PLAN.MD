# MESSENGER_PLAN.MD — План внедрения новых функций мессенджера

> Основано на исследовании архива `мессенджер.zip` (5 экранов VK mobile web) и аудите VK_X_mod.
> См. `VK_IMPORT_API.MD` ЧАСТЬ 21 (карта экранов), ЧАСТЬ 22 (аудит), ЧАСТЬ 23 (карта API).
>
> **Главный принцип:** не поломать существующий мессенджер. Все изменения —
> строго аддитивные, за `MessagesScreen`/`ChatDetailScreen` сохраняется
> backward-compatibility. Новые функции включаются feature-flag'ами в `SovaPrefs`.

---

## 0. Принципы внедрения

### 0.1. Безопасность существующего кода

| Принцип | Реализация |
|---------|------------|
| **Никаких breaking changes в API signatures** | Новые методы `messagesSendReaction` добавляются рядом с `messagesReact`, не заменяют |
| **Feature-flags для всего нового** | В `SovaPrefs` добавить `msgTypingIndicator`, `msgDateSeparators`, `msgPinBar`, `msgMultiSelect`, `msgBubbleless` — все `false` по умолчанию |
| **Старые модели не трогать** | `Attachment` НЕ переделывать в sealed class (поломает парсинг). Добавить новые поля с nullable default |
| **Каждая фича — отдельный коммит** | Идем от P0 → P4, после каждого коммита — `compileDebugKotlin` + smoke-тест |
| **CODING_STYLE.md соблюдается** | `!!` запрещён, `?:` только для JSON/DataStore/early-return (см. `CODING_STYLE.md`) |

### 0.2. Порядок внедрения

```
P0 (быстрые победы, 1-2 часа каждая)
  ↓
P1 (стандартный UX, 3-5 часов каждая)
  ↓
P2 (вложения + multi-select, 5-10 часов)
  ↓
P3 (новые экраны, 1-2 дня каждый)
  ↓
P4 (архитектурные улучшения, недели)
```

### 0.3. Тестирование после каждой фичи

```bash
# 1. Компиляция (пользователь локально)
./gradlew compileDebugKotlin

# 2. Smoke-тест в эмуляторе:
#    - Открыть MessagesScreen → список должен загрузиться
#    - Открыть ChatDetailScreen → история должна загрузиться
#    - Отправить текст → должно отправиться
#    - Принять сообщение (другое устройство) → LongPoll должен сработать
```

---

## P0 — Быстрые победы (low-hanging fruit)

### P0.1. Typing indicator в ChatDetailScreen

**Проблема:** LongPoll уже эмитит `Typing` события (codes 61/62), но UI их игнорирует.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — добавить `var typingUser by remember { mutableStateOf<String?>(null) }`, собирать `LongPollClient.events` для `Typing` с фильтром по `peerId`, показывать «N печатает…» в TopAppBar subtitle
- `realtime/LongPollClient.kt` — проверить, что `Typing(userId, ..., isChat)` правильно парсится (code 61 = DM, 62 = chat)

**Шаги:**
1. В `ChatDetailScreen` добавить `LaunchedEffect(peerId) { app.longPollClient.events.collect { ev -> if (ev is LongPollEvent.Typing && ev.peerId == peerId) { typingUser = ...; launch { delay(5000); typingUser = null } } } }`
2. В TopAppBar subtitle: `typingUser ?: peerTitle`
3. Для чатов: lookup имени user_id через `chatProfiles`

**Риски:** Typing приходит только для других (не для себя). Нужно исключить свой user_id.

**Feature-flag:** `SovaPrefs.msgTypingIndicator` (default: `true` — безопасно, не ломает ничего)

**Проверка:** Открыть чат с другом → друг печатает на другом устройстве → в TopAppBar должно появиться «Имя печатает…» → через 5 сек исчезнуть.

---

### P0.2. Notifications: имя чата + auto-cancel

**Проблема:**
1. `MessageNotifier.showNotification` всегда передаёт `title="Новое сообщение"`, без имени чата
2. `cancelNotification(peerId)` НИКОГДА не вызывается при открытии чата

**Файлы:**
- `realtime/MessageNotifier.kt` — `showNotification` должен принимать `chatTitle: String` и `unreadCount: Int` (реальное число)
- `SovaApp.kt` → `startMessageNotifier()` — нужно резолвить имя чата через `messagesGetConversations` cache или `messagesGetConversationsById(listOf(peerId))`
- `ui/screens/im/ChatDetailScreen.kt` — в `LaunchedEffect(peerId)` вызвать `MessageNotifier.cancelNotification(context, peerId)`

**Шаги:**
1. Изменить сигнатуру `showNotification(context, peerId, chatTitle, messageText, unreadCount)` — `chatTitle` non-null
2. В `SovaApp.startMessageNotifier`: при `NewMessage` — взять peerId из event, сделать `messagesGetConversationsById(listOf(peerId))` → получить `chat.peer.title`, передать в notifier. Кешировать в `activeNotifications[peerId].chatTitle` для следующих сообщений.
3. Инкремент unreadCount: `activeNotifications[peerId]?.unreadCount?.plus(1) ?: 1`
4. В `ChatDetailScreen` `LaunchedEffect`: `MessageNotifier.cancelNotification(context, peerId)`

**Риски:** `messagesGetConversationsById` — лишний API вызов на каждое уведомление. Кешировать через `activeNotifications`.

**Feature-flag:** Без флага — это fix существующего поведения.

**Проверка:** Отправить себе сообщение с другого устройства → notification с именем чата → открыть чат → notification должна исчезнуть.

---

### P0.3. Pin message UI (подключить существующий API)

**Проблема:** `messagesPin`/`messagesUnpin` API уже реализованы в `VKApiClient`, но в `ChatDetailScreen` меню «Закрепить» — TODO stub.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — найти TODO «pin last message», заменить на вызов `app.apiClient.messagesPin(peerId, messageId)` + показать toast «Сообщение закреплено»
- Добавить PinnedMessageBar над `LazyColumn` (показывает закреплённое сообщение + кнопку «Открепить»)

**Шаги:**
1. Найти stub в context menu: `// TODO: pin last message` → заменить на `scope.launch { app.apiClient.messagesPin(peerId, msg.id) }`
2. Добавить state `pinnedMessage: Message?` — fetched через `messages.getHistory` с параметром `pinned=1` или из отдельного API (`messages.getConversationsById` возвращает `chat.pinnedMessage`)
3. Добавить `PinnedMessageBar(msg, onUnpin)` composable: Card над message list, показывает текст (truncated) + аватар + кнопку X (unpin)
4. На unpin: `app.apiClient.messagesUnpin(peerId)` + `pinnedMessage = null`

**Риски:** Не все чаты поддерживают pin (только group chats). Проверять `peer.type == "chat"`.

**Feature-flag:** `SovaPrefs.msgPinBar` (default: `true`)

**Проверка:** В групповом чате long-press на сообщении → «Закрепить» → bar появляется сверху → «Открепить» → исчезает.

---

## P1 — Стандартный UX мессенджера

### P1.1. Date separators + unread divider + scroll-to-bottom FAB

**Проблема:** Стандартные паттерны мессенджера отсутствуют.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — LazyColumn с `stickyHeader` для date separators, `horizontalDivider` для «Непрочитанные сообщения», `FloatingActionButton` для scroll-to-bottom

**Шаги:**
1. При формировании items для LazyColumn — группировать по дате (`message.date.toLocalDate()`)
2. Перед каждой группой — `stickyHeader { DateSeparator(date) }` (использовать `ExperimentalFoundationApi`)
3. Найти индекс первого непрочитанного сообщения (`message.readState == 0 && !message.isOut`) — вставить divider «Непрочитанные сообщения»
4. Добавить `var showScrollToBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100 } }`
5. Когда `showScrollToBottom` — показать FAB снизу справа: `FloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } }) { Icon(Icons.Default.KeyboardArrowDown) }`
6. На FAB badge: unread count (если есть)

**Риски:** `stickyHeader` требует `ExperimentalFoundationApi`. Date format через `java.time.LocalDate` (нужен API 26+, у нас minSdk скорее всего 24 — проверить).

**Feature-flag:** `SovaPrefs.msgDateSeparators`, `SovaPrefs.msgUnreadDivider`, `SovaPrefs.msgScrollFab` (все default: `true`)

**Проверка:** Пролистать историю вверх → должны появиться sticky-даты → FAB «вниз» появляется → тап → скроллит вниз.

---

### P1.2. Reply via swipe-right

**Проблема:** Reply только через long-press context menu.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — `MessageBubble` обернуть в `SwipeToDismissBox` (Material3) или `Box` с `detectHorizontalDragGestures`

**Шаги:**
1. Для исходящих сообщений (right-aligned) — swipe LEFT = reply
2. Для входящих (left-aligned) — swipe RIGHT = reply
3. На swipe > 80dp → вызвать `onReply(message)` (тот же callback что в context menu)
4. Анимация возврата: `Animatable(0f)` с `spring()`
5. Во время swipe показывать иконку Reply (как в Telegram)

**Риски:** Swipe может конфликтовать с LazyColumn scroll. Использовать `detectHorizontalDragGestures` с порогом > 30dp перед активацией.

**Feature-flag:** `SovaPrefs.msgSwipeReply` (default: `true`)

**Проверка:** Swipe вправо на входящем → должна появиться иконка Reply → отпустить → должен активироваться reply mode.

---

### P1.3. Message grouping (последовательные сообщения от одного отправителя)

**Проблема:** Каждый bubble имеет аватар + имя, даже если 5 сообщений подряд от одного юзера.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — при формировании items проверять `previousMessage.fromId == currentMessage.fromId && timeDelta < 5min` → скрывать аватар и имя

**Шаги:**
1. В `items(messages)` — вычислить `val showAvatar = index == 0 || messages[index-1].fromId != msg.fromId || (msg.date - messages[index-1].date) > 300`
2. Передать `showAvatar` в `MessageBubble`
3. В `MessageBubble` — если `!showAvatar` → `Spacer(height = 4.dp)` вместо аватар+имя блока

**Риски:** Может сломать existing UI если неаккуратно — лучше добавить как флаг в `MessageBubble` signature с default `true`.

**Feature-flag:** `SovaPrefs.msgGrouping` (default: `true`)

**Проверка:** Отправить 3 сообщения подряд → только первое должно иметь аватар+имя.

---

### P1.4. Search bar в MessagesScreen + tabs (Все/Каналы/Непрочитанные)

**Проблема:** MessagesScreen — просто список без поиска и фильтров.

**Файлы:**
- `ui/screens/im/MessagesScreen.kt` — добавить TopAppBar с SearchBar + TabRow
- `api/VKApiClient.kt` — добавить `messagesSearchConversations(query: String, count: Int): List<Chat>` (новый API из ЧАСТИ 23)

**Шаги:**
1. Добавить `var searchQuery by remember { mutableStateOf("") }`
2. Добавить `var activeTab by remember { mutableStateOf(0) }` (0=Все, 1=Каналы, 2=Непрочитанные)
3. TopAppBar: `OutlinedTextField(value = searchQuery, placeholder = "Поиск по чатам и сообщениям")` + clear button
4. На query change (debounce 300ms): если длина ≥ 2 → `messagesSearchConversations(query)` → показать результаты
5. Если query empty → обычный список
6. TabRow с 3 табами:
   - Все → `chats.filter { true }`
   - Каналы → `chats.filter { it.peer.type == "group" && it.peer.id < 0 }` (группы как каналы)
   - Непрочитанные → `chats.filter { it.unreadCount > 0 }`
7. Tab badge: count для каждого

**Риски:** `messagesSearchConversations` — новый API, может не работать с token-типом auth (Kate mobile). Сделать fallback: если API error 15 → client-side filter по `chat.peer.title?.contains(query, ignoreCase = true)`.

**Feature-flag:** `SovaPrefs.msgSearch` (default: `true`)

**Проверка:** Открыть Messages → должен быть search bar сверху → ввести «макс» → должны отфильтроваться чаты с «Макс» в имени → стереть → полный список → таб «Непрочитанные» → только непрочитанные.

---

## P2 — Вложения и multi-select

### P2.1. Video attachment playback

**Проблема:** `VideoAttachmentCard` — кликабельный, но `onClick = {}`.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — `VideoAttachmentCard(video, textColor, onClick = { nav?.navigate(Screen.VideoPlayer.buildRoute(video.id, video.ownerId, video.accessKey ?: "")) })`

**Шаги:**
1. В `ChatDetailScreen` signature добавить `onVideoClick: (Video) -> Unit = {}` (default no-op для backward compat)
2. В `SovaNavHost` — wire `onVideoClick = { video -> nav.navigate(Screen.VideoPlayer.buildRoute(video.id, video.ownerId, video.accessKey ?: "")) }`
3. В `VideoAttachmentCard` — добавить `onClick` параметр, передать в Card.clickable

**Риски:** Video может быть приватным (нужен access_key). Проверить что `VideoPlayerScreen` принимает access_key.

**Feature-flag:** Без флага — это fix.

**Проверка:** Открыть чат с видео-вложением → тап на превью → должен открыться VideoPlayerScreen.

---

### P2.2. Audio attachment playback (через PlayerConnection)

**Проблема:** `AudioAttachmentRow` — иконка play декоративная.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — `AudioAttachmentRow(track, textColor, onPlayClick = { app.playerConnection?.playTrack(track) })`
- `media/PlayerConnection.kt` — проверить что есть `playTrack(track: Track)` (есть)

**Шаги:**
1. В `AudioAttachmentRow` signature добавить `onPlayClick: () -> Unit = {}`
2. Иконка play → `IconButton(onClick = onPlayClick)`
3. В `ChatDetailScreen` — передать `onPlayClick = { app.playerConnection?.playTrack(track) }`

**Риски:** `Track` из attachment может не иметь URL (нужно fetch через `audio.getById`). Проверить, что `PlayerConnection.playTrack` сам резолвит URL.

**Feature-flag:** Без флага — это fix.

**Проверка:** Открыть чат с аудио-вложением → тап на play → должен запуститься GlobalMiniPlayer.

---

### P2.3. Poll voting UI

**Проблема:** `PollAttachmentRow` — показывает опрос, но голосовать нельзя. API `polls.addVote` уже есть.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — `PollAttachmentRow(poll, textColor, onVote = { answerId -> scope.launch { app.apiClient.pollsAddVote(poll.id, poll.ownerId, listOf(answerId)) } })`
- Добавить state `votedAnswers: MutableState<Set<Int>>` — локально отмечаем проголосованные

**Шаги:**
1. В `PollAttachmentRow` signature добавить `onVote: (Int) -> Unit = {}`
2. Каждую answer обернуть в `RadioButton` (или `Checkbox` если `poll.multiple`)
3. На тап → `onVote(answer.id)` + локально `votedAnswers.add(answer.id)`
4. После голосования — показать результаты (progress bars по `answer.votes / totalVotes`)

**Риски:** `poll.isAnonymous` — если true, не показывать кто голосовал. `poll.isVoted` — если уже голосовал, показывать результаты сразу.

**Feature-flag:** Без флага — это fix.

**Проверка:** Открыть чат с опросом → тап на ответ → должен проголосовать → показать результаты.

---

### P2.4. Wall attachment click → PostDetail

**Проблема:** `WallAttachmentCard` — `onWallClick = {}` (никогда не wired).

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — `WallAttachmentCard(post, profiles, groups, textColor, onClick = { onPostClick(post) })`
- В `ChatDetailScreen` signature добавить `onPostClick: (Post) -> Unit = {}`
- В `SovaNavHost` — wire `onPostClick = { post -> nav.navigate(Screen.PostDetail.buildRoute(post.ownerId, post.id)) }`

**Шаги:**
1. Найти `onWallClick = {}` в `MessageBubble` → заменить на реальный callback
2. Добавить `onPostClick` в `ChatDetailScreen` signature
3. Wire в NavHost

**Риски:** `PostDetailScreen` может не существовать (нужно проверить). Если есть `PostDetailScreen(ownerId, postId)` — использовать.

**Feature-flag:** Без флага — это fix.

**Проверка:** Открыть чат с репостом → тап на карточку репоста → должен открыться PostDetail.

---

### P2.5. Multi-select mode

**Проблема:** Нет multi-select (long-press → выбор нескольких сообщений для forward/delete).

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — добавить `selectionMode: Boolean` + `selectedIds: MutableState<Set<Long>>`
- TopAppBar меняется в selection mode: «Выбрано: N» + Delete/Forward/Cancel

**Шаги:**
1. `var selectionMode by remember { mutableStateOf(false) }`
2. `var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }`
3. Long-press на message → `selectionMode = true; selectedIds = setOf(msg.id)`
4. В selection mode: тап на message → toggle `selectedIds.contains(msg.id)` (add/remove)
5. TopAppBar в selection mode: title = «Выбрано: ${selectedIds.size}», actions = Delete (with confirmation) + Forward + Cancel (× icon)
6. На Cancel → `selectionMode = false; selectedIds = emptySet()`
7. На Delete → `scope.launch { app.apiClient.messagesDelete(id, deleteForAll = true) for id in selectedIds }` → обновить историю
8. На Forward → открыть `ForwardDialog` с `selectedIds` как source

**Риски:** Long-press сейчас открывает context menu. Нужно различать: short long-press = context menu, long long-press = enter selection. Или: context menu → «Выбрать» пункт → enter selection.

**Feature-flag:** `SovaPrefs.msgMultiSelect` (default: `false` — opt-in)

**Проверка:** Long-press на сообщении → «Выбрать» → выделить ещё 2 → «Удалить» → подтверждение → удалены.

---

### P2.6. Read receipts (✓ / ✓✓)

**Проблема:** LongPoll эмитит `ReadInbox`/`ReadOutbox`, но UI не показывает статусы прочтения.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — в `MessageBubble` для исходящих сообщений показывать иконку `Done` (✓) или `DoneAll` (✓✓) в зависимости от `message.readState`

**Шаги:**
1. В `MessageBubble` для `message.isOut` — добавить `Icon(if (msg.isRead) Icons.Default.DoneAll else Icons.Default.Done)` рядом с time
2. Когда LongPoll эмитит `ReadOutbox(peerId, upToMsgId)` — обновить `messages = messages.map { if (it.id <= upToMsgId && it.isOut) it.copy(readState = 1) else it }`

**Риски:** `readState` поле уже есть в `Message`. Проверить что парсится из JSON.

**Feature-flag:** `SovaPrefs.msgReadReceipts` (default: `true`)

**Проверка:** Отправить сообщение → должно быть ✓ → когда прочитают на другом устройстве → должно стать ✓✓.

---

## P3 — Новые экраны

### P3.1. ChatInfo screen

**Проблема:** Нет отдельного экрана с members/mute/block/report/shared media.

**Файлы:**
- `ui/screens/im/ChatInfoScreen.kt` — новый экран
- `ui/navigation/Screen.kt` — `object ChatInfo : Screen("chat_info/{peerId}")`
- `ui/navigation/SovaNavHost.kt` — регистрация

**Шаги:**
1. Создать `ChatInfoScreen(peerId: Long, onBack, onUserClick)`:
   - Шапка: аватар + имя + last-seen
   - Раздел «Участники» (для group chat): `messagesGetConversationMembers(peerId)` → список
   - Раздел «Общие медиа»: 3 таба (Фото/Видео/Файлы) → `messagesGetHistoryAttachments(peerId, mediaType)`
   - Раздел «Действия»:
     - «Заглушить уведомления» (toggle) — TODO: нужен API `messages.setConversationPushSettings`
     - «Очистить историю» → `messagesDeleteConversation(peerId)`
     - «Заблокировать пользователя» (только для DM) — TODO: `account.ban`
     - «Пожаловаться» — TODO: `messages.markAsSpam`
     - «Выйти из чата» (только для group) — `messagesRemoveChatUser(chatId, null)`
2. Зарегистрировать route в NavHost
3. В `ChatDetailScreen` TopAppBar → «Еще» menu → «Информация о чате» → navigate to ChatInfo

**Риски:** `messages.setConversationPushSettings` — НЕ реализован в VKApiClient. Добавить как P3.1.1.

**Feature-flag:** `SovaPrefs.msgChatInfo` (default: `true`)

**Проверка:** Открыть чат → «Еще» → «Информация» → должен открыться экран с members/media/actions.

---

### P3.2. Mute/unmute chat

**Проблема:** `Chat.pushSettings` парсится, но нет setter API.

**Файлы:**
- `api/VKApiClient.kt` — добавить `messagesSetConversationPushSettings(peerId: Long, disabled: Boolean, sound: Int?): Boolean`
- `data/model/Models.kt` — расширить `Chat.PushSettings` (уже есть: `disabledUntil`, `disabledForever`, `sound`)

**Шаги:**
1. В `VKApiClient`:
   ```kotlin
   suspend fun messagesSetConversationPushSettings(peerId: Long, disabled: Boolean, sound: Int? = null): Boolean {
       val args = mutableMapOf("peer_id" to peerId.toString())
       if (disabled) args["disabled"] = "1"
       sound?.let { args["sound"] = it.toString() }
       val resp = call("messages.setConversationPushSettings", args)
       return resp?.has("response") == true
   }
   ```
2. В `ChatInfoScreen` — toggle «Заглушить» → `app.apiClient.messagesSetConversationPushSettings(peerId, enabled)`
3. Оптимистично обновить `chat.pushSettings.disabledForever = enabled`

**Риски:** API может не работать с Kate mobile auth (token restrictions). Handle error 15.

**Feature-flag:** `SovaPrefs.msgMute` (default: `true`)

**Проверка:** Открыть ChatInfo → включить «Заглушить» → в MessagesScreen у чата должна появиться иконка bell-off.

---

### P3.3. Folders system (полная реализация)

**Проблема:** Нет папок с чатами.

**Файлы:**
- `ui/screens/im/FoldersSettingsScreen.kt` — новый экран (как File 2 из архива)
- `ui/screens/im/MessagesScreen.kt` — добавить tabs для папок
- `api/VKApiClient.kt` — добавить `messagesGetChatFolders()`, `messagesAddChatFolder(title, chatIds)`, `messagesEditChatFolder(folderId, title, chatIds)`, `messagesDeleteChatFolder(folderId)`
- `data/model/Models.kt` — `ChatFolder(id, title, chatIds, icon?)`

**Шаги:**
1. API: VK имеет `messages.getChatFolders` (verify в VK API docs) — добавить в VKApiClient
2. Models: добавить `ChatFolder` data class
3. UI: FoldersSettingsScreen с FieldGroup «Папки» + «Добавить папку» + рекомендации
4. MessagesScreen: вместо 3 табов (Все/Каналы/Непрочитанные) → динамические табы из `chatFolders` + всегда «Все» в начале
5. Tab click → `chats.filter { it.peer.id in folder.chatIds }`

**Риски:** API `messages.getChatFolders` может быть недокументирован. Альтернатива: клиентский folders (сохранять в `SovaPrefs`).

**Feature-flag:** `SovaPrefs.msgFolders` (default: `false` — opt-in, экспериментально)

**Проверка:** Messages → шестерёнка в табах → FoldersSettingsScreen → «Добавить папку» → выбрать чаты → в MessagesScreen появляется новая вкладка.

---

### P3.4. Channel mode (отдельный экран для каналов)

**Проблема:** Канал сейчас открывается как обычный чат, но VK показывает его как ленту постов.

**Файлы:**
- `ui/screens/im/ChannelScreen.kt` — новый экран (как File 3 из архива)
- `ui/navigation/SovaNavHost.kt` — определить, когда открывать Channel vs ChatDetail (по `peer.type` или `peer.id < 0 && isChannel`)
- `data/model/Models.kt` — расширить `Chat.Peer` полем `isChannel: Boolean`

**Шаги:**
1. В `messagesGetConversations` — парсить `conversation.peer.type == "channel"` или проверять `peer.id` (каналы = negative group ids с определённым флагом)
2. В NavHost `onChatClick`: `if (chat.peer.isChannel) nav.navigate(ChannelScreen) else nav.navigate(ChatDetail)`
3. ChannelScreen: лента постов (как FeedScreen, но с другим data source — `messages.getHistory` с `extended=1`)
4. Каждый пост: `ChannelPostCard` с avatar + meta (views, time) + text + media + reactions + comment button
5. Footer: «Включить уведомления» (mute/unmute)
6. Header: «N подписчиков»
7. Actions menu (7 пунктов из §21.4)

**Риски:** Определение «канал vs group» может быть сложным. VK API возвращает `conversation.peer.type == "channel"` для каналов. Проверить.

**Feature-flag:** `SovaPrefs.msgChannelMode` (default: `false` — opt-in, экспериментально)

**Проверка:** Открыть канал «Время Перемен» → должен открыться ChannelScreen с постами → тап на «Подписчики» → ... → «Еще» → actions menu.

---

### P3.5. Multi-file upload

**Проблема:** Сейчас `PickVisualMedia` позволяет только 1 фото.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — заменить `PickVisualMedia` на `PickMultipleVisualMedia`

**Шаги:**
1. `val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)) { uris -> uris.forEach { uploadAndSend(it) } }`
2. На кнопке attach → `pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))`

**Риски:** API contract изменился в новых activity-result версиях. Проверить.

**Feature-flag:** `SovaPrefs.msgMultiFile` (default: `true`)

**Проверка:** Кнопка attach → выбрать 3 фото → все 3 должны отправиться последовательно.

---

### P3.6. Dual send/mic button (6 состояний)

**Проблема:** Сейчас кнопка отправки всегда показывает send icon.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — state machine для кнопки

**Шаги:**
1. `enum SendButtonState { SUBMIT, MIC, DELETE, EDIT, LOADING, LIMIT }`
2. `val sendState by remember(text, isRecording, isEditing, isSending, isLimit) { derivedStateOf { when { isEditing -> EDIT; isRecording -> DELETE; isSending -> LOADING; isLimit -> LIMIT; text.isBlank() -> MIC; else -> SUBMIT } } }`
3. IconButton с `Icon(when(sendState) { SUBMIT -> Send; MIC -> Mic; DELETE -> Delete; EDIT -> Edit; LOADING -> ProgressIndicator; LIMIT -> Warning })`
4. Long-press на MIC → start recording; release → send; drag-out-and-release → cancel

**Риски:** Long-press для записи сейчас уже реализован для voice message. Нужно объединить логику.

**Feature-flag:** `SovaPrefs.msgDualButton` (default: `false` — opt-in)

**Проверка:** Пустое поле → MIC → long-press → запись → отпустить → отправка. Заполнить поле → SUBMIT → тап → отправка.

---

### P3.7. Bubble-less дизайн (полный рефакторинг MessageBubble)

**Проблема:** Сейчас `MessageBubble` — Material Card. VK использует flat layout.

**Файлы:**
- `ui/screens/im/ChatDetailScreen.kt` — рефакторинг `MessageBubble`

**Шаги:**
1. Добавить `MessageBubbleless` composable как альтернативу
2. Структура: Row { Avatar (только incoming, только если showAvatar) | Column { AuthorName (если showAvatar) | Content | InfoRow(Time + StatusIcon) } }
3. Content — flat (без Card, без rounded corners), только `Text` или attachment rendering
4. В `MessageBubble` — `if (prefs.msgBubbleless) MessageBubbleless(...) else MessageBubbleCard(...)`

**Риски:** БОЛЬШОЙ рефакторинг. Может сломать существующие attachment renderings. Делать осторожно, поэтапно.

**Feature-flag:** `SovaPrefs.msgBubbleless` (default: `false` — opt-in, экспериментально)

**Проверка:** Включить в настройках → сообщения должны стать без карточек → flat layout как в VK web.

---

## P4 — Архитектурные улучшения

### P4.1. LongPoll v14 (mode=1226) ✅

**Проблема:** VK_X_mod использует LP v3, VK web — v14.

**Файлы:**
- `realtime/LongPollClient.kt` — обновить `lp_version=14`, `mode=1226`

**Шаги:**
1. Изменить `lp_version=3` → `lp_version=14` в `messagesGetLongPollServer` и в LP URL
2. Изменить `mode=2` → `mode=1226`
3. Обновить парсер событий: v14 имеет расширенные поля (attachments в NewMessage, chat_settings в DialogUpdate и т.д.)
4. Добавить новые event subtypes если нужно (online_friends, chat_user_info)

**Риски:** Может сломать существующий парсинг. Сначала проверить на тестовом token.

**Feature-flag:** `SovaPrefs.msgLpV14` (default: `false` — opt-in)

**Проверка:** Включить → LongPoll должен работать → сообщения приходят real-time.

---

### P4.2. `pts` persistence + `messages.getLongPollHistory` для backfill ✅

**Проблема:** Между сессиями пропускаются события (всегда fresh start).

**Файлы:**
- `realtime/LongPollClient.kt` — персистить `lastTs` и `lastPts` в `SovaPrefs`
- `api/VKApiClient.kt` — добавить `messagesGetLongPollHistory(pts: Long, ts: Long, fields: String): LongPollHistory`
- `data/model/Models.kt` — `LongPollHistory(messages, conversations, profiles, groups, chats)`

**Шаги:**
1. В `SovaPrefs` — `lpLastTs: Long`, `lpLastPts: Long`
2. На старте: если `lpLastPts > 0` → вызвать `messagesGetLongPollHistory(pts, ts)` → применить все пропущенные события → затем стартовать обычный LongPoll
3. После каждого event → обновлять `lpLastTs` и `lpLastPts`

**Риски:** `messages.getLongPollHistory` может вернуть много данных (десятки сообщений). Limit: `preview_length=0`, `count=200`.

**Feature-flag:** `SovaPrefs.msgLpBackfill` (default: `false` — opt-in)

**Проверка:** Закрыть приложение → отправить себе 5 сообщений с другого устройства → открыть → все 5 должны появиться.

**✅ Реализовано (2026-07-19):**
- `SovaPrefs`: +`msgLpBackfill`, +`lpLastTs`, +`lpLastPts` (snapshot + keys + setters).
- `VKApiClient.messagesGetLongPollHistory(pts, ts, fields, eventsLimit=1000, msgsLimit=200)` +
  `data class LongPollHistory(history, newPts, newTs, messagesCount, conversationsCount)`.
  `history` — массив сырых событий (тот же формат что и LP `updates[]`), обрабатывается
  `LongPollClient.handleEvent` без изменений.
- `LongPollClient`:
  - Конструктор принял `prefs: SovaPrefs? = null` (не ломает существующие вызовы).
  - `lastPts: Long` (volatile) — актуальный pts.
  - `performBackfillIfNeeded(lp)` — при старте цикла, если `msgLpBackfill=true`
    и `lpLastPts > 0` и `lpLastPts < lp.pts` → backfill + replay через `handleEvent`.
  - `performBackfillOnFailed1(newTs)` — на `failed=1` (history outdated) пытается
    восстановить пропущенные события (страховка от wait timeout > 25с).
  - В обработке `failed=0` — обновляем `lastPts` и `persistLpState()` после каждой
    пачки updates (non-blocking, non-fatal).
- `SovaApp`: передает `prefs` в `LongPollClient`.
- `SettingsScreen`: тумблер «Восстановление пропущенных сообщений» (opt-in, default off).
- Все backfill-операции обёрнуты в try/catch с `AppLog.w` — non-fatal.
- `lp_version=3` сохранён (P4.2 ортогонален P4.1 — можно включить независимо).

**Commit:** `P4.2: LongPoll pts persistence + getLongPollHistory backfill`

---

### P4.3. WebSocket transport для каналов — STUB ✅

**Проблема:** VK переводит каналы на WebSocket (`frontend.vkm_new_channels_ws_engine:1`).

**Файлы:**
- `realtime/ChannelWebSocketClient.kt` — новый файл
- `mini-services/` — возможно, отдельный WS service

**Шаги:**
1. Исследовать WS protocol VK (из JS-бандлов `vendors~vk.8159f6ce85741948.js`)
2. Реализовать WS client (OkHttp WebSocket)
3. Заменить LongPoll на WS для каналов (peer.type == "channel")
4. Для обычных чатов — оставить LongPoll

**Риски:** WS protocol недокументирован. Может измениться без notice.

**Feature-flag:** `SovaPrefs.msgWsChannels` (default: `false`)

**Проверка:** Открыть канал → сообщения должны приходить через WS в real-time.

---

### P4.4. `execute.*` batching ✅

**Проблема:** Multi-step flows (upload photo, send voice) делают 3-4 HTTP round-trips.

**Файлы:**
- `api/VKApiClient.kt` — добавить `execute(script: String): JsonObject`

**Шаги:**
1. Добавить метод `execute(script: String)` — отправляет VKScript на `execute` endpoint
2. Для photo upload: вместо 3 отдельных вызовов — 1 execute с VKScript:
   ```
   var upload = API.photos.getMessagesUploadServer({peer_id: ...});
   var uploaded = API.uploadPhoto(upload.upload_url, photo);
   var saved = API.photos.saveMessagesPhoto({server: uploaded.server, photo: uploaded.photo, hash: uploaded.hash});
   API.messages.send({peer_id: ..., attachment: "photo" + saved[0].owner_id + "_" + saved[0].id});
   ```
3. ⚠️ `photos.saveMessagesPhoto` в `executeUnsupportedMethods` — НЕЛЬЗЯ batch'ить!
4. Для voice upload — то же ограничение (`docs.save` в списке)

**Риски:** Многие методы НЕ могут быть batch'ены (см. §23.6). Реальная выгода — для последовательностей get + send.

**Feature-flag:** `SovaPrefs.msgExecuteBatch` (default: `false`)

**Проверка:** Отправить фото → должно быть быстрее (1 round-trip вместо 3, где возможно).

---

## Итоговая таблица приоритетов

| ID | Задача | Приоритет | Время | Feature-flag | Риски |
|----|-------|-----------|-------|--------------|-------|
| P0.1 | Typing indicator | P0 | 1-2ч | `msgTypingIndicator` | Низкие |
| P0.2 | Notifications: имя + auto-cancel | P0 | 2-3ч | (fix) | Низкие |
| P0.3 | Pin message UI | P0 | 2-3ч | `msgPinBar` | Низкие |
| P1.1 | Date separators + FAB | P1 | 3-4ч | `msgDateSeparators` + `msgScrollFab` | Средние (ExperimentalFoundationApi) |
| P1.2 | Reply via swipe | P1 | 3-4ч | `msgSwipeReply` | Средние (gesture conflict) |
| P1.3 | Message grouping | P1 | 2-3ч | `msgGrouping` | Низкие |
| P1.4 | Search + tabs в MessagesScreen | P1 | 4-5ч | `msgSearch` | Средние (new API) |
| P2.1 | Video attachment playback | P2 | 1-2ч | (fix) | Низкие |
| P2.2 | Audio attachment playback | P2 | 1-2ч | (fix) | Низкие |
| P2.3 | Poll voting UI | P2 | 2-3ч | (fix) | Низкие |
| P2.4 | Wall attachment click | P2 | 1ч | (fix) | Низкие |
| P2.5 | Multi-select mode | P2 | 5-6ч | `msgMultiSelect` | Средние |
| P2.6 | Read receipts (✓/✓✓) | P2 | 2-3ч | `msgReadReceipts` | Низкие |
| P3.1 | ChatInfo screen | P3 | 1-2 дня | `msgChatInfo` | Высокие (new screen) |
| P3.2 | Mute/unmute chat | P3 | 2-3ч | `msgMute` | Средние (new API) |
| P3.3 | Folders system | P3 | 1-2 дня | `msgFolders` | Высокие (new API, может не работать) |
| P3.4 | Channel mode | P3 | 2-3 дня | `msgChannelMode` | Высокие (new screen) |
| P3.5 | Multi-file upload | P3 | 1-2ч | `msgMultiFile` | Низкие |
| P3.6 | Dual send/mic button | P3 | 3-4ч | `msgDualButton` | Средние |
| P3.7 | Bubble-less дизайн | P3 | 1-2 дня | `msgBubbleless` | Высокие (большой рефакторинг) |
| P4.1 | LongPoll v14 ✅ | P4 | 1 день | `msgLpV14` | Высокие (может сломать парсинг) |
| P4.2 | `pts` + backfill ✅ | P4 | 1 день | `msgLpBackfill` | Средние |
| P4.3 | WebSocket для каналов ✅ (stub) | P4 | 1 неделя | `msgWsChannels` | Очень высокие (недокументировано) |
| P4.4 | `execute.*` batching ✅ | P4 | 2-3 дня | `msgExecuteBatch` | Средние (ограничения execute) |

---

## Рекомендуемый порядок внедрения

### Sprint 1 (1 неделя) — P0 + быстрые P2

1. P0.1 Typing indicator (1-2ч)
2. P0.2 Notifications fix (2-3ч)
3. P0.3 Pin message UI (2-3ч)
4. P2.1 Video attachment playback (1-2ч)
5. P2.2 Audio attachment playback (1-2ч)
6. P2.3 Poll voting UI (2-3ч)
7. P2.4 Wall attachment click (1ч)

**Итог спринта:** ~10-15 часов, 7 фич, всё безопасно (fixes + простые фичи).

### Sprint 2 (1 неделя) — P1

8. P1.3 Message grouping (2-3ч)
9. P1.1 Date separators + FAB (3-4ч)
10. P1.2 Reply via swipe (3-4ч)
11. P1.4 Search + tabs в MessagesScreen (4-5ч)
12. P2.6 Read receipts (2-3ч)

**Итог спринта:** ~15-19 часов, 5 фич, стандартный UX мессенджера.

### Sprint 3 (2 недели) — P2 + начало P3

13. P2.5 Multi-select mode (5-6ч)
14. P3.5 Multi-file upload (1-2ч)
15. P3.6 Dual send/mic button (3-4ч)
16. P3.2 Mute/unmute chat (2-3ч)
17. P3.1 ChatInfo screen (1-2 дня)

**Итог спринта:** ~25-30 часов, 5 фич, продвинутый UX.

### Sprint 4 (3-4 недели) — P3 (экспериментальное) — ✅ ВЫПОЛНЕН

18. ✅ P3.3 Folders system (1-2 дня) — commit `4e913e86a`
19. ✅ P3.4 Channel mode (2-3 дня) — commit `a5f356a16`
20. ✅ P3.7 Bubble-less дизайн (1-2 дня) — commit `c02602799`

**Итог спринта:** Sprint 4 завершён. Все 3 экспериментальные фичи реализованы как opt-in
(feature-flags default false/true). VK_IMPORT_API.MD обновлён с описанием API и UI-паттернов.
Полный прогресс: 20/24 (83%). Осталось Sprint 5 (архитектура P4 — LongPoll v14, pts+backfill,
execute batching, WebSocket) — опционально.

### Sprint 5 (по желанию) — P4 (архитектура) — ✅ ВЫПОЛНЕН

21. ✅ P4.1 LongPoll v14 — commit `49a1cba0c`
22. ✅ P4.2 pts + backfill — commit `8e58083a7`
23. ✅ P4.4 execute batching — commit `3933aa7a9`
24. ✅ P4.3 WebSocket для каналов (STUB) — commit `0a06eeb2a`

**Прогресс Sprint 5:** 4/4 (100% завершено). Overall: 24/24 (100%).

⚠️ P4.3 — заготовка (STUB), не интегрирована в основной flow. Активация только
когда VK форсирует отказ от LongPoll для каналов. Все остальные фичи Sprint 5
функциональны и opt-in (default false).

---

## Финальные заметки

1. **Каждый коммит — отдельная фича.** Не группировать несколько фич в один PR.
2. **Feature-flags по умолчанию `false` для P3/P4** (opt-in), `true` для P0/P1/P2 (включены сразу).
3. **После каждого спринта** — обновлять `HISTORY.md` с описанием новых фич.
4. **Тестировать на реальном устройстве** — не только в эмуляторе.
5. **Не удалять старый код** — только добавлять. Если `MessageBubble` рефакторится в `MessageBubbleless`, оставить обе реализации под feature-flag.
6. **CODING_STYLE.md соблюдать** — никаких `!!`, `?:` только для JSON/DataStore.

*Документ создан: 2026-07-18*
*Основан на: VK_IMPORT_API.MD ЧАСТЬ 21-23 + аудит VK_X_mod*
