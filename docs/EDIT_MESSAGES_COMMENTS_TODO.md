# TODO — редактирование сообщений и комментариев

**Дата аудита:** 2026-09-27
**Статус:** НЕ РЕАЛИЗОВАНО (API есть, UI отсутствует)

## 1. Обычные диалоги — РАБОТАЕТ

- API: `VKApiClient.messagesEdit(peerId, cmid, message, keepForwardMessages=true, groupId=null, forceWebGateway=false)` — VKApiClient.kt:6130
  - шлёт `messages.edit {peer_id, cmid, message, keep_forward_messages=1, keep_snippets=0}`
- UI: `ChatDetailScreen.editMessage()` — ChatDetailScreen.kt:1549-1567
  - `editRef = msgCmid если >0 иначе messageId` (фолбэк)
  - `messagesEdit(peerId, editRef, newText)`; при успехе `reloadMessages()`
- Реалтайм: `LongPollClient.kt:885` event 5 → `LongPollEvent.EditMessage(peerId, msgId)`; учитывается в `MessagesScreen.kt:605`

## 2. ЧАТЫ (групповые беседы) — ТРЕБУЕТ ПРОВЕРКИ/ФИКСА

Чаты идут через тот же `ChatDetailScreen.editMessage()` — отдельной ветки нет.
`localChatId = peerId - 2000000000L` (ChatDetailScreen.kt:670) используется только для управления чатом
(rename/removeUser), НЕ для правки сообщения.

**ПРОБЛЕМА:** `editMessage` не передаёт `groupId` и `forceWebGateway` (оба default).
Для чата `peer_id = 2000000000 + chatId` обычно работает, но при отказах api.vk.com
в group-контексте правка падает молча (`else AppLog.w("edit failed")`).

**TODO:** в `ChatDetailScreen.editMessage` для групповых чатов передавать `groupId`/`forceWebGateway`
(как в `messagesEditChat`).

## 3. КОММЕНТАРИИ / ОТВЕТЫ В ПОСТАХ — НЕ РЕАЛИЗОВАНО

**API ЕСТЬ:** `VKApiClient.wallEditComment(ownerId, commentId, message, attachments=null)` — VKApiClient.kt:4218
(шлёт `wall.editComment`). Но НИ РАЗУ не вызывается в UI.

**UI-пробелы:**
- `CommentItem` (PostDetailScreen.kt:1393) — колбэки `onReply`, `onVideoClick`, `onPhotoClick`,
  `postOwnerId`, `parentComment`, `isExpanded`. НЕТ `onEdit` / `onDeleteComment`.
- `CommentsBottomSheet` (FeedScreen.kt:3324) — есть `replyingToComment`, НЕТ `editingComment`.
- `AdminCommentsScreen` — есть `wallDeleteComment` + `wallCreateComment`, но НЕ `wallEditComment`.

**TODO:**
1. Добавить `onEdit: (() -> Unit)?` в `CommentItem`.
2. В `CommentsBottomSheet` и `PostDetailScreen` — состояние `editingComment: Comment?`,
   предзаполнение поля ввода текстом комментария, кнопка «Сохранить».
3. Вызывать `wallEditComment(ownerId = post.ownerId, commentId = comment.id, message = newText)`.
4. Опционально — в `AdminCommentsScreen` тоже добавить «Редактировать».

## 4. Прочее (для полноты)

- Кириллица/markdown: проверять экранирование при правке (как в отправке).
- Реалтайм-обновление комментария после правки — перезагрузка `wallGetComments`.