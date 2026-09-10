# Волна 33 — W33-PROFILE-STRUCTURE: план изученного и внедрения

Дата: 2026-09-10 · Ветка: PinoK · Валидация: юзерская сборка `:app:compileDebugKotlin` (gradle агентом НЕ запускается)

## 0. Состав волны (ТЗ пользователя)

1. Устранить **~80 ошибок компиляции** `:app:compileDebugKotlin` (ProfileScreen/UserProfileScreen/ChatDetailScreen) + **ворнинги** `:feature:calls` (deprecated `Icons.Outlined.OpenInNew`, `LocalClipboardManager`).
2. Профиль по скриншоту `Screenshot_20260910_212155.png`: **жёлтое** — «Выйти из аккаунта» убрать из профиля; **зелёное** — счётчики (Друзья/Подписчики/Фото/Видео/Аудио/Подарки) → переход в свои разделы; **синее** — табы (Музыка/Видео/Фото/Клипы/Статьи/Закладки) → открывать разделы **целиком**, профиль остаётся НАД ними.
3. VK ID — «нет полного функционала»: изучить снапшот, составить справку/документацию и план.
4. Отчёт тестировщика: (1) «отметить как непрочитанное» не работает («у собеседника отображается прочитанным»); (2) в профиле не открываются друзья/подарки/фото/аудио; (3) фото-вложения сообщений не открываются в полный размер; (4) галочка прочтения всегда одна — хочется ✓✓/цвет.
5. План функционала **администратора сообщества**; изучить, чем снапшот «Группа_админ.zip» отличается от простых групп; приложенный `локалсторд_куки.txt` — в помощь.
6. Составить планы и документации (этот файл + HISTORY.md + worklog.md).

---

## 1. План изученного

### 1.1 Root-cause ~80 ошибок компиляции (главное открытие волны)

Симптомы компилятора: `Unresolved reference 'WallPostCard'` (ChatDetailScreen :231/:3610/:7912), `Unresolved 'ProfileHeader'/'CountersRow'/…` (ProfileScreen/UserProfileScreen), `Modifier 'private' is not applicable to 'local function'` (ProfileScreen :2423+), `Syntax error: Expecting '}'` (ProfileScreen :3345 = EOF).

**Разбор карты глубины скобок** (`/tmp/brace_depth.py` — трассировка depth по строкам):
- `fun ProfileScreen(` открывается на :172 и **никогда не закрывается** — EOF на depth=1.
- Все «топ-уровневые» хелперы после диалогов (`ProfileHeader` :1499, `CountersRow`, `WallPostCard`, `VideoThumbnail`, `RepostBlock`, `ActionIcon`, `ProfileChipsRow`, `WallFilterRow`, `MusicTabSection`, `ProfileTrackCard`, …, `jsonPrimitiveStr`, `ProfileArticleInfo`, `BOOKMARKS_PAGE_SIZE`) оказались **локальными функциями внутри ProfileScreen**:
  - локальная `fun` без модификаторов компилируется, но НЕ видна снаружи → `Unresolved` в UserProfileScreen (тот же пакет) и ChatDetailScreen (`import re.pinok.ui.screens.profile.WallPostCard`);
  - локальная `private fun`/`private const`/`private class` — синтаксическая ошибка («private not applicable to local …»);
  - вызовы ДО объявления локальной функции не видят её (лексическая область) → `Unresolved 'ProfileHeader'` на :979 внутри самой ProfileScreen;
  - потеря типов в trailing-lambda (`Cannot infer type for 'it'/'urls'/'idx'`) — каскад от неразрешённых символов.

**Где потерялась скобка**: `git diff ba42c2f2 0f619f5a -- ProfileScreen.kt` (W31-b, удаление logout). Удаляемый hunk заканчивался на **`-    }` `-    }`** — первая закрывала `if (showLogoutConfirm)`, вторая — **`Column`**. W31-b удалил их вместе с logout-блоком и НЕ вернул скобку Column. Дальше по цепочке: диалоги (`photoViewer`/`repost`/`status`/`delete`/`report`/`edit`) провалились внутрь Column (depth 2), закрывающая скобка в конце файла стала закрывать Column, а ProfileScreen остался открытым до EOF.

**Урок для сканеров**: баланс скобок (откр=закр) НЕ ловит такой кейс, если где-то есть компенсирующий лишний `{` — а здесь файл просто кончался на depth=1, и баланс был нарушен ровно на 1, что проверялось только «EOF-syntax error». Валидатор `check-nested-comments.py` скобки вообще не проверяет. В W31-b claim «683/683 сбалансирован» относился к моменту до финальных правок — контроль глубины ДОЛЖЕН быть обязательным: `final depth == 0` + «декларации на depth 0».

**Фикс (33-a)**: восстановлена закрывающая `}` Column сразу после Box-weight-зоны (после ScrollToTopFab, перед диалогами) с комментарием `W33-a FIX (root-cause …)`. После фикса: `final depth = 0`, все декларации — топ-уровень, `check-nested-comments` ALL CLEAN (177), NULL-скан добавленных строк — 0 хитов `!!`/`?.`/`?:`.

### 1.2 Ворнинги `:feature:calls` (33-b)

- `Icons.Outlined.OpenInNew` deprecated (RTL-зеркалирование) → `Icons.AutoMirrored.Outlined.OpenInNew` + import `androidx.compose.material.icons.automirrored.outlined.OpenInNew`. Точки: `CallsRecordingsSection.kt` (меню записи «Открыть в VK Видео») **и превентивно** `VideoPlayerScreen.kt` (кнопка PiP — та же deprecated-иконка дала бы ворнинг на следующей сборке `:app`).
- `LocalClipboardManager` deprecated (Compose 1.8+, BOM 2025.06.00) → прецедент проекта **Fix #193 (LandingScreen)**: платформенный `android.content.ClipboardManager` (`getSystemService(CLIPBOARD_SERVICE)`), синхронно, без coroutine-scope. Применён в `CallsScheduleDialog.kt` (2 точки) и `CallsScheduledSection.kt` (copyInvite); импорты `AnnotatedString`/`LocalClipboardManager` удалены.

### 1.3 Профиль: изученное (33-c)

- «Выйти из аккаунта» из профиля **уже удалён в W31-b** — на скриншоте юзера сборка ДО волны 31 (31-32 не компилировались). После 33-a кнопки в профиле нет; выход — только в drawer.
- Счётчики: `CountersRow` делал кликабельными только «Друзья»/«Подписчики» (П-7-AB), Фото/Видео/Аудио/Подарки — `chipClick = null` → «отклика нет» (жалоба тестировщика = та же причина).
- Табы контента: переключение работало, но разделы были «превью-полосами» с жёсткими капами: Музыка=10 (`audioGet(count=10)`), Видео=9, Фото=12; Клипы=30, Статьи=20, Закладки — пагинированы. «Разделы целиком» = вертикальные списки + пагинация.
- Урок волны 31 `#AUDIO-PAGING` применён: серверный offset считается по **RAW-странице до фильтров**; для музыки total из `audioGetWithCount` (response.count), для video/photo/gifts — hasMore по заполненности страницы (паттерн закладок).
- `gifts.get` официально поддерживает `offset` → `VKApiClient.giftsGet(+offset: Int = 0)` (аддитивно).
- Виртуализация: строки треков/ряды видео-сетки/ряды фото-сетки выпущены **отдельными item'ами общего LazyColumn** (не Column внутри одного item) — сотни/тысячи элементов; `collectAsState`/`remember` вынесены на уровень тела экрана (в LazyListScope composable-вызовы запрещены).
- Скролл-переходы: индекс контента вкладки = 5 (перед ним ProfileHeader/Редактировать/Счётчики/Подписки/чипы); для подарков — последний item (ряд подарков под лентой) с ожиданием загрузки gifts (таймаут 5с от вечного цикла).

### 1.4 Отчёт тестировщика: верификация по коду (33-d)

| Жалоба | Состояние актуального кода | Вывод |
|---|---|---|
| «Отметить как непрочитанное» (у собеседника — прочитанным) | Реализовано серверно: `messages.markAsUnreadConversation` (VKApiClient :2068, Fix #274) в списке диалогов (MessagesScreen :1439, optimistic+rollback+#MARK-READ-REVERT) и в меню шапки чата (ChatDetailScreen toggleUnreadMark :1695). **Но**: сброс прочтения ДЛЯ СОБЕСЕДНИКА невозможен в принципе — в VK API нет метода, влияющего на read_state у другой стороны; метка непрочитанного действует только на СВОЙ список диалогов (жирный, без бейджа) | Работает как в VK; ожидание «у собеседника непрочитано» = ограничение платформы, задокументировать. Проверить в новой сборке, что метка видна в списке диалогов |
| Друзья/подарки/фото/аудио в профиле не открываются | Чипы Фото/Видео/Аудио/Подарки были не-кликабельны (1.3). В W33: все 6 счётчиков → переходы + скролл к разделу | Исправлено 33-c |
| Фото-вложения сообщений не открываются в полный размер | Реализовано: P5.1 `onPhotoClick` → хостовый `PhotoViewer` (pinch-zoom + swipe, сохранение в галерею), Fix #244 (selection-mode toggle), Fix #228 (масштаб стикер-фото) | Уже есть; тестировщик собирал старую сборку. Перепроверить после исправной компиляции |
| Галочка всегда одна (хочется ✓✓) | Реализовано: `Message.readState` парсится (VKApiClient :1318/:1429/:10101), ✓/✓✓ (Done/DoneAll, ChatDetailScreen :5452), LP-события ReadOutbox(7)/ReadInbox(6) обновляют readState по **cmid** (Fix #296: раньше сравнивали msg.id ≤ ev[2], где ev[2]=cmid — никогда не срабатывало) | Уже есть (Fix #296); старая сборка тестировщика предшествует ему или ловила баг до фикса |

Также найден ложный след: `MessagesScreen :1234 onToggleUnread = { _, _ -> }` — это осознанный no-op для виртуального self-чата «Избранное» (Fix #356, `showListActions=false`, меню не открывается) — НЕ заглушка.

### 1.5 VK ID — изученное (33-e, снапшот upload/snapVK_ID)

Это личный кабинет **id.vk.ru/account** (SPA React+VKUI, общий бандл `account.bundle.521111fa85c2afe0f56a.js` 2.2МБ; ~45 ленивых чанков НЕ скачаны). Роуты: `main/personal/vkpay/services/subs/security/user-restore/family/lite/simple` + модалки (`password-change, devices, view-history, app-passwords, reserve-codes, otp-settings, phone-change, email-change, session-info, security-reset-sessions, my-cards, operations-history…`).

Реальные API из бандла (namespace целиком):
- `accountPersonal.*`: getMainData, getUserProfileInfo/saveProfileInfo/validateProfileInfo, getCanChangePhone/actualizePhone/markActualizePhone, startChangeEmail/changeEmail, startChangeNotifyEmail/changeNotifyEmail, getAllUserLinks, getAppScopes{id}, deactivateLink{id,code,hash,type}, deactivateExternalOAuthService, getActivityHistoryDevices/Group/GroupItem/Map, resetSessions/resetAllSessions, payGetElectronicFunds, payGetOperationsHistory, payAddCardBinds/payDeleteCardBinds, payTopupVKBalance, getSubscriptions, checkPassword, deactivate/reactivate, disable2FAByPassword.
- `settings.*`: getSecurityInfo, changeSecuritySettings, changePasswordStart, doChangePassword{app_id,new_password,hash,reset_session}, changePassword (legacy), getAppPasswords/deleteAppPassword, getReserveCodes, enableOTPBySMS/disableOTPBySMS/enableOTPByApp/disableOTPByApp/getOTPbyAppSecret, getSessionInfoForReset{login_hash,hash}, changePhone/changePhoneForce/changePhoneCancel, startChangeEmail, startChangeNotifyEmail, performEmailBannerAction, resetEmailSoftBouncing, webauthnRegisterBegin/Finish{sid,webauthn_data}, webauthnRemoveCredential/webauthnUpdateDevice/unregisterValidateDevice.
- `cua.*` (подтверждение действий): getValidationMethods, sendPushCode/checkPushCode, sendPhoneCode/checkPhoneCode, sendEmailCode/checkEmailCode, sendPhoneBindCode/checkPhoneBindCode, checkPassword, setPassword. CUA-защищённые методы (список в бандле): changePasswordStart, deletePassword, getReserveCodes, getAppPasswords, webauthn*, startChangeEmail/changeEmail, changeNotifyEmail, changeSecuritySettings, changePhoneForce, saveProfileInfo и др.
- Прочее: `account.deletePassword`, `account.getPasskeyDevices`, `account.getMulti` (мультиаккаунт), `multiaccount.setRelatedUserPinCode/childSignup/…`, `accountVerification.*` (связка esia/sber_id/vtb_id/mail_ru/ok/yandex), `vkProtect.*` (appeals), `photos.getOwnerPhotoUploadServer/saveOwnerPhoto/delete`, `auth.getAppScopes`, `stats.track*` (не переносить).
- Транспорт: POST `https://api.vk.com/method/<метод>`, `credentials:"include"`, `v=5.131/5.207`, access_token в теле; OAuth silent-flow PKCE (`code_challenge_method=s256`, `response_type=silent_token`, connectDomain id.vk.com, v=1.61.1).
- data-testid DOM: `main-data, personal-data, security-data, vkpay-data, subs-data, services-data, panel-header, password-cell, security-page-devices-cell, providers-block, header-dropdown-logout, personal-form, nickname-input, sex-dropdown, year-dropdown, phone-cell-masked-phone…`.

**Уже в PinoK** (W31): `accountPersonal.getSecurityAlerts/setSafetyNetEnabled/getActivityHistoryDevices/resetSessions/resetAllSessions/getSessionInfoForReset`, `cua.getValidationMethods`, `settings.startChangeNotifyEmail/performEmailBannerAction`, `account.getMulti/getToggles`; экран `DevicesScreen` + `CuaVerifySheet` + `SecurityAlertsPoller`; `VkIdAccountScreen` (ссылочные ячейки на id.vk.com — честная заглушка-ссылка).

**Поправка к старому доку**: `vkid.снапшоты.парсинг.полный.md` утверждал «wire сессий/пароля/сервисов/платежей не снят» — устарело: namespaces сняты полностью (см. выше).

### 1.6 Админ сообщества — изученное (33-f)

- **«Группа_админ.zip» В upload ОТСУТСТВУЕТ** (проверено ls) — сравнение со снапшотом = долг волны.
- `локалсторд_куки.txt` (143 строки: localStorage 1–105, cookies 107–141): десктоп web vk.ru, userId 171093180, последняя страница — сообщество `vk.ru/pluton240` (`remixsts`), метрики `groups_list`/`group`/`wall_post`. **Явных админ-маркеров НЕТ** (grep admin/manage/editor/moder/stats/ads_/al_groups/method — 0). Админ-релевантное: черновик поста в сообщество `-241400569` (`channels-drafts`) с `sendOptions.selected=["enable_comments"]` + ads-поля (`predId/erId/isAd`) — модель composer'а постинга в сообщество; ключ `postponedDrafts` (отложенные, пуст). Куки ролей НЕ несут — `admin_level` только серверно из API.
- Снапшоты групп в проекте: **страниц групп/админки нет** (`is_admin:1` — 0 вхождений по всем снапшотам). Ближайшее: snapПрофиль `…настройки_уведомления_ группы в которой я администратор.html` (groups.get с `is_admin/is_advertiser`, lang `settings_admin_groups_left`) и snap2308 `postingForm.*.js`.
- Текущее состояние PinoK: `GroupsScreen` (groupsGet БЕЗ filter), `CommunityScreen` (1802 строки; groupsGetById; табы; groupsJoin/Leave; **пост-карта без меню**), `GroupMembersScreen`, `BoardTopicScreen`, `NotificationSettingsScreen` (groupsGet(filter="editor") + groupsEditNotifications). API: `groupsGet` поддерживает `filter=admin/editor/moder/groups/publics` (Fix #144), модель Group имеет `canPost/adminLevel`; wall-контур полный (post/edit/pin/unpin/delete/restore/getById/comments); upload стены (photos.getWallUploadServer(group_id)/saveWallPhoto/docs). **Отсутствуют**: `groups.editManager`, `groups.getSettings/setSettings`, `groups.getMembers(filter=managers)`, `stats.get/getPostReach`, `groups.banUser/getBanned/getRequests`, `wall.post` с `from_group`, админ-UI в CommunityScreen; `GroupInfo` не содержит can_post/admin_level.

---

## 2. План внедрения

### 2.1 Выполнено в этой волне (33-a…33-d)

- **33-a**: скобка Column в ProfileScreen восстановлена (root-cause §1.1) — закрывает ВСЕ ~80 ошибок трёх файлов.
- **33-b**: OpenInNew→AutoMirrored (×2 файла), LocalClipboardManager→платформенный ClipboardManager (×2 файла).
- **33-c** (#PROFILE-COUNTERS-NAV, #PROFILE-TAB-FULL, #PROFILE-GIFTS-PAGE):
  - `CountersRow`: +onPhotosClick/onVideosClick/onAudiosClick/onGiftsClick (дефолты null — UserProfileScreen совместим);
  - проводка: Фото→вкладка фото, Видео→видео, Аудио→музыка, Подарки→стена+скролл к подаркам; скролл-эффекты `contentScrollTick`/`giftsScrollTick` (animateScrollToItem(5) / к последнему item);
  - разделы целиком: Музыка — вертикальный список ВСЕХ треков (страницы по 100, `audioGetWithCount`, `ProfileTrackRow` + `TabShowMoreRow`), Видео — сетка 2 колонки (страницы по 20, `ProfileVideoCard(+modifier)`), Фото — сетка 3 колонки (страницы по 60, `PhotoGridRow`), Подарки — «Показать ещё» (страницы по 10); server-offset по RAW-странице;
  - VKApiClient: `giftsGet(+offset)`.
- **33-d**: код не менялся (всё уже реализовано: Fix #274/#296/P5.1) — документированы ограничения VK (peer-side read_state недоступен API).

### 2.2 VK ID — план по фазам (будущие волны)

- **P0**: (1) смена пароля VK ID (`changePasswordStart` → CUA → `doChangePassword`, + `deletePassword`); (2) «Вход через VK ID» — подключённые сервисы: `getAllUserLinks` → `getAppScopes{id}` → `deactivateLink`/`deactivateExternalOAuthService`; (3) смена email/телефона (`startChangeEmail/changeEmail`, `changePhone*` + `cua.*Code`). Инфраструктура CUA/CuaVerifySheet готова (прецедент reset_sessions).
- **P1**: (4) 2FA и способы входа (`getSecurityInfo/changeSecuritySettings`, OTP SMS/App + `getOTPbyAppSecret` TOTP, `getReserveCodes`, `getAppPasswords`); (5) история активности (`getActivityHistoryGroup/GroupItem/Map` + модалка view-history; devices-часть готова); (6) смена аватара (`photos.getOwnerPhotoUploadServer/saveOwnerPhoto`); (7) связанные аккаунты-провайдеры (esia/sber_id/… — низкий приоритет для RU-контура).
- **P2**: (8) мультиаккаунт-переключение (нужен multi-slot в ExchangeTokenStorage — сейчас single-session; высокая сложность); (9) VK Pay — только deeplink/внешний браузер (PCI-контур НЕ встраивать); (10) подписки (`getSubscriptions` — страница в снапшоте пуста, wire только из бандла); (11) удаление аккаунта (`deactivate/reactivate`) — только по явному запросу юзера.
- **Не переносить**: телеметрия (`/api/perf/upload`, `/api/crash/*`, sentry, `stats.*`, bid.vk.com), промо-баннеры, `multiaccount.child*`, `vkProtect.*`.
- **Риски**: (а) CUA-hash-контур — нужна live-проверка ответов (часть wire в ленивых чанках, которых в снапшоте нет); (б) кабинет ходит на api.vk.com с web-кукой `credentials:"include"` — не доказано, что все методы примут андроид-токен PinoK (прецедент успешный: getActivityHistoryDevices/resetSessions); (в) домены .com/.ru и версии API дрейфуют — решено через AuthDomainsConfig; (г) секреты из дампов в доки не копировать (прецедент утечки b2f66220/213115f4).

### 2.3 Админ сообщества — план по фазам (будущие волны; драфт БЕЗ снапшота «Группа_админ.zip»)

- **P0 (фундамент)**: (1) роли: `groupsGetById` fields +`can_post,is_admin,admin_level,is_advertiser`; проброс в `GroupInfo`; кнопка/блок «Управление» в CommunityScreen при `adminLevel>0 || canPost==1`; (2) постинг от имени сообщества: `wallPost(+from_group=1)`, composer с переключателем «от моего имени / от имени сообщества», upload уже готов; (3) меню поста сообщества: wall.edit/delete/pin/unpin (API есть, UI собрать как в ProfileScreen).
- **P1 (управление)**: (4) руководство `groups.getMembers(filter=managers, fields=role)` + `groups.editManager` (назначить/снять moderator/editor/administrator); (5) настройки `groups.getSettings` + `groups.edit` (название/описание/screen_name/доступ/категория) + `groups.setSettings` (стена/фото/видео/обсуждения); (6) статистика `stats.get` (group_id, интервалы) + `stats.getPostReach`; (7) участники: `groups.banUser/unbanUser/getBanned`, `groups.getRequests/approveRequest/declineRequest`.
- **P2**: отложенные посты UI (`publish_date` уже в wallPost), обложка/аватар, приглашения `groups.invite`, ссылки `groups.addLink/deleteLink`, альбомы; рекламный кабинет `ads.*` — вне скоупа.
- **Долг-подтверждение**: как только юзер приложит `Группа_админ.zip` — сверить: состав блока «Управление», реальные эндпоинты web (`al_groups.php`/method/*), UI composer (sendOptions: enable_comments/подпись автора/ads-поля — модель уже видна в дампе `channels-drafts`), экран статистики; и что `admin_level` приходит в groups.get/getById без спец. полей.

---

## 3. Проверка пользователем (сборка волны 33)

1. `git pull` → `:app:compileDebugKotlin` — **0 ошибок**; `:feature:calls:compileDebugKotlin` — **0 ворнингов** OpenInNew/Clipboard.
2. Профиль: кнопки «Выйти из аккаунта» внизу НЕТ (выход — drawer).
3. Профиль, счётчики: «Фото» → открыт раздел фото (профиль сверху), «Видео» → видео, «Аудио» → музыка, «Подарки» → лента проматывается к ряду подарков, «Друзья»/«Подписчики» → свои экраны.
4. Табы: «Музыка» — вертикальный список треков с длительностью, «Показать ещё» докидывает страницы; «Видео» — сетка 2 колонки + «Показать ещё»; «Фото» — сетка 3 колонки + «Показать ещё»; «Подарки» на стене — «Показать ещё» под рядом открыток.
5. Тестировщику (собрать заново!): непрочитанное — метка в списке диалогов (жирный); ✓✓ появляется, когда собеседник прочитал (LP); фото сообщения — тап открывает полноэкранный просмотрщик; «у собеседника непрочитано» сделать НЕЛЬЗЯ — нет такого API ни у официального клиента.

## 4. Долги
- Снапшот «Группа_админ.zip» не приложен — сверка админ-плана (§2.3) после получения.
- ~45 ленивых чанков VK ID не скачаны — отдельные флоу (verifications, детали устройств, подписки) требуют живого дампа.
- Пиалет «ещё» 42+ реакций, подменю «Переслать», снятие «важного» — долги волны 32 (не тронуты).
- Токены в git-истории (b2f66220/213115f4) — rewrite вне волн.
