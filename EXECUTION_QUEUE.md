# EXECUTION QUEUE — план-очередь выполнения (2026-09-23)

Источники: разбор 5 доков tempfile.org (`asKMQg9Dbrt`, `o1eUfLxF1du`, `Vs8XE4DeDdQ`, `S7n7upSEhRL`, `7Bqc93Eh2AA`) + бэклог сессии.

**Поправка статуса от юзера (2026-09-23):** remote-hangup через 8–12с в логе redmi — РУЧНОЕ завершение звонка собеседником (НОРМА), не баг. err=10 WAF на `vchat.joinConversation` звонок НЕ ломает (offer→accept→answer→ICE CONNECTED были идеальны) → #CALLS-JOIN-RETRY (0ef4ab6) остаётся как устойчивость/диагностика, НЕ критический фикс. Критичного остатка по звонкам НЕТ.

## Статус 5 доков

| Файл | Что это | Статус |
|---|---|---|
| `asKMQg9Dbrt` — «ДВА экрана при входящем» (22.09) | разбор бага баннер + CallScreen одновременно | **ЗАКРЫТО** — guard'ы #CALLS-NO-DOUBLE-CALLSCREEN (bfd6161) |
| `o1eUfLxF1du` — «Fix двойного поднятия» (22.09) | предлагал УДАЛИТЬ `vchat.joinConversation` из accept-пути | **УСТАРЕЛО / ОТМЕНЕНО** — bfd6161 доказал обратное: join обязателен (регресс 7d1e459 — без него входящее не соединялось); оставлен + force-ретрай (0ef4ab6) |
| `Vs8XE4DeDdQ` — «DevTools общий чек-лист» | HAR / WS-фреймы / webrtc-internals инструкция | Справочник, частично дублирует более свежие |
| `S7n7upSEhRL` — «ЧТО ВЫТАЩИТЬ ИЗ VK» | прицельная инструкция: формат a_check (key=cred.key+comms.key, ts=ts_ts), localStorage-ключи, signaling URL | **АКТУАЛЬНО**, но по queue-части уже реализовано (ee8cb4e, b2d81e5) |
| `7Bqc93Eh2AA` — «VK CALLS DEVTOOLS» | §6 «что уже снято» / §7 «что осталось снять» | **АКТУАЛЬНО** — источник Блока A очереди |

## Блок A — съём данных через DevTools (выполняет ЮЗЕР, ПК Chrome `--remote-debugging-port=9222`)

Чек-лист §7 из `7Bqc93Eh2AA` (по каждому пункту данные → `INCOMING_CALL_REVERSE.md` / `CALLS_MAP.md`):

- [ ] **A-1** Исходящий звонок: `messages.startCall` (или `vchat.startConversation`) — точный метод + params; `vchat.clientStats` c `source:outgoing`
- [ ] **A-2** `accept-call` — полный JSON-фрейм WS (что шлёт VK web)
- [ ] **A-3** offer/answer SDP + формат ICE candidates (WS frames)
- [ ] **A-4** `vchat.getCallParams` / `getConversationParams` — endpoint, params, ПОЛНЫЙ ответ сервера
- [ ] **A-5** `messages.getCurrentCalls` — полная структура ответа (items/vcid/participants)
- [ ] **A-6** Список ВСЕХ `vchat.*` методов (перехват всех `fb.do` за сессию)
- [ ] **A-7** device_id — какой из (reforged / tracer / one-stat) реально уходит в запросы
- [ ] **A-8** WebTransport vs WebSocket — что реально используется VK web (у PinoK WS `wss://videowebrtc.okcdn.ru/ws2`)

## Блок B — код-правки PinoK (выполняет ассистент)

- [ ] **B-1 (P0-2)** `settingsGeneral.setNotifySettings` err=3 → обёртка в `batch.call` на web.api.vk.ru (как VK web)
- [ ] **B-2 (P2)** `getSecurityAlerts` / `getActivityHistoryDevices` err=3 — вероятный общий корень: `AuthDomainsConfig` api.vk.com→vk.ru
- [ ] **B-3 (P2)** mediaThumbs redesign-парсер
- [ ] **B-4** Исходящий звонок: сверить params с данными A-1/A-4, добить недостающие поля (после съёма)

## Блок C — #AUTH-FIRST-OPEN-GUEST (план готов, реализация по апруву юзера)

План: `docs/AUTH-FIRST-OPEN-GUEST-PLAN.md`. Суть: первый запуск без токена — сразу
guest-режим (без AuthActivity); вход — фиксированная кнопка «Войти в аккаунт» в
guest-drawer (запускает AuthActivity → LandingScreen внутри неё). Silent re-login
по remixsid остаётся автоматическим (невидимый).

- [ ] **C-1** MainActivity: boot-no-token без remixsid / silent исчерпан → guest (не launchAuth)
- [ ] **C-2** MainActivity: network-restored-no-token и token-invalidated tick — только silent, иначе guest
- [ ] **C-3** GuestDrawer.kt: ModalNavigationDrawer вокруг OfflineManagerScreen, фиксированный хвост «Войти в аккаунт» → launchAuth("drawer-login")
- [ ] **C-4** Guest onBack: убрать relaunch AuthActivity ("offline-back-to-login")

## Порядок

1. **B-1** — можно делать сразу, данных не требует (P0-2).
2. **A-1..A-8** — параллельно, ждут съёма от юзера.
3. **B-2, B-3** — после B-1 (P2).
4. **B-4** — финал, после данных Блока A.
5. **Блок C** — по апруву юзера (план составлен 2026-09-23).

---
## Статус исполнения (2026-09-23, сессия 2)

- [x] **B-1 (P0-2)** ВЫПОЛНЕНО: settingsGeneral.setNotifySettings (и get) — batch.call fallback на web.api.vk.ru; wire batchCall приведён к эталону CDP (VKApiClient.kt, VKApiClient #P0-2)
- [x] **ВНЕПЛАНОВО: #CALLS-MUTE-HARDEN** — репорт юзера «mute не отключает микрофон»: setMuted глушит localAudioTrack + все аудио-сендеры PC, mutedState переживает пересоздание трека, диагностический лог enabled (WebRtcEngine.kt). UI-обвязка проверена — корректна. Ждёт теста звонком
- [ ] B-2, B-3, B-4, A-1..A-8 — как выше
