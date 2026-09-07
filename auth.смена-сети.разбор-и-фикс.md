# Auth: смена сети (мобильная → Wi-Fi) — разбор бага «не сразу понимает, что делать, и требует авторизацию» и фикс

Тег: **#NET-SWITCH-AUTH-FIX** (2026-09-07). Коммит: волна «fix(auth)».

---

## §0. Симптом и запрос

Юзер: «Проверить и исправить что происходит при авторизации после смены сети с мобильной на ви-фи.
Приложение "не сразу понимает" что ему делать и пытается запросить авторизацию, хотя куки и
локал стордж целы. Авторизацию надо править очень аккуратно.»

Ключевые наблюдения из формулировки:
1. Проблема именно **после смены сети** (мобильная → Wi-Fi — новая подсеть/новый внешний IP).
2. Есть период «непонимания» (задержка/переборы), затем — попытка запросить авторизацию.
3. **Куки и localStorage целы** — то есть материалы сессии на устройстве есть, а приложение
   всё равно идёт в re-login. Значит решение «сессия мертва» принимается без оснований.

---

## §1. Замешанные контуры (карта механизмов)

| Файл / механизм | Роль |
|---|---|
| `core/common/.../NetworkObserver.kt` | Колбэки ConnectivityManager: `registerDefaultNetworkCallback` → `DEFAULT onAvailable` = смена default route → `lastDefaultNetworkSwitchTs` (grace-timestamp); `onLinkPropertiesChanged` = IP change. |
| `app/.../api/VKApiClient.kt` err=5/1117-хендлер (~:10506-10770) | Семейство grace-фиксов: **#96** (1117 = expired), **#175** (grace 30с после switch: не чистить токен), **#230** (subcode 1130 «token given to another ip»), **#IP-MISMATCH-GRACE** (grace и при 1130, если recentlySwitched), **#IP-BINDING-RETRY** (нет silent means → 5с + single retry старым токеном вместо мгновенного re-login), **#GRACE-NO-CLEAR**, **#RELOGIN-FORCE**. |
| `app/.../auth/exchange/ExchangeAuthRepository.kt` | `ensureFreshToken(force)`: **Path 1.5** = `silentRefreshViaRemixsid` (HTTP GET login.vk.ru `?act=web_token` с Cookie header из **storage**), **Path 5** = connect_exchange_token. **Fix #49**: если VK ЯВНО отверг remixsid → `storage.clearRemixsid()` (ломает SILENT-петлю). `hasSilentReloginMeans()` = `storage.remixsid || storage.pCookie` — читает ТОЛЬКО storage, НЕ CookieManager. `refreshSessionCookiesFromCookieManager()` — sync CookieManager → storage (patch, без сети; триггеры: foreground, 6ч Worker, хук после успешного Path 1.5). |
| `app/.../SovaApp.kt` `registerGlobalNetworkWatcher` (~:2103) | **#VKID-SEAMLESS**: на смене default route — проактивный `ensureFreshToken(force=true)` в фоне + `setNetworkSwitchState(Switching/Idle)`. |
| `app/.../mods/network/VkCookieJar.kt`, OkHttp | Сессия живёт в: CookieManager (WebView, обновляется при web-навигации) + ExchangeTokenStorage (SharedPreferences: access_token, remixsid, p, exchange_token, …). |
| `app/.../SovaApp.kt` `notifyTokenInvalidated` | tick → MainActivity перезапускает **AuthActivity** (SILENT авто-вход; после MAX_SILENT_FAILURES — FULL, видимый вход). |

---

## §2. Root-cause разбор (что именно ломалось)

### Сценарий A (главный): стейловый storage-remixsid уничтожается на смене сети

VK **ротейтит** `remixsid` при security events (док-комментарий `#SESSION-COOKIES-BG-REFRESH`,
ExchangeAuthRepository ~:688: «при смене сети silentRefreshViaRemixsid шлёт устаревший Cookie
header → VK отбрасывает → полный re-login»). Свежая копия всегда есть в **CookieManager**,
но Path 1.5 читает **storage**.

Таймлайн бага (mobile → Wi-Fi):
1. `DEFAULT onAvailable` → SovaApp #VKID-SEAMLESS запускает `ensureFreshToken(force=true)`.
2. Path 1.5 уходит с **стейловым** storage-remixsid → VK явно отвергает (auth rejection JSON).
3. Fix #49: «remixsid definitively dead» → **`storage.clearRemixsid()`** — silent-средства
   УНИЧТОЖЕНЫ (хотя в CookieManager куки живы — ровно то, что видел юзер).
4. Первая API-задача на новом интерфейсе получает err=5/1130 → grace-контур →
   `ensureFreshToken(force=true)` → Path 1.5 **уже нечем** работать → Path 5 (web-сессии
   обычно без exchange_token) → null.
5. `hasSilentReloginMeans()` = false → **#RELOGIN-FORCE**: `clearAccessToken` +
   `notifyTokenInvalidated` → **AuthActivity**. При SILENT-неудаче/лимите — FULL форма.

### Сценарий B (гонка): err=5/1130 приходит РАНЬШЕ колбэка сети

Grace armed только по `lastDefaultNetworkSwitchTs` (ставится в `DEFAULT onAvailable`,
диспетчеризация через main-хендлер). Первый запрос через новый default route может получить
5/1130 раньше тика колбэка → `recentlySwitched=false` → **холодный путь**: один force refresh
→ (без silent means) мгновенно `clearAccessToken` + AuthActivity. Это и есть «не сразу
понимает, что делать» — приложение мечется между refresh/retry/re-login.

### Почему subcode 1130 — самодостаточное доказательство

Текст ошибки: *«access_token was given to another ip address»*. VK не вернул бы 1130,
если бы IP не менялся → сигнал о смене сети есть уже в САМОЙ ошибке, ждать тика
NetworkObserver не обязательно.

---

## §3. Фикс (3 хирургические точки, #NET-SWITCH-AUTH-FIX)

### 3.1 `ExchangeAuthRepository.ensureFreshToken` — sync cookies ДО Path 1.5 при force=true

Перед попыткой Path 1.5: `refreshSessionCookiesFromCookieManager()` (локальный, БЕЗ сети:
CookieManager → patch в storage). Теперь force-refresh (проактивный на switch, реактивный
grace-контур, IP-RETRY, keepAlive §44) всегда шлёт **свежайший** remixsid. Если VK отвергает
даже свежий — контракт Fix #49 сохранён: сессия действительно мертва → clear → FULL re-login.

### 3.2 `SovaApp.registerGlobalNetworkWatcher` — sync ДО гварда silent-средств

Проактивный блок перестроен: `sync → hasSilentReloginMeans() → ensureFreshToken(force=true)`.
Раньше гвард читал стейловый storage и ложно пропускал проактивный refresh (оставался только
медленный #SESSION-HOLD WebView-capture ≤10с). Теперь на смене сети сперва подтягиваются
куки, и Path 1.5 срабатывает ДО первой API-задачи — бесшовно, без popup'а и без err=5.

### 3.3 `VKApiClient` err=5-хендлер — 1130 сам arm'ит grace

- attempt==0: `graceEligible = recentlySwitched || isIpMismatch` → 3-атtemptный grace-цикл
  (sync внутри force-refresh + Path 1.5) вместо холодного пути.
- attempt>0: тот же критерий → `return null` БЕЗ clearAccessToken.

Закрывает сценарий B: даже если колбэк сети ещё не тикнул, 1130 гарантирует grace-семантику
(задержки, silent refresh, retry) вместо мгновенного re-login.

---

## §4. Почему аккуратно (сохранённые контракты)

- **Fix #49** (#DEAD-REMIXSID): явное отвержение VK даже свежего remixsid по-прежнему чистит
  его и ломает SILENT-петлю. Фикс не «прощает» мёртвые сессии — он убирает ложную смерть живых.
- **Fix #175/#230/#IP-MISMATCH-GRACE/#IP-BINDING-RETRY**: все задержки, single-retry старым
  токеном и 30с-окно сохранены; расширение только в сторону большего терпения (1130 → grace).
- **#GRACE-NO-CLEAR**: при исчерпании grace без токена — `null` («нет данных»), НЕ clear.
- **#SESSION-HOLD**: WebView-capture remixsid сохранён (инлайн в ту же корутину).
- **Sync без сети**: `refreshSessionCookiesFromCookieManager` — локальный CookieManager-read +
  patch; не добавляет сетевых запросов, не блокирует refreshMutex-семантику (внутри него нет
  рекурсии в ensureFreshToken).
- Скобочный сканер: VKA дельта **0** (HEAD {2626,2627} → раб {2626,2627}), SovaApp +1/+1,
  ExchangeAuthRepository +2/+2 — все сбалансированы; nested-comments ALL CLEAN (168).

---

## §5. Как проверить (юзеру)

1. `git pull` → `assembleDebug`.
2. Открыть приложение → переключить Wi-Fi ↔ мобильная сеть.
3. Ожидаемо: **никакого экрана авторизации**; возможен короткий NetworkSwitchPopup
   (если включён) или вовсе без видимых эффектов — лента продолжает работать.
4. Logcat-метки для диагностики:
   - `#NET-SWITCH-AUTH-FIX: cookie sync …` — sync перед Path 1.5;
   - `ensureFreshToken: silent refresh via remixsid OK` — Path 1.5 успех на новом IP;
   - `grace period silent refresh attempt N/3` — grace-цикл (если VK ещё держит binding);
   - `НЕ чистим токен и НЕ запускаем AuthActivity` — защита от преждевременного re-login.
5. Негативный контроль (настоящая смерть сессии: logout_hash инвалидация на сервере) —
   должен по-прежнему приводить к честному экрану входа.

---

## §6. Остатки / сознательно не тронуто

- `call()` no-token путь (~:10210): там нет error-контекста (нечего брать subcode), критерий
  `recentlySwitched` оставлен как был.
- Дебаунс `clearRemixsid` при «definitively dead» НЕ добавлен: с sync-до-Path-1.5 (§3.1)
  отвержение уже относится к свежайшему куки — доверие контракту Fix #49 важнее.
- FULL-mode после MAX_SILENT_FAILURES (SovaApp) — не изменялся: это честный фолбэк, теперь
  до него практически не доходит при живых куки.
