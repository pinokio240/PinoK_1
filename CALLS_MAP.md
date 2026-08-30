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

### 0.2 ✅ СТАТУС ПРОТОКОЛА: РАБОТАЕТ (30.08, серия 21:49–21:51, лог logs-dl/test-mixed/ciber.txt, md5 ef73d876…)

| Направление / сценарий | Статус | Доказательство |
|---|---|---|
| Входящий (официальный → PinoK) | ✅ РАБОТАЕТ | тест 6 17:55 (23с разговора, ICE за 0.5с) + серия 21:50 (#2, #4) |
| **Исходящий (PinoK → официальный)** | ✅ **РАБОТАЕТ** | серия 21:49–21:50 (#1, #3): startCall → registered-peer → offer доставлен → answer применён → ICE CONNECTED → чистое завершение |
| Смена сети Wi-Fi → мобильная в течение сессии | ✅ OK | звонок #4 в мобильной сети: host 10.210.0.1, srflx 85.249.23.x |
| Двойной answer (ретрай официального, тот же o=, version 2→3) | ✅ дедупликация | «повторный answer проигнорирован (уже применён)», ICE поднялся |
| Приём/отправка ВИДЕО | ❌ выключено | #CALLS-VIDEO-INACTIVE: краш H265-декодера 21:50:51 (§8 п.7); возврат — план §11 |
| Cross-network, если у стороны UDP заблокирован | ❌ только SFU | §34 звонки.md; дорожная карта SFU-клиента |

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
Входящие (уведомления) — ДОПОЛНЕНО логами 21:49–21:51 (все строки встречены в реальном логе):
```
{notification:"connection", conversationParams:{turn,stun,serverTime,activityTimeout}, conversation:{state:"ACTIVE",topology:"DIRECT",participants:[...]}}
{notification:"registered-peer", participantId}                                  // наш пир открыл сигналинг (исходящий)
{notification:"accepted-call"}                                                    // собеседник взял трубку (исходящий)
{notification:"transmitted-data", peerId:{id}, data:{sdp:{type:"offer"|"answer"}}} // SDP-обмен
{notification:"transmitted-data", peerId:{id}, data:{candidate:{...}}}
{notification:"hungup", reason:"HUNGUP", participantId}                           // собеседник завершил
{notification:"closed-conversation", reason:"HUNGUP"}                             // закрытие беседы (следом за hungup)
{command:"media-settings-changed", mediaSettings:{isAudioEnabled,isVideoEnabled,...}} // собеседник переключил микрофон/камеру
{command:"settings-update", camera:{maxDimension,maxBitrateK}, badNet/goodNet}     // серверные лимиты (игнорируем)
{type:"response", sequence:N, response:"accept-call", participantIds:[...]}        // ack с подтверждением доставки
{type:"response", sequence:N, response:"transmit-data"}                            // ack БЕЗ participantIds
{stamp:0, error:"invalid-request", message:"..."}   // ошибка
```
Транспортный пинг: сервер шлёт `ping` → обязан ответить `pong` (#CALLS-WS-PONG, иначе разрыв).


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
7. **ВИДЕО m-line (ОБНОВЛЕНО 30.08 — старая запись «recvonly — норма» ОШИБОЧНА)**:
   UnifiedPlan игнорирует `OfferToReceiveVideo=false` (это Plan B-констрейнта) — авто-транссивер
   отвечал АКТИВНЫМ `m=video a=recvonly`; при включении камеры собеседником
   (`media-settings-changed isVideoEnabled=true`) H.265 (первый кодек в offer официального) шёл
   в декодер → **НАТИВНЫЙ краш процесса** (21:50:51, ни одной Kotlin-строки; стека в логе нет —
   тег AndroidRuntime не в фильтре). Фикс #CALLS-VIDEO-INACTIVE: перед createAnswer все
   video-транссиверы → INACTIVE + SDP-страховка demoteVideoRecvOnly (a=recvonly→a=inactive
   только в m=video-секции). Приём видео вернёт Этап 1 плана §11 — со strip H265.
8. `connection.mediaSettings.isVideoEnabled=false` ДАЖЕ у видео-звонка — connection НЕ маркирует
   видео-запрос. Единственный признак ДО accept: наличие `m=video` в буферизованном offer.

---

## 9. ЧТО НУЖНО СДЕЛАТЬ ДАЛЬШЕ (обновлено 30.08 — пп.1-2 старого списка ГОТОВЫ)

~~1. Проверить входящий~~ → ✅ РАБОТАЕТ (тест 6 + серия 21:50).
~~2. Добавить исходящий~~ → ✅ РАБОТАЕТ (серия 21:49–21:50, полный цикл с ICE CONNECTED).

Осталось (в порядке приоритета):
1. **ВИДЕО: приём + согласие на камеру** — план §11 (видеоответ recvonly + consent на отправку).
2. **SFU-клиент** (серверная топология) — откроет cross-network звонки при заблокированном UDP
   у собеседника (§34 звонки.md): allocate-consumer/accept-producer, producer-updated/consumer-answered.
3. Завершение звонка: hangup-reason enum уже нормализован (#CALLS-HANGUP-REASON-ENUM);
   следить за новыми кодами сервера.

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


---

## 11. ПЛАН: ВИДЕО В PinoK — видеоответ + согласие на камеру (30.08)

### 11.0 Семантика (утверждена пользователем)
- **Входящий видео-звонок**: после accept мы АВТОМАТИЧЕСКИ видим видео звонящего.
- **Наша камера по умолчанию НЕ передаётся**, даже если она физически исправна и свободна.
- Передача нашего видео — только **ЯВНОЕ согласие**: кнопка + подтверждающий диалог.
- Отзыв согласия в любой момент → наше видео к собеседнику прекращается.
- У официального клиента своя зеркальная логика consent (наблюдаем по его isVideoEnabled).

### 11.1 Факты API/SDP (проверено логом 21:50, звонок #4)
| Факт | Значение |
|---|---|
| offer официального (видео-звонок) | `a=group:BUNDLE 0 1 2`: audio mid:0 + **video mid:1** + application/datachannel mid:2 |
| кодеки видео в offer (в порядке приоритета) | **H265(39)** + rtx(40), H264(100/101), VP8(96/97), VP9(98/99), red(103), rtx(104), ulpfec(107) |
| `connection.mediaSettings` | `isVideoEnabled:false` ДАЖЕ у видео-звонка — НЕ маркер |
| признак видео-звонка до accept | наличие `m=video` в буферизованном offer |
| «камера включена» | `media-settings-changed isVideoEnabled:true` (единственное событие перед крашем) |
| краш 21:50:51 | активный `m=video a=recvonly` в нашем answer + включение камеры → H.265 в декодер → нативный краш |
| текущий фикс | #CALLS-VIDEO-INACTIVE: все video-транссиверы → INACTIVE (звонок стабильно аудио) |
| данные стороны (datachannel mid:2) | согласуется у официального; наш onDataChannel игнорирует — безопасно |

### 11.2 Этап 1 — ПРИЁМ видео («видеоответ», recvonly)
1. **Режим вместо «всегда OFF»**: `disableRemoteVideoTransceivers()` параметризуется флагом
   `videoRx = OFF (текущее) | RECEIVE`. В RECEIVE — транссивер `direction = RECVONLY`
   (НЕ stop(): mid=1 живёт, BUNDLE/ICE кандидаты с sdpMid=1 не отваливаются).
2. **H.265 ЗАПРЕТИТЬ в answer** (стек краша не снят — виновник не доказан; H.265 первый
   кодек и главный подозреваемый): munge answer — удалить payload 39/40 (rtpmap/fmtp H265+rtx)
   и вычистить их из списка `m=video`. Первый выживший кодек → **H264(100/101)** — HW-декодер
   на подавляющем большинстве Android; VP8/VP9 остаются как SW-фолбэк.
3. **Рендер**: CallScreen — `SurfaceViewRenderer` (AndroidView в Compose) — оверлей на весь экран
   с возможностью свернуть в карточку 1:1. Общий `EglBase.Context` с PeerConnectionFactory.
   `onAddTrack`: `if (receiver.track() is VideoTrack) videoTrack.addSink(renderer)`.
4. **Состояния UI**: `media-settings-changed isVideoEnabled:false` или нет пакетов >3с →
   плейсхолдер «Камера собеседника выключена» (аватар); ICE DISCONNECTED → заморозить кадр.
5. **Kill-switch**: настройка `callsVideoRx` (по умолчанию ON после этапа 1) — при краше
   декодера на конкретном устройстве пользователь выключает приём видео БЕЗ пересборки
   (fallback → текущее поведение INACTIVE).
6. **Cleanup обязателен**: `videoTrack.removeSink()` + `renderer.release()` в endCall/dispose —
   иначе утечка GL-текстур и повторный краш при следующем звонке.

### 11.3 Этап 2 — СОГЛАСИЕ НА СВОЮ КАМЕРУ (мы callee → звонящий)
1. **По умолчанию направление RECVONLY** — наше видео НЕ уходит (семантика «он наше не видит»).
2. **UI-согласие**: кнопка 🎥 на CallScreen → диалог «Показать собеседнику свою камеру?»
   [Показать | Отмена]. Первый показ → runtime permission `CAMERA` (Android 6+);
   отказ в разрешении → тост «Нужно разрешение на камеру», направление не меняется.
3. **Включение**: `Camera2Enumerator` → createCapturer (фронталка по умолчанию) →
   `factory.createVideoTrack` → `transceiver.direction = SEND_RECV` →
   `onRenegotiationNeeded` → createOffer → `transmit-data` (reoffer).
   Формат нашей отправки настроек (аналог входящего `media-settings-changed`) уточнить
   по эталону Conversation.js — в логах есть только входящее уведомление.
4. **Отзыв согласия**: `direction → RECVONLY` (+ capturer.stop() при полном выключении)
   → reoffer; контроль: кадры к собеседнику прекратились (pcap/статистика).
5. **Совместимость**: официальная сторона ОБЯЗАНА принять reoffer (RFC 3264, браузерный
   WebRTC). Риск: их UI может не ожидать видео от «аудио»-участника — проверить в тесте.
6. Если reoffer официальным теряется (аналог теста 13:06 из §33) — фолбэк:
   сначала `media-settings`-команда (если сработает), затем повтор reoffer.

### 11.4 Этап 3 (опционально) — ИСХОДЯЩИЙ видео-звонок ИЗ PinoK
1. `messages.startCall`: сейчас `voice=1`; видео-вариант — выяснить параметр
   (декомпилят VK+Calls APK / JS SDK; в LP-событии вызываемого payload маркирует видео).
2. offer: для этого кейса снять аудио-only (#CALLS-AUDIO-OFFER) — добавить `m=video`:
   **sendonly** до согласия пользователя (мы показываем своё видео только если сами
   явно включили), **sendrecv** после.
3. У вызываемого официального — их consent UI (не наша зона).

### 11.5 Порядок работ + тест-чеклист
| Шаг | Что | Проверка |
|---|---|---|
| 1 | videoRx=RECEIVE + strip H265 + рендер + kill-switch | видео-звонок от Лида: видео видно, 0 крашей; FULL_answer — H264 первым в m=video, нет rtpmap:39 |
| 2 | consent-кнопка + reoffer SEND_RECV | Лида видит наше видео ТОЛЬКО после кнопки; отзыв — кадр пропадает у неё |
| 3 | исходящий видео (§11.4) | startCall с видео-параметром; вызываемый видит запрос видео |
- Метрики: ICE CONNECTED; видео рендерится <2с после включения камеры собеседника; 0 нативных
  крашей; reoffer принимается с 1-й попытки; отзыв согласия останавливает поток кадров.
- Диагностика: держать наготове `adb logcat -b crash -d > crash.txt` (стек H265-подозреваемого);
  фильтр logcat с `tag:AndroidRuntime | tag:libc` (§35 звонки.md).
