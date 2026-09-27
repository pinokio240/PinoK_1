# ADMIN-POST-ACTIONS — действия администратора над постом сообщества

**Дата:** 2026-09-27
**Статус:** ВЫПОЛНЕНО, сборка успешна
**Источники HAR:**
- `C:/Users/Pinokio240/Desktop/Ссылки/админка/сообщество действи с постами.har` (53.71 МБ, 27.09 14:01) — базовый
- `C:/Users/Pinokio240/Desktop/Ссылки/админка/сообщество действи с постами 1.har` (6.76 МБ, 27.09 15:04) — уточняющий

## 1. Методы из HAR

### HAR №1 (`сообщество действи с постами.har`)
22 уникальных методов, 77 вызовов. Ключевые:
- `owners.pinToMainTab` / `owners.removeFromMainTab`
- `wall.delete` / `wall.restore` / `wall.edit`
- `wall.openComments` / `wall.closeComments`
- `owners.getMainTab`, `wall.get`, `utils.resolveScreenName`, `photos.getWallUploadServer`

### HAR №2 (`сообщество действи с постами 1.har`)
15 уникальных методов, 54 вызова.
**ГЛАВНОЕ ОТКРЫТИЕ:** VK-web зовёт **`owners.unpinFromMainTab`**, НЕ `owners.removeFromMainTab`!

## 2. Форматы API (подтвержденные HAR)

| Метод | params | ответ |
|---|---|---|
| `wall.delete` | `owner_id=-groupId`, `post_id`, `creation_entry_point` | `{response:1}` |
| `wall.restore` | `owner_id=-groupId`, `post_id` | `{response:1}` |
| `wall.edit` | `message`, `post_id`, `close_comments`, `publish_date`, `primary_attachments`, `owner_id=-groupId` | `{response:{post_id}}` |
| `wall.openComments` | `owner_id=-groupId`, `post_id` | `{response:1}` |
| `wall.closeComments` | `owner_id=-groupId`, `post_id` | `{response:1}` |
| `owners.pinToMainTab` | `owner_id=-groupId`, `item_type=post`, `item_id=<owner_post>` | `{response:1}` |
| `owners.unpinFromMainTab` | `owner_id=-groupId`, `item_type=post`, `item_id=<owner_post>` | `{response:1}` |

## 3. Реализация

### VKApiClient.kt — API-методы
- `wallOpenComments(groupId, postId)`
- `wallCloseComments(groupId, postId)`
- `ownersPinToMainTab(groupId, postId)`
- `ownersRemoveFromMainTab(groupId, postId)` → внутри вызывает **`owners.unpinFromMainTab`** (багфикс 27.09)
- `wallDelete`, `wallRestore`, `wallEdit` — уже были

### UI-компоненты
- **`ui/screens/community/PostActionsMenu.kt`** (135 строк) — AlertDialog с пунктами:
  - Редактировать (если `onEdit != null`)
  - Удалить (если `onSoftDelete != null` → мягкое удаление, иначе → `wallDelete`)
  - Открыть/Закрыть комментарии (по `isCommentsClosed`)
  - Закрепить/Убрать из «Главное» (по `isPinnedInMain`)
  - **НЕТ пункта «Восстановить»** — он живёт только на стрипе (решение юзера 27.09, как в VK-web)
- **`ui/screens/community/SoftDeleteStrip.kt`** — стрип «Запись удалена (N)» + кнопка «Восстановить»

### Интеграция в `CommunityScreen.kt`
- Кнопка «⋮» (MoreVert) в шапке карточки — только для менеджеров (`g.isManager`)
- state: `menuPost`, `editingPost`, `softDeletedPost`, `softDeleteSeconds`
- toggle-состояния: `isPinnedInMain = mp.isPinned == 1`, `isCommentsClosed = (mp.comments?.canPost ?: 1) == 0`
- Стрип: таймер 10с → по истечении `wallDelete` + `refreshWall`; «Восстановить» → просто сброс
- Редактирование: `editingPost` → `CreatePostDialog` с `initialText` + `submitLabel=«Сохранить»` → `wallEdit` вместо `wallPost`

### `CreatePostDialog.kt`
Новые параметры:
- `initialText: String = ""` — предзаполнение текста (через `remember(initialText)`)
- `submitLabel: String = «Опубликовать»`

## 4. Багфиксы сессии
1. **`owners.removeFromMainTab` → `owners.unpinFromMainTab`** — пункт «Убрать из Главное» не работал.
2. **PostActionsMenu.kt повреждён вставкой** — переводы строк склеились, `onSoftDelete` попал в `//`-комментарий → Unresolved reference. Файл перезаписан целиком.

## 5. Бэкапы
- `VKApiClient.kt.bak_post_actions`
- `CommunityScreen.kt.bak_post_actions_ui`

## 6. Остаётся (не блокеры)
- Стрип только в списке сообщества (не в PostDetailScreen) — как VK-web, отдельная правка при необходимости.
