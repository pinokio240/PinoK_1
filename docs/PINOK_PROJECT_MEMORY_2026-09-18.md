# PinoK — ПОЛНАЯ ПАМЯТЬ ПО ПРОЕКТУ (сборка 2026-09-18)

> Единый файл: всё, что накоплено о проекте PinoK. Приоритет у кода, если расходится.

---

## 1. Общее

- Android-проект **PinoK_1**, путь `E:\ANDROID_APP\PinoK_1`.
- Kotlin, UI Jetpack Compose. Многомодульная архитектура.
- Git: remote `https://github.com/pinokio240/PinoK_1.git`, ветка **PinoK**.
- VK uid: **171093180**. VK app client_id: **6287487**.
- Тестовое устройство: HOTWAV Cyber 15, Android 13 / API 33. Версия в логах: 2.1.4-debug.
- Ведётся волнами, есть `worklog.md` (~1.3 МБ). Пайплайн валидаторов: check-nested-comments, check-secrets, баланс скобок, NULL-ЯВНО 0 `!!`.

## 2. Правила работы

- Пользователь **сам запускает сборку** (gradle). Ассистент только пишет/правит код.
- Wire-эталоны VK web снимаются через **DevTools/CDP** (браузер залогинен на vk.ru).
- Язык общения — **русский**.
- Всегда давать лог выполненного/невыполненного.

## 3. Realtime: три слоя

1. **LongPoll** — события (сообщения, входящий звонок).
2. **queue.subscribe** — очереди (calls, events, counters).
3. **WebRTC/медиа** — когда звонок идёт.

### 3.1 VK web wire (эталон, CDP, uid 171093180)

**LongPoll (realtime):**
```
POST https://api.vk.ru/ruim171093180?version=21&mode=682
act=a_check&key=<...>&ts=<...>&wait=25
```

**queue.subscribe (calls):**
```
POST https://web.api.vk.ru/method/queue.subscribe?v=5.289&client_id=6287487
Body: queue_ids=multiaccount_171093180&access_token=vk1.a.<...>
=> 200 OK
```

**Сопутствующий calls-трафик (web.api.vk.ru, batch.call):**
`messages.getInboundCalls`, `calls.getSettings`, `messages.getCurrentCalls`,
`messages.getScheduledCalls`, `messages.getGroupsForCall`, `calls.getHistory`, `friends.get`.

**settingsGeneral.*** идёт **внутри batch.call** на `web.api.vk.ru` (например settingsGeneral.getCookiesPolicyVisible).

## 4. Дефекты (актуальный статус)

### P0-1: queue.subscribe calls -> err=15 Access denied
- **Корень:** host api.vk.ru вместо web.api.vk.ru; queue_ids `calls_<uid>_<client>_1` вместо `multiaccount_<uid>`; отсутствие v/client_id; SAT вместо web-токена.
- **Фикс внесён 2026-09-18 (НЕ проверен сборкой):**
  - `VKApiClient.kt` queueSubscribe: suffix -> `multiaccountQueueId(uid)`; URL -> `QUEUE_SUBSCRIBE_HOST_WEB` + `?v=<API_VERSION>&client_id=<VK_WEB_CLIENT_ID>`.
  - `SovaApp.kt:1798-1801`: calls -> `multiaccountQueueId(callsUid)`.
  - Тело оставлено SAT-формой (открытый вопрос: нужен ли web-токен vk1.a).

### P0-2: settingsGeneral.setNotifySettings -> err=3
- **Корень:** метод идёт одиночным call(), а VK web шлёт settingsGeneral.* через **batch.call на web.api.vk.ru**.
- **Статус:** НЕ исправлен.

### P2
- accountPersonal.getActivityHistoryDevices -> err=3 (DevicesScreen, Task 75).
- groups.getById с group_id -> err=100 deprecated 5.218 (заменить на group_ids).
- ProfileScreen.kt:425 ForgottenCoroutineScopeException.
- accountPersonal.getSecurityAlerts -> err=3 (SecurityAlertsPoller).

### Clips §37
- P1: Report API, Share-to-wall, User-author subscribe, Hashtag nav, Music-info nav, Live-clip LP-polling.
- P2: Dislike, Hide-author, Toggle notifications, Share-to-story, 10 полей Video.
- P3: ads tracking, interactive video, stats tokens, BFF feature flags (14), storage persistence, VideoSearch-as-service, /clips_trends + /clips_shops.
- 12 методов §37.4 отсутствуют; типы VkClipCatalog/VkClipAuthor/StoryStickerClip не созданы.

### Auth (AUTH_FULL_AUDIT, 2026-08-06 session 5)
- P0-1 PlayerConnection.init в SovaApp.onCreate:919 блокирует main thread.
- P0-2 нет WebChromeClient в VkAuthWebViewScreenV2.
- P0-3 удалён safety-net onPageStarted.
- P1-4..7 CookieManager, LAYER_TYPE_SOFTWARE, статический UA, network_security_config без .ru.
- P2-8..10 мёртвый VkAuthWebViewScreen, cookie polling 1с, дубли tryLaunch*.

### Инфра
- O2 шаг 2 (вынос audio-домена) — пауза.
- MANAGE_EXTERNAL_STORAGE -> EPERM Music/PinoK.
- MediaSessionCompat media button receiver (кнопки гарнитуры).
- Просадки main thread на старте.
- FeatureFlagsImplExport NoClassDefFoundError.

## 5. Ключевые ссылки в коде

- `VKEndpoints.kt:54` WEB_API_HOST = "https://web.api.vk.ru"
- `VKEndpoints.kt:57` WEB_API_HOSTNAME
- `VKEndpoints.kt:60` QUEUE_SUBSCRIBE_HOST = "https://api.vk.ru"
- `VKEndpoints.kt:61` QUEUE_SUBSCRIBE_HOST_WEB = "https://web.api.vk.ru"
- `VKEndpoints.kt:65` callsQueueId = "calls_${uid}_${clientId}_1" (теперь не используется)
- `VKEndpoints.kt` multiaccountQueueId(uid) = "multiaccount_$uid"
- `VKApiClient.kt:12341` queueSubscribe (правлен)
- `SovaApp.kt:1798-1801` calls queueSubscribe (правлен)
- `SovaApp.kt:1815-1817` events -> countersQueueId
- `SovaApp.kt:1820-1822` events fallback -> multiaccountQueueId (был верный)

## 6. Артефакты сессии 2026-09-18

- `build_artifacts/cdp_capture.js`, `cdp_newtab.js` — CDP-захватчики (Node, встроенный WebSocket).
- `build_artifacts/cdp_net.log` — лог захвата.
- `docs/VK_CALLS_QUEUE_SUBSCRIBE_REFERENCE_2026-09-18.md` — эталон queue.subscribe.
- Chrome запускался: `--remote-debugging-port=9222 --user-data-dir=C:\chrome-debug`.

## 7. Что дальше (завтра)

1. Собрать и проверить P0-1 в тесте входящего звонка (logcat: `queue.subscribe ok via SAT` / код ошибки).
2. Если err сохраняется -> перейти на **web-токен vk1.a** в теле queue.subscribe (web.api.vk.ru).
3. P0-2: settingsGeneral.* через batch.call на web.api.vk.ru.
4. Точечные: groups.getById -> group_ids; скоуп ProfileScreen; getSecurityAlerts.
