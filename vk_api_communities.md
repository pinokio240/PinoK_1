# VK API: Сообщества (Groups/Communities)

## Обзор

Сообщества в VK — это группы, публичные страницы и события. API предоставляет полный CRUD для управления сообществами, их стеной, участниками и настройками.

---

## Текущая реализация в приложении

### Экраны

| Экран | Файл | Назначение |
|-------|------|------------|
| `GroupsScreen` | `ui/screens/groups/GroupsScreen.kt` | Список сообществ пользователя (вкладка "Сообщества") |
| `CommunityScreen` | `ui/screens/community/CommunityScreen.kt` | Детальная страница сообщества: заголовок + стена |

### Модели данных

```kotlin
// Лёгкая модель (feed + CommunityScreen)
data class GroupInfo(
    val id: Long, val name: String, val screenName: String?,
    val photo100: String?, val photo200: String?,
    val isClosed: Int, val isMember: Int, val verified: Int,
)

// Полная модель (GroupsScreen)
data class Group(
    val id: Long, val name: String, val screenName: String?,
    val isClosed: Int, val type: String?,
    val photo100: String?, val photo200: String?,
    val membersCount: Int, val description: String?,
    val status: String?, val verified: Int,
    val isMember: Int, val canPost: Int,
    val canSeeAllPosts: Int, val activity: String?, val site: String?,
)
```

### Навигация

```
FeedScreen → onAuthorClick(post) → if fromId < 0 → onGroupClick(-fromId)
  → nav.navigate("community/{groupId}")
  → CommunityScreen(groupId, onBack, onVideoClick, onPostClick, onUserClick)

GroupsScreen → onGroupClick(group.id)
  → nav.navigate("community/{groupId}")
```

---

## VK API: Методы по сообществам

### Реализованные

| Метод | Файл | Описание |
|-------|------|----------|
| `groups.get` | VKApiClient.kt:2390 | Список сообществ пользователя |
| `groups.getById` | VKApiClient.kt:2444 | Метаданные по ID (name, photo, is_member...) |
| `groups.join` | VKApiClient.kt | Вступить в сообщество |
| `groups.leave` | VKApiClient.kt | Покинуть сообщество |
| `wall.get` | VKApiClient.kt | Посты со стены сообщества (ownerId=-groupId) |

### Не реализованные (потенциально нужны)

| Метод | Описание | Зачем |
|-------|----------|-------|
| `groups.search` | Поиск сообществ | Поиск из GroupsScreen |
| `groups.getMembers` | Участники сообщества | Экран "Участники" |
| `groups.isMember` | Проверка членства | Оптимизация |
| `wall.post` | Публикация на стене | Кнопка "Написать на стену" |
| `wall.getComments` | Комментарии к посту | Комментарии в CommunityScreen |
| `board.getTopics` | Обсуждения | Раздел "Обсуждения" |
| `photos.get` | Фото сообщества | Раздел "Фото" |
| `docs.get` | Документы сообщества | Раздел "Документы" |
| `video.get` | Видео сообщества | Раздел "Видео" |

---

## Структура ответов API

### groups.get

**Запрос:** `groups.get?count=50&offset=0&extended=1&fields=...`

**Ответ:**
```json
{
  "response": {
    "count": 42,
    "items": [
      {
        "id": 123456,
        "name": "Название группы",
        "screen_name": "club123456",
        "is_closed": 0,
        "type": "group",
        "photo_100": "https://...",
        "photo_200": "https://...",
        "members_count": 15000,
        "description": "Описание...",
        "status": "Статус группы",
        "verified": 1,
        "is_member": 1,
        "can_post": 1,
        "activity": "Активность...",
        "site": "https://example.com"
      }
    ]
  }
}
```

### groups.getById

**Запрос:** `groups.getById?group_ids=123456,789012&fields=...`

**Ответ — вариант 1 (JsonArray — самый частый):**
```json
{
  "response": [
    { "id": 123456, "name": "Название", "photo_100": "...", "photo_200": "...", "is_closed": 0, "is_member": 1, "verified": 0 }
  ]
}
```

**Ответ — вариант 2 (JsonObject с items — редкий):**
```json
{
  "response": { "count": 1, "items": [{ "id": 123456, "name": "Название", ... }] }
}
```

> **ВАЖНО:** `groups.getById` может вернуть ЛЮБОЙ формат. Код обрабатывает оба (try-catch ClassCastException в VKApiClient.kt:2444).

### wall.get (для сообщества)

**Запрос:** `wall.get?owner_id=-123456&count=30&offset=0`

**Ответ:**
```json
{ "response": { "count": 500, "items": [...], "profiles": [...], "groups": [...] } }
```

> `owner_id` для групп — ОТРИЦАТЕЛЬНЫЙ (`-groupId`).

### groups.join / groups.leave

**Запрос:** `groups.join?group_id=123456`
**Ответ:** `{ "response": 1 }`

---

## Дерево зависимостей

```
SovaNavHost.kt
├── GroupsScreen (вкладка "Сообщества")
│   ├── VKApiClient.groupsGet() → List<Group>
│   ├── VKApiClient.groupsJoin/Leave(groupId)
│   └── навигация → CommunityScreen
│
├── CommunityScreen (детальная страница)
│   ├── VKApiClient.groupsGetById([groupId]) → List<GroupInfo>
│   ├── VKApiClient.wallGet(ownerId=-groupId) → List<Post>
│   ├── VKApiClient.likesAdd/Delete(type="post")
│   ├── навигация → PostDetailScreen / VideoPlayerScreen / UserProfileScreen
│
└── FeedScreen (точка входа)
    └── onAuthorClick(post) → if fromId < 0 → onGroupClick(-fromId) → CommunityScreen

Модели: GroupInfo (8 полей, feed) + Group (15 полей, список) + Post (переиспользуется)
```

---

## Возможные причины "сообщества не открываются"

Анализ кода не выявил краш-багов. Без логов конкретной попытки:

1. **Сеть/DNS** — лог показывает `Unable to resolve host "api.vk.com"`
2. **Токен истёк** — `call()` вернёт null → "Сообщество не найдено"
3. **Закрытое сообщество** (`is_closed=2`) — `wallGet` вернёт пустой список
4. **API error** (rate limit, captcha) → null response

**Нужно:** лог после попытки открыть сообщество (строки `CommunityScreen: Loaded group...` или `CommunityScreen: Failed to load community`).

---

## План развития сообществ

### P0
1. Диагностика: логи при открытии сообщества
2. Показать `members_count`, `description` в CommunityScreen (данные запрашиваются но GroupInfo не содержит эти поля — нужно расширить модель)
3. Обработка `is_closed=2` — экран "Закрытое сообщество"

### P1
4. `groups.search` — поиск из GroupsScreen
5. `wall.post` — публикация на стене
6. `groups.getMembers` — экран участников

### P2
7. Фото/видео/документы сообщества
8. Обсуждения (`board.getTopics`)
9. Настройки уведомлений