# OpenVK Legacy (mobile-android-legacy) — изучение: что можно вытащить в PinoK

> Задача 2-a. Репозиторий-справочник: /tmp/opvk (read-only, клон https://github.com/OpenVK/mobile-android-legacy).
> Вопрос: «что можно вытащить в наше приложение из этого репозитория?» — с прицелом на последующее внедрение.
> Код PinoK не менялся. Это ИССЛЕДОВАТЕЛЬСКИЙ документ: идеи/логика/UI-паттерны, НЕ копипаста Java.

---

## §0. Метод изучения

1. Прочитаны worklog.md (хвост ~130 строк, конвенции зон/VKA/no-stub) и HISTORY.md (голова).
2. Обход /tmp/opvk целиком: README → COPYING (лицензия) → AndroidManifest (все компоненты) →
   activities (26 объявлений) → fragments (13) → adapters/items/views → services/receivers →
   databases → utils → модуль `modules/ovk-api` (API-слой: OpenVKAPI, OvkAPIWrapper, модели, entities,
   LongPollWrapper, UploadManager, DownloadManager) → res (156 layout, 17 menu, 11 pref-xml, values/arrays).
3. Полный инвентарь API-методов: `grep sendAPIMethod(...)` по всему Java-коду (app + modules) →
   **46 уникальных VK-методов** (список в §1.4). Отдельно проверены отсутствующие семейства:
   `docs.*`, `gifts.*`, `board.*`, `notifications.*`, `photos.createAlbum` — **0 вызовов** в OpenVK.
4. Проверка orphaned-верстки: по 16 спорным layout-ам посчитаны ссылки `R.layout.<name>` в Java
   (результат в §1.6 — часть «фич» существует только как верстка и НЕ подключена).
5. Проверка фезибилити для PinoK: по каждому кандидату — rg по
   `app/src/main/java/re/pinok/api/VKApiClient.kt` (16 299 строк) на наличие wire-метода
   (`rg "\"notes\.|\"polls\.|\"wall\.getComments" ...` и т.д., счётчики приведены в §2),
   и rg по экранам PinoK (`app/src/main/java/re/pinok/ui/screens/**`, `feature/{photos,audio}`) —
   есть ли фича уже (чтобы не предлагать сделанное).
6. Честность: всё, что не подтверждено чтением кода/rg, помечено как «не найдено/не утверждается».

Статистика репо: 5 747 файлов (без .git), 275 Java-файлов (184 в app/src, 91 в modules),
156 layout, 17 menu, 11 pref-xml. Java + legacy support-библиотеки, minSdk эпохи Android 2.1–2.3.

---

## §1. Инвентарь OpenVK Legacy

### 1.1. Что это

Legacy VK-совместимый клиент для старых Android (2.1+), автор Dmitry Tretyakov (Tinelix),
переделан сообществом OpenVK под **OpenVK API** (VK API 3.x-совместимый, сервер — произвольный
«инстанс», например ovk.to). Контур OpenVK: свой хост, своя авторизация `grant_type=password`
на `/token`, свои методы `Ovk.*`, свои поля (`rating`, `is_dead`, `reg_date`, `is_explicit` у поста).
Лицензия всего репо — **GNU AGPL-3.0** (COPYING).

### 1.2. Структура

```
app/src/main/java/uk/openvk/android/legacy/
  core/activities/        15 активностей + base/ (Network*, Translucent*, UsersListActivity)
                          + intents/ (7 deep-link активностей) + settings/ (5 настроек)
  core/fragments/         9 фрагментов-разделов + base/ + pages/ (Profile/Group page)
  core/{listeners,methods,enumerations}
  databases/              6 SQLite-кэшей + base (CacheDatabase)
  receivers/              5 (LongPollReceiver, OvkAPIReceiver, MediaButton*, ...)
  services/               AudioPlayerService (foreground), LongPollService, AuthenticatorService
  ui/list/adapters/       24 адаптера; ui/list/items/ 9
  ui/views/               27 custom-view + attach/ (Audio/Poll/Video/Common) + base/
  ui/{preferences,text,utils,wrappers}; utils/ (NotificationManager, SecureCredentialsStorage,
                          RealPathUtil, ACRACustomSender); utils/media/ (OvkMediaPlayer + FFmpeg-треки)
modules/                  ovk-api (API-клиент-библиотека), actionbar, popupmenu, slidingmenu,
                          twemojicon (эмодзи), wrhttp (legacy http)
```

### 1.3. Полный список экранов/фич (по активностям, фрагментам и верстке)

**Активности (26):** MainActivity (сплэш, включая `activity_splash_xmas` — сезонный), AppActivity
(главный хаб: sliding menu + табы-фрагменты + счётчики-бейджи меню), Auth (логин + 2FA-диалог
`dialog_twofactor_auth` + выбор инстанса), Conversation (чат), GroupMembers (участники сообщества),
NewPost (создание поста с фото-вложениями), NoteViewer (заметка, WebView), PhotoAlbum (фотоальбом),
PhotoViewer (просмотр фото + «Сохранить»), QuickSearch (глобальный поиск), VideoPlayer,
WallPost (пост + комментарии), CrashReporter (ACRA-репорт с редактированием), 7 интентов
(deep-links `openvk://ovk/id...`, `https://ovk.to/id...` → Profile/Friends/Videos/Audios/Photos/Notes/Group),
5 настроек (Main/Advanced/Network/DebugMenu/AboutApplication).

**Фрагменты-разделы (9 + 2 страницы):** Newsfeed (лента: «мои»/«глобальная», infinite scroll,
pull-to-refresh, кэш), Friends (табы Все/Онлайн/Заявки + бесконечная прокрутка), Groups (список +
поиск), Photos (список альбомов), Videos (список), Audios (список + поиск по своим аудио),
Conversations (список диалогов), Notes (список заметок), MainSettings; pages: ProfilePage
(шапка+статус+кнопки ЛС/добавить+счётчики+селектор стены+стена), GroupPage (шапка+о группе+
join/leave+стена).

**Механики/фичи, видимые в коде:**
- Пост-кард: аватар/имя автора, текст, репост-цитата (RepostInfo), счётчики like/repost с
  состояниями isLiked/isReposted (PostCounters), вложения (фото-сетка, видео с thumbnail,
  аудио-плеер-строка, опрос-виджет), иконка платформы публикации (`post_source.platform`:
  android/iphone/mobile — NewsfeedAdapter:222), 18+-гейт (`is_explicit` + настройка `safeViewing`,
  PostViewLayout:201, PostAttachmentsView:133, NewsfeedAdapter:263).
- Комментарии: список (CommentsListAdapter), поле с эмодзи-кнопкой, отправка Wall.createComment,
  оптимистичная вставка своего коммента, «нет комментариев»-состояние.
- Репост поста: диалог с полем сообщения → `Wall.repost` (dialog_repost_msg, Wall.java:768).
- Чат: пузыри in/out, forward-блоки (`message_*_fwd.xml`), сепараторы дат, эмодзи-панель
  (twemojicon) с подстройкой высоты под клавиатуру, копирование текста сообщения,
  удаление сообщения, оптимистичная отправка + isError-состояние, онлайн-статус собеседника
  в титуле, `Messages.getConversationsById`/`getHistory`/`send`/`delete`, LongPoll-подтолкивание.
- LongPoll: `act=a_check&key&ts&wait=15` (LongPollWrapper:125), broadcast в Activity, фоновый
  сервис + `updateCounters` (Account.getCounters каждые 60с) и `keepUptime` (Account.setOnline).
- Уведомления: каналы LongPoll/AudioPlayer, LED/вибро/рингтон-настройки, группировка по
  диалогам (utils/NotificationManager.buildDirectMsgNotification:116).
- Аудио: foreground-сервис-плеер с плейлистом, seek, next/prev, уведомление-плеер, кнопки
  в шторке; текст песни `Audio.getLyrics` (AudioPlayerActivity:300); поиск по своим аудио.
- Видео: `Video.get` → `files.mp4_144…1080/ogv_480` (лестница качеств, VideoPlayerActivity:126).
- Фото: альбомы (`Photos.getAlbums`), фото альбома (`Photos.get` по album_id),
  просмотрщик, «Сохранить» в галерею, кэш по категориям (`photo_albums`,
  `newsfeed_photo_attachments`, `wall_photo_attachments` — DownloadManager:437-446),
  настройки качества (`photos_quality`: оригинал/высокое/среднее → выбор size-индекса 10/8/5
  в WallPost.java:194-196), `forceCaching`, очистка кэша + показ размера
  (DownloadManager.clearCache:743/getCacheSize:770, `dialog_imgcache_quality`).
- Загрузка: UploadManager — multipart с прогрессом; в пост только фото
  (`Photos.getOwnerPhotoUploadServer`/`getWallUploadServer` → `saveWallPhoto` → `Wall.post`,
  NewPostActivity:154/485/516), статус-машина вложения (uploaded/uploading) в списке-чеке.
- Профиль «О себе»: статус-редактор, дата рождения, интересы, музыка, фильмы, ТВ, книги,
  контакты, дата регистрации (AboutProfileLayout:71-157), счётчики-плитки (ProfileCounterLayout),
  селектор стены, кнопки «Написать»/«Добавить в друзья», ban_reason у забаненного.
- Друзья: заявки (`Friends.getRequests`) отдельным адаптером, add/delete (Friends.java:166/170).
- Группы: join/leave с переключением кнопки, участники (`Groups.getMembers`), поиск
  (`Groups.search`), «О группе»: описание + сайт.
- Заметки: список (`Notes.get`), просмотр — XHTML в WebView (NoteViewerActivity:229-261),
  создание/редактирование (`notes.add`/`notes.edit`, NoteViewerActivity:197-199).
- Опросы в постах: вопрос/анонимность/дата конца, ответы с полосами, голосование
  `Polls.addVote`/`deleteVote` (PollAttachView/PollAdapter).
- Кэш-БД (SQLite, databases/): NewsfeedCacheDB, WallCacheDB, MessagesCacheDB, UsersCacheDB,
  GroupsCacheDB, AudioCacheDB — «показать из кэша мгновенно, потом обновить»
  (ProfilePageFragment.loadWallFromCache:580, WallCacheDB.putPosts:610).
- Настройки (pref-ключи): friendsOrderNew, refreshOnOpen, notifyBDays, safeViewing,
  enableNotification/notifySound/notifyVibrate/notifyLED/notifyRingtone, doNotDistrib,
  interfaceLanguage, uiTheme (Blue/Gray/Black), startupSplash, newsBanned, loadImages,
  forceCaching, imageCacheQuality, useHTTPS, useProxy (+dialog_proxy_settings).
- Мульти-инстанс/мульти-аккаунт: список инстансов (InstancesListAdapter/AuthActivity),
  аккаунт-меню sliding menu («Change account», «Change profile photo», «Log out»),
  AuthenticatorService (системный Account).
- Прочее: заготовка домашнего виджета плеера (верстка+provider-xml есть, AppWidget-провайдер
  в манифесте/коде НЕ зарегистрирован — фича брошена на полпути),
  DebugMenu, AboutApplication, CrashReporter (ACRA, кастомный sender), сезонный сплэш,
  глобальный поиск (Users.search + Groups.search в одном экране QuickSearchActivity),
  «О инстансе» (`Ovk.aboutInstance`: статистика/админы/ссылки — dialog_about_instance).

### 1.4. Полный список VK-методов, вызываемых OpenVK (46)

```
Account.getCounters, Account.getProfileInfo, Account.setOnline,
Audio.get, Audio.getLyrics,
Friends.add, Friends.delete, Friends.get, Friends.getRequests,
Groups.get, Groups.getById, Groups.getMembers, Groups.join, Groups.leave, Groups.search,
Likes.add, Likes.delete,
Messages.delete, Messages.getConversations, Messages.getConversationsById,
Messages.getHistory, Messages.getLongPollServer, Messages.send,
Newsfeed.get, Newsfeed.getGlobal,
Notes.get, Notes.getById, Notes.edit (+notes.add),
Ovk.aboutInstance, Ovk.version,
Photos.get, Photos.getAlbums, Photos.getOwnerPhotoUploadServer, Photos.getWallUploadServer,
Photos.saveWallPhoto,
Polls.addVote, Polls.deleteVote,
Users.get, Users.search,
Video.get,
Wall.get, Wall.getById, Wall.getComments, Wall.createComment, Wall.post, Wall.repost
```

**НЕ подключены в OpenVK** (0 вызовов при rg по всему Java): `docs.*`, `gifts.*`, `board.*`,
`notifications.*`, `status.*`, `photos.createAlbum`. Верстка для части из них существует,
но не используется (см. §1.6).

### 1.5. Модули modules/

`ovk-api` — API-библиотека (OvkAPIWrapper 1147 строк: OkHttp/legacy HttpClient, прокси,
HTTPS-переключатель, очередь Account-методов; модели по секциям API; LongPollWrapper 315;
UploadManager 373 — multipart c прогрессом; DownloadManager 797 — файловый фото-кэш);
`actionbar`/`popupmenu`/`slidingmenu` — ретро-UI (не интересны Compose-клиенту);
`twemojicon` — эмодзи-панель (идея заменяема системной клавиатурой/sticker-меню);
`wrhttp` — legacy-HTTP. Все файлы с AGPL-заголовками.

### 1.6. Orphaned-верстка (запланировано, НЕ реализовано — честно, проверено по `R.layout.*`)

0 ссылок в Java у: `list_item_documents` (документы), `notifications_item_comment/users`
(обратная связь), `board_topic_row` (обсуждения), `news_item_feedback`, `groups_invite_item`
(приглашения), `add_friend_alert`, `messages_search_suggestion` (поиск по чату),
`create_chat`/`chat_title_edit` (создание группового чата), `create_photo_album`, `send_photo`
(отправка фото), `widget_player`/`widget_player_big` (домашний виджет плеера: res/xml/
widget_player_provider.xml существует и ссылается на layout, но в манифесте НЕТ
AppWidget-provider receiver и класса AppWidgetProvider в коде → виджет не собран),
`photo_viewer_bottom`.
Пункты sliding menu «Feedback» (8) и «Bookmarks» (9) — закомментированы «Not implemented!»
(Global.java:453-467); массив `fave_tabs` (People/Posts/…) — orphaned.
**Вывод: документы/подарки/обсуждения/закладки/уведомления как фичи в OpenVK Legacy отсутствуют —
в PinoK они УЖЕ ЕСТЬ (DocumentsScreen, gifts-каталог+send, BoardTopicScreen, BookmarksScreen,
NotificationsScreen). Тянуть оттуда нечего.**

### 1.7. Что в PinoK уже есть (перекрыто, проверено rg по экранам)

лента+пост-кард+комментарии+репост (FeedScreen/PostDetailScreen, PollCard, ReactionPicker,
StoriesRow), диалоги+чат (MessagesScreen/ChatDetailScreen/ChatInfo, папки, unread-бейджи
BadgedBox в SovaNavHost), друзья с табом «Заявки» (FriendsScreen #43), сообщества
(CommunityScreen + board), фото (feature/photos: PhotosScreen с фото-альбомами photosGetAlbums,
PhotoViewer с сохранением ImageSaver), музыка (music/*: плеер, очередь, альбомы, артисты,
каталог, LyricsSheet — lyrics уже есть), видео (VideoScreen/VideoPlayerScreen c выбором
качества mp4_144…2160), клипы, документы, закладки, уведомления, поиск (SearchScreen),
настройки (приватность, чёрный список, скрытые источники ленты, VK ID, устройства),
офлайн-менеджер аудио/историй/клипов, звонки (WebRTC), изменение аватара (changeAvatar в
EditProfileScreen), accountSetOnline-пинг (SovaApp), accountGetCounters (ClipsCounter),
messages.createChat (VKA:985), audioGetLyrics (VKA:2737), is_explicit у аудио (Models.kt:298/551).

---

## §2. Кандидат-матрица

Пояснения: «VKA» = wire-метод в VKApiClient.kt PinoK (счётчик вхождений `"<метод>` из rg).
Effort: S ≤ 1 сессия, M — 1-3 сессии, L — много. Приоритет по ценности×фезибилити для PinoK.

| # | Кандидат | Что даёт PinoK | Wire (VK v5.x) | Есть в VKA | Приоритет | Effort |
|---|----------|----------------|----------------|------------|-----------|--------|
| 1 | **«О себе / Информация» в профиле** (interests, books, movies, tv, about, activities, quotes, city, bdate, sex, contacts) — референс AboutProfileLayout.java + layout_profile_about.xml | Секция «Информация» на UserProfileScreen (сейчас её нет — только статус и контент-табы) | `users.get` — просто расширить `fields` (VKA usersGet:183 сейчас: `photo_100,photo_200,online,last_seen,status,verified,counters`) | ✔ (метод есть; fields расширить) | **P1** | **S** |
| 2 | **Участники сообщества** — экран списка членов группы (референс GroupMembersActivity.java:64 `group.getMembers(wrapper,25,"")`) | Строка «Участники: N» → список с подпиской/отпиской, переход в профиль | `groups.getMembers` (extended=1, fields) | ✔ (wire есть, rg `groups.getMembers` = 1; UI-экрана нет — FollowList-паттерн переиспользуем) | **P1** | **S** |
| 3 | **Заметки (Notes)** — список + просмотр + создание/редактирование (референсы: NotesFragment.java, NoteViewerActivity.java:130/197/199, NotesListAdapter.java, Notes.java:48/58/64/117, list_item_note.xml) | Новый раздел «Заметки» (у VK-клиентов 2015-х это была ключевая фича; у PinoK отсутствует полностью) | `notes.get`, `notes.getById`, `notes.add`, `notes.edit`, `notes.delete`, (+`notes.getComments`, `notes.createComment`) | ✘ (rg `notes.` = **0** в VKA) | **P2** | **M** |
| 4 | **Офлайн-кэш ленты/стены (мгновенный старт + оффлайн-чтение)** — референс NewsfeedCacheDB/WallCacheDB + паттерн ProfilePageFragment.loadWallFromCache:580 → показать кэш → обновить | Постов в SQLite/Room (или DataStore-JSON): «мгновенная» лента, чтение в самолёте, дельта-обновление | client-side (API те же newsfeed.get/wall.get) | — (не нужен wire; в PinoK офлайн только аудио/истории/клипы) | **P2** | **M** |
| 5 | **Платформа публикации поста** («с Android/iPhone/моб. веба») — референс NewsfeedAdapter.java:222-230 | Малый UI-ньюанс в PostCard | `wall.get`/`newsfeed.get` поле `post_source.platform` | ◐ (wire есть; парсинг post_source в модели PostCard проверить — rg `post_source` в core/data: 0) | P3 | S |
| 6 | **Управление image-кэшем: качество картинок, размер, «Очистить»** — референс DownloadManager.clearCache:743/getCacheSize:770, dialog_imgcache_quality, префы loadImages/imageCacheQuality/forceCaching | Настройки для Coil 3: качество/размер disk-cache, кнопка очистки с показом размера | client-side | ◐ (Coil loader в SovaApp; настроек кэша нет) | P3 | S |
| 7 | **Домашний виджет плеера** — в OpenVK только заготовка (верстка widget_player*.xml + provider-xml, БЕЗ AppWidget-провайдера) | Glance/RemoteViews-виджет: трек, play/pause/next | client-side | ✘ (виджетов в PinoK нет) | P3 | M |
| 8 | **Глобальная лента** — референс Newsfeed.getGlobal (Newsfeed.java:32-38), переключатель в AppActivity | Второй режим ленты «Всё VK» | `newsfeed.getGlobal` | ✘ (rg = 0; ⚠ в реальном VK v5.x метод помечен deprecated — результат не гарантирован) | P3 | S |
| 9 | **Дни рождения друзей (напоминания)** — референс преф notifyBDays (preferences.xml) | Настройка + бейдж/строка вFriends, client-side сверка bdate | `friends.get(fields=bdate)` (wire есть) | ◐ (wire есть; логики нет) | P3 | S |
| 10 | **Мультиаккаунт (переключение аккаунтов)** — референс leftmenu_account, AccountAuthenticator/SecureCredentialsStorage | Слоты токенов + быстрый свитч | client-side (+ `account.getMulti` wire уже есть в VKA:16239) | ◐ (VKA-метод есть; хранилище — один слот, честное отклонение IMP-VKID) | P3 | L |
| 11 | **Обработка vk.com-ссылок (deep links)** — референс intents/*IntentActivity + Global.getUrlArguments:499 | Тап по vk.com/id123 / vk.com/club… извне → экран профиля/сообщества PinoK | client-side (screen_name → resolve через users.get/groups.getById) | ◐ (InternalBrowser есть; маршрутизации ссылок на экраны нет) | P3 | S |
| 12 | **Темы-пресеты (Blue/Gray/Black)** — референс uiTheme-переключатели в AppActivity:468 и фрагментах | Быстрые палитры поверх Material You | client-side | ◐ (themeDark/themeDynamic есть) | P3 | S |
| 13 | **Прокси (HTTP/SOCKS)** — референс dialog_proxy_settings, OvkAPIWrapper.setProxyConnection | Настройка прокси для сетевого слоя | client-side (OkHttp) | ✘ | P3 | S |
| 14 | **Сезонный сплэш** — референс activity_splash_xmas + преф startupSplash | Праздничный пасхальный экран | client-side | ✘ | P3 | S |
| 15 | **2FA-диалог при логине** — референс AuthActivity:393-413 (dialog_twofactor_auth, `authorize(..., code)`) | UX-референс для повторного входа с 2FA | контур VKID (у PinoK exchange-токен; пароль-логина в VK v5 нет) | — | P3 | — |

Отдельно: **НЕ кандидатами** (перекрыто или не нужно): опросы (PollCard+polls.addVote есть),
документы, подарки, обсуждения, закладки, уведомления, поиск, сохранение фото, lyrics,
качество видео, заявки в друзья, createChat, setOnline, репост-диалог, копирование сообщений,
«Пожаловаться» (P-7/IMP-FEED-1 уже сделано), скрытые источники ленты (FeedHiddenSourcesScreen).

---

## §3. ТОП-рекомендации к внедрению (ценность × фезибилити)

### Т1. Секция «Информация» в профиле пользователя — P1, S
- **Что:** блок «О себе» на UserProfileScreen/ProfileScreen: статус уже есть; добавить
  дату рождения, город, род занятий/интересы, любимая музыка/фильмы/ТВ/книги, цитата, «о себе».
- **VK API:** тот же `users.get` — добавить в `fields`: `bdate,city,country,sex,about,activities,interests,music,movies,tv,books,quotes,contacts` (всё живо в v5.x; город — объект {id,title}).
- **Референс OpenVK:** `app/src/main/java/uk/openvk/android/legacy/ui/views/AboutProfileLayout.java` (setStatus:71, setBirthdate:85, setInterests:89, setContacts:154, setRegistrationDate:157), `res/layout/layout_profile_about.xml`, fields-строка `modules/ovk-api/.../models/Users.java:128-131`.
- **PinoK-точки:** `VKApiClient.kt:183 usersGet` (расширить fields-строку аддитивно), `UserProfileScreen.kt` (новая приватная секция-карточка; честные empty-состояния, как в FollowersSubscriptionsScreen).

### Т2. «Участники сообщества» — P1, S
- **Что:** в CommunityScreen строка-счётчик участников становится кликабельной → список участников (аватар/имя/онлайн, тап → UserProfileScreen), pull-to-refresh, пагинация.
- **VK API:** `groups.getMembers` (group_id, count, offset, fields=photo_100,online,...) — wire в VKA уже есть (rg = 1), нужен только UI-экран/страница.
- **Референс OpenVK:** `core/activities/GroupMembersActivity.java` (вызов :64), `Groups.java getMembers`, `UsersListAdapter.java`, layout `list_item_user.xml`.
- **PinoK-точки:** переиспользовать паттерн FollowList (`Screen.FollowList` mode-followers) + новый mode="members"; маршрут в SovaNavHost рядом с FollowList.

### Т3. Раздел «Заметки» — P2, M
- **Что:** список заметок (тайтл, дата, превью-текст), просмотр, создание/редактирование (title+text), удаление. Опционально комментарии заметок.
- **VK API v5.x:** `notes.get` (user_id/owner_id, count, offset, sort), `notes.getById` (note_id, owner_id), `notes.add` (title, text), `notes.edit`, `notes.delete`; комментарии: `notes.getComments`/`notes.createComment`/`notes.deleteComment`. Все методы документированы и живы в v5.x (мобильные клиенты VK их использовали до удаления UI).
- **Референс OpenVK:** `models/Notes.java` (get :58, getById :64, edit :117, add :48), `core/fragments/NotesFragment.java` (табы/адаптер/пагинация), `core/activities/NoteViewerActivity.java` (просмотр XHTML через WebView :229-261; редактирование :197-199), `ui/list/adapters/NotesListAdapter.java`, `list_item_note.xml`, меню `notes_list.xml`/`note.xml`.
- **PinoK-точки:** 5-6 аддитивных VKA-методов (прецедент accountGetMulti/boardGetTopics: локальная data class + терпеливый парсинг + AppLog.e); экраны `NotesScreen` + `NoteDetailScreen` (+Route в Screen.kt/SovaNavHost); рендер текста — обычный Text с linebreaks (XHTML OpenVK не переносится; VK notes.getById отдаёт текст в API-формате).

### Т4. Мгновенный старт ленты: кэш последней страницы — P2, M
- **Что:** при получении ленты писать сырые items в локальное хранилище; при старте показывать кэш мгновенно + тихо обновлять (паттерн «loadFromCache → fetch → merge»); оффлайн-режим читает кэш.
- **VK API:** без изменений (newsfeed.get/wall.get).
- **Референс OpenVK:** `databases/NewsfeedCacheDB.java`/`WallCacheDB.java` (+ общий `databases/base/CacheDatabase`), паттерн `ProfilePageFragment.loadWallFromCache(ctx,...):580` → `WallCacheDB.putPosts(...):610`; сериализация вложений (WallPost.deserializeAttachments/convertSQLiteToEntity:309).
- **PinoK-точки:** SovaPrefs/DataStore-JSON или Room (решить по месту); затрагивает только FeedScreen/вью-модель, VKA не трогается.

### Т5. Мелкие UI-фичи из OpenVK (пул P3, каждая S)
1. **post_source.platform** в пост-карде («опубликовано с Android/iPhone/моб. версии») — поле приходит в wall.get/newsfeed.get; референс NewsfeedAdapter:222-230.
2. **Настройки image-кэша** (размер/очистка/качество) — референс DownloadManager:743/770 + dialog_imgcache_quality; для Coil 3 — ImageLoader.diskCache + settings-строки в SettingsScreen (стораж-таб).
3. **Дни рождения** — friends.get fields=bdate + настройка-тумблер (преф notifyBDays).
4. **vk.com deep links** — intent-filter + resolve screen_name (users.get/groups.getById) → маршрутизация; референс intents/*IntentActivity + Global.getUrlArguments:499.
5. **newsfeed.getGlobal** — только после проверки на реальном VK (метод в v5.x deprecated; если не отвечает — честная пустая вкладка; VKA +1 метод).
6. **Сезонный сплэш** — activity_splash_xmas как идея, копеечный пасхальный штрих.

### Порядок работ (предложение)
Т1 → Т2 (две дешёвые P1, чисто UI+fields) → Т3 (заметки, 1 VKA-волна + 2 экрана) →
Т4 (кэш ленты) → Т5 по остаточному принципу.

---

## §4. Что НЕ переносимо (и почему)

| OpenVK-фича | Почему нет |
|---|---|
| **NSFW-гейт постов** (`is_explicit` + `safeViewing`, PostViewLayout:201, NewsfeedAdapter:263) | Поле `is_explicit` у wall-постов — **OpenVK-специфичное**; реальный VK v5.x `wall.get/newsfeed.get` его не отдаёт. (У аудио PinoK `is_explicit` уже есть и отображается.) Сам UX-паттерн «заглушка-гейт» идею даёт, но данных в реальном API нет. |
| **Ovk.version / Ovk.aboutInstance** (статистика, админы, ссылки инстанса, dialog_about_instance) | Методы `Ovk.*` существуют только в OpenVK API. В реальном VK аналога нет. |
| **Мульти-инстансы** (выбор сервера-инстанса, InstancesListAdapter, instances.xml, selfeco-relay прокси minvk.ru) | Суть OpenVK (федерация инстансов). В PinoK хост фиксированный (api.vk.com). |
| **Авторизация grant_type=password на `/token`** (OvkAPIWrapper.authorize:263, 2FA-параметр `2fa_supported=1`) | В реальном VK такого wire нет; PinoK живёт в контуре VK ID/exchange. Идея «диалога кода 2FA» — только как UX-референс. |
| **Theora/OGV-видео + FFmpeg NDK-модули** (`files.ogv_480`, utils/media/Ovk*, build-ffmpeg) | Легаси-кодеки OpenVK; реальный VK отдаёт mp4/HLS — у PinoK уже есть лестница качеств и свой плеер. |
| **Поля User: `rating`, `is_dead`, `reg_date`** (User.java:63-69, Users.java:131) | OpenVK-специфичные (rating — валюта OpenVK). reg_date в реальном v5 users.get не отдаётся. |
| **XHTML-рендер заметок через WebView** | Формат OpenVK-заметок; в VK v5 notes текст приходит в API-формате — рендерить штатным Text. |
| **SlidingMenu/actionbar/popupmenu/twemojicon-модули** | Legacy View-библиотеки; в Compose эквиваленты есть/не нужны. |
| **Audio.getLyrics как фича** | Уже реализовано в PinoK (audioGetLyrics VKA:2737 + LyricsSheet) — тянуть нечего. |

---

## §5. Ограничения

1. **Лицензия: GNU AGPL-3.0** (COPYING; AGPL-заголовки в каждом файле, что я открывал, включая modules/ovk-api). Если бы код копировался дословно — PinoK стал бы производным произведением с обязательствами AGPL (открытие исходников, сохранение копирайтов OpenVK Team / Dmitry Tretyakov). Поэтому базовое правило этой выжимки: **только идеи/логика/UI-паттерны, чистая комната; имена API-методов и формат ответов VK — не объект авторского права**, но текст/структура Java-классов — да.
2. **Легаси-API 3.x vs реальный VK v5.x:** OpenVK пишет, например, `Messages.getConversationsById` и `Video.get` в 3.x-семантике, `Photos.get` без версионных фич; ответ OpenVK местами проще (photo sizes как индексный массив 0..10, `files.mp4_*`). Всё из §2 перепроверено на v5-совместимость методов, но **форматы полей** надо снимать по живым ответам реального VK (прецедент работы PinoK: «VKA-ответ парсится по живому формату, брифовский — фоллбэк»).
3. **Разные контуры:** OpenVK — self-hosted VK-совместимый сервер (нет VK ID, нет подарков/клипов/звонков/историй/рекламы/комьюнити-модерации). Часть «отсутствий» OpenVK — просто отсутствие фич у OpenVK, а не у VK; в §1.7 зафиксировано, что в PinoK уже шире, чем в OpenVK.
4. **OpenVK Legacy — Android View-система (XML layouts, RecyclerView/ListView, фрагменты)** — любой UI выносится в Kotlin/Compose только как паттерн (структура экрана, состояния, механики), не как код.
5. **Честные пробелы изучения:** читались все Java-пакеты выборочно-тотально (манифест, все активности/фрагменты — заголовки+ключевые методы; модели/обёртки — целевые файлы целиком), но не каждая строка 5 700 файлов; res-директории плотностей (drawable-hdpi и т.п.) не разбирались; содержимое `.git` не исследовалось; история коммитов не анализировалась. Если для внедрения понадобится точная логика конкретного файла — файл открыть точечно (пути даны в §2/§3).
