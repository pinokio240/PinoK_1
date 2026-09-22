# VK web: calls queue.subscribe — точный эталон (2026-09-18)

> Снято живым CDP-захватом (Chrome --remote-debugging-port=9222 --user-data-dir=C:\chrome-debug)
> с залогиненной сессии vk.ru, uid **171093180** (Сергей).
> Метод: Target.createTarget("https://vk.ru/calls") при уже включённом Network.enable —
> ловит queue.subscribe с самого первого запроса (на тёплой SPA он НЕ переотправляется).

## 1. queue.subscribe (calls)

```
POST https://web.api.vk.ru/method/queue.subscribe?v=5.289&client_id=6287487
Content-Type: application/x-www-form-urlencoded

queue_ids=multiaccount_171093180&access_token=vk1.a.<...>

=> 200 OK
```

### Ключевые отличия от кода PinoK (причина err=15 Access denied)

| Параметр | VK web (правильно) | PinoK сейчас |
|---|---|---|
| Хост | `web.api.vk.ru` | `api.vk.ru` (`QUEUE_SUBSCRIBE_HOST`) |
| query | `v=5.289&client_id=6287487` | нет |
| `queue_ids` | `multiaccount_<uid>` | `calls_<uid>_<client>_1` (`callsQueueId`) |
| токен | web `vk1.a.*` в `access_token` | SAT |
| тело | только `queue_ids`+`access_token` | `queue_ids`,`v`,`https`,`access_token`,`lang` |

**Вывод:** для calls-очереди `queue_ids = multiaccount_<uid>` (НЕ `calls_...`),
хост `web.api.vk.ru`, web-токен, в теле только `queue_ids` + `access_token`, query `v=5.289&client_id=6287487`.

## 2. Realtime LongPoll

```
POST https://api.vk.ru/ruim171093180?version=21&mode=682
act=a_check&key=<...>&ts=<...>&wait=25
```

## 3. Сопутствующий calls-трафик (web.api.vk.ru)

`batch.call` с методами: `messages.getInboundCalls`, `calls.getSettings`,
`messages.getCurrentCalls`, `messages.getScheduledCalls`, `messages.getGroupsForCall`,
`calls.getHistory`, `friends.get`.

## 4. P0-2: settingsGeneral.*

`settingsGeneral.getCookiesPolicyVisible` замечен **внутри `batch.call`** на `web.api.vk.ru`
(не одиночным методом). Значит `settingsGeneral.setNotifySettings` тоже надо слать через
`batch.call` на web.api.vk.ru, а не одиночным `call()` -> отсюда err=3.

## 5. Ссылки в коде

- `app/src/main/java/re/pinok/api/VKEndpoints.kt:60` — `QUEUE_SUBSCRIBE_HOST = "https://api.vk.ru"`
- `:61` — `QUEUE_SUBSCRIBE_HOST_WEB = "https://web.api.vk.ru"`
- `:65` — `fun callsQueueId(uid, clientId) = "calls_${uid}_${clientId}_1"`
- `VKApiClient.kt:12341` — `override suspend fun queueSubscribe(...)` (SAT-путь на QUEUE_SUBSCRIBE_HOST)
