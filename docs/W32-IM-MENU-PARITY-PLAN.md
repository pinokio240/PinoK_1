# Волна 32 — W32-IM-MENU-PARITY: контекстное меню сообщения по снапшоту VK web

Базируется на: сообщение юзера (волна 32) + снапшот `upload/Мессенджер_меню_сообщения.zip` (исследование 32-r) + HEAD `0f619f5a` (волна 31 запушена). 32-b (#IM-LASTSEEN-HEADER) уже выполнен и вошёл в коммит волны 31.

---

## 1. ПЛАН ИЗУЧЕДНОГО (research 32-r, снапшот распакован в /tmp/w32snap)

### 1.1 Карта меню снапшота (личный диалог, исходящее сообщение)
Контейнер MEPopper; пиалет реакций (`MessageReactionPickerExtended`, 7 быстрых + «ещё» more_horizontal_16) и меню `ul role=menu`:

| testid | Текст | Иконка vkuiIcon | Состояние |
|----|----|----|----|
| `vkme_message_reaction_picker_item_1..7` | Сердце/Огонь/Смеюсь до слёз/Большой палец вверх/Неординарно/Вопросы/Плачу | SVG 36px | + кнопка «ещё» |
| `vkme_messages_action_reply` | Ответить | reply_outline_20 | secondary |
| `vkme_messages_action_forward` | Переслать | share_outline_20 + chevron_16 | hover-подменю выбора чата (мобайл: простая кнопка) |
| `vkme_messages_action_mark_important` | Отметить как важное | favorite_outline_20 | secondary |
| `vkme_channel_post_action_copy_text` | Копировать текст | copy_outline_20 | secondary |
| `vkme_messages_action_edit` | Редактировать | write_outline_20 | secondary (только своё) |
| `vkme_messages_action_delete` | Удалить | delete_outline_20 | --danger |
| `vkme_messages_action_select` | Выбрать | check_circle_outline_20 | secondary |

Условия из JS-конструктора (55252): допустимый набор `[forward, reply, delete, unpin/pin_message_for_all, unmark/mark_important, spam, select, edit]`; edit = isEditable && canWrite && !editing; forward = !TTL && !widget; important — не группа/канал-экран; spam — только чужое. Подменю «Переслать» (раскрыто в снапшоте): Избранное → 9 диалогов (сепаратор лички/группы) → «Открыть все чаты».

### 1.2 API пунктов (из JS снапшота) vs PinoK
| Пункт | VK web API | PinoK сегодня |
|----|----|----|
| Реакция | `messages.sendReaction {cmid, peer_id, reaction_id, …}` | `messages.react` (ДРУГОЙ метод), messagesReact :5580 |
| Снятие реакции | `messages.deleteReaction {cmid, peer_id}` | отсутствует (reaction_id=0 через react) |
| Пиалет/счётчики | `messages.getReactionsAssets`, `messages.getMessagesReactions`, `getReactedPeers`, `markReactionsAsRead` | отсутствуют (реакции из поля message.reactions) |
| Ответить | `messages.send` + forward JSON `{conversation_message_ids:[cmid], is_reply:true}` | ЕСТЬ 1:1 (messagesSend, Fix #203c) |
| Переслать | `messages.send` + forward JSON (без is_reply) | ЕСТЬ 1:1 (messagesForward, Fix #295) |
| Важное (сообщение) | `messages.markAsImportant {peer_id, cmids, important:0/1}` | ОТСУТСТВУЕТ (есть только диалоговый markAsImportantConversation Fix #274 — это флажок ДИАЛОГА) |
| Копировать | clipboard + snackbar | ЕСТЬ (:5384) |
| Редактировать | `messages.edit {cmid, peer_id, message, keep_forward_messages, keep_snippets…}` — по **cmid** | `messagesEdit {peer_id, message_id, …}` — по message_id (расхождение; для групп может ломаться, класс Fix #207) |
| Удалить | `messages.delete {peer_id, cmids чанки 100, delete_for_all, spam…}` | ЕСТЬ (messagesDeleteByCmid Fix #207b + spam + restore) |
| Выбрать | UI-selection | ЕСТЬ (P2.5), но только как long-press-перехват |

### 1.3 Root-cause «пропало меню редактировать»
Меню ЦЕЛО (DropdownMenu :5363-5452, «Редактировать» :5406). Пропадает из-за перехвата long-press: `multiSelectAvailable = multiSelectEnabled` (pref `msgMultiSelect`, Настройки «Выбор нескольких сообщений», default false, НО у юзера включён) → все 3 точки long-press (:4662, :4682, :4782) при `msgMultiSelect=true` уходят в `enterSelection`, `contextMsgId` не выставляется → меню недостижимо ВООБЩЕ. В selection-режиме long-press мёртв.

### 1.4 Критичный баг карты реакций
PinoK `REACTION_EMOJIS` :235: 1=👍 2=❤️ 3=😂 4=😭 5=😡 6=🎉 7=🔥 8=😮. VK web (JS 909189): **1=❤️ 2=🔥 3=😂 4=👍 5=💩(«Неординарно») 6=❓ 7=😭**. ⇒ double-click (reactToMessage :3732, задумано ❤️=2) серверно ставит 🔥; весь пикер промахивается мимо серверных id.

---

## 2. ПЛАН ВНЕДРЕНИЯ

Правила волн: API без заглушек; NULL-ЯВНО; Gradle НЕ запускать; .gitignore не трогать; commit/push — мейн-агент; валидация: check-nested-comments + дельта скобок + NULL-скан; worklog append-only.

### 32-a (единый агент, файлы: ChatDetailScreen.kt, VKApiClient.kt, SettingsScreen.kt-текст-тумблера НЕ трогать)
1. **#IM-MENU-LONGPRESS-FIX**: long-press ВСЕГДА открывает контекстное меню (перехват multi-select удалён в 3 точках); «Выбрать» — пункт ВНУТРИ меню (по снапшоту); прямой вход в selection через пункт меню (`enterSelection` с contextMsgId). Pref msgMultiSelect продолжает управлять доступностью selection-режима, но не грабит меню.
2. **#REACTION-WEB-MAP**: REACTION_EMOJIS → web-семантика id (1=❤️ 2=🔥 3=😂 4=👍 5=💩 6=❓ 7=😭 + прочие известные из словаря 909189, где эмодзи однозначен; сомнительные НЕ выдумывать); double-click = ❤️ (id=1).
3. **#REACTION-WEB-API**: VKApiClient + `messagesSendReaction {peer_id, cmid, reaction_id}` / `messagesDeleteReaction {peer_id, cmid}` (web-методы); UI (пикер меню + double-click) переводится на них; старый messagesReact(messages.react) остаётся только если есть другие вызовы (проверить rg; если 0 — пометить @Deprecated-комментарием, не удалять без надобности). Тоггл-семантика: своя реакция на сообщении (message.reactions.user_reaction) → пункт снимает (deleteReaction), иначе ставит (sendReaction).
4. **#IM-MENU-ITEMS**: порядок по снапшоту: пиалет / Ответить / Переслать / Отметить как важное / Копировать текст / Редактировать / Удалить / Выбрать. «Ответить» (reply-стейт композера уже есть — сверить колбэк), «Отметить как важное» = новый API `messagesMarkAsImportant {peer_id, cmids, important}` + honest-тост; условие important — не канал (peer>0 или групповой чат), «Редактировать» — только isOut && canWrite, по **cmid**.
5. **#IM-EDIT-CMID**: messagesEdit → параметр cmid (conversation_message_id из модели; сверить поле в Models.kt) — паритет web, устраняет класс сбоев редактирования в группах (Fix #207-семантика).
6. Пиалет «ещё» (полная сетка 42+ реакций) — ДОЛГ (нужен getReactionsAssets-словарь; снапшот сетку не раскрывает) — в отчёте юзеру.

### Раунд финальный (мейн-агент)
- Валидация всего, HISTORY «Волна 32», worklog, commit+push.

## 3. Риски
- ChatDetailScreen.kt — самый горячий файл репо (8191 строка, несёт 31-e/32-b правки): править ТОЛЬКО регионы меню/long-press/пикера, не трогать шапку/каналы.
- Смена id-карты реакций меняет ЗНАЧЕНИЯ, отправляемые серверу: старые реакции юзера (поставленные старой картой) могут отображаться иначе — честно отметить в отчёте (обратной миграции сервер не даёт).
- messagesEdit cmessage-id→cmid: убедиться, что модель сообщения несёт conversation_message_id во всех путях (getHistory/getById/LP).
