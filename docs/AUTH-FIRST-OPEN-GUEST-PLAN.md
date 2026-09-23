# AUTH-FIRST-OPEN-GUEST — план переделки первого открытия

**Дата:** 2026-09-23
**Статус:** план (не реализовано)
**Тег:** #AUTH-FIRST-OPEN-GUEST

## 1. Требование

При первом открытии приложения (нет валидного токена) **не** показывать автоматически
экран входа. Приложение сразу открывается в guest-режиме. Вход в аккаунт — только
по кнопке **«Войти в аккаунт»**, закреплённой в боковой панели (drawer, фиксированный
хвост — не редактируется пользователем, как «Офлайн → Настройки → Выйти»).

## 2. Уточнение по коду: кнопка вызывает AuthActivity, не LandingScreen

Исследование кода (2026-09-23):

- `LandingScreen` (`auth/LandingScreen.kt`, `internal fun`) — **не** самостоятельный
  экран: он рисуется только внутри `AuthActivity` как фаза `AuthPhase.LANDING`
  (private enum: `LANDING → WEBVIEW → TWO_FA → SUCCESS`, `AuthActivity.kt:535`).
- Показать `LandingScreen` напрямую из drawer MainActivity нельзя без дублирования
  всей логики AuthActivity (result-контракт, silent-режим, вставка токена из
  внешнего браузера, оффлайн-кнопка, guard'ы #SSO-RECREATE-GUARD, throttle).
- **Решение:** кнопка drawer запускает `AuthActivity` через единую точку
  `launchAuth(intent, reason = "drawer-login")` → юзер видит `LandingScreen`
  внутри AuthActivity. Вся существующая обработка результата
  (`RESULT_OK` / `RESULT_OFFLINE_MODE`) остаётся без изменений.

## 3. Текущее поведение (что меняем)

| Путь | Сейчас | Стало |
|---|---|---|
| Boot, токена нет (`boot-no-token`, MainActivity ~L857) | Сразу `launchAuth` (silent если есть remixsid, иначе FULL → юзер видит Landing) | Есть remixsid → **silent auto** (не виден, остаётся). Нет remixsid / silent исчерпан → **guest-режим**, без AuthActivity |
| Сеть вернулась без токена (`network-restored-no-token`, ~L1117) | `launchAuth` (FULL если нет remixsid) | Только silent; иначе остаёмся в guest |
| Token invalidated tick (~L1015) | silent или FULL | Только silent; исчерпан → guest (FULL-запуск убран) |
| Guest-режим (`isOfflineMode`, ~L1341) | `OfflineManagerScreen` **без drawer**; «Назад» → relaunch AuthActivity | `OfflineManagerScreen` + **guest-drawer** с фиксированной кнопкой «Войти в аккаунт»; «Назад» больше не запускает логин |
| `else -> StartupLoadingScreen()` (~L1436) | Промежуточный loading пока AuthActivity не показалась | Остается только на краткий миг до авто-перехода в guest |

## 4. Этапы переделки

### Этап 1 — MainActivity.kt (ядро)
1. **`boot-no-token`** (LaunchedEffect ~L834–907):
   - `hasRemixsid && silentFailCount < MAX_SILENT_FAILURES && !forceFullRelogin` →
     как сейчас, silent-запуск (изменений нет).
   - Иначе → **не** `launchAuth`, а: `isOfflineMode = true` (авто-guest),
     `lockerBootCheckDone = true`, лог `#AUTH-FIRST-OPEN-GUEST: no token, no
     remixsid → guest mode, login via drawer button`.
2. **`network-restored-no-token`** (~L1117): использовать silent-интент только если
   `hasRemixsid && silentFailCount < MAX`; иначе skip (guest продолжает работу).
3. **Token-invalidated tick** (~L1015): при `useSilent == false` — вместо FULL
   `launchAuth` → guest-режим (`isOfflineMode = true`).
4. **Guest onBack** (~L1348–1355): убрать `launchAuth("offline-back-to-login")`;
   «Назад» в guest-режиме — no-op (лог), вход только через drawer-кнопку.
5. Guest-режим при cold start без токена входит автоматически — флаг
   `RESULT_OFFLINE_MODE` продолжает работать как ручной вход в guest.

### Этап 2 — guest-drawer (новый композабл)
Новый файл `ui/navigation/GuestDrawer.kt`:
- `ModalNavigationDrawer` вокруг существующего `OfflineManagerScreen`-блока
  (`isOfflineMode ->` ветка MainActivity).
- Содержимое: header «PinoK» + кнопка сворачивания (стиль-паритет с SovaNavHost
  #247), middle — пункты guest-экрана (Офлайн — текущий), **фиксированный хвост**
  (Fix #337-стиль): `HorizontalDivider()` + `NavigationDrawerItem`:
  - label «Войти в аккаунт», icon `Icons.AutoMirrored.Filled.Login`;
  - onClick → закрыть drawer → `launchAuth(Intent(this, AuthActivity::class.java),
    reason = "drawer-login")`;
  - пункт фиксированный (не попадает в sidebar-редактор — он и так в MainActivity,
    вне SovaNavHost-редактора prefs).
- Адаптивная ширина drawer — переиспользовать подход #247.

### Этап 3 — SovaNavHost (авторизованный режим)
- Без изменений. Кнопка «Войти в аккаунт» в основной drawer НЕ добавляется —
  в авторизованном режиме она не имеет смысла; аналог — «Выйти из аккаунта».

### Этап 4 — AuthActivity
- Без изменений: фазы, result-контракт, «Офлайн-режим» на Landing работают как есть.

## 5. Guard'ы и риски

- **#SSO-RECREATE-GUARD / throttle 20с / окно 90с** — не трогаем: `launchAuth`
  остаётся единственной точкой запуска AuthActivity, drawer-кнопка идёт через неё.
- **Locker (#LOCKER-BOOT-SKIP)**: в авто-guest ветке выставлять
  `lockerBootCheckDone = true` (boot-решение принято без AuthActivity), иначе
  цикл перезапусков эффекта.
- **authVersion / bootLocal**: авто-guest не должен перезапускать boot-эффект
  бесконечно — переход в guest идёт через `isOfflineMode = true`, который уже
  гейтит эффект (`if (isOfflineMode) return@LaunchedEffect`, L853).
- **Существующие silent-пути** (Fix #339, #107, #SILENT-RETRY-AFTER-DOZE) —
  сохраняются полностью: invisible re-login по remixsid не показывается юзеру,
  это не «LandingScreen при первом открытии».
- **Race первого кадра**: до выполнения boot-эффекта ветка `else ->
  StartupLoadingScreen()` может мигнуть 1–2 кадра — допустимо (как сейчас).
- **Back-жест в guest**: убрать relaunch-логин; проверить `onBack`-контракт
  OfflineManagerScreen (параметр остаётся, логика меняется).

## 6. Документация (по факту реализации)

- `HISTORY.md` — запись 2026-09-23: первый запуск без токена → guest, вход —
  кнопка «Войти в аккаунт» в drawer.
- `worklog.md` — Task ID: 4.
- `EXECUTION_QUEUE.md` — блок C добавлен.
- Коммит-тег: `#AUTH-FIRST-OPEN-GUEST`.
