# Парсинг снапшота «VK ID»: 5 страниц id.vk.com + account.bundle.js (инвентарь и wire-каталог)

> Task ID: SNAP-VKID-PARSE-2 · RESEARCH (код/git не тронут, коммита нет). Перезапуск после таймаута первого агента.
> Источник: `upload/snapVK_ID/VK_ID/` — 5 HTML-страниц личного кабинета VK ID (~660–760 КБ каждая, 2913 строк, utf-8), бандлы в `*_files/` (account.bundle.521111fa85c2afe0f56a.js — 2 249 608 байт, + runtime/vendors/polyfills/sentry), `vk_куки_локалстрдж.txt` (**0 байт — пуст**, см. §5).
> Контекст: id.vk.com — **аккаунт-менеджмент VK ID** (профиль/безопасность/платежи/подписки/подключённые сервисы), НЕ профиль vk.com. Ядро PinoK уже имеет: настройки с секциями (`SettingsScreen.kt`), BFF-приватность (`PrivacySettingsScreen.kt`), полный logout по `logout_hash` (`ExchangeAuthRepository.kt`). Кросс-источник структуры cookies/localStorage: `vk.id.md` (3 стадии авторизации).
> Стиль-референс: `лента.снапшоты.парсинг.полный.md` (§0..§5).

---

## §0. Источники и метод

| # | Файл | title | saved-from (по charset/логике SPA) | Размер | Контент в DOM |
|---|---|---|---|---|---|
| 1 | `Мои данные — VK ID.html` | Мои данные — VK ID | id.vk.com/account → роут `/main` | 685 КБ | полный (секции данных) |
| 2 | `Безопасность — VK ID.html` | Безопасность — VK ID | роут `/security` | 656 КБ | полный (4 ячейки + OAuth) |
| 3 | `VK Pay_ карты и платежи — VK ID.html` | VK Pay: карты и платежи | роут `/vkpay` | 664 КБ | полный (карты/кошелёк/голоса) |
| 4 | `Подписки — VK ID.html` | Подписки — VK ID | роут `/subs` | 663 КБ | **пустой shell** — только шапка, контент рендерится клиентом |
| 5 | `Сервисы и сайты — VK ID.html` | Сервисы и сайты — VK ID | роут `/services` | 760 КБ | полный (20 подключённых сервисов) |

Метод (экономный, по окнам): python-вырезка текстов `<body>` со strip тегов + дедуп RU-строк; `rg -o` по `href`, `data-testid`, `data-test-id`; JS-бандл — только rg-паттерны (`method/`, `\bnamespace\.method\b`, `VKWebApp*`, пути `"/..."`, oauth-провайдеры, passkey/webauthn, vkpay-ключи), без полного чтения. Сверка с ядром: `rg 'call\("…"' api/VKApiClient.kt` + enum вкладок SettingsScreen + PrivacySettingsScreen/ExchangeAuthRepository.

**Ключевые факты о снапшоте**: (1) это SPA VKUI (`vkuiRootComponent__host`, `VKConnectPanelHeader`); (2) скелет страниц сервер-рендеред, но общие для всех страниц RU-тексты — навигация «Главная / Мои данные / Безопасность / VK Pay: карты и платежи / Подписки / Сервисы и сайты», футер «Конфиденциальность · Условия · Помощь», меню юзера «Перейти в Почта Mail / Выйти»; (3) «Подписки» в снапшоте пуста (клиентский рендер не пойман); (4) все действия — SPA-кнопки без href (модалки/переходы роутера), следы действий — `data-test-id` и события аналитики в бандле.

`data-test-id`-инвентарь (уникальные, все 5 страниц): `main-data`, `personal-data`, `security-data`, `vkpay-data`, `subs-data`, `services-data`, `panel-header`, `password-cell`, `security-page-devices-cell`, `providers-block`, `provider-icon-sber_id`, `protect-status-banner-block/-icon`, `header-avatar-dropdown`, `header-dropdown-avatar/-logout/-mail-ru/-phone`, `vkid-logo`. + классы-следы: `OAuthProviders`, `OAuthProvider`, `OAuthLinkedProviderModal` (×16), `OAuthBeforeIcon`, `OAuthVerifiedData`.

---

## §1. Инвентарь страниц

### 1.1. «Мои данные» (роут `/main`)

Секции сверху вниз (RU-тексты дословно):
- **Шапка профиля**: «Сергей Ширабоков» (аватар), «Перейти в Почта Mail» (→ `https://e.mail.ru/inbox?x-email=…`), «Выйти» (`data-test-id="header-dropdown-logout"`).
- **Анкета**: поля «Имя», «Фамилия», «Пол» (выбор «Мужской»/«Женский»), «Дата рождения» (значение в снапшоте обрезано — «Июн»), «Никнейм»; кнопка «Сохранить».
- **«Подтверждение данных аккаунта»** — статус «Не подтверждён» (бейдж).
- **«Контактные данные»**:
  - «Телефон» → кнопка «Изменить» (SPA, следы в бандле: `changePhone`, `changePhoneFx`, `changePhoneForce`, `changePhoneCancel`);
  - «Основная почта» — блок «ВКонтакте появилась почта / Выберите для себя подходящий адрес @vk.com» → «Создать почту» / «Не интересно»;
  - «Почта для уведомлений» («Для уведомлений от сервисов VK») → «Привязать» (след в бандле: `changeEmail`, `changeEmailFx`).
- **«Действия с аккаунтом»**: «Удаление аккаунта VK ID» — «Вы можете полностью удалить аккаунт VK ID, данные в нём и информацию о подключённых сервисах. Профиль ВКонтакте также будет удалён.» (тайды `deleteAccountBlock`, `deleteAccountGroupHeader`, иконка `delete_outline_`; wire удаления в бандле не обнаружено — переход на отдельный контур удаления).
- Служебное: «Оцените личный кабинет VK ID» («Нравится/Не нравится»), внешний трекер `https://bid.vk.com/profile?uid=…` (рекламный bid-профиль, не переносимо).

### 1.2. «Безопасность» (роут `/security`)

- Баннер-статус: «Аккаунт надёжно защищён / Ваш аккаунт и данные в нём максимально защищены» (`protect-status-banner-*`).
- **«Способы входа»** (список ячеек):
  - «Вход по скану лица или отпечатку пальца» — тоггл «Выкл.» (`data-test-id`-ряд с password-cell; wire в бандле: **WebAuthn/passkey** — `account.getPasskeyDevices`, `passkey_mode`, `passkeyDevicesCount`, `webauthnRegisterBegin/Finish`, `webauthnRemoveCredential`, `webauthnUpdateDevice`, `webauthn_only_auth_status`);
  - «Двухфакторная аутентификация» — тоггл «Вкл.» (следы `2fa` ×15);
  - «Пароль / Ваш пароль / Был изменён месяц назад» (`password-cell`; wire: `changePasswordStart/StartFx/Started`, `account.deletePassword`, роуты `/create-password`, `/create-password-success`);
  - «Устройства и активность / Список устройств и история активности» (`security-page-devices-cell`; аналитика `devicesAndActivityClicked`, `activityHistoryClicked`, `devicesPageEnteredFx` — сами данные сессий в снапшот не попали).
- **«Связанные аккаунты»** (`OAuthProviders` + `providers-block`): горизонтальная карусель привязанных внешних ID. В DOM виден провайдер **Sber ID** (`provider-icon-sber_id`, `logo_sber_28.svg`), модалка `OAuthLinkedProviderModal`. Полный реестр провайдеров из бандла: `"esia"` (Госуслуги), `"sber_id"`, `"vtb_id"`, `"mail_ru"`, `"ok"`, `"yandex"`; события `oauth_link`/`oauth_disabled`, фильтры `oauth_linked`/`oauth_verification`.
- Переходы/подсказки безопасности: FAQ-ссылки `id.vk.com/about/faq/users/account_problem/…`, `id.vk.com/about/client/security` (лейблы `vkui_passport_accident_res…` — «Сообщить о взломе» контур).

### 1.3. «VK Pay: карты и платежи» (роут `/vkpay`)

- **«Мои карты»** → «Добавить карту» (след в бандле только аналитический: `walletCardClicked`, `vkpay_wallet_status`).
- **«Создайте кошелёк VK Pay»**: «Виртуальный кошелёк для ваших денег, кешбэк 5% на все покупки от 100 рублей, переводы в один клик» → кнопка «Создать кошелёк» (`vkpay_create_wallet_go_to_miniapp` — **уводит в мини-апп**).
- «Бонусы», «История операций» («Платежи и переводы через сервис VK Pay»).
- **«Баланс голосов»**: «0 голосов» → «Пополнить».
- Промо: «Покупайте с выгодой через VK Pay / Получайте 5% кешбэка со своих онлайн-покупок» → «Подробнее».
- **«Дополнительно»**: «Написать в Поддержку VK Pay», «Юридическая информация».
- ВАЖНО: платёжных endpoint'ов в бандле **не обнаружено** — только bridge-события `VKWebAppOpenPayForm`, `VKWebAppOpenApp` и go_to_miniapp-аналитика. VK Pay — отдельный контур мини-приложения.

### 1.4. «Подписки» (роут `/subs`)

- Снапшот поймал **пустой shell**: шапка + только заголовок «Подписки» (тайд `subs-data`), аватар, реклама `max_banner_desktop`. RU-текстов контента (список подписок VK Donut/Music/Play/Video/Dating/Dzen — по набору иконок-заглушек в `*_files/`: `vk_donut_mini`, `vk_music_mini`, `vk_play_mini`, `vk_video_mini`, `vk_dating_mini`, `dzen_logo`) в DOM **не обнаружено** — рендер клиентский, в сохранёнку не попал. Ключи `subscriptionIcons`/`subscription` в бандле.
- Это единственная страница с неполным инвентарём (честное ограничение, см. §5).

### 1.5. «Сервисы и сайты» (роут `/services`)

- Блок «Рассказываем, что здесь изменилось».
- **«Сообщить о взломе»**: «Если подозреваете, что кто-то посторонний получил доступ к аккаунту, напишите нам» → `vk.ru/support?act=home_vkid`.
- **«Вход через VK ID»** — список сервисов/сайтов, которым выдан доступ (20 записей в DOM): ВКонтакте, VK Видео, Приложение VK Мессенджер, VK Бизнес ID, Ответы Mail: Автологин, Авторизация на форуме, «Кисвк» (Сайт), VK Почта, Юла, Дзен, VK Реклама, Авторизация Habr Account, Маруся, Авторизация анимерост, Клевер, ТЕРАБАЙТ МАРКЕТ, Аудиокниги Клуб, ГдеПосылка, Авторизация на AnimeVost.org (+служебные карточки с логотипами ok/mail: `ok_logo`, `mail_ru_logo` — карусель привязанных OAuth, дублирует §1.2).
- Действие над каждым сервисом (отключение доступа) — SPA-модалка (`OAuthLinkedProviderModal` ×16), wire отзыва доступа в бандле не обнаружено.
- Ссылки страницы: `ok.ru/offer/vkid/bind` (привязка OK), `id.vk.ru/privacy`, `id.vk.ru/terms`, `vk.ru/support?act=home_vkid`.

### 1.6. Общая структура (все 5 страниц)

```
vkuiRoot → VKConnectPanelHeader (vkid-logo, тайтл) → vkuiPanel
  ├── нав. меню: Главная / Мои данные / Безопасность / VK Pay: карты и платежи / Подписки / Сервисы и сайты
  ├── контент секции (§1.1–1.5)
  ├── «Вернуться ВКонтакте» → https://vk.ru/
  ├── футер: Конфиденциальность (id.vk.ru/privacy) · Условия (id.vk.ru/terms) · Помощь (vk.ru/support?act=home_vkid)
  └── меню юзера (header-avatar-dropdown): Почта Mail · Выйти
```
Роуты SPA из бандла: `/main`, `/services`, `/subs`, `/vkpay`, `/video`, `/lite`, `/family`, `/simple`, `/static`, `/blocked`, `/create-password`, `/create-password-success`, `/about/id`.

---

## §2. Wire-каталог (account.bundle.521111fa85c2afe0f56a.js, 2.2 MB, только rg)

### 2.1. VK API bridge (единственный «классический» транспорт)

Конструктор запросов в бандле: `domain:"api.vk.com", urlConstructor: "https://".concat(domain,"/method/",name,…)` + `fetch("https://…/method/"+method, {credentials:"include", method:"POST"})`. Т.е. VK ID-кабинет ходит тем же `/method/` с cookie-креденшалами.

Выписанные имена методов (`\bns\.method\b`, уникальные):

| Метод | Что делает в кабинете |
|---|---|
| `users.get` (×4) | профиль юзера в шапке |
| `account.getProfileInfo` | анкета «Мои данные» (имя/фамилия/пол/дата/ник) |
| `account.saveProfileInfo` | «Сохранить» анкеты |
| `account.getToggles` / `account.getTogglesExternal` | фича-флаги кабинета (external — для анонима) |
| `account.getMulti` | мультиаккаунты (переключение юзера) |
| `account.getPasskeyDevices` | список passkey-устройств (вход по лицу/отпечатку) |
| `account.deletePassword` | удаление пароля |
| `account.getHelpHints` / `account.hideHelpHint` | подсказки/скрытие подсказок |
| `account.get` | общий account.get |
| `photos.getOwnerPhotoUploadServer` / `photos.saveOwnerPhoto` / `photos.delete` | смена аватара |
| `stats.trackVisitor` / `stats.trackEvents` | телеметрия кабинета (не переносимо) |

### 2.2. Домены и пути

- Домены (конфиг VK Connect): `loginHost: "login.vk.com"`, `oauthDomain: "oauth.vk.com"`, `connectDomain: "id.vk.com"`; whitelist хостов: `vk.ru, vk.com, id.vk.com, id.vk.ru, m.vk.ru, m.vk.com, ok.ru` (+`login.vk.com` ×3, `oauth.vk.com` ×2, `api.vk.com`).
- Пути: `/api/perf/upload`, `api/crash/track`, `api/crash/upload` (телеметрия), `captcha.php`, `away.php`.
- OAuth-flow: `oauth` ×111 (VKWebAppGetAuthToken, `VKWebAppAuthByExchangeToken(Success/Result/Failed)` — exchange-token согласен с ядром, см. §3.4).

### 2.3. Состояния/события (не endpoint, но wire-значимые)

- Passkey/WebAuthn: `webauthnRegisterBegin`, `webauthnRegisterFinish`, `webauthnRemoveCredential`, `webauthnUpdateDevice`, `passkeyOnlyConnect/Disconnect`, `webauthnOnlyAuthStatus`, `passkey_devices_count`.
- Сессии/выход: `logout_hash` (в потоке `onAccessTokenReceived{access_token,user_id,logout_hash,expires}`), `logout` ×8, `session_id`, `sessionUuid`, `sessionLiteFlow/sessionFullFlow`, `session_reset_enabled/disabled`.
- Смена контактов: `changeEmail*`, `changePhone*` (Fx/Triggered/Field/Force/Cancel), `changePasswordStart/Started`.
- VK Pay: только аналитика `vkpay_wallet_status`, `vkpay_wallet_balance_go_to_miniapp`, `vkpay_create_wallet_go_to_miniapp`, `walletCardClicked` + bridge `VKWebAppOpenPayForm`. Платёжных API нет.
- Провайдеры связанных аккаунтов: `"esia"`, `"sber_id"`, `"vtb_id"`, `"mail_ru"`, `"ok"`, `"yandex"`; `oauth_linked`, `oauth_verification`, `oauth_name`, `oauth_disabled`.
- localStorage-ключи VK ID из бандла: `vkid_auth_by_autologin_timestamp`, `vkid-redirect`, `sessionViewLs`; тюнинг-ключи `vkid_account_*` (баннеры/исследования: `vkid_account_mail_wallet_widget`, `vkid_account_new_integration_with_security_landing`, `vkid_account_max_safe_pass_banner`, …).

### 2.4. Реклама/телеметрия (не переносимо)

`bid.vk.com/profile` (bid-профиль), `trk.mail.ru`, `stats.trackVisitor/trackEvents`, `/api/perf/upload`, `/api/crash/*`, sentry-бандл `account.sentry.*`, промо-баннеры `max_banner_desktop`.

---

## §3. GAP-матрица (снапшот ↔ ядро PinoK)

Ядро: `api/VKApiClient.kt`, `ui/screens/settings/{SettingsScreen, PrivacySettingsScreen, BlacklistScreen, LogScreen}`, `auth/exchange/*`. Статусы: ✅ закрыто ядром (файл:строка) · ❗ gap с известным wire · ⚠️ wire отсутствует/отдельный контур.

### 3.1. Аккаунт-данные (страница «Мои данные»)

| Элемент снапшота | Статус | Привязка / комментарий |
|---|---|---|
| Анкета: имя/фамилия/пол/дата/ник | ✅ | `account.getProfileInfo` VKA:16037, `account.saveProfileInfo` VKA:16102; UI-полей в ядре нет — но wire полный |
| Кнопка «Сохранить» анкеты | ✅ | VKA:16102 (params уже собраны) |
| Смена аватара | ⚠️ | В бандле `photos.getOwnerPhotoUploadServer/saveOwnerPhoto/delete` — в ядре **не обнаружено** (call-строк нет); загрузка файла из натив потребует upload-сервера (реализуемо) |
| Телефон → «Изменить» | ⚠️ | Wire в бандле только состояния UI (`changePhone*`), самого метода смены телефона нет; в ядре не обнаружено. Контур SMS-верификации VK ID |
| Создание почты @vk.com / «Почта для уведомлений» → «Привязать» | ⚠️ | Mail.ru-контур (`e.mail.ru`, `mail_ru` провайдер); в ядре не обнаружено, отдельный сервис |
| «Подтверждение данных аккаунта» (паспорт/ESIA) | ⚠️ | Провайдер `"esia"` в бандле есть, endpoint верификации не обнаружен; отдельный контур |
| «Удаление аккаунта VK ID» | ⚠️ | Тайды есть, wire не обнаружен (уводит на отдельную страницу удаления); в ядре не обнаружено |
| Смена email | ❗ | `changeEmail`-состояния + «Почта для уведомлений» — UI-поток в бандле; API-метод в снапшоте не снят (SPA-модалка) |
| Выход («Выйти» в меню юзера) | ✅ | logout_hash-поток бандла = ядро `ExchangeAuthRepository.kt:1643` (`login.vk.com/?act=logout&logout_hash=…`, Fix #182 полный logout) |

### 3.2. Безопасность

| Элемент снапшота | Статус | Привязка / комментарий |
|---|---|---|
| Баннер «Аккаунт надёжно защищён» | ✅ (декор) | статический статус; дублировать не обязательно |
| Двухфакторная аутентификация (тоггл «Вкл.») | ❗ | В бандле только флаг `2fa`; API-тоггла в снапшоте нет (secure.* в бандле отсутствуют, в ядре `secure.` = 0 совпадений). Нативный экран возможен поверх `account.getProfileInfo`-стиля, но метод установки 2FA не снят |
| Вход по лицу/отпечатку (passkey) | ❗ | Wire известен: `account.getPasskeyDevices` (бандл) + `webauthnRegister*` (WebAuthn). В ядре не обнаружено; WebAuthn из Android требует Credential Manager — средний по сложности нативный кандидат |
| Пароль: «Был изменён…», смена/создание | ❗ | `changePasswordStart/Started` + роуты `/create-password` + `account.deletePassword` (бандл); в ядре не обнаружено. Смена пароля идёт через отдельный VK ID-флоу (верификация) |
| «Устройства и активность» (сессии) | ❗ | Тайд `security-page-devices-cell`, аналитика кликов есть; сами данные/метод списка сессий в снапшоте НЕ сняты (клиентский рендер). В ядре экрана сессий нет. Топ-кандидат на натив-экран, но wire придётся добывать отдельно |
| Связанные аккаунты (OAuth-провайдеры) | ❗ | Бандл: реестр провайдеров esia/sber_id/vtb_id/mail_ru/ok/yandex, `oauth_linked`. В ядре привязок нет; «Привязать OK» → `ok.ru/offer/vkid/bind` — внешний web |
| FAQ/«Сообщить о взломе» | ✅ (декор) | ссылки `vk.ru/support?act=home_vkid`, `id.vk.com/about/…` — web-only |

### 3.3. VK Pay / Подписки / Сервисы

| Элемент снапшота | Статус | Привязка / комментарий |
|---|---|---|
| VK Pay: карты, кошелёк, история операций | ⚠️ | Отдельный контур мини-аппа: `VKWebAppOpenPayForm`, `*_go_to_miniapp`; платёжных endpoint'ов в бандле нет, в ядре не обнаружено. Честный отказ — см. §4 |
| Баланс голосов + «Пополнить» | ⚠️ | Тот же VK Pay/платёжный контур; wire не обнаружен |
| Подписки (страница `/subs`) | ⚠️ | Снапшот пустой (§1.4); подписки на сервисы VK — биллинг-контур, wire не обнаружен |
| «Вход через VK ID» (список 20 сервисов) | ❗ | Аналог `apps.getUsersConnected` (классический метод списка приложений с доступом) — в бандле **не обнаружен** (только UI-список из DOM); в ядре `apps.*` есть только allow/deny/readAllNotifications (VKA:14781/14789/14797) — это push-уведомления приложений, семантика другая. Отзыв доступа к сервису — SPA-модалка, wire не снят |
| Отключение сервиса (модалка `OAuthLinkedProviderModal`) | ❗ | Классический кандидат `secure.appAuthRevoke`/`account.???` — в снапшоте отсутствует; ядро не имеет |

### 3.4. Авторизация/инфраструктура (контекстная сверка)

| Элемент снапшота | Статус | Привязка / комментарий |
|---|---|---|
| `VKWebAppAuthByExchangeToken(Success/Result/Failed)` | ✅ | Exchange-token flow — ядро `auth/exchange/*` (ExchangeAuthRepository/WebTokenAuth/AuthDomainsConfig) построено ровно на этом контуре |
| Домены login.vk.com/oauth.vk.com/id.vk.com | ✅ | `auth/exchange/AuthDomainsConfig.kt` (вход из VK ID-кабинета уже реализован) |
| `account.getToggles` | ✅ | VKA:12936 (Fix #267) |
| `account.getTogglesExternal` | ❗ | Внешняя (доавторизная) версия — в ядре не обнаружено; нужна только для пре-логина |
| `account.getMulti` (мультиаккаунты) | ❗ | Переключение аккаунтов — в ядре не обнаружено; wire известен |
| `account.getHelpHints`/`hideHelpHint` | ⚠️ | Подсказки кабинета — продукт VK ID, ценность для PinoK низкая |
| Телеметрия (stats.*, /api/perf, /api/crash, sentry) | ⚠️ | Не переносимо (анти-паттерн для PinoK, см. «Сеть»-настройки) |

**Итог §3**: ✅ 7 · ❗ 9 · ⚠️ 9 (всего 25 позиций).

---

## §4. Рекомендации волне внедрения

1. **Экран «Безопасность» (native-first, ценность макс.)** — секция в существующие настройки (`SettingsScreen` уже имеет вкладки PRIVACY/SECURITY — добавить «Аккаунт VK»): ячейки «Двухфакторная аутентификация (статус)», «Пароль», «Устройства и активность», «Связанные аккаунты». Реально закрываемое ядром+снапшотом: статус-агрегат. Список сессий — главный ❗-gap: wire списка сессий в снапшоте не снят → добывать отдельным срезом (id.vk.com/…devices SPA-запрос) до внедрения. No-stub: без wire ячейку не рисовать.
2. **Экран «Сервисы и сайты» («Вход через VK ID»)** — список подключённых сервисов + отзыв доступа. Классический кандидат `apps.getUsersConnected`/`secure.*` — в снапшоте отсутствует, поэтому внедрение только после добычи wire (этап парсинга OAuth-запросов при открытии модалки). У ядерного `apps.allowNotifications/denyNotifications` (VKA:14781) семантика другая — не подмешивать.
3. **«Мои данные»** — анкета (имя/фамилия/пол/ник) полностью ложится на уже внедрённые `account.getProfileInfo`/`saveProfileInfo` (VKA:16037/16102): нативная форма поверх — дешёвый экран, можно совместить с текущим редактированием профиля. Смена аватара — добавить `photos.getOwnerPhotoUploadServer`+`saveOwnerPhoto` (wire из бандла, реализуемо; в ядре отсутствует).
4. **Мультиаккаунт** — `account.getMulti` (wire из бандла): переключение аккаунтов согласуется с exchange-token хранилищем ядра (`ExchangeTokenStorage`). Средний приоритет.
5. **VK Pay/платежи/голоса/Подписки — честный отказ.** Обоснование: (а) платёжный контур физически не в кабинете — бандл содержит только `VKWebAppOpenPayForm` и go_to_miniapp-переходы, т.е. карты/кошелёк живут в отдельном мини-приложении VK Pay со своей PCI-инфраструктурой; (б) в бандле не найдено ни одного платёжного endpoint'а (no-stub: переносить нечего); (в) биллинг/голоса требуют отдельной авторизации VK Pay и юрисдикционных обязательств — вне рамок клиента-мессенджера PinoK. Вместо встраивания — deeplink/внешний браузер на id.vk.com/vkpay при необходимости.
6. **Не переносить**: телеметрия (stats.trackVisitor/trackEvents, /api/perf, /api/crash, sentry, trk.mail.ru, bid.vk.com), промо-баннеры, «Оцените кабинет», FAQ-виджеты.

---

## §5. Честные ограничения

1. **`vk_куки_локалстордж.txt` — 0 байт (пустой файл).** Структуру ключей значением-бесплатно берём из более раннего среза `vk.id.md` (стадии 1–3): cookies-домены `.vk.ru/.vk.com/.id.vk.ru/.login.vk.ru`, ключи `anonym_id`, `anonym_id_long`, `sua`, `sui`, `httoken`, `remixsid`, `logout_hash`, `access_token`, `web_token`, `exchange_token`, `sat_token`, `trusted_hash`, `device_id`, `_clientId`, `app_id`, `uuid`; localStorage: `vkid_auth_by_autologin_timestamp`, `vkid-redirect`, `sessionViewLs` + флаги `vkid_account_*`. **Значения/секреты не копирулись и в документ не переносились** (секреты — только в защищённом хранилище ядра, см. ExchangeTokenStorage).
2. «Подписки» — пустой shell в снапшоте (клиентский рендер): инвентарь секции неполный, восстановлен только по набору иконок-ассетов (donut/music/play/video/dating/dzen).
3. SPA-модалки (смена телефона/email, отзыв сервиса, устройства/сессии) в сохранёнку не попали — для них в бандле есть только имена событий-состояний (effex), но не API-методы: соответствующие строки §3 помечены честно (❗ с неполным wire / ⚠️).
4. `secure.*` методов в бандле и в ядре **не обнаружено** вообще (0 совпадений) — двухфакторка/сессии через secure-неймспейс не подтвердились; вероятный транспорт — GraphQL/JSON-RPC id.vk.com, который в сохранённые JS-чанки не вынесен.
5. Бандл — минифицированный (1 строка ~2.2 MB), выписка только rg-ом: имена методов могли частично остаться в других чанках (на страницах лежат 20+ ленивых чанков `account.NNNN.*.chunk.js` — не скачивались в снапшот, только URL в HTML).
6. Время съёмки снапшота — сентябрь 2026; RU-тексты/статусы («Был изменён месяц назад», «Не подтверждён») — инстанс-специфичные.
