# Разбор vk.ru_2609.har — чего не хватает в PinoK

Источник: C:/Users/Pinokio240/Desktop/vk.ru_2609.har (70 868 953 б, снят 2026-09-26 20:46).
Записей: 1769. Уникальных VK API методов: 87. Есть в VKApiClient.kt: 41. НЕТ: 40.

## 1. Топ методов по частоте

| Метод | Вызовов | В PinoK |
|---|---:|---|
| settingsGeneral.getNotifySettings | 174 | да |
| settingsGeneral.setNotifySettings | 170 | да (падает err=3) |
| groups.setGroupSettings | 57 | да |
| statsDashboard.getOwnerStats | 39 | НЕТ (async) |
| groups.getGroupSettings | 35 | да |
| groups.getContentForTabs | 30 | НЕТ |
| groups.getById | 22 | да |
| owners.getContentTabs | 15 | НЕТ |
| appWidgets.get | 8 | НЕТ |
| donut.getInfo | 8 | НЕТ |

## 2. Ключевые находки

### 2.1 Статистика АСИНХРОННАЯ
statsDashboard.getOwnerStats возвращает {response:{success:true, task_id:"...", task_jwt_id:"..."}}.
Параметры: type=stat_board, act=layout|data, section=top_community|top_posts|top_stories|top_clips,
sub_section=stat_board_general|stat_board_audience|stat_board_content, card_id, sort_by,
owner_id=-165284550, period=last7Days, sdate, edate.
Результат приходит через queuev4.vk.ru (374 запроса в HAR) + stats.vk-portal.net (84). WebSocket нет.
=> Полная статистика = подсистема async task polling. НЕ реализовано.

### 2.2 СИНХРОННЫЕ методы статистики — ВНЕДРЕНЫ
- statsDashboard.getBootstrapData {type=stat_board, owner_id} -> owner{id,name,avatarUrl,isActive,domain} + profile{id,name,avatarUrl}
- statsDashboard.getDashboardSections {type=stat_board, owner_id} -> hash + sections[{id,name,subsections[{id,name,disable_range}]}]

### 2.3 owners.getContentTabs — ВНЕДРЁН
{tabs_configuration:{<key>:{base_configuration:{content_types:[],can_add,can_move_to_section,order}}}}
Ключи: posts, videos, photos, market, audios.

## 3. Что уже внедрено (2026-09-26)

| Что | Файл | Статус |
|---|---|---|
| statsDashboardGetBootstrapData | VKApiClient.kt | OK |
| statsDashboardGetDashboardSections | VKApiClient.kt | OK |
| ownersGetContentTabs | VKApiClient.kt | OK |
| AdminStatsScreen: шапка + секции, не падает на пустом stats | CommunityAdminScreens.kt | OK |
| AdminSectionsScreen: блок «Табы сообщества» | AdminSectionsScreen.kt | OK |
| Регистрация 10 админ-роутов | SovaNavHost.kt | OK |

## 4. Осталось

### P0
- statsDashboard.getOwnerStats (ASYNC! + queuev4) — отдельная подсистема
- groups.getContentForTabs — контент табов
- groups.getGroupSettingsMenu, groups.getSuggestions
- owners.getContentSections, owners.getVideoContent
- photos.getGroupMenuCoverUploadServer / saveGroupMenuCoverPhoto
- groups.addChat, businessGroups.getShieldSettings, notifications.setGroupSettings, internal.getWebLeftMenu

### P1
- donut.getInfo / getSettings / getLevels, appWidgets.get
- messages.getChatOnline / getRecentStickers / getReactionsAssets, stickers.getSettings
- wall.getCommentsForPosts, wall.getSubscriptions

### P2
- payments.*, channels.getOwnersForCreate / getPinnedMessages
- account.getInfo / getProfileNavigationInfo / getHelpHints
- settingsGeneral.getAccountSettings, utils.getReleaseVersionsInfo
- store.*, vmoji.getAvatar, onboarding.get, market.getAdult18Plus

## 5. Прямое доказательство

PinoK звал legacy stats.get -> err=7 Permission denied.
VK-web зовёт statsDashboard.getOwnerStats (39 раз).

## 6. Бэкапы

- VKApiClient.kt.bak_stats1
- CommunityAdminScreens.kt.bak_stats2
- AdminSectionsScreen.kt.bak_sections1


## 8. Состояние на 2026-09-26 (конец дня)

### Внедрено и записано на диск
- **VKApiClient.kt** (1 109 263 б): добавлены suspend-методы `statsDashboardGetBootstrapData(ownerId)`,
  `statsDashboardGetDashboardSections(ownerId)`, `ownersGetContentTabs(ownerId)`;
  типы `StatsOwner`, `StatsBootstrap`, `StatsSubsection`, `StatsSection`, `ContentTabConfig`.
  Все вызовы — `forceWebGateway = true` (web.api.vk.ru).
- **CommunityAdminScreens.kt** (158 316 б): `AdminStatsScreen` — state `boot`/`sections`,
  загрузка sync-методов ДО legacy `statsGet`; условие ошибки теперь `stats.isEmpty() && boot == null`
  (раньше падал на пустом stats из-за `stats.get err=7`); в UI добавлена шапка (имя сообщества + секции).
- **AdminSectionsScreen.kt** (25 614 б): блок «Табы сообщества» из `ownersGetContentTabs`
  (state `tabs`, отдельный scope.launch в load(), UI: key + contentTypes + #order);
  добавлен импорт `androidx.compose.ui.text.font.FontWeight`.

### Сборка
- Первая проверка (после правок VKApiClient + AdminStatsScreen) — **ПРОШЛА**.
- Правки AdminSectionsScreen сборкой **НЕ проверены** (следующая сборка).

### Бэкапы
- `VKApiClient.kt.bak_stats1`
- `CommunityAdminScreens.kt.bak_stats2`
- `AdminSectionsScreen.kt.bak_sections1`

### Следующие шаги
1. Собрать, проверить `AdminSectionsScreen` (новый блок tabs + импорт FontWeight).
2. `groups.getContentForTabs` — контент табов (30 вызовов в HAR).
3. Async-статистика: `statsDashboard.getOwnerStats` + очередь `queuev4.vk.ru` (374 запроса) — отдельная подсистема.
4. P0 остаток: `groups.getGroupSettingsMenu`, `groups.getSuggestions`, `owners.getContentSections`,
   `owners.getVideoContent`, `photos.getGroupMenuCoverUploadServer`/`saveGroupMenuCoverPhoto`,
   `groups.addChat`, `businessGroups.getShieldSettings`, `notifications.setGroupSettings`, `internal.getWebLeftMenu`.
5. P1: `donut.*`, `appWidgets.get`, `messages.getChatOnline`, `stickers.getSettings`,
   `wall.getCommentsForPosts`, `wall.getSubscriptions`.

### Источник
C:/Users/Pinokio240/Desktop/vk.ru_2609.har (70 868 953 б).
