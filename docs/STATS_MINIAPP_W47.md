# ADMIN-STATS-W47 — статистика сообщества через Mini App 51912452

**Дата:** 2026-09-27
**Статус:** код записан, сборка за пользователем
**Источник:** C:/Users/Pinokio240/Desktop/Ссылки/админка/статистика.har (29.5 МБ, 275 запросов)

## 1. Главное открытие

Статистика сообщества — это VK Mini App app_id=51912452 (внутреннее имя community_dashboard),
а НЕ страница сообщества. При нажатии «Статистика» VK-web переходит на https://m.vk.ru/app51912452#/-<groupId>.

Старый PinoK-экран звал legacy stats.get -> err=7 Permission denied. Новые данные — только через
async-подсистему Mini App.

## 2. Полный поток данных

1. id.vk.ru/mini_app_token?app_id=51912452&access_token=<токен> -> отдельный Mini App токен
2. statsDashboard.getBootstrapData {type=stat_board_mobile, owner_id=-gid} -> owner, profile, exportConfig
3. statsDashboard.getDashboardSections {type=stat_board_mobile, owner_id=-gid}
     -> sections[top_community|top_posts][stat_board_general|audience|content] + hash
4. queue.subscribe {queue_ids: "vboardcard_<uid>_<gid>_1"}
     -> {base_url: "https://queuev4.vk.ru/im1180", queues:[{key, timestamp}]}
5. statsDashboard.getOwnerStats {type=stat_board_mobile, act=layout, section, sub_section,
                                  owner_id=-gid, period=last7Days, sdate, edate} -> {success, task_id, task_jwt_id}
6. GET {base_url}?act=a_check&key=<key>&ts=<timestamp>&id=<uid>&wait=45
     -> events[{entity_type:"vboardcard", data:{task_id, task_result:{chunk:"<JSON-СТРОКА>"}}}]
7. Для каждой карточки: getOwnerStats {act=data, card_id=<id>} + шаг 6
8. statsDashboard.dropTasks {type, task_jwt_ids, owner_id}

Ключевое: цифры приходят НЕ в ответе API, а асинхронно через long-poll queuev4.vk.ru,
в events[].data.task_result.chunk (JSON-строка, парсится вторым json.loads).

## 3. Реализация (5 шагов)

- Шаг 1 — api/VKEndpoints.kt: statsBoardQueueId(uid, gid) = "vboardcard_<uid>_<gid>_1"
- Шаг 2 — api/VKApiClient.kt: type=stat_board -> stat_board_mobile (2 места); блок #ADMIN-STATS-W47:
  StatsTaskHandle, StatsBoardEvent, statsDashboardGetOwnerStats, statsBoardQueueSubscribe,
  statsBoardQueuePoll, collectBoardEvents, statsDashboardDropTasks
- Шаг 3 — realtime/StatsQueuePoller.kt (новый): long-poll очередь статистики, SharedFlow<StatsBoardEvent>
- Шаг 4a — ui/screens/community/StatsChunkParser.kt (новый): парсер LAYOUT и DATA
- Шаг 4b — ui/screens/community/AdminStatsScreenW47.kt (новый): новый AdminStatsScreen

Старый AdminStatsScreen в CommunityAdminScreens.kt переименован в AdminStatsScreenLegacy.

## 4. Форматы чанков (из HAR)

LAYOUT: {"items":[{type, id, title, isLazy, tabs?}]}. Тип: summary, tabs, advanced_timeline_chart, data_list.

DATA: {"item":{...},"range":{sdate,edate,key}}.
- summary -> summary:[{label, values:[{type:type_main,value},{type:type_relative,value}]}]
- data_list -> items:[{label, value, percentage_value}]
- doughnut_chart -> graph[0].datasets{labels,values,percents}
- advanced_timeline_chart -> graph[0].datasets[0].data[{x,y}]

## 5. Бэкапы и откат

Бэкапы (суффикс .bak_stats_w47): VKEndpoints.kt.bak_stats_w47, VKApiClient.kt.bak_stats_w47,
CommunityAdminScreens.kt.bak_stats_w47.

Откат: вернуть эти 3 файла + удалить 3 новых (StatsQueuePoller.kt, StatsChunkParser.kt, AdminStatsScreenW47.kt).

## 6. Известные ограничения

- mini_app_token не реализован. Если getOwnerStats вернёт ошибку, нужен обмен токена
  id.vk.ru/mini_app_token?app_id=51912452&access_token=<токен>. Сейчас используется обычный токен.
- Графики рендерятся как таблица точек (без визуального графика) — MVP-вариант.
- act=data без card_id (sub_section=content) может вернуть список — обрабатывается частично.