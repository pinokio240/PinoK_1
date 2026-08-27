# МАРШРУТНАЯ КАРТА ЗВОНКОВ VK — ВХОДЯЩИЙ И ИСХОДЯЩИЙ

> Полная карта: методы, запросы, ответы, зависимости, последовательности.
> Собрано из: декомпилят VK+Calls APK (ru.ok.android.webrtc, externcalls.sdk),
> JS calls SDK (vendors~calls-sdk), логи реальных звонков PinoK.

---

## 0. БАЗОВЫЕ ФАКТЫ (проверено логами)

| Факт | Значение |
|---|---|
| **okcdn uid** | `584520805550` (из `_okcls_anonymLogin.uid`) — НЕ VK user_id (171093180) |
| **VK user_id** | `171093180` |
| **session_key** | из `_okcls_anonymLogin.session_key` (работает для vchat API) |

### 0.1 LONG-POLL ОЧЕРЕДИ (из реальных fetch браузера + localStorage дампа входящего)

Веб держит **несколько параллельных queuev4 long-poll** (`https://queuev4.vk.ru/im1180`):

| Очередь | key (localStorage) | ts | URL-флаг |
|---|---|---|---|
| accountcounters / calls | `queue_credential_calls_cache_<uid>_<app_id>` = `{data:{key,ts,url,id}}` | напр. `1160418160`, `583705473` | `mode=202&version=10&id=<uid>` (пример: `key=46878e1a...&ts=1223912323&mode=202&version=10`) |
| nccts (IM/звонки) | `im_m_comms_key` = `{key,ts,queue:"nccts<uid>"}` | напр. `1129989305`, `1129989244` | входит в состав **двух-ts** запроса: `ts=583705473_1129989244` (слэш `_` разделяет два ts) |
| events (conversation params) | `queue_connection_events_queue<uid>` = `{__client,__act,__rnd}`; `server_queue_connection_events_queue<uid>` = `[client,ts]` | — | отдельный формат (не a_check) |

Формат ответа:
- одна очередь: `{"ts":"1223912324","events":[]}` (пусто) или `{"ts":"...","events":[[115,"<payload>"],...]}`
- две очереди: `[{"ts":"583705474","events":[]},{"ts":"1129989245","events":[]}]`
- устаревший ts: `{"failed":2,"err":4}`
- events — **JSON-строки** (напр. `{"version":10,"type":"new_post",...}`)


---

## 1. АВТОРИЗАЦИЯ VCHAT (общая для обоих направлений)

```
auth.anonymLogin POST calls.okcdn.ru/fb.do
  session_data = {version:3, device_id:<uuid>, client_version, client_type:"SDK_JS", auth_token:<$callToken>}
  application_key = CGMMEJLGDIHBABABA
→ {session_key, session_secret_key, uid:584520805550, api_server}

user_id для WS = uid из этого ответа (584520805550)
```

Примечание: `$Ksd...`-токен — клиентский (маркер `$`+random), vchat принимает.
VK oauth-токен (anonym.eyJ...) → 401 AUTH_LOGIN. Session_key из дампа достаточно.

---

## 2. ПОЛУЧЕНИЕ WS-ПАРАМЕТРОВ (общая)

```
vchat.getConversationParams POST calls.okcdn.ru/fb.do
  conversation_id = <call_id>
  session_key = <из prefs>        (или anonymToken/anonymToken)
  application_key = CGMMEJLGDIHBABABA
→ {token, endpoint:wss://videowebrtc.okcdn.ru/ws2,
   turn_server:{urls[], username, credential},
   stun_server:{urls[]},
   client_type:"VK", device_idx:0, external_user_type:"VK", server_time}
```

---

## 3. ПОДКЛЮЧЕНИЕ WEBSOCKET (общая)

URL: `wss://videowebrtc.okcdn.ru/ws2` + query:
```
userId=<okcdn uid 584520805550>
entityType=USER
deviceIdx=0
conversationId=<call_id>
token=<wssToken из getConversationParams>
platform=android
appVersion=2.0.0
version=1
device=<device>
capabilities=0
clientType=USER
```
→ onOpen → сервер шлёт:
```
{notification:"connection", peerId:{id:0,type:"WEB_SOCKET"},
 endpoint:..., conversationParams:{turn,stun,...}}
{notification:"settings-update", ...}           // media settings
{notification:"transmitted-data", peerId:{id:<participant>,type:"WEB_SOCKET"},
 data:{sdp:{type:"offer",sdp:"..."}}}           // offer (входящий)
{notification:"transmitted-data", ... data:{candidate:{...}}}  // ICE
```

---

## 4. КОМАНДЫ СИГНАЛИНГА (формат JSON)

Исходящие:
```
{command:"accept-call", sequence:N, mediaSettings:{isAudioEnabled:true,isVideoEnabled:false,...},
 conversationId:<call_id>}
{command:"hangup", sequence:N, reason:"declined"|"hungup", conversationId:<call_id>}
{command:"transmit-data", sequence:N, participantId:<participant>, data:{sdp:"...",type:"answer"}}
{command:"transmit-data", sequence:N, participantId:<participant>, data:{candidate:{candidate:"...",sdpMid,sdpMLineIndex}}}
```
Входящие (уведомления):
```
{notification:"connection", ...}
{notification:"transmitted-data", peerId:{id}, data:{sdp:{type:"offer"}}}
{notification:"transmitted-data", peerId:{id}, data:{candidate:{...}}}
{type:"response", sequence:N, response:"accept-call"|"transmit-data", participantIds:[]}  // ack
{stamp:0, error:"invalid-request", message:"..."}   // ошибка
```

---

## 5. ВХОДЯЩИЙ ЗВОНОК — маршрут (PinoK)

```
[VK сервер] → LP 115 (messages LongPoll) payload="-1"
  │
  ├─ messages.getCurrentCalls → {call_id, caller_id:152094335, user_ids:[...]}
  │
  ├─ vchat.getConversationParams(conversation_id=call_id, session_key)
  │   → {token, endpoint, turn_server, stun_server}
  │
  ├─ WebSocket connect (userId=okcdn uid, token, conversationId=call_id)
  │   → notification:connection
  │   → notification:transmitted-data offer (data.sdp.type=offer) + candidates
  │
  ├─ [ПОЛЬЗОВАТЕЛЬ] нажимает «Принять»
  │
  ├─ engine.acceptCall() → createLocalAudioTrack + createPeerConnection
  │   (JavaAudioDeviceModule обязателен! иначе SIGABRT)
  │
  ├─ engine.setRemoteSdp(offer) → onSetSuccess → createAnswer()
  │   (createAnswer СТРОГО в onSetSuccess, НЕ сразу!)
  │
  ├─ createAnswer → setLocalDescription → onLocalSdpReady
  │   → transmit-data {participantId, data:{sdp:answer, type:"answer"}}
  │
  ├─ onIceCandidate → transmit-data {participantId, data:{candidate}}
  │
  ├─ remote candidates → addIceCandidate (после remoteDescription)
  │
  ├─ accept-call {mediaSettings}
  │
  └─ ICE CONNECTED → media (opus)
```

Зависимости входящего:
- call_id ← messages.getCurrentCalls (нужен web-токен)
- conversation params ← session_key (введён в настройки)
- okcdn uid ← prefs.callsSessionUid (введён)
- WS ← userId + token + conversationId

---

## 6. ИСХОДЯЩИЙ ЗВОНОК — маршрут (что нужно реализовать)

```
[ПОЛЬЗОВАТЕЛЬ] жмёт «Позвонить» в чате
  │
  ├─ messages.startCall(peer_id) → {call_id}         // VK API
  │   (ВНИМАНИЕ: VK-клиент генерирует СВОЙ conversation_id = uuid!)
  │
  ├─ conversation_id = uuid()  (генерирует клиент, ConversationFactory)
  │
  ├─ vchat.startConversation POST calls.okcdn.ru/fb.do
  │   {conversationId:uuid, isVideo:false, protocolVersion, capabilities,
  │    uids:<peer_id>, payload:"", joiningAllowed:false, requireAuthToJoin:false}
  │   → {id, endpoint, token, turn_server, stun_server, ...}
  │   (этот метод в PinoK НЕ реализован — НУЖНО ДОБАВИТЬ в VKApiClient)
  │
  ├─ WebSocket connect (userId=okcdn uid, token, conversationId=uuid)
  │   → notification:connection
  │   → (собеседник ответит offer/answer)
  │
  ├─ engine.startCall(call, isInitiator=true)
  │   → createLocalAudioTrack + createPeerConnection
  │   → createOffer() → transmit-data {participantId, data:{sdp:offer, type:"offer"}}
  │
  ├─ принимаем answer от собеседника → setRemoteSdp(answer)
  │
  ├─ ICE candidates обе стороны
  │
  └─ ICE CONNECTED → media
```

Зависимости исходящего (чего НЕТ в PinoK):
1. `messages.startCall` — есть в VKApiClient (возвращает call_id)
2. **`vchat.startConversation`** — НЕТ, нужно добавить
3. Генерация conversation_id (uuid) — нужно
4. WS connect + createOffer — частично есть в CallSignalingClient/WebRtcEngine
5. participantId для исходящего — из notification:connection (peerId)

---

## 7. WEBRTC ENGINE — ТРЕБУЕМАЯ КОНФИГУРАЦИЯ (из VK)

```kotlin
// Factory (SharedPeerConnectionFactory):
factory = PeerConnectionFactory.builder()
    .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglCtx, true, true))
    .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglCtx))
    .setAudioDeviceModule(JavaAudioDeviceModule.builder(ctx)
        .setUseHardwareAcousticEchoCanceler(false)
        .setUseHardwareNoiseSuppressor(false)
        .createAudioDeviceModule())
    .createPeerConnectionFactory()

// PC (PeerConnectionClient):
pcConstraints.optional.add("DtlsSrtpKeyAgreement", "true")   // обязательно
config.tcpCandidatePolicy = ENABLED
config.bundlePolicy = MAXBUNDLE
config.rtcpMuxPolicy = REQUIRE
config.continualGatheringPolicy = GATHER_CONTINUALLY
config.keyType = ECDSA
config.iceTransportsType = ALL
config.sdpSemantics = UNIFIED_PLAN
// TURN: добавить ?transport=tcp вариант + основной

// SDP constraints:
sdpConstraints.mandatory.add("OfferToReceiveAudio", "true")
sdpConstraints.mandatory.add("OfferToReceiveVideo", "true")  // как VK
```

---

## 8. КРИТИЧЕСКИЕ ПОДВОДНЫЕ КАМНИ (проверено на практике)

1. **Без JavaAudioDeviceModule** → SIGABRT `front() called on an empty vector` (ИСПРАВЛЕНО).
2. **createAnswer должен вызываться в onSetSuccess** (после установки remote SDP), а не сразу.
   SdpObserverAdapter: для setRemoteDescription нужен `onSetSuccess`, НЕ `onSuccess`!
3. **userId в WS URL = okcdn uid** (584520805550), не VK id → иначе invalid-token.
4. **TURN/STUN из conversation params** обязательны (хардкод videostun не работает).
5. `conversation_id` в vchat — с подчёркиванием.
6. Для исходящего conversation_id генерирует клиент (uuid), НЕ берёт из startCall.
7. Только audio m-line: ответ содержит и video m-line (recvonly) — это норма.

---

## 9. ЧТО НУЖНО СДЕЛАТЬ ДАЛЬШЕ

1. **Проверить входящий** (текущая сборка с onSetSuccess) — создаётся ли answer, ICE CONNECTED?
2. **Добавить исходящий**:
   - `VKApiClient.startConversation()` (vchat.startConversation)
   - генерация conversation_id (uuid)
   - `engine.startCall(isInitiator=true)` + createOffer
   - отправка offer через transmit-data
   - обработка answer от собеседника
3. Если ICE всё ещё не CONNECTED — сравнить answer SDP с эталоном VK
   (проверить a=setup, m-line, DTLS fingerprint).

---

## 10. ЭТАЛОН: УСПЕШНЫЙ ЗВОНОК ЧЕРЕЗ CHROME (эмулятор Android 16, pcap)

Снято 2026-08-23: входящий звонок в Chrome (мобильный vk.ru) на эмуляторе.
pcap: `logs/call2.pcap`.

**Сетевые потоки успешного звонка (эталон):**

| Поток | Направление | Пакеты | Роль |
|---|---|---|---|
| UDP `95.26.26.135:37922` ↔ `10.0.2.16:42090` | P2P | **2086+2029** | WebRTC media (прямой P2P!) |
| UDP `10.0.2.16:42090` → `193.203.43.26:19302` | TURN relay | 22 | ICE relay |
| UDP `10.0.2.16:42090` → `90.156.236.127:19302` | TURN relay | 20 | ICE relay |
| TCP `90.156.236.127:19302` ↔ `10.0.2.16:58386` | TURN TCP | 32 | ICE TURN TCP |
| UDP `10.0.2.16:42090` → `192.168.0.101:42638` | host | 22 | локальный кандидат |
| TCP `155.212.204.12:443` | calls.okcdn.ru | 56+ | vchat API + WS |

**Выводы (эталон):**
1. **P2P media работает** (95.26.26.135 — публичный IP звонящего, тот же что в srflx-кандидатах).
2. **TURN relay работает** (193.203.43.26, 90.156.236.127 — из conversation params).
3. **Конфигурация ICE (STUN/TURN из params) — ПРАВИЛЬНАЯ**. Chrome соединился.
4. Значит проблема PinoK — НЕ в iceServers и НЕ в протоколе сигналинга.
   Причина ICE CHECKING→CLOSED — в **SDP-генерации libjingle** (unified plan, codec priority,
   a=setup, DTLS), либо в порядке answer/кандидатов.

**Отличие libjingle (PinoK) vs браузерный WebRTC (Chrome):**
- Chrome использует нативный браузерный WebRTC (новый, актуальный).
- PinoK — stream-webrtc 1.3.10 (M107+ libjingle).
- Оба должны быть совместимы; разница только в деталях SDP.

