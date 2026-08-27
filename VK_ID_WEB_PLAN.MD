# План внедрения VK ID Web Account API в re.pinok (PinoK)

> **Источник:** анализ архива `VK ID_веб.zip` (см. `VK_IMPORT_API.MD` §49, строки 17859-18457)
> **Дата:** 2026-08-04
> **Цель:** внедрить недостающие элементы VK ID account page (сессии, устройства, 2FA, passkey, SafetyNet, email) в PinoK для усиления авторизации и поддержки пользователей

---

## 1. Карта содержимого архива (краткая сводка)

### 1.1. Файлы (12 шт.)

| Файл | Размер | Тип |
|------|--------|-----|
| `VK ID_веб.html` | 692K | SSR-снимок `id.vk.ru/account/#/main` |
| `VK ID_веб.txt` | 798B | Дамп localStorage (14 ключей) |
| `account.bundle.86a79b86956f3bf5c39b.js` | 2.2M | Главный app-чанк (VKUI Passport) |
| `account.vendors.8601ca645531eb61f445.js` | 235K | Vendor libs |
| `account.sentry.b67dbd9b8f1ba93cff94.js` | 145K | Sentry |
| `account.polyfills.*.js` | 7.5K | Polyfills |
| `account.runtime.*.js` | 6K | Webpack runtime |
| `banner_label.svg`, `large_OnePass.svg`, `large_community.svg` | 2-16K | SVG-иконки |
| `vk_dating_banner_desktop.png` | 527K | Promo-баннер |
| `VQVUR1z...jpg` | 23K | Promo-изображение |

### 1.2. Языки и технологии

| Язык | Где | Назначение |
|------|-----|-----------|
| **HTML5** | `VK ID_веб.html` | SSR-разметка (2874 строк) |
| **JavaScript (ES2020+, webpack)** | 5 бандлов, 2.6MB | SPA-логика (React + VKUI) |
| **CSS (inline + CSS Modules)** | inline `<style>` blocks | VKUI theme tokens (243 переменных), 101 component prefix |
| **SVG** | 3 файла | Иконки |
| **PNG/JPG** | 2 файла | Promo-изображения |
| **JSON** | в localStorage + в JS-объектах | Конфиг, переводы, данные |

### 1.3. Фреймворки и библиотеки

- **React** (VKUI-based SPA)
- **VKUI v5** (VK UI kit, `vkit-*` hashed classes)
- **VK Sans Display** font (30 woff/woff2 файлов)
- **Sentry** (error tracking, DSN в sentry-bundle)
- **Top Mail.ru counter** (analytics)
- **webpack 5** (chunk loading, lazy routes)

---

## 2. Карта API (97 методов, по namespace)

| Namespace | Кол-во | Назначение | Auth relevance |
|-----------|--------|-----------|----------------|
| `accountPersonal.*` | 43 | Управление аккаунтом (профиль, сессии, devices, email, SafetyNet) | ✅✅✅ КРИТИЧНО |
| `settings.*` | 24 | Security (2FA, passkey, password, phone) | ✅✅✅ КРИТИЧНО |
| `cua.*` | 11 | Confirm User Action (multi-step verification) | ✅✅✅ КРИТИЧНО |
| `vkProtect.*` | 8 | Account recovery appeals (с фото) | ✅ |
| `multiaccount.*` | 8 | Детские аккаунты | — |
| `accountVerification.*` | 3 | Bank/ESIA верификация | — |
| Прочие | 7 | `auth.getAppScopes`, `internal.getCountersInfo`, `stats.*`, `users.get`, `photos.*`, `esia.*`, `tinkoff.*` | ✅ |

**Полная таблица методов с параметрами и response-shape** — в `VK_IMPORT_API.MD` §49.2.

---

## 3. Карта меню / подменю / кнопок

### 3.1. Hash-роуты (31 роут)

| Группа | Роуты |
|--------|-------|
| **Профиль** | `#/main`, `#/personal`, `#/email-change`, `#/notify-email-change`, `#/change-phone` |
| **Безопасность** | `#/security`, `#/password-change`, `#/otp-settings`, `#/otp`, `#/passkey`, `#/codes`, `#/codegen-setup` |
| **Сессии/устройства** | `#/devices`, `#/sessions`, `#/activity-history` |
| **Уведомления** | `#/notifications` (история, НЕ настройки push) |
| **Сервисы** | `#/services`, `#/subs`, `#/verifications` |
| **Семья** | `#/family`, `#/create-password`, `#/create-password-success` |
| **Прочее** | `#/lite`, `#/simple`, `#/blocked`, `#/key`, `#/installed`, `#/my-cards`, `#/operations-history`, `#/bind-card`, `#/research`, `#/accident-research` |

### 3.2. Sidebar (левое меню, 34 data-test-id)

`header_back`, `header_close`, `header_help`, `header_notifications_settings`, `sidebar_item_main`, `sidebar_item_personal`, `sidebar_item_security`, `sidebar_item_devices`, `sidebar_item_sessions`, `sidebar_item_notifications`, `sidebar_item_verifications`, `sidebar_item_services`, `sidebar_item_subs`, `sidebar_item_family`, `account_info_avatar`, `account_info_name`, `account_info_phone`, `account_info_email`, `account_info_status`, `account_info_edit`, `security_card`, `security_card_password`, `security_card_2fa`, `security_card_passkey`, `security_card_safetynet`, `device_item`, `device_item_name`, `device_item_app`, `device_item_location`, `device_item_last_activity`, `device_item_ip`, `device_item_current`, `device_item_terminate`, `recommendation_item`, `popular_action`.

### 3.3. Ключевые кнопки и их API

| Кнопка | API | Категория |
|--------|-----|-----------|
| «Завершить все сессии» | `resetAllSessions` + `cua` | Auth support |
| «Завершить» (device) | `resetSessions` + `cua` | Auth support |
| «Сменить пароль» | `changePasswordStart` → `doChangePassword` | Security |
| «Включить 2FA» | `enableOTPByApp` → `getOTPbyAppSecret` (QR) | Security |
| «Отключить 2FA» | `disableOTPByApp` + `cua` | Security |
| «Добавить passkey» | `webauthnRegisterBegin` → `webauthnRegisterFinish` | Security |
| «Удалить passkey» | `webauthnRemoveCredential` | Security |
| SafetyNet toggle | `setSafetyNetEnabled` | **Notifications** |
| «Сменить email» | `startChangeEmail` → `changeEmail` | Notifications |
| «Сменить notify-email» | `startChangeNotifyEmail` → `changeNotifyEmail` | **Notifications** |
| «Сменить телефон» | `changePhone` → `changePhoneForce`/`changePhoneCancel` | Auth |
| «Выйти» | `r.M7("web_token")` (localStorage cleanup) | Auth |
| «Отключить OAuth» | `deactivateExternalOAuthService` | Security |
| «Удалить app-password» | `deleteAppPassword` | Security |
| «Резервные коды» | `getReserveCodes` | Security |
| «Recovery appeal» | `vkProtect.appeals` + `attachPhotoToAppeal` | Recovery |

---

## 4. Пробелы в разделе «Уведомления» (§10, §31, §42) — что дополнено

> VK ID account page **НЕ управляет push-уведомлениями напрямую**. Но управляет **сопутствующими** функциями, которые в существующей документации упомянуты вскользь или отсутствуют.

| Пробел | Где в §49 | Связь с уведомлениями |
|--------|-----------|----------------------|
| SafetyNet — alerts о подозрительных входах | §49.5.1 | Прямая: источник «security-пушей» |
| Email для уведомлений (адрес, не частота) | §49.5.2 | Прямая: куда слать email-уведомления |
| `connect_code_auth` flow semantics | §49.5.3 | Косвенная: токен для polling уведомлений |
| `web_token` localStorage key format | §49.5.4 | Прямая: §10.5.5 headless re-login |
| `PromoShowed_*` keys | §49.5.5 | Нет связи (информационно) |
| `deviceId` / `tracer-device-id` / `unauthId` форматы | §49.5.6 | Косвенная: device fingerprint |
| app_id `7344294` (VK ID account) | §49.1.3 | Косвенная: кто выдаёт web_token |
| `connect_exchange_token` (Path 5 в ensureFreshToken) | §49.6 Sprint VK-ID-6 | Прямая: меньше «ошибка авторизации» = меньше пропущенных пушей |

**Дополнения внесены в `VK_IMPORT_API.MD` §49 (строки 17859-18457, +602 строки).**

---

## 5. План работ (6 спринтов, ~7 недель)

### Sprint VK-ID-1: Auth Support APIs (P0, 1 неделя)

**Цель:** управление сессиями/устройствами из приложения.

| # | Задача | API | Файлы | Сложность |
|---|--------|-----|-------|-----------|
| 1.1 | `VkAccountApi` — обёртка | `getMainData`, `getActivityHistoryDevices`, `resetSessions`, `resetAllSessions`, `getSessionInfoForReset` | `data/api/VkAccountApi.kt`, `data/api/VkAccountModels.kt` | Средняя |
| 1.2 | `DevicesScreen` — список сессий | `getActivityHistoryDevices` | `ui/screens/devices/DevicesScreen.kt`, `DeviceItem.kt` | Средняя |
| 1.3 | «Завершить сессию» (single) | `resetSessions` + `cua` | inline | Высокая |
| 1.4 | «Завершить все» | `resetAllSessions` + `cua` | inline | Высокая |
| 1.5 | `cua` verification framework | `getValidationMethods`, `send*Code`, `check*Code` | `data/api/CuaApi.kt`, `ui/screens/cua/CuaVerifyScreen.kt` | Высокая |
| 1.6 | Deep-link из push → `DevicesScreen` | — | `VkUrlDeepLinker` | Низкая |

**Критерий приёмки:** пользователь видит список своих сессий, может завершить любую (с подтверждением через push/SMS).

### Sprint VK-ID-2: Security & 2FA (P1, 1 неделя)

| # | Задача | API | Файлы |
|---|--------|-----|-------|
| 2.1 | `SecurityScreen` — dashboard | `getSecurityInfo` | `ui/screens/security/SecurityScreen.kt` |
| 2.2 | Смена пароля (3-step) | `changePasswordStart` → `doChangePassword` | `PasswordChangeScreen.kt` |
| 2.3 | 2FA TOTP включение | `enableOTPByApp`, `getOTPbyAppSecret` (QR), `getReserveCodes` | `OtpSetupScreen.kt` |
| 2.4 | 2FA отключение | `disableOTPByApp` + `cua` | inline |
| 2.5 | Резервные коды | `getReserveCodes` | `ReserveCodesScreen.kt` |
| 2.6 | App passwords | `getAppPasswords`, `deleteAppPassword` | `AppPasswordsScreen.kt` |

**Критерий приёмки:** пользователь может включить/выключить 2FA, получить резервные коды, управлять app-passwords — всё без браузера.

### Sprint VK-ID-3: Passkey / WebAuthn (P1, 2 недели)

| # | Задача | API | Файлы |
|---|--------|-----|-------|
| 3.1 | Passkey регистрация | `webauthnRegisterBegin`, `navigator.credentials.create()` (Play Credential Manager API), `webauthnRegisterFinish` | `data/api/PasskeyApi.kt`, `ui/screens/security/PasskeySetupScreen.kt` |
| 3.2 | Passkey список/удаление | `getSecurityInfo`, `webauthnRemoveCredential` | inline |
| 3.3 | Passkey login (Path 6 в ensureFreshToken) | — | `auth/PasskeyAuthRepository.kt` |

**Критерий приёмки:** пользователь может создать passkey, войти с ним в следующий раз без пароля.

### Sprint VK-ID-4: SafetyNet + Security Alerts (P1, 1 неделя) — ПРЯМО relates to notifications

| # | Задача | API | Файлы |
|---|--------|-----|-------|
| 4.1 | `SovaPrefs.pushSafetyNetAlerts: Boolean = true` | — | `SovaPrefs.kt` Snapshot + DataStore |
| 4.2 | `SecurityAlertsPoller` — poll каждые 10 мин | `getSecurityAlerts` | `notifications/SecurityAlertsPoller.kt` |
| 4.3 | `SecurityAlertNotifier` — системное уведомление | — | `notifications/SecurityAlertNotifier.kt` |
| 4.4 | Channel `vk_security_alerts` (IMPORTANCE_HIGH) | — | `VkNotificationsNotifier.kt` (дополнить §42) |
| 4.5 | Toggle в Settings → «Уведомления о входе» | `setSafetyNetEnabled` | `SettingsScreen.kt` |
| 4.6 | Deep-link из push → `DevicesScreen` | — | `VkUrlDeepLinker` |
| 4.7 | Action button «Завершить сессию» в notification | `resetSessions` via `cua` | `SecurityAlertNotifier` + `RemoteAction` |

**Критерий приёмки:** при входе с нового устройства юзер получает системное уведомление в PinoK (а не только в официальном VK app), может завершить сессию одной кнопкой.

### Sprint VK-ID-5: Email & Phone (P2, 1 неделя)

| # | Задача | API | Файлы |
|---|--------|-----|-------|
| 5.1 | Смена notify-email | `startChangeNotifyEmail`, `changeNotifyEmail` | `NotifyEmailChangeScreen.kt` |
| 5.2 | Смена основного email | `startChangeEmail`, `changeEmail` | `EmailChangeScreen.kt` |
| 5.3 | Смена телефона (3-step) | `changePhone`, `changePhoneForce`, `changePhoneCancel` | `PhoneChangeScreen.kt` |
| 5.4 | `SovaPrefs.notifyEmailAddress: String?` | — | `SovaPrefs.kt` |

**Критерий приёмки:** пользователь может сменить email/phone из приложения без браузера.

### Sprint VK-ID-6: connect_exchange_token (Path 5) (P2, 3 дня)

| # | Задача | API | Файлы |
|---|--------|-----|-------|
| 6.1 | `ConnectExchangeTokenRepository` — Path 5 в `ensureFreshToken` | POST `login.vk.com/?act=connect_exchange_token` с `{token, hash}` | `auth/ConnectExchangeTokenRepository.kt` |
| 6.2 | Multi-account storage | localStorage key `<app_id>:web_token:login:auth` | `ExchangeTokenStorage.kt` |
| 6.3 | Bridge event `VKWebAppAuthByExchangeToken` (WebView mode) | — | `auth/VkIdBridgeClient.kt` |

**Критерий приёмки:** при истечении web_token в фоне, если есть валидный токен для другого app_id (например VK ID QR 7934655), exchange даёт новый для PinoK (6287487) без повторного логина.

---

## 6. Как это поможет авторизации и её поддержке

### 6.1. Удалённое завершение сессий при взломе

| До | После Sprint VK-ID-1 |
|----|---------------------|
| Единственный способ «выйти везде» — сменить пароль через веб в браузере | Пользователь открывает `DevicesScreen` в PinoK, видит все сессии, одной кнопкой завершает подозрительные через `resetAllSessions` + `cua` verification (push/SMS) |

**Win:** поддержка пользователей с взломанными аккаунтами — пользователь сам решает проблему за 30 секунд, не обращаясь в support.

### 6.2. Token refresh при network switch — +1 path

| До (§43, §44) | После Sprint VK-ID-6 |
|---------------|---------------------|
| При переключении сети (WiFi→LTE) LongPoll получает error 5/1117, `ensureFreshToken` пробует 5 paths, иногда все падают → «ошибка авторизации» | Path 5 `connect_exchange_token`: если есть валидный токен для другого app_id (например, юзер логинился через VK ID QR 7934655), exchange его на токен для PinoK 6287487 без повторного логина |

**Win:** +1 path в `ensureFreshToken`, меньше «ошибка авторизации» при network switch. Особенно полезно для пользователей VK ID QR.

### 6.3. SafetyNet — proactive security alerts

| До | После Sprint VK-ID-4 |
|----|---------------------|
| VK шлёт email + push на официальный VK app. Если юзер использует PinoK — не получает security-alert-push, видит только email | `SecurityAlertsPoller` каждые 10 мин проверяет `accountPersonal.getSecurityAlerts()`. При новом алерте → heads-up notification (channel `vk_security_alerts`, IMPORTANCE_HIGH). Action button «Завершить сессию» → `resetSessions` через `cua` |

**Win:** proactive security — пользователь узнаёт о взломе за 10 минут (а не за день через email), может сразу завершить сессию.

### 6.4. 2FA / Passkey — меньше взломов

| До | После Sprint VK-ID-2 + VK-ID-3 |
|----|-------------------------------|
| Для включения 2FA юзер идёт на `id.vk.ru/account` в браузере. Многие пропускают | Полное управление 2FA (TOTP, SMS, passkey) из Sova. Onboarding-промпт «Включите 2FA за 30 секунд» → QR → TOTP app → резервные коды |

**Win:** больше юзеров с 2FA = меньше взломов = меньше обращений в support по «взломали аккаунт».

### 6.5. Token expires в фоне — меньше потерянных сообщений

| До (§10.5.5) | После Sprint VK-ID-6 |
|--------------|---------------------|
| `LongPollKeepAliveService` ловит `tokenInvalidationTicks`, вызывает `ensureFreshToken`. Если все 5 paths fail → `AuthActivity` silent mode, сообщения теряются | Path 5 `connect_exchange_token` добавляется в `ensureFreshToken`. Если есть любой валидный токен — exchange даёт новый для нужного app_id |

**Win:** меньше случаев «сообщения не приходят пока приложение закрыто» — главный pain-point пользователей мессенджер-модов.

### 6.6. deviceId в web vs android — нет путаницы

| До (§41.11) | После §49.5.6 документации |
|-------------|----------------------------|
| `device_id` в OAuth URL = `android-<uuid>`. Но VK ID account API ожидает web-формат `87v-we10y1_...`. Путаница → баги | Чётко разделено: `android-<uuid>` для OAuth, web-format для VK ID account API. При вызове `accountPersonal.resetSessions` передаём web deviceId |

**Win:** нет бага «не могу завершить сессию, потому что device_id в неправильном формате».

---

## 7. Риски и митигации

| # | Риск | Митигация |
|---|------|-----------|
| 1 | `accountPersonal.*` требует web-token (от `login.vk.com/?act=connect&app_id=7344294`), не обычный access_token от `oauth.vk.com` | Использовать `connect_exchange_token` (Path 5) для exchange PinoK-token → VK-ID-account-token. Либо тестировать, принимает ли метод обычный access_token |
| 2 | `cua` verification может требовать VK ID app на устройстве (для push-verification) | Fallback на SMS (`cua.sendPhoneCode`). UI: «Введите код из SMS» |
| 3 | Passkey (WebAuthn) в Android требует `Play Credential Manager API` (androidx.credentials) | Добавить зависимость в build.gradle.kts. Min SDK 23 (уже есть) |
| 4 | `resetAllSessions` завершит и текущую сессию PinoK | Передавать `exclude_device_id: <current>`. UI: warning «вы будете разлогинены и в PinoK» |
| 5 | SafetyNet alerts могут прийти ДО poll-цикла (10 мин delay) | После login (success auth) сразу дернуть `getSecurityAlerts` один раз. Poll — для фонового режима |
| 6 | `connect_exchange_token` может вернуть empty array если нет связанных app_id | Обрабатывать как «Path 5 failed, try next» (Path 6 passkey или fallback to AuthActivity) |
| 7 | `PromoShowed_*` и `landings:unauthId` НЕ связаны с auth — не внедрять | Документированы в §49.5.5-49.5.6 для полноты, в код не добавляются |
| 8 | Sentry DSN и analytics endpoint могут слать telemetry, противоречащую `privacyAntiTelemetry` | НЕ внедрять Sentry/analytics из VK ID web в PinoK. Только API методы |

---

## 8. Приоритеты и очередность

| Приоритет | Спринт | Длительность | Зависимости |
|-----------|--------|--------------|-------------|
| **P0** | VK-ID-1 (Auth Support) | 1 неделя | нет |
| **P0** | VK-ID-6 (connect_exchange_token) | 3 дня | нет (параллельно с VK-ID-1) |
| **P1** | VK-ID-4 (SafetyNet alerts) | 1 неделя | VK-ID-1 (для «Завершить сессию» action) |
| **P1** | VK-ID-2 (2FA) | 1 неделя | VK-ID-1 (для `cua` framework) |
| **P1** | VK-ID-3 (Passkey) | 2 недели | VK-ID-2 |
| **P2** | VK-ID-5 (Email/Phone) | 1 неделя | VK-ID-1 |

**Total:** ~7 недель (если VK-ID-1 и VK-ID-6 параллельно, остальные последовательно).

**Quick wins (3 дня):**
1. VK-ID-6 (Path 5) — сразу меньше «ошибка авторизации» при network switch.
2. §49.5.4 (web_token localStorage key format) — документация для следующего fix'а auth-loop.

---

## 9. Связанные секции в VK_IMPORT_API.MD (update references)

| Секция | Что дополнено | Где в §49 |
|--------|---------------|-----------|
| §10.5 (Push) | SafetyNet alerts, web_token localStorage format | §49.5.1, §49.5.4 |
| §31.8 (Notify settings) | notify-email management (адрес, не частота) | §49.5.2 |
| §41.13 (silent_token flow) | connect_code_auth bridge flow, 3 transports | §49.5.3 |
| §41.14 (app_id реестр) | app_id `7344294` (VK ID account), `51925014` (Mail Wallet) | §49.1.3 |
| §41.20 (ensureFreshToken paths) | Path 5 `connect_exchange_token` | §49.6 Sprint VK-ID-6 |
| §42 (Push channels) | channel `vk_security_alerts` | §49.6 Sprint VK-ID-4 |
| §44.G (dual-token storage) | формат `<app_id>:web_token:login:auth` | §49.5.4 |

---

## 10. Статус

- ✅ Анализ архива `VK ID_веб.zip` завершён (HTML + JS + localStorage)
- ✅ Карта содержимого построена (§49.1)
- ✅ Карта API построена — 97 методов (§49.2)
- ✅ Карта меню/подменю/кнопок построена — 31 роут, 34 data-test-id (§49.3)
- ✅ CSS/Styling проанализирован — 243 VKUI-токена, 101 component prefix (§49.4)
- ✅ Пробелы в разделе «Уведомления» выявлены и дополнены в §49.5
- ✅ План работ (6 спринтов, ~7 недель) составлен (§49.6)
- ✅ Как находки помогут авторизации — 6 сценариев (§49.7)
- ✅ Риски и митигации описаны (§49.8)
- ⏳ Реализация — НЕ начата (этот документ = план, не код)

**Следующий шаг:** начать с Sprint VK-ID-6 (Path 5 connect_exchange_token, 3 дня) — quick win для auth-loop fix.
