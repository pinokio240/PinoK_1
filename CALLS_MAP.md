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

⚠️ **31.08 вечер (лог upload/Pasted Content_1788198811826.txt) — серия НЕ прошла, все 4 звонка умерли, гипотеза: антифрод VK по IP/устройству (WAF):**
- Входящий видео ×2: сигналинг ОК, answer валиден и ACKнут, НО пир 0 ответов на ICE (reqS=12 resR=0, в т.ч. relay↔relay через TURN VK) → через ~10с сервер `topology-changed DIRECT→SERVER` → SFU-offer не приходит → ICE FAILED → remote-hangup.
- Исходящий аудио ×2: `messages.startCall` OK, WS открыт (ping→pong), но сервер НЕ прислал FULL_CONNECTION/registered-peer → offer закэширован и ни разу не отправлен → отмена 15-18с.
- `vchat.joinConversation` → `PERMISSION_DENIED: ... is blocked for 512002378693 from IP 95.26.29.238` (WAF по IP/устройству; код join не менялся с 26.08).
- Дифф 95480ea..8c5432f (Этап 1 + компил-фиксы) НЕ трогает сигналинг/auth/queue/API — отказ в исходящем происходит ДО участия медиа-кода Этапа 1.
- Диагностика: сменить IP (Wi-Fi ↔ мобильная / перезагрузка роутера), проверить официальный клиент на том же Wi-Fi, спросить у пира звонил ли телефон.
- Патч: kill-switch `callsVideoRx=OFF` теперь даёт answer БИТ-В-БИТ как в работавшей серии 30.08 (strip H265 только в RECEIVE).

**01.09 11:23–11:29 (логи upload/Pasted Content_1788251249948.txt + _1788251560498.txt, коммит 449332df) — 3 звонка, 3 разных исхода:**
1. **Входящий Wi-Fi same-NAT (11:25)**: answer m=video **sendrecv** с SSRC (симметрия #CALLS-SYMMETRIC работает на SDP-уровне!) — гипотеза пользователя ОПРОВЕРГНУТА: EARLYSTATS пар=24 • reqS=103 resR=0 reqR=0 — пир по-прежнему 0 проверок и 0 ответов. FAILED ×2 → PC-RESTART ×2 → remote-hangup. topology-changed(SERVER, offerTo=[], connectTo=[]) на 9-й секунде — СЛЕДСТВИЕ (фолбэк сервера при несобравшейся ноге), а не причина (в успешном LTE-звонке 11:26 topology-changed НЕ пришёл вовсе).
2. **Входящий LTE (11:26)**: ICE CONNECTED за 2с, видео 544x960 рот270 декодировалось (frames=30), TextureView surface 1080x756 → ПЕРВЫЙ КАДР отрисован ✓ (11:27:09.224) → **НАТИВНЫЙ КРАШ в окне 11:27:09.3–11.9** (crash-буфер был, записи отфильтрованы фильтром пользователя; пир сбросил в 11:27:12). ВАЖНО: swDecode=true уже был в настройках и применён (CallScreen пушит до initialize) → краш НЕ HW-текстуры декодера. **Нужен дамп: `adb logcat -b crash -d > crash.txt` (буфер живёт до перезагрузки!)**.
3. **Исходящий LTE (11:29)**: наш оффер m=video **a=sendonly** (баг: addTrack создаёт транссивер SEND_ONLY, prepareVideoTransceivers вызывался только во входящем пути) + **H265 в оффере** → VK выбрал H265 для заглушки: initEncode **c2.mtk.hevc.encoder**. Оффер уехал **ТРИЖДЫ** (seq=2 flush → seq=3 REOFFER+113мс → seq=4 topology, одинаковая o=- строка), ответ дважды (11:25). Пир ответил (серверные host-кандидаты 155.212.197.168) и УМЕР на повторных setRemoteDescription — reqS=256 resR=0 reqR=0. FAILED → hangup(FAILED).
Фиксы: **#CALLS-OUT-SENDRECV** (prepareVideoTransceivers в startCall до createOffer), **#CALLS-OFFER-STRIPH265** (вырез H265 из оффера, как в answer), **#CALLS-SDP-DUP-GUARD** (choke-point sendSdpDedup: максимум 2 отправки одного SDP, 3-я блокируется; новый SDP после PC-restart/restartIce проходит), **#CALLS-CAND-FILTER-2** (loopback/any/tcp фильтр ДО обеих веток — trickle уходил бы как есть; в сборе есть 127.0.0.1/::1/0.0.0.0/tcp-мусор).

🔎 **31.08 ночь 21:20–21:26 (лог upload/Pasted Content_1788200986644.txt) — решающий эксперимент Wi-Fi vs мобильная: найдены ТОЧНЫЕ причины, вместо WAF-гипотезы — наши системные дефекты:**
- Входящий видео по Wi-Fi: answer ACKнут, но reqS=307 resR=0 **reqR=0** — пир не прислал НИ ОДНОЙ STUN-проверки даже после 3 REANSWER → ICE FAILED → topology-changed→SERVER (SFU-offer нет).
- Входящий видео по МОБИЛЬНОЙ (тот же код, те же стороны): **ICE CONNECTED за 0.7с, аудио работает** → сигналинг/согласование Этапа 1 в порядке; решает сеть.
- РАЗГАДКА Wi-Fi-отказа: answer уходил первым, но 10 trickle-кандидатов — залпом в первые 200мс; у эталона (OK/videochat DirectTransport) addIceCandidate до применения answer (async setRemoteDescription) роняет ВЕСЬ транспорт (catch→close). Пир так и не получил наши кандидаты → на его TURN-аллокации нет permissions для наших relay-IP → relay↔relay глохнет; строгий NAT Wi-Fi добивает direct-пути (LTE спасает peer-reflexive от наших проверок).
- ✅ ФИКС #CALLS-INLINE-ICE (WebRtcEngine): кандидаты зашиваются ВНУТРЬ SDP (одна посылка — гонка невозможна; loopback/tcp отфильтрованы — answer ~3.7КБ < порога доставки ~4КБ), trickle — только для кандидатов после отправки; lastLocalSdp всегда с кандидатами → REANSWER/REOFFER уезжают с ними.
- Исходящий аудио: `messages.startCall` OK, НО `ensureCallsSessionKey` → кэш $-токен ПРОТУХ → auth.anonymLogin 401 «Token is outdated» → sk2=null → **vchat.startConversation ПРОПУЩЕН** → conversation не начата → нет FULL_CONNECTION → offer в кэше навсегда (CALL END: offer=false answer=false ice=false).
- ✅ ФИКС #CALLS-TOKEN-REFRESH (SovaApp): провал на кэш-токене → свежий $-токен через messages.getCallToken → повтор auth.anonymLogin.
- ✅ ФИКС #CALLS-OUT-SK2-FALLBACK (CallScreen): при sk2=null startConversation идёт с session_key из prefs (начать conversation важнее свежести ключа).
- Видео «звонок прошёл, но видео не показывает» (LTE): камера пира включена (media-settings-changed isVideoEnabled=true в 21:22:57), НО Scaffold красил контент непрозрачным 0xFF1A1A2E ПОВЕРХ области SurfaceViewRenderer (поверхность ЗА окном) — видео физически не могло быть видно.
- ✅ ФИКС #CALLS-VIDEO-BG (CallScreen): при активном видео containerColor Scaffold/TopAppBar → Transparent; + лог videoFrames/renderActive (различение «кадры не идут» vs «рендер перекрыт»).

🔬 **31.08 вечер 22:25–22:32 (лог upload/ciber.txt) — AFTER #CALLS-INLINE-ICE: медиа-тракт ПРОВЕРЕН ДО КОДЕРОВ, найдены и починены 2 UI-бага + усилена диагностика ICE:**
- Входящий видео по Wi-Fi (оба телефона за ОДНИМ NAT 95.26.29.238): offer БЕЗ inline-кандидатов; 4 удалённых кандидата (srflx+3×relay) пришли ДО создания PC → PENDING-буфер → drain после setRemoteSdp SUCCESS (pending=4); answer ушёл С inline-кандидатами (#CALLS-INLINE-ICE работает). НО: ICE CHECKING → FAILED через 16с, stats «нет candidate-pair (пар=0 • reqS=0 resR=0 reqR=0)» — агент НЕ ОТПРАВИЛ НИ ОДНОЙ проверки. drain-boolean раньше игнорировался — терялись ли кандидаты, неизвестно.
- Входящий видео на МОБИЛЬНОЙ сети (тот же код): ICE CONNECTED за ~2с, фаза ACTIVE, peerCam=true, **videoFrames 0→35→83→…→321+ (видео ДЕКОДИРУЕТСЯ end-to-end: ICE+DTLS+SRTP+декодер)**, renderActive=true, ошибок нет — а на экране чёрный цвет → punch-through SurfaceView НЕ пробивается и в этой иерархии.
- ✅ ФИКС #CALLS-TIMER-FIX (CallScreen): таймер ЗАСТЫВАЛ на «0:05» навсегда — LaunchedEffect(phase) обновлял callDuration ОДИН раз и завершался (delay → присвоить → конец корутины). Теперь цикл каждую секунду.
- ✅ ФИКС #CALLS-VIDEO-ZORDER + #CALLS-VIDEO-DIAG (CallScreen): setZOrderMediaOverlay(true) (поверхность ПОВЕРХ окна — punch-through не нужен); видео = верхние 55% контента, панель статуса/кнопок прижата к низу; RendererEvents → лог «ПЕРВЫЙ КАДР отрисован ✓» (доказательство доставки кадров в surface). Прозрачность #CALLS-VIDEO-BG сохранена (не мешает).
- ✅ ФИКС #CALLS-ICE-DRAIN-LOG (WebRtcEngine): drain логирует результат addIceCandidate (boolean) — след. Wi-Fi-тест различит «кандидаты терялись в drain» vs «сетевой уровень».
- Побочное: vchat.joinConversation снова в WAF-блоке по обоим IP (не мешает: accept-call ушёл, звонки соединялись). «Пинок не просит разрешение на свою камеру» — Этап 2, ещё не реализован (по умолчанию камера НЕ передаётся — так задумано).

🧪 **31.08 ночь 22:55–22:59 (лог upload/ciber.txt, 3495 строк + скриншоты 225721/225921) — тест ПОСЛЕ фиксов #CALLS-TIMER-FIX/#CALLS-VIDEO-ZORDER/#CALLS-ICE-DRAIN-LOG:**
- ✅ Таймер РАБОТАЕТ (скриншот 225921: 0:48 на живом звонке; фикс #CALLS-TIMER-FIX подтверждён).
- ✅ #CALLS-ICE-DRAIN-LOG отработал: все удалённые кандидаты добавлены (drain «добавлен» ×4 в обоих звонках; FALSE не было) — кандидаты НЕ теряются.
- ❌ Wi-Fi (22:55, оба за одним NAT): offer/answer/кандидаты доставлены (наши 6 inline ACKнуты сервером; их 4: srflx 95.26.29.238 + 3 relay), ICE CHECKING → **16с абсолютной тишины → FAILED; пар=0 • reqS=0 resR=0 reqR=0** — агент НЕ образовал НИ ОДНОЙ пары и НЕ отправил НИ ОДНОЙ проверки, при этом: сбор кандидатов УСПЕШЕН (host+srflx+4 relay за 1.5с — STUN/TURN доступен!), та же схема на LTE — CONNECTED за 2с. 4×REANSWER тем же answer бесполезны; topology-changed→SERVER в +10с; звонящий сдался (remote-hangup, dur=117с, ice=false).
- ❌ Мобильная сеть (22:58): ICE CONNECTED за 2с, peerCam=true, videoFrames 0→2094, renderActive=true, **«ПЕРВЫЙ КАДР отрисован на поверхности ✓» (RendererEvents)** — кадры ДОШЛИ ДО ПОВЕРХНОСТИ SurfaceView — а экран/скриншот ЧЁРНЫЙ. Значит дело НЕ в punch-through/прозрачности: аппаратный слой SurfaceView на HOTWAV Cyber 15 (MTK, Android 13) не попадает в композицию окна/скриншота даже НАД окном (zOrderMediaOverlay).
- ✅ ФИКС #CALLS-VIDEO-TEXVIEW (VideoTextureRenderer.kt — НОВЫЙ класс + CallScreen): TextureView-рендерер на базе org.webrtc.EglRenderer (в артефакте 1.3.10 TextureViewRenderer отсутствует, но EglRenderer публичен: init(EglBase.Context,int[],GlDrawer) / createEglSurface(Surface) / releaseEglSurface(Runnable) / onFrame — сверено парсингом constant pool classes.jar; GlDrawer=null — ровно то, что передаёт SurfaceViewRenderer.init(ctx,events)). TextureView — обычный view-узел: композитится GPU вместе с окном, без punch-through и z-order, попадает в скриншоты. RendererEvents-эмуляция (первый кадр/резолюция) — внутри класса.
- ✅ ФИКС #CALLS-PC-RESTART (WebRtcEngine.recreateAndReanswer + CallScreen ICE FAILED): REANSWER тем же answer доказанно бессмыслен при пар=0. Пересоздаём PC, применяем СОХРАНЁННЫЙ offer (поле lastRemoteOffer), отвечаем НОВЫМ answer: новые ice-ufrag/pwd = ICE-restart у собеседника (RFC 5245 §9) — свежий check-list и permissions на его TURN-аллокации. Аудио source/track переиспользуются.
- ✅ ФИКС #CALLS-ICE-EARLYSTATS (WebRtcEngine): снимок candidate-pair stats на 5-й секунде CHECKING — следующий лог различит «пары формировались и умерли» (пар>0, reqS>0) vs «пары не создавались вовсе» (пар=0).
- ✅ #CALLS-NATIVE-LOG (WebRtcEngine.initialize): Logging.enableLogToDebugOutput(LS_INFO) — нативные логи libwebrtc (формирование пар, привязка портов, pruning) в logcat; тег в logcat — **«logging»** (добавить в фильтр захвата!).
- Ожидание от след. теста: видео видно на мобильной (и в скриншоте); по Wi-Fi либо звонок проходит после PC-restart (новые ufrag/pwd заставят пира перепроверить), либо в логе есть EARLYSTATS+нативные строки «logging» — решающая диагностика.

🧪 **01.09 утро 09:50–09:53 (лог upload/Pasted Content_1788245628569.txt, 1502 строк) — тест коммита 44e3047 (PC-restart + TextureView): решающие данные EARLYSTATS + опровергнут «лимит 4КБ» + подтверждён рендер TextureView:**
- ✅ #CALLS-ICE-EARLYSTATS сработал и дал РЕШАЮЩИЙ сдвиг по Wi-Fi (09:50, оба за одним NAT 95.26.26.169): на 5-й секунде CHECKING **пар=24 • reqS=103 resR=0 reqR=0 resS=0, все пары in-progress** — пары ФОРМИРУЮТСЯ, проверки УХОДЯТ (103 шт., вкл. relay↔relay через TURN VK), но НЕ возвращается НИ ОДНОГО ответа и НЕ приходит НИ ОДНОЙ проверки от пира. При этом: исходный answer ушёл ОДНИМ сообщением с inline-кандидатами (seq=2, 4310Б, ACK сервера 09:50:18), сбор наших кандидатов успешен (host+srflx+4 relay), и — ключ — ТУРN-аллокации создались (UDP до TURN серверов ходит туда-обратно!). Вывод: пир получил answer (сервер подтвердил), но его агент НИЧЕГО не делает на сетевом уровне — без permissions на его аллокациях наши relay-проверки глохнут у его TURN (RFC 5766), а его проверки не стартуют вовсе. PC-RESTART ×2 (новые ufrag/pwd) не помог — пир не реагирует и на рестарт. Это поведение пира в same-NAT, не наш дефект проводки.
- ❌ ОПРОВЕРГНУТ «лимит доставки ~4КБ»: на мобильной сети (09:52) ответ того же формата стал ЕЩЁ БОЛЬШЕ (7 inline кандидатов, 4474Б) — и пир его ПРИМЕНИЛ (FULL_CONNECTION → ICE CONNECTED за 2с, звонок живой 77с). Размер ни при чём.
- ✅ TextureView РЕНДЕРИТ: звонок 2 (мобильная сеть) — media-settings isVideoEnabled=true в 09:52:18, videoFrames 0→32→…→1354 (24fps, 57с непрерывно), renderActive=true, **«ПЕРВЫЙ КАДР отрисован ✓ (TextureView)»** — кадры доставлялись в VideoTextureRenderer и рисовались до самого remote-hangup. Пользователь при этом видео на экране НЕ увидел (нужен след. тест с полной наблюдаемостью).
- ⚠ ГЭП НАБЛЮДАЕМОСТИ: в дампе нет НИ ОДНОЙ строки тега PinoK/VideoTextureRenderer (init/surface/первый кадр) при живом колбэке CallScreen из того же метода — тег не попал в фильтр logcat. ФИКС: рендерер логирует через тег «CallScreen» с префиксом «[TexView]» (видность гарантирована), + лог каждые 150 кадров, + createEglSurface в runCatching, + setDefaultBufferSize ДО создания EGL-поверхности (страх от буфера 1x1 → GL рисует «в никуда» → чёрный экран).
- ✅ НОВОЕ #CALLS-SYMMETRIC (гипотеза пользователя «аудиовидео должны ходить в обе стороны»): видеозаглушка наружу — чёрные кадры 320×180@10fps через createVideoSource/capturerObserver (БЕЗ камеры и разрешения CAMERA, API сверено по classes.jar), answer m=video → **SEND_RECV** — звонок симметричен как у офиц. клиента. Тумблер «Отправлять видеозаглушку» (Настройки→Звонки→Видео, callsVideoTx, default ON). Это и заготовка Этапа 2. Если с sendrecv Wi-Fi same-NAT заработает — гипотеза подтверждена на уровне поведения пира; если нет — остаётся сетевой уровень роутера (для чистоты: тест VK↔VK на одном Wi-Fi).
- ✅ НОВОЕ #CALLS-SWDECODE: тумблер «Программный декодер видео» (callsVideoSwDecode, default OFF, вступает после перезапуска приложения) — SoftwareVideoDecoderFactory вместо DefaultVideoDecoderFactory. Если со SW-декодером видео появится при чёрном экране — проблема в HW-текстурах декодера (shared EGL-контекст на MTK); + в getStats лог decoderImplementation/framesReceived по входящему видео.
- Ожидание от след. теста: Wi-Fi с видеозаглушкой (sendrecv) — соединение (гипотеза) или та же тишина (роутер/пир; тогда контрольный тест VK↔VK на одном Wi-Fi); мобильная — [TexView]-логи (surface/буфер/кадр #150) + при чёрном экране тумблер SW-декодера решает HW-vs-композиция.

| Направление / сценарий | Статус | Доказательство |
|---|---|---|
| Входящий (официальный → PinoK) | ✅ РАБОТАЕТ (30.08) / ❌ 31.08 вечер — см. warning выше | тест 6 17:55 (23с разговора, ICE за 0.5с) + серия 21:50 (#2, #4) |
| **Исходящий (PinoK → официальный)** | ✅ **РАБОТАЕТ** (30.08) / ❌ 31.08 вечер — нет FULL_CONNECTION | серия 21:49–21:50 (#1, #3): startCall → registered-peer → offer доставлен → answer применён → ICE CONNECTED → чистое завершение |
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

### 11.2 Этап 1 — ПРИЁМ видео («видеоответ», recvonly) — ✅ РЕАЛИЗОВАН 2026-08-31 (#CALLS-VIDEO-RX)
1. ✅ **Режим вместо «всегда OFF»**: `disableRemoteVideoTransceivers()` заменён на
   `prepareVideoTransceivers()` + `@Volatile videoRxEnabled` (`setVideoRxEnabled`).
   RECEIVE → транссивер `direction = RECVONLY` (НЕ stop(): mid=1 живёт, BUNDLE/ICE
   кандидаты с sdpMid=1 не отваливаются); OFF → INACTIVE (прежнее поведение).
2. ✅ **H.265 ЗАПРЕТЕН в answer** (стек краша не снят — виновник не доказан; H.265 первый
   кодек и главный подозреваемый): `stripH265()` — munge answer: находит payload'ы H265
   по `a=rtpmap:<pt> H265/` + rtx-потомков по `a=fmtp:<pt> … apt=<h265>`, вырезает их
   из списка `m=video` и удаляет все `a=rtpmap/a=fmtp/a=rtcp-fb` строки этих payload'ов.
   Вызывается ТОЛЬКО в режиме RECEIVE (#CALLS-WAF 557fc9b): в OFF видео не согласуется
   (a=inactive), декодер не стартует — answer остаётся БИТ-В-БИТ как в работавшей серии
   30.08 (kill-switch = точный откат wire-формата одним тумблером). Первый выживший кодек →
   **H264(100/101)** — HW-декодер на подавляющем большинстве Android; VP8/VP9 — SW-фолбэк.
3. ✅ **Рендер**: **VideoTextureRenderer (TextureView)** через `AndroidView`. ⚠️ ИСПРАВЛЕНО (2026-08-31):
   план изначально называл TextureViewRenderer, но в артефакте
   `io.getstream:stream-webrtc-android:1.3.10` его НЕТ (проверено по classes.jar: из
   рендереров только SurfaceViewRenderer / SurfaceEglRenderer / EglRenderer /
   VideoFileRenderer) — отсюда краш компиляции «Unresolved reference
   'TextureViewRenderer'».
   ✅ #CALLS-VIDEO-BG (звонок 21:22 LTE): containerColor Scaffold при активном видео →
   Transparent. ОКАЗАЛОСЬ НЕДОСТАТОЧНО (см. ниже).
   ✅ #CALLS-VIDEO-ZORDER (лог 22:28 mobile): setZOrderMediaOverlay(true) + раскладка
   «видео = верхние 55% (TopCenter) / панель прижата к низу». НЕДОСТАТОЧНО (см. ниже).
   ✅ **#CALLS-VIDEO-TEXVIEW (22:58 mobile — РЕШАЮЩИЙ ФИКС)**: в логе 22:58 кадры
   декодировались (0→2094), renderActive=true и **«ПЕРВЫЙ КАДР отрисован на поверхности ✓»**
   (RendererEvents) — кадры реально доходили до SurfaceView — а экран/скриншот ЧЁРНЫЙ.
   Аппаратная поверхность SurfaceView (HOTWAV Cyber 15, MTK, Android 13) не попадает в
   композицию окна/скриншота даже с zOrderMediaOverlay. Решение: собственный
   **VideoTextureRenderer** (VideoTextureRenderer.kt) — TextureView + org.webrtc.EglRenderer
   (VideoSink): обычный view-узел, композитится GPU вместе с окном — без punch-through,
   без z-order, виден в скриншотах. API сверено парсингом constant pool classes.jar 1.3.10:
   EglRenderer(String), init(EglBase$Context,[I,GlDrawer)V — GlDrawer=null (ровно как
   SurfaceViewRenderer.init(ctx,events)), createEglSurface(Surface)V,
   releaseEglSurface(Runnable)V, setLayoutAspectRatio(F)V, onFrame(VideoFrame)V;
   RendererEvents-эмуляция (первый кадр/резолюция) — внутри класса.
   Разметка «видео сверху / панель снизу» и cleanup (removeSink + releaseRenderer ровно
   один раз в onDispose) сохранены.
   Сверка API по classes.jar 1.3.10 (скачан с Maven Central): SurfaceViewRenderer
   extends android.view.SurfaceView; init(EglBase$Context, RendererCommon$RendererEvents)V;
   RendererEvents = onFirstFrameRendered()V + onFrameResolutionChanged(III)V.
   Общий `EglBase.Context` с PeerConnectionFactory — **НАЙДЕН и закрыт скрытый баг**:
   раньше `eglBase` создавался локально в `initialize()` и релизился СРАЗУ после
   создания factory — для аудио это не мешало, но видео-декодер с терминированным
   EGL-контекстом не работает. Теперь `eglBase` живёт вместе с factory (release в
   `release()` ПОСЛЕ `factory?.dispose()`), наружу — `eglBaseContext()`.
   Колбэк `onRemoteVideoTrack(track|null)` (onAddTrack; endCall/release отдают null).
4. ✅ **Состояния UI**: `media-settings-changed isVideoEnabled` → `peerVideoEnabled`;
   поллинг `framesDecoded` inbound-rtp каждые 2с (`pollVideoFramesDecoded`) — рендер
   стартует ТОЛЬКО при кадрах > 0, пока кадров нет — плейсхолдер (аватар + статус:
   «Приём видео выключен (Настройки → Звонки)» / «Камера собеседника выключена» /
   «Ждём видео собеседника…»). Бейдж «Входящий видеозвонок…» по `m=video` в offer.
   Аватар и имя скрываются, когда видео рендерится (как в VK).
5. ✅ **Kill-switch**: настройка `callsVideoRx` (SovaPrefs, default ON) — тумблер
   Настройки → Звонки → Видео «Приём видео собеседника»; читается в CallScreen сразу
   после композиции (до первого answer); при OFF — прежнее поведение INACTIVE БЕЗ пересборки.
6. ✅ **Cleanup обязателен**: DisposableEffect.onDispose → `videoTrack.removeSink()` +
   `renderer.release()` (release ровно ОДИН раз — повторный бросает); `endCall()/release()`
   сбрасывают remoteVideoTrackRef → колбэк null → UI уходит из if-блока и чистит рендер.
   Иначе — утечка GL-текстур и повторный краш при следующем звонке.
   **Обновление Snapshot**: FeedScreen initial-конструктор получил `callsVideoRx = true`
   (тот же класс бага, что Fix #100/#110/#189 — Snapshot расширился).

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
| Шаг | Что | Проверка | Статус |
|---|---|---|---|
| 1 | videoRx=RECEIVE + strip H265 + рендер + kill-switch | видео-звонок от Лида: видео видно, 0 крашей; FULL_answer — H264 первым в m=video, нет rtpmap:39 | ✅ реализовано 2026-08-31, ЖДЁТ ТЕСТА |
| 2 | consent-кнопка + reoffer SEND_RECV | Лида видит наше видео ТОЛЬКО после кнопки; отзыв — кадр пропадает у неё | следующий |
| 3 | исходящий видео (§11.4) | startCall с видео-параметром; вызываемый видит запрос видео | — |
- Метрики: ICE CONNECTED; видео рендерится <2с после включения камеры собеседника; 0 нативных
  крашей; reoffer принимается с 1-й попытки; отзыв согласия останавливает поток кадров.
- Диагностика: держать наготове `adb logcat -b crash -d > crash.txt` (стек H265-подозреваемого);
  фильтр logcat с `tag:AndroidRuntime | tag:libc` (§35 звонки.md).
