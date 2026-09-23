# VK Звонки — CDP-захват (реальные данные, 2026-09-23)

Документ описывает рабочий метод перехвата трафика входящего/исходящего веб-звонка VK
через Chrome DevTools Protocol (CDP) и все реально пойманные вызовы. Использовать для
переноса логики звонков в PinoK.

---

## 1. Окружение захвата

| Параметр | Значение |
|---|---|
| Браузер | Chrome, отдельный профиль `C:\chrome-debug` |
| Процесс | PID 9184 (browser), порт `--remote-debugging-port=9222` |
| CDP endpoint | `http://127.0.0.1:9222/json/list` |
| Целевая вкладка | `page`, title `Звонки`, url `https://vk.ru/calls` |
| target id | `2695580F6A7C458602E39D366193D1D4` |
| WebSocket CDP | `ws://127.0.0.1:9222/devtools/page/2695580F6A7C458602E39D366193D1D4` |

Важно: обычный профиль Chrome (DeepSeek++ Browser Control) НЕ видит этот профиль — только через CDP-порт 9222.

---

## 2. Скрипт захвата

`E:/ANDROID_APP/PinoK_1/_debug/vk_calls/cdp_capture.py` — чистый stdlib (без pip).

Запуск:
```powershell
Start-Process py -ArgumentList '-3','cdp_capture.py' -WorkingDirectory 'E:/ANDROID_APP/PinoK_1/_debug/vk_calls' -WindowStyle Normal
```
НЕ использовать `-RedirectStandardOutput` — вешает MCP-хост.

Выход: `_debug/vk_calls/cdp_capture.jsonl`.

### Что включает v2
- `Network.enable` с maxTotalBufferSize=50MB, maxResourceBufferSize=20MB, maxPostDataSize=1MB
- `Runtime.enable`, `Page.enable`
- `Target.setAutoAttach` (flatten) — ловит worklet/worker/service_worker
- WS: `webSocketWillSendHandshakeRequest`, `webSocketHandshakeResponseReceived`, `webSocketCreated`, `FrameReceived/Sent`, `Closed`
- `Network.loadingFinished` -> `Network.getResponseBody` для getCurrentCalls/videochat/queue.subscribe/getCallParams/al_video/al_calls
- payload БЕЗ обрезки

---

## 3. Пойманные вызовы (реальные)

### 3.1. queue.subscribe — ответ на P0-блокер (err=100/15)

```
POST https://web.api.vk.ru/method/queue.subscribe?v=5.289&client_id=6287487
body: queue_ids=calls_171093180_6287487_1
      &access_token=vk1.a.<web-token>
-> status 200
```

ИМЯ ОЧЕРЕДИ: `calls_<uid>_<client_id>_<N>` = `calls_171093180_6287487_1`.
НЕ `nccts$uid`, НЕ `events_queue$uid` (как строит PinoK SovaApp.kt:1345-1347 -> err=100).
ХОСТ: `web.api.vk.ru` (не api.vk.com).
ТОКЕН: web-токен `vk1.a.*` (НЕ SAT — требование SAT в PinoK лишнее).

### 3.2. messages.getCurrentCalls
```
POST https://web.api.vk.ru/method/messages.getCurrentCalls?v=5.289&client_id=6287487
body: fields=photo_100,photo_200,sex,screen_name,first_name_gen,is_nft,animated_avatar,custom_names_for_calls
      &access_token=vk1.a.<web-token>
-> 200 (опрашивается каждые ~5 сек)
```

### 3.3. batch.call (цепочка при звонке)
```
POST https://web.api.vk.ru/method/batch.call?v=5.289&client_id=6287487
body: {"requests":[
  {"id":"3s","method":"calls.getCallSettings","params":{"call_id":"02294a4a-4961-45bb-bfc4-11d27f8494e6"}},
  {"id":"3t","method":"messages.getConversationsById","params":{"peer_ids":"152094335","extended":1,...}},
  {"id":"3u","method":"messages.getCurrentCalls","params":{"fields":"..."}}
]}
```

### 3.4. messages.getCallParticipants
```
POST https://web.api.vk.ru/method/messages.getCallParticipants?v=5.289&client_id=6287487
body: call_id=02294a4a-4961-45bb-bfc4-11d27f8494e6
      &participant_ids=171093180
      &fields=photo_100,photo_200,sex,screen_name,first_name_gen,is_nft,animated_avatar,custom_names_for_calls
      &access_token=vk1.a.<web-token>
-> 200
```

### 3.5. LongPoll очереди (фоновые)
```
https://queuev4.vk.ru/im1180?act=a_check&id=171093180&key=<im_m_comms_key.key>&ts=...&wait=25
https://api.vk.ru/ruim171093180?version=21&mode=682  POST act=a_check&key=...&ts=...&wait=25
```

---

## 4. Ключевые идентификаторы

| Идентификатор | Пример | Где взять |
|---|---|---|
| `call_id` (UUID звонка) | `02294a4a-4961-45bb-bfc4-11d27f8494e6` | LP-событие / batch.call |
| `client_id` | `6287487` | localStorage `queue_credential_calls_cache_*_6287487` |
| `uid` | `171093180` | аккаунт |
| `queue_ids` | `calls_171093180_6287487_1` | формируется как `calls_<uid>_<client_id>_1` |
| web-токен | `vk1.a.*` | localStorage / запросы |
| `im_m_comms_key.key` | `47dae17a...` | localStorage |

---

## 5. Цепочка входящего звонка (VK-web)

1. LongPoll -> событие о звонке -> `call_id` (UUID)
2. `messages.getCurrentCalls` — узнать активный звонок
3. `batch.call`: `calls.getCallSettings(call_id)` + `messages.getConversationsById` + `messages.getCurrentCalls`
4. `messages.getCallParticipants(call_id, participant_ids)` — участники
5. Медиа: signaling через `wss://videowebrtc.okcdn.ru/ws2` (только если вкладка сама инициирует/принимает звонок)

---

## 6. Что НЕ поймано (ограничения)

`wss://videowebrtc.okcdn.ru/ws2`, SDP offer/answer, ICE-кандидаты — НЕ появились,
потому что web-вкладка не была инициатором/принимающим медиа-сессии (звонок шёл
через другой клиент, web только отражал состояние через API+LP).

Для захвата ws2: звонок должен быть ПРИНЯТ/НАЧАТ кнопкой на самой вкладке `vk.ru/calls`.

---

## 7. Выводы для PinoK

| PinoK сейчас | Как VK-web |
|---|---|
| `queue.subscribe` с шаблоном `nccts$uid` -> err=15 | `queue_ids=calls_<uid>_<client_id>_1` на `web.api.vk.ru`, web-токен -> 200 |
| требует SAT | web-токен `vk1.a.*` достаточно |
| `vchat.getCallParams` (payload без conversation params) | `calls.getCallSettings(call_id=UUID)` через batch.call |
| CallScreen.kt:781 ищет conversation params | брать `call_id` (UUID) из LP-события |
| не читает localStorage | `im_m_comms_key`, `queue_credential_calls_cache_*` |

Двойной экран (баннер + CallScreen) — отдельный UI-баг, фикс Вариант A (см. PinoK_Double_IncomingScreen_FIX.md).

---

## 8. Файлы

- Скрипт: `_debug/vk_calls/cdp_capture.py`
- Лог: `_debug/vk_calls/cdp_capture.jsonl`
- Гайд по DevTools: `~/Desktop/VK_Calls_Debug_Extract_Guide.md`