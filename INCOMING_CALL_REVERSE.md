# INCOMING CALL REVERSE (VK web -> PinoK)

Источник: CDP-снимок localStorage вкладки https://vk.ru/calls (Chrome 153, port 9222)
Дата: 2026-09-22. uid=171093180, appId=6287487, device=HOTWAV Cyber 15

## КЛЮЧИ ИЗ VK WEB (localStorage, реальные значения)

### im_m_comms_key  <- ИМЯ CALLS-ОЧЕРЕДИ + КЛЮЧ
```json
{"ts":"1889084789","key":"f0c05c488b2b982325af528f59bde4b5ebab5443b882f967750a205f5c7a413b","queue":"nccts171093180"}
```
- queue = `nccts171093180` (совпадает с шаблоном PinoK SovaApp.kt:1345!)
- key = авторизационный ключ (нужен при subscribe)

### queue_credential_calls_cache_171093180_6287487  <- ЧТО НУЖНО ДЛЯ queue.subscribe
```json
{"data":{"key":"19e0445b82c736a654167ff452b19c67d4f882742d08967c822958d600bf8d0f","ts":257176134,"url":"https://queuev4.vk.ru/im1180","id":171093180},"lastUpdate":1790104614177}
```
- url = `https://queuev4.vk.ru/im1180`  <- АДРЕС для queue.subscribe (не api.vk.com!)
- key = credential
- ts = 257176134

### queue_connection_events_queue171093180  <- ФОРМАТ EVENTS-ОЧЕРЕДИ
```json
{"__client":"NTMyOTI4","__act":"focus","__rnd":0.9545299590241209,"instance_id":"1NTMyOTI4"}
```
### server_queue_connection_events_queue171093180
```json
["NTMyOTI4",1790105089909]
```

### calls_token_with_url_171093180
```
$SifIAb1F00ZNqWiekngOt7u9kZufonQNhgU65XgC9hORJPuZLLbv6JjJOvZ7TGwqLONzt///https://calls.okcdn.ru
```
- calls-токен `$...` + базовый url https://calls.okcdn.ru

### 6287487:web_token:login:auth (web access_token)
- access_token = `vk1.a.w3mD...MASKED...S9FA`
- user_id=171093180, expires=1790105509, logout_hash=4e51f9e4943d863241

### 6287487:get_anonym_token:login:auth (anonym -> session key для calls)
- access_token = `anonym.eyJ...` (JWT, expires=1789848585, expired)

### _okcls_anonymLogin:$SifIAb... (calls session)
```json
{"session_key":"-w-fl0000MtRwXi530010GOjSsn000000000gidOcCcA56pMQOdy1zcJgjeBljbV83cCRycM4moNkjcztjpP4j000008yN9IWq000000EwcIOb0wM767oFfnfVPpaxnYMDUUDwbK2pdpiCTaoOWUk0Aj5VA","session_secret_key":"XvK625zAQeZMP79UQR8w","uid":"584520805550"}
```
- uid=584520805550 (okUid для calls)
- session_key начинается с `-w-fl` (совпадает с логом PinoK CallScreen.kt:791 'sessionKey=-w-fl0000MtR...')
- session_secret_key=XvK625zAQeZMP79UQR8w

### Прочее
- `reforged-storage-db-v1-171093180-reforged-device-id` = `mpDMftTcxHO5ZvN1Cq127`
- `_one-stat_deviceId` = `553CC961-1DC3-4C84-BCA8-4929D1D34213`
- `tracer-device-id` = `b89bf36b-2c8d-44ac-b52b-725f52b25bda`
- `audio_unique_unauth_id` = `9695ae3f-baa4-4fbf-80f6-490ed3b645e0`

## ПРИМЕНЕНИЕ ДЛЯ PinoK (входящий звонок)
1. queue.subscribe слать на url ИЗ `queue_credential_calls_cache` (`https://queuev4.vk.ru/im1180`), а НЕ строить api.vk.com. Требуется key из того же объекта + im_m_comms_key.key.
2. Имя calls-очереди = im_m_comms_key.queue = `nccts<uid>`. Формат events = `queue_connection_events_queue<uid>`.
3. Токены: web access_token (vk1.a.*) + anonym JWT (для session_key) + calls_token_with_url.
4. okUid = session uid из _okcls_anonymLogin (584520805550).

## СТАТУС
СНЯТО из localStorage: ДА. Осталось (для полной картины): Network.enable -> реальный HTTP-запрос queue.subscribe (URL, params, какой токен) + WS-фреймы signaling.

---
## ДОБАВЛЕНО (перехват реального входящего 2026-09-22, CDP Network)

### ГЛАВНОЕ: VK web НЕ вызывает queue.subscribe как API-метод!
Он держит LONG-POLL напрямую на queuev4.vk.ru:
```
GET https://queuev4.vk.ru/im1180?act=a_check
    &id=171093180
    &key=d1109505c92c3439cac1eb8e99040335fd307c4c51bf88080cfdadbaa3238488f0c05c488b2b982325af528f59bde4b5ebab5443b882f967750a205f5c7a413b
    &ts=1182138131_1889084796
    &wait=25
```
- key = <queue_credential.key> + <im_m_comms_key.key>
  = d1109505c92c3439cac1eb8e99040335fd307c4c51bf88080cfdadbaa3238488
  + f0c05c488b2b982325af528f59bde4b5ebab5443b882f967750a205f5c7a413b
- ts = <ts>_<im_m_comms_key.ts> = 1182138131_1889084796
- id=171093180, wait=25, server=im1180 (из url queue_credential)
- Второй (events) очередь: ?key=62c8b2f0...&act=a_check&wait=25&mode=202&version=10&id=171093180&ts=185710768

### Полный флоу входящего (VK web, real):
1. POST web.api.vk.ru/method/messages.getCurrentCalls?v=5.289&client_id=6287487 (access_token=vk1.a.*)
2. POST calls.okcdn.ru/fb.do method=system.getInfo (application_key=CGMMEJLGDIHBABABA, session_key=-w-fl...)
3. POST calls.okcdn.ru/fb.do method=vchat.clientStats data={call_init, source:incoming, call_topology:D, vcid:<uuid>}
4. GET queuev4.vk.ru/im1180 act=a_check (long-poll, wait=25)
5. vchat.clientStats call_start {labels:[incoming]}
6. vchat.clientStats call_accepted_incoming -> first_media_sent
7. vchat.clientStats webtransport_connected transport=udp + signaling_connected

### КОНСТАНТЫ calls OK:
- application_key = CGMMEJLGDIHBABABA
- fb.do = OK API endpoint (calls.okcdn.ru/fb.do)
- sdk_version=2.8.12-beta.15, app_version=1.1, platform=vk_web2, sdk_type=WEB
- call_topology=D (direct)
- webtransport transport=udp (НЕ WebSocket!)

### P0-ФИКС PinoK:
Заменить queue.subscribe(api.vk.com) на LONG-POLL GET queuev4.vk.ru/<id>?act=a_check&id=<uid>&key=<cred.key>+<im_m_comms_key.key>&ts=<ts>_<im_m_comms_key.ts>&wait=25


---
## ФЛОУ ВХОДЯЩЕГО ПО КОДУ PinoK

### Шаг 1. LongPoll получает событие
- LongPollClient.kt (67249 b), case 115:
  - ev=[115, payload], payload = строка conversation-params (WebRTC)
  - AppLog 'INCOMING_CALL (LP 115): payload.len=N'
  - _events.emit(LongPollEvent.IncomingCall(payload))

### Шаг 2. SovaApp.kt принимает LP 115
- SovaApp.kt:~1695/1697 'INCOMING_CALL (LP 115): payload.len=N'
  - ставит pendingIncomingCallPayload / pendingIncomingCallPeerId / Title / Photo
  - refreshIncomingCaller: callerId -> users.get (profile=OK ~1755/1764)
  - БАННЕР (IncomingCallBanner) рисуется по state (incomingCallCollapsed)

### Шаг 3. Навигация
- SovaNavHost.kt:~401 'INCOMING_CALL: navigating to CallScreen payload.len=N'
  - условие: if (!payload.isNullOrBlank() && app.incomingCallAccepted)  <-- БЫЛ DEADLOCK
  - ФИКС: убран '&& app.incomingCallAccepted' -> if (!payload.isNullOrBlank())
  - nav.navigate(Screen.Call.buildRoute(peerId,title,photo,incoming=true,payload=payload)) { launchSingleTop=true }
  - app.consumeIncomingCall()

### Шаг 4. CallScreen
- CallScreen.kt:677 'CALL START: входящий peer=.. name=.. video=.. payload=..'
- CallScreen.kt:756 'Incoming call, payload.len=N'
- CallScreen.kt:767 messagesGetCurrentCalls: items=1
- CallScreen.kt:773 current call: convId=<uuid>
- CallScreen.kt:781 'payload не содержит conversation params - пробуем vchat API'
- CallScreen.kt:791 getCallParams: sessionKey=.. vchat=OK
- CallScreen.kt:829 Signaling start: conversationId=<uuid> userId=<okUid>

### Шаг 5. Accept (кнопка Принять)
- CallScreen.kt:2287 'Принять: params готовы, ws готов — accept'
- CallSignalingClient send command=accept-call seq=1
- CallScreen.kt:2304 'Принять: accept-call отправлен'
- CallScreen.kt:2309 'Принять: PC создаётся, offerReceived=true'

### Шаг 6. Медиа
- WebRtcEngine setIceServers / setRemoteSdp(offer) / createAnswer
- ICE CHECKING -> CONNECTED / FAILED
- signaling wss://videowebrtc.okcdn.ru/ws2

### КЛЮЧЕВОЙ БАГ (регресс)
SovaNavHost.kt:399 блокировал вход в CallScreen до accept, но accept делает CallScreen.
DEADLOCK снят фиксом. ПРОВЕРИТЬ: после фикса экран должен открываться сразу при LP 115.


---
## ФЛОУ ВХОДЯЩЕГО ПО КОДУ PinoK

### Шаг 1. LongPoll получает событие
- LongPollClient.kt (67249 b), case 115:
  - ev=[115, payload], payload = строка conversation-params (WebRTC)
  - AppLog 'INCOMING_CALL (LP 115): payload.len=N'
  - _events.emit(LongPollEvent.IncomingCall(payload))

### Шаг 2. SovaApp.kt принимает LP 115
- SovaApp.kt:~1695/1697 'INCOMING_CALL (LP 115): payload.len=N'
  - ставит pendingIncomingCallPayload / pendingIncomingCallPeerId / Title / Photo
  - refreshIncomingCaller: callerId -> users.get (profile=OK ~1755/1764)
  - БАННЕР (IncomingCallBanner) рисуется по state (incomingCallCollapsed)

### Шаг 3. Навигация
- SovaNavHost.kt:~401 'INCOMING_CALL: navigating to CallScreen payload.len=N'
  - условие: if (!payload.isNullOrBlank() && app.incomingCallAccepted)  <-- БЫЛ DEADLOCK
  - ФИКС: убран '&& app.incomingCallAccepted' -> if (!payload.isNullOrBlank())
  - nav.navigate(Screen.Call.buildRoute(peerId,title,photo,incoming=true,payload=payload)) { launchSingleTop=true }
  - app.consumeIncomingCall()

### Шаг 4. CallScreen
- CallScreen.kt:677 'CALL START: входящий peer=.. name=.. video=.. payload=..'
- CallScreen.kt:756 'Incoming call, payload.len=N'
- CallScreen.kt:767 messagesGetCurrentCalls: items=1
- CallScreen.kt:773 current call: convId=<uuid>
- CallScreen.kt:781 'payload не содержит conversation params - пробуем vchat API'
- CallScreen.kt:791 getCallParams: sessionKey=.. vchat=OK
- CallScreen.kt:829 Signaling start: conversationId=<uuid> userId=<okUid>

### Шаг 5. Accept (кнопка Принять)
- CallScreen.kt:2287 'Принять: params готовы, ws готов — accept'
- CallSignalingClient send command=accept-call seq=1
- CallScreen.kt:2304 'Принять: accept-call отправлен'
- CallScreen.kt:2309 'Принять: PC создаётся, offerReceived=true'

### Шаг 6. Медиа
- WebRtcEngine setIceServers / setRemoteSdp(offer) / createAnswer
- ICE CHECKING -> CONNECTED / FAILED
- signaling wss://videowebrtc.okcdn.ru/ws2

### КЛЮЧЕВОЙ БАГ (регресс)
SovaNavHost.kt:399 блокировал вход в CallScreen до accept, но accept делает CallScreen.
DEADLOCK снят фиксом. ПРОВЕРИТЬ: после фикса экран должен открываться сразу при LP 115.
