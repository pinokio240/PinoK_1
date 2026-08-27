# ARCHIVE_ANALYSIS_AND_WORKPLAN.md

> **Дата обновления:** 2026-08-06 (PinoK branch)
> **Источник:** глубокий анализ репозитория `pin24/VK_X_mod@PinoK` — 130+ Kotlin-файлов в `app/src/main/java/re/pinok/`, 9 корневых .md-планов, `VK_IMPORT_API.MD` (1.15 МБ / 19 460 строк / 51 «ЧАСТЬ»).
> **Цель:** единый актуальный документ-карта + план работ + план внедрения недостающих элементов.
> **Предыдущая версия:** 2026-08-04 (устарела на 2 дня активной разработки — за это время слита ветка `PinoK`, удалён WebView-механизм `recreate-on-dead-renderer`, добавлен `#SSO-RECREATE-GUARD`).

---

## 0. Исполнительная сводка

**Состояние:** PinoK — зрелый форк-клиент VK (re.pinok, applicationId=`re.pinok`, versionName=`2.0.0`, minSdk=24, targetSdk=36). Чистый Kotlin + Jetpack Compose (без XML UI), Material3, OkHttp (НЕ Retrofit), Media3/ExoPlayer, Coil 3, CameraX, Hilt НЕ используется (DI через `SovaApp` singleton), DataStore, EncryptedSharedPreferences.

**Главные активы:**
- `VKApiClient.kt` — god-class **12 436 строк**, ~150 public suspend-методов, покрывает весь открытый VK API + BFF-only методы (`shortVideo.*`, `catalog.*`, `settingsGeneral.*`, `cua.*`, `accountPersonal.*`).
- `auth/exchange/` — 8 файлов, 5 стратегий авторизации (password, 2FA, trusted_hash, external, exchange_token) + 5 fallback endpoints для silent_token + web_token flow через m.vk.ru JS. Клонировано с декомпилята VK 8.178.
- LongPoll v3 (mode=2, wait=25) с backfill через `getLongPollHistory`, обрабатывает 14 кодов событий.
- 27 composable destinations + 8 overlay-слоёв в `SovaNavHost`. Динамический drawer/dock с пользовательской перестановкой.
- 4 download-менеджера (Audio/Video/Story/Clip) + Offline Manager с 4 вкладками.

**Главные долги:**
1. `VKApiClient.kt` — god-class 12k строк, нужен split по модулям (Users/Wall/Messages/Photos/Video/Audio/...).
2. **Нет ViewModel-слоя** в 25 из 27 экранов. API-вызовы прямо из `LaunchedEffect` внутри `@Composable`. MVI реализован только в `ClipsViewModel`/`ClipCreateViewModel`.
3. **0 `@Stable`/`@Immutable`** во всей UI-базе → Compose compiler считает все composables unstable → избыточные рекомпозиции в больших списках.
4. `collectAsState` вместо `collectAsStateWithLifecycle` — нет lifecycle-awareness.
5. PinoK-стиль (`?.`/`?:`/`!!` avoidance) задокументирован в `CODING_STYLE.md`, но **повсеместно нарушен** в `VKApiClient.kt` (сотни multi-chain `?.`, десятки unsafe `as*` casts).
6. `ChatDetailScreen.kt` — **6 837 строк**, `SettingsScreen.kt` — **3 868 строк**. Кандидаты на декомпозицию.
7. `ChannelWebSocketClient.kt` — **полностью stub** (8 TODO), `SovaPrefs.msgWsChannels=false` по умолчанию.
8. Modern Messenger Sync API (`messages.getDiff`/`getItems`/`getConfig`) — НЕ реализован, используется 3-4 последовательных запроса вместо 1.
9. `ВК.txt` (113 KB) содержит **живые access_token** 5 разных appId —_security leak в репозитории.

---

## 1. Карта содержимого архивных файлов (9 шт. + 1 большой дамп)

| # | Файл | Строк | Тип | Назначение | Связь с VK_IMPORT_API.MD |
|---|------|-------|-----|------------|--------------------------|
| 1 | `CODING_STYLE.md` | 233 | Policy | Запрет `!!`, правила `?:`, smart-cast, `as?`, **критичное правило про nested `/*` в KDoc**. 6-пунктный чек-лист ревью. | §22, §13.5 |
| 2 | `OK_VIDEO_PLAN.md` | 291 | Plan | План OK.ru video playback в нативном ExoPlayer без рекламы. 7 этапов. | Расширяет §39 |
| 3 | `VK_ID_WEB_PLAN.MD` | 314 | Plan | План VK ID Web Account API (97 методов, 31 hash-роут, 34 sidebar testid). 6 спринтов (~7 недель). | Расширяет §49 |
| 4 | `STORY_VIDEO_CACHE_PLAN.md` | 403 | Plan | План кэширования story videos (24h TTL) с офлайн-просмотром. StoryVideoDownloadManager + Offline Manager. | Расширяет §17.2, §20.7, §18.1 |
| 5 | `MESSENGER_PLAN.MD` | 742 | Plan | План мессенджера (typing indicator, date separators, pin bar, folders, channel mode, bubble-less, Modern Sync API). | Расширяет §21, §22, §23, §26, §29, §35 |
| 6 | `EQUALIZER_INTEGRATION_PLAN.md` | 498 | Plan | План полного audio-эквалайзера (6 эффектов + spectrum + custom presets). 5 этапов. | Расширяет §12 |
| 7 | `FEED_RESEARCH.md` | 488 | Research | Карта ленты VK (m.vk.ru) — исследование архива `лента.zip`. 18 API namespaces, `audioUnmaskSource` deobfuscation, `OfflineAudioStorage` IndexedDB. | Расширяет §17, §20 |
| 8 | `OK_PLAYER_REVERSE.md` | 3395 | Research | Самый объёмный. Task ID `RESEARCH-JS-1` — реверс OK player + JS bundles VK. API Methods Catalog по 18 namespaces, apiPrefetchCache SSR, story video playback, audioUnmaskSource. | Расширяет §39, §42 |
| 9 | `vk_api_communities.md` | 205 | Research | Узкий справочник по groups.* — текущая реализация + 9 нереализованных методов. | Расширяет §1.4 |
| 10 | `ВК.txt` | 260 | Dump | Дамп localStorage VK Web (десктоп + мобильная версия). **Содержит живые токены 5 appId: 6287487, 7879029, 7913379, 52461373, 52649896.** Реальные `vk1.a.*` access_token, `videoplayer_auth_token`, sticker packs, ABR telemetry. | §41, §49 (auth) |
| 11 | `VK_VP_API.MD` | 673 | Research | Детальный реверс десктопного VK видеоплеера (`vk-vp-root`). 8 settings menu items + 7 context menu + CSS vars + keyboard shortcuts + SVG paths + Compose mapping. | Расширяет §9.2 |
| 12 | `VK_IMPORT_API.MD` | 19 460 | Master | **Главный research-лог.** 51 «ЧАСТЬ» + ~330 подсекций. API + UI + планы + Fix-история (#48/#49/#237/#254/#263/#285/#333/#335/#339/#340/#341). | — |

### Top-разделы `VK_IMPORT_API.MD` (51 часть)
1. API ЭНДПОИНТЫ (§1.1–1.18, 18 подсекций)
2. UI КОМПОНЕНТЫ VK (VKUI v5 → Compose mapping)
3. ЛЕВОЕ МЕНЮ (18 пунктов + submenu)
8. ГЛУБОКОЕ ИССЛЕДОВАНИЕ VKNEXT (расширения, VK Music Saver)
9. ДЕСКТОПНЫЙ ПРОФИЛЬ (vk.com DOM)
10/14. УВЕДОМЛЕНИЯ (mobile + desktop структура, 67 testid)
11. ОФФЛАЙН МЕНЕДЖЕР (audio/video/message cache)
12. ЭКВАЛАЙЗЕР (9 пресетов, 5 полос)
15. VKUI Group/InternalGroup — дерево классов + vkit-* hash mappings (38 шт.)
16. Дамп profile-страницы (104 fields users.get, 67 testid)
17. Дамп Лента.zip (newsfeed.getFeed ≠ newsfeed.get, stories.get real, 38 vkit-* mappings)
18. Bug-фиксы Fix #49 (story video, feed scroll, audio attachments)
21–23. MESSENGER (5 экранов, 52 testid, 242 CSS classes, 248 API methods, аудит VK_X_mod)
24. Web API Gateway (web.api.vk.ru)
25. Fix #112 — Session stability after long sleep
26. Sprint 4 — Folders/Channel/Bubble-less
27–28. Голосовые сообщения (30 CSS rules, waveform SVG, JS player, **баг docs.save parsing**)
29. Контекстное меню сообщений (cmid vs reply_to — критичный вердикт)
30. Форматы файлов VK (docs upload validation)
31. Настройки уведомлений — BFF-архитектура
32–34. Комментарии + notifications.get vs getRedesign
35. Modern Messenger Sync API (vk.me dump) — **getDiff/getItems/getConfig**, 96 Convo* blocks, 20 namespaces ~617 методов
36. CHAT SETTINGS SCREEN (ConvoProfile DOM, ACL, 5-layer avatar pipeline, **account.getTogglesExternal 100+ feature flags**)
37. VK Clips — полный анализ (CSS/JS/API/5 routes/testid)
38. apiPrefetchCache clip dump (`shortVideo.get` canonical ≠ `video.get`)
39. Внешние видеоплееры: OK.ru + Cross-Platform
40. Архитектура токенов: VTosters Lite + PinoK
41. VK ID AUTH SDK (3 auth flow, QR auth, silent_token flow, 20 SCOPES, 200+ feature flags, 23 подпункта Fix'ов)
42. VK Music Saver v2.10.1 + #PUSH-NOTIFICATIONS (LongPoll code 114 mapping, 9 channels, SnNotifyFilter 241 строка)
43–46. Bug-фиксы (#NET-SWITCH-DELAY, #KEEPALIVE-FORCE, #DNR-MARK-READ-UX, #REMOTE-INPUT, #URL-INTENT-FILTER)
47. PinoK как системный обработчик VK-ссылок
48. #VIDEO-BOARD-COMMENT (video.createComment + board.createComment)
49. VK ID WEB ACCOUNT (97 методов, 31 hash-роут, 6 namespace'ов)
50. Дополнение «Уведомления» (SnNotifyFilter, NotificationItem data class, Reply API routing)
51. NETWORK-RESILIENCE: Offline-first auth + ExponentialBackoff

---

## 2. Карта API

### 2.1. Transport и endpoint'ы

| URL pattern | HTTP | Назначение | Файл |
|---|---|---|---|
| `https://api.vk.com/method/<method>` | POST (FormBody) | Все VK API методы (Android gateway, UA `X-VK-Android-Client:new`, `sig` для messages.*/audio.*/execute) | `VKEndpoints.kt:98` |
| `https://web.api.vk.ru/method/<method>` | POST | Mobile-web gateway (без sig для vk1.a.* токенов; когда `SovaPrefs.netUseWebApiGateway=true`) | `VKEndpoints.kt:54,107` |
| `https://oauth.vk.com/authorize?client_id=...&response_type=token&scope=&state=sova2&revoke=1&display=mobile` | GET (WebView) | Implicit OAuth flow | `VKEndpoints.kt:78` |
| `https://oauth.vk.com/access_token` | POST (FormBody) | `grant_type=password|phone_confirmation_sid|without_password|trusted_hash|vk_external_auth|exchange_token|silent_token` (legacy endpoint) | `ExchangeAuthApi.kt:436` |
| `https://id.vk.com/auth_by_exchange_token` | POST | ONLY `grant_type=exchange_token` (refresh) — НЕ поддерживает password | `ExchangeAuthApi.kt:440` |
| `https://oauth.vk.com/auth_by_exchange_token` | POST (follow 302) | Path 4 — `grant_type=exchange_token` через legacy oauth (для client_id=2274003) | `ExchangeTokenExchanger.kt:142` |
| `https://api.vk.com/method/auth.getAuthData` / `auth.getAnonymToken` / `execute` | POST | silent_token exchange candidates #1/#2/#5 | `SilentTokenExchanger.kt:307,326,381` |
| `https://id.vk.com/auth_by_silent_token` | POST | silent_token exchange candidate #3 | `SilentTokenExchanger.kt:343` |
| `https://login.vk.com/?act=connect_exchange_token` | POST | refresh expired web_token через remixsid cookie | `WebTokenAuth.kt:786` |
| `https://vk.com/al_audio.php` (`act=reload_audio`) | POST (remixsid cookie) | Web fallback для аудио URL (когда audio.getById вернул masked URL) | `AlAudioFallback.kt:42` |
| `https://ok.ru/videoembed/<movieId>` | GET | HTML parsing OK video metadata (`data-options` JSON 6.5 KB) | `OkVideoRepository.kt:59` |
| `https://api.mycdn.me/dk?cmd=videoPlayerMetadata` | POST | OK mycdn fallback для приватных видео | `OkVideoRepository.kt:62` |
| LongPoll: `https://<server>?act=a_check&key=&ts=&wait=25&mode=2&version=3` | GET (long-poll, 45s timeout) | Real-time сообщения | `LongPollClient.kt` |
| `wss://api.vk.com/` или `wss://imp.vk.com/` | WS | VK channels transport — **STUB, НЕ активировано** | `ChannelWebSocketClient.kt:29,107` |

**Общий POST-FormBody для всех VK method-вызовов** (`VKApiClient.callInternal` ~8918+):
`<method args> + v=5.269 + https=1 + lang=ru + device_id=... + access_token=...`; для подписанных методов дополнительно `sig=<md5_hex>` (через `VkSigner.sign()`).

**VK credentials в BuildConfig** (`app/build.gradle.kts:22-50`):
- `VK_CLIENT_ID=2274003` (official VK Android)
- `VK_CLIENT_SECRET=hHbZxrka2uZ6jB1inYsH` (хардкод app_secret — норма для VK Android)
- `VK_API_VERSION=5.269`
- `VK_WEB_CLIENT_ID=6287487` (desktop web)
- `VK_WEB_MOBILE_CLIENT_ID=7879029` (m.vk.com, БЕЗ secret)
- `VK_OAUTH_HOST=oauth.vk.com`, `VK_ID_HOST=id.vk.com`

### 2.2. Auth-флоу (5 стратегий + 5 fallback endpoints для silent_token)

| HTTP endpoint | Kotlin функция | grant_type | file:line |
|---|---|---|---|
| `oauth.vk.com/access_token` | `authByPassword(phone,password,deviceId)` | `password` | `ExchangeAuthApi.kt:73` |
| `oauth.vk.com/access_token` | `authBy2FaCode(phone,sid,code,deviceId)` | `phone_confirmation_sid` | `:99` |
| `oauth.vk.com/access_token` | `authWithoutPassword(phone,sid,deviceId,...)` | `without_password` | `:127` |
| `oauth.vk.com/access_token` | `authByTrustedHash(phone,trustedHash,deviceId)` | `trusted_hash` | `:159` |
| `oauth.vk.com/access_token` | `authByExternalService(vkService,externalCode,...)` | `vk_external_auth` | `:185` |
| `oauth.vk.com/access_token` | `resendValidationCode(phone,password,sid,...)` | `password` (force_sms) | `:405` |
| `api.vk.com/method/execute` | `getExchangeTokenDetailed(accessToken)` | VKScript `API.auth.getExchangeToken` | `:234` |
| `id.vk.com/auth_by_exchange_token` | `authByExchangeToken(exchangeToken,deviceId,initiator,...)` | `exchange_token` | `:328` |
| `oauth.vk.com/auth_by_exchange_token` (Path 4) | `ExchangeTokenExchanger.exchange(...)` | `exchange_token` (follow 302) | `ExchangeTokenExchanger.kt:110` |
| 5 candidates (auth.getAuthData / auth.getAnonymToken / id.vk.com/auth_by_silent_token / oauth/access_token / execute) | `SilentTokenExchanger.exchange(silentToken,...)` | `silent_token` | `SilentTokenExchanger.kt:122` |
| `login.vk.com/?act=connect_exchange_token` | `WebTokenAuth.connectExchangeToken(remixsid,exchangeToken)` | cookie+form | `WebTokenAuth.kt:786` |
| m.vk.ru WebView (login.vk.com/?act=web_token) | `WebTokenAuth.fullAuthFlow(webView)` / `tryReadWebToken(webView)` | VK ID SDK JS | `:227,600` |
| `api.vk.com/method/account.getProfileInfo` | `validateWebToken(webToken)` | — | `ExchangeAuthApi.kt:362` |

**Initiator enum** (`ExchangeAuthApi.kt:508`): `NO_INITIATOR`, `EXPIRED_TOKEN`, `ADD_EDU_PROFILE`, `AUTHORIZATION`, `SILENT_AUTHORIZATION`, `WEB_HANDLER_AUTHORIZATION`.

**Auth State machine** (`AuthModels.kt:28`): `Idle | Loading | NeedValidation | Error(kind,message) | Success(result) | OfflineWithCache(cachedUserId,lastSeenMs,tokenExpiredAt)`.

### 2.3. VK API methods (каталог по модулям — ~150 методов)

> Все вызовы идут через private `call("vk.method.name", args)` в `VKApiClient.kt`. Ниже — выборка по модулям. Полный каталог — в `VK_IMPORT_API.MD §1.1–1.18, §17.4, §19.3, §35.7, §36.7, §37.4, §49.2`.

#### Users / Friends
`users.get` (104 fields), `users.getFollowers`, `users.search`, `users.getContentTabs`, `users.getWallTabs`, `friends.get`, `friends.getRequests`, `friends.getOnline`, `friends.getCounters`, `friends.add`, `friends.delete`.

#### Wall / Newsfeed / Likes
`newsfeed.get`, `newsfeed.getFeed` (20 фильтров — НЕ newsfeed.get), `newsfeed.search`, `newsfeed.ignoreItem`/`unignoreItem`, `newsfeed.addBan`/`unban`, `newsfeed.unsubscribe`/`subscribe`, `newsfeed.banUser`, `wall.get`/`getById`/`post`/`edit`/`delete`/`restore`/`pin`/`unpin`/`repost`/`createComment`/`getComments`/`editComment`/`deleteComment`/`restoreComment`/`subscribe`/`unsubscribe`, `likes.add`/`delete`/`isLiked`.

#### Messages (40+ методов)
`messages.getConversations` (+ `UnreadCount` / `ConversationRequests`), `messages.getConversationsById`, `messages.getConversationMembers`, `messages.getHistory` (+ `WithProfiles`), `messages.getHistoryAttachments`, `messages.send` (текст/аудио/видео/пост/attachment/sticker/sticker-as-image/voice), `messages.edit`, `messages.forward`, `messages.react`, `messages.delete` (по msgId и по cmid), `messages.deleteConversation`, `messages.restore`, `messages.markAsRead`, `messages.setActivity` (typing), `messages.markAsAnswered`, `messages.markAsSpam`, `messages.search`, `messages.getChatFolders`, `messages.getLastActivity`, `messages.pin`/`unpin`, `messages.markAsImportantConversation`/`markAsUnreadConversation`, `messages.setConversationPushSettings`, `messages.createChat`, `messages.editChat`, `messages.setChatPhoto`, `messages.getChat`, `messages.addChatUser`, `messages.removeChatUser`, `messages.getLongPollServer`, `messages.getLongPollHistory`, `messages.allowMessagesFromGroup`/`denyMessagesFromGroup`.
**НЕ реализовано (Modern Sync API):** `messages.getDiff`, `messages.getItems`, `messages.getConfig`, `messages.searchConversations`, `messages.searchConversationMembers`. См. §35.1.

#### Photos / Video / Audio / Docs
- **Photos** (12+): `photos.getAlbums`, `photos.get`, `photos.getById`, `photos.createComment`, `photos.like`, `photos.getWallUploadServer`, `photos.getMessagesUploadServer`, `photos.saveMessagesPhoto`, `photos.saveWallPhoto`, `photos.getChatUploadServer` + composite upload-функции.
- **Video** (20+): `video.get`/`getById`/`getClipById`/`getComments`/`createComment`/`add`/`delete`/`deleteClip`/`edit`/`save`/`uploadFile`/`getPlayerConfig`/`addViewingHistoryRecord`/`getLongPollServer`/`getAds`/`trackAdEvent`, `reportVideo` (execute), `search.getClips`, `shortVideo.getRecom`/`shortVideo.get` (canonical clip method), `clipsDislike`/`clipsRemoveDislike` (через storage.set execute).
- **Audio** (40+): `audio.get`/`getById`/`getByIdBatch`/`getSnippets`/`search`/`searchPlaylists`/`searchArtists`/`searchAlbums`/`getLyrics`/`getPlaylists`/`getPlaylistById`/`getPlaylistTracks`/`getRecommendations`/`getAudiosByArtist`/`getArtistsById`/`getRelatedArtistsById`/`getAudioIdsBySource`/`getIdsBySource`/`add`/`delete`/`restore`/`reorder`/`addDislike`/`removeDislike`/`createPlaylist`/`editPlaylist`/`deletePlaylist`/`addToPlaylist`/`removeFromPlaylist`/`followPlaylist`/`savePlaylistAsCopy`/`followArtist`/`unfollowArtist`/`followRadioStation`/`unfollowRadioStation`/`radioGetById`/`getSearchSuggestions`. **Catalog:** `catalog.getAudio`/`getSection`/`getBlockItems`/`getAudioArtist`/`getAudioSearch`/`getSearchAll`/`getSearchTop`/`hideBlock`.
- **Docs** (8): `docs.get`/`getTags`/`getTypes`/`getMessagesUploadServer`/`save`/`getWallUploadServer` + composite `uploadDocForMessage`/`uploadDocForComment`/`uploadAndSendDoc`/`docsUploadVoice`.

#### Stories
`stories.get`, `stories.view` (fire-and-forget через `callPublic`).
**НЕ реализовано:** `stories.getById`, `getReplies`, `getViewers`, `getArchive`, `getDiscover`, `getStats`, `markSeen`, `banOwner`, `hideReply`, `getPhotoUploadServer`, `getVideoUploadServer`.

#### Groups / Communities / Board
`groups.get`/`getById`/`search`/`getMembers`/`join`/`leave`/`edit` (notifications), `board.getTopics`/`getComments`/`createComment`.

#### Search / Bookmarks / Notifications / Account
- `search.getHints`, `search.getClips`.
- `fave.get`/`getTagList`/`add`/`remove`/`addPage`/`removePage`.
- `notifications.get`/`getRedesign`/`getUnreadCounters`/`markAsRead`/`markAsViewed`.
- `account.getCounters`/`getToggles`/`setOnline`/`ban`/`unban`/`getBanned`/`setSilentMode`/`getSilentModeStatus`/`startSilentMode`/`stopSilentMode`/`setObsceneFilter`/`getSecurityAlerts`/`setSafetyNetEnabled`/`getActivityHistoryDevices`/`resetSessions`/`resetAllSessions`/`getSessionInfoForReset`.

#### CUA (Confirm User Action) — sprint VK-ID-1
`cua.getValidationMethods`, `cua.sendPhoneCode`/`sendEmailCode`/`sendPushCode`/`sendPhoneBindCode`, `cua.checkPhoneCode`/`checkEmailCode`/`checkPushCode`/`checkPhoneBindCode`.

#### Settings (BFF m.vk.ru settingsGeneral namespace)
`settingsGeneral.getNotifySettings(page)`, `settingsGeneral.setNotifySettings(key,value)`, `settingsGeneral.toggleNotify(key,value)`, `settingsGeneral.getAccountSettings(page)`, `settingsGeneral.setAccountSettings(key,value)`, `settings.startChangeNotifyEmail`, `settings.performEmailBannerAction`.

#### Store / Gifts / Stickers / Polls
`store.getProducts` (stickers × 2 — packs и catalog), `gifts.getCatalog`/`send`, `polls.addVote`, `apps.allowNotifications`/`denyNotifications`/`readAllNotifications`.

#### Execute / VKScript
`execute(script)`, `executeGetConversationsBatch(peerIds)`, `reportVideo(...)` (`API.video.report`), `clipsDislike`/`clipsRemoveDislike` (`API.storage.set`).

### 2.4. LongPoll events (v3, mode=2, wait=25)

| code | описание | эмитированный `LongPollEvent` |
|---|---|---|
| 1 | заменить флаги сообщения | `DialogUpdate(peerId)` + `UnreadCountersChanged` |
| 2 | установить флаги (mask) | то же |
| 3 | сбросить флаги (mask=1 → прочитано) | то же |
| 4 | новое сообщение | `NewMessage(peerId, messageId, flags, text, ts, cmid?, randomId?, fromId?, attachType?, replyMessagePreview?, fwdCount, action?, actionText?)` |
| 5 | редактирование | `EditMessage(peerId, messageId)` |
| 6/7 | прочитано inbox/outbox | `ReadInbox`/`ReadOutbox(peerId, upTo)` |
| 8/9 | юзер online/offline | `UserOnline`/`UserOffline(userId)` |
| 12/13 | диалог изменён | `DialogUpdate(peerId)` |
| 51/52 | unread counters changed (chat) | `UnreadCountersChanged` |
| 61 | DM typing | `Typing(userId, flags, isChat=false, peerId=userId)` |
| 62 | chat typing | `Typing(userId, chatId, isChat=true, peerId=chatId+2_000_000_000)` |
| 80 | unread counters changed (общее) | `UnreadCountersChanged` |
| 114 | $new_unread_count (уведомления) | `NotificationsCountChanged` → `NotificationsPoller.triggerImmediatePoll()` |

Backfill через `messages.getLongPollHistory(pts,ts)` — обрабатывает `history: [[code, ...]]` тем же `handleEvent`. На `failed=1/2/3/4` — recovery через `getLongPollServer`.

### 2.5. WebSocket events (`ChannelWebSocketClient.kt`) — STUB

⚠️ **СТАТУС: ЗАГОТОВКА, НЕ ИНТЕГРИРОВАНО.** Feature-flag `SovaPrefs.msgWsChannels=false`. Все методы содержат TODO. LongPoll остаётся каноничным.

Запланированный протокол: `wss://api.vk.com/` (или `wss://imp.vk.com/`), auth frame `{"key":"<access_token>"}` или query `?access_token=...`, subscribe `{"action":"subscribe","channel":"im_<peer_id>"}`, heartbeat каждые 25с `{"action":"ping"}`, reconnect exponential backoff 1→2→4→30с. Event types: `new_message`/`edit_message`/`read_inbox`/`typing` (маппинг на `LongPollEvent`).

---

## 3. Карта типов (data classes)

### 3.1. `data/model/Models.kt` (60+ классов, основные)

`UserProfile` (с `LastSeen`, `City`, `Country`, `Counters`, `Cover`, `Personal`), `Post` (с `Likes`, `Reposts`, `Views`, `Comments`, `Reactions`, `Copyright`, `Donut`), `Attachment` (с `Photo`+`Size`, `Link`, `Doc`+`AudioMsg`, `Video`, `Audio`, `Wall`, `Sticker`, `Poll`, `AudioPlaylist`), `VideoPlatform` enum (VK/OK/YOUTUBE/INSTAGRAM/EXTERNAL_IFRAME/UNKNOWN), `Video`, `Track`, `TrackArtist`, `AudioPlaylist`, `Chat` (с `Peer`, `CanWrite`, `SortId`), `ChatAcl`, `ChatPermissions`, `ChatFolder`, `Message` (с `MessageReaction`, `RecentReaction`), `Comment` (с `CommentThread`), `PhotoStandalone`, `BoardTopic`, `BoardComment`, `EqualizerPreset`, `LogEntry` (+`Level`), `PlayerState`, `DownloadStatus` enum, `FailReason` enum, `DownloadState`, `Friend`, `Group`, `Album`, `PhotoItem`, `FaveTag`, `Bookmark` (полиморфная), `Poll` (+`Answer`), `Article`, `DocFile` (+`Preview`), `SearchHint`, `StickerAttachment`, `StickerImage`, `StickerPack`, `StickerItem`, `GiftItem`, `Story` (+`StoryPhoto`/`StoryVideo`/`StoryLink`/`StoryReplies`), `StoryGroup`, `CatalogViewType` enum, `CatalogBlock`, `CatalogPlaylist`, `ArtistRef`, `AudioArtist`, `AudioRadioStation`, `AudioCatalogBlock`, `AudioCatalogItem` (sealed: TrackItem/PlaylistItem/ArtistItem/RadioItem), `AudioCatalogSection`, `PlaylistDetails`, `AudioSearchResult`, `AudioDislikeStatus`, `SettingsSection`, `SettingsParam`, `SettingsParamOption`, `SilentModeStatus`, `BannedUsersList`, `BannedUser`.

### 3.2. `data/model/VkAccountModels.kt` (Sprint VK-ID-1)
`DeviceSession`, `DeviceType` enum (MOBILE/DESKTOP/TABLET/UNKNOWN), `CuaMethod` enum (SMS/PUSH/EMAIL/PHONE_BIND), `CuaValidationMethod`, `CuaValidationMethods`, `CuaSendResult`, `CuaCheckResult`, `CuaAction` object (RESET_SESSIONS/RESET_ALL_SESSIONS/CHANGE_PASSWORD/CHANGE_EMAIL/CHANGE_PHONE/DISABLE_OTP), `classifyDeviceType(name, appName): DeviceType`.

### 3.3. `auth/exchange/AuthModels.kt`
`AuthState` (sealed: Idle/Loading/NeedValidation/Error/Success/OfflineWithCache), `ValidationType` enum (10 каналов 2FA), `AuthErrorKind` enum (12 категорий), `ExchangeTokenResult` (sealed: Success/TokenInvalid/Unavailable), `AuthResult` (20+ полей), `VkAuthCredentials`, `BanInfo`, `ValidateInfo`, `SendOtpInfo`, `UtilityTokens` (+`UtilityToken`), `LongPollCredentials`.

### 3.4. LongPoll events (`realtime/LongPollClient.kt:1240+`)
`LongPollEvent` (sealed): `NewMessage`, `EditMessage`, `ReadInbox`/`ReadOutbox`, `UserOnline`/`UserOffline`, `Typing`, `DialogUpdate`, `UnreadCountersChanged` (object), `NotificationsCountChanged` (object), `Reset` (object).

### 3.5. Inline data classes (внутри `VKApiClient.kt`)
`NewsfeedResult`, `GroupInfo`, `LongPollServer`, `LongPollHistory`, `ClipsFeedResult`, `VideoUploadTicket`, `WallByIdResult`, `HistoryResult`, `NewsfeedBannedResult`, `WallCommentsResult`, `NotificationItem`, `ContentTab`, `WallTab`, `ChatMember`, `MessageSearchResult`, `HistoryAttachment`, `LastActivity`, `UploadedPhoto`, `FaveTag`.

### 3.6. OkVideoRepository (`api/OkVideoRepository.kt`)
`OkVideoMetadata` (movieId, title, duration, posterUrl, hlsManifestUrl, videos: List<OkQuality>, collageUrl, showAd), `OkQuality` (key, label, url, width, height), `CacheEntry`.

---

## 4. Карта меню / подменю / кнопок

### 4.1. Dock (нижняя панель, 5 дефолтных + расширяемые)
| Пункт | Иконка | Action | Badge |
|---|---|---|---|
| Лента | `Icons.Default.Dashboard` | `nav.navigate("feed")` | — |
| Сообщения | `Icons.Default.Email` | `nav.navigate("messages")` | **`UnreadMessagesCounter.unreadCount`** (99+ cap) |
| Музыка | `Icons.Default.LibraryMusic` | `nav.navigate("music")` | — |
| Видео | `Icons.Default.PlayCircle` | `nav.navigate("video")` | — |
| Профиль | `Icons.Default.Person` | `nav.navigate("profile")` | — |
| (доп. до 5+ при настройке) | (см. drawer) | nav.navigate(route) | Clips badge (если Clips) |

Все dock items: `popUpTo(nav.graph.startDestinationId) { saveState = true } + launchSingleTop = true + restoreState = true`. Если >5 кнопок → горизонтально-скроллируемый Row (80dp each, `BottomNavScrollButton`).

### 4.2. Drawer (боковое меню, динамическое через `PanelEditorTab`)
| Пункт | Иконка | Action | Особое |
|---|---|---|---|
| **Header:** «PinoK» + `Icons.AutoMirrored.Outlined.MenuOpen` (collapse) | — | `drawerState.close()` | RTL-safe |
| Друзья | `Icons.Default.Group` | nav.navigate("friends") | скрываемый/переставляемый |
| Сообщества | `Icons.Outlined.Groups` | nav.navigate("groups") | то же |
| Фото | `Icons.Outlined.Image` | nav.navigate("photos") | то же |
| Поиск | `Icons.Default.Search` | nav.navigate("search") | то же |
| Закладки | `Icons.Outlined.Bookmark` | nav.navigate("bookmarks") | то же |
| Документы | `Icons.Outlined.Description` | nav.navigate("documents") | то же |
| Клипы | `Icons.Outlined.VideoLibrary` | nav.navigate("clips") + `ClipsCounter.reset()` | **Badge** clips count >0 |
| Сервисы | `Icons.Default.Apps` | nav.navigate("services") | то же |
| Уведомления | `Icons.Default.AlternateEmail` | nav.navigate("notifications") | то же |
| Логи | `Icons.Default.BugReport` | nav.navigate("logs") | то же |
| Эквалайзер | `Icons.Filled.Equalizer` | nav.navigate("equalizer") | то же |
| **Fixed tail (не редактируется):** | | | |
| Офлайн | `Icons.Outlined.CloudOff` | nav.navigate("offline_manager") | — |
| Настройки | `Icons.Default.Settings` | nav.navigate("settings") | — |
| Выйти из приложения | `Icons.Outlined.PowerSettingsNew` | `showExitAppDialog = true` | AlertDialog confirm |

Ширина drawer динамическая (200–320dp), измеряется через `TextMeasurer` по самому длинному пункту.

### 4.3. TopBar actions по экранам (27 destinations)

| Экран | TopBar тип | Actions |
|---|---|---|
| Feed | Глобальный (hamburger) | `Icons.Default.Menu` → `drawerState.open()` |
| Messages | Глобальный + ScreenTopBar | Search toggle (`Icons.Filled.Search`/`Close`), tabs (Все/Каналы/Непрочитанные/Folders) |
| Music | Глобальный | Tabs: Главная / Моя музыка / Обзор. Inline search в «Моя музыка» |
| Video | Глобальный + ScreenTopBar | Search toggle |
| Profile | hasOwnTopBar (без AppBar) | — |
| Friends | Глобальный + ScreenTopBar | Search toggle + `FilterChip` (Все/Онлайн/Запросы) |
| Groups | Глобальный + ScreenTopBar | Search toggle |
| Photos | Глобальный | Album list → AlbumPhotosView (back inline) |
| Search | Глобальный | Inline OutlinedTextField + `TabChip` (Подсказки/Люди/Сообщества/Новости) |
| Bookmarks | Глобальный | `TagFilterChip` FlowRow |
| Documents | Глобальный | — |
| Clips | hidesGlobalTopBarOnly | Overlay: ← / 🔊 / + (create clip) |
| ClipCreate | Свой UI (CameraX stages) | CameraStage / ReviewStage / PublishStage / DoneStage |
| Services | Глобальный | — |
| Notifications | Глобальный + ScreenTopBar | Mark-all-read (`Icons.Outlined.Visibility`), Search toggle, Filter toggle (`Icons.Outlined.FilterList`), FlowRow filter chips |
| Settings | Глобальный | 15 вкладок PrimaryScrollableTabRow |
| NotificationSettings | hasOwnTopBar | ← + ⋮ (overflow) |
| Devices | hasOwnTopBar | ← + refresh (`Icons.Outlined.Refresh`) |
| About | Глобальный | — |
| Logs | hasOwnTopBar | ← close, share, clear, refresh |
| StoryViewer | hasOwnTopBar (hidden) | Close (`Icons.Default.Close`), Download (для video stories) |
| OfflineManager | hasOwnTopBar | ← + scan menu (audio/video/stories/clips) + TabRow |
| OfflineAudioPlayer | hasOwnTopBar | ← + currentTrackCard controls (prev/play/next/shuffle/repeat) |
| Equalizer | hasOwnTopBar | ← + master Switch + 5 табов + FAB «Сохранить пресет» |
| StoryOfflinePlayer | hasOwnTopBar | ← |
| ClipOfflinePlayer | hasOwnTopBar | ← |
| Community | hasOwnTopBar (inline header) | ← + avatar + name + verified + type + members + status + description + Subscribe button + Tabs (Записи/Фото/Видео/Музыка/Обсуждения) |
| BoardTopic | hasOwnTopBar | ← + title |
| UserProfile | hasOwnTopBar | ← + Message (`Icons.Outlined.Email`) + Add/Delete friend |
| PostDetail | hasOwnTopBar | ← + like + comment scroll + repost |
| ChatDetail (норм) | hasOwnTopBar | ← + аватар+имя+online/typing (кликабельно) + muted icon + ⋮ menu |
| ChatDetail (selection) | hasOwnTopBar | ← close + «Выбрано: N» + Forward + Delete |
| ChatInfo | hasOwnTopBar | ← + ⋮ (ChatActionsDropdown, 8-9 пунктов conditional) |
| FoldersSettings | hasOwnTopBar | ← + add (`Icons.Filled.Add`) |
| InternalBrowser | hasOwnTopBar | ← (WebView goBack fallback) + reload + ⋮ (Открыть внешне / Поделиться / Копировать) |
| AudioPlayer | hasOwnTopBar | ← + ⋮ (Add to my music, Bookmark, Copy link) + ⏭ + ⏪ + speed menu + queue + EQ + repeat |
| AudioQueue | hasOwnTopBar | ← + shuffle + repeat |
| VideoPlayer | hasOwnTopBar | ← + download toggle (`Icons.Filled.Download`/`DownloadDone`/CircularProgress) |

### 4.4. Context menus (long-press)

| Объект | Меню | Иконки действий |
|---|---|---|
| Post (FeedScreen PostCard) | DropdownMenu через `IconButton(Icons.Outlined.MoreHoriz)` | Bookmark (`Icons.Outlined.Bookmark`/`BookmarkBorder`), Edit (`Icons.Outlined.Edit`, только если canEdit), Pin/Unpin (`Icons.Outlined.PushPin`/`VisibilityOff`, только если canPin), Hide from feed (`Icons.Outlined.VisibilityOff`), Ban author (`Icons.Outlined.ContentCopy`), Delete (`Icons.Outlined.Delete`, красный, только если canDelete) |
| Chat (MessagesScreen ChatCard) | long-press DropdownMenu | Pin/Unpin (`Icons.Filled.PushPin`/`Outlined.PushPin`), Mute/Unmute (`Icons.Outlined.Notifications`/`NotificationsOff`), Mark read/unread (`Icons.Outlined.MarkChatUnread`), Delete (`Icons.Outlined.Delete`, красный, AlertDialog confirm) |
| Message (ChatDetailScreen MessageBubble) | combinedClickable.onLongClick DropdownMenu | Reply, Edit, Forward, Copy, Delete, Pin/Unpin, Mark answered, Restore, React (→ `ReactionPicker`) |
| Notification (NotificationsScreen) | combinedClickable.onLongClick | Прочитать, Удалить |
| Bookmark (BookmarksScreen) | combinedClickable.onLongClick | Remove (faveRemove, AlertDialog confirm) |
| Group (GroupsScreen GroupRow) | long-press | Optimistic leave/join (без меню) |
| Friend (FriendsScreen FriendRow) | `FilledTonalIconButton` | Delete+re-add |
| Track (MusicScreen VKTrackRow) | `AudioMoreMenu` через `IconButton(Icons.Outlined.MoreVert)` | Add/Delete/Restore/Share/Bookmark/CopyLink/ShowLyrics/ShowRecommendations/Dislike/OpenAlbum/SetNext/Edit |
| Clip (ClipsFeedScreen) | `ClipMoreActionsSheet` через «more» ActionButton | Subscribe/Unsubscribe/Favorite/Report/Ban/CopyLink/ToggleNotifications/HideAuthor/Edit/Delete |

### 4.5. Bottom sheets / Dialogs

**Bottom sheets (10):** `CommentsBottomSheet` (Feed), `ShareSheet` (component), `ForwardDialog`, `RepostDialog`, `AttachmentPickerSheet` (3 таба: Music/Video/Gifts), `LyricsSheet` (audio), `EQ sheet` (упрощённый: presets + 5 bands), `CuaVerifySheet` (devices), `ClipShareSheet`/`ClipCommentsSheet`/`ClipMoreActionsSheet`.

**Dialogs (18):** AlertDialog logout/exit-app (SovaNavHost), `CaptchaDialog`, `CreatePostDialog`, `RepostDialog`, rename chat (ChatDetail), members (ChatDetail), ban/leave confirm (ChatDetail), session expired (Fix #137), delete chat confirm (Messages), delete bookmark confirm (Bookmarks), terminate all (Devices), save/delete preset (Equalizer), FolderEditDialog (FoldersSettings), add-to-playlist/create-playlist (AudioPlayer), clear dead tracks (Settings), download-all confirm (Settings).

### 4.6. Global overlays (вне NavHost)
- `VideoHolder.active` → `VideoPlatformRouter` (overlay над любым экраном, для сохранения позиции скролла)
- `PhotoHolder.active` → `PhotoViewer` (Dialog)
- `CaptchaDialog()` — подписан на `app.captchaHandler.challenge`
- `NetworkSwitchPopup` — модальный popup при переключении сети
- `OfflineBanner` — persistent баннер (BottomCenter, padding 80dp)
- `DraggableLogFab` — перетаскиваемый FAB (`SmallFloatingActionButton` с BugReport icon)
- `LogViewerDialog()` — подписан на `LogDialogState.visible`
- `ShareToChatSheet` — когда есть `pendingShareText`/`Uri` (ACTION_SEND)

### 4.7. Deep-link patterns (`VkUrlDeepLinker.DeepLinkAction`)
`OpenPost(ownerId, postId, commentId)`, `OpenVideo(ownerId, videoId)`, `OpenUser(userId)`, `OpenCommunity(groupId)`, `OpenPhoto(ownerId, photoId, photoUrl?)`, `OpenNotifications`, `OpenDevices`. Push chat-open → `Screen.ChatDetail.buildRoute(peerId, title, photo)`.

### 4.8. In-memory Holders (синглтоны для preservation при навигации)
`VideoHolder`, `PostHolder`, `PhotoHolder`, `PostDetailTarget` (commentId), `FeedScrollHolder`, `PostDetailScrollHolder`, `FeedDataHolder` (cache ленты), `StoriesHolder`, `LogDialogState`. **Не переживают process death** (только `rememberSaveable` для cameraReturn* в SovaNavHost).

---

## 5. Карта CSS / JS / языков

### 5.1. JS-скрипты в Kotlin-коде (4 штуки)
**ТОЛЬКО** в `ui/screens/videoplayer/OkWebViewPlayer.kt`:
| Функция | Назначение | Размер |
|---|---|---|
| `extractVideoUrlAndDownload` (стр. 185-225) | Извлекает `<video>.currentSrc/src` или `<source>.src` для скачивания | ~10 строк JS |
| `toggleWebViewPlayback` (стр. 349-364) | `v.play()` или `v.pause()` для PiP RemoteAction | ~4 строки |
| JS-polling (стр. 388-403) | Каждую 1сек: `document.querySelector('video').paused` → 'paused'/'playing'/'none' для `VideoPipController.setIsPlaying` | 1 строка inline |
| `injectAdmanStub` (стр. 869-939) | JS-stub для блокировки OK рекламы: `window.AdmanHTML` class stub, `flashvars.isAdvertismentsSwitchOffForced='1'`, localStorage hacks (`@vpl-flags`, `_vp_lastDayAdvShown=999`, `_vp_lastVideoQualityName='hd'`, `deviceId=''`, `tracer-device-id=''`) | ~70 строк |

### 5.2. CSS в Kotlin-коде
**НЕТ.** CSS-строки не найдены ни в одном .kt-файле.

### 5.3. `app/src/main/res/` — полный список
```
res/
├── drawable/
│   ├── ic_launcher_foreground.xml   (вектор — буква «S», 108dp, белый)
│   └── ic_notification.xml          (вектор — chat bubble, 24dp, tinted white)
├── mipmap-anydpi-v26/
│   ├── ic_launcher.xml              (adaptive-icon: bg + fg + monochrome)
│   └── ic_launcher_round.xml        (adaptive-icon round)
├── values/
│   ├── colors.xml                   (5 colors: sova_bg/sova_bg_dark/sova_fg/sova_fg_dark/ic_launcher_background=#FF000000)
│   ├── strings.xml                  (121 строка, RU: app_name="PinoK", nav_*, auth_*, locker_*, feed_*, messages_*, music_*, *_download_channel_*, settings_*, cache_*, mod_* (DNR/DNT/undelete/unedit/offline/device_mask/antitelemetry/ssl_pinning/away_bypass/ad_block), about_*)
│   └── themes.xml                   (3 styles: Theme.PinoK (Material.Light.NoActionBar), Theme.PinoK.Splash, Theme.PinoK.Silent — transparent для AuthActivity silent re-login)
└── xml/
    ├── backup_rules.xml             (exclude sova_secure_prefs.xml + datastore/)
    ├── data_extraction_rules.xml    (cloud-backup + device-transfer: exclude sova_secure_prefs.xml + datastore/)
    ├── file_paths.xml               (FileProvider paths: cache, files, external_files, external_cache)
    └── network_security_config.xml  (cleartextTrafficPermitted=false + VK domains: vk.com, vkontakte.ru, userapi.com, vk.me, mycdn.me, vk-cdn.net, id.vk.com, oauth.vk.com — trust system+user certs)
```

### 5.4. `app/src/main/assets/`
**НЕ существует.** Все ресурсы встроены в код или в `res/`.

### 5.5. Reference CSS/JS (НЕ в APK, в `reference/`)
- `reference/vkcom-kit.6ab210dc6e50d1b6.css` — основной VK UI kit CSS (для reverse-engineering vkit-* mappings).
- `reference/vk_web_audio/js/` (11 файлов): `audio_config_*.js`, `AudioCatalog.js`, `audio_player_bottom.js`, `audio.fd1671935f0feb93.js`, `audio_postingPlayer.js`, `audio_player_mini.js`, `mvk-left-menu-player.js`, `audio_onMediaAttachmentPlayer.js`, `audio_catalog.js`.
- `reference/vk_web_audio/css/` (3 файла): `audio.db7b1cc29056918a5c6f.css`, `audio_player_bottom.377af502482d42ef0dbb.css`, `audio_player_mini.8b49f486b7129b7d4f4c.css`.
- `reference/vk_web_audio/pages/` (2 HTML): «музыка Обзор.html», «Главная музыка.html».
- `reference/vk_web_localstorage_dump.txt`.
- `reference/equalizer/` — apktool+jadx sources декомпилированного `Equalizer v6.3.5.7` (для `EQUALIZER_INTEGRATION_PLAN.md`).
- `reference/VKID_SDK_ANALYSIS.md`, `reference/VK_WEB_COMPOSER_ANALYSIS.md`, `reference/vk_web_audio/ANALYSIS.md`.

### 5.6. `decompiled-auth-extract/` — 14 .java файлов (1434 строк)
Jadx-декомпиляция VK SDK OAuth/auth классов из official VK APK. **НЕ компилируются и НЕ используются в рантайме** PinoK — служат reference для `auth/exchange/`.

Ключевые файлы:
- `AuthByExchangeToken.java` (170 строк) — подтверждает эндпоинт `https://{host}/auth_by_exchange_token` для обмена exchange_token на access_token. Параметры: `client_id, exchange_token, scope="all", initiator, validate_session, silent_auth_by_login=1`.
- `VkAuthState.java` (203 строки) — все grant_type: `vk_external_auth` / `password` / `phone_confirmation_sid` / `trusted_hash` / `without_password`.
- `VKScope.java` (91 строка) — enum OAuth-скоупов (ADS, AUDIO, DOCS, EMAIL, FRIENDS, GROUPS, MARKET, MESSAGES, ...).
- `VkOAuthService.java` (101 строка) — 11 внешних OAuth-провайдеров (MAILRU, GOOGLE, OK, VK, PASSKEY, ESIA, SBER, YANDEX, TINKOFF, ALFA, VTB).
- `AuthResult.java` (280 строк) — Parcelable модель auth result (access_token, user_id, utility tokens, personal data, ban info, validate info).
- `UtilityToken.java` / `UtilityTokens.java` — utility tokens для mini-apps.

### 5.7. Языки в проекте
- **Kotlin** — основной язык (все `.kt` в `app/src/main/java/re/pinok/`). Чистый Compose UI, без XML.
- **XML** — только ресурсы (`res/`) + AndroidManifest.xml + proguard-rules.pro.
- **JavaScript** — встроенные строки в `OkWebViewPlayer.kt` (4 скрипта).
- **ProGuard** — `app/proguard-rules.pro`.
- **Gradle Kotlin DSL** — `build.gradle.kts` (root + app), `settings.gradle.kts`, `libs.versions.toml`.
- **HTML/CSS** — только в `reference/` (reverse-engineering reference, НЕ в APK).

---

## 6. Gap Analysis (задокументировано, но НЕ реализовано)

### 6.1. Modern Messenger Sync API (P0, критично)
**Документация:** `VK_IMPORT_API.MD §35.1, §36.9, §35.11 P0-1`; `MESSENGER_PLAN.MD P4`.
**Что не реализовано:** `messages.getDiff` (initial sync: folders+counters+credentials+profiles одним запросом), `messages.getItems` (cursor-based pagination), `messages.getConfig` (version 18), `messages.searchConversations`, `messages.searchConversationMembers`.
**Эффект:** −30% запросов при старте, folders сразу доступны, cursor-based пагинация вместо offset.
**Сложность:** Высокая (новый API контракт, новый LongPoll URL `?version=21&mode=1226`).

### 6.2. Push-уведомления через LongPoll code 114 (P0)
**Документация:** `VK_IMPORT_API.MD §10.5, §42 (#42.1–42.10), §50`.
**Что не реализовано:**
- 9 notification channels (messages/bg_keepalive/downloads/media_playback/comments/reposts/likes/mentions/security_alerts) — частично есть `vk_security_alerts`, остальные как system notifications.
- `SnNotifyFilter` (client-side `sn_*` filter, 241 строка spec) — НЕ реализован.
- `pushPerUserMuted` (CSV per-user mute) — НЕ реализован.
- Reply via RemoteInput → `ReplyResultNotifier` — частично реализовано.
- BigPictureStyle для всех типов с фото/видео — НЕ везде.
- Per-category channel → SovaPrefs key mapping (12+ новых) — частично.
- `NotificationsPoller` lifecycle — частично.

### 6.3. VK ID Auth SDK features (P1)
**Документация:** `VK_IMPORT_API.MD §41, §41.13–41.23`.
**Что не реализовано:**
- OAuth 2.1 PKCE flow (с `code_verifier`/`code_challenge`).
- QR auth (`vk_app_sign_in` / `SCAN_QR`).
- 20 SCOPES — не формируется корректно (только `scope="all"`).
- 200+ feature flags из `auth.js` — не обрабатываются.
- Passkey (WebAuthn) — не реализован (см. §49.6).
- Open Fixes: `#NO-SILENT-MEANS`, `#NULL-SAFE-HELPER`, `#FORCE-REFRESH`, `#RELOGIN-FORCE`, `#REMIXSID-CAPTURE`, `#DOUBLE-FLICKER`, `#EXCHANGE-IP-MISMATCH`, `#SILENT-REFRESH-ORIGIN-MULTI`.

### 6.4. VK ID Web Account API (P1)
**Документация:** `VK_ID_WEB_PLAN.MD`, `VK_IMPORT_API.MD §49`.
**Что не реализовано из 97 методов:**
- `accountPersonal.getMainData`, `getUserProfileInfo`, `saveProfileInfo`, `validateProfileInfo`, `getSettings`, `getRecommendations`, `hideRecommendation`, `getAllUserLinks`, `getAppScopes`, `getSubscriptions`, `getWidgetsQueue`, `getCanChangePhone`, `actualizePhone`, `markActualizePhone`, `changeEmail`, `changeNotifyEmail`, `startChangeEmail`, `startChangeNotifyEmail`, `checkPassword`, `deactivate`/`deactivateLink`/`reactivate`, `disableLoginViaMax`, `startEnableLoginViaMax`, `payAddCardBinds`, `payDeleteCardBinds`, `payGetElectronicFunds`, `payTopupVKBalance`, `payGetOperationsHistory`, `touchQuestionnaire`.
- `settings.getSecurityInfo`, `changeSecuritySettings`, `changePasswordStart`, `doChangePassword`, `changePhone`/`Cancel`/`Force`, `enableOTPByApp`, `enableOTPBySMS`, `disableOTPByApp`, `disableOTPBySMS`, `getOTPbyAppSecret`, `getReserveCodes`, `getAppPasswords`, `deleteAppPassword`, `webauthnRegisterBegin`, `webauthnRegisterFinish`, `webauthnRemoveCredential`, `webauthnUpdateDevice`, `unregisterValidateDevice`, `getSessionInfoForReset`, `resetEmailSoftBouncing`, `performEmailBannerAction`.
- `vkProtect.appeals`/`checkAppeals`/`generatePassword`/`getAppealsUploadAttachmentServer`/`attachPhotoToAppeal`/`detachPhotoFromAppeal`/`getAppealAttachments`.
- `multiaccount.childSignup`/`Delete`/`Edit`/`Restore`/`validateChildProfileInfo`/`getChildProfileSettings`/`setChildProfileSettings`/`checkRelatedUserPinCode`/`setRelatedUserPinCode`.
- `accountVerification.getUserInfo`/`linkWithVerify` (esia/tinkoff/sber/alfa/vtb)/`deleteVerification`.
- 31 hash-роут для навигации (`#/main`, `#/personal`, `#/security`, `#/sessions`, `#/password`, `#/2fa`, `#/passkeys`, `#/email`, `#/phone`, `#/safety-net`, `#/deactivate`, `#/data-privacy`, `#/login-via-max`, `#/links`, `#/subscriptions`, `#/widgets`, `#/recommendations`, `#/multiaccount`, `#/child-signup`, `#/vk-protect`, `#/verification`, `#/esia`, `#/tinkoff`, `#/sber`, `#/alfa`, `#/vtb`, `#/pay`, `#/pay-cards`, `#/pay-history`, `#/questionnaire`, `#/logout`).

### 6.5. Stories API (P2)
**Документация:** `VK_IMPORT_API.MD §1.8, §17.4, §36.7`.
**Что не реализовано:** `stories.getById`, `getReplies`, `getViewers`, `getArchive`, `getDiscover`, `getStats`, `getDetailedStats`, `getQuestions`, `search`, `save`, `delete`, `markSeen`, `markSkipped`, `markNotInterested`, `banOwner`, `unbanOwner`, `hideReply`, `hideAllReplies`, `hidePrivacyBlock`, `setDiscoverVisible`, `askQuestion`, `deleteQuestion`, `banQuestionAuthor`, `unbanQuestionAuthor`, `seenReplies`, `getPhotoUploadServer`, `getVideoUploadServer`, `getReactionsAssets`.

### 6.6. Audio Effects Engine (P2)
**Документация:** `EQUALIZER_INTEGRATION_PLAN.md`, `VK_IMPORT_API.MD §12`.
**Что не реализовано:**
- Замена `EqualizerHelper` на `AudioEffectsEngine` (6 эффектов): Equalizer (legacy API 9+) + BassBoost + Virtualizer + LoudnessEnhancer + PresetReverb + DynamicsProcessing (API 28+).
- Spectrum visualizer (Canvas) — есть заглушка `SpectrumVisualizer`.
- Custom presets Room DB — есть `CustomPresetStore` (DataStore-based), без Room.
- Auto-apply per audio device — НЕ реализовано.
- Global Mix (session 0) — НЕ реализовано.
- Foreground service для EQ — НЕ реализовано.
- Per-band on/off — НЕ реализовано.
- L/R balance — НЕ реализовано.

### 6.7. Offline Manager (P2)
**Документация:** `VK_IMPORT_API.MD §11`, `STORY_VIDEO_CACHE_PLAN.md`, `§19.7`.
**Что реализовано:** `TrackDownloadManager` (audio), `VideoDownloadManager`, `StoryVideoDownloadManager` (24h TTL), `ClipVideoDownloadManager` (7d TTL), `OfflineManagerScreen` (4 вкладки), `OfflineAudioPlayerScreen`, `StoryOfflinePlayerScreen`, `ClipOfflinePlayerScreen`.
**Что не реализовано:** `OfflineVideoManager` с HLS segments + merge (сейчас `VideoDownloadManager` только progressive MP4), `MessageCacheManager` (cacheConversation/getCachedMessages/cacheAttachments).

### 6.8. OK.ru Video Player (P3)
**Документация:** `OK_VIDEO_PLAN.md`, `VK_IMPORT_API.MD §39`.
**Что реализовано:** `OkWebViewPlayer` (WebView-based с ad-blocking через `injectAdmanStub`), `OkVideoRepository` (HTML parsing `data-options` JSON + `api.mycdn.me` fallback).
**Что не реализовано:** нативный ExoPlayer для OK (без WebView), DASH MPD support, Adman SDK ad-blocking в нативе, P2P WebRTC STUN `videostun.okcdn.ru:19302`, Fix #341 (HEVC filter в нативном плеере).

### 6.9. Context Menu for Messages (cmid vs reply_to) (P1)
**Документация:** `VK_IMPORT_API.MD §29`.
**Реализовано:** Long-press context menu в `ChatDetailScreen` (Reply/Edit/Forward/Copy/Delete/Pin/Mark answered/React).
**Проблема:** Используется `reply_to` (устарел), а не `cmid` (conversation_message_id). VK в 2026 использует cmid как первичный идентификатор. Нужно мигрировать на cmid-based API (`messages.send`/`edit`/`delete` с `cmid` параметрами).

### 6.10. Network Resilience (P1)
**Документация:** `VK_IMPORT_API.MD §51, §25, §43, §44`.
**Что реализовано:** `ExponentialBackoff` класс (есть), `AuthState.OfflineWithCache`, `LongPollKeepAliveService` (foreground, type=`remoteMessaging`), `#NET-SWITCH-DELAY` (TOKEN_INVALIDATION_PAUSE_MS 30s→4s), `#KEEPALIVE-FORCE`, `#DUAL-TOKEN` (remixsid+web_token), `#ATTACH-SUPPRESS-WINDOW` (60s→120s), `msgLpBackfill`.
**Что не реализовано:** Offline-guard в `ensureFreshToken(force)` — частично; P2 items из §51.9.

### 6.11. WebSocket transport (P4)
**Документация:** `VK_IMPORT_API.MD §23.9, §36.8`.
**Реализовано:** `ChannelWebSocketClient.kt` — 8 TODO, НЕ активирован (`SovaPrefs.msgWsChannels=false`).
**Что нужно:** Реализовать handshake + auth + heartbeat + subscribe/unsubscribe + event mapping на `LongPollEvent`. Endpoints: `wss://api.vk.com/` (или `wss://imp.vk.com/`).

### 6.12. Video Player controls parity (P2)
**Документация:** `VK_VP_API.MD` (673 строки).
**Реализовано:** `VideoPlayerScreen` — ← back, download toggle, VKTimeline (seek bar), VKControlButton (play/pause/seek), VKSettingsPopup (quality/speed), VideoActionBar (like/comment/share/repost/more).
**Что не реализовано из 8 settings menu items:** `audio-language-settings` (звуковая дорожка), `subtitles-settings` (субтитры), `traffic-saving-settings` (экономия данных), `report` (сообщить о проблеме), `copy-data`, `debug-info`. Из 7 context menu items: `copy-link-timestamp`, `copy-embed-code`, `video-loop`, `rotate`. Keyboard shortcuts (§4.12). Big Play Button center feedback. Double-tap forward/rewind. Timeline preview track (horizontal). Z-Index layers.

### 6.13. Compose Stability (P2 — техдолг)
**Проблема:** 0 `@Stable`/`@Immutable` во всей UI-базе. ~150 topLevel composables без аннотаций стабильности → Compose compiler считает все unstable → избыточные рекомпозиции в больших списках (FeedScreen LazyColumn с PostCard, ChatDetail LazyColumn с MessageBubble).
**Решение:** Добавить `@Immutable` к data classes (`ReactionEntry`, `PendingPhoto`, `PendingFileAttachment`, `AttachmentSelectionState`, `ClipCreateUiState`, `ClipsViewModel.UiState`, `ScrollPosition`). Добавить `@Stable` к topLevel composables с unstable params. Мигрировать `collectAsState` → `collectAsStateWithLifecycle`.

---

## 7. Undocumented (реализовано, но НЕ в doc)

- `shortVideo.getRecom` (section, count, pageAnchor) — реализован в `VKApiClient.kt:11797`, НЕ упомянут явно в `VK_IMPORT_API.MD` (упоминается косвенно в §38.1 через `shortVideo.get`).
- `clipsDislike` / `clipsRemoveDislike` — эмуляция через `storage.set` execute. Реализовано, не задокументировано как BFF-метод.
- `reportVideo` — `execute` с `API.video.report`. Реализовано, не задокументировано.
- `users.subscribe` / `users.unsubscribe` — реализованы через `friends.add` / `friends.delete` (хак). Реализовано, не задокументировано.
- `preloadStickerToCache` — local cache warming, не VK API.
- `AudioUrlUnmasker` — клиентская де-обфускация аудио URL (порт JS из VK Music Saver v2.10.1). 4 transforms: v=reverse, r=caesar, s=BigInt-XOR-seeded permutation, x=XOR-char.
- `AlAudioFallback` — web fallback через `vk.com/al_audio.php?act=reload_audio` (remixsid cookie, не access_token).
- `GeniusLyricsFetcher` — интеграция с Genius API для текстов песен.
- `Mp4TagWriter` — ID3 v2.4 frames для скачанных аудио.
- `ZipExporter` — экспорт плейлиста в ZIP.
- `SirenTranscoder` — ffmpeg-kit-audio декодер VK Siren (G.722.1) → AAC.
- `HevcSupport` — детекция HEVC поддержки для video downloads.
- `VkUserAgent` — динамическая генерация VK Android UA (формат идентичен `VK.app`).
- `Linkify` — кастомный linkifier для VK URLs.
- `NetworkSwitchState` (sealed: Idle/Switching/Refreshing/Failed/Offline) — state machine для network transitions.
- `PinnedConversationsRepository` — local pinned order (JSON в SovaPrefs), drag-swap.
- `FoldersRepository` — local folders config (SovaPrefs.msgFoldersData).
- `SovaPrefs` — 100+ ключей настроек (DataStore), включая `sidebarItemsOrder`/`Hidden`, `bottomBarItemsOrder`/`Hidden`, `interfaceAnimSpeed`, `themeAccentIndex`, `msgDnr`/`Dnt`/`undelete`/`unedit`/`typing`/`pinBar`/`grouping`/`dateSeparators`/`unreadDivider`/`scrollFab`/`multiSelect`/`swipeReply`/`readReceipts`/`bubbleless`/`search`/`folders`, `audioFormat`/`convertMethod`, `videoQuality`, `offlineMode`/`deviceMask`/`antitelemetry`/`hideLastSeen`, `locker*`, `equalizer*`, `push*`, `netUseWebApiGateway`, `msgWsChannels`, `msgLpBackfill`, и др.

---

## 8. План работ (Sprint plan)

### Sprint 0 (1–2 дня) — Стабилизация и техдолг
**Цель:** Устранить критичные риски перед новым функционалом.

| # | Задача | Файлы | Оценка |
|---|--------|-------|--------|
| 0.1 | **Безопасность:** удалить `ВК.txt` из репозитория (живые токены), добавить в `.gitignore`. Уведомить пользователя о необходимости revoke всех 5 токенов. | `ВК.txt`, `.gitignore` | 0.5д |
| 0.2 | **Compose Stability P0:** добавить `@Immutable` к `ReactionEntry`, `PendingPhoto`, `PendingFileAttachment`, `AttachmentSelectionState`, `ClipCreateUiState`, `ClipsViewModel.UiState`, `ScrollPosition`. | `FeedScreen.kt`, `ChatDetailScreen.kt`, `PendingPhotosBar.kt`, `ClipCreateViewModel.kt`, `ClipsViewModel.kt`, `SovaNavHost.kt` | 0.5д |
| 0.3 | **Lifecycle-aware:** мигрировать `collectAsState` → `collectAsStateWithLifecycle` в 5 критичных экранах (FeedScreen, MessagesScreen, ChatDetailScreen, NotificationsScreen, SettingsScreen). | 5 файлов | 0.5д |
| 0.4 | **PinoK-стиль:** убрать `?: 0`/`?: ""` "ублажить компилятор" в `VKApiClient.kt` (top 50 нарушений по grep). Заменить на `if (x != null)` или `requireNotNull`. | `VKApiClient.kt` | 1д |
| 0.5 | **Безопасные JSON-casts:** мигрировать `asJsonObject`/`asJsonArray` → `getObj`/`getArr` (введены в `:9645-9732` для clips) в `parsePost`/`parseVideo`/`parseAttachments`/`parsePushSettings` (`VKApiClient.kt:180,263,274,299`). | `VKApiClient.kt` | 1д |

### Sprint 1 (1 неделя) — Modern Sync API + Push notifications
**Цель:** Уменьшить количество запросов при старте + полноценные push-уведомления.

| # | Задача | Файлы | Оценка |
|---|--------|-------|--------|
| 1.1 | Реализовать `messages.getDiff` в `VKApiClient.kt` (новый suspend-метод). Парсинг `MessagesDiffResponse(folders, counters, profiles, credentials, serverVersion, invalidateAll)`. | `VKApiClient.kt` | 1д |
| 1.2 | Реализовать `messages.getItems(cursor)` + `messages.getConfig` (version 18). | `VKApiClient.kt` | 0.5д |
| 1.3 | Интегрировать Modern Sync в `MessagesScreen` (замена 3-4 запросов на 1 `getDiff`). | `MessagesScreen.kt` | 1д |
| 1.4 | LongPoll URL migration: `?version=21&mode=1226` (сейчас `version=3&mode=2`). Обработка новых codes `[10]/[11]/[12]` (RESET/REPLACE/SET_DIRECTORIES для folders). | `LongPollClient.kt` | 1д |
| 1.5 | `SnNotifyFilter` (241 строка spec) — client-side `sn_*` filter для notifications. | новый `realtime/SnNotifyFilter.kt` | 1д |
| 1.6 | `pushPerUserMuted` (CSV per-user mute) в `SovaPrefs`. | `SovaPrefs.kt` | 0.5д |
| 1.7 | BigPictureStyle для всех notification types с фото/видео. Per-category channel → SovaPrefs key mapping (12+ новых). | `realtime/MessageNotifier.kt`, `NotificationsPoller.kt` | 1.5д |

### Sprint 2 (1 неделя) — Auth & Security
**Цель:** Устойчивая авторизация + VK ID Web Account (sessions/devices/2FA).

| # | Задача | Файлы | Оценка |
|---|--------|-------|--------|
| 2.1 | OAuth 2.1 PKCE flow: `code_verifier`/`code_challenge` generation, `OAuthWebViewActivity` update. | `auth/OAuthWebViewActivity.kt` | 1д |
| 2.2 | 20 SCOPES — формирование корректного scope string (сейчас `scope="all"`). | `auth/exchange/ExchangeAuthApi.kt` | 0.5д |
| 2.3 | QR auth (`vk_app_sign_in` / `SCAN_QR`) — новый flow в `AuthActivity`. | `auth/AuthActivity.kt` | 2д |
| 2.4 | `accountPersonal.getMainData` / `getUserProfileInfo` / `saveProfileInfo` / `validateProfileInfo` — sprint VK-ID-2.1. | `VKApiClient.kt`, новый `ui/screens/account/AccountScreen.kt` | 1.5д |
| 2.5 | `settings.changePasswordStart` / `doChangePassword` / `checkPassword` — Change Password screen. | новый `ui/screens/account/ChangePasswordScreen.kt` | 1д |
| 2.6 | `settings.enableOTPByApp` / `enableOTPBySMS` / `disableOTPByApp` / `disableOTPBySMS` / `getOTPbyAppSecret` / `getReserveCodes` — 2FA Management screen. | новый `ui/screens/account/TwoFactorScreen.kt` | 1.5д |
| 2.7 | `settings.webauthnRegisterBegin` / `webauthnRegisterFinish` / `webauthnRemoveCredential` / `webauthnUpdateDevice` — Passkey (WebAuthn) screen. | новый `ui/screens/account/PasskeysScreen.kt` | 2д |

### Sprint 3 (1 неделя) — Messenger UX
**Цель:** Parity с VK web messenger.

| # | Задача | Файлы | Оценка |
|---|--------|-------|--------|
| 3.1 | **cmid migration:** заменить `reply_to` на `cmid` в `messages.send`/`edit`/`delete`/`forward`. Обновить `MessageBubble` context menu. | `VKApiClient.kt`, `ChatDetailScreen.kt` | 1.5д |
| 3.2 | Folders system: `messages.addChatFolder`/`editChatFolder`/`deleteChatFolder`/`reorderChatFolders`. LP events 10/11/12. | `VKApiClient.kt`, `FoldersSettingsScreen.kt` | 1.5д |
| 3.3 | Channel mode UX: отдельный layout для каналов (`vkme_*` testid parity). 6 пунктов more-menu (mark_read/pin/archive/unmute_notifications/report/leave). | `ChatDetailScreen.kt`, `ChatInfoScreen.kt` | 1д |
| 3.4 | Bubble-less layout: flat layout сообщений `ConvoMessageWithoutBubble__attachments` (для последовательных сообщений от одного автора). | `ChatDetailScreen.kt` | 1д |
| 3.5 | Typing indicator в chat list (сейчас только в ChatDetail). Date separators (сейчас есть). Pin bar (сейчас есть). | `MessagesScreen.kt` | 1д |
| 3.6 | `messages.searchConversations` + `searchConversationMembers` — поиск по диалогам и участникам. | `VKApiClient.kt`, `MessagesScreen.kt` | 1д |

### Sprint 4 (1 неделя) — Stories + Clips parity
**Цель:** Полноценные Stories и Clips.

| # | Задача | Файлы | Оценка |
|---|--------|-------|--------|
| 4.1 | `stories.markSeen` / `markSkipped` / `markNotInterested` / `banOwner` / `hideReply` — интерактивные действия. | `VKApiClient.kt`, `StoryViewerScreen.kt` | 1д |
| 4.2 | `stories.getReplies` / `getViewers` / `getStats` — replies и viewers count. | `VKApiClient.kt`, `StoryViewerScreen.kt` | 1.5д |
| 4.3 | `stories.askQuestion` / `deleteQuestion` / `banQuestionAuthor` — story questions. | `VKApiClient.kt`, `StoryViewerScreen.kt` | 1д |
| 4.4 | `stories.getPhotoUploadServer` / `getVideoUploadServer` — создание stories. | `VKApiClient.kt`, новый `ui/screens/stories/StoryCreateScreen.kt` | 2д |
| 4.5 | Clips: `video.getLiveStatus` / `liveHeartbeat` / `liveSubscribe` / `liveGetSpectators` — live streams support. | `VKApiClient.kt`, `ClipsViewModel.kt` | 1.5д |
| 4.6 | Clips: `video.notInterested` / `notRecommendOwner` — feed tuning. | `VKApiClient.kt`, `ClipsViewModel.kt` | 0.5д |

### Sprint 5 (1 неделя) — Video Player parity + OK.ru native
**Цель:** Полноценный видеоплеер + нативный OK.

| # | Задача | Файлы | Оценка |
|---|--------|-------|--------|
| 5.1 | Video settings menu: `audio-language-settings`, `subtitles-settings`, `traffic-saving-settings`. | `VideoPlayerScreen.kt` | 1.5д |
| 5.2 | Video context menu: `copy-link-timestamp`, `copy-embed-code`, `video-loop`, `rotate`. | `VideoPlayerScreen.kt` | 1д |
| 5.3 | Big Play Button center feedback. Double-tap forward/rewind (±10s). Timeline preview track (horizontal). | `VideoPlayerScreen.kt` | 1.5д |
| 5.4 | Keyboard shortcuts (§4.12 VK_VP_API.MD): Space=play/pause, ←/→=±5s, J/L=±10s, K=play/pause, F=fullscreen, M=mute, ↑/↓=volume, 0-9=seek to 10%-100%. | `VideoPlayerScreen.kt` | 1д |
| 5.5 | Native OK.ru ExoPlayer (без WebView): DASH MPD support, Adman ad-blocking в нативе. | новый `ui/screens/videoplayer/OkNativePlayer.kt`, `OkVideoRepository.kt` | 2д |
| 5.6 | Fix #341: HEVC filter в нативном плеере (`HevcSupport`). | `VideoPlayerScreen.kt` | 0.5д |

### Sprint 6 (1 неделя) — Audio Effects Engine + Offline
**Цель:** Полный эквалайзер + offline parity.

| # | Задача | Файлы | Оценка |
|---|--------|-------|--------|
| 6.1 | `AudioEffectsEngine` (замена `EqualizerHelper`): BassBoost + Virtualizer + LoudnessEnhancer + PresetReverb + DynamicsProcessing (API 28+). | новый `media/AudioEffectsEngine.kt` | 2д |
| 6.2 | Spectrum visualizer (Canvas, real FFT). | `ui/components/SpectrumVisualizer.kt` | 1д |
| 6.3 | Custom presets Room DB (миграция с DataStore). Auto-apply per audio device. Global Mix (session 0). | `media/CustomPresetStore.kt`, новый `media/EqualizerDatabase.kt` | 1.5д |
| 6.4 | Foreground service для EQ. Per-band on/off. L/R balance. | `media/AudioEffectsEngine.kt`, `service/EqualizerService.kt` | 1.5д |
| 6.5 | `OfflineVideoManager` с HLS segments + merge (сейчас только progressive MP4). | `media/VideoDownloadManager.kt` | 2д |
| 6.6 | `MessageCacheManager` (cacheConversation/getCachedMessages/cacheAttachments). | новый `media/MessageCacheManager.kt` | 2д |

### Sprint 7 (1 неделя) — Архитектурный рефакторинг
**Цель:** Устранить god-class, ввести ViewModel-слой.

| # | Задача | Файлы | Оценка |
|---|--------|-------|--------|
| 7.1 | Split `VKApiClient.kt` (12 436 строк) на модули: `UsersApi`, `WallApi`, `MessagesApi`, `PhotosApi`, `VideoApi`, `AudioApi`, `StoriesApi`, `GroupsApi`, `SearchApi`, `FaveApi`, `NotificationsApi`, `AccountApi`, `DocsApi`, `StoreApi`, `PollsApi`, `CatalogApi`, `ClipsApi`, `ExecuteApi`. Сохранить `VKApiClient` как facade. | `api/` (16+ новых файлов) | 3д |
| 7.2 | Ввести ViewModel-слой для топ-5 экранов: `FeedViewModel`, `MessagesViewModel`, `ChatDetailViewModel`, `MusicViewModel`, `NotificationsViewModel`. MVI с sealed `Intent` + `State` + `Effect`. | 5 новых ViewModel + 5 экранов refactor | 3д |
| 7.3 | Split `ChatDetailScreen.kt` (6 837 строк): вынести `MessageBubble`, `ForwardedMessageBlock`, `VoiceRecordingToolbar`/`VoiceReviewToolbar`/`VoiceMessageBubble`, attachments (`WallAttachmentCard`/`VideoAttachmentCard`/`LinkAttachmentCard`/`DocAttachmentCard`/`AudioAttachmentRow`/`GiftAttachmentCard`/`GraffitiAttachmentCard`/`PollAttachmentRow`/`MapAttachmentCard`), `EmojiStickerPanel`, `PinnedMessageBar`, `ChannelFooterBar`, `PendingFilesBar` в отдельные файлы. | `ui/screens/im/` (10+ новых файлов) | 2д |
| 7.4 | Split `SettingsScreen.kt` (3 868 строк): вынести 15 вкладок в отдельные файлы `InterfaceTab.kt`, `NewsTab.kt`, `MessagesTab.kt`, `MusicTab.kt`, `OfflineTab.kt`, `VideoTab.kt`, `EqualizerTab.kt`, `NetworkTab.kt`, `PrivacyTab.kt`, `SecurityTab.kt`, `LoggingTab.kt`, `NotificationsTab.kt`, `CacheTab.kt`. | `ui/screens/settings/` (13 новых файлов) | 1д |
| 7.5 | `MainActivity.kt` (1 585 строк): вынести auth/clipboard/network логику в `AuthBootstrap`/`ClipboardHelper`/`NetworkBootstrap` repositories. | `ui/MainActivity.kt` + 3 новых файла | 1д |

---

## 9. План внедрения недостающих элементов (детальный, с файлами)

### 9.1. Modern Messenger Sync API (Sprint 1)

**Новые файлы:**
- `app/src/main/java/re/pinok/api/MessagesSyncApi.kt` — suspend `getDiff(lpVersion, conversationsLimit, extendedFilters, supportedTypes, counterFilters, fields)`, `getItems(cursor)`, `getConfig()`.
- `app/src/main/java/re/pinok/data/model/MessagesDiff.kt` — `MessagesDiffResponse(folders, counters, profiles, credentials, serverVersion, invalidateAll)`, `ConversationCursor`, `MessagesConfig`.

**Изменяемые файлы:**
- `app/src/main/java/re/pinok/api/VKApiClient.kt` — добавить делегирование в `MessagesSyncApi` (или напрямую методы, если не split'аем).
- `app/src/main/java/re/pinok/realtime/LongPollClient.kt` — мигрировать URL на `?version=21&mode=1226`, добавить обработку codes `[10]/[11]/[12]` (RESET/REPLACE/SET_DIRECTORIES для folders events).
- `app/src/main/java/re/pinok/ui/screens/im/MessagesScreen.kt` — заменить `messagesGetConversations` + `messagesGetUnreadCount` + `messagesGetChatFolders` + `messagesGetLongPollServer` на единый `getDiff` при первом запуске.
- `app/src/main/java/re/pinok/data/local/SovaPrefs.kt` — добавить `msgSyncApiVersion` (default 21), `msgSyncExtendedFilters` (default `"credentials,server_version,profiles,contacts,groups,messages,counters,folders,folders_with_peers"`), `msgSyncSupportedTypes` (default `"channels,business,personal,unread,managed_groups"`).

**Эффект:** −30% запросов при старте, folders сразу доступны, cursor-based пагинация вместо offset (стабильнее при быстрых обновлениях).

**Метрики:** время старта Messages экрана до/после (target: −200ms), количество network запросов до/после (target: 4→1).

### 9.2. Push Notifications channels (Sprint 1)

**Новые файлы:**
- `app/src/main/java/re/pinok/realtime/SnNotifyFilter.kt` (241 строка spec) — client-side `sn_*` filter. Поля: `sn_filter_messages`, `sn_filter_comments`, `sn_filter_reposts`, `sn_filter_likes`, `sn_filter_mentions`, `sn_filter_subscriptions`, `sn_filter_gifts`, `sn_filter_security_alerts`, `sn_filter_per_user: Map<Long, Set<String>>`.

**Изменяемые файлы:**
- `app/src/main/java/re/pinok/realtime/MessageNotifier.kt` — BigPictureStyle для всех notification types с фото/видео. Per-category channel mapping: `vk_messages` → `SovaPrefs.pushMessagesEnabled`, `vk_comments` → `SovaPrefs.pushCommentsEnabled`, `vk_reposts` → `SovaPrefs.pushRepostsEnabled`, `vk_likes` → `SovaPrefs.pushLikesEnabled`, `vk_mentions` → `SovaPrefs.pushMentionsEnabled`, `vk_subscriptions` → `SovaPrefs.pushSubscriptionsEnabled`, `vk_gifts` → `SovaPrefs.pushGiftsEnabled`, `vk_security_alerts` → `SovaPrefs.pushSecurityAlertsEnabled` (12+ новых SovaPrefs ключей).
- `app/src/main/java/re/pinok/data/local/SovaPrefs.kt` — добавить `pushPerUserMuted: String` (CSV per-user mute, формат `"userId1,userId2,..."`).
- `app/src/main/java/re/pinok/realtime/NotificationsPoller.kt` — lifecycle hardening: stop polling when app in background (если `SovaPrefs.pushBgPolling=false`), resume on foreground. ExponentialBackoff on error.

**Эффект:** Полноценные push-уведомления без FCM (LongPoll code 114 → system notification). Пользователь может тонко настраивать каналы.

### 9.3. VK ID Web Account API (Sprint 2)

**Новые файлы (12 экранов по 31 hash-роуту):**
- `app/src/main/java/re/pinok/ui/screens/account/AccountMainScreen.kt` (`#/main`) — overview.
- `app/src/main/java/re/pinok/ui/screens/account/PersonalScreen.kt` (`#/personal`) — personal data edit.
- `app/src/main/java/re/pinok/ui/screens/account/SecurityScreen.kt` (`#/security`) — security overview.
- `app/src/main/java/re/pinok/ui/screens/account/LoginHistoryScreen.kt` (`#/login-history`).
- `app/src/main/java/re/pinok/ui/screens/devices/DevicesScreen.kt` (`#/sessions`) — **уже существует**, расширить.
- `app/src/main/java/re/pinok/ui/screens/account/ChangePasswordScreen.kt` (`#/password`).
- `app/src/main/java/re/pinok/ui/screens/account/TwoFactorScreen.kt` (`#/2fa`) — OTP by App/SMS.
- `app/src/main/java/re/pinok/ui/screens/account/PasskeysScreen.kt` (`#/passkeys`) — WebAuthn.
- `app/src/main/java/re/pinok/ui/screens/account/EmailScreen.kt` (`#/email`) — change email.
- `app/src/main/java/re/pinok/ui/screens/account/PhoneScreen.kt` (`#/phone`) — change phone.
- `app/src/main/java/re/pinok/ui/screens/account/SafetyNetScreen.kt` (`#/safety-net`) — safety net settings.
- `app/src/main/java/re/pinok/ui/screens/account/DeactivateScreen.kt` (`#/deactivate`) — account deactivation.

**Изменяемые файлы:**
- `app/src/main/java/re/pinok/api/VKApiClient.kt` — добавить 97 методов (43 accountPersonal.*, 24 settings.*, 11 cua.*, 8 vkProtect.*, 8 multiaccount.*, 3 accountVerification.*).
- `app/src/main/java/re/pinok/ui/navigation/Screen.kt` — добавить 12 новых Screen objects.
- `app/src/main/java/re/pinok/ui/navigation/SovaNavHost.kt` — добавить 12 composable destinations.
- `app/src/main/java/re/pinok/ui/screens/settings/SettingsScreen.kt` — в `SecurityTab` добавить ссылки на новые экраны.

**Эффект:** Полноценный auth support (sessions, devices, 2FA, passkey, password change, email/phone change, SafetyNet, deactivation) — parity с `id.vk.ru/account/`.

### 9.4. Stories API parity (Sprint 4)

**Изменяемые файлы:**
- `app/src/main/java/re/pinok/api/VKApiClient.kt` — добавить 28 методов stories.*.
- `app/src/main/java/re/pinok/ui/screens/feed/StoryViewerScreen.kt` — replies viewers, ask question, mark seen/skipped/not interested, ban owner.
- `app/src/main/java/re/pinok/data/model/Models.kt` — расширить `Story`/`StoryGroup` (stats, replies, questions).

**Новые файлы:**
- `app/src/main/java/re/pinok/ui/screens/stories/StoryCreateScreen.kt` — создание stories (`stories.getPhotoUploadServer`/`getVideoUploadServer`).

### 9.5. Audio Effects Engine (Sprint 6)

**Новые файлы:**
- `app/src/main/java/re/pinok/media/AudioEffectsEngine.kt` — замена `EqualizerHelper`. 6 эффектов: `Equalizer` (legacy API 9+), `BassBoost`, `Virtualizer`, `LoudnessEnhancer`, `PresetReverb`, `DynamicsProcessing` (API 28+).
- `app/src/main/java/re/pinok/media/EqualizerDatabase.kt` — Room DB для custom presets (миграция с `CustomPresetStore` DataStore).
- `app/src/main/java/re/pinok/service/EqualizerService.kt` — foreground service для EQ (survive background).
- `app/src/main/AndroidManifest.xml` — добавить `<service android:name=".service.EqualizerService" android:foregroundServiceType="mediaPlayback" />`.

**Изменяемые файлы:**
- `app/src/main/java/re/pinok/media/EqualizerHelper.kt` — deprecated, делегирует в `AudioEffectsEngine`.
- `app/src/main/java/re/pinok/ui/screens/music/EqualizerScreen.kt` — UI для 6 эффектов (сейчас только Equalizer + presets).
- `app/src/main/java/re/pinok/ui/components/SpectrumVisualizer.kt` — real FFT (сейчас заглушка).
- `app/src/main/java/re/pinok/media/EqualizerFeatureFlags.kt` — расширить флагами для новых эффектов.

### 9.6. Compose Stability migration (Sprint 0 + Sprint 7)

**Изменяемые файлы:**
- `app/src/main/java/re/pinok/ui/screens/feed/ReactionPicker.kt` — `@Immutable data class ReactionEntry`.
- `app/src/main/java/re/pinok/ui/components/PendingPhotosBar.kt` — `@Immutable data class PendingPhoto`.
- `app/src/main/java/re/pinok/ui/screens/im/ChatDetailScreen.kt` — `@Immutable data class PendingFileAttachment`, `@Immutable data class AttachmentSelectionState`.
- `app/src/main/java/re/pinok/ui/screens/clips/ClipCreateViewModel.kt` — `@Immutable data class ClipCreateUiState`.
- `app/src/main/java/re/pinok/ui/screens/clips/ClipsViewModel.kt` — `@Immutable data class UiState`.
- `app/src/main/java/re/pinok/ui/navigation/SovaNavHost.kt` — `@Immutable data class ScrollPosition`.
- Все topLevel composables с unstable params → добавить `@Stable` (по результатам Compose Compiler Metrics report).

**Эффект:** −20-40% рекомпозиций в больших списках (FeedScreen LazyColumn с PostCard, ChatDetail LazyColumn с MessageBubble). Профайлинг через Layout Inspector / Compose Compiler Metrics.

### 9.7. VKApiClient split (Sprint 7)

**Новые файлы (16 модулей):**
```
api/
├── VKApiClient.kt          (facade, ~1000 строк — delegates to modules)
├── UsersApi.kt             (users.*, friends.*)
├── WallApi.kt              (wall.*, newsfeed.*, likes.*)
├── MessagesApi.kt          (messages.* — самый большой модуль, ~2000 строк)
├── PhotosApi.kt            (photos.* + composite upload functions)
├── VideoApi.kt             (video.*, shortVideo.*)
├── AudioApi.kt             (audio.*, catalog.*)
├── StoriesApi.kt           (stories.*)
├── GroupsApi.kt            (groups.*, board.*)
├── SearchApi.kt            (search.*, fave.*)
├── NotificationsApi.kt     (notifications.*)
├── AccountApi.kt           (account.*, cua.*, settingsGeneral.*, accountPersonal.*, settings.*)
├── DocsApi.kt              (docs.* + composite upload functions)
├── StoreApi.kt             (store.*, gifts.*, stickers.*)
├── PollsApi.kt             (polls.*)
├── ClipsApi.kt             (shortVideo.*, clips-related execute)
└── ExecuteApi.kt           (execute, VKScript helper)
```

**Изменяемые файлы:**
- `app/src/main/java/re/pinok/api/VKApiClient.kt` — сохранить как facade с `@Deprecated` annotations для обратной совместимости. Внутренние вызовы делегируют в модули.
- Все UI-файлы — обновить импорты (если нужно).

**Эффект:** Читаемость, тестируемость, изоляция изменений. SRP compliance. Снижение риска merge conflicts.

---

## 10. Замеченные проблемы (для отдельной работы)

### 10.1. Дублирование / перекрытие методов
1. `audio.get` вызывается из `audioGet` (`:2162`) и `audioGetWithCount` (`:2116`) — идентичные args, разный return type.
2. `users.get` — 3 функции: `usersGet` (mini), `usersGetByIds` (batch), `usersGetFull/Extended` (70+ fields). DTO расходятся.
3. `messages.getChat` — 2 функции: `messagesGetChatInviteLink` (`:966`) и `messagesGetChat` (`:994`). Разные return types.
4. `video.get` — 3 функции: `videoGetById`/`videoGet`/`videoGetClipById`. Разные args, путаное именование.
5. `messages.send` — 7+ вариантов: `messagesSend`/`sendWithAttachment`/`sendAudioToChat`/`sendVideoToChat`/`sendPostToChat`/`uploadAndSendPhoto`/`Doc`/`Video`/`sendVoiceMessage`/`messagesSendSticker`/`messagesSendStickerAsImage`. Логично, но сигнатуры пересекаются.
6. `users.subscribe`/`users.unsubscribe` — НЕ существуют в VK open API. Реализованы через `friends.add`/`friends.delete` (хак, может ломать для приватных профилей).
7. `reportVideo` — `execute` с `API.video.report`. Не VK open API, вернёт `execute_errors`. Best-effort.
8. `clipsDislike`/`clipsRemoveDislike` — эмуляция через `storage.set` execute. Не настоящий API.

### 10.2. TODO / Stub / Незавершённое
- `ChannelWebSocketClient.kt` — полностью stub (8 TODO).
- `SecurityAlertNotifier.kt:31` — TODO: action button «Завершить сессию».
- `SilentTokenExchanger.kt` — перебор 5 endpoints с разными `grant_type` (research-mode код).
- `videoAddViewingHistoryRecord` — стабильно возвращает error 100 через прямой токен (BFF-only метод). Мёртвый функционал.

### 10.3. PinoK-стиль (`?.` / `!!` / `?:`)
- Документация `VKApiClient.kt:62-74` требует избегать `?.`/`!!`/`?:`. Реальный код **повсеместно нарушает** это правило: сотни `obj.get("field")?.takeIf { !it.isJsonNull }?.asString`, `?: 0`/`?: 0L`/`?: ""`/`?: emptyList()`/`?: return null` повсюду.
- `asJsonObject`/`asJsonArray`/`asInt`/`asLong`/`asString` без `isJsonObject`/`isJsonArray` check — много где (`:180,263,274,299,580`).
- `!!` найден только в комментарии `VKApiClient.kt:9472`. Это OK.
- `OkVideoRepository.kt:530-540` — helpers `getStringOrNull/getIntOrNull/getLongOrNull` соблюдают стиль.

### 10.4. Архитектурные проблемы
- `VKApiClient.kt` — 12 436 строк god-class. Включает API methods + JSON parsers + VKScript + LongPoll class + 30+ inline data classes + rate limiter + captcha retry + offline detection + backoff.
- `VKApiClient` напрямую обращается к `SovaApp`, `ExchangeAuthRepository`, `SovaPrefs`, `TokenStorage`, `NetworkObserver`, `NetworkMods`, `AppLog` — высокая связность.
- Inline data classes внутри `VKApiClient` — нарушает инкапсуляцию данных от логики API.
- `AlAudioFallback` использует `vk.com/al_audio.php` через **remixsid cookie** (НЕ access_token) — отдельный web-стек, дублирующий auth-состояние.
- `SovaApp.apiClient` — глобальный singleton, доступ из всех ViewModels через `app.apiClient.*`. Сильная связанность через Application context.
- В UI-экранах **нет ViewModel-слоя** в 25 из 27 случаев: API вызовы прямо из `LaunchedEffect` внутри `@Composable`. Анти-pattern для Compose.
- `VKApiClient.lastApiError` / `lastApiErrorCode` — public mutable state на singleton. Гонка состояний между concurrent вызовами.

### 10.5. Безопасность
- `ВК.txt` (113 KB) содержит **живые access_token** 5 разных appId + `videoplayer_auth_token`. **Заккоммичен в репозиторий** — critical leak. Нужно: удалить файл, добавить в `.gitignore`, уведомить пользователя revoke'нуть все токены.
- `VK_CLIENT_SECRET=hHbZxrka2uZ6jB1inYsH` хардкод в `build.gradle.kts` — норма для VK Android (public app_secret), но привлекает внимание security audit.
- В `ExchangeAuthApi` для `authByPassword`/`authBy2FaCode`/etc. логируются `grant_type` и `username`, но НЕ `password` (корректно).

---

## 11. Приоритезация (итог)

| Sprint | Длительность | Эффект | Риск |
|--------|--------------|--------|------|
| Sprint 0 (Стабилизация) | 1-2 дня | Безопасность + стабильность UI | Низкий |
| Sprint 1 (Modern Sync + Push) | 1 неделя | UX messaging + notifications | Средний (LongPoll migration) |
| Sprint 2 (Auth & Security) | 1 неделя | Auth parity + VK ID Web | Высокий (auth изменения) |
| Sprint 3 (Messenger UX) | 1 неделя | Messenger parity | Средний (cmid migration) |
| Sprint 4 (Stories + Clips) | 1 неделя | Stories/Clips parity | Низкий |
| Sprint 5 (Video Player) | 1 неделя | Video parity + OK native | Средний (ExoPlayer + DASH) |
| Sprint 6 (Audio + Offline) | 1 неделя | EQ + offline parity | Средний (audio effects) |
| Sprint 7 (Рефакторинг) | 1 неделя | Архитектура | Высокий (большой refactor) |

**Итого:** ~7-8 недель full-time работы. Рекомендуемый порядок: Sprint 0 → 1 → 2 → 3 → 7 (после стабилизации messaging) → 4 → 5 → 6. Sprint 7 можно делать параллельно с 4-6, если есть отдельный разработчик.

**Quick wins (1-2 дня каждый):**
1. Удалить `ВК.txt` + `.gitignore` (0.5д, критично).
2. Compose Stability P0 (0.5д, +performance).
3. `collectAsStateWithLifecycle` migration (0.5д, +lifecycle correctness).
4. `asJsonObject`/`asJsonArray` → `getObj`/`getArr` migration (1д, +crash safety).
5. `#SSO-RECREATE-GUARD` уже реализован — проверить, что работает (0д, уже сделано).
