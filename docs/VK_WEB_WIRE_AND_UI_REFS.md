# VK web wire — карта хостов и UI-референсы (PinoK)

> Извлечено из `PinoK_NOTES.md` (сводка 2026-09-18). Уникальные разделы, которых нет в других docs.
> При расхождении с кодом — приоритет у кода.

## 1. VK web wire (эталоны DevTools, 2026-09-17)

Realtime/API VK web использует **три разных хоста**, не один:

1. **Realtime LongPoll**
   - `POST https://api.vk.ru/ruim<uid>?version=21&mode=682` — form-urlencoded, висит ~25 с, ответ отдаёт `x-next-ts=ts`
   - `GET https://queuev4.vk.ru/im<server>?act=a_check&id=<uid>&key=&ts=&wait=25|30` (встречались `mode=202` / `version=10`)
2. **Веб-шлюз API**
   - `POST/GET https://web.api.vk.ru/method/<method>?v=5.289&client_id=6287487`
   - Через него идут: `queue.subscribe`, `account.setOnline`, `account.getInfo`, `eventHub.getToken`, `batch.call`, `stickers.subscribeToQueue`, `messages.getRecentStickers`, `utils.getReleaseVersionsInfo`
3. **Авторизация**
   - `POST https://login.vk.ru/?act=web_token` → `vk1.a*` + httoken
   - `batch.call` — POST JSON с `Authorization: Bearer vk1.a*` (единственный из списка с Bearer)

Побочно: аудио `cs9-10v4.vkuseraudio.ru/.../index.m3u8?siren=1` + key.pub (Siren/HLS); `24112.ms.vk.ru` HEAD heartbeat.

**Вывод для PinoK:** наш `queue.subscribe` уходил на `api.vk.com` → `err=100 invalid queue name`; VK web шлёт его на `web.api.vk.ru/method/queue.subscribe?v=5.289&client_id=6287487`. Старый LongPoll (`messages.getLongPollServer` + `api.vk.com/gim*`) заменяется на `api.vk.ru/ruim<uid>` / `queuev4.vk.ru/im*`.

### Домены

- `vk.com` редиректит (302) на зеркало `vk.ru` — тот же фронтенд.
- Wire всегда идёт на `.vk.ru`: `web.api.vk.ru`, `api.vk.ru/ruim<uid>`, `queuev4.vk.ru`, `login.vk.ru`. Домен страницы (vk.com vs vk.ru) на хосты не влияет.
- При съёме эталонов в DevTools фильтровать по `vk` / `*.vk.ru`, а не только `api.vk.com` — иначе `batch.call`/`queue.subscribe` можно не увидеть.
- Проверить в PinoK хардкоды `vk.com`: `AuthDomainsConfig` показывал `api=api.vk.com` (при фронте VK на `.vk.ru`) — возможная причина части `err=3`/`err=100`; домен remixsid должен совпадать с редирект-доменом.

## 2. ErrorView (core:ui)

Файл: `core/ui/src/main/java/re/pinok/ui/components/ErrorView.kt`

```kotlin
fun ErrorView(
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    isOffline: Boolean = false,
    defaultMessage: String = "...",
    modifier: Modifier = Modifier
)

fun ErrorViewCompact(message: String, modifier: Modifier)
```

**Важно:** 3-й позиционный аргумент — `isOffline` (Boolean), поэтому `modifier` передавать именованно (`ErrorView(message=, onRetry=, modifier=)`).

---

*Сгенерировано ассистентом 2026-09-22 из PinoK_NOTES.md.*