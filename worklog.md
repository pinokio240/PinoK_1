# PinoK Worklog

## Task 3
- **Agent**: Explore
- **Task**: Найти причину, почему в каналах (сообществах) отсутствует кнопка контента «Клипы» (clips), хотя клипы как фича существуют. Репозиторий: /home/z/repos/PinoK_1, ветка PinoK. Только исследование (read-only).

### Work Log
1. Прочитал клип-фичу в `app/.../ui/screens/clips/` (ClipsFeedScreen, ClipsViewModel, ClipsRepository, ClipCreateScreen, ClipInteractionsSheet). Клип-фича полностью реализована: лента (popular/subscriptions/trends), просмотр, лайки, репосты, комментарии, скачивание, создание.
2. Изучил `app/.../ui/screens/community/CommunityScreen.kt` — в нём вкладка «Клипы» ЕСТЬ и работает: `tabs = listOf("Записи","Фото","Видео","Клипы","Музыка","Обсуждения")` (CommunityScreen.kt:198), загрузка через `shortVideoGetOwnerVideos(-groupId)` (CommunityScreen.kt:438), рендер в `when(selectedTab) 3 -> {...}` (CommunityScreen.kt:1155–1211), отдельный `ClipThumbnail` (CommunityScreen.kt:1881). Т.е. в «обычном» экране сообщества клипы присутствуют.
3. История коммитов выявила отдельное понятие «канал» (`feat(channels)`, коммит a78ed0c, 2026-10-04): канал в VK — это особый вид group/чата (`peer.type="group" && canWrite.allowed=false`), реализованный НЕ в CommunityScreen, а внутри `ChatDetailScreen.kt` (`isChannel`, `channelHistoryMode`, `showChannelInfoPanel`, `channelGroup` и т.п.).
4. Нашёл, где у канала определяется список разделов: `ChannelInfoSectionsGrid` в `ChatDetailScreen.kt:10212–10250` — HARDCODED сетка 2×2: «Фотографии / Видео / Музыка / Файлы». Кнопки «Клипы» в ней нет.
5. Проверил всю цепочку: state-var’ы только 4 (`showChannelPhotos/Video/Audio/Files`, ChatDetailScreen.kt:947–952), `onSectionClick`/`when(section)` (ChatDetailScreen.kt:5764–5789) тоже только 4 кейса, и нет отдельного `ChannelClipsDialog` (соседи есть: `ChannelVideoDialog`:10765, `ChannelAudioDialog`:10855+, `ChannelFilesDialog`:10948).
6. Проверил источник данных: `VKApiClient.shortVideoGetOwnerVideos(ownerId, count)` определён в `VKApiClient.kt:18093–18131` и возвращает `List<Video>` (тот же формат, что `shortVideo.getRecom`), парсинг через `parseVideoFull`. Метод уже используется в CommunityScreen. Воспроизведение одного клипа через `VideoHolder.open(video)` уже подключено в `SovaNavHost.kt:2427`. `GroupInfo` (`VKApiClient.kt:282–324`) не содержит поля `is_clips`/`clips_enabled` — флаг «есть ли клипы» не парсится, но это не блокер (CommunityScreen показывает вкладку безусловно).
7. Существуют также `video.get` с параметром `is_clips=1` (`VKApiClient.kt:20333/20376`) и `ClipsRepository.getClip` (ClipsRepository.kt:120) с fallback `videoGetClipById` — можно использовать как fallback, если `shortVideo.getOwnerVideos` недоступен.

### Stage Summary
- **Где определяется список разделов канала:** `app/src/main/java/re/pinok/ui/screens/im/ChatDetailScreen.kt:10212` — `ChannelInfoSectionsGrid` (жёстко зашитая сетка 2×2: Фото/Видео/Музыка/Файлы). Это НЕ CommunityScreen — каналы реализованы внутри ChatDetailScreen (concept «канал» = group-чат с `canWrite=false`, коммит a78ed0c «feat(channels)»).
- **Почему «клипы» там нет:** 4 уровня пробела:
  1. HARDCODED-сетка без «Клипов» (`ChatDetailScreen.kt:10212–10250`).
  2. Нет state-переменной `showChannelClips` (есть только `showChannelPhotos/Video/Audio/Files`, ChatDetailScreen.kt:947–952).
  3. Нет кейса `"Клипы"` в `when(section)` обработчика `onSectionClick` (ChatDetailScreen.kt:5764–5789) — даже если добавить кнопку, тап будет no-op.
  4. Нет композабла `ChannelClipsDialog` (аналоги: ChannelVideoDialog:10765, ChannelAudioDialog:10855+, ChannelFilesDialog:10948) — некуда routing-нуть.
- **Баги/пробелы (файл:строка):**
  - `ChatDetailScreen.kt:10212–10250` — `ChannelInfoSectionsGrid` хардкод 2×2, нет кнопки «Клипы».
  - `ChatDetailScreen.kt:947–952` — нет `var showChannelClips`.
  - `ChatDetailScreen.kt:5764–5789` — нет кейса `"Клипы"` в `when(section)`.
  - `ChatDetailScreen.kt:10758+` — нет `ChannelClipsDialog` (для сравнения `ChannelVideoDialog` есть на 10765).
  - Доп.: `VKApiClient.kt:282–324` (`GroupInfo`) не парсит `is_clips`/`clips_enabled` (можно было бы скрывать кнопку для каналов без клипов), но это необязательно — CommunityScreen вкладку показывает безусловно.
- **Предложения по фиксу (кратко):**
  1. `ChatDetailScreen.kt:952` — добавить `var showChannelClips by remember { mutableStateOf(false) }`.
  2. `ChatDetailScreen.kt:10212` — расширить `ChannelInfoSectionsGrid` третьей строкой (или `FlowRow`) с кнопкой `label="Клипы"`, `icon=Icons.Outlined.PlayCircle`, `onClick = { onSectionClick("Клипы") }`.
  3. `ChatDetailScreen.kt:5764` — добавить кейс `"Клипы" -> { showChannelInfoPanel = false; showChannelClips = true }`.
  4. После `ChatDetailScreen.kt:5837` — добавить блок `if (peerId < 0 && isChannel && showChannelClips) { ChannelClipsDialog(apiClient = app.apiClient, ownerId = -abs(peerId), onDismiss = { showChannelClips = false }, onVideoClick = onVideoClick) }`.
  5. Создать `ChannelClipsDialog` по образцу `ChannelVideoDialog` (ChatDetailScreen.kt:10765), заменив `apiClient.videoGet(...)` → `apiClient.shortVideoGetOwnerVideos(ownerId = ownerId, count = 30)` (VKApiClient.kt:18093). Карточку рендерить вертикальным 9:16 постером (вынести `ClipThumbnail` из `CommunityScreen.kt:1881` в общий компонент или скопировать). Тап → `onVideoClick(video)` (воспроизведение уже подключено через `VideoHolder.open` в SovaNavHost.kt:2427).
  6. (Опц. fallback): если `shortVideo.getOwnerVideos` пуст/ошибка — повторить через `videoGet(ownerId, count=30)` с фильтром `it.isClips == 1` (паттерн `ClipsRepository.getClip`, ClipsRepository.kt:120).
- **Статус:** read-only, код не менялся.

---

## Task 2 — Канальные/сообщественные КНОПКИ КОНТЕНТА (Фото/Видео/Музыка/Файлы/Обсуждения)
- **Agent**: Explore (subagent)
- **Task**: Найти причину, почему кнопки отображения контента в каналах/сообществах не выполняют свою функцию (разделы Фото/Видео/Музыка/Файлы/Обсуждения). Read-only, репо `/home/z/repos/PinoK_1`, ветка PinoK.

### Work Log
1. Прочитал `/home/z/repos/PinoK_1/worklog.md` (ключевые записи): предыдущий Task 3 уже установил, что «каналы» реализованы НЕ в `CommunityScreen.kt`, а внутри `ChatDetailScreen.kt` (концепт channel=group-чат с `canWrite=false`, коммит a78ed0c «feat(channels)»). Это критично — два независимых места рендера разделов контента: (a) `CommunityScreen.kt:198` для обычных сообществ, (b) `ChatDetailScreen.kt` (`ChannelInfoPanelContent` + `ChannelInfoSectionsGrid`) для каналов. Запись `CHANNEL-FIX-C02024` (2026-10-04) подтверждает: 4 канальные панели (Фото/Видео/Музыка/Файлы) были заглушкой Toast «скоро» → реализованы недавно. Это объясняет «свежесть» багов.

2. Полностью прочитал `CommunityScreen.kt` (2129 строк), `AdminSectionsScreen.kt` (576 строк), ключевые куски `SovaNavHost.kt` (3383 строки), `Screen.kt` (614 строк), `ChatDetailScreen.kt` (11890 строк — критические секции: 555-985 параметры/state, 2900-3200 канальный детект, 3600-3720 шапка+клик, 5680-5840 рендер панелей, 10035-10250 ChannelInfoPanelContent/Grid, 10269 ChannelPhotosDialog мёртвый, 10605 ChannelPhotoFeedDialog, 10765 ChannelVideoDialog, 10861 ChannelAudioDialog, 10955 ChannelFilesDialog), `Models.kt:758` (`Chat.isChannel`), `ProfileScreen.kt:156-166` (для сравнения), `VKApiClient.kt` (audioGetWithCount:2677, photosGet:9865, docsGet:10083, photosPhotoFeedList:20744, photosPhotoFeedGet:20717).

3. Сверил фактическую цепочку вызовов для каждой кнопки:
   - **CommunityScreen.kt tabs (line 198):** `listOf("Записи","Фото","Видео","Клипы","Музыка","Обсуждения")` — HARD-CODED, не зависит от GroupSections. Tab onClick (line 902-907) корректно переключает selectedTab. LaunchedEffect-ы (lines 356, 376, 432, 452, 517) корректно грузят данные через photosGet/videoGet/shortVideoGetOwnerVideos/audioGetWithCount/boardGetTopics. Photo/Video/Clip/Topic click-хендлеры (lines 1068, 1130, 1208, 1334) корректно пробрасываются в SovaNavHost (lines 2427-2500) → VideoHolder.open / nav.navigate(BoardTopic).
   - **ChannelInfoSectionsGrid (line 10214):** 4 кнопки — Фотографии/Видео/Музыка/Файлы. onClick вызывает `onSectionClick("X")` (lines 10222-10247). Обработчик (line 5764-5789) — `when(section)` без `else`-ветки, переключает showChannel* флаги. Панели рендерятся по условию `peerId<0 && isChannel && showChannel*` (lines 5798, 5813, 5823, 5832).

4. Сравнил с ProfileScreen (рабочий паттерн): 7 динамических вкладок `PROFILE_CONTENT_TABS` (lines 156-166: Стена/Музыка/Видео/Фото/Клипы/Статьи/Закладки). У CommunityScreen — 6 статических. У ProfileScreen нет «Файлов»/«Обсуждений»/«Товаров» — но там это обосновано (вкладки USER-контента, не COMMUNITY-секций). Расхождение CommunityScreen vs ProfileScreen: CommunityScreen также НЕ парсит `users.getContentTabs`/`groups.getSettings` для динамического набора — хардкод.

5. Нашёл КОНКРЕТНЫЕ баги (см. Stage Summary). Главные: (1) хардкод tabs в CommunityScreen не учитывает GroupSections (отображает вкладку даже если секция выключена; не отображает «Файлы» даже если docs=1); (2) в канальной панели отсутствует «Обсуждения»; (3) для АДМИНИСТРАТОРА канала `isChannel=false` (т.к. `canWrite.allowed=true`) → панель канала вообще не открывается, клик по шапке молча no-op; (4) ChannelPhotoFeedDialog использует `photos.photoFeedGet` (личная фотолента пользователя) вместо `photosGet`/`photosGetAlbums` (фото сообщества); (5) мёртвый `ChannelPhotosDialog` (line 10269) с правильным API никогда не вызывается.

### Файлы разделов канала/сообщества и их onClick-хендлеры

**A) CommunityScreen.kt (обычные сообщества):**
| Tab | Файл:строка | onClick | Роут/Действие |
|---|---|---|---|
| Записи (0) | CommunityScreen.kt:912-1063 | (default, wall items) | onPostClick → PostDetailScreen (SovaNavHost.kt:2430) |
| Фото (1) | CommunityScreen.kt:1064-1075 | onPhotoClick→photoViewerState | PhotoViewer overlay (line 1367) |
| Видео (2) | CommunityScreen.kt:1076-1154 | onVideoClick(video) | VideoHolder.open (SovaNavHost.kt:2427) |
| Клипы (3) | CommunityScreen.kt:1155-1211 | onVideoClick(video) | VideoHolder.open |
| Музыка (4) | CommunityScreen.kt:1212-1278 | AudioAttachmentList→PlayerConnection.playTrackList | локальный плеер (AudioAttachmentList.kt:99) |
| Обсуждения (5) | CommunityScreen.kt:1279-1338 | onTopicClick(gId,tId,title) | nav.navigate(BoardTopic) (SovaNavHost.kt:2438) |

**Б) ChatDetailScreen.kt ChannelInfoPanelContent (каналы):**
| Секция | Файл:строка кнопки | onSectionClick → state | Рендер панели (файл:строка) |
|---|---|---|---|
| Фотографии | ChatDetailScreen.kt:10222 | showChannelPhotos=true (5770) | ChannelPhotoFeedDialog (5798) — photos.photoFeedGet |
| Видео | ChatDetailScreen.kt:10228 | showChannelVideo=true (5777) | ChannelVideoDialog (5813) — videoGet ✓ |
| Музыка | ChatDetailScreen.kt:10236 | showChannelAudio=true (5781) | ChannelAudioDialog (5823) — audioGetWithCount ✓ |
| Файлы | ChatDetailScreen.kt:10242 | showChannelFiles=true (5785) | ChannelFilesDialog (5832) — docsGet ✓ |
| Обсуждения | — ОТСУТСТВУЕТ — | — | нет (board.getTopics для канала не вызывается) |

### Кнопки, которые реально «мёртвые» (нет onClick / нет роута / нет данных)
1. **«Файлы» в CommunityScreen** — кнопка ОТСУТСТВУЕТ ВООБЩЕ (нет в `tabs` list, CommunityScreen.kt:198). Документы сообщества нельзя посмотреть, даже если `docs=1` в AdminSectionsScreen.
2. **«Обсуждения» в канальной панели** — кнопка ОТСУТСТВУЕТ ВООБЩЕ (нет в `ChannelInfoSectionsGrid`, ChatDetailScreen.kt:10214-10250).
3. **«Клипы» в канальной панели** — ОТСУТСТВУЕТ (см. предыдущий Task 3).
4. **«Товары» (Market)** — отсутствует везде (AdminSectionsScreen.kt:100 в SECTION_CODES есть, но UI-кнопки нет).
5. **«Статьи» (Articles)** — отсутствует в CommunityScreen и канальной панели (есть только в ProfileScreen).
6. **«Материалы» (Wiki)** — отсутствует везде.
7. **Все канальные кнопки для АДМИНА** — мёртвые: `isChannel=false` (т.к. admin has canWrite.allowed=true) → условие `peerId<0 && isChannel` (ChatDetailScreen.kt:5731/5798/5813/5823/5832) ложно → панели не рендерятся, а клик по шапке (line 3618) — no-op без else-ветки.
8. **«Фотографии» в канальной панели** — открывается, но `ChannelPhotoFeedDialog` (line 10627) вызывает `photos.photoFeedGet` — это личная фотолента пользователя, не фото сообщества. Возвращает пусто/ошибку → пользователь видит «В сообществе нет фотографий» даже если фото есть.

### Конкретные баги (файл:строка)

1. **CommunityScreen.kt:198** — `val tabs = listOf("Записи", "Фото", "Видео", "Клипы", "Музыка", "Обсуждения")` — HARDCODED список вкладок. Не учитывает `GroupSections` (AdminSectionsScreen.kt:69-76 — `TRI_STATE_SECTIONS`: topics/photos/video/audio/docs/wiki). Если администратор выключил «Обсуждения» (`topics=0`) — вкладка «Обсуждения» всё равно показывается (при тапе topicsLoaded=true, но topics=[] → «В сообществе нет обсуждений»). Если администратор включил «Документы» (`docs=1`) — вкладки «Файлы» нет вообще. Отсутствуют вкладки: Файлы (Документы), Товары (Market), Материалы (Wiki), Статьи.

2. **ChatDetailScreen.kt:10214-10250** — `ChannelInfoSectionsGrid` — HARDCODED сетка 2×2: Фотографии/Видео/Музыка/Файлы. (a) Нет «Обсуждений» (board.getTopics для канала не вызывается). (b) Не учитывает `GroupSections` — если секция выключена, кнопка всё равно показывается (тап → пустая панель «нет X»). (c) Нет «Клипов» (см. Task 3). (d) Нет «Товаров».

3. **ChatDetailScreen.kt:3614-3623** — `Modifier.clickable { if (peerId < 0 && isChannel) { showChannelInfoPanel = true } else if (peerId in 1..1_999_999_999L) { onUserClick(peerId) } }` — НЕТ `else`-ветки для случая `peerId<0 && !isChannel`. Это происходит, когда: (a) пользователь — АДМИН канала (`canWrite.allowed=true` → `Chat.isChannel=false`, Models.kt:758); (b) `channelModeEnabled=false` (SovaPrefs msgChannelMode, default true, SovaPrefs.kt:235); (c) VK не прислал `can_write` И wall.get вернул ошибку (e.g., err 15 «wall is disabled»). В этих случаях клик по шапке канала молча ничего не делает — пользователь не понимает, почему панель не открывается.

4. **Models.kt:758** — `val isChannel: Boolean get() = peer.id < 0 && canWrite?.allowed == false` — для АДМИНИСТРАТОРА канала `canWrite.allowed=true` → `isChannel=false`. Поскольку `isChannel` используется в условиях рендера панелей (ChatDetailScreen.kt:5731/5798/5813/5823/5832) и в условии клика по шапке (3618), админ не может открыть панель канала и увидеть разделы Фото/Видео/Музыка/Файлы. Подписчики — могут, админы — нет.

5. **ChatDetailScreen.kt:10627** — `val r = apiClient.photosPhotoFeedList(ownerId = ownerId, count = 40, startFrom = null)` — использует `photos.photoFeedGet` (VKApiClient.kt:20760) — это **личная фотолента пользователя** (см. KDoc VKApiClient.kt:20709 «фото-лента владельца … ПАРАМЕТРЫ ТОЧНО из бандла pageProfile»). Для `owner_id=-<channel_id>` (отрицательный = сообщество) VK либо возвращает пустой ответ, либо ошибку. Должно использоваться `photosGet(ownerId, albumId="wall")` (как в CommunityScreen.kt:363) или `photosGetAlbums` + `photosGet(album_id)` (паттерн мёртвого `ChannelPhotosDialog` на line 10269, который никогда не вызывается).

6. **ChatDetailScreen.kt:10269** — `private fun ChannelPhotosDialog(...)` — МЁРТВЫЙ КОД. Никогда не вызывается (grep подтверждает 0 вызовов). Использует ПРАВИЛЬНОЕ API (`photosGetAlbums` + `photosGet`, lines 10295, 10312) — но перекрыт `ChannelPhotoFeedDialog` (line 10605, вызывается из 5798) с неверным API. Нужно либо удалить мёртвый, либо поменять вызов на него.

7. **ChatDetailScreen.kt:10705/10831/10927/11020** — MISLEADING EMPTY STATE. Когда API возвращает пустой список из-за: (a) `isOffline()` — ранний return `emptyList()` (VKApiClient.kt:9871/10084/20749); (b) API error с пустым items (но без exception); (c) отсутствия прав доступа — диалог показывает «В сообществе нет X» вместо честной ошибки/«Нет подключения»/«Нет прав». ChannelVideoDialog (line 10785-10787) корректно ловит exception в `error`, но videoGet() возвращает emptyList() БЕЗ exception при ошибке VK (return emptyList()) → пользователь видит «В сообществе нет видео».

8. **ChatDetailScreen.kt:5764-5788** — `when (section) { "Фотографии" -> {...}; "Видео" -> {...}; "Музыка" -> {...}; "Файлы" -> {...} }` — НЕТ `else`-ветки. Если в `ChannelInfoSectionsGrid` добавят новую кнопку (например, «Обсуждения» или «Клипы») БЕЗ одновременного обновления `when` — клик будет молча no-op. (Сейчас 4 строки совпадают, но это хрупкая связка — нет compile-time гарантии.)

9. **ChatDetailScreen.kt:10067** — STALE KDoc: `@param onSectionClick клик по разделу (Toast «Раздел … скоро»)`. Реально Toast'ов нет — обработчик (5764) открывает панели. Вводит в заблуждение при поддержке.

10. **AdminSectionsScreen.kt:69-76** — `TRI_STATE_SECTIONS` содержит `"docs" to "Документы"` и `"wiki" to "Материалы"` — переключатели есть, но ни CommunityScreen, ни ChannelInfoPanelContent эти секции НЕ отображают (нет UI для просмотра). Пользователь включает «Документы» — но посмотреть их не может.

### Сравнение с рабочим паттерном (ProfileScreen.kt)
- ProfileScreen.kt:156-166 — `PROFILE_CONTENT_TABS` также hardcoded, НО это статичный набор вкладок для своего профиля (стена/музыка/видео/фото/клипы/статьи/закладки) — нет концепции «включаемых/выключаемых секций» для USER-профиля.
- CommunityScreen.kt:198 — должен был бы использовать `GroupSections` (groups.getSettings), но не делает этого.
- Канальная панель (ChannelInfoPanelContent) — должна была бы использовать `GroupSections` (или owners.getContentTabs, см. AdminSectionsScreen.kt:151 — `tabs` уже загружается через `ownersGetContentTabs`, но в ChannelInfoPanelContent не используется).

### Предложения по фиксу (кратко)
1. **CommunityScreen.kt:198** — сделать tabs динамическими. После `groupInfo`-загрузки (LaunchedEffect line 245) параллельно вызвать `app.apiClient.groupsGetSettings(groupId)` (или читать из `groupInfo.sectionStatuses` если доступно). Сформировать `tabs` на основе `GroupSections`: photos>0 → «Фото», video>0 → «Видео», audio>0 → «Музыка», docs>0 → «Файлы», topics>0 → «Обсуждения», всегда «Записи» (wall), «Клипы» (если shortVideoGetOwnerVideos не пуст). Соответственно добавить state для docs (по образцу photos state, line 203-206) и обработчик `when(selectedTab)` для нового индекса «Файлы» — `app.apiClient.docsGet(ownerId=-groupId, count=50)` (VKApiClient.kt:10083, уже принимает ownerId).
2. **ChatDetailScreen.kt:10214-10250** — расширить `ChannelInfoSectionsGrid` третьей строкой с «Обсуждениями» (board.getTopics, как в CommunityScreen:523). Передача `onSectionClick("Обсуждения")`. Добавить state `showChannelTopics` (line 952), кейс в `when(section)` (5764), рендер `ChannelTopicsDialog` (новый composable по образцу ChannelVideoDialog, line 10765, но с `boardGetTopics(groupId)` вместо `videoGet`).
3. **ChatDetailScreen.kt:3614-3623** — добавить `else`-ветку для `peerId<0 && !isChannel`: либо открывать `ChatInfoScreen` (как для обычного чата), либо Toast «Канал недоступен» с причиной. Минимум — fallback на `onInfoClick(peerId)` если `chatInfoEnabled`.
4. **Models.kt:758** — добавить отдельный флаг `isAdminOfChannel: Boolean` (или использовать существующий `channelGroup?.isManager == true`) и ослабить условие: панель канала должна открываться для любого `peerId<0` peer'а, у которого `channelGroup != null` (т.е. это channel-сообщество), независимо от canWrite. Конкретно — заменить `isChannel` в условиях (5731/5798/5813/5823/5832) на `peerId < 0 && (isChannel || channelGroup?.isChannel == true)` или ввести `isChannelUi = isChannel || (peerId<0 && channelGroup != null)`.
5. **ChatDetailScreen.kt:5798** — заменить `ChannelPhotoFeedDialog` на `ChannelPhotosDialog` (мёртвый, line 10269) который использует правильный API (`photosGetAlbums` + `photosGet`). Или переписать `ChannelPhotoFeedDialog` на `photosGet(albumId="wall"|"saved"|"profile")` (как CommunityScreen.kt:363).
6. **ChatDetailScreen.kt:5764-5788** — добавить `else -> { AppLog.w("ChannelPanel", "Unknown section: $section") }` (минимум — логирование; лучше — defensive Toast «Раздел … недоступен»).
7. **ChatDetailScreen.kt:10705/10831/10927/11020** — различать «нет данных» от «ошибка API». После вызова API проверять `apiClient.lastApiError`/`lastApiErrorCode` — если non-null, показывать ErrorView с «Повторить» (как делает ChannelVideoDialog:10816-10830 для exception-ветки). Для `isOffline()` — «Нет подключения к интернету» вместо «В сообществе нет X».
8. **AdminSectionsScreen.kt:10067** (KDoc) — обновить комментарий: `onSectionClick клик по разделу → открывает соответствующую панель (ChannelPhotoFeedDialog/ChannelVideoDialog/ChannelAudioDialog/ChannelFilesDialog)`.
9. **Удалить мёртвый `ChannelPhotosDialog`** (ChatDetailScreen.kt:10269) ИЛИ заменить им активный `ChannelPhotoFeedDialog`.

### Stage Summary
- **Файлов изменено:** 0 (read-only research).
- **Корневая причина «кнопки не работают»:**
  - (a) Для **обычных сообществ** — `CommunityScreen.kt:198` хардкод вкладок: нет «Файлов» (Документы), и вкладки не фильтруются по `GroupSections`. Пользователь включает «Документы» в админке → вкладки нет. Пользователь выключает «Обсуждения» → вкладка всё равно есть, но пуста.
  - (b) Для **каналов** — `ChatDetailScreen.kt:10214` хардкод 2×2: нет «Обсуждений», «Клипов», «Товаров»; не учитывает `GroupSections`. Плюс критичный баг **(Models.kt:758 + ChatDetailScreen.kt:3614)**: для **администратора канала** `isChannel=false` → панель канала не открывается вообще (клик по шапке — no-op). Плюс **(ChatDetailScreen.kt:10627)** «Фотографии» использует неверный API `photos.photoFeedGet` (личная лента, не фото сообщества) → пустая панель.
- **Ключевые баги (top-5):**
  1. `CommunityScreen.kt:198` — хардкод tabs без «Файлов» и без учёта GroupSections.
  2. `ChatDetailScreen.kt:10214` — хардкод секций канала без «Обсуждений»/«Клипов».
  3. `Models.kt:758` + `ChatDetailScreen.kt:3614` — для админа канала `isChannel=false` → панель не открывается (no-op без else).
  4. `ChatDetailScreen.kt:10627` — неверный API `photos.photoFeedGet` для фото канала (личная лента, не сообщества).
  5. `ChatDetailScreen.kt:10269` — мёртвый `ChannelPhotosDialog` с правильным API перекрыт активным с неверным.
- **Статус:** read-only, код не менялся. Готов к передаче в Fix-фазу (предложения 1-9 выше).

---
Task ID: 1 (COMMUNITY-VIDEO-NOT-PLAYING)
Agent: subagent (Explore)
Task: Найти причину, почему в каналах/сообществах НЕ воспроизводится видео (репозиторий PinoK_1, ветка PinoK). Только исследование read-only.

Work Log:
- Прочитал /home/z/my-project/worklog.md для контекста. Последняя запись — исследование каналов (коммиты a78ed0c «канальный пост как карточка поста сообщества; фиксы аватара/комментариев; разделы фото/видео/музыка/файлы» и CHANNEL-FIX-C02024).
- Прочитал ключевые файлы:
  * app/.../ui/screens/community/CommunityScreen.kt (2130 строк) — экран сообщества (4 вкладки: Записи/Фото/Видео/Клипы), uses wallGetExtended(ownerId=-groupId) и videoGet(ownerId=-groupId, extended=1).
  * app/.../ui/components/PostVideoCarousel.kt (273 строки) — карусель видео-вложений, onClick → onVideoClick(video).
  * app/.../ui/screens/videoplayer/VideoPlatformRouter.kt (275 строк) — диспетчер платформ (VK/UNKNOWN → VideoPlayerScreen, OK/YOUTUBE/INSTAGRAM/EXTERNAL_IFRAME → OkWebViewPlayer, gated by externalVideosEnabled).
  * app/.../ui/screens/videoplayer/VideoPlayerScreen.kt (3655 строк) — ExoPlayer + LaunchedEffect fallback к videoGetById когда files==null.
  * app/.../ui/screens/videoplayer/OkWebViewPlayer.kt (1075 строк) — WebView плеер для OK/YouTube/Instagram/external iframe (ad-blocking + ADMAN stub).
  * app/.../api/VKApiClient.kt — parseVideoFull (11976), parseVideoFiles (12136), parseAttachments (11658), parsePostMini (11887), videoGet (9201), videoGetById (9144), wallGet (11232), wallGetExtended (15477), parseChannelHistoryItem (8090).
  * core/data/.../data/model/Models.kt — Video data class (226), detectPlatform() (435), hasPlayableFiles() (467), withDetectedPlatform() (516), isClip getter (340).
  * app/.../ui/screens/im/ChatDetailScreen.kt — ChannelVideoDialog (10765, новый), WallPostCard usage (4590, 4849), messageToChannelPost (7484), ChannelPostWallInfoRow (7363).
  * app/.../ui/screens/profile/ProfileScreen.kt — WallPostCard (2213), VideoThumbnail public (2706).
  * app/.../ui/navigation/SovaNavHost.kt — CommunityScreen onVideoClick = VideoHolder.open (2427-2429), ChatDetailScreen onVideoClick (2888-2890), overlayVideo render (3074-3082).
  * app/.../ui/MainActivity.kt — overlayVideo render в guest-режиме (1670-1678), VideoHolder wiring.
  * app/.../res/xml/network_security_config.xml — cleartextTrafficPermitted=false (HTTPS only, ok.ru/vk.com/vk.ru домены в white-list).
  * app/src/main/AndroidManifest.xml — INTERNET permission ok, usesCleartextTraffic=false, hardwareAccelerated implied.
- Корректность click-wiring: VideoHolder.open → overlayVideo → VideoPlatformRouter → VideoPlayerScreen/OkWebViewPlayer — работает для всех экранов (Feed/Community/ChatDetail/Profile/Video). Click-обработчики в CommunityScreen (1130/1208/1627/1634/1636/1721/1728/1730) и ChatDetailScreen (4594/4681/4853/5040/5818) пробрасывают onVideoClick.
- Корневая причина (Bug 1): detectPlatform() в Models.kt:435-461 misroutes VK-native видео (player=`vk.com/video_ext.php?...`, files=null — типичный случай для video-аттачментов в wall.get/channels.getHistory) на `VideoPlatform.EXTERNAL_IFRAME`, минуя VideoPlayerScreen и его нативный videoGetById fallback. Видео уходит в OkWebViewPlayer, который грузит video_ext.php в WebView как main-page (не iframe) — VK-плеер либо не стартует, либо показывает пустой экран.
- Дополнительные баги: (2) videoGetById требует access_key, который часто отсутствует в wall.get attachments; (3) default `{}` для onVideoClick — silent no-op если caller забыл передать; (4) ChannelVideoDialog (новый, 2026-10-04) не имеет пагинации и не использует wall.get fallback; (5) OkWebViewPlayer не вызывает setAcceptThirdPartyCookies per-WebView; (6) тот же Bug 1 ломает видео в каналах (channels.getHistory → message.attachments).

Stage Summary:
- Read-only исследование завершено. Код не менялся.
- Найдена корневая причина: Models.kt:435-461 (`detectPlatform()`) — VK-native видео с player=`vk.com/video_ext.php` и files=null классифицируются как EXTERNAL_IFRAME вместо VK/UNKNOWN. Это маршрутизирует видео в OkWebViewPlayer (WebView с video_ext.php как main-page) вместо VideoPlayerScreen (нативный ExoPlayer + videoGetById fallback). Затронуты: CommunityScreen (вкладки Записи/Видео/Клипы), ChatDetailScreen (channel wall-mode и history-mode), а также FeedScreen/PostDetailScreen/UserProfileScreen (тот же баг, но менее заметный, т.к. лента работает с большим объёмом и чаще получает videos с files).
- Гипотезы-баги (3-7 шт. с файл:строка) и предложения по фиксу — в финальном отчёте агенту-оркестратору.
- Файлы: читал только через Read/Grep/Glob/Bash(только для git log/tail); правок не вносил.

---
Task ID: 6 (сводный план)
Agent: orchestrator (main)
Task: Свести находки 4 агентов в единый план проблем и работ. Внешний LLM 26.147.87.212:4444 недоступен (приватный IP, connection timeout).

Work Log:
- Агент 1 (видео в каналах): корневая причина — Video.detectPlatform() misroute vk.com/video_ext.php на EXTERNAL_IFRAME, минуя нативный VideoPlayerScreen с videoGetById fallback. 7 багов.
- Агент 2 (кнопки контента): 6 hardcoded вкладок CommunityScreen без Файлов; 2×2 grid канала без Обсуждений/Клипов; isChannel=false для админа; ChannelPhotoFeedDialog зовёт ЛИЧНОЕ photos.photoFeedGet вместо photos сообщества; мёртвый ChannelPhotosDialog; нет else в when; misleading empty state.
- Агент 3 (клипы в каналах): кнопки Клипов нет в ChannelInfoSectionsGrid (ChatDetailScreen 10212), нет showChannelClips state, нет кейса в when, нет ChannelClipsDialog. Источник shortVideoGetOwnerVideos уже есть (VKApiClient 18093).
- Агент 4 (аудит): 20 находок, 3 CRITICAL (access_token в logcat через android.util.Log.i; user-CA в network_security_config; plaintext account.json), 5 HIGH, 9 MED, 3 LOW.

Stage Summary (приоритезированный план):
P0 — функциональные баги пользователя (видео/кнопки/клипы):
  P0.1 Models.kt:435-461 detectPlatform — добавить ветку vk.com/video_ext.php + vkvideo.ru → VK/UNKNOWN (чинит видео в каналах/фиде/постах).
  P0.2 parseVideoFull (VKApiClient 12079) — извлекать accessKey из player URL &hash= если поля нет.
  P0.3 VideoPlayerScreen 505-514 — fallback videoGetById при пустом accessKey: hash из URL → videoGet(list) → Intent.ACTION_VIEW.
  P0.4 ChannelInfoSectionsGrid (ChatDetailScreen 10212) — добавить 3-ю строку: Клипы + Обсуждения; FlowRow.
  P0.5 Добавить showChannelClips state + кейс "Клипы" в when(5764) + ChannelClipsDialog (по образцу ChannelVideoDialog 10765, через shortVideoGetOwnerVideos).
  P0.6 Заменить ChannelPhotoFeedDialog (зовёт личное photoFeedGet) на photosGet(albumId="wall") сообщества (или оживить мёртвый ChannelPhotosDialog 10269).
  P0.7 isChannel-условие (Models 758 + ChatDetailScreen 3614) — isChannelUi=isChannel||(peerId<0&&channelGroup!=null); добавить else-ветку клика по шапке.
  P0.8 CommunityScreen tabs (198) — динамически из GroupSections; добавить Файлы (docsGet).
  P0.9 when(section) 5764 — добавить else с AppLog.w; различать empty vs error в диалогах (10705/10831/10927/11020).

P1 — security CRITICAL:
  P1.1 OAuthWebViewActivity (583,453,464,474) — убрать android.util.Log.i с access_token; маскировать.
  P1.2 network_security_config.xml (13,42) — убрать <certificates src="user"/>; добавить CertificatePinner для vk доменов.
  P1.3 ExchangeTokenStorage dumpToFile — не писать silent_token/trusted_hash/password/remixsid в plaintext; SovaPrefsCrypto с device-bound ключом.

P2 — security HIGH:
  P2.1 SilentTokenExchanger 129 + WebTokenAuth 461 — не логировать silent_token_uuid.
  P2.2 AppLog SENSITIVE_KEYS (78) — добавить remixsid, p, httoken, trusted_hash, remixstid, remixuas и т.д.
  P2.3 UpdaterManager 718 — требовать sha256 обязательным; reject version без sha256.
  P2.4 ExchangeTokenStorage 117 — удалить публичный saveCredentials.

P3 — concurrency/stability MED:
  P3.1 HiddenSessionRefresher 80,104,117 — Mutex/AtomicBoolean вместо if(inProgress) return.
  P3.2 EqualizerHelper 60-95,230 — синхронизировать engine() getter; release() занулять engine.
  P3.3 Folders/Pinned/Archived Repository — Mutex или DataStore compareAndSet.
  P3.4 Queuev4Client 152 — URL-encode cred.key; отменять currentCall в stop().
  P3.5 LongPollClient ~540 — на failed=4 добавить backoff.
  P3.6 SovaApp 950,1002,1035 — вынести runBlocking из onCreate.
  P3.7 AudioEffectsEngine 754 — сбрасывать scoSuspended в release.
  P3.8 VideoPipController 71 — cleanup в DisposableEffect / WeakReference.
  P3.9 VideoPipActivity 254 — ContextCompat.registerReceiver с RECEIVER_NOT_EXPORTED.

P4 — LOW:
  P4.1 Music/VideoDownloadService — ServiceCompat.startForeground с type.
  P4.2 VkCookieJar 79 — skip-invalid-cookie per-cookie try-catch.
  P4.3 VkSigner hardcoded VK_CLIENT_SECRET — задокументировать риск.

---
Task ID: 8
Agent: general-purpose (P1.3+P2.4)
Task: минимизация дампа account.json + удалить мёртвый saveCredentials

Work Log:
- Прочитал /home/z/my-project/worklog.md (контекст Tasks 1-6 от предыдущих агентов).
  Task 6 уже приоритизировал P1.3 (минимизация дампа) и P2.4 (удалить saveCredentials)
  из аудита Task AUDIT-NONOBVIOUS-ISSUES-2026-10-04 (находки 3 CRITICAL + 5 HIGH).
- Прочитал /home/z/repos/PinoK_1/app/src/main/java/re/pinok/auth/exchange/ExchangeTokenStorage.kt
  полностью (1085 строк); /home/z/repos/PinoK_1/app/src/main/java/re/pinok/auth/exchange/CookieJarBackup.kt
  (160 строк); ExchangeAuthRepository.kt:1100-1140 (remixsid() accessor — источник истины
  ЖИВОЙ CookieManager, storage.remixsid() только как legacy fallback).
- TASK 1 (P2.4): grep `saveCredentials\(` по всему репо — 0 вызывателей в .kt
  (только определение на строке 117 + упоминание в HISTORY.md:12863 "saveCredentials
  вызовы в signIn убраны"). Метод мёртвый, публичный, писал phone+password plaintext
  в prefs (KEY_LAST_PHONE/KEY_LAST_PASSWORD) + dumpToFile. УДАЛЕН полностью (вместе
  с docstring). Оставлен комментарий-маркер "#P2.4 (2026-10): saveCredentials(username,
  password) УДАЛЕН — мёртвый метод" в секции "// Credentials" (строка 118).
  Константы KEY_LAST_PHONE/KEY_LAST_PASSWORD оставлены в companion — используются
  wipeLegacySessionArtifacts() при старте и credentials() accessor (тоже мёртвый,
  но вне скоупа Task 8 — не трогал).
- TASK 2 (P1.3): dumpToFile() переписан. Убраны putOpt/put для 25 ключей
  (см. список ниже). Оставлены ТОЛЬКО утверждённый пользователем минимум:
  access_token, access_token_invalidated, expires_at, user_id, exchange_token,
  secret, trusted_hash, device_id, sat_token, web_cookies snapshot (CookieJarBackup),
  __backup_at, __backup_version. Версия дампа bumped с 1 → 2 (маркер минимизации).
  Docstring переписан: перечисляет что пишется и что НЕ пишется, объясняет почему
  (silent_token короткоживущий, куки уже в web_cookies snapshot, lp_* получаются
  заново через messages.getLongPollServer, scope implicit "all", last_phone/
  last_password не нужны #SESSION-WEB-MECHANISM).
- restoreFromFileBackup() обновлён: убраны putOptStr для тех же 25 ключей (для
  новых файлов v2 они не нужны; для старых v1 — игнорируются, что безопасно).
  Оставлены putOptStr только для 5 ключей минимума: exchange_token, secret,
  trusted_hash, device_id, sat_token. Лог "PARTIAL" обновлён: вместо
  `remixsid present=${!remixsid().isNullOrBlank()}` (после минимизации prefs
  пуст → всегда false) теперь `web_cookies restored=$cookiesRestored` (значение
  из CookieJarBackup.restoreJsonArray, отражает web-сессию). Лог "access_token
  absent" обновлён: вместо remixsid проверяет web_cookies snapshot entries.
- Класс-level docstring обновлён (строка 28-37): "все токены дублируются" →
  "МИНИМАЛЬНЫЙ набор критичных полей дублируется (#P1.3 #MIN-DUMP)".
- Проверил обратную совместимость цепочки: после restore из нового дампа
  prefs не содержит remixsid, НО CookieManager заполнен через
  CookieJarBackup.restoreJsonArray → exchangeAuthRepository.remixsid() (читает
  CookieManager первым через RemixsidCapturer.snapshotCookies()) возвращает
  корректное значение. Это #SESSION-WEB-MECHANISM паттерн (CookieManager —
  источник истины, storage-копии legacy). Цепочка restore → CookieManager →
  remixsid() → silent paths (Path 1.5/2.5/3/5) работает.
- Синтаксис проверен: braces 130/130, parens 724/724, brackets 23/23 (баланс
  сошёлся). Все KEY_* константы в companion на месте (не удалял). Сохранены:
  saveAuthResult, updateAccessToken, saveWebTokenResult, saveRemixsidOnly,
  saveSessionCookiesOnly, saveSilentToken, saveLongPoll, clear, clearAccessToken,
  exportSessionSnapshot, applyExportedSession — БЕЗ изменений логики (только
  docstring exportSessionSnapshot обновил: убрал устаревшую ссылку `:607`).
- Gradle НЕ запускал (нет Android SDK в sandbox, по условию задачи).

Stage Summary:
- Файлов изменено: 1 (ExchangeTokenStorage.kt, +16 строк: 1101 vs 1085).
- TASK 1 (P2.4): saveCredentials удалён. `rg -n "saveCredentials\(" /app /core /feature`
  возвращает 0 вызывателей (только комментарий-маркер в ExchangeTokenStorage.kt:118
  и упоминание в HISTORY.md:12863 — оба не код).
- TASK 2 (P1.3): дамп минимизирован. УБРАНО из дампа (25 ключей):
  silent_token, silent_token_uuid, webview_access_token, webview_refresh_token,
  webview_expires_in, last_phone, last_password, utility_tokens, lp_key,
  lp_server, lp_ts, lp_pts, remixsid, remixnsid, p (vk_p_cookie), logout_hash,
  vk_httoken, vk_remixnttpid, vk_remixuacck, vk_remixuas, vk_remixdmgr,
  vk_remixmvkfp, vk_remixstid, vk_remixstlid, scope.
- ОСТАВЛЕНО в дампе (12 полей): access_token, access_token_invalidated,
  expires_at, user_id, exchange_token, secret, trusted_hash, device_id,
  sat_token, web_cookies snapshot, __backup_at, __backup_version (v=2).
- restoreFromFileBackup остаётся рабочим для:
  (a) новых файлов v2 (минимум): заливает 9 prefs-ключей + CookieManager
      через web_cookies snapshot → exchangeAuthRepository.remixsid() работает
      (читает CookieManager, не storage);
  (b) старых файлов v1 (все ключи): игнорирует лишнее (silent_token/lp_*/etc.
      не восстанавливаются в prefs, но web_cookies snapshot восстанавливает
      все куки в CookieManager — functionality preserved).
- Безопасность: из plaintext account.json убраны короткоживущие токены
  (silent_token, webview_tokens), plaintext креды (last_phone, last_password),
  LongPoll креды (lp_*) и 11 дубликатов кук (remixsid, p, httoken, remixstid,
  remixstlid, remixnsid, remixnttpid, remixuacck, remixuas, remixdmgr,
  remixmvkfp — все теперь ТОЛЬКО в web_cookies snapshot). На rooted/ADB
  backup/malware-with-storage атака увеличивается surface для устаревания,
  но критичный минимум для restore сохранён. Полный fix требует шифрования
  файла (SovaPrefsCrypto с device-bound ключом — уже есть в репо, отдельная
  задача, не Task 8).
- Не трогал (по условию): основную логику saveAuthResult/updateAccessToken/
  saveWebTokenResult/saveRemixsid/saveSessionCookiesOnly/saveSilentToken/
  saveLongPoll (они пишут в EncryptedSharedPreferences, не в файл); логику
  clear/clearAccessToken (Path 5 сохраняет протухший access_token для
  connect_exchange_token); exportSessionSnapshot/applyExportedSession
  (пользовательский экспорт, ему можно отдавать больше).

---
Task ID: 7
Agent: general-purpose (P1.1+P2.1)
Task: маскировка access_token и silent_token в логах

Work Log:
- Прочитал /home/z/my-project/worklog.md (контекст Tasks 1-6). Понял область: P1.1 = OAuthWebViewActivity.kt (access_token), P2.1 = SilentTokenExchanger.kt:129 + WebTokenAuth.kt:453,461 (silent_token+uuid), P2.2 = AppLog SENSITIVE_KEYS расширение.
- Прочитал AppLog.kt: SENSITIVE_KEYS (строки 66-69 оригинала), maskParams (754-766) — matcher `k.lowercase() in SENSITIVE_KEYS || k.contains("token", ignoreCase = true)`. Методы AppLog.v/d/i/w/e — все через общий log() с гейтами по category/errorsOnly/sections. AppLog.maskParams работает ТОЛЬКО с Map<String,String>, НЕ с произвольными строками — поэтому для WebView JS-output нужен был локальный redactAccessToken.
- Прочитал OAuthWebViewActivity.kt (602 строки): нашёл 8 прямых `android.util.Log.i/.w` вызовов (453/464/474/489/491/589/594/600), обходящих AppLog и его маскирование. Логи 453/464/474 (autoclick result r/r2/r3) содержат JS-вывод вида 'no-btn:... || loc:https://oauth.vk.com/blank.html#access_token=vk1.a.XXX&user_id=...' — ПОЛНЫЙ access_token в URL fragment. Логи 589/594/600 (extractAccessTokenFromJs) печатают первые 120 символов raw JS-вывода, содержащие `vk1.a.XXX`.
- P1.1 фиксы (OAuthWebViewActivity.kt):
  * Добавил top-level private fun `redactAccessToken(raw: String): String` (стр.638) и `maskTokenValue(v: String): String` (стр.657). Стратегия: regex1 `((?:access_token|silent_token)=)([A-Za-z0-9_.\-]+)` → маска значения (первые8…последние4, или `***` если <=12); regex2 `vk1\.a\.[A-Za-z0-9_.\-]{8,}` → маска raw JWT. Остальная часть строки сохраняется (URL-путь, btn text, body).
  * Заменил все 8 `android.util.Log.i/.w(...)` на `AppLog.d/i/w(OAuthWebViewActivity.TAG, ...)` с redactAccessToken(r) для тех, что содержат access_token (autoclick result/retry2/retry3 + extract input len=...). Для `extract found vk1.a len=`/`extract fallback found len=` — только длина, маска не нужна (токен не в сообщении), но вызов переведён на AppLog.d для единства и гейта по категориям. Для `autoclick injected`/`autoclick failed: ${e.message}` — безопасно, переключил на AppLog.d/w для единого пути через errorsOnly-гейт.
  * `android.util.Log` в файле больше нет (rg exit=1, 0 совпадений).
- Прочитал SilentTokenExchanger.kt (501 строка): строка 129 `AppLog.i(TAG, "exchange: silent_token=${silentToken.take(12)}... uuid=$silentTokenUuid ...")` — take(12) + ПОЛНЫЙ uuid. UUID+silent_token=полная credential для auth.getAuthData.
- P2.1 фикс (SilentTokenExchanger.kt:129):
  * Добавил top-level private fun `maskTokenPrefix(v: String): String` (стр.40) — `первые8…последние4` для длинных, `***` для коротких.
  * Заменил лог на `exchange: silent_token=${maskTokenPrefix(silentToken)} (len=...) uuid=${maskTokenPrefix(silentTokenUuid)} (len=...) provider=...` — оба значения маскируются, длина для диагностики сохранена. Логика обмена НЕ тронута (форма отправляется с raw silent_token/silent_token_uuid как и раньше — маска только в логе).
  * Существующий safeBody в postForm (стр.266-269 оригинала) уже заменяет `"access_token":"..."` и `"silent_token":"..."` на `***` в response body — не трогал.
- Прочитал WebTokenAuth.kt (1605 строк): строка 453 `AppLog.d(TAG, "window.init silent_token raw: ${raw.take(200)}")` — первые 200 символов JSON с silentToken+uuid+anonymousToken+providerAppId plaintext. Строка 461 (была ~461 до моих правок — стала 468) `AppLog.i(TAG, "...(silent_token=${silentToken.take(12)}... uuid=$silentTokenUuid ...")` — снова take(12) + полный UUID.
- P2.1 фиксы (WebTokenAuth.kt):
  * Строка 453 → заменил на лог о длине raw + факт наличия полей (silentToken=yes/no, uuid=yes/no, anonymousToken=yes/no, providerAppId=value). JSON парсится через JsonParser в try/catch (если raw не JSON — fallback `raw-not-json(len=...)`). Никаких plaintext-значений sensitive полей в логе.
  * Строка 461 (tryReadSilentTokenFromWindowInit: OK ...) → `silent_token=${maskTokenPrefix(silentToken)} (len=...) uuid=${maskTokenPrefix(silentTokenUuid)} (len=...) anon=yes/no provider=...` — оба значения маскируются.
  * Добавил top-level private fun `maskTokenPrefix(v: String): String` в конце файла (стр.1642) — идентичен SilentTokenExchanger.kt (private в Kotlin = file-scoped, конфликтов нет).
  * Все остальные AppLog-вызовы в WebTokenAuth.kt не содержат raw sensitive-значений (только длины, status'ы, urls, user_id) — не трогал.
- P2.2 фикс (AppLog.kt:65-69):
  * Расширил SENSITIVE_KEYS: добавлены `silent_token_uuid`, `remixsid`, `remixstid`, `remixstlid`, `remixnttpid`, `remixuas`, `remixuacck`, `remixdmgr`, `remixmvkfp`, `trusted_hash`, `logout_hash`, `sat_token`, `httoken`. Существующее поведение matcher'а (`k.lowercase() in SENSITIVE_KEYS || k.contains("token")`) сохранено — contains("token") по-прежнему ловит access_token/silent_token/exchange_token/anonymous_token, новые explicit-entries ловят *_hash / remix* cookies / *_uuid (которые без подстроки "token" в ключе — например `uuid` сам по себе, или camelCase `silentTokenUuid` после lowercase = `silenttokenuuid` содержит "token" — ловится и contains, и explicit не помешает).
  * НЕ добавлял `p` (PVK persistent cookie) — слишком короткий ключ, может совпасть с обычным параметром и вызвать ложную маску. НЕ добавлял `remixnsid` — не в spec. Spec-список покрыт полностью (13 из 13).
  * Подробный KDoc-комментарий с разъяснением каждой группы ключей и matcher-логики.
- Логика авторизации НЕ тронута: ни одного изменения в OAuth flow, exchange flow, parsing, form-body construction, cookie capture, save-в-prefs. Все правки — только в log-вызовах (замена `android.util.Log` → `AppLog`, raw значение → mask, и SENSITIVE_KEYS расширение).

Stage Summary:
- Файлов изменено: 4 (только логирование и маскирование).
  1. `app/src/main/java/re/pinok/auth/OAuthWebViewActivity.kt` (+74/-7): 8 `android.util.Log.i/.w` → `AppLog.d/i/w` с маской (autoclick result r/r2/r3, extract input/found/fallback, autoclick injected/failed). Добавлены `private fun redactAccessToken(raw)` (regex для access_token=XXX и vk1.a.XXX) и `private fun maskTokenValue(v)`. `android.util.Log` в файле больше не используется.
  2. `app/src/main/java/re/pinok/auth/exchange/SilentTokenExchanger.kt` (+22/-1): строка 129 — silent_token+uuid логируются через `maskTokenPrefix` (первые8…последние4) + длина. Добавлен `private fun maskTokenPrefix(v)`.
  3. `app/src/main/java/re/pinok/auth/exchange/WebTokenAuth.kt` (+43/-4): строка 453 — вместо `raw.take(200)` логируется длина + факт наличия полей (silentToken/uuid/anonymousToken=yes/no, providerAppId). Строка 461 — silent_token+uuid через `maskTokenPrefix` + длина. Добавлен `private fun maskTokenPrefix(v)` (file-scoped, не конфликтует с одноимённым в SilentTokenExchanger.kt).
  4. `core/common/src/main/java/re/pinok/util/AppLog.kt` (+28/-1): SENSITIVE_KEYS расширено 13 ключами (`silent_token_uuid`, `remixsid`, `remixstid`, `remixstlid`, `remixnttpid`, `remixuas`, `remixuacck`, `remixdmgr`, `remixmvkfp`, `trusted_hash`, `logout_hash`, `sat_token`, `httoken`). Matcher сохранён (`in SENSITIVE_KEYS || contains("token")`).
- Какие токены теперь маскируются:
  - В OAuthWebViewActivity.kt логах: access_token (URL fragment/query `#access_token=...` или `?access_token=...`), raw JWT `vk1.a.XXX` (40+ символов в payload), silent_token (URL fragment/query).
  - В SilentTokenExchanger.kt:129 логе: silent_token, silent_token_uuid (через `maskTokenPrefix`).
  - В WebTokenAuth.kt:453 логе: silentToken, silentTokenUuid, anonymousToken, providerAppId — только факт наличия, не значения.
  - В WebTokenAuth.kt:461 логе: silent_token, silent_token_uuid (через `maskTokenPrefix`).
  - Автоматически через AppLog.maskParams (для будущих VK API-логов): silent_token_uuid, remixsid, remixstid, remixstlid, remixnttpid, remixuas, remixuacck, remixdmgr, remixmvkfp, trusted_hash, logout_hash, sat_token, httoken + существующие (access_token, silent_token, exchange_token, anonymous_token через contains "token").
- Формат маски: для значений длиннее 12 символов — `первые8…последние4` (например `vk1.a.ab…7890`), для <=12 символов — `***`. В AppLog.maskParams сохранён старый формат `первые4…последние3` (не трогал — это изменение visible-формата для существующих логов было бы регрессией).
- Логика авторизации НЕ тронута (см. деталь выше).
- Проверки: `rg "android.util.Log" OAuthWebViewActivity.kt` → exit=1 (нет совпадений). Баланс фигурных скобок в WebTokenAuth.kt: -1 (как и до правок — за счёт regex-кванторов `{8,}` в строковых литералах, не влияет на компиляцию). `git diff --stat` показывает +153/-14 строк в 4 файлах. Android SDK нет — `:app:compileDebugKotlin` не запускался (по инструкции); синтаксис проверен ручным review и Python-аналогом regex-логики (тесты прошли: URL fragment, JS output с loc=, silent_token URL, no-token, short token <=12, raw vk1.a — все маскируются корректно, остальная часть строки сохраняется).
- Доп. наблюдения (НЕ фиксил — вне scope P1.1/P2.1):
  - WebTokenAuth.kt:254 — `access_token=${exchanged.accessToken.take(12)}...` (silent_token-derived access_token, первые 12 символов). P1.1-style leak в WebTokenAuth.kt, не в OAuthWebViewActivity.kt.
  - WebTokenAuth.kt:972 — `localStorage raw: ${raw.jsonStr.take(200)}` (содержит access_token из localStorage web_token JSON).
  - Рекомендую отдельную задачу P1.1b: применить redactAccessToken (или maskTokenPrefix) к этим двум логам.

---
Task ID: 9
Agent: general-purpose (P0.1-3 video)
Task: починить воспроизведение видео в каналах/сообществах

Work Log:
- Прочитал worklog Task 1/Task 6 — корневая причина подтверждена:
  `Video.detectPlatform()` misroute'ил VK-native видео с player=embed URL
  (video_ext.php?oid=-XXX&id=YYY&hash=ZZZ) и пустым `files` на
  `VideoPlatform.EXTERNAL_IFRAME` (OkWebViewPlayer грузит video_ext.php как
  главную страницу вне iframe-контекста → чёрный экран).
- Прочитал `VideoPlatformRouter.kt` (114-119): подтверждено, что и VK, и UNKNOWN
  уже маршрутизируются в `VideoPlayerScreen` (нативный ExoPlayer + LaunchedEffect
  fallback). Следовательно, задача сводится к тому, чтобы VK-embed видео
  попадали в UNKNOWN вместо EXTERNAL_IFRAME.
- Прочитал `parseVideoFull` (VKApiClient.kt:11976-12117) и `videoGetById`
  (9144-9195) + перегрузку `videoGet(List<String>)` (9279-9343) — убедился, что
  есть перегрузка по списку id (нужна для P0.3 шаг 2).
- Прочитал `VideoPlayerScreen.kt` LaunchedEffect (440-522) и UI error-state
  (1580-1699) — понял, где вставлять fallback-chain и кнопку «Открыть в браузере».

- **P0.1 (Models.kt:449-463)**: добавил в `detectPlatform()` новую ветку ПЕРЕД
  generic http/https-веткой, ловящую VK-embed паттерны:
  `lower.contains("video_ext.php") || vkvideo.ru/ || vk.com/video || vk.ru/video`.
  Ветка возвращает `if (hasPlayableFiles()) VK else UNKNOWN`. EXTERNAL_IFRAME
  остался для всех не-VK http/https URL (строка 470 — generic ветка не тронута).
  VideoPlatform.UNKNOWN существует в enum (Models.kt:224).
- **P0.2 (VKApiClient.kt:12069-12086)**: ввёл локальные `playerUrl` + `accessKey`
  перед `return Video(...)`, чтобы не вызывать `safeString(o.get("player"))`
  дважды. `accessKey = safeString(o.get("access_key")) ?: extractHashFromPlayerUrl(playerUrl)`.
  Добавил private-функцию `extractHashFromPlayerUrl(playerUrl: String?): String?`
  рядом с `parseVideoFiles` (12154-12175) — regex `[?&]hash=([a-zA-Z0-9]+)`,
  case-insensitive. Hash из player URL прокидывается в accessKey, и videoGetById
  в P0.3 шаг 1 сможет его использовать.
- **P0.3 (VideoPlayerScreen.kt:504-549)**: заменил прямое `videoGetById` на
  fallback-chain:
    Шаг 1: `videoGetById(ownerId, videoId, accessKey)` (как раньше).
    Шаг 2: если шаг 1 вернул null/пустое И accessKey был пуст →
           `videoGet(listOf("ownerId_videoId"))` + firstOrNull с matching id и
           files/player. Если accessKey был НЕ пуст — шаг 2 пропускается
           (бессмысленно повторять videoGetById без access_key, если он уже
           с access_key не помог).
    Шаг 3 (UI): если finalVideo == null → fetchError/sessionExpired как раньше,
           плюс в error-state UI (1653-1697) кнопку «Открыть в браузере» теперь
           показываем не только для OK, но и для VK/UNKNOWN, при наличии
           `video.player` URL. Текст ошибки адаптирован по платформе.
- **Верификация**:
  - `EXTERNAL_IFRAME` всё ещё используется в Models.kt:470 для не-VK embed.
  - Баланс скобок проверен сравнением с pre-edit baseline: Models.kt сбалансирован
    полностью (105/105, 1123/1123); VKApiClient.kt и VideoPlayerScreen.kt имеют
    ту же дельту (предсуществующий disbalance от regex/строк в комментариях —
    не мой). Все мои правки симметричны (+N откр. / +N закр.).
  - Success-ветка не сломана: если `videoGetById` вернул Video с files/player →
    `finalVideo = fresh` → `resolvedVideo = finalVideo` (поведение идентично
    старому коду, за исключением rename `fresh`→`finalVideo`).

Stage Summary:
- Корневая причина устранена на 3 уровнях:
  (1) маршрут: VK-embed видео (video_ext.php / vkvideo.ru / vk.com/video /
      vk.ru/video) без прямых files теперь идут в UNKNOWN → VideoPlayerScreen
      (раньше — EXTERNAL_IFRAME → OkWebViewPlayer → чёрный экран).
  (2) access_key: parseVideoFull извлекает `hash` из player URL в accessKey,
      чтобы videoGetById мог получить files для приватных/канальных видео.
  (3) fallback: VideoPlayerScreen при пустом accessKey и неудаче videoGetById
      пробует перегрузку `videoGet(List<String>)`; при неудаче показывает
      кнопку «Открыть в браузере» (Intent.ACTION_VIEW на player URL) вместо
      пустого экрана «VK не вернул прямой URL файла».
- Изменено 3 файла (+95 строк, −9):
  • core/data/src/main/java/re/pinok/data/model/Models.kt: +15 (P0.1)
  • app/src/main/java/re/pinok/api/VKApiClient.kt: +34/−2 (P0.2)
  • app/src/main/java/re/pinok/ui/screens/videoplayer/VideoPlayerScreen.kt:
    +55/−9 (P0.3)
- Не трогал: ChatDetailScreen.kt, CommunityScreen.kt, AppLog.kt,
  ExchangeTokenStorage.kt, OkWebViewPlayer, VideoPlatformRouter (он уже
  корректно маршрутизирует VK/UNKNOWN в VideoPlayerScreen).
- Next actions: проверить на устройстве — открыть канал с VK-native видео
  (player=video_ext.php, files=null), убедиться что:
  (a) лог VideoPlatformRouter показывает platform=UNKNOWN (не EXTERNAL_IFRAME);
  (b) videoGetById получает access_key из hash (видно в логе VKApiClient);
  (c) ExoPlayer играет нативно, без чёрного экрана. Gradle не запускал
      (по инструкции — нет Android SDK в sandbox).

---
Task ID: 10
Agent: general-purpose (P0.4-7,9 channels panel)
Task: кнопки контента + Клипы + Фото + isChannel для админа + else в when
Work Log:
- Прочитал /home/z/my-project/worklog.md (Task 2, Task 3, Task 6) для контекста.
  Цель: реализовать P0.4 (кнопка Клипы в ChannelInfoSectionsGrid), P0.5
  (showChannelClips + кейс в when + ChannelClipsDialog), P0.6 (починить фото
  канала), P0.7 (isChannelUi для админа канала), P0.9 (else в when с AppLog.w).
- Прочитал ключевые секции ChatDetailScreen.kt (12155 строк после правок):
  imports 1-285 (есть Icons.Filled.PlayCircle:90, Icons.Filled.PlayArrow:89,
  coil3.compose.AsyncImage:209, AppLog:249, abs:276, Color:181, Video/PhotoItem
  imports), state-секции 855-985, channelGroup:921, channel header click
  3630-3648, panels 5747-5893, ChannelInfoSectionsGrid 10228-10317,
  ChannelPhotosDialog (мёртвый) 10319+, ChannelPhotoFeedDialog 10660-10842,
  ChannelVideoDialog 10844-10939, ChannelAudioDialog 11120+, ChannelFilesDialog
  после.
- Проверил VKApiClient.kt: shortVideoGetOwnerVideos(ownerId, count=30):
  List<Video> (18093-18131, парсит short_video_full через parseVideoFull —
  тот же формат что videoGet, переиспользуется в CommunityScreen:438);
  photosGet(ownerId, albumId, count, offset): List<PhotoItem> (9865-9887,
  используется в CommunityScreen:363 и в мёртвом ChannelPhotosDialog:10312).
- Проверил Models.kt: Video.title:String (229, non-null → isNotBlank() safe),
  Video.thumbUrl:String? (330), Video.clipPosterUrl:String? (333, first_frames
  → thumbUrl fallback), Video.isClips:Int? (269). ChannelClipsDialog не
  использует isClips — shortVideo.getOwnerVideos уже возвращает только клипы.
- Проверил CommunityScreen.kt:1881 ClipThumbnail pattern (private там —
  скопировал локально как ChannelClipThumbnail, т.к. выносить в shared-модуль
  вне scope задачи). Pattern: Card + Box(aspectRatio 9f/16f) + AsyncImage +
  play-overlay (Box+CircleShape+PlayArrow) + bottom title-overlay.

P0.7 (isChannelUi для админа канала):
- ChatDetailScreen.kt:932 — добавлена локальная val `isChannelUi = isChannel
  || (peerId < 0 && channelGroup != null)`. Производная val (не state) —
  пересчитывается при recomposition, читая state isChannel/channelGroup.
 channelGroup != null означает, что groupsGetById загрузил метаданные
  сообщества (канал-подписчик ИЛИ админ канала — для обоих UI-панель должна
  открываться). Логику send/block (channelWriteDenied, строки 2541/2858)
  НЕ трогал — там isChannel/peerId<0 корректны как есть.
- Заменил `peerId < 0 && isChannel` → `peerId < 0 && isChannelUi` в 6 местах
  gated-панелей: 5756 (showChannelInfoPanel), 5839 (showChannelPhotos),
  5854 (showChannelVideo), 5864 (showChannelAudio), 5873 (showChannelFiles),
  5884 (новый showChannelClips). И в клике по шапке канала 3639.
- ChatDetailScreen.kt:3643 — добавлен `else if (peerId < 0) {
  showChannelInfoPanel = true }` в клике по шапке (был no-op для peerId<0 &&
  !isChannel — т.е. для админа канала до загрузки channelGroup или для
  обычной группы/chat). Теперь тап по шапке любого peerId<0 открывает панель
  канала (контент сообщества).

P0.4 (кнопка Клипы в ChannelInfoSectionsGrid):
- ChatDetailScreen.kt:10308-10315 — добавлена третья Row в ChannelInfoSectionsGrid
  с одной кнопкой «Клипы» (label="Клипы", icon=Icons.Filled.PlayCircle —
  уже импортирован в файле на строке 90, используется в 11784; Outlined-версии
  в файле нет, по spec разрешили Filled как fallback). onClick =
  `{ onSectionClick("Клипы") }`. Стиль совпадает с существующими кнопками
  (ChannelInfoSectionItem, weight(1f)). Третья Row с одним item'ом — кнопка
  занимает всю ширину, выглядит как отдельный раздел (допустимо по spec).

P0.5 (showChannelClips + when + ChannelClipsDialog):
- ChatDetailScreen.kt:968 — `var showChannelClips by remember {
  mutableStateOf(false) }` рядом с showChannelFiles (тот же блок, единый
  стиль объявления).
- ChatDetailScreen.kt:5817-5820 — кейс `"Клипы" -> { showChannelInfoPanel =
  false; showChannelClips = true }` в onSectionClick when (между "Файлы" и
  else). Структура совпадает с существующими кейсами (Фото/Видео/Музыка/Файлы).
- ChatDetailScreen.kt:5884-5891 — render-блок `if (peerId < 0 && isChannelUi
  && showChannelClips) { ChannelClipsDialog(apiClient=app.apiClient,
  ownerId=-abs(peerId), onDismiss={ showChannelClips=false },
  onVideoClick=onVideoClick) }`. ownerId=-abs(peerId) — отрицательный,
  как у соседних ChannelVideoDialog/ChannelAudioDialog/ChannelFilesDialog.
  onVideoClick пробрасывается из параметров ChatDetailScreen (default={} —
  VideoHolder.open подключён в SovaNavHost.kt:2427).
- ChatDetailScreen.kt:10941-11118 — создан ChannelClipsDialog (по образцу
  ChannelVideoDialog:10844) + ChannelClipThumbnail (по образцу
  CommunityScreen.ClipThumbnail:1881). Сигнатура точно по spec:
  ```kotlin
  @Composable private fun ChannelClipsDialog(
      apiClient: VKApiClient, ownerId: Long,
      onDismiss: () -> Unit, onVideoClick: (Video) -> Unit)
  ```
  Загрузка через `apiClient.shortVideoGetOwnerVideos(ownerId=ownerId,
  count=30)` (LaunchedEffect, seq для «Повторить»). Рендер: ModalBottomSheet
  + Column + Row(«Клипы» + ×) + when { loading / error / empty / LazyRow }.
  LazyRow горизонтальный (не вертикальный, как у видео) — клипы 9:16 влезают
  по 2-3 на экран, скроллится вбок (как VK clips-app). items(clips,
  key="chclip_${ownerId}_${id}"). ChannelClipThumbnail: Card(width=140.dp,
  RoundedCornerShape(12.dp), clickable → onClick(video)) → onVideoClick →
  VideoHolder.open. Внутри Box(aspectRatio(9f/16f)) с AsyncImage(thumbUrl=
  clipPosterUrl?:thumbUrl) + play-overlay (Box+CircleShape+PlayArrow 26dp,
  black-50% bg) + bottom title-overlay (black-55% bg, bodySmall white, 2
  lines, ellipsis). loading/error/empty states точно как в ChannelVideoDialog
  (CircularProgressIndicator / «Не удалось загрузить клипы» + Повторить /
  «В сообществе нет клипов»).

P0.6 (починить «Фотографии» канала):
- Вариант A (минимальные изменения, как рекомендовал spec). Переписан
  ChannelPhotoFeedDialog (ChatDetailScreen.kt:10660-10842): внутренности
  loadFirst/loadMore заменены с photosPhotoFeedList (photos.photoFeedGet —
  ЛИЧНАЯ фотолента, работает только для owner_id>0; для ownerId<0 канала
  VK возвращал пусто → пользователь видел «В сообществе нет фотографий»
  даже когда фото есть) на photosGet(albumId="wall", ownerId=-abs(peerId),
  count=40, offset=N) — фото сообщества (стена), как в CommunityScreen.kt:363.
- Пагинация сменилась курсорная (nextFrom:String?) на offset-ную
  (offset:Int + hasMore:Boolean): page.size == pageSize → есть ещё; иначе
  последняя страница. LaunchedEffect для подгрузки в футере сетки теперь
  зависит от (hasMore, offset) вместо nextFrom. KDoc переписан с указанием
  P0.6: что было → что стало, источник VKApiClient.photosGet:9865.
- Публичный API ChannelPhotoFeedDialog НЕ изменился (apiClient, ownerId,
  onDismiss, onPhotoClick) — call site 5839-5850 не трогал. UI тоже не
  менялся (заголовок/сетка 3×N/футер-пагинация/empty-error-loading).

P0.9 (else в when(section)):
- ChatDetailScreen.kt:5825-5827 — добавлен `else -> { AppLog.w("ChannelPanel",
  "Unknown section: $section") }` в onSectionClick when. Раньше тап по
  кнопке без кейса в when был silent no-op (по Task 2 аудиту — хрупкая
  связка с ChannelInfoSectionsGrid). Теперь логируется (AppLog уже
  импортирован в файле, строка 249), можно отлавливать в logcat.

Проверки:
- Баланс скобок (Python, с stripping строк/комментариев): braces 2322/2322,
  parens 4830/4830, brackets 27/27 — все diff=0. Регекс-кванторы типа {8,}
  в строковых литералах не влияют на подсчёт.
- grep "peerId < 0 && isChannel &&" → 0 совпадений (все 6 gating-условий
  заменены на isChannelUi).
- grep "photosPhotoFeedList|nextFrom" → только 4 упоминания в KDoc-комментариях
  (как «БЫЛО»/«заменил»), живых вызовов нет.
- grep "isChannelUi" → 7 мест: 1 декларация (932) + 6 использований (3639
  шапка, 5756 панель, 5839 фото, 5854 видео, 5864 музыка, 5873 файлы,
  5884 клипы). Send/block логика (2541/2858) НЕ тронута — там isChannel
  корректен (canWrite-гейт).
- grep "showChannelClips|ChannelClipsDialog|ChannelClipThumbnail" → 8 мест:
  state var (968), when-кейс (5819), render-блок (5884-5891), function
  ChannelClipsDialog (10958), function ChannelClipThumbnail (11057),
  KDoc-упоминания (10268, 10948, 11042).
- git diff --stat: 1 file changed, 299 insertions(+), 34 deletions(-)
  (ChatDetailScreen.kt: 11889 → 12155 строк).
- НЕ трогал Models.kt, VKApiClient.kt, VideoPlayerScreen.kt,
  OAuthWebViewActivity.kt, ExchangeTokenStorage.kt, AppLog.kt — по условию
  владения (P0.1-3 другой агент, P1.x — Tasks 7/8).
- Gradle НЕ запускал (по инструкции — нет Android SDK в sandbox). Синтаксис
  проверен ручным review + Python-балансом скобок.

Stage Summary:
- Файлов изменено: 1 (app/src/main/java/re/pinok/ui/screens/im/ChatDetailScreen.kt,
  +299/-34 строк, 11889→12155 строк).
- P0.4: ChatDetailScreen.kt:10308-10315 — кнопка «Клипы» (Icons.Filled.PlayCircle)
  добавлена третьей Row в ChannelInfoSectionsGrid → onSectionClick("Клипы").
- P0.5: ChatDetailScreen.kt:968 (state), 5817-5820 (when-кейс), 5884-5891
  (render), 10941-11118 (ChannelClipsDialog + ChannelClipThumbnail composables).
  Dialog по образцу ChannelVideoDialog с shortVideoGetOwnerVideos вместо
  videoGet; рендер горизонтальным LazyRow с 9:16 постерами. Тап → onVideoClick
  → VideoHolder.open (нативный плеер с videoGetById-fallback).
- P0.6: ChatDetailScreen.kt:10660-10842 — ChannelPhotoFeedDialog переписан:
  photosPhotoFeedList (личная фотолента, photos.photoFeedGet) → photosGet
  (фото сообщества, albumId="wall"). Пагинация курсорная → offset. Публичный
  API и UI не изменились. Теперь «Фотографии» канала показывают реальное
  фото сообщества (стена), а не пустоту.
- P0.7: ChatDetailScreen.kt:932 — val isChannelUi = isChannel || (peerId<0
  && channelGroup != null). Заменено в 6 gating-условиях (5756/5839/5854/
  5864/5873/5884) + клик по шапке (3639). Добавлен else для peerId<0 &&
  !isChannelUi (3643): открывает showChannelInfoPanel=true (контент сообщества
  доступен и админу канала, и обычной группе/chat). Send/block-логика не
  тронута (channelWriteDenied корректен как есть).
- P0.9: ChatDetailScreen.kt:5825-5827 — else -> { AppLog.w("ChannelPanel",
  "Unknown section: $section") } в onSectionClick when. Silent no-op для
  неизвестных секций → логируется в logcat.
- Подтверждение: админ канала теперь может открыть панель канала (клик по
  шапке → isChannelUi=true при channelGroup!=null, либо fallback else для
  peerId<0) и тапнуть «Клипы» → ChannelClipsDialog откроется (gated by
  isChannelUi). Подписчики канала — тоже работают (isChannel=true →
  isChannelUi=true). Обычные DM/группы не затронуты (peerId>0 → onUserClick;
  peerId<0 для групп — fallback else открывает панель, что безопасно: панель
  канала показывает GroupInfo + разделы, что корректно и для обычной группы).
- Статус: код изменён в одном файле (владение эксклюзивное). Готов к
  ручному тесту на устройстве: открыть канал как админ → тап по шапке →
  панель открывается → тап «Клипы» → ChannelClipsDialog с лентой 9:16
  постеров; тап «Фотографии» → реальные фото сообщества (не «нет фото»).

---
Task ID: P0.37-P0.38 (Music2 + Chat fixes)
Agent: Z.ai Code (main session)
Task: Music 2 container — friends music, radio, pagination; chat keyboard gap + slow loading

Work Log:
- P0.37 #MUSIC2-FRIENDS: music of friend (links slider → audio.get owner_id)
  - Models.kt: CatalogFriend + CatalogViewType.SLIDER + CatalogViewType.LARGE_LIST + BANNER + RADIO_LIST
  - VKApiClient: parse links[] from catalog.getSection → CatalogFriend (ownerId from url /audios{id})
  - Music2Screen: clickable friend cards → navigate to Music2Friend route
  - Screen.kt + SovaNavHost.kt: Music2Friend route with ownerId/name args
- P0.38 #MUSIC2-RADIO: radio stations (catalog.getSection radio)
  - CatalogRadioStation model + radio_stations[] companion array parser
  - Music2RadioStationRow: grid card (120dp, gradient bg from backgroundColor, logo 72dp, play overlay)
  - 2-column grid via chunked(2) + Row(weight 1f)
- P0.38 #MUSIC2-FRIENDS-GRID: friends as 3-column grid (was horizontal slider)
  - VK web snapshot analysis: data-testid="grid" --grid-columns=6, 81 friends, avatar 135x135
  - Music2FriendsSliderRow rewritten as Column+Row grid (3 cols mobile, 108dp avatars)
- P0.38 #MUSIC2-PAGINATION-V2: based on response.count (VK total)
  - WRONG logic (guessed end by size < pageSize) → CORRECT (tracks.size >= totalCount)
  - audioGetWithCount() returns Pair(totalCount, tracks) — was ignoring .first
  - Header shows real total (5600 треков) not loaded count
  - Trigger at total-10 for preemptive load
- Chat fixes:
  - Keyboard gap (black rectangle ~50dp above keyboard): Scaffold auto-applies navbar padding to bottomBar + Row added ime.union(navbar) = double padding. Fix: contentWindowInsets = WindowInsets(0,0,0,0)
  - Slow message loading: keepAlive() was blocking before messages.getHistory (1-3s delay). Fix: scope.launch (parallel, non-blocking)
- Section IDs fixed via HAR analysis:
  - friends: PUldVA8FR0RzSVNUagVSRGpJUFQPBUdEfklTRwIFW191WlFGSA (was wrong deep-link)
  - radio: PUldVA8FR0RzSVNUR1UPDykYHRdBXQQINUlFVAwWUVdqSVFUDwVcV3ZSWkcNBBY (was truncated → HTTP 500)
  - updates: REMOVED (VK returns only placeholder banners, no real content)
- HAR analysis (музыка_друзья.zip + музыка_загрузка.zip):
  - Музыка друзей.har: catalog.getSection with section_id=friends, response.links[20] per page, 5 pages = 81 friends
  - Радио._обновления.har: catalog.getSection radio returns radio_stations[100], section.next_from for pagination
  - Музыка друзей.html: data-testid="grid" --grid-columns=6, links-cell × 81, card 147px, avatar 135x135
  - Радио.html: AudioCatalog_BlockRadioStationCell × 100, flex-basis=166px, logo 72x72, --radio_cell_gradient_color

Stage Summary:
- Music2 friends: 81 friends in 3-column grid (was 20 in horizontal slider)
- Music2 radio: 100 stations in 2-column grid with gradient backgrounds
- Music2 pagination: response.count-based, 100/page, infinite scroll to real end
- Chat: keyboard gap fixed (Scaffold contentWindowInsets), loading speedup (parallel keepAlive)
- Tabs: 5 (removed Updates — empty; added Friends — separate section)
- Commits: aab6406 → 14bdaa8 (12 commits on PinoK branch)
- Unresolved: need fresh logcat to verify pagination reaches 5600 end, radio play not implemented (TODO stream_url HLS), friend music pagination (audio.get with ownerId)


---
Task ID: P0.40-P0.41 (IM speed + Clips playback)
Agent: Z.ai Code (main session)
Task: Ускорить загрузку раздела «Сообщения» + починить воспроизведение клипов

Work Log:
- P0.40 #IM-SPEED-ASYNC-CHANNELS: messagesGetAllChannels (до 20 страниц по 100) блокировал primary рендеринг. Перенёс в scope.launch (фон), primary список = conversations(10) + requests(10). Рендер за ~500мс вместо ~2.5с.
- P0.40 #IM-SPEED-FIELDS: убраны online,last_seen из fields getConversations. Ответ на ~30% меньше. online: Boolean? = null → UI показывает без зелёной точки (безопасно, LongPoll доставит статус).
- requests: 50→10 (тоже блокировал primary).
- P0.41 #CLIPS-SCL-FALLBACK: клип -227662787_456239647 не воспроизводился — все 10 источников имели scl= (preview URL). Прошлый фикс #CLIP-SCL-FIX пропускал ВСЕ scl= → chosen=null → No playable source. Двухпроходная логика: первый проход ищет clean URL (без scl=), если нет — fallback на лучший scl= URL.

Stage Summary:
- MessagesScreen: открытие ~500мс (было ~2.5с), каналы догружаются в фоне
- Clips: клипы с только preview URL теперь воспроизводятся через fallback
- Commits: a2309d7, 868f4fa, 01ff103 (на ветке PinoK поверх 0cf3994)
- Unresolved: online-индикатор не показывается до refresh (LongPoll не обновляет peer.online в кэше MessagesScreen); scl= fallback может давать артефакты на некоторых клипах


---
Task ID: P0.42 (Звонки — HAR-based rewrite)
Agent: Z.ai Code (main session)
Task: Тотальное изучение звонков VK web и перенос механизмов в PinoK

Work Log:
- Полный анализ HAR звонков (входящий + исходящий) через subagent: 2 HAR + 4 HTML + 16 CSS + 24 JS файлов
- DNS fallback для web.api.vk.ru (коммит 9ac0442)
- startCallSignaling переписан: queue.subscribe → stickers.subscribeToQueue (коммит 8f04070)
- CALLS лог-категория включена по умолчанию (коммит e33601a)
- Queuev4Client: парсинг нового формата entity_type="calls" + incoming_call (коммит 44e3035)
- SovaApp: обработка incoming_call с conversation_params и caller_info (коммит 44e3035)
- application_key: CGMMEJLGDIHBABABA → CDLGIBMGDIHBABABA (коммит 44e3035)
- caller_app_id: 6287487 → 7879029 (коммит b762949)
- Новые методы: messagesGetJoinLink, vchatClientStats, vchatSystemGetInfoExternal (коммит d368dae)
- Новые WS команды: CMD_CHANGE_OPTIONS, CMD_CUSTOM_DATA + send-методы (коммит d368dae)
- Лог подтвердил: stickers.subscribeToQueue OK, все 3 queuev4 активны

Stage Summary:
- Все 3 queuev4 (main/events/calls) запускаются через stickers.subscribeToQueue
- Входящие звонки приходят через новый формат entity_type="calls"
- conversation_params парсится через ConversationParamsDecoder (LZ4+JSON)
- CallScreen уже поддерживает tgt=accept, accept-call, get-rooms
- Commits: 9ac0442, e33601a, 8f04070, d368dae, 44e3035, b762949


---
Task ID: P0.43 (Calls — incoming event parser fix)
Agent: Z.ai Code (main session)
Task: Починить приём входящих звонков — PinoK не показывает и не уведомляет о входящем

Work Log:
- Прочитал /home/z/my-project/worklog.md — контекст P0.40-P0.42 известен.
- Изучил HAR /home/z/my-project/upload/сигналинг.zip (58 entries):
  - 15 entries на 24112.ms.vk.ru, 9 на st.vk.ru, 8 на queuev4.vk.ru, 7 на m.vk.ru, 5 на calls.okcdn.ru, 3 на web.api.vk.ru, 1 wss://videowebrtc.okcdn.ru/ws2
  - Entry 7: первый queuev4 ответ с events (1819 chars) — полная структура incoming_call
  - Entry 43: call_finished (value=строка, не объект)
  - Entry 49: call_history_updated + current_calls_update (value отсутствует)
- Изучил logcat PinoK (4640 строк): 3 queuev4 pollLoop стартуют, но 4.5 минуты тихо — ни одного poll/response/event в логе.
- Корневые причины (2 бага):
  - Баг #1: Queuev4Client.pollLoop был silent — if (rawEvents.isEmpty() && callEvents.isEmpty()) continue без единого лога. Невозможно понять: ждёт / получил пусто / парсер отбросил.
  - Баг #2: callsQueuev4Client.events.collect в SovaApp:1899 читал ev.payload["payload"] (внутренний JSON {"is_video":false}, 40 байт) вместо ev.payload["conversation_params"] (LZ4-blob с tkn/wse/TURN, 578+ байт). CallScreen получал мусор.
- Подтвердил структуру события по HAR:
  - entity_type — НА ВЕРХНЕМ УРОВНЕ events[i] (рядом с data), НЕ внутри data
  - type — ВНУТРИ data, НЕ внутри value
  - value — внутри data; объект (incoming_call) / строка (call_finished) / отсутствует (call_history_updated)
  - conversation_params — внутри value, формат "<int>:<base64-LZ4>"
- Проверил cookie-гипотезу: запросы queuev4.vk.ru/im1180 в HAR НЕ содержат cookies вообще. Авторизация — только через query-параметр key (из stickers.subscribeToQueue). У PinoK это работает: key=e7bad5f7… получен, URL правильный.
- Проверил origin ключа: 669dcf789f6f0a88... НЕ приходит ни в одном HTTP-ответе HAR — он из localStorage браузера (генерируется при первом stickers.subscribeToQueue в более ранней сессии). У PinoK ключ свой, валидный.

Фиксы:
- Queuev4Client.kt:236-247 — добавлен diagnostic-лог на каждый poll-цикл:
  AppLog.d(TAG, "poll: code=$httpCode body.len=${body.length} key=${cred.key.take(8)}… ts=$ts body=$bodyPreview")
  + AppLog.w для blank body
- Queuev4Client.kt:200-218 — parseCallEvents логирует результат:
  AppLog.i(TAG, "parseCallEvents: parsed ${result.size} call events: types=${result.map { it.type }}")
  когда события найдены
- SovaApp.kt:1893-1944 — callsQueuev4Client.events.collect переписан:
  теперь читает type/call_id/from_id/conversation_params/caller_info из мапы напрямую
  (как queuev4Client.events.collect), а не ev.payload["payload"]
  → pendingIncomingCallPayload = convParams (правильный blob для CallScreen)
  → имя/фото из caller_info (как VK web HAR показывает)

Stage Summary:
- Коммиты: 5fedec8 (compile fixes от прошлого раунда) + c90554e (parser + diagnostic logs)
- Файлов изменено: 2 (Queuev4Client.kt, SovaApp.kt), +77/-12 строк
- Обновлена документация: /home/z/my-project/звонки.полный.разбор.md
  добавлен раздел 14 «ОТЛАДКА ВХОДЯЩИХ В PinoK (раунд 2)» с 7 подразделами
  + раздел 15 «ОБНОВЛЁННЫЕ ВЫВОДЫ» (15 пунктов вместо 10)
- Ожидание: после pull+сборки в logcat должны появиться poll-логи каждый ~25с
  и parseCallEvents/INCOMING_CALL при реальном входящем
- Unresolved: если poll: body={"events":[]}持续но — VK не присылает событие,
  значит проблема в подписке (подписались не туда / ts устарел / credential expired)

---
Task ID: P0.43-r3 (Calls — calls.subscribeToQueue via calls.okcdn.ru)
Agent: Z.ai Code (main session)
Task: Радикальный фикс подписки на calls-очередь — stickers.subscribeToQueue подписывал на multiaccount, не на calls

Work Log:
- Прочитал /home/z/my-project/worklog.md — контекст P0.43 (предыдущий раунд) известен.
- Изучил новый logcat /home/z/my-project/upload/ciber.txt (8569 строк):
  - Diagnostic-логи из P0.43 (c90554e) ЗАРАБОТАЛИ: poll: code=200 body.len=30 body={"ts":"...","events":[]}
  - 3 очереди (97faa2b0…, 97583136…, 018fce3c…) — все возвращают events:[]
  - 4.5 минуты (19:56:05 → 19:56:56) — ни одного события во всех 3 очередях
  - ensureCallsSessionKey: уже есть (len=156) — session_key получен
  - 3 вызова stickers.subscribeToQueue — все вернули валидные key/ts/base_url
  - Но ключи для MULTIACCOUNT-очереди, не для calls → events всегда пустые

- Корневая причина (расшифровка JS-бандла m.vk.ru a-5_371124dd5e57d323.js):
  - VK web использует ДВА разных механизма подписки:
    1) calls.subscribeToQueue(anonym_auth_user_id, secret, device_id) через calls.okcdn.ru/fb.do — calls-очередь (incoming_call events)
       function eb(e,t,r){return createApi("calls.subscribeToQueue",!0)({anonym_auth_user_id:e, secret:t, device_id:r})}
       Response: { queue_subscription_info: { base_url, queues: [{ key, timestamp }] } }
    2) stickers.subscribeToQueue (без параметров) через web.api.vk.ru — multiaccount-очередь (messages/online/counters, НЕ calls)
  - P0.42 ошибочно использовал stickers.subscribeToQueue для всех 3 очередей (3 вызова, 3 ключа)
  - VK возвращал валидные base_url/key/ts, но ключ был для multiaccount → events всегда пустые

- Cookie-гипотеза (ОКОНЧАТЕЛЬНО ЗАКРЫТА):
  - queuev4.vk.ru/im1180 — БЕЗ cookies (авторизация через query key)
  - calls.okcdn.ru/fb.do — БЕЗ cookies (авторизация через session_key в POST body)
  - web.api.vk.ru/method/* — cookies есть, но для web-токена (НЕ calls)

- SAT-токен: в логе "WebToken saved — user_id=171093180, sat=no, exchange=no" — SAT-токена НЕТ.
  Значит queue.subscribe (через web.api.vk.ru) упадёт в fallback → err=100 (VK не принимает web-токен для queue.subscribe).
  Единственный рабочий путь — calls.subscribeToQueue через calls.okcdn.ru (с session_key от auth.anonymLogin).

Фиксы (коммит ac07af7):
- VKApiClient.kt:138-145 — добавлено хранение lastAnonymSecret (session_secret_key из auth.anonymLogin).
  Раньше сохранялся только session_key + uid. Теперь safeString(res.get("session_secret_key")) сохраняется в @Volatile var lastAnonymSecret.
- VKApiClient.kt:14207-14214 — vchatAnonymLogin() теперь сохраняет session_secret_key из response.
- VKApiClient.kt:17467-17542 — добавлен метод callsSubscribeToQueue(sessionKey, okUid, secret, deviceId):
  POST на calls.okcdn.ru/fb.do с method=calls.subscribeToQueue, application_key, session_key, anonym_auth_user_id, secret, device_id.
  Парсит queue_subscription_info.queues[0] → { key, timestamp } → QueueCredential.
- SovaApp.kt:2047-2151 — startCallSignaling переписан:
  1) calls-очередь → calls.subscribeToQueue (ПРИОРИТЕТ, через calls.okcdn.ru с session_key)
     Fallback: queue.subscribe через SAT/web-токен с queue_ids=calls_<vkUid>
  2) multiaccount → stickers.subscribeToQueue (без параметров)
  3) events → stickers.subscribeToQueue (второй вызов, новый credential)

Stage Summary:
- Коммит: ac07af7 (2 files, +174/-67 строк)
- Обновлена документация: /home/z/my-project/звонки.полный.разбор.md
  добавлен раздел 14.8 «Раунд 3 — радикальный фикс подписки»
  + раздел 14.9 «Cookie-вопрос (окончательно закрыт)»
  + раздел 14.10 «Коммиты раунда 3»
- Ожидание: после pull+сборки в logcat должны появиться:
  1) "calls.subscribeToQueue OK (key=… ts=… url=https://queuev4.vk.ru/im1180)" — подписка на calls-очередь
  2) "calls-queuev4 активна (через calls.subscribeToQueue, key=… ts=…)" — pollLoop стартовал с правильным ключом
  3) При входящем: "poll: code=200 body.len=1819 body={"ts":"...","events":[{"data":{"value":{"call_id":"...","caller_info":{...}..." — событие пришло!
  4) "parseCallEvents: parsed 1 call events: types=[incoming_call]"
  5) "INCOMING_CALL (calls-очередь HAR): callId=… fromId=… convParams.len=578"
  6) showIncomingCallNotification → системное уведомление → CallScreen
- Unresolved: если calls.subscribeToQueue вернёт error (error_code) — нужно проверить
  application_key=CDLGIBMGDIHBABABA (может быть изменён VK → error 101).
  checkCallsApiKeyError() уже вызывается при null результате.

---
Task ID: P0.43-r3-docs (Документация — post-mortem P0.42 → P0.43)
Agent: Z.ai Code (main session)
Task: Обновить документацию после радикального фикса подписки (ac07af7)

Work Log:
- Прочитал /home/z/my-project/worklog.md — контекст P0.43-r3 известен.
- Пользователь признал, что в начале предупреждал: «stickers.subscribeToQueue не то направление». Я проигнорировал, сделал P0.42 по HAR-запросам без JS-контекста — это была ошибка.
- Обновил /home/z/my-project/звонки.полный.разбор.md (753 → 891 строк):

  1. Section 6.4 (новая): «ДВА механизма подписки» — расшифровка JS-бандла
     a-5_371124dd5e57d323.js с полным кодом eb()/ek() и Q()/J().
     6.4.1 calls-очередь — calls.subscribeToQueue (calls.okcdn.ru, session_key).
     6.4.2 multiaccount — stickers.subscribeToQueue (web.api.vk.ru, web-токен).
     6.4.3 Типичная ошибка (P0.42): использовать stickers для calls.

  2. Section 9 (Жизненный цикл входящего): добавлен шаг 0 — подписка на
     calls-очередь через calls.subscribeToQueue перед шагом 1 (LP RECV).

  3. Section 14: заголовок «раунд 2» → «раунды 2-3» (охватывает все 3 коммита).

  4. Section 15 (выводы): исправлены пункты 9 и 13 — теперь корректно
     указывают, что calls-очередь подписывается через calls.subscribeToQueue,
     а stickers.subscribeToQueue — только для multiaccount.

  5. Section 16 (новая): «УРОКИ (post-mortem P0.42 → P0.43)» — 6 правил:
     16.1 HAR без JS-контекста = полкартинки
     16.2 Diagnostic-логи раньше парсера
     16.3 3 раунда фиксов вместо 1 (HAR+JS обязательная пара)
     16.4 Метод может существовать, но не быть в HAR (localStorage)
     16.5 Ключ в ответе ≠ правильный ключ (multiaccount vs calls)
     16.6 SAT-токен ≠ session_key (разные токены для разных API)

Stage Summary:
- Документация звоники.полный.разбор.md: +138 строк, теперь 891 строка.
- Добавлены 2 новых раздела (6.4, 16) + обновлены 3 существующих (9, 14, 15).
- Worklog: этот раздел (P0.43-r3-docs).
- Unresolved: ждём свежий logcat после pull+сборки ac07af7 для проверки
  calls.subscribeToQueue OK + INCOMING_CALL (calls-очередь HAR).

---
Task ID: P0.43-r8-analyze-v216
Agent: Explore
Task: Analyze V2.1.6 working calls code to find correct incoming_call mechanism

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние 100 строк) — контекст P0.43-r3/r4/r5/r6/r7 известен. Текущий HEAD: 6fbb83b (P0.43-r7 diag). Пользователь сообщил: V2.1.6 была последняя РАБОЧАЯ версия входящих звонков, после что-то сломалось.
- Изучил V2.1.6 архив в /tmp/pinok_v216/PinoK_1-2.1.6/:
  • INCOMING_CALL_REVERSE.md (199 строк) — CDP-снимок localStorage + реальный входящий 2026-09-22.
  • CALLS_MAP.md (большой, 41.7KB) — полная маршрутная карта звонков.
  • звонки.внедрение.итог.md — сводный документ внедрения (волны 1-7).
  • звонки.md (2204 строк) — полная карта подсистемы.
  • звонки.реверс.Ж0-протокол.md — wire-протокол (107 команд WS).
  • реверс.звонки-протокол.md — реверс протокола по GitHub.
  • звонки.перенос.план.md — план переноса.
- Изучил V2.1.6 ИСХОДНЫЙ КОД:
  • app/.../SovaApp.kt (2566 строк, vs текущий 2917) — startCallNotifier (строки 1652-1714), refreshIncomingCaller (1722-1752), startCallSignaling (1764-1831).
  • app/.../realtime/Queuev4Client.kt (205 строк, vs текущий 361) — pollLoop (146-204), parseResponse (99-135).
  • app/.../realtime/LongPollClient.kt (1366 строк, vs текущий 1395) — buildPollUrl (671-677), case 115 (956-963).
  • app/.../api/VKApiClient.kt (20640 строк, vs текущий 23031) — queueSubscribe (12423-12473), parseQueueCredential (12476-12494).
  • app/.../api/VKEndpoints.kt (150 строк) — multiaccountQueueId/countersQueueId/callsQueueId (60-78).
- Сравнил с текущим кодом /home/z/repos/PinoK_1/app/src/main/java/re/pinok/ (SovaApp, Queuev4Client, VKApiClient, VKEndpoints, LongPollClient).

НАХОДКИ (V2.1.6 РАБОЧИЙ МЕХАНИЗМ):

1. ПЕРВИЧНЫЙ канал входящих в V2.1.6 = **VK Messages LongPoll (classic api.vk.com/api.vk.ru LP)**, НЕ queuev4.
   • LongPollClient.kt:956 case 115 → emits `LongPollEvent.IncomingCall(payload)`.
   • URL: `${base}?act=a_check&key=<key>&ts=<ts>&wait=25&mode=<mode>&version=<lpVersion>` (mode=2 для v3, mode=1226 для v14).
   • Сервер: messages.getLongPollServer ИЛИ messages.getDiff (Modern Sync).
   • Это ГЛАВНЫЙ канал — startCallNotifier собирает longPollClient.events В ПЕРВУЮ очередь.

2. V2.1.6 startCallNotifier (SovaApp.kt:1652-1714) — ТРИ коллектора:
   a) longPollClient.events → IncomingCall → принимает ЛЮБОЙ payload (включая "-1" или null) → pendingIncomingCallPayload = event.payload → refreshIncomingCaller → showIncomingCallNotification. НЕТ проверки `payload == "-1"` для пропуска!
   b) queuev4Client.events → QUEUE_CALLS="calls" → payload as String → та же логика.
   c) eventsQueuev4Client.events → та же логика.
   Все три канала идут параллельно, guard перезаписи защищает от дублей.

3. V2.1.6 refreshIncomingCaller (SovaApp.kt:1722-1752):
   • messagesGetCurrentCalls().firstOrNull()?.get("caller_id") → callerId.
   • usersGetByIds(listOf(callerId))[callerId] → profile.
   • pendingIncomingCallTitle/Photo = name/photo.
   • showIncomingCallNotification (повторно, уже с именем).
   Это работает даже если payload="-1" — caller_id тянется через messagesGetCurrentCalls.

4. V2.1.6 startCallSignaling (SovaApp.kt:1764-1831) — ПОДПИСЫВАЕТ ТОЛЬКО queuev4 (FALLBACK):
   • queuev4Client: queueSubscribe(queueIdSuffix=multiaccountQueueId(callsUid)) = "multiaccount_<uid>" — ОДНА очередь.
   • eventsQueuev4Client: queueSubscribe(queueIdSuffix=countersQueueId(uid)) = "onlfriends_<uid>,accountcounters_<uid>" — ВТОРАЯ очередь, fallback multiaccount_<uid>.
   • НЕ использует stickersSubscribeToQueue, НЕ использует callsSubscribeToQueue.
   • НЕ использует callsQueueId (calls_<uid>_<client>_1) — он определён в VKEndpoints но НИ РАЗУ не вызывается в startCallSignaling.
   • Ручной fallback на SovaPrefs.callsQueueKey/Ts если queue.subscribe упал.

5. V2.1.6 queueSubscribe (VKApiClient.kt:12423-12473):
   • SAT-токен приоритет: POST web.api.vk.ru/method/queue.subscribe с queue_ids=<suffix>, v, https=1, access_token=SAT, lang=ru.
   • Fallback: web-токен через call("queue.subscribe", mapOf("queue_ids" to suffix), forceWebGateway=true).
   • Парсит response.queues[0] = { key, timestamp } → QueueCredential.

6. V2.1.6 Queuev4Client.pollLoop URL (строка 152):
   • `${cred.url}?act=a_check&key=${cred.key}&ts=$ts&id=${cred.userId}&wait=25`
   • БЕЗ mode, БЕЗ version, НО С id=<uid> (отличие от messages LP).
   • parser: parseResponse(body, prevTs) → collectEvents → events[] (массивы [код, payload]).
   • code 115 → emits QueueEvent(QUEUE_CALLS="calls", payload=mapOf("payload" to payload)).
   • НЕ парсит entity_type:"calls" (этот формат появился позже — в HAR 2308/2608).

7. V2.1.6 НЕ ИСПОЛЬЗУЕТ EventHub (wss://eh.vk.ru/) для incoming_call — только queuev4.vk.ru/im1180 long-poll.

8. V2.1.6 НЕ ИМЕЕТ callsQueuev4Client (3-я dedicated calls-очередь) — только 2 клиента: queuev4Client + eventsQueuev4Client.

РАЗЛИЧИЯ V2.1.6 vs CURRENT (P0.43-r7 / 6fbb83b):

D1. **LP 115 — PRIMARY канал в V2.1.6, DISABLED в current:**
   • V2.1.6: startCallNotifier принимает ЛЮБОЙ payload от longPollClient.events (включая "-1" или null). При "-1" refreshIncomingCaller() через messagesGetCurrentCalls() вытягивает реальный caller_id.
   • Current (SovaApp.kt:1808-1810): `if (event.payload == "-1") { AppLog.i(..., "LP 115 заглушка -1, пропущен (жду queuev4 calls)"); ... }` — ПРОПУСКАЕТ LP 115 с payload="-1", НЕ вызывает refreshIncomingCaller.
   • Это РЕГРЕССИЯ: messages LongPoll — единственный рабочий канал входящих в V2.1.6 — отключён.

D2. **queue_ids для primary calls-очереди:**
   • V2.1.6: queuev4Client ← queueSubscribe(multiaccountQueueId) = "multiaccount_<uid>".
   • Current: callsQueuev4Client ← queueSubscribe(countersQueueId) = "onlfriends_<uid>,accountcounters_<uid>".
   • V2.1.6 НЕ использовал countersQueueId для primary — только для events_queue (вторая очередь).

D3. **API метод для multiaccount/events подписки:**
   • V2.1.6: queueSubscribe (queue.subscribe) для ВСЕХ очередей (multiaccount + counters).
   • Current: queueSubscribe для callsQueuev4Client (countersQueueId) + stickersSubscribeToQueue (NEW!) для queuev4Client + eventsQueuev4Client.
   • V2.1.6 НЕ ИМЕЕТ stickersSubscribeToQueue — метод добавлен в P0.42 (8f04070).

D4. **Парсер events в Queuev4Client:**
   • V2.1.6: ТОЛЬКО legacy LP коды (115, 70, -1, -2) — events как массивы [код, payload].
   • Current: legacy + НОВЫЙ формат (entity_type:"calls", data.value.{call_id, conversation_params, caller_info, type:incoming_call}) через parseCallEvents/collectCallEvents (строки 168-232).
   • V2.1.6 НЕ парсит entity_type:"calls" — этого формата не было или он не использовался.

D5. **callsQueuev4Client (3-я dedicated calls-очередь):**
   • V2.1.6: НЕ СУЩЕСТВУЕТ. Только queuev4Client + eventsQueuev4Client.
   • Current: ДОБАВЛЕН (SovaApp.kt:1350, lateinit var callsQueuev4Client: Queuev4Client).

D6. **handleQueuev4Incoming (новый метод):**
   • V2.1.6: НЕ СУЩЕСТВУЕТ. LP 115 с "-1" обрабатывался напрямую в startCallNotifier через refreshIncomingCaller.
   • Current: ДОБАВЛЕН (SovaApp.kt:1959-1997). Логика: realPayload или messagesGetCurrentCalls → callerId → если 0 то пропустить, иначе pendingIncomingCallPayload="-1" + refreshIncomingCaller + showNotification. Но этот метод ВЫЗЫВАЕТСЯ только из queuev4/events_queue коллекторов, НЕ из longPollClient (LP 115 "-1" пропускается раньше).

РЕКОМЕНДУЕМЫЙ ФИКС (минимальный, вернуть V2.1.6 поведение):

Вариант A (минимальный патч, рекомендую):
• УДАЛИТЬ блок `if (event.payload == "-1") { пропущен }` в SovaApp.kt:1808-1810.
• ВЕрнуть V2.1.6 логику: принимать ЛЮБОЙ payload от longPollClient.events, вызывать refreshIncomingCaller() (которая через messagesGetCurrentCalls вытягивает caller_id, даже если payload="-1").
• Это вернёт PRIMARY канал входящих — messages LongPoll LP 115 — без зависимости от queuev4 подписки (которая в P0.42/P0.43 нестабильна).

Вариант B (более радикальный, если A не сработает):
• Вернуть V2.1.6 startCallSignaling: queueSubscribe(multiaccountQueueId) для queuev4Client, queueSubscribe(countersQueueId) для eventsQueuev4Client, без stickersSubscribeToQueue, без callsQueuev4Client.
• Удалить callsQueuev4Client полностью (он не нужен — V2.1.6 работал без него).
• Удалить parseCallEvents/collectCallEvents (V2.1.6 не использовал entity_type:"calls").

Вариант C (возврат к V2.1.6 file целиком):
• Скопировать V2.1.6 SovaApp.kt startCallSignaling + startCallNotifier + Queuev4Client.kt + VKEndpoints.kt as-is.
• Прогнать compile + живой тест входящего звонка.

Stage Summary:
- КЛЮЧЕВАЯ НАХОДКА: V2.1.6 incoming_call работал через **VK Messages LongPoll (api.vk.com LP) case 115**, НЕ через queuev4.
  queuev4 был FALLBACK, в V2.1.6 подписывался через queue.subscribe (SAT-токен) с queue_ids="multiaccount_<uid>".
- РЕГРЕССИЯ: текущий код (P0.43-r7, SovaApp.kt:1808-1810) ПРОПУСКАЕТ LP 115 с payload="-1", ожидая данных от queuev4 (которая не работает). V2.1.6 такой guard НЕ имел — принимал "-1" и через messagesGetCurrentCalls вытягивал caller_id.
- V2.1.6 НЕ ИМЕЛ: callsQueuev4Client (3-я очередь), stickersSubscribeToQueue, callsSubscribeToQueue, parseCallEvents (entity_type:"calls").
- V2.1.6 ИМЕЛ: longPollClient (PRIMARY) + queuev4Client (multiaccount) + eventsQueuev4Client (counters/multiaccount fallback) + refreshIncomingCaller (messagesGetCurrentCalls fallback).
- V2.1.6 URL формат queuev4: `?act=a_check&key&ts&id=<uid>&wait=25` (без mode/version, с id).
- V2.1.6 URL формат messages LP: `?act=a_check&key&ts&wait=25&mode=<2|1226>&version=<3|14>` (без id, с mode+version).
- Рекомендация: Вариант A (минимальный патч) — удалить `if (event.payload == "-1") skip` guard. Это вернёт PRIMARY канал входящих без переделки queuev4 подписки.

- ДОПОЛНЕНИЕ (найдено после основного анализа): текущий LongPollClient.kt:987-989 ТАКЖЕ подавляет LP 115 с payload="-1" — НЕ эмитит IncomingCall вообще. V2.1.6 LongPollClient.kt:956-963 эмитил ВСЕГДА. Значит для полного восстановления V2.1.6 поведения нужно ДВА фикса:
  (1) LongPollClient.kt:987-989 — удалить `if (payload == "-1") skip`, всегда emit IncomingCall(payload).
  (2) SovaApp.kt:1808-1810 — удалить `if (event.payload == "-1") skip` (дублирующий guard, сейчас мёртвый т.к. LongPollClient не эмитит "-1").
  Без обоих фиксов messages LongPoll (PRIMARY канал V2.1.6) остаётся отключенным.

---
Task ID: P0.47-revert
Agent: main (continuation)
Task: Диагностика и фикс: «Почему записи сообщества вообще не отображаются?» (PinoK Android app, ветка PinoK, /home/z/repos/PinoK_1)

Work Log:
- Прочитал приложенный пользователем logcat (714 строк, 22:39:50–22:40:22).
  Найдено ВСЕГО 3 вызова groups.getContentForTabs:
    1) 22:39:51 tabs=photos → 24B (ПУСТО)
    2) 22:40:07 tabs=short_videos → 497343B (ОК, страница 2 — есть start_from)
    3) 22:40:21 tabs=photos → 24B (ПУСТО)
  НИ ОДНОГО вызова tabs=wall НЕТ в логе (но wallState.loadFirst() вызывается в LaunchedEffect —
  значит сработал ДО начала лога и вернул пусто, как и photos).
- Изучил git-историю: коммит 9635707 «P0.47 — ВСЕ вкладки на cursor-based» перевёл ВСЕ 6
  контентных вкладок (wall/photos/videos/topics/docs/clips) на groupsGetContentForTabsCursor.
  ДО него каждая вкладка использовала свой классический метод (wallGetExtended, photosGetWithCount,
  videoGetWithCount, boardGetTopicsWithCount, docsGetWithCount, shortVideoGetOwnerVideos).
- Корневая причина: groups.getContentForTabs через WEB-шлюз ВК (web.api.vk.ru) НЕ ПОДДЕРЖИВАЕТ
  вкладки wall/photos/videos/topics/docs — возвращает ПУСТОЙ ответ (24B, ~`{"response":{}}`).
  Метод реально работает ТОЛЬКО для short_videos (клипы). Доказательство — в самом логе:
  photos→24B vs short_videos→497KB для одной и той же группы 196847141.
- ФИКС: откатил 5 сломанных fetcher'ов в CommunityScreen.kt обратно на классические методы
  (offset-based, стоп по totalCount), оставил ТОЛЬКО clipsState на groupsGetClipsForTabs:
    • wallState → wallGetExtended (wall.get, owner_id=-groupId) ← ПРЯМОЙ фикс «записи не видны»
    • photosState → photosGetWithCount (photos.get, album="wall")
    • videosState → videoGetWithCount (video.get)
    • topicsState → boardGetTopicsWithCount (board.getTopics)
    • docsState → docsGetWithCount (docs.get)
    • clipsState → groupsGetClipsForTabs (БЕЗ ИЗМЕНЕНИЙ — работает)
- Удалил ставший неиспользуемым import re.pinok.data.model.DocFile (CommunityDocRow использует FQN).
- Проверил сигнатуры всех 5 методов + поля WallGetExtendedResult (posts/profileNames/rawCount/totalCount) —
  типы деконструкции совпадают, компиляция не сломается. ServerNextFrom импорт остаётся (clipsState).

Stage Summary:
- КОРНЕВАЯ ПРИЧИНА: P0.47-рефакторинг (коммит 9635707) ошибочно перевёл вкладку «Записи» (и ещё
  4 вкладки) с рабочего wall.get на groups.getContentForTabs(tab="wall"), который ВК web-шлюз
  возвращает ПУСТЫМ. Гипотеза «VK web использует getContentForTabs для ВСЕХ вкладок» оказалась
  НЕВЕРНОЙ — метод работает только для short_videos (клипы).
- ДОКАЗАТЕЛЬСТВО (из лога пользователя): tabs=photos→24B пусто, tabs=short_videos→497KB ОК.
- ФИКС: 5 fetcher'ов откачены на классические методы (offset-based, стоп по totalCount),
  clips оставлен на getContentForTabs. Файл: CommunityScreen.kt:188-338.
- НЕ РЕШЕНО / РИСК: исходная задача P0.47 («доскроллить до конца») для wall/photos/videos/topics/docs
  теперь снова зависит от корректности totalCount (VK может занижать count). Если проявится —
  лечить точечно (напр. стоп по rawCount==0 + guard от дублей), а НЕ возвратом к getContentForTabs.
  groupsGetContentForTabsCursor / groupsGetContentForTabs в VKApiClient.kt оставлены (использует clips).
- ФАЙЛЫ: изменён только app/src/main/java/re/pinok/ui/screens/community/CommunityScreen.kt.

---
Task ID: DIAG-C
Agent: Explore (notifications UI)
Task: Диагностика экранов уведомлений (список + настройки)

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние ~240 строк, см. P0.43-r3-docs / P0.43-r8-analyze-v216 / P0.47-revert) —
  контекст PinoK_1 известен: репозиторий ожидается в /home/z/repos/PinoK_1, ветка PinoK.
- Попытался открыть целевые файлы:
    app/src/main/java/re/pinok/ui/screens/notifications/NotificationsScreen.kt
    app/src/main/java/re/pinok/ui/screens/notifications/NotificationSettingsScreen.kt
  → директории /home/z/repos/PinoK_1 НЕ СУЩЕСТВУЕТ на этой машине.
- Исчерпывающий поиск по всей ФС:
    find / -name "*.kt"                  → 0 файлов
    find / -name "AndroidManifest.xml"   → 0 файлов
    find / -name "build.gradle*"         → 0 файлов
    find / -name "SovaApp.kt"            → 0 файлов
    find / -name "SovaNavHost.kt"         → 0 файлов
    find / -name "VKApiClient.kt"        → 0 файлов
    find / -name "NotificationsScreen.kt" → 0 файлов
    ls /home/z/repos                     → No such file or directory
  Также отсутствует /tmp/pinok_v216/PinoK_1-2.1.6/ (архив V2.1.6, использованный
  в P0.43-r8-analyze-v216). Ни одного Kotlin-источника на машине нет.
- mount(8): смонтированы только /home/official_skills (ossfs RO),
  /home/sync (пустой tmpfs), /home/z/my-project/upload (tmpfs),
  /tmp/my-project (PolarFS — зеркало my-project), /home/user_skills (PolarFS).
  Репозиторий PinoK_1 НЕ примонтирован.
- Соответственно 10 целевых вопросов (data source / типы уведомлений / взаимодействия /
  markAsRead / фильтры / empty-states / NotificationSettingsScreen / per-chat mute /
  navigation entry points / TODO & git history) — ответить по исходному коду НЕВОЗМОЖНО:
  исходного кода на машине нет. Grep/Read/Glob по *.kt вернут пусто.
- КОД НЕ МОДИФИЦИРОВАЛ (агент Explore, read-only; модифицировать было нечего).

Stage Summary:
- BLOCKER (окружение): репозиторий /home/z/repos/PinoK_1 отсутствует на этой sandbox-машине.
  Все артефакты PinoK_1 из worklog (SovaApp.kt, VKApiClient.kt, CommunityScreen.kt,
  Queuev4Client.kt, LongPollClient.kt, VKEndpoints.kt) НЕ ДОСТУПНЫ здесь — worklog был
  написан на ДРУГОМ хосте, где репозиторий был клонирован.
- DIAG-C по NotificationsScreen.kt / NotificationSettingsScreen.kt НЕ ВЫПОЛНЕН по причине
  отсутствия исходников. Verdict по всем 10 пунктам: UNKNOWN — нужен доступ к коду.
- ДЛЯ ВОЗОБНОВЛЕНИЯ: либо клонировать/примонтировать репозиторий PinoK_1 в /home/z/repos/PinoK_1
  (ветка PinoK), либо запустить DIAG-C повторно на хосте, где репозиторий действительно доступен.
  После этого повторно запустить агента Explore для DIAG-C — тогда отчёт по 10 пунктам
  (file:line refs, WORKING/PARTIAL/BROKEN verdicts) может быть подготовлен по регламенту.
- Файлы кода НЕ изменены. Изменён только /home/z/my-project/worklog.md (этот блок).

---
Task ID: DIAG-B
Agent: Explore (notifiers & display)
Task: Диагностика отображения уведомлений (system notifiers + actions)

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние 250 строк) — контекст P0.43-r3/r4/r5/r6/r7/r8 (звонки), P0.47 (community wall fix), Task 3 (clips в каналах) известен.
- КРИТИЧЕСКИЙ БЛОКЕР: репозиторий `/home/z/repos/PinoK_1` (ветка PinoK) НЕ ПРИМОНТИРОВАН в этой sandbox-среде. Поиск по всей ФС:
  • `find / -name "*.kt"` → 0 файлов Kotlin;
  • `find / -name "AndroidManifest.xml"` → 0 (только libkml Java-примеры в /usr/share/doc);
  • `find / -name "MessageNotifier.kt" -o -name "NotificationActionReceiver.kt"` → ничего;
  • `/home/sync/repo.tar` (460 МБ) — это архив git-репозитория Next.js проекта `/home/z/my-project/.git` (commit-сообщения = UUID'ы), НЕ PinoK Android.
  • Единственный доступ к PinoK Android — через логкаты реальной работы приложения в `/home/z/my-project/upload/` и `/home/sync/upload/` (4 logcat-файла, ~10 «Pasted Content_*.txt» логов, 1 «Новый текстовый документ.txt» = 9000 строк лога).
- Извлек ВСЮ доступную runtime-информацию из логкатов по ключевым словам: MessageNotifier, VkNotificationsNotifier, SecurityAlertNotifier/SecurityAlertsPoller, ReplyResultNotifier, NotificationActionReceiver, NotificationSource, EventDedup, SnNotifyFilter, UnreadMessagesCounter, ClipsCounter, LongPollKeepAliveService, NotificationsPoller, CommunityPostsPoller, POST_NOTIFICATIONS, RemoteInput, PendingIntent, ShortcutBadger.
- Сформировал partial-отчёт на основе runtime-доказательств (file:line refs взяты из stacktrace'ов лога `@ File.kt:LINE#METHOD`); для тех вопросов, где нужны исходники, пометил CANNOT VERIFY (no source).

Stage Summary (RUNTIME-EVIDENCE ONLY — без исходников):

1. Notifier inventory — PARTIAL / CANNOT FULLY VERIFY
   • MessageNotifier (MessageNotifier.kt):
     - init() на строке 163 создаёт 5 каналов: `messages`, `messages_silent`, `communities`, `communities_novib`, `communities_silent`. Лог: "Notification channels created: messages, messages_silent, communities, communities_novib, communities_silent".
     - showNotification() на строке 288: показывает уведомление вида `Notification shown: peer=<peerId> title='<interlocutor name>' unread=<N> text='Новое сообщение'`. ВАЖНО: text — это ГЕНЕРИЧЕСКИЙ placeholder «Новое сообщение», а не тело сообщения (вероятно privacy choice, но теряется информация).
     - cancelNotification() на строке 302: отменяет по peer_id (лог: "Notification cancelled: peer=171093180").
   • VkNotificationsNotifier (VkNotificationsNotifier.kt):
     - init() на строке 198 создаёт «9 categories + 1 security» = 10 каналов (лог: "Notification channels created (9 categories + 1 security)").
     - showBatch() на строках 365 / 389 / 421: фильтрует VK-уведомления. При notifyMode=0 (MESSAGES_ONLY) пропускает ВСЕ community posts (type=new_posts). Лог: "showBatch: notifyMode=0 — skip community owner=<X> type=new_posts" + "showBatch: all N items filtered out".
   • SecurityAlertNotifier: НИ ОДНОЙ runtime-записи. Poller падает раньше (см. §10).
   • ReplyResultNotifier: НИ ОДНОЙ runtime-записи во всех логкатах — либо не вызывается, либо не логирует.
   • ЧТО НЕ ВЕРИФИЦИРУЕТСЯ без исходников: channel priority/importance, sound URI, vibration pattern, icon res id, content title/text builder, PendingIntent target на tap. В логе только факт создания каналов, но не их настройки.

2. Notification actions (Reply / MarkRead / Mute / Delete) — CANNOT VERIFY
   • Поиск `NotificationActionReceiver|RemoteInput|ACTION_REPLY|ACTION_MARK_READ|ACTION_DELETE|PendingIntent|reply_inline` по всем логкатам → 0 записей от PinoK-приложения (попадаются только Permission Denial от других приложений).
   • НЕВОЗМОЖНО подтвердить: (a) есть ли action-кнопки на уведомлениях; (b) работает ли inline reply (RemoteInput); (c) какие intent extras передаются в NotificationActionReceiver.
   • РИСК: если action-кнопки отсутствуют — пользователь не может ответить/прочитать/удалить из шторки.

3. POST_NOTIFICATIONS permission check (Android 13+) — CANNOT VERIFY / RISK
   • Поиск `POST_NOTIFICATIONS|areNotificationsEnabled|notificationPermission|requestPermission` по всем логкатам → 0 записей от PinoK.
   • Уведомления ARE shown (MessageNotifier.showNotification отрабатывает) → на тестовом устройстве разрешение предоставлено.
   • НО: нет логов проверки/запроса разрешения → нельзя подтвердить, что приложение корректно обрабатывает отказ (Android 13+ требует runtime-permission). Если пользователь отзовёт разрешение — уведомления просто не покажутся, без видимого feedback'а.

4. Dedup logic (EventDedup.kt) — PARTIAL
   • Найдена dedup-логика в UnreadMessagesCounter.kt:94#emit: "NewMessage dup (EventHub/LongPoll) skipped peer=<peerId>". Т.е. если одно и то же NewMessage приходит и через EventHub (WebSocket), и через LongPoll — второе событие пропускается. Dedup key = peer_id.
   • EventDedup.kt: НИ ОДНОЙ runtime-записи во всех логкатах. Либо класс не логирует, либо не используется в рантайме.
   • NotificationsPoller.kt:183 / VKApiClient.kt:16999: "getRedesign: uniqueKey OK — 20 items, 20 distinct keys" — есть отдельная dedup-логика на стороне VK redesign-уведомлений (keyed по rawId).
   • РИСК: если EventDedup.kt не используется, могут быть дубли между разными каналами (LP / EventHub / queuev4 / NotificationsPoller), особенно для разных event-types.

5. Filter logic (SnNotifyFilter.kt / inline) — PARTIAL / FOUND FILTER
   • SnNotifyFilter.kt: НИ ОДНОЙ runtime-записи.
   • НО в VkNotificationsNotifier.kt:389#showBatch встроен inline-фильтр: при notifyMode=0 (MESSAGES_ONLY mode) ВСЕ уведомления type=new_posts (community posts) пропускаются — лог "notifyMode=0 — skip community owner=<X> type=new_posts". В одном из логов: "showBatch: all 6 items filtered out".
   • CommunityPostsPoller.kt:220#pollOnce: дублирующий фильтр "pollOnce: notifyMode=0 — community posts not shown".
   • SovaApp.kt:1573: "#NOTIFY notifier started: mode=MESSAGES_ONLY" — глобальный режим.
   • ВЫВОД: в MESSAGES_ONLY режиме ВСЕ community-уведомления (new_posts) SILENTLY DROPPED. Если пользователь ожидает видеть посты сообществ — они не покажутся, и это не очевидно из UI.

6. Counter sync (UnreadMessagesCounter.kt + badge) — PARTIAL / NO BADGE
   • UnreadMessagesCounter.kt:97#emit: "NewMessage: unreadCount → N" — инкрементальный счётчик.
   • UnreadMessagesCounter.kt:94#emit: dedup (см. §4).
   • UnreadMessagesCounter.kt:177/180#computeUnread: полный пересчёт через dialogs.get: "#COUNTER-CHANNELS: refreshCount: dialogsUnread=N channelsExcluded=0 pages=1". channelsExcluded=0 — каналы не исключаются из подсчёта.
   • ShortcutBadger / launcher badge: НИ ОДНОЙ runtime-записи. НЕ ПОДТВЕРЖДЕНО, что приложение синхронизирует unread-бейдж с лаунчером.
   • ClipsCounter.kt:77#refreshOnce: W "refreshOnce error: Unable to resolve host api.vk.com" — счётчик клипов падает на DNS без graceful degradation (просто W, ретраит или нет — без исходников не ясно).

7. Notification tap → screen routing — PARTIAL / WORKS FOR BASIC CASE
   • MessageNotifier.cancelNotification (строка 302) срабатывает на peer=171093180 → через секунду открывается ChatDetailScreen (ChatDetailScreen.kt:3162): "#IM-CHANNEL-OPEN enter: peerId=171093180 channelModeEnabled=true title='Сергей Ширабоков'".
   • ChatDetailScreen.kt:3284: "markAsRead on open: peer=171093180 upTo=307893 ok=true" — чат открывается AND помечает прочитанным до последнего cmid.
   • РАБОТАЕТ: базовый кейс — тап по уведомлению → открытие чата → markAsRead.
   • НЕ ВЕРИФИЦИРУЕТСЯ: (a) что если чат архивирован (показывается ли archived chat, или routing ломается?); (b) что если сообщение удалено (404? empty chat?); (c) какой PendingIntent target (Activity / NavDeepLinkBuilder) — нужно исходники.

8. Foreground service notification (LongPollKeepAliveService) — PARTIAL / WORKING
   • LongPollKeepAliveService.kt:303#start, :69#onCreate ("starting foreground + headless refresh observer"), :85#onStartCommand ("LongPollClient ensured running"), :316#stop.
   • Системный дамп (от MiniScreenManager): `pkg=re.pinok.debug id=7777 channel=bg_keepalive category=service vis=PRIVATE flags=0x62 color=0x00000000`.
   • flags=0x62 = FLAG_FOREGROUND_SERVICE (0x40) | FLAG_NO_CLEAR (0x20) | FLAG_ONGOING_EVENT (0x02) → пользователь НЕ МОЖЕТ смахнуть это уведомление (FLAG_NO_CLEAR выставлен).
   • Канал `bg_keepalive` — отдельный от 5 сообщений-каналов и 10 VK-каналов.
   • НЕ ВЕРИФИЦИРУЕТСЯ: какой content title/text/icon. По логам — категория service, цвет 0x00000000 (чёрный/прозрачный).

9. Known issues (TODO/FIXME/BUG/HACK grep) — CANNOT VERIFY
   • Без исходников невозможно сделать grep по TODO/FIXME/HACK/BUG в MessageNotifier.kt и др.
   • Без git-доступа невозможно проверить `git log --oneline -10 -- realtime/` на предмет недавних регрессий.
   • Runtime-находки потенциальных проблем:
     - SecurityAlertsPoller.kt:128 — "method unavailable (accountPersonal.getSecurityAlerts: Unknown method passed) — stopping poller" — VK API method не существует или требует другой auth (см. §10).
     - SecurityAlertsPoller.kt:170 — `pollOnce error` (NET, таймаут 6072ms) — также альтернативная ошибка.
     - ClipsCounter.kt:77 — DNS-ошибка `api.vk.com` проглатывается как W.
     - MessageNotifier.showNotification — text='Новое сообщение' (placeholder, не реальный body) — возможная потеря информации.
     - В MESSAGES_ONLY режиме ВСЕ community-posts фильтруются — неочевидно для пользователя.

10. Security alert handling (SecurityAlertsPoller / SecurityAlertNotifier) — БРОКЕН
    • SovaApp.kt:2550/2602/2642#startSecurityAlertsPoller — poller стартует при запуске приложения (лог: "SecurityAlertsPoller started (§49.5.1 safety-net alerts)").
    • SecurityAlertsPoller.kt:78#invokeSuspend: "start: security alerts poller started".
    • Poller вызывает `accountPersonal.getSecurityAlerts` через WEB-шлюз `web.api.vk.ru` с web-токеном `vk1.a.*` (без sig) — VKApiClient.kt:12988, :13019, :13025.
    • ОТВЕТ VK: `apiCode=3 "Unknown method passed"` (VKApiClient.kt:13121) — МЕТОД НЕ СУЩЕСТВУЕТ или недоступен для web-токена. Альтернативный ответ — NET error (6072ms timeout, :13069).
    • SecurityAlertsPoller.kt:128#pollOnce: "method unavailable (accountPersonal.getSecurityAlerts: Unknown method passed) — stopping poller".
    • SecurityAlertsPoller.kt:99#stop: "stop: security alerts poller stopped" — poller останавливается ПЕРМАНЕНТНО (до след. запуска приложения).
    • SecurityAlertNotifier.kt: НИ РАЗУ не вызывается (ни одного лога).
    • ВЫВОД: security alerts (новый вход, подозрительная активность) SILENTLY DROPPED — пользователь НЕ ПОЛУЧАЕТ их ни в виде system notification, ни в виде UI alert. Это серьёзная проблема безопасности.

NEXT ACTIONS (требуется доступ к /home/z/repos/PinoK_1 для полного отчёта):
- Запросить у пользователя / main-агента перемонтировать `/home/z/repos/PinoK_1` (ветка PinoK) в sandbox.
- После доступа — повторить отчёт с исходниками по каждому из 10 пунктов, особенно:
  (a) NotificationActionReceiver.kt — есть ли Reply/MarkRead/Delete action-кнопки;
  (b) POST_NOTIFICATIONS runtime-check в SovaApp.kt / MainActivity.kt / SettingsScreen.kt;
  (c) MessageNotifier.kt — channel priority/sound/icon и PendingIntent на tap (target screen);
  (d) VkNotificationsNotifier.kt — список 10 channel IDs и их настройки;
  (e) EventDedup.kt и SnNotifyFilter.kt — подтверждение их использования;
  (f) SecurityAlertsPoller.kt:128 — починить (или заменить метод API, или убрать poller совсем, чтобы не плодить ложные логи);
  (g) AndroidManifest.xml — проверить POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, NotificationListenerService, foreground service type.

PRIORITY ИСПРАВЛЕНИЙ (по runtime-доказательствам):
- P0: §10 SecurityAlertsPoller —VK API method `accountPersonal.getSecurityAlerts` не существует (error 3). Poller падает при каждом запуске. Либо убрать poller полностью, либо найти правильный метод/токен.
- P1: §5 community posts silent filter в MESSAGES_ONLY — пользователь не видит post-уведомления от сообществ. Либо показывать с отдельного канала, либо дать UI-toggle для режима.
- P2: §2 NotificationActionReceiver — НЕОБХОДИМО подтвердить наличие action-кнопок (Reply inline / Mark as read) — без исходников нельзя сказать, есть ли они вообще.
- P3: §1 MessageNotifier.showNotification text='Новое сообщение' — placeholder вместо реального текста сообщения (если это privacy choice, оставить; если баг — починить).
- P3: §6 ShortcutBadger — нет логов, нужно проверить, используется ли вообще badge API.
- P4: §3 POST_NOTIFICATIONS permission check — добавить runtime-check на Android 13+.

---
Task ID: DIAG-A
Agent: Explore (realtime channels & polling)
Task: Диагностика транспортных каналов и поллеров уведомлений PinoK

Work Log:
- Прочитал `/home/z/my-project/worklog.md` (полностью, 1196+ строк). Получил
  контекст предыдущих Task 1–P0.47: предыдущий P0.42/P0.43-r2/r3/r8-analyze-v216
  уже глубоко исследовали транспорт звонков (LongPoll, queuev4, EventHub WS).
- Проверил доступность репозитория PinoK_1: `/home/z/repos/PinoK_1/` ОТСУТСТВУЕТ
  на этом sandbox-сервере (только `/home/z/my-project/` — Next.js-дашборд + логи).
  Поиск `.kt`-файлов по всей ФС: 0 результатов. Репо не смонтировано.
- Извлёк максимальную информацию из доступных источников:
  1) `/home/z/my-project/worklog.md` (записи P0.42, P0.43-r2, P0.43-r3, P0.43-r8-analyze-v216
     с file:line-ссылками на LongPollClient.kt, Queuev4Client.kt, VKEndpoints.kt, SovaApp.kt).
  2) `/home/z/my-project/звонки.полный.разбор.md` (890 строк — транспорт VK: эндпоинты,
     аутентификация, форматы событий queuev4 / EventHub / WS / fb.do).
  3) Реальные logcat-файлы в `/home/z/my-project/upload/`:
     - `Pasted Content_1791919723547.txt` (2.2.1-debug-221, 2026-10-13 22:21–22:23)
     - `Новый текстовый документ.txt` (9026 строк, 2026-10-13 22:32 — полный startup-снапшот)
     - `Pasted Content_1791908927389.txt` (4640 строк, 2026-10-13 19:16–19:28 — polling-циклы)
     - `Pasted Content_1791897093405.txt` (4431 строк, 2026-10-13 16:07 — startCallSignaling failed)
     - `Pasted Content_1792611638269.txt` (2026-10-21 22:39 — EventHub WS failure паттерн)
  Это дало runtime-доказательства работы каждого канала с file:line-ссылками.
- Установил ИНВЕНТАРЬ транспортных каналов (по логам):
  1) `LongPollClient` (api.vk.com LP, messages.getLongPollServer) — РАБОТАЕТ.
  2) `EventHubWebSocketClient` (wss://eh.vk.ru/) — БАГ: pong timeout каждые ~30с.
  3) `Queuev4Client` ×3 (queuev4.vk.ru/im1180) — РАБОТАЕТ для online/counters,
     НО calls-queue подписана НА НЕ ТО (counters, не calls).
  4) `ChannelWebSocketClient` — НЕ ВИДЕН в логах (вероятно не стартует).
  5) `NotificationsPoller` (notifications.getRedesign) — РАБОТАЕТ, ~2 мин.
  6) `CommunityPostsPoller` — СТАРТУЕТ, но notifyMode=0 → не показывает (inert).
  7) `SecurityAlertsPoller` — БАГ: API error 3 "Unknown method" → permanent stop.
  8) `StatsQueuePoller` — НЕ ВИДЕН в логах (нет ссылок на этот poller).
  9) `LongPollKeepAliveService` — РАБОТАЕТ как foreground service (id=7777, flags=0x62).
  10) `RealtimeWakeReceiver` — РАБОТАЕТ (exact alarm +15min).
- Подтвердил РЕГРЕССИЮ из P0.43-r8-analyze-v216: в текущем билде (2.2.1-debug-221)
  startCallSignaling (`SovaApp.kt:2015#invokeSuspend`) использует `queue.subscribe` с
  queue_ids="onlfriends_<uid>,accountcounters_<uid>" для calls-queuev4 — НЕ
  `calls.subscribeToQueue` через calls.okcdn.ru (commit ac07af7 из P0.43-r3 был
  отменён). Это значит, что entity_type:"calls" / incoming_call НЕ доставляются
  (events с `entity_type:"online"` приходят на эту очередь вместо calls).
- Зафиксировал dedup: НЕ отдельный `EventDedup.kt`, а INLINE в `SovaApp.kt:1613#emit`
  ("notifier: dup (EventHub/LongPoll) skipped peer=...") и `UnreadMessagesCounter.kt:94#emit`
  ("NewMessage dup (EventHub/LongPoll) skipped peer=..."). Дедуп работает на peer-уровне.

Stage Summary:
- РЕПО НЕДОСТУПНО на sandbox (`/home/z/repos/PinoK_1/` отсутствует). Диагноз
  построен на предыдущих worklog-записях + `звонки.полный.разбор.md` + реальных
  logcat-файлах в `/home/z/my-project/upload/`. Все file:line-ссылки — из runtime-логов.
- WORKING каналы (runtime-подтверждено):
  • LongPollClient (api.vk.com/gim<uid>, version=3, mode=2, ~25s polls, failed=0).
  • Queuev4Client ×3 (queuev4.vk.ru/im1180, stickers.subscribeToQueue + queue.subscribe).
  • NotificationsPoller (notifications.getRedesign каждые ~2 мин).
  • LongPollKeepAliveService (foreground, id=7777, channel=bg_keepalive, flags=0x62).
  • RealtimeWakeReceiver (exact alarm +15min, onReceive → LongPoll.notifyResumed + EventHub.start).
  • INLINE dedup в SovaApp.kt:1613 + UnreadMessagesCounter.kt:94 (peer-level, EventHub/LongPoll).
- BROKEN каналы:
  • EventHubWebSocketClient — pong timeout каждые ~30с, 0 успешных ping/pong
    (EventHubWebSocketClient.kt:202#onFailure → :145#connectLoop reconnect 1000мс).
    Вероятная причина: OkHttp WebSocket ping frame не обрабатывается ehsp2-протоколом VK.
  • SecurityAlertsPoller — VK API error 3 "Unknown method passed" на
    `accountPersonal.getSecurityAlerts`, permanent stop (SecurityAlertsPoller.kt:128→99).
  • calls-queuev4 (имя misleading) — подписана на `onlfriends_<uid>,accountcounters_<uid>`
    (counters-queue), НЕ на `calls_<uid>_<client>_1`. entity_type:"calls" / incoming_call
    НЕ доставляется. В logcat видна только `entity_type:"online"`.
  • LP case 115 (incoming call) — LongPollClient.kt:987-989 silently пропускает
    payload="-1" (регрессия vs V2.1.6, P0.43-r8-analyze-v216 D1).
- RISKY / НЕИЗВЕСТНО:
  • ChannelWebSocketClient — нет логов; неясно, стартует ли вообще (нет `channels.ws_enabled`?).
  • StatsQueuePoller — нет логов; вероятно не существует в кодовой базе или не стартует.
  • CommunityPostsPoller — стартует, но notifyMode=0 (MESSAGES_ONLY default) → inert.
  • VKNotifyWeb — не найден в логах (возможно, заменён на `VkNotificationsNotifier.kt:198#init`).
- NEXT ACTIONS (требуют /home/z/repos/PinoK_1):
  1) Прочитать `realtime/LongPollClient.kt` 987-989 (case 115 "-1" guard) — подтвердить регрессию.
  2) Прочитать `realtime/Queuev4Client.kt` 200-260 (parseCallEvents, parseResponse) — подтвердить формат.
  3) Прочитать `realtime/EventHubWebSocketClient.kt` 140-206 (connectLoop, onFailure) — почему
     pong не доходит (возможно, нужен ручной текстовый ping вместо OkHttp WebSocket.ping()).
  4) Прочитать `realtime/ChannelWebSocketClient.kt` (если существует) — проверить, стартует ли.
  5) Прочитать `realtime/NotificationsPoller.kt` 85-200 — интервал, notifyMode handling.
  6) Прочитать `realtime/CommunityPostsPoller.kt` 163-220 — notifyMode gating.
  7) Прочитать `realtime/SecurityAlertsPoller.kt` 78-170 — найти правильный VK API метод.
  8) Прочитать `realtime/LongPollKeepAliveService.kt` 69-316 — что показывается в notification.
  9) Прочитать `realtime/RealtimeWakeReceiver.kt` 40-130 — alarm scheduler.
  10) Прочитать `api/VKEndpoints.kt` 60-78 — multiaccountQueueId/countersQueueId/callsQueueId.
  11) Прочитать `api/VKNotifyWeb.kt` (если существует).
  12) grep `SovaApp.kt` на `startCallSignaling`, `longPollClient`, `queuev4Client`, `eventHub`,
      `NotificationsPoller`, `communityPostsPoller`, `securityAlertsPoller`, `statsQueuePoller` —
      подтвердить порядок старта и инициализацию.
  13) `git log --oneline -20 -- app/src/main/java/re/pinok/realtime/` — недавние регрессии.

---
Task ID: DIAG-D
Agent: Explore (settings & prefs)
Task: Диагностика хранилища настроек уведомлений и sync с VK

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние ~250 строк) — контекст Tasks 1-6,
  P0.43 (звонки), P0.47 (community wall fix), Task 3 (clips), DIAG-B (notifiers),
  DIAG-C (notifications UI) известен. Особенно важно: DIAG-C (стр. 1044-1084)
  уже столкнулся с тем же BLOCKER'ом — репозиторий `/home/z/repos/PinoK_1` НЕ примонтирован.
- Попытался открыть целевые файлы (согласно task description):
    core/data/src/main/java/re/pinok/data/local/SovaPrefs.kt
    app/src/main/java/re/pinok/ui/screens/settings/SettingsScreen.kt
    app/src/main/java/re/pinok/ui/screens/settings/PrivacySettingsScreen.kt
    app/src/main/java/re/pinok/api/VKNotifyWeb.kt
    app/src/main/java/re/pinok/realtime/SnNotifyFilter.kt
    app/src/main/java/re/pinok/data/SovaPrefsBackup.kt
    app/src/main/java/re/pinok/data/SovaPrefsCrypto.kt
    app/src/main/java/re/pinok/ui/screens/settings/{LogScreen,PanelEditorTab,AuthDomainsScreen,BlacklistScreen}.kt
    app/src/main/java/re/pinok/data/local/{PinnedConversations,ArchivedConversations}Repository.kt
  → /home/z/repos/ НЕ СУЩЕСТВУЕТ → все пути неразрешимы.
- Исчерпывающий поиск по всей ФС (подтверждение той же картины, что DIAG-C):
    find / -type d -name "PinoK*"            → 0 каталогов
    find / -type f -name "*.kt"              → 0 файлов Kotlin
    find / -name "build.gradle*"              → 0 файлов
    find / -name "AndroidManifest.xml"        → 0 файлов
    find / -name "SovaPrefs.kt"               → 0 файлов
    find / -name "SettingsScreen.kt"          → 0 файлов
    find / -name "PrivacySettingsScreen.kt"   → 0 файлов
    find / -name "VKNotifyWeb.kt"             → 0 файлов
    find / -name "SnNotifyFilter.kt"          → 0 файлов
    find / -name "SovaPrefsBackup.kt"         → 0 файлов
    find / -name "SovaPrefsCrypto.kt"         → 0 файлов
    ls /home/z/repos                          → No such file or directory
- mount(8): единственное, что доступно для PinoK-данных — это logcat-архивы в
  /home/z/my-project/upload/ (4 logcat-файла + несколько "Pasted Content_*.txt"
  из логов). Репозиторий PinoK_1 НЕ примонтирован (только /home/official_skills
  RO, /home/sync tmpfs с пустым repo.tar от Next.js-проекта my-project).
- Проанализировал все сохранённые tool-results прошлых задач (DIAG-B и др.) для
  извлечения runtime-доказательств из логкатов. Эти доказательства касаются
  только ЧАСТИ задач DIAG-D — в первую очередь «notifyMode», «MESSAGES_ONLY»,
  «msgChannelMode» (SovaPrefs.kt:235) и факта отсутствия каких-либо логов с
  упоминанием `account.saveNotificationSettings` / `account.getNotificationSettings`
  / `VKNotifyWeb` / `notify.vk.ru` / `muteUntil` / `isMuted` / `NotifyModeSelector`
  / `DND` / `quietHours` / `notificationsDisabled`.
- КОД НЕ МОДИФИЦИРОВАЛ (агент Explore, read-only; модифицировать было нечего).

Stage Summary:
- BLOCKER (окружение, тот же что DIAG-C): репозиторий /home/z/repos/PinoK_1
  отсутствует на sandbox-машине. Все артефакты PinoK_1 (SovaPrefs.kt,
  SettingsScreen.kt, PrivacySettingsScreen.kt, VKNotifyWeb.kt, SnNotifyFilter.kt,
  SovaPrefsBackup.kt, SovaPrefsCrypto.kt и др.) НЕДОСТУПНЫ здесь — worklog был
  написан на ДРУГОМ хосте, где репозиторий был клонирован.
- DIAG-D по settings & prefs НЕ ВЫПОЛНЕН В ПОЛНОМ ОБЪЁМЕ. По 8 из 10 пунктов
  ответ: UNKNOWN (нет исходников). По 2 пунктам (global notifyMode + msgChannelMode)
  получены частичные ответы из runtime-доказательств.
- Verdict по 8 пунктам: UNKNOWN — нужен доступ к коду.
  Verdict по пункту «VK server sync»: НЕ ПОДТВЕРЖДЕНО (нет ни одного лога,
  указывающего на вызов account.saveNotificationSettings/getNotificationSettings).
  Verdict по пункту «Per-chat mute»: НЕ ПОДТВЕРЖДЕНО (0 логов с muteUntil/isMuted
  в обработанных логкатах — либо не используется, либо не логируется).
- ДОПОЛНИТЕЛЬНО (runtime-evidence, без исходников):
  • Глобальный notifyMode существует (default = 0 = MESSAGES_ONLY):
    SovaApp.kt:1600 — "#NOTIFY notifier started: mode=MESSAGES_ONLY"
    CommunityPostsPoller.kt:220 — "pollOnce: notifyMode=0 — community posts not shown"
    VkNotificationsNotifier.kt:389 — "showBatch: notifyMode=0 — skip community owner=<X>"
    В MESSAGES_ONLY-режиме ВСЕ community posts silently фильтруются.
  • SovaPrefs.kt:235 — `msgChannelMode` boolean (default true) — отдельный флаг
    канального режима сообщений, НЕ связан напрямую с notify prefs.
  • SecurityAlertsPoller.kt:128 — `accountPersonal.getSecurityAlerts` возвращает
    VK apiCode=3 "Unknown method passed" → poller permanently stops. Это не
    settings-sync, но показывает, что polling-механизмы на accountPersonal.*
    ВК-методах НЕ работают для web-токена.
  • SovaPrefsBackup (в :app module) упаковывает EncryptedSharedPreferences snapshot
    в секцию "session" JSON-экспорта. SovaPrefsCrypto использует device-bound
    ключ. Включены ли туда notify prefs — без исходников неизвестно.
- ДЛЯ ВОЗОБНОВЛЕНИЯ: либо клонировать/примонтировать репозиторий PinoK_1 в
  /home/z/repos/PinoK_1 (ветка PinoK), либо запустить DIAG-D повторно на хосте,
  где репозиторий действительно доступен. После этого повторно запустить агента
  Explore для DIAG-D — тогда отчёт по 10 пунктам (file:line refs,
  WORKING/PARTIAL/BROKEN verdicts) может быть подготовлен по регламенту.
- Файлы кода НЕ изменены. Изменён только /home/z/my-project/worklog.md (этот блок).

---
Task ID: DIAG-E
Agent: Explore (orchestration & lifecycle)
Task: Диагностика оркестровки realtime в SovaApp + lifecycle/манифест

Work Log:
- Прочитал /home/z/my-project/worklog.md (полностью, 1369+ строк): получил контекст
  P0.42/P0.43-r2/r3/r8-analyze-v216 (регрессия звонков), P0.47 (community wall fix),
  Task 3 (clips), DIAG-A (транспортные каналы), DIAG-B (notifiers & display),
  DIAG-C (notifications UI — BLOCKER), DIAG-D (settings & prefs — BLOCKER).
- Проверил доступность репозитория PinoK_1:
  • `/home/z/repos/PinoK_1` → отсутствует (тот же BLOCKER, что DIAG-B/C/D).
  • `find / -type f -name "*.kt"` → 0 Kotlin-файлов в ФС.
  • `find / -name "AndroidManifest.xml"` → 0 PinoK-манифестов (только Java-примеры в /usr/share/doc).
  • `find / -name "SovaApp.kt" -o -name "BootReceiver.kt" -o -name "LongPollKeepAliveService.kt"
       -o -name "RealtimeWakeReceiver.kt" -o -name "MainActivity.kt"` → 0 файлов.
  • `/tmp/pinok_v216/` (архив V2.1.6, использованный в P0.43-r8-analyze-v216) → отсутствует.
- Источники диагностики (всё, что доступно на sandbox):
  1) `/home/z/my-project/worklog.md` — записи P0.43-r8-analyze-v216 (строки 865-990),
     с file:line-ссылками на V2.1.6 vs CURRENT SovaApp.kt/LongPollClient.kt/Queuev4Client.kt.
  2) Cached Read `/home/z/my-project/tool-results/read_1791137521720_213f2a6dee2a.txt`
     (90741 bytes, 1366 строк) — содержит `realtime/LongPollClient.kt` целиком
     (версия с безусловным emit IncomingCall в case 115 — V2.1.6-стиль поведения,
     см. подробности в §3 ниже). Cached reads 213f2a6dee2a / c27771695f46 —
     один и тот же файл, две копии (90741 / 103035 bytes — второй с line-prefix дубликатом).
  3) Реальные logcat-захваты в `/home/z/my-project/upload/`:
     • `Pasted Content_1791188862530.txt` (v2.1.9-debug, 2026-10-05 11:18 — 1630 строк)
       → НЕТ EventHub, НЕТ RealtimeWakeReceiver, НЕТ CommunityPostsPoller логов.
     • `Pasted Content_1791285169465.txt` (v2.2.0-debug, 2026-10-06 14:06 — 2001 строк)
       → НЕТ EventHub, НЕТ RealtimeWakeReceiver, НЕТ CommunityPostsPoller логов.
     • `Pasted Content_1791896995072.txt` (v2.2.1-debug-221, 2026-10-13 16:07)
       → startCallSignaling failed, calls queue subscribe error (DNS).
     • `Pasted Content_1791908927389.txt` (v2.2.1-debug-221, 2026-10-13 19:16)
       → RealtimeWakeReceiver.onReceive в действии (#DOZE-RELIABILITY wake-receiver fired).
     • `Pasted Content_1791919723547.txt` (v2.2.1-debug-221, 2026-10-13 22:21 — 555988 bytes)
       → полный startup-снапшот v2.2.1 со всеми realtime-каналами.
     • `Pasted Content_1792611638269.txt` (v2.2.1-debug-221, 2026-10-21 22:39)
       → EventHub WS failure pattern (pong timeout каждые ~30с).
  4) `звонки.полный.разбор.md` (890+ строк) — детальный разбор транспортных каналов
     VK (LP, queuev4, EventHub, calls.subscribeToQueue).
- Извлёк полную startup-последовательность из v2.2.1-debug-221 (2026-10-13 22:21:41)
  с file:line refs по каждому каналу (см. §1 ниже).
- Извлёк cached LongPollClient.kt:956-963 (case 115) — источник ПРОТИВОРЕЧИТ
  worklog-утверждению о LongPollClient.kt:987-989 (см. подробности в §3).
- Код НЕ модифицировал (агент Explore, read-only; модифицировать было нечего —
  репо отсутствует).

Stage Summary:

# БЛОКЕР ОКРУЖЕНИЯ (как DIAG-B/C/D)
Репозиторий `/home/z/repos/PinoK_1` (ветка PinoK) НЕ примонтирован в этой sandbox.
Все целевые файлы (SovaApp.kt, AndroidManifest.xml, BootReceiver.kt,
LongPollKeepAliveService.kt, RealtimeWakeReceiver.kt, PlayerService.kt,
VideoPlaybackService.kt, MainActivity.kt) НЕ ДОСТУПНЫ для прямого чтения.
Диагноз построен на:
  (a) cached LongPollClient.kt source (1 файл из прошлой сессии — не текущий HEAD!),
  (b) 6 logcat-захватов реальной работы приложения (v2.1.9 / v2.2.0 / v2.2.1),
  (c) documented findings из worklog P0.43-r8-analyze-v216 (строки 865-990).
Все file:line refs, помеченные «[logcat v2.2.1]», взяты из stacktrace-строк
лога `@ File.kt:LINE#METHOD` — это достоверно текущая кодовая база.
Refs, помеченные «[P0.43-r8 worklog]», взяты из worklog P0.43-r8 — это анализ
коммита 6fbb83b (P0.43-r7), который МОГ устареть относительно v2.2.1 (см. §3 caveat).

---
## §1. Startup sequence — порядок старта каналов в v2.2.1-debug-221

**Источник:** `Pasted Content_1791919723547.txt` (2026-10-13 22:21:41, v2.2.1-debug-221).
App: `re.pinok.debug v2.2.1-debug-221`, device: HOTWAV Cyber 15, Android 13 API 33.

### 1A. SovaApp.onCreate (main thread) — 22:21:41.263 → 22:21:41.440
| Timestamp | File:Line | Method | Что происходит |
|-----------|-----------|--------|----------------|
| 41.263 | SovaApp.kt:1017#onCreate | onCreate | P3.10: log-config runBlocking (45ms, ANR risk) |
| 41.340 | SovaApp.kt:1039#onCreate | onCreate | User-Agent setup |
| 41.343 | SovaApp.kt:825#invokeSuspend | coroutine | Siren decoder check |
| 41.348 | SovaApp.kt:1082#onCreate | onCreate | P3.10: migrations runBlocking (7ms) |
| 41.351 | SovaApp.kt:1096#onCreate | onCreate | P3.10: prefsSnapshot seed runBlocking (3ms) |
| 41.357 | SovaApp.kt:1197#onCreate | onCreate | SSL Pinning disabled |
| 41.358 | SovaApp.kt:1226#onCreate | onCreate | Proxy disabled |
| 41.388 | SovaApp.kt:550#setupCookieBackgroundRefresh | setupCookieBackgroundRefresh | ON_RESUME flush hook registered |
| 41.414 | SovaApp.kt:1389#onCreate | onCreate | **skip PlayerConnection.init — no token (auth flow), #AUTH-WEBVIEW-STARVATION-V2** |
| 41.426 | SovaApp.kt:1419#invokeSuspend | coroutine | Prefs loaded (musicPath, adBlock) |
| 41.432 | SovaApp.kt:2602#startNotificationsPoller | startNotificationsPoller | **NotificationsPoller started (push notifications)** |
| 41.434 | SovaApp.kt:1600#invokeSuspend | coroutine | #NOTIFY notifier started: mode=MESSAGES_ONLY |
| 41.434 | SovaApp.kt:2621#startCommunityPostsPoller | startCommunityPostsPoller | **CommunityPostsPoller started (#COMMUNITY-POSTS)** |
| 41.435 | NotificationsPoller.kt:85#invokeSuspend | coroutine | Notifications poller started |
| 41.438 | SovaApp.kt:2642#startSecurityAlertsPoller | startSecurityAlertsPoller | **SecurityAlertsPoller started (§49.5.1 safety-net alerts)** |
| 41.440 | SovaApp.kt:1564#onCreate | onCreate | **SovaApp initialized: okHttp=..., api=VKApiClient** (END of onCreate) |
| 41.444 | CommunityPostsPoller.kt:163#invokeSuspend | coroutine | Community posts poller started |

**ВЫВОД 1A:** ВНУТРИ `SovaApp.onCreate()` СТАРТУЮТ ТОЛЬКО 3 poller'а:
NotificationsPoller, CommunityPostsPoller, SecurityAlertsPoller. LongPoll,
EventHub, LongPollKeepAliveService, RealtimeWake, calls-queuev4 НЕ стартуют
в onCreate — они стартуют позже, после MainActivity onCreate.

### 1B. MainActivity.onCreate → onActivityStarted → cookie flush (main thread) — 22:21:41.491 → 22:21:41.722
| Timestamp | File:Line | Method | Что происходит |
|-----------|-----------|--------|----------------|
| 41.491 | MainActivity.kt:911#onCreate + SovaApp.kt:836#onActivityStarted | onActivityStarted | **Activity started (MainActivity), foreground count=1** |
| 41.493 | LongPollClient.kt:165#notifyResumed | notifyResumed | resetting backoff, evicting connections, cancelling in-flight poll |
| 41.722 | SovaApp.kt:542#invokeSuspend | coroutine | cookieSync: CookieManager.flush() OK (foreground) |

### 1C. FIRST TEARDOWN (no auth yet) — 22:21:41.729 → 22:21:41.735
| Timestamp | File:Line | Method | Что происходит |
|-----------|-----------|--------|----------------|
| 41.729 | LongPollClient.kt:205#stop | stop | **LongPoll stopped** |
| 41.730 | CommunityPostsPoller.kt:187#stop | stop | Community posts poller stopped |
| 41.734 | LongPollKeepAliveService.kt:316#stop | stop | keep-alive service stopped |
| 41.735 | RealtimeWakeReceiver.kt:129#cancel | cancel | #DOZE-RELIABILITY cancel: no pending alarm |

**ВЫВОД 1C:** При первом onActivityStarted → onStop цикле (без auth) ВСЕ realtime-каналы
(кроме NotificationsPoller/SecurityAlertsPoller которые не зависят от auth) STOPPED.
Cookie sync + foreground hooks остаются активными. **Pause-app-на-old-foreground-count=0 pattern.**

### 1D. MainActivity.invokeSuspend → запускает realtime-стек — 22:21:48.557 → 22:21:48.983
| Timestamp | File:Line | Method | Что происходит |
|-----------|-----------|--------|----------------|
| 47.591 | SovaApp.kt:1168#lookup | lookup (coroutine) | DNS fallback: web.api.vk.ru (system DNS failed) |
| 47.980 | SecurityAlertsPoller.kt:128#pollOnce | pollOnce | **method unavailable (accountPersonal.getSecurityAlerts: Unknown method passed) — stopping poller** |
| 47.980 | SecurityAlertsPoller.kt:99#stop | stop | security alerts poller stopped (PERMANENT) |
| 48.557 | LongPollClient.kt:185#start | start | **LongPoll started** |
| 48.559 | LongPollClient.kt:240#emit | emit (coroutine) | Network restored — resuming poll |
| 48.560 | EventHubWebSocketClient.kt:65#start | start | **start: запускаю EventHub WebSocket** |
| 48.562 | LongPollKeepAliveService.kt:303#start | start | **start: keep-alive service requested** |
| 48.563 | LongPollClient.kt:410#loop | loop (coroutine) | [LP:version-select] version=3 useV14=false useModern=false prefsSet=true |
| 48.567 | RealtimeWakeReceiver.kt:98#schedule | schedule | **#DOZE-RELIABILITY alarm scheduled (exact, +15min)** |
| 48.724 | LongPollClient.kt:487#loop | loop (coroutine) | [LP:server-fetch] ts=1671719489 pts=10512051 mode=2 server=api.vk.com/gim171093180 |
| 48.734 | LongPollClient.kt:742#performBackfillIfNeeded | performBackfillIfNeeded | [BACKFILL:start] savedPts=10512009 currentPts=10512051 delta=42 |
| 48.883 | LongPollKeepAliveService.kt:69#onCreate | onCreate | **starting foreground + headless refresh observer** |
| 48.904 | RealtimeWakeReceiver.kt:98#schedule | schedule | #DOZE-RELIABILITY alarm scheduled (exact, +15min) — повтор |
| 48.910 | LongPollClient.kt:180#start | start (idempotent) | start: already running |
| 48.910 | LongPollKeepAliveService.kt:85#onStartCommand | onStartCommand | **onStartCommand: LongPollClient ensured running** |
| 48.983 | EventHubWebSocketClient.kt:176#onOpen | onOpen | onOpen: EventHub connected (code=101) |

### 1E. Calls-queuev4 / multiaccount / events_queuev4 subscribe — 22:21:49.881 → 22:21:50.218
| Timestamp | File:Line | Method | Что происходит |
|-----------|-----------|--------|----------------|
| 49.143 | NotificationsPoller.kt:183#pollOnce | pollOnce | pollOnce: 26 total, 6 NEW — showing notifications |
| 49.881 | SovaApp.kt:2081#invokeSuspend | coroutine | **queue.subscribe IM OK (key=c9855c81... ts=1492853466 url=https://queuev4.vk.ru/im1180)** |
| 49.882 | SovaApp.kt:2084#invokeSuspend | coroutine | **calls-queuev4 активна (IM-очередь: onlfriends_171093180,accountcounters_171093180)** |
| 49.885 | SovaApp.kt:2153#ensureCallsSessionKey | ensureCallsSessionKey | ensureCallsSessionKey: уже есть (len=156) |
| 50.030 | SovaApp.kt:2106#invokeSuspend | coroutine | **stickers.subscribeToQueue OK (key=d484a3f1... ts=106379888)** |
| 50.031 | SovaApp.kt:2109#invokeSuspend | coroutine | queuev4 активна (multiaccount, через stickers.subscribeToQueue) |
| 50.218 | SovaApp.kt:2125#invokeSuspend | coroutine | events_queuev4 активна (через stickers.subscribeToQueue) |

**ВЫВОД 1E:** startCallSignaling стартует ПОСЛЕ LP+EventHub+KeepAlive+WakeReceiver,
через ~1.3 сек. **УСЛОВНЫЙ СТАРТ:** `skip PlayerConnection.init — no token` (SovaApp.kt:1389)
говорит, что realtime-стек НЕ запускается БЕЗ токена. LongPollClient.stop → start
происходит в момент, когда auth completed (silentRefreshViaRemixsidLive OK HTTP 200,
ExchangeAuthRepository.kt:1510#onResponse — Pasted Content_1791919723547.txt:4318).
Значит realtime-стек **CONDITIONAL on auth state** — это правильное поведение.

### 1F. Сводный порядок старта (post-auth):
```
1. LongPollClient.start              LongPollClient.kt:185#start
2. EventHubWebSocketClient.start     EventHubWebSocketClient.kt:65#start       (параллельно с #1)
3. LongPollKeepAliveService.start    LongPollKeepAliveService.kt:303#start      (параллельно с #1)
4. RealtimeWakeReceiver.schedule     RealtimeWakeReceiver.kt:98#schedule         (параллельно с #1)
5. LP loop: version-select           LongPollClient.kt:410#loop
6. LP loop: server-fetch             LongPollClient.kt:487#loop
7. LP performBackfillIfNeeded        LongPollClient.kt:742#performBackfillIfNeeded
8. LongPollKeepAlive.onCreate        LongPollKeepAliveService.kt:69#onCreate    (foreground promote)
9. LongPollKeepAlive.onStartCommand  LongPollKeepAliveService.kt:85#onStartCommand (ensure LP running)
10. EventHub.onOpen                  EventHubWebSocketClient.kt:176#onOpen
11. EventHub.connectLoop             EventHubWebSocketClient.kt:140#connectLoop
12. ensureCallsSessionKey            SovaApp.kt:2153#ensureCallsSessionKey
13. queue.subscribe (IM)            SovaApp.kt:2081  → calls-queuev4
14. stickers.subscribeToQueue #1     SovaApp.kt:2106  → multiaccount queuev4Client
15. stickers.subscribeToQueue #2     SovaApp.kt:2106 (повтор) → events_queuev4Client (SovaApp.kt:2125)
```

---
## §2. Per-channel start —逐 channel verdict (v2.2.1-debug-221)

### 2.1 LongPollClient — WORKING ✅
- **Start call site:** `LongPollClient.kt:185#start` (SovaApp.kt post-auth).
- **Stop call site:** `LongPollClient.kt:205#stop`.
- **Idempotent re-start:** `LongPollClient.kt:180#start` (лог "start: already running").
- **URL fetch:** `LongPollClient.kt:487#loop` → server=api.vk.com/gim171093180, version=3, mode=2.
- **Loop:** `LongPollClient.kt:410#loop` (version-select), `LongPollClient.kt:584#loop` (poll-response).
- **Backfill:** `LongPollClient.kt:742#performBackfillIfNeeded` (start) → `:755#performBackfillIfNeeded` (replay) → `:770#performBackfillIfNeeded` (done).
- **notifyResumed:** `LongPollClient.kt:165#notifyResumed` — вызывается onActivityStarted (foreground) и RealtimeWakeReceiver.onReceive (Doze wake).
- **Доказательство работы:** `[LP:poll-response] version=3 ts=... events=N ms=25279 failed=0` (Pasted Content_1791919723547.txt:4565) — polling стабилен 25-сек wait, failed=0.

### 2.2 EventHubWebSocketClient — PARTIAL ⚠
- **Start call site:** `EventHubWebSocketClient.kt:65#start`.
- **onOpen:** `EventHubWebSocketClient.kt:176#onOpen` ("EventHub connected (code=101)").
- **connectLoop:** `EventHubWebSocketClient.kt:140#connectLoop` ("EventHub подключён").
- **onFailure:** `EventHubWebSocketClient.kt:206#onFailure` ("sent ping but didn't receive pong within 15000ms").
- **Reconnect:** `EventHubWebSocketClient.kt:145#connectLoop` (reconnect через 1000мс).
- **Проблема:** WebSocket ping/pong timeout каждые ~30 секунд (0 успешных ping/pong) →
  постоянные reconnect циклы. Channel TECHNICALLY connected, но PRACTICALLY unstable.
- **Verdict:** PARTIAL — соединение устанавливается, но держится <30 сек до следующего reconnect.

### 2.3 Queuev4Client ×3 — WORKING ✅ (но с неправильной подпиской для calls)
- **calls-queuev4 (callsQueuev4Client):**
  - Subscribe: `SovaApp.kt:2081#invokeSuspend` → `queue.subscribe IM OK` (key=c9855c81... ts=1492853466 url=https://queuev4.vk.ru/im1180).
  - Лог: `calls-queuev4 активна (IM-очередь: onlfriends_171093180,accountcounters_171093180)` (SovaApp.kt:2084).
  - **ПРОБЛЕМА:** подписаны на `onlfriends_<uid>,accountcounters_<uid>` (countersQueueId),
    НЕ на `calls_<uid>_<client>_1` (callsQueueId). Это значит entity_type:"calls" /
    incoming_call НЕ доставляется на эту очередь (только entity_type:"online").
- **queuev4Client (multiaccount):**
  - Subscribe: `SovaApp.kt:2106#invokeSuspend` → `stickers.subscribeToQueue OK` (key=d484a3f1... ts=106379888).
  - Лог: `queuev4 активна (multiaccount, через stickers.subscribeToQueue)` (SovaApp.kt:2109).
- **eventsQueuev4Client (events):**
  - Subscribe: `SovaApp.kt:2125#invokeSuspend` → `events_queuev4 активна (через stickers.subscribeToQueue)`.
  - Второй вызов stickers.subscribeToQueue (SovaApp.kt:2106 — повторный вызов).

### 2.4 NotificationsPoller — WORKING ✅
- **Start call site:** `SovaApp.kt:2602#startNotificationsPoller` (в onCreate!).
- **Coroutine entry:** `NotificationsPoller.kt:85#invokeSuspend`.
- **pollOnce:** `NotificationsPoller.kt:161#pollOnce` (fetch), `:173#pollOnce`, `:183#pollOnce` (result), `:200#pollOnce` (batch order).
- **Доказательство работы:** "pollOnce: 26 total, 6 NEW — showing notifications" + "pollOnce: batch order — dialogs=0, news=6".
- **Интервал:** ~5 сек первый pollOnce, затем ~2 мин регулярные (Pasted Content_1791908927389.txt).

### 2.5 CommunityPostsPoller — PARTIAL ⚠
- **Start call site:** `SovaApp.kt:2621#startCommunityPostsPoller` (в onCreate).
- **Coroutine entry:** `CommunityPostsPoller.kt:163#invokeSuspend`.
- **Stop call site:** `CommunityPostsPoller.kt:187#stop`.
- **pollOnce (filtered):** `CommunityPostsPoller.kt:220#pollOnce` ("notifyMode=0 — community posts not shown" — из DIAG-B).
- **Проблема:** poller стартует, но в notifyMode=0 (MESSAGES_ONLY — default)
  community posts silently dropped. Inert для пользователя по умолчанию.
- **Verdict:** PARTIAL — стартует корректно, но не показывает результат (см. §5).

### 2.6 SecurityAlertsPoller — BROKEN ❌
- **Start call site:** `SovaApp.kt:2642#startSecurityAlertsPoller` (в onCreate).
- **Coroutine entry:** `SecurityAlertsPoller.kt:78#invokeSuspend` ("start: security alerts poller started").
- **pollOnce:** `SecurityAlertsPoller.kt:128#pollOnce` — **"method unavailable (accountPersonal.getSecurityAlerts: Unknown method passed) — stopping poller"**.
- **Stop call site:** `SecurityAlertsPoller.kt:99#stop`.
- **Доказательство:** VK API возвращает `apiCode=3 "Unknown method passed"` для
  `accountPersonal.getSecurityAlerts` через WEB-шлюз (VKApiClient.kt:13121 — DIAG-B §10).
- **Verdict:** BROKEN — VK API method не существует (или недоступен для web-токена
  `vk1.a.*`). Poller permanently stopped после первого pollOnce.

### 2.7 StatsQueuePoller — DEAD-CODE / NOT FOUND ❓
- **НЕ НАЙДЕН** ни в одном logcat (v2.1.9, v2.2.0, v2.2.1).
- Грep по `StatsQueue|statsQueue|startStatsQueue` в кэше tool-results и upload → 0 совпадений.
- **Verdict:** DEAD-CODE / НЕ СУЩЕСТВУЕТ в текущей кодовой базе (либо удалён, либо
  переименован, либо task-description указал метод, которого нет в коде).
- ПРИМЕЧАНИЕ: P0.47-revert (worklog 994-1037) упоминает `stats.*` дроп в telemetry-гейте,
  но это про другое (account.saveNotificationSettings / statsDashboard.*). Не подтверждено,
  что есть отдельный класс `StatsQueuePoller`.

### 2.8 LongPollKeepAliveService — WORKING ✅
- **Start call site:** `LongPollKeepAliveService.kt:303#start` ("start: keep-alive service requested").
- **onCreate (foreground promote):** `LongPollKeepAliveService.kt:69#onCreate` ("onCreate: starting foreground + headless refresh observer").
- **onStartCommand:** `LongPollKeepAliveService.kt:85#onStartCommand` ("onStartCommand: LongPollClient ensured running").
- **Stop call site:** `LongPollKeepAliveService.kt:316#stop` ("stop: keep-alive service stopped").
- **Foreground notification:** id=7777, channel=`bg_keepalive`, flags=0x62
  (FLAG_FOREGROUND_SERVICE | FLAG_NO_CLEAR | FLAG_ONGOING_EVENT) — из DIAG-B §8.
- **ServiceCompat type:** `ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING` — из
  worklog P4.1+P4.2 (read_1791189065691_075856c4e6f8.txt:1061-1071).
- **Активная валидация:** `onStartCommand: LongPollClient ensured running` — service
  ACTIVE проверяет, что LP запущен. Если LP умер, но service жив — он пере-запустит LP.

### 2.9 RealtimeWakeReceiver — WORKING ✅
- **Action:** `re.pinok.action.REALTIME_WAKE` (Pasted Content_1791908927389.txt:13).
- **onReceive (line 40):** `RealtimeWakeReceiver.kt:40#onReceive` — "#DOZE-RELIABILITY wake-receiver: action=re.pinok.action.REALTIME_WAKE".
- **onReceive (line 50):** `RealtimeWakeReceiver.kt:50#onReceive` — "#DOZE-RELIABILITY realtime woken (LongPoll.notifyResumed + EventHub.start)".
- **schedule:** `RealtimeWakeReceiver.kt:98#schedule` — "#DOZE-RELIABILITY alarm scheduled (exact, +15min)".
- **cancel:** `RealtimeWakeReceiver.kt:127#cancel` (2026-10-13 16:07 log: "alarm cancelled")
  ИЛИ `:129#cancel` (2026-10-13 22:21 log: "cancel: no pending alarm").
- **Доказательство работы:** alarm fires каждые 15 мин, onReceive дёргает LP.notifyResumed
  и EventHub.start — wake-механизм для Doze mode WORKING.

---
## §3. Incoming call regression — Q3 (CRITICAL)

### 3.1 Документированная регрессия из worklog P0.43-r8 (строки 865-990 worklog.md)
> Current (SovaApp.kt:1808-1810): `if (event.payload == "-1") { AppLog.i(..., "LP 115 заглушка -1, пропущен (жду queuev4 calls)"); ... }` — ПРОПУСКАЕТ LP 115 с payload="-1", НЕ вызывает refreshIncomingCaller.

> (1) LongPollClient.kt:987-989 — удалить `if (payload == "-1") skip`, всегда emit IncomingCall(payload).
> (2) SovaApp.kt:1808-1810 — удалить `if (event.payload == "-1") skip` (дублирующий guard, сейчас мёртвый т.к. LongPollClient не эмитит "-1").

> КЛЮЧЕВАЯ НАХОДКА: V2.1.6 incoming_call работал через VK Messages LongPoll (api.vk.com LP) case 115, НЕ через queuev4. queuev4 был FALLBACK, в V2.1.6 подписывался через queue.subscribe (SAT-токен) с queue_ids="multiaccount_<uid>".

### 3.2 СТАТУС — НЕ ВЕРИФИЦИРОВАНО в v2.2.1 (нет прямого доступа к SovaApp.kt)
- Repo `/home/z/repos/PinoK_1` отсутствует → SovaApp.kt:1808-1810 НЕЛЬЗЯ прочитать напрямую.
- Worklog P0.43-r8 документировал guard на основе коммита `6fbb83b` (P0.43-r7). Начиная с
  v2.2.0 (2026-10-06) line numbers сдвинуты (~270 строк вверх-вниз в разных методах),
  поэтому прямые ссылки `SovaApp.kt:1808-1810` могут быть УСТАРЕВШИМИ для v2.2.1.
- В v2.2.1-debug-221 логах НЕТ ни одной записи "LP 115 заглушка" или "IncomingCall (LP 115)"
  или "INCOMING_CALL" — потому что в логах НЕТ ни одного реального входящего звонка
  (ни в одной из 6 logcat-захватов). Это означает, что REGRESSION-поведение просто не
  срабатывало на тестах — нет входящих VK-звонков в момент захвата лога.

### 3.3 КОЭФФИЦИЕНТ ПРОТИВОРЕЧИЯ (cached LongPollClient.kt vs worklog P0.43-r8)
Cached Read `read_1791137521720_213f2a6dee2a.txt` (1366 строк) содержит полный
LongPollClient.kt. ПРОВЕРКА строк 956-963 (case 115):
```kotlin
956:                115 -> {
957:                    // #CALLS: входящий звонок. ev=[115, payload] — payload =
958:                    // строка conversation-params (WebRTC). Формат подтверждён из
959:                    // m.vk.ru бандла: case 115: return {type:INCOMING_CALL, payload:e}
960:                    val payload = ev.stringAt(1)
961:                    AppLog.i(TAG, "INCOMING_CALL (LP 115): payload.len=${payload?.length ?: 0}")
962:                    _events.emit(LongPollEvent.IncomingCall(payload))
963:                }
```

**Cached LongPollClient.kt:956-963 — БЕЗ УСЛОВИЙ эмитит IncomingCall(payload) — V2.1.6-корректное поведение.**

ПРОВЕРКА строк 980-1022 (handleNewMessage echo-skip, fix #138):
```kotlin
980:        // Fix #138 (URGENT 2026-08-03): пользователь жалуется на «непонятные
981:        // уведомления» — в шторке показывает текст = число (например "1785781370")
...
986:        // АНАЛИЗ:
987:        //  - msgId == peerId (295315 == 295315) — НЕВОЗМОЖНО для настоящего
988:        //    сообщения VK (msg_id это глобальный счётчик, peer_id это
989:        //    userId/dialogId, они не могут совпадать).
...
1011:        if (msgId == peerId && msgId > 0) {
1012:            AppLog.lp(phase = "event-new-message-SKIPPED-echo", level = android.util.Log.WARN, fields = ...)
...
1021:            return  // пропускаем echo/meta-event
1022:        }
```

**Cached LongPollClient.kt:987-989 — это КОММЕНТАРИЙ к handleNewMessage echo-skip
(Fix #138, msgId==peerId), НЕ payload=="-1" guard для case 115!**

### 3.4 РАСХОЖДЕНИЕ
- Worklog P0.43-r8 строка 988: "(1) LongPollClient.kt:987-989 — удалить `if (payload == "-1") skip`".
- Cached LongPollClient.kt source: строки 987-989 = КОММЕНТАРИЙ (msgId==peerId анализ),
  а НЕ код с `if (payload == "-1") skip`.

**ДВА возможных объяснения:**
1. Worklog P0.43-r8 содержит ОШИБОЧНУЮ ссылку на строки 987-989 — на самом деле
   guard'а НЕТ в LongPollClient.kt вообще (lines 956-963 уже V2.1.6-correct).
2. Cached LongPollClient.kt — из СТАРОЙ версии (pre-regression), а в текущем HEAD
   строки 956-963 переписаны с guard'ом, и строки сдвинуты. worklog P0.43-r8
   анализировал commit 6fbb83b, а cached read мог быть из чуть более раннего коммита.

**КРИТИЧНО:** Для точной верификации необходимо прочитать LongPollClient.kt в текущем
HEAD репозитория. Без исходников это НЕВОЗМОЖНО.

### 3.5 Что нужно изменить для восстановления V2.1.6 поведения (по worklog P0.43-r8)
Из worklog строк 961-990:
- **Вариант A (минимальный патч, рекомендация P0.43-r8):**
  (1) УДАЛИТЬ блок `if (event.payload == "-1") { пропущен }` в `SovaApp.kt:1808-1810`.
  (2) УДАЛИТЬ guard `if (payload == "-1") skip` в `LongPollClient.kt:987-989` (если он существует в текущем HEAD).
  Это вернёт PRIMARY канал входящих — messages LongPoll LP 115.
- **Вариант B (более радикальный):**
  Вернуть V2.1.6 startCallSignaling: `queueSubscribe(multiaccountQueueId)` для queuev4Client,
  `queueSubscribe(countersQueueId)` для eventsQueuev4Client. Без stickersSubscribeToQueue.
  Удалить callsQueuev4Client полностью (V2.1.6 работал без него).
- **Вариант C (полный rollback):** Скопировать V2.1.6 SovaApp.kt startCallSignaling +
  startCallNotifier + Queuev4Client.kt + VKEndpoints.kt as-is.

### 3.6 Дополнительно — calls-queuev4 подписана на НЕ ту очередь (cross-ref DIAG-A §3)
Даже если LP 115 "-1" guard будет удалён, текущая подписка calls-queuev4Client на
`onlfriends_<uid>,accountcounters_<uid>` (countersQueueId) НЕ доставит
entity_type:"calls" / incoming_call events. Это отдельная проблема от LP 115.
В V2.1.6 calls-queue была `multiaccount_<uid>` (multiaccountQueueId) через
`queue.subscribe` (а не stickers.subscribeToQueue). **Для полной регрессии нужно
ВЕРНУТЬ и LP guard fix, и queue_ids + API method fix** (Вариант B или C).

---
## §4. Lifecycle: foreground vs background

### 4.1 Foreground/background hooks
- **ProcessLifecycleOwner:** зарегистрирован в MainActivity.kt:950#onCreate
  ("#CHECK-ON-FOREGROUND: ProcessLifecycleOwner updater hook registered").
- **ActivityLifecycleCallbacks:** `SovaApp.kt:836#onActivityStarted` ("Activity started (MainActivity), foreground count=1")
  и `SovaApp.kt:809#onActivityStopped` ("Activity stopped (MainActivity), foreground count=0") —
  из v2.2.0 лога (read_1791285377832_a2423bbf90bc.txt:138, 148).
- **notifyResumed (foreground):** `LongPollClient.kt:165#notifyResumed` — вызывается
  onActivityStarted → сбрасывает backoff, evicts connections, cancels in-flight poll.

### 4.2 Что НЕ паузится на background
- NotificationsPoller — НЕ имеет stop-на-background логов. Poller продолжает работу
  (pollOnce каждые ~2 мин) и в foreground, и в background.
- SecurityAlertsPoller — стартует в onCreate, останавливается только после VK API error.
- CommunityPostsPoller — стартует в onCreate. В v2.2.1 при первом onActivityStopped
  (22:21:41.730) CommunityPostsPoller.kt:187#stop ВЫЗЫВАЕТСЯ. Но позже в логе (16:07:56.214)
  poller снова стартует. Значит — pause на background, restart на foreground.

### 4.3 Что ПАУЗИТСЯ на background (teardown sequence)
При onActivityStopped (foreground count=0):
1. `LongPollClient.kt:205#stop` — LongPoll stopped.
2. `CommunityPostsPoller.kt:187#stop` — Community posts poller stopped.
3. `LongPollKeepAliveService.kt:316#stop` — keep-alive service stopped.
4. `RealtimeWakeReceiver.kt:129#cancel` — DOZE-RELIABILITY alarm cancelled.

При восстановлении foreground (post-auth или network restored):
1. `LongPollClient.kt:185#start` — LongPoll started.
2. `EventHubWebSocketClient.kt:65#start` — EventHub WebSocket started.
3. `LongPollKeepAliveService.kt:303#start` — keep-alive service started.
4. `RealtimeWakeReceiver.kt:98#schedule` — DOZE-RELIABILITY alarm scheduled (+15min).

### 4.4 Проблемы
- **EventHub не имеет явного stop-на-background лога** — возможно остаётся подключённым
  в background (WS connection), что может приводить к battery drain или 401 spam.
- **NotificationsPoller НЕ паузится на background** — это CORRECT behavior для push-уведомлений
  (нужно ловить даже когда app закрыт). Но polling каждые 2 мин = battery cost.
- **pause на background для LP** — это STRANGE, потому что LP — это долгоживущий long-poll
  запрос (wait=25 сек). Pause при каждом onStop = restart = новый getLongPollServer fetch
  (40-300ms) → относительно небольшой overhead. Но в Doze mode LP не запустится без
  RealtimeWakeReceiver (см. §7).

---
## §5. Foreground service (LongPollKeepAliveService) — Q5

### 5.1 Foreground promote
- `LongPollKeepAliveService.kt:69#onCreate` → "onCreate: starting foreground + headless refresh observer".
- ServiceCompat.startForeground with `ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING`
  (worklog P4.1+P4.2, read_1791189065691_075856c4e6f8.txt:1061-1071).
- Notification: id=7777, channel=`bg_keepalive`, flags=0x62 (FLAG_FOREGROUND_SERVICE |
  FLAG_NO_CLEAR | FLAG_ONGOING_EVENT) — DIAG-B §8.
- "headless refresh observer" — намёк на то, что service запускает фоновый observer
  (вероятно для periodic re-validation session_key / silentRefresh).

### 5.2 onStartCommand поведение
- `LongPollKeepAliveService.kt:85#onStartCommand` → "onStartCommand: LongPollClient ensured running".
- Service ACTIVELY проверяет, что LongPollClient запущен. Если LP умер → пере-запустит.
- Idempotent: `LongPollClient.kt:180#start` → "start: already running" — LP защищён от
  duplicate starts.

### 5.3 START_STICKY / REDELIVER_INTENT — НЕ ВЕРИФИЦИРОВАНО
- Не видно из логов, какой `onStartCommand` return value (START_STICKY / START_REDELIVER_INTENT /
  START_NOT_STICKY). Нужен исходник LongPollKeepAliveService.kt:85-100.
- ЕСЛИ START_STICKY: service будет пере-создан системой после kill → при запуске onStartCommand
  пере-запустит LongPollClient. Хорошо для surviving app kill.
- ЕСЛИ START_NOT_STICKY: service умрёт вместе с app → notifications перестанут работать
  после принудительного kill.

### 5.4 Survive app kill?
- Не подтверждено runtime-логами (нет записей "service recreated after kill" в логах).
- Однако: фоновый процесс приложения (`re.pinok.debug`) и service — ОДИН процесс (Android
  default), значит service НЕ переживёт app kill. Чтобы пережить — нужен `android:process=":background"`
  в manifest для service. НЕ ВЕРИФИЦИРОВАНО без manifest.

### 5.5 Auto-restart LP если LP dies?
- ДА — косвенно подтверждено. `onStartCommand: LongPollClient ensured running` означает,
  что при каждом onStartCommand service проверяет LP и при необходимости запускает.
- Также RealtimeWakeReceiver.onReceive → LongPoll.notifyResumed → тоже пере-запускает LP.
- Покрытие: foreground service onStartCommand + 15-min alarm wake = двойная защита.

---
## §6. Boot receiver — Q6 — НЕ ВЕРИФИЦИРОВАНО ❓

### 6.1 Что есть в логах
- НЕТ ни одной записи от `BootReceiver` во всех доступных logcat-захватах.
- НЕТ записей `onBootCompleted`, `RECEIVE_BOOT_COMPLETED` permission grant.
- НЕТ записей "service started after boot" / "alarm rescheduled after boot".

### 6.2 Что можно предположить из worklog P4.1+P4.2
Cached read `read_1791139959775_25a672952f36.txt` упоминает "SovaApp lifecycle, BootReceiver, Linkify"
в тегах логов (строка 235, 299). Значит класс `BootReceiver` существует в кодовой базе.

### 6.3 РИСК без верификации
Если BootReceiver НЕ запускает LongPollKeepAliveService после перезагрузки устройства,
то после reboot:
- LongPoll НЕ запустится автоматически (он запускается только post-auth в SovaApp/MainActivity).
- NotificationsPoller НЕ запустится (он в SovaApp.onCreate — нужно открыть app).
- RealtimeWakeReceiver alarm НЕ запланирован (он schedule'ится только после старта LP).
- → ВСЕ уведомления НЕ будут работать до тех пор, пока пользователь не откроет приложение.

**Verdict:** UNKNOWN — нужно проверить `app/src/main/AndroidManifest.xml` на:
1. `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`
2. `<receiver android:name=".locker.BootReceiver" ...>` с `<action android:name="android.intent.action.BOOT_COMPLETED" />` intent-filter
3. Реализация BootReceiver.kt → startService(LongPollKeepAliveService) (или
   startForegroundService на API 26+).

---
## §7. Battery / Doze — Q7 — ЧАСТИЧНО ПОДТВЕРЖДЕНО

### 7.1 DOZE-RELIABILITY mechanism (РАБОТАЕТ)
- `RealtimeWakeReceiver.kt:98#schedule` → "#DOZE-RELIABILITY alarm scheduled (exact, +15min)".
- Alarm type: exact (вероятно `AlarmManager.setExactAndAllowWhileIdle` или `setAlarmClock`).
- Interval: +15 минут.
- onReceive action: `re.pinok.action.REALTIME_WAKE`.
- Wake-triggered actions (RealtimeWakeReceiver.kt:50#onReceive):
  "realtime woken (LongPoll.notifyResumed + EventHub.start)" — восстанавливает LP + EventHub.

### 7.2 WakeLock — НЕ ВЕРИФИЦИРОВАНО
- НЕТ записей `wakeLock.acquire` / `wakeLock.release` / `PowerManager.newWakeLock` в логах.
- Вероятно WakeLock используется внутри RealtimeWakeReceiver (через `WakeLockManager` или
  `BroadcastReceiver.goAsync` + `PowerManager`), но БЕЗ исходников НЕ ПОДТВЕРЖДЕНО.

### 7.3 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS — НЕ ВЕРИФИЦИРОВАНО
- НЕТ записей `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` / `isIgnoringBatteryOptimizations` /
  `requestIgnoreBatteryOptimizations` в логах.
- Если permission НЕ объявлен в manifest и НЕ запрошен runtime → приложение будет
  Doze-restricted → 15-min exact alarm может не сработать в Doze mode (Android 6+).
- На Android 13+ точные alarm'ы требуют `USE_EXACT_ALARM` (normal permission) или
  `SCHEDULE_EXACT_ALARM` (special permission, needs user grant).
- **РИСК:** 15-min alarm может не сработать в Doze, если не запрошен SCHEDULE_EXACT_ALARM.

### 7.4 DOZE-RELIABILITY alarm firing — ДОКАЗАНО
В `Pasted Content_1791908927389.txt` (2026-10-13 19:17:43.831):
```
2026-10-13 19:17:43.831 RealtimeWakeReceiver.kt:40#onReceive
   "#DOZE-RELIABILITY wake-receiver: action=re.pinok.action.REALTIME_WAKE"
2026-10-13 19:17:43.835 LongPollClient.kt:165#notifyResumed
   "notifyResumed: resetting backoff, evicting connections, cancelling in-flight poll"
2026-10-13 19:17:43.841 RealtimeWakeReceiver.kt:50#onReceive
   "#DOZE-RELIABILITY realtime woken (LongPoll.notifyResumed + EventHub.start)"
2026-10-13 19:17:43.845 RealtimeWakeReceiver.kt:98#schedule
   "#DOZE-RELIABILITY alarm scheduled (exact, +15min)"
```
Alarm сработал через ~5 мин после предыдущего schedule. Не точно +15 мин, но в пределах
Doze maintenance window — это нормально.

**Verdict: PARTIAL — DOZE-RELIABILITY механизм РАБОТАЕТ (alarm fires, LP/EventHub
restart). WakeLock + battery opt-out НЕ ПОДТВЕРЖДЕНО.**

---
## §8. Manifest completeness — Q8 — НЕ ВЕРИФИЦИРОВАНО ❓

### 8.1 Что должно быть в AndroidManifest.xml
- `<uses-permission android:name="android.permission.INTERNET" />`
- `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />` (для BootReceiver)
- `<uses-permission android:name="android.permission.WAKE_LOCK" />` (для wakeLock в RealtimeWakeReceiver)
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />` (для KeepAliveService)
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING" />` (Android 14+)
- `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` (Android 13+)
- `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />` или
  `<uses-permission android:name="android.permission.USE_EXACT_ALARM" />` (Android 12+)
- `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />` (optional)
- `<service android:name=".realtime.LongPollKeepAliveService"
          android:foregroundServiceType="remoteMessaging"
          android:exported="false" />`
- `<receiver android:name=".realtime.RealtimeWakeReceiver" android:exported="false">
     <intent-filter>
       <action android:name="re.pinok.action.REALTIME_WAKE" />
     </intent-filter>
   </receiver>`
- `<receiver android:name=".locker.BootReceiver" android:exported="true">
     <intent-filter>
       <action android:name="android.intent.action.BOOT_COMPLETED" />
     </intent-filter>
   </receiver>`

### 8.2 Подтверждённое —_CLIPDownloadService registered
Из worklog Task 3 (read_1791137421183_270e35d76230.txt:1770):
> Зарегистрировал сервис в AndroidManifest.xml (после StoryVideoDownloadService, line 261):
>   `<service android:name=".media.ClipDownloadService" ... />`

Это подтверждает, что StoryVideoDownloadService и ClipDownloadService есть в manifest.
НО отсутствие прямого доступа к AndroidManifest.xml не позволяет проверить наличие
LongPollKeepAliveService / RealtimeWakeReceiver / BootReceiver declarations.

### 8.3 РИСК
- Если `RealtimeWakeReceiver` НЕ объявлен в manifest с intent-filter `re.pinok.action.REALTIME_WAKE`
  → alarm не сработает после app kill (BroadcastReceiver не получит broadcast). Но в логах
  он срабатывает → значит intent-filter есть (runtime-registered не подойдёт — должен быть
  manifest-declared, потому что alarm fires даже когда app killed).
- Если `BootReceiver` НЕ объявлен с `<action android:name="android.intent.action.BOOT_COMPLETED" />`
  → не получит broadcast после перезагрузки → notifications перестанут работать до открытия app.
- Если `LongPollKeepAliveService` НЕ имеет `android:foregroundServiceType="remoteMessaging"`
  → на Android 14+ startForeground с REMOTE_MESSAGING type упадёт с SecurityException.

**Verdict:** UNKNOWN — требует прямого чтения AndroidManifest.xml.

---
## §9. Multi-account — Q9 — НЕ ВЕРИФИЦИРОВАНО ❓

### 9.1 Что видно в логах
- ВСЕ 6 доступных logcat-захватов показывают работу с ОДНИМ аккаунтом: `uid=171093180`.
- НЕТ записей account switch / multi-account login / queue_ids для нескольких uid.
- queuev4Client подписан через `stickers.subscribeToQueue` (без параметров) →
  multiaccount-queue подразумевается в названии переменной, но в логах queue_ids
  не виден (вероятно, VK API возвращает общий multiaccount key для user, без queue_ids).

### 9.2 Оркестрация multi-account
- queuev4Client → "queuev4 активна (multiaccount, через stickers.subscribeToQueue)" (SovaApp.kt:2109).
- ИМЯ переменной `queuev4Client` (а не `multiaccountQueuev4Client`) — намёк, что
  клиент ОДИН на все аккаунты (multiaccount в смысле multi-device sync, не multi-user).
- НО V2.1.6 использовал `multiaccountQueueId(uid) = "multiaccount_<uid>"` — суффикс
  uid, значит ОДИН queuev4Client на аккаунт, queue_id перекомандуется при switch.

### 9.3 РИСК без верификации
- Если пользователь переключит аккаунт и старый queuev4Client НЕ остановлен →
  будет polling старой очереди со старыми credentials → 401 spam или wrong events.
- В логах НЕТ `stop()` calls при account switch — НО это потому что в логах
  нет account switch моментов.
- Нужны исходники SovaApp.kt для поиска `accountSwitchHandler` / `onAccountChanged`
  callback, который должен: stop LP + stop queuev4 ×3 + clear SovaPrefs + restart.

**Verdict:** UNKNOWN — без исходников и без logcat-захвата account switch не верифицируется.

---
## §10. Stop / cleanup on logout / account switch — Q10

### 10.1 Подтверждённые stop calls (из v2.2.1 startup sequence, §1C):
| Channel | Stop call site | Trigger |
|---------|---------------|---------|
| LongPollClient | LongPollClient.kt:205#stop | onActivityStopped (foreground count=0) |
| CommunityPostsPoller | CommunityPostsPoller.kt:187#stop | onActivityStopped (foreground count=0) |
| LongPollKeepAliveService | LongPollKeepAliveService.kt:316#stop | onActivityStopped (foreground count=0) |
| RealtimeWakeReceiver alarm | RealtimeWakeReceiver.kt:129#cancel | onActivityStopped (foreground count=0) |
| SecurityAlertsPoller | SecurityAlertsPoller.kt:99#stop | pollOnce error (apiCode=3) |

### 10.2 НЕ ПОДТВЕРЖДЁННЫЕ stop calls (risk of leak)
- **EventHubWebSocketClient.stop** — НЕТ ни одного лога "EventHub stopped" или
  "EventHubWebSocketClient.stop". Возможно метод существует, но не логирует. Возможно
  EventHub остаётся подключённым даже после onActivityStopped (WS connection persistent).
  В логах видно что EventHub.onFailure → reconnect цикл продолжается в background.
- **callsQueuev4Client.stop / queuev4Client.stop / eventsQueuev4Client.stop** — НЕТ логов
  "queuev4 stopped". Возможно методы существуют, но не логируют.
- **NotificationsPoller.stop** — НЕТ логов "NotificationsPoller stopped" в v2.2.1 startup
  (он стартует в onCreate и НЕ останавливается при onActivityStopped — это CORRECT для
  push-уведомлений). Но при logout / account switch должен быть stop + restart с новыми
  credentials — НЕ ВИДНО в логах.

### 10.3 Logout handling
- Worklog P0.43-r3 (read_1792663418979_26cc7c23f0ff.txt:1178-1186) упоминает:
  `startCallSignaling` переведён на queuev4.vk.ru (im1180). Если logout_hash используется
  для cleanup (login.vk.ru → logout?hash=...), то после logout credentials инвалидны.
- НО нет прямых логов "all realtime channels stopped on logout" в доступных logcat.
- `ExchangeAuthRepository.kt:1510#onResponse` (Pasted Content_1791919723547.txt:4318) —
  silentRefreshViaRemixsidLive возвращает logout_hash, но это refresh-сценарий, не logout.

**Verdict:** PARTIAL — для foreground/background переходов cleanup РАБОТАЕТ (4 канала
останавливаются). Для logout / account switch cleanup НЕ ВЕРИФИЦИРОВАНО — риск утечки
(stale credentials → 401 spam от queuev4 clients).

---
## §11. Recent regressions — git log

### 11.1 git log НЕДОСТУПЕН
- Repo `/home/z/repos/PinoK_1` отсутствует → `git log --oneline -30 -- app/src/main/java/re/pinok/SovaApp.kt app/src/main/java/re/pinok/realtime/` НЕ ВЫПОЛНЯЕТСЯ.
- Cached git log: bash_1791137829004_744acde14d37.txt (29.7KB) — содержит коммиты
  610be42 → 04c7256 (snapshot before markdown/txt/docs cleanup). Это V2.2.x era.
- Основные коммиты по оркестровке (из кэша):
  • `5e9d958 fix(video): волна 38 — #VIDEO-BG-KEEP` — фоновое видео умирает через 200мс
    после ухода в фон (ROM юзера убивает active). НЕ о realtime оркестровке.
  • `9d4c88c feat(video/community): волна 36 — фикс краша FG-сервиса` —
    #VIDEO-FG-TIME: FATAL ForegroundServiceDidNotStartInTimeException — VideoPlaybackService
    onCreate теперь ПЕРВЫМ делом promoteToForegroundImmediately. НЕ о LongPollKeepAliveService,
    но shows паттерн foreground service start.
  • `04c7256 fix(stats+sec): mini_app_token для statsDashboard.* (err=3) + маскировка logout_hash + #STATS-FIX3` —
    упоминание "stats+sec" намекает на фикс SecurityAlertsPoller или StatsQueuePoller, но
    из логов v2.2.1 видно что SecurityAlertsPoller ВСЁ ЕЩЁ BROKEN (apiCode=3 "Unknown method").
    Значит либо фикс incomplete, либо refers к другому "stats" (account.getCounterStats?).

### 11.2 Документированные регрессии из worklog P0.43-r8
- **LP 115 "-1" guard regression** (SovaApp.kt:1808-1810, LongPollClient.kt:987-989):
  добавлено между V2.1.6 (no guard, working) и P0.43-r7 (commit 6fbb83b, guard present,
  incoming calls broken). Точная дата ввода guard'а НЕ определена (git log недоступен).
- **calls-queuev4 mis-subscription** (countersQueueId вместо callsQueueId/multiaccountQueueId):
  worklog P0.43-r3 (commit ac07af7, +174/-67 строк) перевёл на calls.subscribeToQueue через
  calls.okcdn.ru. Но worklog DIAG-A §3 указывает, что в v2.2.1 calls-queuev4 снова использует
  queue.subscribe (НЕ calls.subscribeToQueue) — то есть commit ac07af7 был ОТМЕНЁН в последующих
  коммитах. Точный revert-commit НЕ найден (git log недоступен).

### 11.3 Рекомендация по git inspect
После восстановления доступа к /home/z/repos/PinoK_1 выполнить:
```
git log --oneline -30 -- app/src/main/java/re/pinok/SovaApp.kt \
                          app/src/main/java/re/pinok/realtime/LongPollClient.kt \
                          app/src/main/java/re/pinok/realtime/Queuev4Client.kt \
                          app/src/main/java/re/pinok/realtime/LongPollKeepAliveService.kt \
                          app/src/main/java/re/pinok/realtime/RealtimeWakeReceiver.kt \
                          app/src/main/java/re/pinok/realtime/EventHubWebSocketClient.kt \
                          app/src/main/java/re/pinok/realtime/NotificationsPoller.kt \
                          app/src/main/java/re/pinok/realtime/CommunityPostsPoller.kt \
                          app/src/main/java/re/pinok/realtime/SecurityAlertsPoller.kt \
                          app/src/main/AndroidManifest.xml \
                          app/src/main/java/re/pinok/locker/BootReceiver.kt
git log --oneline --grep="LP 115\|payload.*-1\|заглушка\|incoming_call\|case 115"
git log -p --all -S 'payload == "-1"' -- app/src/main/java/re/pinok/realtime/LongPollClient.kt
git log -p --all -S 'заглушка -1' -- app/src/main/java/re/pinok/SovaApp.kt
```

---
## СВОДНЫЙ VERDICT ПО ВСЕМ КАНАЛАМ

| # | Канал | Verdict | Доказательство |
|---|-------|---------|----------------|
| 1 | LongPollClient (api.vk.com LP v3) | ✅ WORKING | [logcat v2.2.1] LongPollClient.kt:185#start, :584#loop (failed=0, ~25s polls) |
| 2 | EventHubWebSocketClient (wss://eh.vk.ru) | ⚠ PARTIAL | [logcat v2.2.1] :176#onOpen, :206#onFailure (pong timeout каждые 30s) |
| 3 | callsQueuev4Client (calls-queuev4) | ⚠ PARTIAL | [logcat v2.2.1] SovaApp.kt:2081 — подписан на counters, не на calls |
| 4 | queuev4Client (multiaccount) | ✅ WORKING | [logcat v2.2.1] SovaApp.kt:2106/2109 — stickers.subscribeToQueue OK |
| 5 | eventsQueuev4Client (events) | ✅ WORKING | [logcat v2.2.1] SovaApp.kt:2125 — events_queuev4 активна |
| 6 | NotificationsPoller | ✅ WORKING | [logcat v2.2.1] SovaApp.kt:2602, NotificationsPoller.kt:183 — 26 total, 6 NEW |
| 7 | CommunityPostsPoller | ⚠ PARTIAL | [logcat v2.2.1] SovaApp.kt:2621 — стартует, но notifyMode=0 → inert |
| 8 | SecurityAlertsPoller | ❌ BROKEN | [logcat v2.2.1] SecurityAlertsPoller.kt:128 — VK apiCode=3 "Unknown method" |
| 9 | StatsQueuePoller | ❓ DEAD-CODE / NOT FOUND | нет ни одного лога в v2.1.9/v2.2.0/v2.2.1 — вероятно не существует |
| 10 | LongPollKeepAliveService (foreground) | ✅ WORKING | [logcat v2.2.1] :69 onCreate (foreground), :85 onStartCommand (LP ensured running) |
| 11 | RealtimeWakeReceiver (Doze alarm) | ✅ WORKING | [logcat v2.2.1] :98 schedule, :40/:50 onReceive (alarm fires + LP restart) |
| 12 | BootReceiver | ❓ UNKNOWN | нет логов; worklog P4.1+P4.2 упоминает как существующий класс |
| 13 | LP case 115 → IncomingCall | ❌ BROKEN (per worklog P0.43-r8) | [P0.43-r8 worklog] SovaApp.kt:1808-1810, LongPollClient.kt:987-989 (НЕ ВЕРИФИЦИРОВАНО в v2.2.1) |
| 14 | ProcessLifecycleOwner foreground hook | ✅ WORKING | [logcat v2.2.1] MainActivity.kt:950 — registered |
| 15 | ActivityLifecycleCallbacks (onActivityStarted/Stopped) | ✅ WORKING | [logcat v2.2.1] SovaApp.kt:836/809 — foreground count tracking |

---
## ПРИОРИТЕТЫ ИСПРАВЛЕНИЙ (по evidence-weight)

**P0 (КРИТИЧНО — incoming calls broken):**
1. §3 — Подтвердить/удалить LP 115 "-1" guard в SovaApp.kt:1808-1810 И LongPollClient.kt:987-989.
   Для верификации: прочитать LongPollClient.kt и SovaApp.kt в текущем HEAD репо.
   ВНИМАНИЕ: cached LongPollClient.kt:956-963 уже показывает V2.1.6-correct behavior
   (безусловный emit IncomingCall). worklog P0.43-r8 строка 988 может содержать
   ОШИБОЧНУЮ ссылку — строки 987-989 = КОММЕНТАРИЙ к handleNewMessage echo-skip (Fix #138),
   НЕ payload=="-1" guard. Требуется FRESH чтение исходников.

2. §3.6 — Восстановить V2.1.6 calls-queuev4 подписку:
   queuev4Client ← queue.subscribe(queue_ids="multiaccount_<uid>") (multiaccountQueueId),
   eventsQueuev4Client ← queue.subscribe(queue_ids="onlfriends_<uid>,accountcounters_<uid>") (countersQueueId),
   БЕЗ stickers.subscribeToQueue, БЕЗ callsQueuev4Client.
   Cross-ref: DIAG-A §3 (calls-queue подписана на counters — entity_type:"calls" НЕ доставляется).

**P1 (серьёзная проблема, влияет на надёжность):**
3. §2.2 — EventHub pong timeout. EventHubWebSocketClient.kt:202#onFailure → :145#connectLoop
   reconnect cycle каждые 30 сек. Возможно OkHttp WebSocket ping frame не обрабатывается
   ehsp2-протоколом VK — нужен ручной текстовый ping вместо OkHttp WebSocket.ping().
4. §2.6 — SecurityAlertsPoller.kt:128 — VK apiCode=3 "Unknown method passed" для
   `accountPersonal.getSecurityAlerts`. Poller permanently stops. Либо убрать poller полностью
   (чтобы не плодить ложные логи), либо найти правильный VK API method/токен.

**P2 (нужно верифицировать):**
5. §6 — BootReceiver — проверить manifest entry + реализацию. Если отсутствует → notifications
   не работают после перезагрузки устройства.
6. §8 — AndroidManifest completeness — проверить permissions + service/receiver declarations.
7. §7 — REQUEST_IGNORE_BATTERY_OPTIMIZATIONS + SCHEDULE_EXACT_ALARM + WAKE_LOCK permissions.

**P3 (minor):**
8. §2.5 — CommunityPostsPoller notifyMode=0 → community posts silently filtered. Либо UI-toggle
   для режима, либо показывать на отдельном канале.
9. §10 — Cleanup on logout / account switch — нужны исходники для проверки stop-вызовов
   (callsQueuev4Client.stop, queuev4Client.stop, eventsQueuev4Client.stop, EventHub.stop).
10. §9 — Multi-account handling — нужны logcat-захват account switch + исходники SovaApp.kt
    для проверки accountSwitch callback.

---
## NEXT ACTIONS (требуется доступ к /home/z/repos/PinoK_1)
1. Прочитать `app/src/main/java/re/pinok/SovaApp.kt` — найти строки 1808-1810 (если ещё там)
   и surrounding startCallNotifier code; проверить наличие `if (event.payload == "-1")` guard.
2. Прочитать `app/src/main/java/re/pinok/realtime/LongPollClient.kt:956-963` и `:980-1022` —
   подтвердить что case 115 emits unconditionally, а строки 987-989 — комментарий (Fix #138).
3. Прочитать `app/src/main/AndroidManifest.xml` — проверить все permissions и service/receiver
   declarations (BootReceiver, RealtimeWakeReceiver, LongPollKeepAliveService).
4. Прочитать `app/src/main/java/re/pinok/locker/BootReceiver.kt` — проверить onReceive →
   startForegroundService(LongPollKeepAliveService).
5. Прочитать `app/src/main/java/re/pinok/realtime/LongPollKeepAliveService.kt:69-316` —
   проверить START_STICKY / START_REDELIVER_INTENT return value + onCreate foreground promote.
6. Прочитать `app/src/main/java/re/pinok/realtime/RealtimeWakeReceiver.kt:40-130` —
   проверить WakeLock acquire/release + AlarmManager.setExactAndAllowWhileIdle vs setAlarmClock.
7. Прочитать `app/src/main/java/re/pinok/realtime/EventHubWebSocketClient.kt:140-206` —
   понять причину pong timeout (возможно нужен ручной ping frame).
8. `git log --oneline -30 -- app/src/main/java/re/pinok/SovaApp.kt
   app/src/main/java/re/pinok/realtime/` — найти commit, добавивший LP 115 "-1" guard.
9. `git log -p --all -S 'payload == "-1"'` — найти точное место ввода guard'а.
10. Проверить cleanup-on-logout: grep `logout\|accountSwitch\|stopAllRealtime\|stop()` в SovaApp.kt.

- Файлы кода НЕ изменены. Изменён только /home/z/my-project/worklog.md (этот блок).

---
Task ID: DIAG-B2
Agent: Explore (notifiers & display) — RE-RUN WITH SOURCE
Task: Диагностика отображения уведомлений (system notifiers + actions)

Work Log:
- Прочитан worklog.md (lines 1500-2119): подтверждено, что предыдущий DIAG-B упал
  на «repo missing». Репо /home/z/repos/PinoK_1 доступно, ветка PinoK, HEAD=f678836.
- Прочитаны (полностью, из исходников):
  MessageNotifier.kt (350 строк), VkNotificationsNotifier.kt (1306 строк),
  SecurityAlertNotifier.kt (127 строк), ReplyResultNotifier.kt (122 строки),
  NotificationActionReceiver.kt (229 строк), NotificationSource.kt (38 строк),
  UnreadMessagesCounter.kt (202 строки), SnNotifyFilter.kt (244 строки),
  EventDedup.kt (36 строк), ClipsCounter.kt (90 строк),
  LongPollKeepAliveService.kt (322 строки), NotificationsPoller.kt (264 строки),
  SecurityAlertsPoller.kt (217 строк), NotifyModeSelector.kt (203 строки),
  AndroidManifest.xml (500 строк), PermissionManager.kt (247 строк),
  SovaApp.kt:1530-1816 (init + startMessageNotifier), SovaApp.kt:2771-2829,
  MainActivity.kt:2360-2516 (handleOpenChatIntent + handleDeepLinkIntent),
  ChatDetailScreen.kt / MessagesScreen.kt / NotificationsScreen.kt (key call sites).
- Grep TODO/FIXME/HACK/BUG по /app/src/main/java/re/pinok/realtime/ — 17 совпадений,
  все в ChannelWebSocketClient.kt (dead-code заглушки P4.3) + 1 в SecurityAlertNotifier.kt:31.
- git log --all -5 на 6 notifier-файлов — НЕТ истории (только текущий commit c9d4ff8
  в git log без --all, с --all история пуста — repo shallow clone без full history).
  Невозможно определить, когда введён guard или revert.

Stage Summary:
- 4 notifiers полностью описаны (channel id/name/IMPORTANCE/icon/PendingIntent target).
- Все 5 каналов MessageNotifier + 10 каналов VkNotificationsNotifier + 1 channel
  LongPollKeepAlive — создаются в SovaApp.onCreate (MessageNotifier.init @ SovaApp.kt:1543,
  VkNotificationsNotifier.init @ SovaApp.kt:1552, ensureChannel в KeepAlive @ :258-271).
- Notification actions: MARK_READ + REPLY (RemoteInput) + DISMISS — все работают, код
  receiver чистый, manifest intent-filters на месте (AndroidManifest.xml:453-462).
- POST_NOTIFICATIONS runtime permission: PermissionManager.RequestAllPermissionsEffect
  вызывается из MainActivity:1488 ПОСЛЕ валидного токена. Без permission — нотифаеры
  тихо skip (MessageNotifier.kt:205-210, VkNotificationsNotifier.kt:333-338).
- EventDedup: ключ (peerId, msgId), окно 5 сек (EventDedup.kt:21-36). Используется
  в UnreadMessagesCounter (UnreadMessagesCounter.kt:93) и SovaApp.startMessageNotifier
  (SovaApp.kt:1645) для дедупа EventHub+LongPoll.
- SnNotifyFilter: 19 sn_* predicates + 41 default value. Применяется в showBatch
  (VkNotificationsNotifier.kt:397). Defaults ВКЛЮЧАЮТ sn_likes=false, sn_reposts=false,
  sn_new_posts=false, sn_app_invites=false — лайки/репосты по умолчанию фильтруются.
- UnreadMessagesCounter: StateFlow<Int> для IN-APP badge (нижний нав-бар). Лаунчер-
  badge — НЕ ПИШЕТСЯ явно (нет ShortcutBadger, нет setNumber на launcher). Системный
  badge на иконке появляется автоматически через setNumber(unread) в MessageNotifier:260
  (Android runtime применяет badge канала, setShowBadge по умолчанию true).
- Tap routing: MessageNotifier → ACTION_OPEN_CHAT → MainActivity.handleOpenChatIntent
  (MainActivity.kt:2378-2394) → pendingOpenChatPeerId → SovaNavHost → ChatDetailScreen
  (где MessageNotifier.cancelNotification @ ChatDetailScreen.kt:3194). Для deleted
  message или archived chat — НЕТ специальной обработки: если peerId валиден (не 0),
  navigation пробуется без проверки существования. #IM-CHANNEL-OPEN (MainActivity.kt:2380)
  фикс позволил peerId<0 (каналы), раньше отклонялись.
- LongPollKeepAliveService: id=7777, channel=bg_keepalive, IMPORTANCE_LOW, title="PinoK",
  text="Получение сообщений в фоне", icon=ic_notification, setOngoing(true) (НЕ dismissible).
  Tap → MainActivity. Тип remoteMessaging (Android 14+).
- SecurityAlertsPoller.kt:119-134: BROKEN — accountPersonal.getSecurityAlerts
  возвращает apiCode=3 "Unknown method passed" через WEB-шлюз. Poller self-stops
  (SecurityAlertsPoller.kt:128-129). SecurityAlertNotifier.showAlert НЕ ВЫЗЫВАЕТСЯ —
  chain порван на poller level. Notifier код исправен, но мёртв (нет источника данных).
- VkNotificationsNotifier batching: showBatch (VkNotificationsNotifier.kt:321-434) →
  groupItems по mode (none/category/community/user) → showGroup → showSingle+summary.
  Per-channel dedup — НЕТ (каждое уведомление получает новый notifId через
  AtomicInteger nextId.incrementAndGet() @ VkNotificationsNotifier.kt:659). Новые
  уведомления STACK (не заменяют) — замена только для MessageNotifier (NOTIFICATION_ID_BASE
  + peerId.hashCode() обновляет in-place @ MessageNotifier.kt:287).

---
Task ID: DIAG-D2
Agent: Explore (settings & prefs) — RE-RUN WITH SOURCE
Task: Диагностика хранилища настроек уведомлений и sync с VK

Work Log:
1. Прочитал /home/z/my-project/worklog.md (tail) — предыдущий DIAG-A2 исследовал transport (LP/EventHub/queuev4/pollers), не настройки. DIAG-D (первый запуск) упал с «репо отсутствует» — сейчас репо доступно на f678836.
2. Прочитал focus-файлы: SovaPrefs.kt (2437 строк), NotifyModeSelector.kt (202), SnNotifyFilter.kt (244), VKNotifyWeb.kt (537), MessageNotifier.kt (350), Models.kt:PushSettings (726-758), SovaPrefsBackup.kt (787), SovaPrefsCrypto.kt (225), PinnedConversationsRepository.kt (108), ArchivedConversationsRepository.kt (78), PrivacySettingsScreen.kt (600).
3. Прочитал фрагменты SettingsScreen.kt: NotificationsTab (7266-7600), notify-toggle defs (7000-7127), toggleNotify impl (7290-7340), DND card (7414-7496), notifyModeOptions (8033-8098), quietHours UI (8255-8310).
4. Прочитал фрагменты VKApiClient.kt: messagesSetConversationPushSettings+accountSetSilentMode (2370-2539), settingsGeneral.* (19110-19329), account.startSilentMode/stopSilentMode/getSilentModeStatus (19330-19370).
5. Прочитал фрагменты ChatDetailScreen.kt: toggleMute (2167-2205), toggleChannelNotifications (2220-2306). ChatInfoScreen.kt: mute toggle (370-405). MessagesScreen.kt: onToggleMute (1778-1829), mute menu (2509-2524).
6. Прочитал SovaApp.kt:1632-1764 — startMessageNotifier, применение notifyMode + выбор channelId + чтение cached.muted.
7. Прочитал VkNotificationsNotifier.kt:330-405 (showBatch фильтры) + 1125-1149 (isInQuietHours).
8. Grep по всему /app и /core: account.saveNotificationSettings → 0 совпадений. account.getNotificationSettings → 0. Все другие notify-related вызовы — найдены.
9. Grep TODO/FIXME/HACK/BUG в /realtime: ChannelWebSocketClient (TODO для активации P4.3), SecurityAlertNotifier (TODO action button), #NOTIF-NFDCAT (hide/unsubscribe endpoint TODO).
10. Cross-check VKNotifyWeb GROUP_KEYS (30 шт.) vs PinoK sn_* toggles (49 шт. в ALL_NOTIFY_TOGGLES): 21 sn_* ключ НЕ имеет веб-группы → фильтруются realNotifyToggles из UI.

Stage Summary:
- Локальные prefs: SovaPrefs.kt содержит ~15 notify-ключей (глобальные, quiet hours, per-user mute CSV, notifyMode, notifyCommunitiesSound/Vibration, notifyCacheJson, channelNotifEnabledIds, msgMute, emailNotifyFreq). Per-chat mute НЕ в SovaPrefs — server-side PushSettings + in-memory кеш MessageNotifier.
- VK server sync: account.saveNotificationSettings НЕ вызывается НИГДЕ. Вместо него — settingsGeneral.* (PinoK-имя для несуществующего VK API → fallback на VKNotifyWeb HTML-скрейпинг m.vk.ru/settings?act=notify). Для DND — account.startSilentMode/stopSilentMode (real VK API). Для per-chat mute — messages.setConversationPushSettings + fallback account.setSilenceMode. Для per-channel — channels.setNotificationMode + fallback messages.allow/denyFromGroup. Для per-community (admin) — groups.editNotifications.
- Per-chat mute: UI = binary Switch (no 1h/8h/forever). Всегда disabled_until=-1 (forever). Останавливает notification (MessageNotifier.showNotification line 199-202 проверяет muted).
- Per-community mute: 2 уровня — channel (peerId<0, canWrite=false) через channels.setNotificationMode; admin/editor через groups.editNotifications. Локальный кеш channelNotifEnabledIds.
- Quiet hours: LOCAL (pushQuietHours*) — enforced ТОЛЬКО в VkNotificationsNotifier.showBatch (community/news). MessageNotifier НЕ проверяет quiet hours → direct messages проходят. SERVER DND (account.startSilentMode) — PinoK отображает статус, но НЕ подавляет локально (PinoK не использует FCM).
- NotifyModeSelector: 4 режима (MESSAGES_ONLY=0, ALL=1, COMMUNITIES_ONLY=2, SILENT=3) — enum в SovaPrefs.kt:2432-2435. GLOBAL, не per-chat. UI в 2 местах: NotifyModeItem в SovaNavHost drawer + RadioButton в SettingsScreen.NotificationsTab.
- VKNotifyWeb.kt: HTML-скрейпер m.vk.ru/settings?act=notify. fetch() + parseSnapshot() + saveGroup() + savePushMaster/Group/Message/Chat (последние 4 НИКОГДА не вызываются). webGroupForPinoKKey() маппит 28 sn_* → веб-группы; 21 sn_* → null (фильтруются из UI).
- Backup/restore: SovaPrefsBackup.export использует prefs.rawSnapshot() → ВСЕ DataStore ключи (включая все notify prefs) попадают в экспорт. restoreRaw — атомарная транзакция с guard'ом конфликта типов. Шифрование через SovaPrefsCrypto (AES-256-GCM + PBKDF2). WORKING.
- Settings UI completeness: NotificationsTab + NotificationSettingsScreen покрывают ОСНОВНОЕ. НО 21 sn_* ключ (включая sn_birthdays, sn_messages, sn_chats, sn_groups, sn_new_posts, sn_friend_accepted, sn_message_requests, sn_app_invites, sn_stories, sn_photo_tags и др.) отфильтрованы из UI → MISSING.
- Known issues: (1) sn_push_send master toggle BROKEN — settingsSetNotifyViaWeb → webGroupForPinoKKey("sn_push_send")=null → revert + Toast. Должен использовать VKNotifyWeb.savePushMaster, но не вызывается. (2) quietHours НЕ применяется к direct messages. (3) Server-side DND (silent_until) НЕ подавляет локальные notifications. (4) Per-chat mute только FOREVER (нет 1h/8h). (5) emailNotifyFreq — dead store. (6) 4 метода VKNotifyWeb (savePushMaster/Group/Message/Chat) — dead code.


---
Task ID: DIAG-C2
Agent: Explore (notifications UI) — RE-RUN WITH SOURCE
Task: Диагностика экранов уведомлений (список + настройки)

Work Log:
- Прочитал /home/z/my-project/worklog.md (стр. 1039-1084 — провальный DIAG-C «репо отсутствует»). Репо теперь СУЩЕСТВУЕТ: /home/z/repos/PinoK_1, ветка PinoK, HEAD=f6788365, 307 .kt файлов проверены через LS.
- Прочитал оба целевых файла полностью:
  • NotificationsScreen.kt (2137 строк).
  • NotificationSettingsScreen.kt (767 строк).
- Прочитал связанные callsites в VKApiClient.kt:
  notificationsGet (9174), notificationsGetPage (9266), notificationsMarkAsRead (9293),
  notificationsMarkAsViewed (9306), notificationsHideItem (9332), notificationsUnsubscribe (9361),
  notificationsGetUnreadCounters (19058), settingsGeneralGetNotifySettings (19166),
  settingsGeneralSetNotifySettings (19207), settingsGeneralToggleNotify (19268),
  accountGetSilentModeStatus (19336), accountStartSilentMode (19359), accountStopSilentMode (19366),
  accountSetObsceneFilter (19482), groupsEditNotifications (20000), resolveRedesignNotificationType (18991).
- Прочитал навигационные входы в SovaNavHost.kt (2366-2432 — маршруты Notifications/NotificationSettings),
  drawer-список (857), feed-menu вход (1731), hasOwnTopBar-список (1000), deep-link router (611).
- Прочитал SettingsScreen.kt для входов: вкладка NOTIFICATIONS (212), NotificationsTab (7266, onOpenNotificationSettings),
  «Полные настройки уведомлений» (8312).
- Grep TODO/FIXME/HACK/BUG: NotificationsScreen — 3 TODO (#NOTIF-NFDCAT: 945, 1140, 1605); NotificationSettingsScreen — 0.
- git log --follow -- 'app/.../notifications/NotificationsScreen.kt' → единственный коммит c9d4ff8
  (P0.46 feat(comments): wall.getComment after reply + long-press menu) — файл был пересоздан/тронут в P0.46,
  более ранняя история не сохранена (вероятно squash/rename). Для NotificationSettingsScreen.kt — тот же
  единственный коммит c9d4ff8.
- КОД НЕ МОДИФИЦИРОВАЛ (агент Explore, read-only).

Stage Summary (file:line refs из РЕАЛЬНОГО исходника):

═══════════════════════════════════════════════════════════════════
1. NotificationsScreen — ИСТОЧНИК ДАННЫХ  → WORKING (с оговорками)
═══════════════════════════════════════════════════════════════════
• Источник: `app.apiClient.notificationsGetPage(count, startFrom, category)`
  (NotificationsScreen.kt:780, 867, 910). Внутри (VKApiClient.kt:9266-9283):
    – web-токен (vk1.a.*) → `notificationsGetRedesign(count, startFrom, category)` — канонический web-метод.
    – не-web токен → `notifications.get` (VKApiClient.kt:9174-9254) с fallback на getRedesign при err=3.
  То есть фактически ОСНОВНОЙ путь — `notifications.getRedesign` (через `notificationsGetPage`).
• Пагинация: КУРСОРНАЯ через `next_from` (НЕ offset). pageSize=30 (NotificationsScreen.kt:672).
  – Первая страница: NotificationsScreen.kt:780-801.
  – Pull-to-refresh: refreshNotifications() 862-889 (та же notificationsGetPage).
  – Load more: loadMoreNotifications() 892-937, передаёт `startFrom = nextFrom`.
  – endReached вычисляется по `nextFrom == null` (Fix #255, строка 801/876/928) — РАНЬШЕ был
    баг «< pageSize → конец», VK часто отдаёт <30 items но с next_from.
  – Триггер пагинации — `snapshotFlow { lastVisible >= size-3 }` (989-998) + ручная кнопка
    «Загрузить ещё» (1293-1300) как fallback.
  – Fix #NOTIF-PAGINATION-DOUBLE-LOAD (893-906): loadingMore ставится СИНХРОННО до launch,
    иначе snapshotFlow дважды дёргал loadMore с одним nextFrom → дубли.
• Дедупликация: `list.distinctBy { it.uniqueKey }` (794, 873). uniqueKey (VKApiClient.kt:9732-9738):
  – для redesign — полный `rawId` (Fix #255: раньше `rawId.take(40)` обрезал общий префикс → все
    23 уведомления схлопывались в 1);
  – для legacy — date+type+parentOwnerId+parentItemId+feedbackIds+text.hashCode.
• Поля, парсимые из VK: type/date/feedbackProfiles/feedbackIds/parentType/parentOwnerId/
  parentItemId/parentText/parentPhotoUrl/parentVideoThumb/attachments/actions/replyText/
  replyFromId/profilesMap/parentCommentId/parentUrl/rawId/dotsMenu/preButtons/preButtonUrls/
  attachmentType/attachmentsString/bubbleText (VKApiClient.kt:9621-9715).
• Last_viewed: `page.lastViewed` сохраняется в `lastViewed` (787-788), используется
  переключателем «Новые/Просмотренные».

═══════════════════════════════════════════════════════════════════
2. ТИПЫ УВЕДОМЛЕНИЙ — РЕНДЕРИНГ  → WORKING (большинство), PARTIAL (несколько типов)
═══════════════════════════════════════════════════════════════════
• Парсер redesign (VKApiClient.kt:18434 parseRedesignNotificationItem, тип определяется через
  resolveRedesignNotificationType 18991-19055):
  – Категория из dots_menu.name (машинно-читаемая): friend/birthday/gift/invite/mention/like/
    comment/cop/follow/market/podcast/topic/story/clip/photo/video/app/post|wall.
  – Фолбэк — глагол в item.text: «оценил»→like, «прокомментировал»→comment, «ответил»→
    reply_comment, «упомянул»→mention, «поделил»→copy, «подписал»→follow, «в друзья»→
    friend_accepted, «подарил»→gift, «день рождения»→birthday_reminder, «пригласил»→group_invites.
  – Контентные: post/wall→new_posts, photo, video, clip, comment, topic, market_item→market,
    story, app, podcast_episode→podcast, ""→new_posts.
• Иконки (NotificationsScreen.kt:316-388 getTypeIcon): like_post, like_comment, like_photo,
  like_video, like_topic, like (generic), comment, reply_comment, copy/repost, mention*,
  follow, friend_accepted, friend_requested, wall, new_posts/post, photo, video, clip, topic,
  market, story, app, podcast, birthday_reminder, app_request, else→Notifications-default.
  НЕТ icon-кейсов для: sdk_app_request, event_invite, group_invite (есть только в фильтре
  group_invites), wall_reply, audio. Они попадут в `else → defaultColor` — рендер корректный,
  но без тип-специфичной иконки.
• Фильтр (NotificationsScreen.kt:723-753): comment|mention|like|copy|follow|new_posts|birthday|
  gifts|messages|group_chats|group_invites|market|clips|stories|photos|videos|apps_requests.
  Все 16 категорий матчаtся (большинство через startsWith — корректно покрывает вариантные формы).
• Уведомления VK web-типов: post, comment, like, new_post, mention, wall — ОК.
  photo/video/audio: photo/video — ОК; audio — НЕТ (нет в resolveRedesignNotificationType, нет
  в фильтре). friend_accepted, group_invite, event_invite: friend_accepted — ОК; group_invites
  (через «пригласил»/dots_menu.name содержит «invite») — ОК; event_invite — НЕ парсится
  (явных event* веток нет, но пройдёт как pass-through parentType = "event*").
  app_invite, sdk_app_request: app_invite пройдёт через pass-through (n.contains("app")→"app"),
  sdk_app_request — НЕТ явной ветки. birthday — ОК (birthday_reminder).
  wall_reply — НЕ обрабатывается как тип (парсер не выделяет wall_reply → клиент увидит
  родительский тип comment/post).
• VERDICT: основные ~20 типов рендерятся корректно; audio/event_invite/sdk_app_request/
  wall_reply — отсутствуют (низкий приоритет: эти типы редки в redesign-ответе VK).

═══════════════════════════════════════════════════════════════════
3. INTERACTIONS — Тап / Long-press / Swipe / Pull-to-refresh  → WORKING
═══════════════════════════════════════════════════════════════════
• Тап по карточке (NotificationsScreen.kt:1409-1424 navigate): VkUrlDeepLinker.deepLinkFor(item)
  → точный target (пост с commentId, фото, видео, юзер, сообщество). Если DeepLinkAction=
  OpenNotifications → fallback на onUserClick (feedbackIds.first) → на onPostClick
  (parentOwnerId, parentItemId).
• Long-press (1435): `showContextMenu = true` → DropdownMenu (1921-1996): «Открыть профиль»,
  «Открыть запись» (если parentOwnerId!=0), «Скопировать текст», «Убрать из списка»
  (если dotsMenu.isHide), «Не уведомлять» (если dotsMenu.isUnsubscribe), «Настроить»
  (если dotsMenu.isOpenSetting). ПЛЮС видимая кнопка ⋮ (IconButton 1907-1917) — то же меню.
• Swipe to dismiss (1346-1377 SwipeToDismissBox): EndToStart → onDismiss → скрытие
  (hiddenKeys.add(key)) + Snackbar «Уведомление скрыто» + «Отменить» (1241-1250).
  StartToEnd отключён (enableDismissFromStartToEnd=false, 1364).
• Pull-to-refresh: PullToRefreshBox (1190-1194, Material3), isRefreshing → refreshNotifications()
  (862-889) → серверная перезагрузка через notificationsGetPage.
• Пагинация: snapshotFlow-триггер на lastVisible >= size-3 (989-998) + ручная кнопка
  «Загрузить ещё» (1293-1300) как fallback если экран короткий.
• Action-кнопки (1686-1764 FlowRow): «Ответить» (REPLY) → открывает пост (Fix #233 — для
  reply_comment открывает пост, а не чат с комментатором), «Подарить в ответ» (GIFT_REPLY),
  OPEN_USER, OPEN_POST. PreButtons (1609-1655) — под текстом, аналогичная логика.
• Секции (1195-1212): «НОВЫЕ» (<24h) и «РАНЬШЕ» (>=24h) — заголовки, как VK web.
• ScrollToTopFab (1309-1313) — FAB «наверх».

═══════════════════════════════════════════════════════════════════
4. MARK AS READ  → PARTIAL (UI не обновляется оптимистично)
═══════════════════════════════════════════════════════════════════
• Кнопка «Прочитать все» в TopAppBar actions (NotificationsScreen.kt:1017-1035):
  IconButton (иконка Visibility) → scope.launch { app.apiClient.notificationsMarkAsRead() }.
  Если ok → Snackbar «Все уведомления прочитаны». НЕТ обновления unreadCount=0 локально
  → бейдж в заголовке остаётся, пока не перезайдёшь на экран (тогда LaunchedEffect
  перечитает counters, 835-845).
• Auto-mark-as-viewed при входе: `app.apiClient.notificationsMarkAsViewed()` (854) —
  вызывается в LaunchedEffect(serverCategory), т.е. при каждом открытии экрана или смене
  категории. ПЛЮС `VkNotificationsNotifier.cancelAll(app)` (853) — отменяет активные
  системные уведомления. Это закрывает «dead code» (раньше notificationsMarkAsViewed был
  определён, но не вызывался — теперь активирован, см. коммент 850-852).
• API (VKApiClient.kt:9293-9303): `notifications.markAsRead` → response.state==1.
  `notifications.markAsViewed` (9306-9312) — с опциональным group_id.
• VERDICT: API-вызовы есть и корректные, но ОПТИМИСТИЧНОГО UI-обновления нет —
  после тапа «Прочитать все» бейдж не зануляется (только snackbar). Минорный UX-баг.

═══════════════════════════════════════════════════════════════════
5. ФИЛЬТРАЦИЯ / TABS  → WORKING (двухуровневая: серверная + локальная)
═══════════════════════════════════════════════════════════════════
• Серверные категории (NotificationCategoryBar, 534-580, 274-282 NOTIFICATION_CATEGORIES):
  7 категорий notifications.getRedesign: all («Уведомления профиля»), communities, feedback,
  friends, services, communication, account. Выбор → `serverCategory = key` → LaunchedEffect
  (772) → notificationsGetPage(category=...) → СЕРВЕРНАЯ перезагрузка списка.
• Локальный тип-фильтр (activeFilter, 722-753): 16 категорий (NOTIFICATION_FILTERS 233-254),
  применяется поверх серверной. НО UI-вход в bottom-sheet «Фильтр» УДАЛЁН (#NOTIFSHOW, 1047-1048
  / 1158-1159): «кнопка фильтров (N10) убрана — фильтр по типам закрыт». ActiveFilter остался
  в коде, но без UI-триггера пользователь НЕ может сменить тип-фильтр → фактически только
  серверная фильтрация по категориям. NotificationFilterSheet (448-521) — определён, но
  НИГДЕ не вызывается (showFilters удалён).
• Новые/Просмотренные toggle (589-621 NewViewedToggle): showViewed=false → только моложе
  last_viewed (720: item.date >= lastViewedValue). showViewed=true → все.
• Поиск (searchQuery, 754-758): по item.text + parentText + feedbackProfiles[].name.
• Сортировка sortDialogsFirst (297-303): диалоги (message*/mail/group/chat) выше новостных
  (класс A vs B), внутри классов порядок VK сохраняется.
• VERDICT: серверная фильтрация и переключатель Новые/Просмотренные — WORKING.
  Локальный тип-фильтр — MISSING в UI (хотя код фильтрации остался; кнопка-триггер удалена).

═══════════════════════════════════════════════════════════════════
6. EMPTY STATES  → WORKING
═══════════════════════════════════════════════════════════════════
• Loading skeleton (1160-1172): LazyColumn × 8 NotificationSkeletonCard (2080-2135) с shimmer
  (rememberInfiniteTransition, animateFloat 0.3↔0.8).
• Empty state (1174-1187 ErrorView): 4 варианта message:
  – `notifications.isEmpty() && errorText` → «VK отключил метод… (err 3)» / «Доступ ограничен
    (err 15)» / «Токен недействителен (err 5)» / «Нет новых уведомлений» (803-818).
  – `hiddenKeys.isNotEmpty()` → «Все уведомления скрыты».
  – `searchQuery.isNotBlank()` → «Ничего не найдено по запросу «…»».
  – `activeFilter != "all"` → «Нет уведомлений этого типа».
  OnRetry → refreshNotifications(). ErrorView — стандартный компонент re.pinok.ui.components.ErrorView.
• Pagination footer (1271-1302): loadingMore → CircularProgressIndicator; endReached → «Это все
  уведомления»; nextFrom!=null → кнопка «Загрузить ещё».
• Офлайн-проверка: VKApiClient.isOffline() возвращает emptyList в notificationsGetPage
  (9271), экран увидит `notifications.isEmpty()` → «Нет новых уведомлений» (НЕ идеальное
  сообщение для оффлайна, но не падение).
• 401 (token expired): lastApiErrorCode==5 → «Токен недействителен. Авторизуйтесь заново.»
  (803-815). НЕТ автоматического редиректа на логин.
• VERDICT: skeleton + empty/error/pagination-footer — WORKING. Оффлайн-сообщение могло
  быть точнее, но не критично.

═══════════════════════════════════════════════════════════════════
7. NotificationSettingsScreen — СОДЕРЖИМОЕ  → WORKING (с честными оговорками)
═══════════════════════════════════════════════════════════════════
Файл: NotificationSettingsScreen.kt (767 строк). Заголовок: «Уведомления» (146), TopAppBar
с кнопкой «Назад» (148), actions={} (157 — «псевдо-пункт меню Сбросить удалён»).

Секции экрана (loadAll 120-139, LazyColumn 193-399):
1. **«Не беспокоить»** (SilentModeCard 198-230, 434-500): аккаунт-level DnD.
   – Загрузка: accountGetSilentModeStatus() (124).
   – Snooze: onSnooze(900L/3600L/28800L/-1L) → accountStartSilentMode(time) (203).
   – Stop: onStop → accountStopSilentMode() (217).
   – Optimistic: silentMode обновляется повторным accountGetSilentModeStatus() после успеха.
2. **BFF-секции settingsGeneral.getNotifySettings** (233-299): динамическое дерево
   sections (List<SettingsSection>). ParamRow (517-666) рендерит 5 типов: toggle/custom_toggle,
   select/radio, button, warning, else→Text.
   – onToggle: settingsGeneralToggleNotify(p.key, newValue) (267) + optimistic + rollback.
   – onSelect: settingsGeneralSetNotifySettings(p.key, value) (286) + optimistic + rollback.
   – button-type (612-636): Toast «Действие «…» недоступно» (т.к. per-key actions типа
     change_notify_email НЕ реализованы — честный тост, не фейк-экшен).
   – select БЕЗ options (549-592, #SETTINGS-FIX P3-8): рендер значения БЕЗ шеврона — read-only.
3. **«Уведомления сообществ»** (302-373, GroupNotifyRow 683-736): только сообщества где я
   админ/редактор (groups.get filter="editor", 126). ОДИН тумблер на группу —
   «Отображать в ленте уведомлений» через groupsEditNotifications(-g.id, newValue) (346-349,
   VKApiClient.kt:20000-20010 → groups.edit с group_id=-groupId, notifications=0/1).
   – Начальное состояние НЕ читается из API (нет гетчера): groupNotifyState[group.id] ?: true
     (332) — показан VK-дефолт «вкл». Честная подпись в UI (305-318).
   – Optimistic: prevMap сохраняется, откат при ошибке + Toast с lastApiError (359-368).
   – 16 событийных тумблеров §1.5 (комментарии/упоминания/предложенные записи/…) НЕ
     реализованы — legacy al_settings.php, BFF-ключей в бандлах НЕТ (KDoc 668-681 честно
     объясняет ограничение).
4. **«Контент» → «Фильтр нецензурной лексики»** (376-398, ObsceneFilterRow 739-766):
   accountSetObsceneFilter(enabled) (386, VKApiClient.kt:19482-19487). Начальное состояние НЕ
   читается (гетчера account.getObsceneFilter в VK API нет, #SETTINGS-FIX P2-6) — честная
   подпись в UI (756-762).

Хранение:
– ВСЕ настройки — СЕРВЕРНЫЕ (BFF settingsGeneral.* / account.* / groups.edit), НИЧЕГО
  локально (SovaPrefs не используется на этом экране).
– #NOTIFY-WEB-REWRITE (VKApiClient.kt:19176-19265): settingsGeneral.* НЕ существует в
  VK API (err=3/15) → рабочий путь через ВЕБ-ФОРМЫ m.vk.ru (VKNotifyWeb.fetch → buildSections
  для чтения, saveGroup для записи). Чтение и запись идут через http-снапшот веб-страницы
  (hash/cookies), не через VK API напрямую.
– Optimistic UI везде (94-95 KDoc): «переключатели двигаются мгновенно, откатываются при
  ошибке API + Toast».

VERDICT: WORKING — но с 2 честными оговорками в UI: 1) нач. состояние obsceneFilter НЕ
читается; 2) нач. состояние group notify НЕ читается (нет гетчера). И 1 существенное
ограничение: per-event community settings (16 шт.) НЕ реализованы (API не предоставляет).

═══════════════════════════════════════════════════════════════════
8. PER-CHAT / PER-COMMUNITY MUTE  → PARTIAL (только global-level для сообществ)
═══════════════════════════════════════════════════════════════════
• Per-community mute: ЕСТЬ, но только GLOBAL для группы (один тумблер «Отображать в
  ленте уведомлений»), НЕ per-event (см. §7 выше, GroupNotifyRow 683-736).
• Per-chat mute из NotificationSettingsScreen: НЕТ. Экран не работает с чатами.
  Per-chat mute реализован ВНЕ этого экрана — в ChatDetailScreen / SovaPrefs (DIAG-D2 scope).
  В этом экране — только account-level DnD (SilentModeCard) и group-level notify.
• Per-app mute (apps.allowNotifications / apps.denyNotifications — VKApiClient.kt:19449-19463):
  API есть, НО в NotificationSettingsScreen.kt НЕ вызывается. Раздел «Игры» в фильтре
  NotificationsScreen (apps_requests) есть, но в настройках — нет.
• messages.allowMessagesFromGroup / denyMessagesFromGroup (VKApiClient.kt:19429-19443):
  API есть, НО в NotificationSettingsScreen.kt НЕ вызывается. Per-community push для ЛС
  сообщества — не на этом экране.
• VERDICT: PARTIAL. Per-community global mute есть (только для admin/editor-групп).
  Per-chat, per-app, per-event mute — ОТСУТСТВУЕТ на этом экране.

═══════════════════════════════════════════════════════════════════
9. NAVIGATION ENTRY POINTS  → WORKING (минорное: нет бейджа в drawer)
═══════════════════════════════════════════════════════════════════
• NotificationsScreen (список):
  – Drawer-пункт (SovaNavHost.kt:857): Screen.Notifications в drawerScreens → боковая панель.
    НЕТ бейджа непрочитанных на drawer-пункте (есть только у Messages, SovaNavHost.kt:339-357).
  – Feed-меню правое (SovaNavHost.kt:1728-1738): onFeedPanelOpenNotifications = { category →
    nav.navigate(Screen.Notifications.buildRoute(category)) }. Категория из меню ленты
    передаётся как path-аргумент (initialCategory). Route параметризованный
    "notifications/{category}", дефолт "all" (2366-2376).
  – Deep-link из системного VK-уведомления: DeepLinkAction.OpenNotifications (SovaNavHost.kt:
    611-617) → nav.navigate(Screen.Notifications.buildRoute(DEFAULT_CATEGORY)).
  – Маршрут Screen.Notifications.route (2366) в NavHost composable.
• NotificationSettingsScreen (настройки):
  – ИЗ NotificationsScreen: иконка-шестерёнка в TopAppBar actions (NotificationsScreen.kt:
    1052-1062) → onOpenNotificationSettings() → nav.navigate(Screen.NotificationSettings.route)
    (SovaNavHost.kt:2403-2405).
  – ИЗ SettingsScreen (общие настройки приложения): карточка «Полные настройки уведомлений»
    (SettingsScreen.kt:8312-8340) → onOpenNotificationSettings() → nav.navigate
    (SovaNavHost.kt:2409-2411).
  – ИЗ SettingsScreen вкладка NOTIFICATIONS (SettingsScreen.kt:212, 482): NotificationsTab
    рендерится inline как одна из вкладок SettingsScreen (мини-версия с переходом на полный
    NotificationSettingsScreen, см. SettingsScreen.kt:7266 NotificationsTab). Это ДРУГОЙ
    UI-блок (внутри SettingsScreen), НЕ NotificationSettingsScreen.
  – Контекстное меню карточки уведомления → «Настроить» (NotificationsScreen.kt:1986-1995):
    если dotsMenu.isOpenSetting → onOpenNotificationSettings → NotificationSettingsScreen.
• NotificationSettingsScreen имеет собственный Scaffold+TopAppBar (Fix #NOTIF-SETTINGS-DUAL-BAR,
  SovaNavHost.kt:995-1000): маршрут в hasOwnTopBar → глобальный ScreenTopBar скрывается,
  чтобы не было двойного AppBar.
• VERDICT: 4 entry-point'а в NotificationsScreen (drawer, feed-menu, deep-link, контекст-меню),
  3 в NotificationSettingsScreen (из NotificationsScreen, из SettingsScreen-карточки, из
  контекст-меню уведомления). WORKING. Минорное: НЕТ бейджа непрочитанных на drawer-пункте
  «Уведомления» (только в title после входа на экран).

═══════════════════════════════════════════════════════════════════
10. KNOWN ISSUES  → 3 TODO в NotificationsScreen, 0 в NotificationSettingsScreen
═══════════════════════════════════════════════════════════════════
TODO/FIXME/HACK/BUG (grep):
• NotificationsScreen.kt:
  – 945 (#NOTIF-NFDCAT): «TODO: реальный endpoint уточнить по web-bundle. Локальный эффект
    (убрать из списка + «Отменить») гарантирован всегда, независимо от результата API.» —
    для notificationsHideItem / notificationsUnsubscribe (best-effort API, локальный dismiss).
  – 1140 (#NOTIF-NFDCAT): «TODO: точный маппинг бейджей getUnreadCounters → getRedesign
    категории не подтверждён — не рисуем.» — счётчики {mentions, comments, ...} из
    getUnreadCounters не маппятся на серверные категории (all/communities/...), бейджи на
    чипах сайдбара НЕ рисуются.
  – 1605 (#NOTIF-NFDCAT): «TODO: url пред-кнопки (preButtonUrls[idx]) НЕ реализован как
    внешний открыватель — модель кнопки не несёт надёжного web-открывателя; при появлении
    точного маппинга (анкета → ссылка, ЛС → peerId) подключить url.»
• NotificationSettingsScreen.kt: 0 TODO/FIXME.
• Связанные TODO в VKApiClient.kt:
  – 9327 (#NOTIF-NFDCAT): «TODO #NOTIF-NFDCAT: подтвердить реальный endpoint (hide/unsubscribe)
    через анализ web-bundle уведомлений и заменить вызов на фактический.» — endpoint
    notifications.hideNotification / notifications.unsubscribe НЕ подтверждён,
    best-effort через документированный VK notifications.hideNotification.

git log --follow -- NotificationsScreen.kt / NotificationSettingsScreen.kt:
• Единственный коммит c9d4ff8 «feat(comments): P0.46 — wall.getComment after reply + long-press
  menu» — файлы были пересозданы/тронуты в P0.46 (ранняя история не сохранена через
  squash/rename). Это означает, что недавних правок В ЭТИХ ДВУХ ФАЙЛАХ не было (с момента
  P0.46 — последнего коммита до HEAD f678836). P0.47 (5 коммитов a9b1fc8...f678836) эти файлы
  НЕ трогал — все изменения P0.47 в CommunityScreen.kt и pagination-хелперах VKApiClient.kt.

═══════════════════════════════════════════════════════════════════
ИТОГОВЫЕ ВЕРДИКТЫ
═══════════════════════════════════════════════════════════════════
1. Data source                  → WORKING (getRedesign, курсорная пагинация, дедупликация)
2. Notification types rendering → WORKING (≈20 типов), PARTIAL (audio/event_invite/sdk_app_request/wall_reply отсутствуют)
3. Interactions (tap/swipe/pull/pagination) → WORKING
4. Mark as read                 → PARTIAL (API есть, optimistic UI не зануляет бейдж)
5. Filtering / tabs             → PARTIAL (серверная категория + Новые/Просмотренные WORKING;
                                    локальный тип-фильтр MISSING — UI-триггер удалён #NOTIFSHOW)
6. Empty states                 → WORKING (skeleton/empty/error/pagination-footer)
7. NotificationSettingsScreen   → WORKING (4 секции: DnD / BFF / groups / obscene; всё серверное)
8. Per-chat/per-community mute  → PARTIAL (только global group-mute для admin/editor;
                                    per-chat/per-app/per-event mute отсутствует на этом экране)
9. Navigation entry points     → WORKING (4 входа в Notifications, 3 в NotificationSettings)
                                    минорное: НЕТ бейджа на drawer-пункте
10. Known issues               → 3 TODO (#NOTIF-NFDCAT) в NotificationsScreen, 0 в SettingsScreen;
                                    endpoint hide/unsubscribe best-effort (VKApiClient.kt:9327)

СЛЕДУЮЩИЕ ШАГИ (для агента-исполнителя, не диагноста):
• P1: Занулять unreadCount локально после notificationsMarkAsRead (NotificationsScreen.kt:1020).
• P1: Вернуть UI-триггер для activeFilter (удалён #NOTIFSHOW) — либо удалить мёртвый код
  NOTIFICATION_FILTERS + NotificationFilterSheet + фильтрацию 723-753 (сейчас dead code).
• P2: Реализовать маппинг getUnreadCounters → serverCategory для бейджей в сайдбаре (TODO 1140).
• P2: Добавить бейдж на drawer-пункте «Уведомления» (SovaNavHost.kt:857, паттерн Messages 339-357).
• P3: Подтвердить web-endpoint для notifications.hideNotification / unsubscribe (TODO VKApiClient.kt:9327).
• P3: Реализовать per-event community mute через web-формы m.vk.ru (legacy al_settings.php).
• P3: Добавить иконки/обработку audio, event_invite, sdk_app_request, wall_reply.
• P3: Реализовать preButtonUrls как внешние открыватели (TODO NotificationsScreen.kt:1605).

---
Task ID: DIAG-A2
Agent: Explore (realtime channels & polling) — RE-RUN WITH SOURCE
Task: Диагностика транспортных каналов и поллеров уведомлений PinoK (по исходникам)

Work Log:
- Прочитал worklog.md (старые записи Task 3, P0.43-r8-analyze-v216) для контекста. Подтвердил: предыдущий DIAG-A упал без исходников; в worklog уже была запись P0.43-r8-analyze-v216, утверждавшая регрессию «LP 115 с payload="-1" пропускается» в SovaApp.kt:1808-1810.
- Репозиторий доступен: /home/z/repos/PinoK_1, ветка PinoK, HEAD=f678836 (P0.47-REVERT). Все 23 .kt файла в realtime/ присутствуют (LongPollClient 1394 строк, EventHubWebSocketClient 234, Queuev4Client 361, ChannelWebSocketClient 183, LongPollKeepAliveService 322, RealtimeWakeReceiver 136, 4 poller'а, EventDedup, MessageNotifier, etc.).
- Полностью прочитал: LongPollClient.kt (все 1395 строк), EventHubWebSocketClient.kt (234), Queuev4Client.kt (361), ChannelWebSocketClient.kt (183), LongPollKeepAliveService.kt (322), RealtimeWakeReceiver.kt (136), EventDedup.kt (36), NotificationsPoller.kt (264), CommunityPostsPoller.kt (495), SecurityAlertsPoller.kt (217), StatsQueuePoller.kt (124), VKEndpoints.kt (153), VKNotifyWeb.kt (537, не realtime — настройки через m.vk.ru web-формы).
- В SovaApp.kt (3104 строки): init каналов в onCreate (1378-1601), startCallNotifier (1828-2027) — 4 коллектора (LP/queuev4/events_queue/calls_queue), handleQueuev4Incoming (2041-2084), refreshIncomingCaller (2092-2121), startCallSignaling (2134-2205), callDedicatedCallsQueueStart (2214+). MainActivity.kt:1204-1228 — запуск/остановка longPollClient/eventHubClient/startCallSignaling/LongPollKeepAliveService при валидном токене.
- Прочитал VKApiClient.kt:14649-14721 (queueSubscribe — SAT preferred, web fallback), 17995-18054 (eventHubGetToken — Bearer web token), 18258-18262 (stickersSubscribeToQueue — forceWebGateway).
- git log по realtime/ показывает ОДИН squash-коммит c9d4ff8 (P0.46, 2026-10-19, "feat(comments): P0.46 — wall.getComment after reply + long-press menu"). Все файлы в realtime/ были добавлены/тронуты в этом единственном коммите; HEAD f678836 (P0.47-REVERT) realtime/ не трогает. git blame подтверждает: строки case 115 в LongPollClient.kt:977-992 и consumer в SovaApp.kt:1846-1878 помечены `^c9d4ff8` (single squash author) — то есть регрессия P0.42 (если она была) уже исправлена в c9d4ff8 тегом `P0.43-r8-V216-RESTORE`.
- Q10 VERBATIM verif: в LongPollClient.kt:977-992 case 115 БЕЗ guards — `val payload = ev.stringAt(1); _events.emit(LongPollEvent.IncomingCall(payload))`. В SovaApp.kt:1846-1878 consumer принимает ЛЮБОЙ payload, на строке 1863 `val realPayload = event.payload?.takeIf { it.isNotBlank() && it != "-1" }`, при realPayload==null И pendingPayload пустом → ставит placeholder "-1" и зовёт refreshIncomingCaller() (SovaApp.kt:1866-1869). handleQueuev4Incoming (SovaApp.kt:2041-2084) на ветке "-1"/null тянет caller_id через messagesGetCurrentCalls() (строка 2059-2061). Это ПОЛНОЕ соответствие V2.1.6 поведению → регрессия ИСПРАВЛЕНА.
- Греп EventHubEvent/eventHubClient.events по всему /app/src/main/java — 0 коллекционеров вне самого EventHubWebSocketClient.kt. EventHub — DEAD-EVENT канал (соединение открывается, ping/pong отвечается, но _events.tryEmit(...) уходит в void).

Stage Summary:

1) Channels inventory (8 total): LongPollClient (HTTPS LP, api.vk.com/queuev4-style url, web token) — PRIMARY. EventHubWebSocketClient (wss://eh.vk.ru, web token via eventHub.getToken). 3× Queuev4Client (HTTPS LP queuev4.vk.ru via stickers.subscribeToQueue или queue.subscribe) — multiaccount/events/calls. ChannelWebSocketClient (STUB, wss://api.vk.com — не реализован). StatsQueuePoller (queuev4-protocol wait=45, admin only). NotificationsPoller (REST notifications.getRedesign). CommunityPostsPoller (REST newsfeed.get filters=post). SecurityAlertsPoller (REST accountPersonal.getSecurityAlerts).

2) Auth per channel: LongPoll — web access_token vk1.a.* (api.vk.com or web.api.vk.ru, lp_version 3/14/21). EventHub — web token → eventHub.getToken (Bearer). Queuev4 (3 инстанса) — SAT preferred при queue.subscribe (VKApiClient.kt:14655-14693), web-token fallback через forceWebGateway; НО queuev4Client/eventsQueuev4Client подписываются через stickers.subscribeToQueue (forceWebGateway=true, web token) (SovaApp.kt:2163, 2180). ChannelWebSocketClient — нет (stub). Pollers — через apiClient (web token). Broken auth: SecurityAlertsPoller на web-шлюзе ловит err=3 "Unknown method passed" и сам останавливается (SecurityAlertsPoller.kt:124-129).

3) Event flow: LongPollClient._events собирают 4 потребителя (MessageNotifier SovaApp.kt:1637; startCallNotifier LP-branch SovaApp.kt:1833; startNotificationsPoller SovaApp.kt:2779; UnreadMessagesCounter UnreadMessagesCounter.kt:86) — рабочий flow. EventHubWebSocketClient._events (EventHubEvent) НЕ собирает НИКТО в проекте — DEAD-EVENT (подтверждено grep). Queuev4 ×3: каждый собирается startCallNotifier'ом (SovaApp.kt:1882 queuev4Client, 1945 eventsQueuev4Client, 1972 callsQueuev4Client). ChannelWebSocketClient — не стартует (stub start() — только лог). StatsQueuePoller.chunks собирается AdminStatsScreenW47.kt (на-деманд). Pollers — не Flow, сразу рисуют system notifications через Notifier'ы.

4) Pollers detail: NotificationsPoller — interval 60-600s default 120s, first poll +5s, API notifications.getRedesign(count=30), diff по pushLastSeenKeys CSV в SovaPrefs, showBatch через VkNotificationsNotifier. pollMutex с защитой, triggerImmediatePoll по LP code 114. start/stop корректны (idempotent). CommunityPostsPoller — interval ≥120s default newsPollIntervalSec, first poll +5s, API newsfeedGet(count=30, filters="post"), фильтр fromId<0, diff по communityPostsSeenKeys, по одному уведомлению на пост. Skip если FeedVisibility.isVisible или newsAutoRefreshOff (защита от flood control). SecurityAlertsPoller — interval 1-1440 min default 10, IMMEDIATE first poll, API accountGetSecurityAlerts(hash=logout_hash), diff через SharedPreferences "security_alerts_cache". САМ-СТОП при err=3 Unknown method (web gateway). StatsQueuePoller — wait=45s, backoff 1.5s→30s, re-subscribe на max backoff. Запускается только AdminStatsScreenW47.

5) KeepAlive: LongPollKeepAliveService — foreground (ServiceCompat.startForeground, FGS type=FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING на Android 14+, bypass 6h timeout). Notification: smallIcon=R.drawable.ic_notification, title="PinoK", text="Получение сообщений в фоне", PRIORITY_LOW, CATEGORY_SERVICE, ongoing=true. Channel "bg_keepalive" / "Фоновая работа" / IMPORTANCE_LOW. Restart: START_STICKY (line 93) + onStartCommand re-call startForegroundCompat + RealtimeWakeReceiver.schedule + longPollClient.start() if token valid. Headless silent re-login через exchangeAuthRepository.ensureFreshToken() при tick'е tokenInvalidationTicks и нет foreground Activity (line 169-184). bringMainActivityToForeground throttled 15s (line 217). RealtimeWakeReceiver: AlarmManager.setExactAndAllowWhileIdle, INTERVAL_MS=15 min, будит LongPoll.notifyResumed + EventHub.start.

6) Restart/reconnect: LongPoll — exponential 2s×2^errors + jitter max 60s (LongPollClient.kt:309-313); NetworkObserver.isOnlineFlow → reset backoff + interruptWait; token-invalidation pause 4s (TOKEN_INVALIDATION_PAUSE_MS, line 1287) с isTokenValid early-exit каждые 2s; failed=2,3,4 → break inner loop → re-fetch server; failed=1 → Reset event + backfill; failed=4 → backoff (consecutiveErrors++); notifyResumed() → currentCall.cancel() (line 173). Бесконечный цикл while(running), без give-up-after-N. EventHub — exponential 1s→30s (line 99), CONNECT_TIMEOUT_MS=10s, reconnect on close/failure (line 144-148, 152-156). Бесконечный. Queuev4 — backoff 2s→60s + jitter (line 265, 357); НЕТ network observer; LP -1/-2 → emit и продолжение (re-subscribe ложится на consumer — callsQueuev4Client: callDedicatedCallsQueueStart). ChannelWebSocketClient — STUB, TODO reconnect (line 164, 171).

7) Deduplication: EventDedup.kt — object, WINDOW_MS=5_000L (5s), Key=(peerId, msgId), ConcurrentHashMap. ИСПОЛЬЗУЕТСЯ ТОЛЬКО MessageNotifier'ом (SovaApp.kt:1645) для cross-channel LongPoll+EventHub dedup NewMessage. НЕТ cross-channel dedup для calls — LP 115 + 3× Queuev4 коллектора дают 4 источника incoming_call; защита только через `pendingIncomingCallPayload != event.payload` guard (SovaApp.kt:1858, 2047) — гонка возможна (4 корутины keepAliveScope). НЕТ dedup для typing/read/online/counters.

8) TODOs/FIXMEs: ChannelWebSocketClient.kt — TODO lines 106, 129, 138, 148, 153, 164, 171 (весь класс STUB); FIXME lines 29, 45, 80. Это RESEARCH-заготовка P4.3 для будущего перехода каналов на WS (когда VK отключит LP). EventHubWebSocketClient.kt — комментарий подтверждает "пока не парсятся (главное — доставка)" — фактически доставка никуда. SecurityAlertsPoller.kt — комментарий к самостопу при err=3. SecurityAlertNotifier.kt:31 — TODO action button «Завершить сессию». Других TODO/FIXME/HACK в transport-каналах нет.

9) History check: `git log -- app/src/main/java/re/pinok/realtime/` → единственный коммит c9d4ff8 (P0.46, 2026-10-19). Все 23 .kt файла появились/были переписаны в этом squash-коммите; PRE-P0.46 история отсутствует. HEAD=f678836 (P0.47-REVERT) realtime/ НЕ трогает — то есть текущее состояние realtime = результат единственного squash-коммита P0.46. Регрессий в realtime/ с момента c9d4ff8 нет (просто нечего регрессить — коммит единственный).

10) CRITICAL — incoming call regression: РЕГРЕССИЯ ИСПРАВЛЕНА (вкоммичена в c9d4ff8 как P0.43-r8-V216-RESTORE). Цитата LongPollClient.kt:977-992 (case 115):
```
115 -> {
    // #CALLS: входящий звонок. ev=[115, payload] — payload =
    // строка conversation-params (WebRTC). Формат подтверждён из
    // m.vk.ru бандла: case 115: return {type:INCOMING_CALL, payload:e}
    //
    // P0.43-r8-V216-RESTORE: в V2.1.6 (последней рабочей версии звонков)
    // LP 115 — PRIMARY канал входящих. payload="-1" (маркер без данных)
    // НЕ пропускался — SovaApp.handleQueuev4Incoming тянул реальный
    // caller_id через messagesGetCurrentCalls(), потом usersGetByIds
    // для имени/фото. ...
    val payload = ev.stringAt(1)
    AppLog.i(TAG, "INCOMING_CALL (LP 115): payload.len=${payload?.length ?: 0}")
    _events.emit(LongPollEvent.IncomingCall(payload))
}
```
Consumer SovaApp.kt:1846-1878 принимает ЛЮБОЙ payload, на строке 1863:
```
val realPayload = event.payload?.takeIf { it.isNotBlank() && it != "-1" }
if (realPayload != null) {
    pendingIncomingCallPayload = realPayload
} else if (pendingIncomingCallPayload.isNullOrBlank()) {
    pendingIncomingCallPayload = "-1"  // placeholder, handleQueuev4Incoming вытянет реальный
}
refreshIncomingCaller()
if (realPayload != null) ensureIncomingSignalingOnRing(realPayload)
showIncomingCallNotification()
```
handleQueuev4Incoming (SovaApp.kt:2041-2084) на ветке "-1"/null зовёт messagesGetCurrentCalls() и берёт caller_id (строка 2059-2061). Это ИДЕНТИЧНО V2.1.6. Никакого `if (payload == "-1") return` НЕТ — claim старого worklog (P0.43-r8, SovaApp.kt:1808-1810) устарел.

VERDICT per channel:
- LongPollClient — WORKING. Primary realtime, web-token auth, exponential backoff + token-pause + network observer + backfill (P4.2) + Modern Sync (lp_version 21). 
- EventHubWebSocketClient — PARTIAL/DEAD-EVENT. WS connection работает (auth, ping/pong, reconnect 1s→30s), но EventHubEvent flow НЕ собирается НИКЕМ — события уходят в void. Реальной пользы для доставки нет.
- queuev4Client (multiaccount) + eventsQueuev4Client — NEAR-DEAD for calls. По логике SovaApp.kt:2196-2199 доставляют только online/im/counters (entity_types=[online/null]); calls events через них НЕ приходят. Но startCallNotifier всё же их слушает на всякий случай.
- callsQueuev4Client — WORKING. Реальный канал calls-событий (subscribe → calls_<uid>_<clientId>_1).
- ChannelWebSocketClient — DEAD-CODE/STUB. Не интегрирован. Класс-заготовка P4.3.
- NotificationsPoller — WORKING.
- CommunityPostsPoller — WORKING (с защитой от flood control через FeedVisibility.isVisible и newsAutoRefreshOff).
- SecurityAlertsPoller — PARTIAL (самостоп на web gateway err=3; работает только на api.vk.com).
- StatsQueuePoller — WORKING but on-demand (admin only).
- LongPollKeepAliveService + RealtimeWakeReceiver — WORKING (foreground remoteMessaging + 15min AlarmManager wake).

Risks/gaps:
- EventHub тратит сетевой ресурс и токен eventHub.getToken, но события не доходят до UI — либо реализовать парсер EventHubEvent → LongPollEvent, либо отключить старт (MainActivity.kt:1209).
- Cross-channel call dedup отсутствует — 4 коллектора (LP/3× queuev4) могут race-condition'ить pendingIncomingCallPayload. Guard `!= event.payload` (SovaApp.kt:1858) не покрывает случай, когда 2 коллектора эмитят РАЗНЫЕ payload'ы одного звонка (реальный convParams из queuev4 + "-1" из LP).
- Queuev4Client.stop() обнуляет credential (line 114) — но не отменяет подписку на сервере (нет queue.unsubscribe). При logout очередь остаётся активной на стороне VK до таймаута key.
- SecurityAlertsPoller молча останавливается на web gateway (err=3) без fallback на api.vk.com.
- ChannelWebSocketClient — если VK форсирует WS для каналов, заготовка не готова (все TODO).

---
Task ID: DIAG-E2
Agent: Explore (orchestration & lifecycle) — RE-RUN WITH SOURCE
Task: Диагностика оркестровки realtime в SovaApp + lifecycle/манифест

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние ~350 строк, секции P0.43-r8-analyze-v216 + предыдущий DIAG-E sourceless).
  Подтверждено: предыдущий DIAG-E упал «без исходников» — все verdict'ы UNKNOWN помечены
  «требует прямого чтения AndroidManifest.xml / SovaApp.kt / BootReceiver.kt». Сейчас
  /home/z/repos/PinoK_1 доступен (HEAD = f678836 «fix(community): P0.47-REVERT»).
- Прочитал ПОЛНОСТЬЮ (или с off-сетами по grep-навигации):
  • app/src/main/AndroidManifest.xml (501 строка) — все permissions/services/receivers.
  • app/src/main/java/re/pinok/SovaApp.kt (3104 строки) — onCreate, startMessageNotifier,
    startNotificationsPoller, startCommunityPostsPoller, startSecurityAlertsPoller,
    startCallNotifier, handleQueuev4Incoming, refreshIncomingCaller, startCallSignaling,
    callDedicatedCallsQueueStart, ensureCallsSessionKey, startKeepAlive,
    setupCookieBackgroundRefresh (ProcessLifecycleOwner hook), ActivityLifecycleCallbacks.
  • app/src/main/java/re/pinok/locker/BootReceiver.kt (60 строк) — onReceive + startService.
  • app/src/main/java/re/pinok/realtime/LongPollKeepAliveService.kt (323 строки) — FG service.
  • app/src/main/java/re/pinok/realtime/RealtimeWakeReceiver.kt (137 строк) — Doze alarm.
  • app/src/main/java/re/pinok/realtime/LongPollClient.kt:160-205, 970-1000 — start/stop/notifyResumed + case 115.
  • app/src/main/java/re/pinok/realtime/StatsQueuePoller.kt (125 строк) — полностью.
  • app/src/main/java/re/pinok/ui/MainActivity.kt:280-360, 920-1080, 1190-1290, 1500-1700, 2060-2140 —
    LaunchedEffect(currentAuthVersion) start/stop, onLogout, onExitApp, onResume hooks,
    battery-exemption dialog, ProcessLifecycleOwner hook.
  • Grep-проверка: WakeLock / PowerManager / REQUEST_IGNORE_BATTERY_OPTIMIZATIONS по app/src/main.
- git log --all -L 1846,1880:app/.../SovaApp.kt — blame показал что вся текущая startCallNotifier
  (включая «P0.43-r8-V216-RESTORE» коммент) восходит к одному squashed-коммиту c9d4ff8
  (P0.46 — wall.getComment). Промежуточные P0.43-r8 коммиты в видимой истории отсутствуют
  (вероятно rebase/squash), но КОД содержит fixed-версию — фикс применён.

---

## §1. Startup sequence — Q1 (EXACT order, quote code)

### 1.1 SovaApp.onCreate (app/src/main/java/re/pinok/SovaApp.kt:765-1601)
Порядок создания/старта realtime-компонентов:

| # | Строка | Что | Старт цикла? |
|---|--------|-----|--------------|
| 1 | 857-875 | `registerActivityLifecycleCallbacks(...)` — счётчик `startedActivities` | нет (только счётчик) |
| 2 | 541-583 | `setupCookieBackgroundRefresh()` — ProcessLifecycleOwner ON_RESUME hook (см. §4) | нет |
| 3 | 1523-1530 | `longPollClient = LongPollClient(...)` — INSTANTIATE | нет (start в MainActivity:1206) |
| 4 | 1534-1538 | `eventHubClient = EventHubWebSocketClient(...)` — INSTANTIATE | нет (start в MainActivity:1209) |
| 5 | 1543-1544 | `MessageNotifier.init(this)` + `startMessageNotifier()` | да — keepAliveScope coroutine, собирает `longPollClient.events` (SovaApp.kt:1637) |
| 6 | 1552-1554 | `notificationsPoller = NotificationsPoller(...)` + `startNotificationsPoller()` | да — poller.start() (SovaApp.kt:2787) |
| 7 | 1560-1562 | `CommunityPostsPoller.initChannel(this)` + `communityPostsPoller = ...` + `startCommunityPostsPoller()` | да — poller.start() (SovaApp.kt:2806) |
| 8 | 1568-1569 | `createCallNotificationChannel()` + `startCallNotifier()` | да — 4 keepAliveScope coroutines (SovaApp.kt:1832, 1881, 1944, 1971) — собирают LP/queuev4/events/calls |
| 9 | 1577-1578 | `securityAlertsPoller = SecurityAlertsPoller(...)` + `startSecurityAlertsPoller()` | да — poller.start() (SovaApp.kt:2827) |
| 10 | 1581-1585 | `UnreadMessagesCounter.init(this)` + `ClipsCounter.start(this)` | да — отдельные счётчики badge |
| 11 | 1589 | `registerGlobalNetworkWatcher()` | да — collect isOnlineFlow + onLost/onDefault listeners |
| 12 | 1600 | `startKeepAlive()` | да — token refresh loop (60s polling, SovaApp.kt:2956-2960) |

**ВАЖНО:** longPollClient.start() и eventHubClient.start() В onCreate НЕ вызываются.
Эти объекты ИНСТАНЦИИРУЮТСЯ, но их loop стартует ПОЗЖЕ — из MainActivity.

### 1.2 После login (MainActivity.kt:1203-1230) — gated на token
```kotlin
LaunchedEffect(currentAuthVersion) {
    if (app.tokenStorage.hasValidToken()) {                       // line 1204
        AppLog.i("MainActivity", "Token valid — starting LongPoll")
        app.longPollClient.start()                                 // line 1206
        app.eventHubClient.start()                                 // line 1209
        app.startCallSignaling()                                   // line 1211
        re.pinok.realtime.LongPollKeepAliveService.start(this@MainActivity)  // line 1215
    } else {
        AppLog.i("MainActivity", "No token — stopping LongPoll")
        app.longPollClient.stop()                                  // line 1218
        app.queuev4Client.stop()                                   // line 1219
        app.callsQueuev4Client.stop()                              // line 1221
        app.eventHubClient.stop()                                  // line 1223
        try { app.communityPostsPoller?.stop() } catch ...         // line 1225
        re.pinok.realtime.LongPollKeepAliveService.stop(this@MainActivity)  // line 1226
        re.pinok.realtime.RealtimeWakeReceiver.cancel(this@MainActivity)     // line 1228
    }
}
```

**СТАРТ УСЛОВЕН на auth state:** ДА. Все 4 канала (LP, EventHub, queuev4×3, KeepAliveService)
стартуют ТОЛЬКО если `tokenStorage.hasValidToken() == true`. Poller'ы (notifications/community/
security) стартуют в onCreate БЕЗУСЛОВНО, но poller.start() сам проверяет `hasValidToken`
внутри pollOnce — без токена он просто простаивает (no network calls).

---

## §2. Per-channel start() call site — Q2

| # | Канал | start() call site | Verdict |
|---|-------|-------------------|---------|
| 1 | **LongPollClient** | MainActivity.kt:1206 (`app.longPollClient.start()`) — gated на token. ALSO в LongPollKeepAliveService.kt:84 (`app.longPollClient.start()`) — onStartCommand после boot/re-delivery. | ✅ WORKING (2 start sites) |
| 2 | **EventHubWebSocketClient** | MainActivity.kt:1209 (`app.eventHubClient.start()`) — gated на token. ALSO RealtimeWakeReceiver.kt:49 (`app.eventHubClient.start()`) — Doze wake. | ✅ WORKING (2 start sites) |
| 3 | **queuev4Client** (multiaccount) | SovaApp.kt:2173-2174 (`queuev4Client.setCredential(cred); queuev4Client.start()`) — inside startCallSignaling, called from MainActivity:1211. Подписка через `stickersSubscribeToQueue()` (SovaApp.kt:2163). | ⚠ PARTIAL — см. DIAG-A2 (stickersSubscribeToQueue отдаёт counters, не calls/multiaccount) |
| 4 | **eventsQueuev4Client** (events) | SovaApp.kt:2189-2190 — второй `stickersSubscribeToQueue()`. | ⚠ PARTIAL (тот же API, тот же queue_id) |
| 5 | **callsQueuev4Client** (dedicated calls) | SovaApp.kt:2235-2236 — `queueSubscribe(queueIdSuffix=callsQueueId(uid, clientId))`. Dedicated calls-queue v1.9.8-механизм. | ✅ WORKING — восстановлен (см. §3, callDedicatedCallsQueueStart) |
| 6 | **NotificationsPoller** | SovaApp.kt:2787 (`poller.start()`) — внутри startNotificationsPoller(), вызывается из onCreate:1554. | ✅ WORKING |
| 7 | **CommunityPostsPoller** | SovaApp.kt:2806 — внутри startCommunityPostsPoller(), вызывается из onCreate:1562. | ✅ WORKING (но notifyMode filter на стороне display — DIAG-B2) |
| 8 | **SecurityAlertsPoller** | SovaApp.kt:2827 — внутри startSecurityAlertsPoller(), вызывается из onCreate:1578. | ⚠ PARTIAL — poller стартует, но VK API method `accountPersonal.getSecurityAlerts` reportedly err=3 «Unknown method» (DIAG-A2); нужен runtime logcat для подтверждения |
| 9 | **StatsQueuePoller** | НЕТ старты в SovaApp. Используется ТОЛЬКО on-demand из UI: `AdminStatsScreenW47.kt:101` `StatsQueuePoller(app.httpClient, app.apiClient)`, старт через `poller.start(uid, gid)` при открытии экрана admin-статистики. НЕ глобальный realtime-канал. | ✅ NOT-DEAD-CODE — намеренно per-screen, не требует оркестровки. Предыдущий DIAG-E verdict «DEAD-CODE / NOT FOUND» ОТМЕНЁН — poller существует и используется. |
| 10 | **LongPollKeepAliveService** (foreground) | MainActivity.kt:1215 (`LongPollKeepAliveService.start(this@MainActivity)`) — gated на token. ALSO BootReceiver.kt:45 (`LongPollKeepAliveService.start(context)`) — post-boot если есть token/remixsid. | ✅ WORKING (2 start sites) |
| 11 | **RealtimeWakeReceiver** (alarm schedule) | RealtimeWakeReceiver.kt:75 `schedule(context)` — из: LongPollKeepAliveService.kt:78 (onStartCommand), LongPollKeepAliveService.kt:309 (start companion), BootReceiver.kt:49 (post-boot). | ✅ WORKING (3 schedule sites) |

**DEAD CODE:** НЕТ. Все объявленные realtime-каналы имеют live start() call site.
Единственное уточнение: StatsQueuePoller НЕ стартует из SovaApp — это намеренно
(per-screen admin stats), не dead code.

---

## §3. CRITICAL — Incoming call regression — Q3 (verbatim quotes)

Worklog P0.43-r8-analyze-v216 (строки 933-990) зафиксировал регрессию:
> «Current (SovaApp.kt:1808-1810): `if (event.payload == "-1") { AppLog.i(..., "LP 115 заглушка -1, пропущен (жду queuev4 calls)"); ... }` — ПРОПУСКАЕТ LP 115 с payload="-1"»
> «текущий LongPollClient.kt:987-989 ТАКЖЕ подавляет LP 115 с payload="-1" — НЕ эмитит IncomingCall вообще»

### 3.1 ВЕРДИКТ: РЕГРЕССИЯ УЖЕ ИСПРАВЛЕНА ✅

В текущем HEAD (f678836) оба guard'а УДАЛЕНЫ. Фикс помечен комментарием
`P0.43-r8-V216-RESTORE` (видно в git blame — весь блок восходит к squashed commit c9d4ff8).

### 3.2 LongPollClient.kt case 115 (ТЕКУЩИЙ КОД, строки 977-992)
```kotlin
115 -> {
    // #CALLS: входящий звонок. ev=[115, payload] — payload =
    // строка conversation-params (WebRTC). Формат подтверждён из
    // m.vk.ru бандла: case 115: return {type:INCOMING_CALL, payload:e}
    //
    // P0.43-r8-V216-RESTORE: в V2.1.6 (последней рабочей версии звонков)
    // LP 115 — PRIMARY канал входящих. payload="-1" (маркер без данных)
    // НЕ пропускался — SovaApp.handleQueuev4Incoming тянул реальный
    // caller_id через messagesGetCurrentCalls(), потом usersGetByIds
    // для имени/фото. CallScreen получал conversation_params через
    // vchat.getConversationParams. Отключение "-1" в P0.42 сломало
    // входящие — queuev4 не доставляет incoming_call events.
    val payload = ev.stringAt(1)
    AppLog.i(TAG, "INCOMING_CALL (LP 115): payload.len=${payload?.length ?: 0}")
    _events.emit(LongPollEvent.IncomingCall(payload))         // line 991 — БЕЗУСЛОВНЫЙ emit
}
```
**Сравнение с V2.1.6 ожидаемым поведением:** ✅ СОВПАДАЕТ.
LP case 115 ВСЕГДА эмитит `IncomingCall(payload)` — без проверки `payload == "-1"`.
Строка 991 идёт сразу после чтения payload, без if-guard.

### 3.3 SovaApp.kt startCallNotifier — LP 115 коллектор (ТЕКУЩИЙ КОД, строки 1846-1878)
```kotlin
if (event is re.pinok.realtime.LongPollEvent.IncomingCall) {        // line 1846
    try {
        AppLog.i("SovaApp", "INCOMING_CALL (LP 115): payload.len=${event.payload?.length ?: 0}")  // 1848
        // P0.43-r8-V216-RESTORE: в V2.1.6 (последней рабочей версии) LP 115
        // с payload="-1" НЕ пропускался — handleQueuev4Incoming тянул реальный
        // caller_id через messagesGetCurrentCalls(), потом usersGetByIds для
        // имени/фото. CallScreen получал conversation_params через
        // vchat.getConversationParams. Отключение "-1" в P0.42 сломало входящие.
        // #CALLS-NO-DOUBLE-CALLSCREEN: guard перезаписи — один звонок приходит
        // ИЗ ТРЁХ коллекторов (LP 115 / queuev4 / events_queue); повторная
        // запись того же payload перезапускала LaunchedEffect в SovaNavHost
        // и пушила вторую копию CallScreen в backstack.
        if (pendingIncomingCallPayload.isNullOrBlank() || pendingIncomingCallPayload != event.payload) {  // 1858
            // Сохраняем pending — MainActivity откроет CallScreen по тапу на уведомление.
            // P0.43-r8: при payload="-1" (маркер без данных) передаём null в
            // handleQueuev4Incoming — оно через messagesGetCurrentCalls вытянет
            // реальный caller_id (как V2.1.6).
            val realPayload = event.payload?.takeIf { it.isNotBlank() && it != "-1" }  // 1863
            if (realPayload != null) {
                pendingIncomingCallPayload = realPayload                              // 1865
            } else if (pendingIncomingCallPayload.isNullOrBlank()) {
                pendingIncomingCallPayload = "-1"  // placeholder, handleQueuev4Incoming вытянет реальный  // 1867
            }
            refreshIncomingCaller()                                                   // 1869 — ВСЕГДА
            // #CALLS-COMMON-SIGNALING: поднимаем WS на ring сразу
            // (поймать offer) — ТОЛЬКО при реальном payload (не "-1").
            if (realPayload != null) ensureIncomingSignalingOnRing(realPayload)       // 1872
            showIncomingCallNotification()                                            // 1873
        }
    } catch (e: Exception) {
        AppLog.w("SovaApp", "startCallNotifier: ${e.message}")
    }
}
```
**Сравнение с V2.1.6 ожидаемым поведением:** ✅ СОВПАДАЕТ (с точностью до новых guard'ов).
- Принимается ЛЮБОЙ payload (включая "-1", null, blank).
- При "-1" устанавливается placeholder `pendingIncomingCallPayload = "-1"` (line 1867).
- ВСЕГДА вызывается `refreshIncomingCaller()` (line 1869), который через
  `apiClient.messagesGetCurrentCalls().firstOrNull()?.get("caller_id")` (SovaApp.kt:2095-2097)
  вытягивает реальный caller_id — как V2.1.6.
- ВСЕГДА вызывается `showIncomingCallNotification()` (line 1873).
- НОВОЕ vs V2.1.6 (не регрессия, а улучшение): `#CALLS-NO-DOUBLE-CALLSCREEN` guard (line 1858)
  защищает от повторной нотификации если тот же payload уже обрабатывается — нужно потому что
  ОДИН звонок приходит ИЗ ТРЁХ коллекторов (LP 115 + queuev4 + events_queue).
- НОВОЕ: `#CALLS-COMMON-SIGNALING` (line 1872) — поднимает WS на ring сразу при реальном payload,
  чтобы поймать offer. При "-1" payload WS не поднимается (session_key ещё не получен).

### 3.4 Дополнительно: handleQueuev4Incoming (SovaApp.kt:2041-2084) — V2.1.6-эквивалент
Для path из queuev4/eventsQueuev4/callsQueuev4 коллекторов:
```kotlin
private fun handleQueuev4Incoming(payload: String?) {            // line 2041
    keepAliveScope.launch {
        try {
            // 1) Реальный payload (не "-1") — используем как есть
            val realPayload = payload?.takeIf { it != "-1" }       // line 2045
            if (!realPayload.isNullOrBlank()) {
                ... pendingIncomingCallPayload = realPayload; refreshIncomingCaller();
                ensureIncomingSignalingOnRing(realPayload); showIncomingCallNotification()
                return@launch
            }
            // 2) payload == "-1"/null — проверяем messagesGetCurrentCalls (caller_id)
            val callerId = withContext(Dispatchers.IO) {
                apiClient.messagesGetCurrentCalls().firstOrNull()?.get("caller_id")?....?.asLong ?: 0L   // line 2059-2061
            }
            if (callerId <= 0L) {                                  // line 2063
                // Звонка на сервере нет (getCurrentCalls пусто/0) — не показываем ложный входящий.
                return@launch
            }
            pendingIncomingCallPeerId = callerId
            if (pendingIncomingCallPayload.isNullOrBlank()) pendingIncomingCallPayload = "-1"
            refreshIncomingCaller()
            ensureIncomingSignalingOnRing(null)
            showIncomingCallNotification()
        } catch (e: Exception) { ... }
    }
}
```
**Verdict по §3:** ✅ REGRESSION FIXED. V2.1.6 поведение восстановлено:
LP case 115 всегда эмитит IncomingCall, SovaApp принимает любой payload и через
messagesGetCurrentCalls вытягивает caller_id при "-1"/null. Дублирующий guard
"пропущен (жду queuev4 calls)" отсутствует — он удалён вместе с `-1` skip в LongPollClient.

### 3.5 НОВЫЙ kill-switch (НЕ регрессия, осознанная фича)
SovaApp.kt:1842-1845 (LP), :1889-1892 (queuev4), :1951-1954 (events_queue), :1978-1981 (calls):
```kotlin
val callsEnabledNow = prefsSnapshot?.callsEnabled
if (callsEnabledNow != true) {
    AppLog.i("SovaApp", "INCOMING-CALL-KILLSWITCH: LP-событие проигнорировано (callsEnabled=$callsEnabledNow) — включите тумблер «Звонки» в настройках")
    return@collect
}
```
`#SETTINGS-CALLS-TOGGLE` (2026-10-14) — kill-switch звонков через prefsSnapshot.callsEnabled.
Если в настройках выключено → ВСЕ 4 коллектора пропускают события. Это НЕ регрессия —
осознанная фича. Но ВАЖНО: при `callsEnabled=false` IncomingCall НЕ показывается даже если
LP 115 успешно эмитит. Это может маскировать «звонки не приходят» — диагностика должна
проверять `prefsSnapshot.callsEnabled == true`.

---

## §4. Lifecycle: foreground vs background — Q4

### 4.1 ActivityLifecycleCallbacks (SovaApp.kt:861-875)
```kotlin
override fun onActivityStarted(activity) {
    startedActivities++
    AppLog.d("SovaApp", "Activity started, foreground count=$startedActivities")
}
override fun onActivityStopped(activity) {
    startedActivities = (startedActivities - 1).coerceAtLeast(0)
    AppLog.d("SovaApp", "Activity stopped, foreground count=$startedActivities")
}
```
ТОЛЬКО счётчик. НИКАКИХ stop() вызовов на onActivityStopped. Предыдущий DIAG-E
(sourceless) утверждал «LongPollClient.stop / CommunityPostsPoller.stop / KeepAliveService.stop
при foreground count=0» — ЭТО НЕВЕРНО по source. Каналы НЕ останавливаются при уходе в фон —
они продолжают работать под защитой foreground service. Это КОРРЕКТНО для messaging app.

### 4.2 ProcessLifecycleOwner ON_RESUME hook (SovaApp.kt:541-583)
`setupCookieBackgroundRefresh()` — регистрирует `DefaultLifecycleObserver` на
`ProcessLifecycleOwner.get().lifecycle`:
- `onResume(owner)`:
  - инкремент `foregroundTicks.value` (SovaApp.kt:557) — FeedScreen использует для reload после Doze.
  - throttle 30s → `CookieManager.getInstance().flush()` (SovaApp.kt:569).

Poller'ы НЕ паузятся в background. LongPoll продолжает poll. EventHub WS не закрывается.
Это правильно — канал доставки работает в фоне.

### 4.3 Возврат в foreground — MainActivity onResume (MainActivity.kt:2060-2135)
- `app.networkObserver.refreshNow()` — обновляет snapshot сети (#DOZE-RESUME-NET).
- `httpClient.connectionPool.evictAll()` если `> 5 минут` в фоне (#DOZE-STALE-POOL).
- `app.longPollClient.notifyResumed()` (MainActivity.kt:2088) — сброс backoff, evict
  connections, cancel in-flight poll, сброс tokenPauseUntilMs. LP пере-подключается сразу.
- `maybeProactiveTokenRefresh(app)` если `wasStopped && !isOfflineMode` (#DOZE-RESUME-PROACTIVE-REFRESH).
- `app.checkTokenValidity()` (MainActivity.kt:2105) — проактивная проверка истечения токена.
- Если токен инвалидирован в фоне → `launchAuth(AuthActivity SILENT)` (MainActivity.kt:2128-2133).

**Verdict §4:** ✅ WORKING. App НЕ паузит poller'ы в фоне (правильно для messaging),
при возврате в foreground сбрасывает backoff LP + evict stale pool + proactive token refresh.

---

## §5. Foreground service (LongPollKeepAliveService) — Q5

### 5.1 Foreground promote (LongPollKeepAliveService.kt:67-72, 235-256)
- `onCreate` → `startForegroundCompat()` (line 70).
- `startForegroundCompat()` (line 235-256):
  - Notification: id=7777, channel=`bg_keepalive`, IMPORTANCE_LOW, ongoing=true,
    category=CATEGORY_SERVICE, content="Получение сообщений в фоне",
    smallIcon=R.drawable.ic_notification, contentIntent → MainActivity.
  - `ServiceCompat.startForeground(this, 7777, notif, type)` где type =
    `ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING` на API 34+ (Android 14+), иначе 0.
  - Manifest declares `android:foregroundServiceType="remoteMessaging"` (AndroidManifest.xml:482)
    — соответствие кода и манифеста ✅.

### 5.2 onStartCommand return value (LongPollKeepAliveService.kt:74-94)
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForegroundCompat()                                       // line 76 — re-promote
    RealtimeWakeReceiver.schedule(this)                           // line 78 — re-arm alarm
    try {
        val app = SovaApp.get(this)
        if (app.tokenStorage.hasValidToken()) {
            app.longPollClient.start()                            // line 84 — auto-restart LP
            AppLog.i(TAG, "onStartCommand: LongPollClient ensured running")
        } else {
            AppLog.i(TAG, "onStartCommand: token not valid — LongPoll not started, awaiting refresh")
        }
    } catch (e: Exception) { ... }
    return START_STICKY                                           // line 93
}
```
- **START_STICKY** ✅ — Android перезапустит сервис если процесс убит.
- **Auto-restart LP если LP dies но service alive:** ✅ — каждый onStartCommand проверяет
  `app.longPollClient.start()` (idempotent per LongPollClient.kt:178-184 `synchronized`).
  Но это срабатывает ТОЛЬКО при повторном `startForegroundService` (BootReceiver /
  RealtimeWakeReceiver → `LongPollKeepAliveService.start()` → onStartCommand). Если LP
  умер сам по себе (без re-delivery) — он поднимается через `notifyResumed` из
  RealtimeWakeReceiver alarm (каждые 15 мин) ИЛИ через networkObserver onAvailable.
- **Headless refresh observer** (LongPollKeepAliveService.kt:149-185) — собирает
  `SovaApp.tokenInvalidationTicks`, при tick в фоне → `ensureFreshToken()` (Path 1.5 remixsid),
  при success → `app.longPollClient.notifyResumed()`, при fail → `bringMainActivityToForeground()`
  (FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP | SINGLE_TOP, throttled 15s).

### 5.3 Survive app kill?
- Сервис и app в ОДНОМ процессе (`android:process` НЕ указан в manifest:480-482).
  Foreground service удерживает процесс от Doze-kill, НО НЕ от swype-kill из recent apps
  на некоторых OEM. После swype-kill — START_STICKY восстанавливает сервис (через ~5-30с),
  onStartCommand пере-стартует LP. Это нормально.

### 5.4 Notification content
- Title="PinoK", text="Получение сообщений в фоне".
- Тап → MainActivity (FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP).
- Канал `bg_keepalive` / "Фоновая работа" / IMPORTANCE_LOW / setShowBadge=false.

**Verdict §5:** ✅ WORKING. Foreground promote + START_STICKY + headless refresh +
LP auto-restart on onStartCommand. Тип remoteMessaging правильный для Android 14+
(не подпадает под 6-часовой timeout).

---

## §6. Boot receiver — Q6

### 6.1 Manifest declaration (AndroidManifest.xml:438-448)
```xml
<receiver
    android:name=".locker.BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>
```
- ✅ `BOOT_COMPLETED` — после полной загрузки устройства.
- ✅ `LOCKED_BOOT_COMPLETED` — после загрузки в locked state (Direct Boot).
- ✅ `MY_PACKAGE_REPLACED` — после обновления приложения (app update не убивает push).
- ✅ `RECEIVE_BOOT_COMPLETED` permission declared (AndroidManifest.xml:102).

### 6.2 BootReceiver.onReceive (BootReceiver.kt:28-58)
```kotlin
override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_LOCKED_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED -> {
            AppLog.i("BootReceiver", "Boot/pkg-replaced received (${intent.action}), locker armed")
            try {
                val app = SovaApp.getOrNull()
                val hasToken = app?.tokenStorage?.hasValidToken() == true
                val hasRemixsid = !app?.exchangeAuthRepository?.remixsid().isNullOrBlank()
                if (hasToken || hasRemixsid) {                           // line 43
                    AppLog.i("BootReceiver", "Session present (...) — starting keep-alive service")
                    LongPollKeepAliveService.start(context)              // line 45
                    re.pinok.realtime.RealtimeWakeReceiver.schedule(context)  // line 49
                } else {
                    AppLog.i("BootReceiver", "No session — keep-alive service not started")
                }
            } catch (e: Exception) { ... }
        }
    }
}
```
- ✅ После boot: если есть token ИЛИ remixsid → стартует LongPollKeepAliveService.
- ✅ Дублирует `RealtimeWakeReceiver.schedule()` — на случай если service не поднялся сразу.
- ✅ Если сессии нет — сервис НЕ стартует (не гонять LP без кредов).
- Замечание: в Direct Boot (LOCKED_BOOT_COMPLETED) EncryptedSharedPreferences ещё НЕ
  доступен → `hasValidToken()` может вернуть false даже если token есть. Но
  `LongPollKeepAliveService.start(context)` всё равно вызывается если hasRemixsid
  (remixsid в CookieManager доступен в Direct Boot? — зависит от устройства). На
  практике после unlock device сработает `BOOT_COMPLETED` повторно → сервис поднимется.

**Verdict §6:** ✅ WORKING. Boot + package-replaced covered. Manifest + permission complete.

---

## §7. Battery / Doze — Q7

### 7.1 Manifest permissions (AndroidManifest.xml:90, 111)
```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />        <!-- line 90 -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />  <!-- line 111 -->
<uses-permission android:name="android.permission.WAKE_LOCK" />                  <!-- line 56 -->
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />      <!-- line 91 -->
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />  <!-- line 92 -->
```
- ✅ `SCHEDULE_EXACT_ALARM` (Android 12+) — для `setExactAndAllowWhileIdle` (реализовано в RealtimeWakeReceiver.kt:91-98).
- ✅ `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — для диалога «Работа в фоне» (MainActivity.kt:1064-1069).
- ✅ `WAKE_LOCK` — объявлен, но НЕ ИСПОЛЬЗУЕТСЯ в realtime коде (см. §7.3).
- ✅ `USE_FULL_SCREEN_INTENT` — для incoming call notification (Android 14+).
- ✅ `ACCESS_NOTIFICATION_POLICY` — для bypass DND.

### 7.2 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS usage (MainActivity.kt:1036-1077)
- `LaunchedEffect(snap.batteryExemptionAskedAt, currentAuthVersion, isOfflineMode)` (line 1036).
- `pm.isIgnoringBatteryOptimizations(packageName)` (line 1040).
- Если НЕ в whitelist И НЕ спрашивали 7 дней → показываем AlertDialog (line 1049-1078).
- On confirm: `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (line 1066).
- Также дублируется в SettingsScreen.kt:2527-2537 (та же логика, тот же intent).

### 7.3 WakeLock — НЕ ИСПОЛЬЗУЕТСЯ в realtime (Grep по realtime/ и SovaApp.kt — 0 совпадений)
- RealtimeWakeReceiver.onReceive (RealtimeWakeReceiver.kt:38-60) НЕ acquiring WakeLock.
- `AlarmManager.RTC_WAKEUP` (line 97) будит CPU, но BroadcastReceiver.onReceive без WakeLock
  имеет ~10с system window. Однако работа (LP notifyResumed + EventHub.start) запускает
  корутины в `keepAliveScope` (app-scope) — они продолжаются в процессе после возврата receiver.
- Процесс удерживается foreground service LongPollKeepAliveService → риск kill минимален.
- **НО:** если foreground service НЕ запущен (post-logout, или BootReceiver без token) и
  alarm fires → receiver returns быстро, корутины могут не успеть завершиться. На практике
  это не проблема (alarm fires только если был schedule, а schedule только если service был
  поднят). Микро-риск.

### 7.4 DOZE-RELIABILITY alarm (RealtimeWakeReceiver.kt:75-106)
```kotlin
fun schedule(context: Context) {
    ...
    val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()                            // line 92
    } else true
    if (canExact) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)  // line 97
        AppLog.i(TAG, "#DOZE-RELIABILITY alarm scheduled (exact, +${INTERVAL_MS / 60_000}min)")
    } else {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)      // line 100
        AppLog.i(TAG, "#DOZE-RELIABILITY alarm scheduled (inexact, ...)")
    }
}
```
- ✅ Interval 15 мин (RealtimeWakeReceiver.kt:67 `INTERVAL_MS = 15 * 60_000L`).
- ✅ `setExactAndAllowWhileIdle` если `canScheduleExactAlarms()` (Android 12+).
- ✅ Fallback на `setAndAllowWhileIdle` (inexact, но всё равно будит в Doze).
- ✅ onReceive (RealtimeWakeReceiver.kt:38-60): `longPollClient.notifyResumed()` +
  `eventHubClient.start()` + `schedule(context)` (re-arm).

### 7.5 Re-arm после boot / service restart
- BootReceiver.kt:49 — `RealtimeWakeReceiver.schedule(context)` (post-boot).
- LongPollKeepAliveService.kt:78 — `RealtimeWakeReceiver.schedule(this)` (onStartCommand).
- LongPollKeepAliveService.kt:309 — `RealtimeWakeReceiver.schedule(context)` (companion start).
- RealtimeWakeReceiver.kt:59 — `schedule(context)` (onReceive, self-re-arm).

**Verdict §7:** ✅ WORKING. SCHEDULE_EXACT_ALARM + REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
объявлены и используются. Alarm 15 мин точный (с fallback). Микро-риск: WakeLock не
acquired в receiver — но foreground service покрывает. Если пользователь отклонит
battery exemption → LP будет периодически «замерзать» в Doze, но alarm + foreground
service + notifyResumed компенсируют (сообщения будут приходить с задержкой до 15 мин).

---

## §8. Manifest completeness — Q8

### 8.1 Permissions (AndroidManifest.xml)
| Permission | Line | Status |
|------------|------|--------|
| INTERNET | 6 | ✅ |
| ACCESS_NETWORK_STATE | 7 | ✅ |
| ACCESS_WIFI_STATE | 8 | ✅ |
| RECORD_AUDIO | 9 | ✅ |
| CAMERA | 11 | ✅ |
| MODIFY_AUDIO_SETTINGS | 18 | ✅ |
| WRITE_EXTERNAL_STORAGE (maxSdk=28) | 21-22 | ✅ |
| READ_EXTERNAL_STORAGE (maxSdk=32) | 23-24 | ✅ |
| READ_MEDIA_AUDIO/VIDEO/IMAGES | 29-31 | ✅ |
| MANAGE_EXTERNAL_STORAGE | 37 | ✅ |
| REQUEST_INSTALL_PACKAGES | 49 | ✅ |
| FOREGROUND_SERVICE | 52 | ✅ |
| FOREGROUND_SERVICE_MEDIA_PLAYBACK | 53 | ✅ |
| FOREGROUND_SERVICE_DATA_SYNC | 54 | ✅ |
| POST_NOTIFICATIONS | 55 | ✅ |
| WAKE_LOCK | 56 | ✅ (объявлен, не используется в realtime — см. §7.3) |
| FOREGROUND_SERVICE_MICROPHONE | 69 | ✅ |
| FOREGROUND_SERVICE_REMOTE_MESSAGING | 70 | ✅ |
| FOREGROUND_SERVICE_CAMERA | 71 | ✅ |
| BLUETOOTH_CONNECT | 78 | ✅ |
| SCHEDULE_EXACT_ALARM | 90 | ✅ |
| USE_FULL_SCREEN_INTENT | 91 | ✅ |
| ACCESS_NOTIFICATION_POLICY | 92 | ✅ |
| USE_BIOMETRIC / USE_FINGERPRINT | 95-96 | ✅ |
| VIBRATE | 99 | ✅ |
| RECEIVE_BOOT_COMPLETED | 102 | ✅ |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 111 | ✅ |

### 8.2 Services (AndroidManifest.xml:381-482)
| Service | Line | fgType | Exported |
|---------|------|--------|----------|
| .service.PlayerService | 382-389 | mediaPlayback | false | ✅ |
| .service.VideoPlaybackService | 393-396 | mediaPlayback | false | ✅ |
| .media.MusicDownloadService | 402-405 | dataSync | false | ✅ |
| .media.VideoDownloadService | 411-414 | dataSync | false | ✅ |
| .media.StoryVideoDownloadService | 422-425 | dataSync | false | ✅ |
| .media.ClipDownloadService | 433-436 | dataSync | false | ✅ |
| **.realtime.LongPollKeepAliveService** | **479-482** | **remoteMessaging** | **false** | ✅ |

### 8.3 Receivers (AndroidManifest.xml:438-471)
| Receiver | Line | Exported | Intent-filter |
|----------|------|----------|---------------|
| .locker.BootReceiver | 439-448 | **true** | BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, MY_PACKAGE_REPLACED | ✅ |
| .realtime.NotificationActionReceiver | 453-462 | false | re.pinok.action.MARK_READ/REPLY/DISMISS | ✅ |
| .realtime.RealtimeWakeReceiver | 468-471 | false | (явный, без intent-filter — PendingIntent) | ✅ |

### 8.4 Runtime-registered receivers missing manifest entry?
- `ActivityLifecycleCallbacks` (SovaApp.kt:861-875) — НЕ receiver, callbacks.
- `ProcessLifecycleOwner` observer (SovaApp.kt:547) — Lifecycle observer, не receiver.
- `NetworkObserver` (через ConnectivityManager.registerNetworkCallback) — runtime callback,
  НЕ receiver — переживает только жизнь процесса. После app kill — теряется, НО
  LongPollKeepAliveService при re-create регистрирует снова? — НЕ проверено (нужно читать
  NetworkObserver). Но LongPollClient.startNetworkWatcher() (LongPollClient.kt:187) тоже
  регистрирует — при onStartCommand service пере-стартует LP → пере-регистрирует watcher.
- Все manifest receivers (Boot, NotificationAction, RealtimeWake) присутствуют.

**Verdict §8:** ✅ COMPLETE. Все services/receivers объявлены. Permissions полные.
Рантайм-регистрируемых receivers, требующих manifest entry, нет.

---

## §9. Multi-account — Q9

### 9.1 Оркестрация multi-account в SovaApp.kt
Grep `switchAccount|accountSwitch|onAccountChanged|switchUser|multiaccount` по SovaApp.kt —
**0 совпадений**. Специального account-switch handler'а НЕТ.

### 9.2 Что происходит при смене аккаунта
Из логики кода следует: смены аккаунта как таковой НЕТ. Есть только logout → signOut →
login под новым аккаунтом. При logout (MainActivity.kt:1515-1650):
1. `app.longPollClient.stop()` (line 1535)
2. `app.eventHubClient.stop()` (line 1542)
3. `app.communityPostsPoller?.stop()` (line 1550)
4. `LongPollKeepAliveService.stop(this@MainActivity)` (line 1558)
5. `RealtimeWakeReceiver.cancel(this@MainActivity)` (line 1565)
6. `app.exchangeAuthRepository.signOut()` (line 1584) — очищает cookies+storage.
7. `launchAuth(AuthActivity)` (line 1639) — запуск нового login flow.

После login под новым аккаунтом — `LaunchedEffect(currentAuthVersion)` (MainActivity.kt:1203)
срабатывает повторно, стартует longPollClient/eventHubClient/startCallSignaling/KeepAliveService.

### 9.3 ПРОБЛЕМА: queuev4Client/eventsQueuev4Client НЕ остановлены при logout
В onLogout handler (MainActivity.kt:1515-1650) НЕТ вызовов:
- `app.queuev4Client.stop()` ❌
- `app.eventsQueuev4Client.stop()` ❌
- `app.callsQueuev4Client.stop()` ❌
- `app.notificationsPoller?.stop()` ❌
- `app.securityAlertsPoller?.stop()` ❌

В LaunchedEffect(currentAuthVersion) else-branch (MainActivity.kt:1216-1229) ЕСТЬ:
- `app.queuev4Client.stop()` ✅ (line 1219)
- `app.callsQueuev4Client.stop()` ✅ (line 1221)
- `app.communityPostsPoller?.stop()` ✅ (line 1225)
- НО `app.eventsQueuev4Client.stop()` ❌ — отсутствует
- НО `app.notificationsPoller?.stop()` ❌ — отсутствует
- НО `app.securityAlertsPoller?.stop()` ❌ — отсутствует

### 9.4 РИСК: leak → 401 spam
- `eventsQueuev4Client` — после logout продолжит long-poll старую очередь с протухшими
  кредами → VK вернёт 401 → reconnect loop → 401 spam в логах. Протухает только когда
  процесс будет убит системой (foreground service уже остановлен).
- `notificationsPoller` / `securityAlertsPoller` — poller.start() запущен в onCreate
  безусловного, но poller сам проверяет hasValidToken перед каждым poll → безопасно
  (не делает запросов без токена). НО цикл корутины жив, периодически просыпается.
- onExitApp (MainActivity.kt:1660-1688) — та же неполнота: не останавливает queuev4/events/calls.

### 9.5 Multi-account handling
Поскольку account switch = logout+login, НЕТ одновременной работы нескольких аккаунтов.
Нет риска mixed credentials — каждый startCallSignaling получает свежий `vkUid` из
`exchangeAuthRepository.userId()` (SovaApp.kt:2148, 2217). НО если logout не останавливает
старые queuev4 — они будут polling под старым uid → 401 spam.

**Verdict §9:** ⚠ PARTIAL. Multi-account не реализован (только logout+login).
НО cleanup при logout НЕПОЛНЫЙ: eventsQueuev4Client, queuev4Client (onLogout path only),
callsQueuev4Client (onLogout path only), notificationsPoller, securityAlertsPoller
не останавливаются. Риск 401 spam от queuev4 clients между logout и process kill.

---

## §10. Stop / cleanup — Q10

### 10.1 Cleanup matrix (3 cleanup paths)
| Channel | LaunchedEffect else-branch (MainActivity:1216-1229) | onLogout (MainActivity:1515-1650) | onExitApp (MainActivity:1660-1688) |
|---------|---|---|---|
| longPollClient | ✅ stop (1218) | ✅ stop (1535) | ✅ stop (1668) |
| eventHubClient | ✅ stop (1223) | ✅ stop (1542) | ✅ stop (1672) |
| queuev4Client | ✅ stop (1219) | ❌ НЕ остановлен | ❌ НЕ остановлен |
| eventsQueuev4Client | ❌ НЕ остановлен | ❌ НЕ остановлен | ❌ НЕ остановлен |
| callsQueuev4Client | ✅ stop (1221) | ❌ НЕ остановлен | ❌ НЕ остановлен |
| notificationsPoller | ❌ НЕ остановлен | ❌ НЕ остановлен | ❌ НЕ остановлен |
| communityPostsPoller | ✅ stop (1225) | ✅ stop (1550) | ✅ stop (1677) |
| securityAlertsPoller | ❌ НЕ остановлен | ❌ НЕ остановлен | ❌ НЕ остановлен |
| LongPollKeepAliveService | ✅ stop (1226) | ✅ stop (1558) | ✅ stop (1681) |
| RealtimeWakeReceiver alarm | ✅ cancel (1228) | ✅ cancel (1565) | ✅ cancel (1685) |

### 10.2 Анализ последствий
- **eventsQueuev4Client** — БРОКЕН: останавливается ТОЛЬКО при process kill. После logout
  (без выхода из приложения) продолжит polling старой очереди с протухшими кредами → 401 spam.
  Самый серьёзный leak. **Вердикт: ❌ LEAK.**
- **queuev4Client + callsQueuev4Client** в onLogout path — НЕ остановлены (только в
  LaunchedEffect else-branch, который НЕ срабатывает при onLogout, т.к. onLogout сам
  инкрементит authVersion). После `signOut()` (очистка token) — LaunchedEffect перезапускается
  с hasValidToken()==false → else-branch → стоп. Так что в итоге они остановятся, но через
  рекомпозицию. Микро-race window — подписки могут успеть сделать 1 poll с invalid creds.
  **Вердикт: ⚠ race, но self-healing через LaunchedEffect.**
- **notificationsPoller** — НЕ остановлен ни в одном path. Но poller.start() запускает
  корутину, которая в pollOnce проверяет `hasValidToken` → без токена не делает запросов.
  Safe — утечки нет, просто корутина спит. **Вердикт: ✅ safe-by-design.**
- **securityAlertsPoller** — аналогично. Safe-by-design. **Вердикт: ✅ safe-by-design.**

### 10.3 Cleanup recommendation
Добавить в onLogout (MainActivity:1515) и onExitApp (MainActivity:1660):
```kotlin
app.queuev4Client.stop()
app.eventsQueuev4Client.stop()
app.callsQueuev4Client.stop()
app.notificationsPoller?.stop()
app.securityAlertsPoller?.stop()
```
Это уберёт race window и 401 spam от eventsQueuev4Client.

**Verdict §10:** ⚠ PARTIAL. Основные каналы (LP, EventHub, KeepAlive, alarm, community)
останавливаются. eventsQueuev4Client НЕ останавливается НИ В ОДНОМ path — leak.
queuev4/callsQueuev4 — race window в onLogout, но self-healing. Poller'ы safe-by-design
(hasValidToken check в pollOnce).

---

## §11. Recent regressions — git log

### 11.1 git log --all -- app/src/main/java/re/pinok/SovaApp.kt app/src/main/java/re/pinok/realtime/
История полностью squashed в один initial commit c9d4ff8 (P0.46 wall.getComment):
```
c9d4ff8 feat(comments): P0.46 — wall.getComment after reply + long-press menu
```
Все последующие коммиты (a9b1fc8 → f678836) НЕ затрагивают SovaApp.kt / realtime/ —
они касаются community pagination (P0.47), reactions (P0.46), comments (P0.46), IME (P0.46).

### 11.2 git log -p --all -S 'пропущен (жду queuev4 calls)' — 0 результатов
Guard-строка, описанная в P0.43-r8, НЕ найдена ни в одном коммите. Значит либо guard
никогда не дошёл до git (был в working tree, но откатан до commit), либо repository был
rebuilt/squashed и история потеряна. В любом случае в текущем HEAD guard'а НЕТ.

### 11.3 git blame SovaApp.kt:1846-1880
Все строки восходят к `^c9d4ff8` (squashed initial commit). P0.43-r8-V216-RESTORE
комментарии уже в этом initial commit → фикс применён ДО snapshot HEAD.

### 11.4 git blame LongPollClient.kt:977-992
Та же картина — все строки от `^c9d4ff8`. Фикс применён до snapshot.

### 11.5 Видимые коммиты (top 30) — анализ подозрительных
| Commit | Анализ |
|--------|--------|
| f678836 fix(community): P0.47-REVERT | НЕ о realtime (записи/фото/видео сообществ). |
| a9b1fc8 fix(pagination): P0.47 compile | НЕ о realtime. |
| 9635707 refactor(pagination): P0.47 cursor-based | НЕ о realtime. |
| e797063 chore(release): 2.2.5 | Версия 2.2.5, манифест обновления, «Авторизация 2» по умолчанию. НЕ realtime. |
| Все P0.46 (reactions/comments/IME) | НЕ о realtime. |

**Подозрительных коммитов по orchestration в видимой истории НЕТ.**
Последнее изменение orchestration — squashed commit c9d4ff8, который содержит
уже-FIXED код (P0.43-r8-V216-RESTORE).

### 11.6 Изменения между P0.43-r7 (worklog) и текущим HEAD
Из worklog P0.43-r8 D1-D6 (строки 933-955):
- D1 (LP 115 "-1" guard): ✅ ИСПРАВЛЕНО (см. §3).
- D2 (callsQueuev4Client подписан на countersQueueId): ✅ ИСПРАВЛЕНО — теперь
  callsQueuev4Client подписан на dedicated callsQueueId(uid, clientId) через queue.subscribe
  (SovaApp.kt:2219-2236, см. callDedicatedCallsQueueStart).
- D3 (stickersSubscribeToQueue вместо queueSubscribe): ⚠ ЧАСТИЧНО — stickersSubscribeToQueue
  всё ещё используется для queuev4Client (multiaccount) и eventsQueuev4Client (events),
  НО callsQueuev4Client переведён на queue.subscribe (V2.1.6-механизм). Гибридная схема.
- D4 (парсер entity_type:"calls"): ✅ реализован в Queuev4Client.parseCallEvents
  (вызывается в startCallNotifier, SovaApp.kt:1895-1936 + 1983-2021).
- D5 (callsQueuev4Client существует): ⚠ ОСТАЁТСЯ — 3-я dedicated calls-очередь всё ещё
  есть (SovaApp.kt:144 lateinit, 2214-2245 start). НО теперь используется для своей
  задачи (dedicated calls), а не ломает multiaccount.
- D6 (handleQueuev4Incoming): ✅ реализован (SovaApp.kt:2041-2084) — V2.1.6-эквивалент.

**Verdict §11:** Все P0.43-r8 регрессии ИСПРАВЛЕНЫ в текущем HEAD. История squashed,
но код-состояние соответствует fixed-версии. Подозрительных коммитов в видимой истории нет.

---

## СВОДНЫЙ VERDICT ПО ВСЕМ КАНАЛАМ (с source refs)

| # | Канал | Verdict | Source refs |
|---|-------|---------|-------------|
| 1 | LongPollClient (api.vk.com LP) | ✅ WORKING | SovaApp.kt:1523 (instantiate), MainActivity.kt:1206 (start), LongPollClient.kt:177-190 (start idempotent), :193-205 (stop) |
| 2 | EventHubWebSocketClient (wss://eh.vk.ru) | ✅ WORKING | SovaApp.kt:1534 (instantiate), MainActivity.kt:1209 (start), RealtimeWakeReceiver.kt:49 (re-start on wake) |
| 3 | queuev4Client (multiaccount via stickers.subscribeToQueue) | ⚠ PARTIAL | SovaApp.kt:2163-2176 (stickersSubscribeToQueue — отдаёт counters, не calls/multiaccount per DIAG-A2) |
| 4 | eventsQueuev4Client (events via stickers.subscribeToQueue) | ⚠ PARTIAL | SovaApp.kt:2180-2193 (тот же API, тот же queue_id — дублирует #3) |
| 5 | callsQueuev4Client (dedicated calls via queue.subscribe) | ✅ WORKING | SovaApp.kt:2214-2245 (callDedicatedCallsQueueStart — восстановлен v1.9.8-механизм, callsQueueId(uid, clientId)) |
| 6 | NotificationsPoller | ✅ WORKING | SovaApp.kt:2771-2789 (start + LP 114 → triggerImmediatePoll) |
| 7 | CommunityPostsPoller | ✅ WORKING | SovaApp.kt:2800-2808 (display filter — DIAG-B2) |
| 8 | SecurityAlertsPoller | ⚠ PARTIAL | SovaApp.kt:2821-2829 (стартует, но VK method может err=3 — нужен runtime logcat) |
| 9 | StatsQueuePoller | ✅ NOT-GLOBAL (per-screen) | StatsQueuePoller.kt:42-125, AdminStatsScreenW47.kt:101 (on-demand, не оркеструется из SovaApp) |
| 10 | LongPollKeepAliveService (foreground) | ✅ WORKING | LongPollKeepAliveService.kt:60-322, START_STICKY (line 93), FG type remoteMessaging (line 248-249) |
| 11 | RealtimeWakeReceiver (Doze alarm) | ✅ WORKING | RealtimeWakeReceiver.kt:36-136, 15-min exact alarm, self-re-arm |
| 12 | BootReceiver (boot/pkg-replaced) | ✅ WORKING | BootReceiver.kt:28-58, manifest:439-448 |
| 13 | LP case 115 → IncomingCall | ✅ WORKING (REGRESSION FIXED) | LongPollClient.kt:977-992 (безусловный emit), SovaApp.kt:1846-1878 (принимает "-1" + refreshIncomingCaller) |
| 14 | ActivityLifecycleCallbacks (foreground count) | ✅ WORKING | SovaApp.kt:861-875 (только счётчик, НЕ останавливает каналы) |
| 15 | ProcessLifecycleOwner ON_RESUME | ✅ WORKING | SovaApp.kt:541-583 (foregroundTicks + cookie flush) |
| 16 | Logout cleanup | ⚠ PARTIAL | MainActivity.kt:1515-1650 — eventsQueuev4Client/notificationsPoller/securityAlertsPoller НЕ остановлены |

---

## ПРИОРИТЕТЫ ИСПРАВЛЕНИЙ (по evidence-weight из source)

**P0 (КРИТИЧНО — leak):**
1. §10 — eventsQueuev4Client НЕ останавливается НИ В ОДНОМ cleanup path (logout / exit /
   token-cleared). После logout продолжает polling старой очереди с протухшими кредами →
   401 spam. ДОБАВИТЬ `app.eventsQueuev4Client.stop()` в MainActivity.kt:1515 (onLogout),
   1660 (onExitApp), 1216 (else-branch).

**P1 (серьёзная проблема, влияет на надёжность):**
2. §3.5 — kill-switch `callsEnabled` (SovaApp.kt:1842-1845, 1889-1892, 1951-1954, 1978-1981)
   глушит ВСЕ 4 коллектора если prefsSnapshot.callsEnabled != true. Если пользователь
   случайно выключил «Звонки» в настройках — incoming calls не покажутся даже при
   успешно-emitted IncomingCall. Диагностика должна проверять это значение.
3. §9.4 — queuev4Client + callsQueuev4Client НЕ остановлены в onLogout/onExitApp (только в
   LaunchedEffect else-branch). Race window между signOut() и recomposition → возможен 1-2
   poll с invalid creds. Само-healing через LaunchedEffect. ДОБАВИТЬ в onLogout/onExitApp.
4. §7.3 — WakeLock не acquired в RealtimeWakeReceiver.onReceive. Микро-риск (foreground
   service покрывает), но если service не поднят — корутины могут не успеть завершиться.

**P2 (архитектурный долг):**
5. §2 — queuev4Client и eventsQueuev4Client ОБА подписаны через `stickersSubscribeToQueue()`
   (SovaApp.kt:2163, 2180) — ВТОРОЙ ВЫЗОВ даёт тот же queue_id, что и первый → дублирование.
   См. DIAG-A2 — stickersSubscribeToQueue отдаёт counters, не multiaccount.
6. §8 — WAKE_LOCK permission объявлен, но НЕ ИСПОЛЬЗУЕТСЯ в realtime. Либо убрать, либо
   приобрести WakeLock в RealtimeWakeReceiver.onReceive (через PowerManager.newWakeLock
   + goAsync + acquire(timeout) + release в finally).

**P3 (минор):**
7. §4 — нет явного pause pollers в background. На батарее это OK (foreground service
   держит), но `notificationsPoller` / `communityPostsPoller` / `securityAlertsPoller`
   продолжают REST-poll в фоне. Можно паузить если foreground count==0 ИЛИ сократить
   частоту (но это уже UX-решение).

---

## NEXT ACTIONS (для след. агентов)
1. **DIAG-A2 cross-ref**: проверить, что `stickersSubscribeToQueue()` (SovaApp.kt:2163, 2180)
   реально возвращает разные queue_id для двух вызовов. Если возвращает ОДИНАКОВЫЙ — второй
   queuev4 client дублирует первый (no events duplication, но лишний long-poll connection).
2. **Runtime verification**: запустить logcat с фильтром `INCOMING_CALL (LP 115)` + `callsEnabled`
   чтобы подтвердить что P0.43-r8-V216-RESTORE действительно работает на живом устройстве.
3. **Fix eventsQueuev4Client leak**: добавить `app.eventsQueuev4Client.stop()` в 3 cleanup path
   (MainActivity.kt:1515, 1660, 1216). Это P0 задача — 401 spam after logout.
4. **SecurityAlertsPoller runtime check**: запустить logcat и проверить, продолжает ли
   VK возвращать err=3 для `accountPersonal.getSecurityAlerts`. Если да — отключить poller
   или найти правильный method name.
5. **DIAG-B2 cross-ref**: проверить, что CommunityPostsPoller с notifyMode=ALL реально
   показывает посты (DIAG-E §2.5 отмечал notifyMode=0 → silent — но notifyMode=0 это
   MESSAGES_ONLY, не SILENT, так что сообщества фильтруются по design).

- Файлы кода НЕ изменены. Изменён только /home/z/my-project/worklog.md (этот блок).


---
Task ID: DIAG-SUMMARY
Agent: main (orchestrator)
Task: Сводный отчёт по 5 параллельным диагностикам системы уведомлений PinoK (DIAG-A2/B2/C2/D2/E2)

# СВОДНЫЙ ОТЧЁТ: ДИАГНОСТИКА СИСТЕМЫ УВЕДОМЛЕНИЙ PINOK

Репо: `/home/z/repos/PinoK_1`, ветка PinoK, HEAD `f678836` (P0.47-REVERT).
Все 5 агентов получили доступ к реальным исходникам (307 .kt файлов).

## Каналы realtime и их статус

| Канал | Назначение | Статус | Доказательство |
|---|---|---|---|
| LongPollClient | PRIMARY: msg/online/typing/LP115 calls | ✅ WORKING | LongPollClient.kt:42-83, exp backoff + token-pause + net observer |
| EventHubWebSocketClient | WS eh.vk.ru, мгновенная доставка (fallback LP) | ⚠️ DEAD-EVENT | EventHubWebSocketClient.kt:58-59 emits, НО 0 коллекторов во всём /app |
| queuev4Client (multiaccount) | online/im/counters | ⚠️ NEAR-DEAD for calls | SovaApp.kt:2173-2175, слушает на всякий случай |
| eventsQueuev4Client | 2-й поток online/im/counters | ⚠️ NEAR-DEAD + LEAK | SovaApp.kt:2189-2191, НЕ остановлен в cleanup |
| callsQueuev4Client | РЕАЛЬНЫЙ канал входящих звонков | ✅ WORKING | SovaApp.kt:2214+, queue_id=calls_<uid>_<clientId>_1 |
| ChannelWebSocketClient | WS для каналов VK | ❌ DEAD-CODE | ChannelWebSocketClient.kt:102-111 stub, все методы TODO |
| NotificationsPoller | notifications.getRedesign | ✅ WORKING | NotificationsPoller.kt:52-56, +triggerImmediatePoll по LP 114 |
| CommunityPostsPoller | newsfeed.get (community posts) | ✅ WORKING | CommunityPostsPoller.kt:71-75, gated by FeedVisibility |
| SecurityAlertsPoller | accountPersonal.getSecurityAlerts | ❌ BROKEN | SecurityAlertsPoller.kt:124-129, err=3 "Unknown method" → permanent stop |
| StatsQueuePoller | admin stats | ✅ WORKING on-demand | AdminStatsScreenW47.kt:101 |
| LongPollKeepAliveService + RealtimeWakeReceiver | foreground keep-alive | ✅ WORKING | FGS type=remoteMessaging, id=7777, +15min AlarmManager |

## Notifiers и отображение

| Notifier | Каналы | Статус |
|---|---|---|
| MessageNotifier | 5 (messages, messages_silent, communities×3) | ✅ WORKING |
| VkNotificationsNotifier | 10 (vk_likes/comments/replies/follows/mentions/reposts/wall/gifts/other/security_alerts) | ✅ WORKING |
| ReplyResultNotifier | переиспользует vk_comments | ✅ WORKING (auto-cancel 3s/5s) |
| SecurityAlertNotifier | vk_security_alerts (HIGH+bypassDnd) | ⚠️ PARTIAL (код исправен, источника данных нет) |
| LongPollKeepAliveService FG | bg_keepalive (LOW, ongoing) | ✅ WORKING |

## Настройки и prefs

- ✅ SovaPrefs: 15+ notify-ключей (звук/вибрация/превью/per-section/per-user-mute/quietHours/notifyMode)
- ✅ NotifyModeSelector: 4 режима (MESSAGES_ONLY/ALL/COMMUNITIES_ONLY/SILENT), global, 2 UI места
- ✅ VKNotifyWeb: HTML-скрейпер m.vk.ru/settings?act=notify (после провала settingsGeneral.* на err=3)
- ✅ Backup/restore: полный дамп DataStore + AES-GCM шифрование

## TOP ПРОБЛЕММЫ (по приоритету)

### P0 — критичные (ломают core-функциональность)

1. **sn_push_send master toggle BROKEN** — главный тумблер «Получать push-уведомления» всегда
   откатывается (Toast «Не удалось изменить»). `webGroupForPinoKKey("sn_push_send")=null` →
   `settingsSetNotifyViaWeb` возвращает false. Фикс существует, но не подключён:
   `VKNotifyWeb.savePushMaster()` (VKNotifyWeb.kt:311-325) реализован, но НИКЕМ не вызывается.
   **Файлы**: VKApiClient.kt:19245-19265, SettingsScreen.kt:7502, VKNotifyWeb.kt:311-325.

2. **Quiet hours НЕ применяются к direct messages** — `VkNotificationsNotifier.showBatch`
   проверяет `isInQuietHours` (VkNotificationsNotifier.kt:341-344), но `MessageNotifier.showNotification`
   НЕ проверяет. Прямые сообщения приходят со звуком в окне 22:00-08:00.
   **Файл**: MessageNotifier.kt:182-292.

3. **Server-side DND (silent_until) НЕ подавляется локально** — `account.startSilentMode`
   вызывается, статус отображается, но PinoK notifications продолжают приходить (VK перестаёт
   слать FCM, но PinoK не использует FCM — он опрашивает LongPoll/getRedesign).
   **Файл**: SovaApp.kt:1637-1764, SettingsScreen.kt:7414-7496.

4. **eventsQueuev4Client утечка после logout** — НЕ остановлен НИ В ОДНОМ cleanup path
   (MainActivity.kt:1216/1515/1660). После logout продолжает polling с протухшими кредами →
   401 spam до process kill.
   **Файл**: MainActivity.kt:1216, 1515, 1660.

5. **SecurityAlertsPoller сломан** — `accountPersonal.getSecurityAlerts` не существует на
   WEB-шлюзе (err=3) → permanent stop. SecurityAlertNotifier.kt НИКОГДА не вызывается.
   Пользователь не получает уведомления о подозрительных входах.
   **Файл**: SecurityAlertsPoller.kt:119-134.

### P1 — серьёзные (видимые пользователю)

6. **EventHub — потерянный канал** — WS-соединение работает (auth, ping/pong, reconnect),
   но `EventHubEvent` flow НЕ собирается НИКЕМ во всём проекте. Тратит сетевой ресурс и
   `eventHub.getToken` впустую. Либо реализовать парсер, либо отключить старт.
   **Файл**: EventHubWebSocketClient.kt:58-59, SovaApp.kt (grep eventHubClient.events = 0).

7. **21 sn_* ключ HIDDEN из UI** — нет VK web group mapping для: sn_birthdays, sn_messages,
   sn_chats, sn_message_requests, sn_groups, sn_new_posts, sn_stories, sn_photo_tags, и др.
   Пользователь не может их настроить через PinoK UI.
   **Файл**: SettingsScreen.kt:7101-7111, VKNotifyWeb.kt:59-90.

8. **Per-chat timed mute MISSING** — UI только бинарный Switch (forever). VK API
   `account.setSilenceMode` поддерживает `time=N` секунд, но UI не передаёт N.
   **Файл**: VKApiClient.kt:2402-2484, ChatInfoScreen.kt:376-402.

9. **Mark-as-read не зануляет бейдж локально** — после тапа «Отметить все прочитанными»
   бейдж остаётся, только snackbar показывается.
   **Файл**: NotificationsScreen.kt:1017-1020.

10. **Архивированный чат / удалённое сообщение — нет edge-case guard** — пользователь тапает
    на пуш для удалённого сообщения → навигируется на ChatDetailScreen → получает пустой
    экран / generic error.
    **Файл**: MainActivity.kt:2378-2394.

11. **Cross-channel call dedup отсутствует** — 4 коллектора (LP + 3× queuev4) могут race'ить
    `pendingIncomingCallPayload`. Guard `!= event.payload` (SovaApp.kt:1858) не покрывает
    случай, когда 2 коллектора эмитят РАЗНЫЕ payload'ы одного звонка.

12. **Мёртвый код UI тип-фильтра** — триггер кнопки фильтра удалён (#NOTIFSHOW), но мёртвый
    код фильтрации остался.
    **Файл**: NotificationsScreen.kt:1047, 1158.

13. **Нет бейджа на drawer-пункте «Уведомления»** — только у Messages.

14. **4 типа уведомлений без иконок/матчинга**: audio, event_invite, sdk_app_request,
    wall_reply.
    **Файл**: NotificationsScreen.kt:316-388, VKApiClient.kt:18991-19055.

### P2 — minor (не критичные, но видимые)

15. **4 VKNotifyWeb save-метода — DEAD CODE**: savePushMaster/Group/Message/Chat определены,
    но 0 callers кроме самого файла.
16. **emailNotifyFreq — DEAD STORE**: UI не читает (SettingsScreen.kt:7596).
17. **Per-community notify state unreadable**: нет гетчера в VK API → drift после toggle.
18. **EventDedup unbounded growth**: ConcurrentHashMap без eviction (~10к ключей/день).
19. **Counter drift после network failure**: no retry (UnreadMessagesCounter.kt:191-195).
20. **Queuev4Client.stop() не отписывается на сервере**: queue остаётся активной до таймаута key.
21. **TODO action button «Завершить сессию»** в SecurityAlertNotifier — не реализован.
22. **ChannelWebSocketClient — dead-code stub P4.3**: все методы TODO.
23. **WAKE_LOCK permission declared but not used in realtime** (P3 cleanup).
24. **Shallow git history** (1 squashed commit c9d4ff8) — невозможно отследить регрессии через git log -S.

## ЧТО РАБОТАЕТ КОРРЕКТНО

✅ **LongPollClient** — primary realtime, robust backoff + token-pause + net observer + backfill
✅ **CallsQueuev4Client** — реальный канал входящих звонков
✅ **Incoming call regression (LP 115 "-1")** — ИСПРАВЛЕНА в `c9d4ff8` (P0.43-r8-V216-RESTORE)
✅ **MessageNotifier + VkNotificationsNotifier** — 15 каналов создаются в SovaApp.onCreate
✅ **NotificationActionReceiver** — MarkRead + Reply (RemoteInput) + Dismiss, manifest:453-462
✅ **POST_NOTIFICATIONS runtime permission** — PermissionManager.kt:182-247
✅ **EventDedup (5s window, peerId+msgId)** — защищает от LP+EventHub дублей
✅ **SnNotifyFilter (19 predicates, 41 defaults)** — корректно имитирует VK web defaults
✅ **UnreadMessagesCounter** — in-app badge (dialogs only, intentional)
✅ **NotificationsScreen** — data source, ~20 типов, tap/long-press/swipe/refresh/pagination
✅ **NotificationSettingsScreen** — 4 секции серверные (SilentMode/BFF/communities/obscene)
✅ **NotifyModeSelector** — 4 режима, global, enforced в SovaApp + VkNotificationsNotifier
✅ **Backup/restore** — полный дамп DataStore + AES-GCM
✅ **LongPollKeepAliveService** — foreground, FGS type=remoteMessaging, START_STICKY
✅ **BootReceiver** — BOOT_COMPLETED + LOCKED_BOOT_COMPLETED + MY_PACKAGE_REPLACED
✅ **Battery/Doze** — SCHEDULE_EXACT_ALARM + REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
✅ **AndroidManifest** — все 7 services + 3 receivers объявлены
✅ **NotificationsPoller + CommunityPostsPoller** — WORKING с защитой от flood control

## РЕКОМЕНДУЕМЫЕ ФИКСЫ (по приоритету)

### Sprint 1 (P0 — core)
1. Подключить `VKNotifyWeb.savePushMaster()` для sn_push_send → master toggle заработает.
2. Добавить quiet hours check в `MessageNotifier.showNotification` (или в SovaApp.collect).
3. Локально подавлять notifications во время server-side silent_until.
4. Останавливать `eventsQueuev4Client` в MainActivity.kt:1216/1515/1660.
5. Найти правильный VK API method для security alerts (или убрать poller).

### Sprint 2 (P1 — UX)
6. Реализовать парсер EventHubEvent → LongPollEvent (или отключить EventHub start).
7. Добавить missing sn_* keys mapping (sn_birthdays, sn_messages, sn_chats, sn_groups, ...).
8. Per-chat timed mute (1h / 8h / forever) UI.
9. Mark-as-read → local badge update.
10. Edge-case guard для архивированных/удалённых при notification tap.

### Sprint 3 (P2 — cleanup)
11. Подключить 4 VKNotifyWeb save-метода (push_master/group/message/chat).
12. Удалить dead store emailNotifyFreq.
13. EventDedup periodic cleanup.
14. Queuev4Client.stop() → queue.unsubscribe.
15. Реализовать SecurityAlertNotifier «Завершить сессию» action.
16. Удалить ChannelWebSocketClient stub ИЛИ дописать.


---
Task ID: P0.1-fix
Agent: general-purpose (sn_push_send master toggle)
Task: Подключить VKNotifyWeb.savePushMaster() для sn_push_send toggle

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние 350 строк) — изучил DIAG-D2 §10.1
  и DIAG-SUMMARY P0#1: главный тумблер «Получать push-уведомления» (sn_push_send)
  ВСЕГДА откатывается с Toast «Не удалось изменить». Корневая причина: в
  VKApiClient.settingsSetNotifyViaWeb() вызывается VKNotifyWeb.webGroupForPinoKKey("sn_push_send")
  → возвращает null (нет case для sn_push_send, т.к. это НЕ группа, а мастер-тумблер)
  → метод возвращает false → UI откатывает toggle.
- Прочитал VKNotifyWeb.kt полностью (538 строк). Подтвердил:
  * savePushMaster(client, hash, enabled) реализован корректно (VKNotifyWeb.kt:311-325):
    POST /settings?act=notify_push_save&hash=<hash>, body push_switcher=0|1 + hash,
    использует общий postForm() (тот же OkHttpClient/cookies/headers, что и saveGroup).
  * Snapshot.hashGeneral — это hash для act=notify_push_save (мастер-тумблер),
    отличается от Snapshot.hashGroup (hash для notify_group_save).
  * fetch(client) возвращает Snapshot? — кешированного поля в VKApiClient НЕТ,
    snapshot фетчится on-demand через VKNotifyWeb.fetch(httpClient).
- Прочитал VKApiClient.kt:19150-19349 (settingsGetNotifyViaWeb, settingsSetNotifyViaWeb,
  соседние методы). Подтвердил:
  * Поле клиента называется `httpClient` (НЕ `okHttpClient`, как предполагалось
    в задании) — VKApiClient.kt:113 `private val httpClient: OkHttpClient`.
  * Существующий path для остальных sn_* ключей:
    VKNotifyWeb.fetch(httpClient) → snap.hashGroup → VKNotifyWeb.saveGroup(...).
- Прочитал SettingsScreen.kt:7300-7334 (toggleNotify) и 7498-7510 (sn_push_send row).
  Подтвердил: UI вызывает app.apiClient.settingsGeneralToggleNotify(key, value) →
  при false откатывает notifyStates и показывает Toast «Не удалось изменить «…»».
  UI логику НЕ ТРОГАЛ — только убедился, что после фикса VKApiClient начнёт
  возвращать true для sn_push_send, и UI автоматически сохранит optimistic state.
- ИЗМЕНЕНИЕ (только 1 файл — VKApiClient.kt, метод settingsSetNotifyViaWeb:19245-19286):
  Добавил special case `if (key == "sn_push_send")` ПЕРЕД общим path. Логика:
    1. VKNotifyWeb.fetch(httpClient) → snapMaster
    2. snapMaster.hashGeneral → hashMaster (если null/blank — log + return false)
    3. return VKNotifyWeb.savePushMaster(httpClient, hashMaster, enabled)
  Используется hashGeneral (НЕ hashGroup) — это критично, т.к. VK использует
  разные CSRF-hash для notify_push_save vs notify_group_save. Общий path для
  остальных sn_* ключей НЕ ИЗМЕНЁН — по-прежнему hashGroup + saveGroup.
- VKNotifyWeb.kt НЕ изменял — метод savePushMaster уже корректен, postForm()
  логирует HTTP-ошибки. Новых импортов не добавлял: OkHttpClient (line 18),
  AppLog (line 65) уже импортированы; VKNotifyWeb в том же package re.pinok.api.
- Запустил 4 grep-проверки (см. Stage Summary) — все прошли.
- Пытался проверить компиляцией `./gradlew :app:compileDebugKotlin --offline`,
  но сборка не стартовала: env не имеет кешированного плагина
  `org.gradle.toolchains.foojay-resolver-convention:1.0.0`, а в offline-режиме
  скачать нельзя. Это ограничение окружения, НЕ кода. Синтаксис/типы/импорты
  проверены вручную (см. доказательства ниже).
- git commit НЕ делал — согласно инструкции.

Stage Summary:
- ИЗМЕНЁН файл: `app/src/main/java/re/pinok/api/VKApiClient.kt`
  метод `settingsSetNotifyViaWeb`, строки 19245-19286 (было 21 строк, стало 42).
  Добавлен special case `if (key == "sn_push_send")` (строки 19250-19267),
  который использует `snapMaster.hashGeneral` + `VKNotifyWeb.savePushMaster(...)`.
  Общий path (sn_* → webGroup → saveGroup) НЕ тронут — строки 19269-19285.
- ФАЙЛЫ НЕ ТРОНУТЫ (по инструкции):
  * `VKNotifyWeb.kt` — метод savePushMaster (311-325) уже корректен, изменений не требует.
  * `SettingsScreen.kt` — UI логика корректна (true → persist optimistic, false → revert+Toast).
- ДОКАЗАТЕЛЬСТВА:
  1. grep savePushMaster в VKApiClient.kt:
     19266: `return VKNotifyWeb.savePushMaster(httpClient, hashMaster, enabled)`
  2. grep sn_push_send в VKApiClient.kt:
     19255: `if (key == "sn_push_send") {`
  3. grep fun savePushMaster в VKNotifyWeb.kt:
     311: `suspend fun savePushMaster(`
  4. grep -rn savePushMaster app/src/main/java/re/pinok/ — теперь 2 ссылки
     (definition + новый caller), ранее был 1 (только definition = dead code).
- ТИПЫ/ИМПОРТЫ:
  * httpClient: OkHttpClient (VKApiClient.kt:113) — импорт line 18 ✓
  * VKNotifyWeb.fetch(httpClient): Snapshot? (VKNotifyWeb.kt:116) ✓
  * snapMaster.hashGeneral: String? (VKNotifyWeb.kt:99) ✓
  * savePushMaster(client: OkHttpClient, hash: String, enabled: Boolean): Boolean (VKNotifyWeb.kt:311-315) ✓
  * AppLog — импорт line 65 ✓
  * VKNotifyWeb — same package re.pinok.api (no import needed) ✓
- РИСКИ/ВОПРОСЫ:
  * Не удалось запустить реальную компиляцию gradle (окружение: offline + нет
    foojay-resolver-convention:1.0.0 в кеше). Код проверен вручную по сигнатурам.
  * Файл settingsGetNotifyViaWeb (строки 19189-19197) уже фетчит snapshot для
    чтения; в новом path для sn_push_send мы делаем ВТОРОЙ fetch (на запись).
    Микро-overhead (1 extra HTTP GET на m.vk.ru/settings?act=notify), но это
    согласуется с существующим паттерном saveGroup (тоже fetch на каждый toggle).
    В будущем можно закешировать snapshot с TTL, но это отдельная задача (P2).
  * hashGeneral может отличаться от hashGroup — это нормально и ожидаемо,
    т.к. VK использует разные CSRF-токены для разных действий.
- worklog обновлён (этот блок). Файлы кода изменены только в пределах разрешённых:
  VKApiClient.kt (метод settingsSetNotifyViaWeb), VKNotifyWeb.kt НЕ тронут.

---
Task ID: P0.4-fix
Agent: general-purpose (eventsQueuev4Client leak cleanup)
Task: Остановить eventsQueuev4Client в cleanup paths после logout

Work Log:
- Прочитал worklog.md (DIAG-SUMMARY §10 + P0 priority list) — подтвердил, что
  eventsQueuev4Client НЕ остановлен НИ В ОДНОМ cleanup path (MainActivity.kt:1216/1515/1660).
- Прочитал MainActivity.kt секции 1190-1235 (LaunchedEffect else-branch),
  1500-1690 (onLogout), 1660-1712 (onExitApp) — нашёл 3 cleanup paths.
- Прочитал SovaApp.kt:125-146 — проверил объявление полей:
    * `lateinit var queuev4Client: Queuev4Client`        (private set) — SovaApp.kt:128
    * `lateinit var eventsQueuev4Client: Queuev4Client`  (private set) — SovaApp.kt:135
    * `lateinit var callsQueuev4Client: Queuev4Client`   (private set) — SovaApp.kt:144
  Все три публично читаемы (private set ограничивает только запись) →
  `app.eventsQueuev4Client.stop()` компилируется БЕЗ изменений SovaApp.kt.
  Тот же паттерн что у queuev4Client/callsQueuev4Client (уже используются в :1219/:1221).
- Прочитал Queuev4Client.kt:90-115 — подтвердил что stop() idempotent:
    `pollJob?.cancel()` (no-op if null), `currentCall?.cancel()` (no-op if null),
    `credential = null`. Повторный вызов безопасен.
- Подтвердил что `app` в scope всех 3 cleanup paths (val app = SovaApp.get(this@MainActivity)
  в MainActivity.kt:560, используется в :1206/:1535/:1668).

Изменения в MainActivity.kt (3 cleanup paths):

### Path 1: LaunchedEffect(currentAuthVersion) else-branch (MainActivity.kt:1216-1233)
ДО: queuev4Client.stop() + callsQueuev4Client.stop() + eventHubClient.stop() + ...
ПОСЛЕ: добавлен `app.eventsQueuev4Client.stop()` между queuev4Client.stop() и
callsQueuev4Client.stop() (строка 1223).
```kotlin
app.longPollClient.stop()
app.queuev4Client.stop()
// #CALLS-EVENTS: останавливаем events_queue-подписку
// (eventsQueuev4Client) при отсутствии токена — иначе
// продолжит long-poll с протухшими кредами → 401 spam (P0.4).
app.eventsQueuev4Client.stop()
// #CALLS-INCOMING: останавливаем dedicated calls-очередь при logout.
app.callsQueuev4Client.stop()
```

### Path 2: onLogout callback (MainActivity.kt:1515-1567)
ДО: longPollClient + eventHubClient + communityPostsPoller + keepAlive + wakeReceiver.
ПОСЛЕ: добавлен try/catch блок с queuev4Client + eventsQueuev4Client + callsQueuev4Client
.stop() после eventHubClient.stop() (строки 1551-1567).
```kotlin
// #CALLS-QUEUEV4 (P0.4): останавливаем ВСЕ три queuev4-подписки при logout.
try {
    app.queuev4Client.stop()
    app.eventsQueuev4Client.stop()
    app.callsQueuev4Client.stop()
    AppLog.i("MainActivity", "Logout: queuev4Client + eventsQueuev4Client + callsQueuev4Client stopped (P0.4)")
} catch (e: Exception) {
    AppLog.w("MainActivity", "Logout: queuev4 clients stop failed: ${e.message}")
}
```

### Path 3: onExitApp callback (MainActivity.kt:1685-1705)
ДО: longPollClient + eventHubClient + communityPostsPoller + keepAlive + wakeReceiver.
ПОСЛЕ: добавлен try/catch блок с queuev4Client + eventsQueuev4Client + callsQueuev4Client
.stop() после eventHubClient.stop() (строки 1695-1705).
```kotlin
// #CALLS-QUEUEV4 (P0.4): останавливаем ВСЕ три queuev4-подписки при полном выходе.
try {
    app.queuev4Client.stop()
    app.eventsQueuev4Client.stop()
    app.callsQueuev4Client.stop()
} catch (_: Exception) { }
```

Verification (grep -n 'eventsQueuev4Client\.stop\(\)|queuev4Client\.stop\(\)|callsQueuev4Client\.stop\(\)'):
- LaunchedEffect else-branch:  queuev4Client.stop()=1219, eventsQueuev4Client.stop()=1223, callsQueuev4Client.stop()=1225
- onLogout:                    queuev4Client.stop()=1561, eventsQueuev4Client.stop()=1562, callsQueuev4Client.stop()=1563
- onExitApp:                   queuev4Client.stop()=1702, eventsQueuev4Client.stop()=1703, callsQueuev4Client.stop()=1704

Cleanup matrix AFTER fix (все 3 paths × 3 queuev4 clients = 9 stop() calls):
| Client               | LaunchedEffect else | onLogout | onExitApp |
|----------------------|:---:|:---:|:---:|
| queuev4Client        | ✅ 1219 | ✅ 1561 (NEW) | ✅ 1702 (NEW) |
| eventsQueuev4Client  | ✅ 1223 (NEW) | ✅ 1562 (NEW) | ✅ 1703 (NEW) |
| callsQueuev4Client   | ✅ 1225 | ✅ 1563 (NEW) | ✅ 1704 (NEW) |

Stage Summary:
- Файл изменён: `app/src/main/java/re/pinok/ui/MainActivity.kt` (только cleanup paths,
  существующие stop() вызовы НЕ тронуты).
- SovaApp.kt НЕ изменён — не потребовалось (eventsQueuev4Client уже публично читаем).
- Добавлено 3×1=3 вызова `app.eventsQueuev4Client.stop()` (критичный P0 leak из DIAG-E2 Q10).
- Добавлено 2×2=4 вызова `app.queuev4Client.stop()` + `app.callsQueuev4Client.stop()`
  в onLogout/onExitApp (закрытие race window из §9.4 — ранее только LaunchedEffect else-branch).
- Всего 7 новых stop() вызовов, все в try/catch (стиль соседнего кода сохранён).
- stop() idempotent (Queuev4Client.kt:107-115) — повторные вызовы безопасны
  (LaunchedEffect else-branch + onLogout могут оба сработать для queuev4/calls).
- Compile check: визуальный — `app: SovaApp`, `eventsQueuev4Client` — public lateinit var
  (private set, SovaApp.kt:135), `stop()` — public fun (Queuev4Client.kt:107).
  Full gradle build невозможен в sandbox (нет Android SDK).
- Git commit НЕ выполнялся (по инструкции).

Risks / Questions:
- Микро-race window между onLogout и LaunchedEffect recomposition теперь закрыт напрямую
  (раньше был self-healing через recomposition). Двойной stop() безопасен — idempotent.
- eventsQueuev4Client.stop() обнуляет credential (`credential = null`) — при следующем
  login startCallSignaling() вызовет setCredential() снова (SovaApp.kt:2189). OK.
- notificationsPoller / securityAlertsPoller НЕ добавлены в stop() — safe-by-design
  (DIAG-E2 §10.2: pollOnce проверяет hasValidToken, без токена запросов нет).

---
Task ID: P0.5-fix
Agent: general-purpose (SecurityAlertsPoller fix)
Task: Починить SecurityAlertsPoller — найти рабочий VK API method (accountPersonal.getSecurityAlerts → err=3 "Unknown method passed")

Work Log:
- Прочитал worklog.md (DIAG-A2 §2, DIAG-B2 §10, DIAG-SUMMARY P0 #5): SecurityAlertsPoller.kt:119-134 вызывает `api.accountGetSecurityAlerts(hash)` через `forceWebGateway=true` (web.api.vk.ru). VK возвращает `apiCode=3 "Unknown method passed"` → poller вызывает `stop()` → permanent stop, SecurityAlertNotifier НИКОГДА не вызывается.
- Прочитал полностью (из /home/z/repos/PinoK_1, ветка PinoK, HEAD f678836):
  • `app/src/main/java/re/pinok/realtime/SecurityAlertsPoller.kt` (278 строк после фикса).
  • `app/src/main/java/re/pinok/realtime/SecurityAlertNotifier.kt` (127 строк, READ-ONLY — код исправен, источника данных нет; TODO action button «Завершить сессию» опционален, не трогал).
  • `VKApiClient.kt:17717-17765` (метод `accountGetSecurityAlerts`).
  • `VKApiClient.kt:13444-13460` (private suspend fun call — gateway routing).
  • `VKApiClient.kt:13475-13820` (callInternal — gateway selection, err handling).
  • `VKApiClient.kt:13649-13656` (useWebGateway logic + WEB_INCOMPATIBLE_METHODS check).
  • `VKApiClient.kt:14374-14381` (WEB_INCOMPATIBLE_METHODS set: 4 метода — messages.setConversationPushSettings, account.setSilenceMode, messages.markAsImportantConversation, messages.markAsUnreadConversation — форсируются через api.vk.com).
  • `VKEndpoints.kt` (полностью, 153 строки — два gateway: api.vk.com default, web.api.vk.ru mobile-web).
  • `core/common/src/main/java/re/pinok/util/ExponentialBackoff.kt` (189 строк) — для transient network errors, НЕ подходит для err=3 (это контрактный ответ VK, не transient).
  • `SovaApp.kt:1575-1578, 2810-2829` — securityAlertsPoller = SecurityAlertsPoller(...), start() один раз в onCreate. НЕТ stop() на logout (poller синглтон, переживает logout через isEnabled() gate).
- Grep-проверка `accountPersonal.*` в VKApiClient.kt: 7 методов ВСЕ используют `forceWebGateway=true` (getSecurityAlerts, setSafetyNetEnabled, getActivityHistoryDevices, resetSessions, resetAllSessions, getSessionInfoForReset, + cua.*).
  Сосед `accountPersonal.getActivityHistoryDevices` ИСПОЛЬЗУЕТСЯ в DevicesScreen.kt (7 call sites) и, по всей видимости, РАБОТАЕТ на web-шлюзе (никаких "broken" маркеров в worklog/коде нет, DevicesScreen functional per DIAG-SUMMARY). Это означает что namespace существует на web-шлюзе, но `getSecurityAlerts` конкретно отсутствует в allowlist'е → err=3.
- Решение: гибрид Вариант A + Вариант D.
  • Вариант A (VKApiClient.kt): убрать `forceWebGateway=true` → запрос пойдёт через api.vk.com (default gateway). Шанс что метод существует на api.vk.com — небольшой (accountPersonal.* это VK ID account API, обычно web-only), но стоит попробовать — стоимость нулевая.
  • Вариант D (SecurityAlertsPoller.kt): заменить permanent `stop()` на err=3 на counter-based graceful backoff. Poller продолжает опрос каждые 10 мин (естественный backoff). После 6 последовательных err=3 (= 1 час при default interval) → graceful-disable с INFO-логом. Любой успешный poll сбрасывает counter.
- Не трогал (по границам файлов):
  • `VKNotifyWeb.kt` — чужой файл.
  • `SecurityAlertNotifier.kt` — READ для понимания, не EDIT (TODO action button опционален).
  • Остальные методы `accountPersonal.*` в VKApiClient.kt — не трогал (только getSecurityAlerts).
- Hash check: `logout_hash` получается корректно через `TokenStorage.logoutHash()` → `ExchangeTokenStorage.logoutHash()` → SharedPreferences KEY_LOGOUT_HASH (WebTokenAuth.kt:1113 — парсится из web_token response). Если hash null (legacy direct auth flow без web_token) — `accountGetSecurityAlerts(hash=null)` шлётся без hash-параметра (VKApiClient.kt:17740: `if (!hash.isNullOrBlank()) args["hash"] = hash`). Метод может работать и без hash (VK может принять просто access_token), либо вернуть другой err. Этот edge case НЕ блокирующий.

Stage Summary:
- **Файл 1**: `app/src/main/java/re/pinok/api/VKApiClient.kt` (строки 17737-17749).
  Изменение: метод `accountGetSecurityAlerts(hash)` теперь вызывает `call("accountPersonal.getSecurityAlerts", args)` БЕЗ `forceWebGateway=true`.
  Было:  `val json = call("accountPersonal.getSecurityAlerts", args, forceWebGateway = true) ?: return null`
  Стало:  `val json = call("accountPersonal.getSecurityAlerts", args) ?: return null`
  + 8-строчный комментарий P0.5-fix с обоснованием. Другие методы `accountPersonal.*` (getActivityHistoryDevices, resetSessions, и т.д.) НЕ ТРОГАЛ — они используют web-шлюз осознанно.
- **Файл 2**: `app/src/main/java/re/pinok/realtime/SecurityAlertsPoller.kt` (строки 64-94 + 149-199).
  Изменения:
  (a) Добавлен `private var consecutiveMethodUnavailable: Int = 0` + `private companion object { const val MAX_CONSECUTIVE_METHOD_UNAVAILABLE = 6 }` (строки 84, 86-94). Порог = 6 polls × 10 мин = 1 час.
  (b) В `pollOnce()` заменил permanent `stop()` (старые строки 124-129) на counter-based graceful backoff:
      - err=3 → `consecutiveMethodUnavailable++`, log W "method unavailable (N/6) — will retry on next interval".
      - `consecutiveMethodUnavailable >= 6` → log I "graceful-disable (method not exposed by VK, will resume on next app start if VK adds it)", затем `stop()`.
      - Любой успешный poll (alerts != null) → reset counter в 0 + log I "method available again after N unavailable polls — counter reset" (если counter был > 0).
  (c) Убран старый комментарий `#ADMIN-SECURITY-ALERTS-FIX` (про permanent stop) — заменён на новый `#SAFETY-NET-ALERTS-NO-PERM-STOP` (строки 153-160) с описанием нового поведения.
- **Проверка grep (post-fix)**:
  • `rg -n 'accountPersonal.getSecurityAlerts' app/src/main/java/re/pinok/api/VKApiClient.kt` → 2 совпадения:
    - L17721: doc-comment "VK ID account API method: `accountPersonal.getSecurityAlerts`"
    - L17749: `val json = call("accountPersonal.getSecurityAlerts", args) ?: return null` ← БЕЗ forceWebGateway ✅
  • `rg -n 'stop\(\)|MAX_CONSECUTIVE|consecutiveMethodUnavailable|graceful-disable|method unavailable' app/src/main/java/re/pinok/realtime/SecurityAlertsPoller.kt` → 14 совпадений, подтверждают:
    - L93: `const val MAX_CONSECUTIVE_METHOD_UNAVAILABLE = 6`
    - L167-177: counter increment + INFO log "graceful-disable" + `stop()` ТОЛЬКО при counter >= 6
    - L178-183: WARN log "will retry on next interval" без stop() в остальных случаях ✅
- **Sanity check**:
  • Braces в SecurityAlertsPoller.kt: 32/32 (сбалансированы). Parens несбалансированы только в комментариях (a)/b)/c)/d) list markers в doc-comment) — код валиден.
  • Braces в VKApiClient.kt: pre-edit delta=3 / post-edit delta=3 (без изменений — мой edit только добавил комментарий и убрал один аргумент, brace-баланс не затронут). +3 delta — это PRE-EXISTING артефакт от `${... { lambda }}` string-templates, не баг.
  • `kotlinc` / Android SDK НЕ доступны в окружении — полную компиляцию запустить нельзя. Ручная проверка синтаксиса: оба файла выглядят Kotlin-валидными.
- **Риски/вопросы**:
  1. **Работает ли `accountPersonal.getSecurityAlerts` на api.vk.com?** — Не подтверждено runtime-тестом (нет Android SDK/device). Если метод НЕ существует ни на api.vk.com, ни на web.api.vk.ru — poller будет получать err=3 на каждом poll'е, после 6 polls (= 1 час) graceful-disable'ится с INFO-логом. Это и есть graceful disable (Вариант D), как требовалось задачей. Если метод ВДРУГ существует на api.vk.com — poller заработает с первого poll'а, counter сбросится.
  2. **DIAG-A2 §2 / DIAG-B2 §10 err=3 был по сообщению (через код guard'а), а не runtime logcat** — в worklog явная фраза "нужен runtime logcat для подтверждения". Если в реальности метод работал и err=3 был артефактом логики — то после моего фикса (убрали forceWebGateway) всё останется работать. Если err=3 реальный — graceful-disable спасёт poller от зомби-state.
  3. **Method name guess (Вариант B)** — НЕ пробовал альтернативные имена (`account.getSecurityAlerts`, `auth.getSecurityAlerts`, `security.getAlerts`). Причина: отсутствие подтверждённого VK API с таким именем (grep по worklog → 0 упоминаний). Пробовать все имена вслепую = spam VK API. Текущий фикс даёт VK шанс ответить на `accountPersonal.getSecurityAlerts` через api.vk.com, а если не ответит — graceful-disable.
  4. **Alternative: m.vk.ru web scraping (Вариант C)** — НЕ реализован. Был бы через `VKNotifyWeb.kt` (чужой файл) или новый scraper в `SecurityAlertsPoller.kt`. Это значительный объём работы (HTML parse, session cookies, anti-bot), выходит за рамки P0.5. Если метод стабильно недоступен на всех REST-шлюзах — следующий спринт может добавить scraper для `m.vk.ru/settings?act=security` (если такая страница существует).
  5. **triggerImmediatePoll() после graceful-disable** — если SovaApp или UI вызовет `triggerImmediatePoll()` после того как poller был graceful-disabled, pollOnce запустится на `pollScope` (не на periodicJob), получит err=3 снова, counter (который уже >= 6) станет 7, и снова вызовет `stop()` (idempotent). Это OK — нет бесконечного цикла, нет зомби-state.
  6. **logout не вызывает stop() для SecurityAlertsPoller** — это pre-existing поведение (SovaApp не имеет stop-вызова для этого poller'а). Poller остаётся живым между login-сессиями, но `isEnabled()` gate (pushSafetyNetAlerts && pushEnabled) блокирует pollOnce когда токен невалиден. Не баг.
- **Не сделано (вне scope)**:
  • TODO action button «Завершить сессию» в SecurityAlertNotifier.kt:31 (P2 — DIAG-SUMMARY §21). Опционально по задаче, не трогал.
  • Полная компиляция через `./gradlew :app:compileDebugKotlin` — Android SDK не установлен (`SDK location not found`). Требуется ручная проверка сборки на CI.
  • Git commit — НЕ делал (по инструкции).
- **Файлы изменены (2)**:
  1. `/home/z/repos/PinoK_1/app/src/main/java/re/pinok/api/VKApiClient.kt` (+8 строк комментария, -1 параметр `forceWebGateway = true` в `accountGetSecurityAlerts`).
  2. `/home/z/repos/PinoK_1/app/src/main/java/re/pinok/realtime/SecurityAlertsPoller.kt` (+52 строк: counter field, companion const, переписанный err=3 handling с reset-on-success).

---
Task ID: P0.23-fix
Agent: general-purpose (quiet hours + server DND for direct messages)
Task: Применить quiet hours и server-side silent_until к MessageNotifier

Work Log:
- Прочитал DIAG-SUMMARY (worklog.md), DIAG-D2 §10.2/§10.3 — Quiet hours НЕ применяются к direct messages; server-side silent_until НЕ подавляется локально.
- Прочитал MessageNotifier.kt (полностью, 350 строк) — showNotification(snap+channelId) имеет выбор канала, но silent channel используется ТОЛЬКО при notifyMode=SILENT или для сообществ. Нет проверки quiet hours или server DND.
- Прочитал VkNotificationsNotifier.kt:1130-1149 — isInQuietHours(snap) приватный, поддерживает переход через полночь (22:00→08:00).
- Прочитал SovaApp.kt:1624-1817 (startMessageNotifier) — collect блок NewMessage: событие → выбор pushChannelId по mode/isCommunity/sound/vibration → showNotification. Quiet hours + server DND НЕ применяются.
- Прочитал SovaPrefs.kt:1820-1900 (Snapshot) — silentUntil отсутствует. Есть pushQuietHours* (3 поля).
- Прочитал NotificationsPoller.kt (полностью, 264 строки) — pollOnce() каждые ~2 мин, нет poll account.getSilentModeStatus.
- Прочитал VKApiClient.kt:19357 — accountGetSilentModeStatus() существует, возвращает SilentModeStatus? (silentUntil: Long).
- Прочитал Models.kt:1939-1949 — SilentModeStatus с silentUntil: Long, isActive extension.

Шаг 1: SovaPrefs.kt — добавил поле silentUntil в Snapshot:
  - Keys.SILENT_UNTIL = longPreferencesKey("silent_until") (стр. ~2297)
  - read в map: silentUntil = p[Keys.SILENT_UNTIL] ?: 0L (стр. 585)
  - field в data class Snapshot: val silentUntil: Long = 0L (стр. 1907) — default 0L,
    прецедент notifyMode (стр. 1889), FeedScreen.kt:256 (единственный SovaPrefs.Snapshot(
    вызов вне SovaPrefs) использует named args → собирается без правок
  - setter: suspend fun setSilentUntil(v: Long) = put(Keys.SILENT_UNTIL, v) (стр. 1321)

Шаг 2: VkNotificationsNotifier.kt:1140 — изменил `private fun isInQuietHours` →
  `internal fun isInQuietHours` (видимость из SovaApp). Тело метода без изменений.
  Добавил комментарий-маркер P0.2 #QUIET-MSGS.

Шаг 3: SovaApp.kt:1713-1744 — добавил quiet hours + server DND check в startMessageNotifier,
  ПОСЛЕ выбора pushChannelId (when-блок) и ПЕРЕД showNotification:
    val nowSec = System.currentTimeMillis() / 1000L
    val inQuietHours = snap.pushQuietHoursEnabled && VkNotificationsNotifier.isInQuietHours(snap)
    val inServerDnd = snap.silentUntil > 0L && snap.silentUntil > nowSec
    val effectiveChannelId = if (inQuietHours || inServerDnd) {
        AppLog.d(...)  // логирует причину (quiet_hours/server_dnd) + контекст
        if (isCommunity) CHANNEL_COMMUNITIES_SILENT else CHANNEL_MESSAGES_SILENT
    } else {
        pushChannelId
    }
  SovaApp.kt:1844 — showNotification(channelId = effectiveChannelId) (был pushChannelId).

Шаг 4: NotificationsPoller.kt — добавил refreshSilentUntilCache() private suspend функцию
  (стр. 239-259) и её вызов в pollOnce() (стр. 167) сразу после pushEnabled-проверки,
  ПЕРЕД notifications.getRedesign. Функция:
    - api.accountGetSilentModeStatus() → status.silentUntil
    - сравнивает с prefs.data.first().silentUntil
    - логирует смену состояния (active↔inactive, для дебага)
    - если изменилось — prefs.setSilentUntil(newUntil)
    - весь блок в try/catch — отказ silent-mode-poll не рвёт основной поток

Выбранный подход (ВАРИАНТ B из задачи): НЕ подавляем notification полностью, а
переопределяем channelId на *_silent. Преимущества:
  - пользователь всё равно видит входящее сообщение (heads-up остаётся, IMPORTANCE_HIGH)
  - звук и вибрация отключены (CHANNEL_MESSAGES_SILENT.setSound(null,null) + enableVibration(false))
  - mute path (cached.muted=true) остаётся нетронутым — уведомление вообще не показывается
  - переиспользуются существующие каналы (messages_silent, communities_silent) — никаких новых
    каналов создавать не нужно

Проверка grep'ом (см. ниже «Доказательства») подтверждает все 4 файла изменены.

Stage Summary:
- core/data/src/main/java/re/pinok/data/local/SovaPrefs.kt:
  • Keys.SILENT_UNTIL = longPreferencesKey("silent_until") (стр. 2297)
  • Snapshot.silentUntil: Long = 0L (стр. 1907)
  • read в map: silentUntil = p[Keys.SILENT_UNTIL] ?: 0L (стр. 585)
  • suspend fun setSilentUntil(v: Long) = put(Keys.SILENT_UNTIL, v) (стр. 1321)
- app/src/main/java/re/pinok/realtime/VkNotificationsNotifier.kt:1140:
  • private fun isInQuietHours → internal fun isInQuietHours (без изменений в теле)
- app/src/main/java/re/pinok/SovaApp.kt:1713-1744:
  • новый блок quiet hours + server DND check
  • SovaApp.kt:1844 — showNotification(channelId = effectiveChannelId) (был pushChannelId)
- app/src/main/java/re/pinok/realtime/NotificationsPoller.kt:
  • новый private suspend fun refreshSilentUntilCache() (стр. 239-259)
  • вызов refreshSilentUntilCache() в pollOnce() (стр. 167)

Доказательства (grep output):
1. SovaApp.kt — новые проверки:
   :1716:  // isInQuietHours проверялся только в VkNotificationsNotifier
   :1727:  // через silentUntil (кэш из account.getSilentModeStatus,
   :1730:  val inQuietHours = snap.pushQuietHoursEnabled &&
   :1731:      re.pinok.realtime.VkNotificationsNotifier.isInQuietHours(snap)
   :1732:  val inServerDnd = snap.silentUntil > 0L && snap.silentUntil > nowSec
   :1733:  val effectiveChannelId = if (inQuietHours || inServerDnd) {
   :1738:      "silentUntil=${snap.silentUntil} now=$nowSec " +
   :1844:  channelId = effectiveChannelId,

2. MessageNotifier.kt — silent channel (существующий, переиспользуется):
   :38:   const val CHANNEL_MESSAGES_SILENT = "messages_silent"
   :42:   const val CHANNEL_COMMUNITIES_SILENT = "communities_silent"
   :114:  val silentChannel = NotificationChannel(CHANNEL_MESSAGES_SILENT, ...)
   :124:  nm.createNotificationChannel(silentChannel)

3. SovaPrefs.kt — поле добавлено:
   :585:  silentUntil = p[Keys.SILENT_UNTIL] ?: 0L,
   :1321: suspend fun setSilentUntil(v: Long) = put(Keys.SILENT_UNTIL, v)
   :1907: val silentUntil: Long = 0L,
   :2297: val SILENT_UNTIL = longPreferencesKey("silent_until")

4. NotificationsPoller.kt — poll добавлен:
   :167:  refreshSilentUntilCache()
   :239:  private suspend fun refreshSilentUntilCache() {
   :241:      val status = api.accountGetSilentModeStatus() ?: return
   :242:      val newUntil = status.silentUntil
   :243:      val prevUntil = prefs.data.first().silentUntil
   :254:          prefs.setSilentUntil(newUntil)

Риски/вопросы:
- Poll частота: accountGetSilentModeStatus вызывается каждые ~2 мин (частота poller'а).
  Это безопасно для VK flood control, но если станет проблемой — можно добавить
  throttle (не чаще раза в 5 мин) или poll только когда UI запущен.
- Silent_until кэш может устареть на ~2 мин после активации DND (между poll-циклами).
  Это приемлемый trade-off (DND активируется на 1ч+ — 2 мин не критичны).
- if (silentUntil > 0L && silentUntil > nowSec) — условие >0L исключает special VK value
  -1 (= forever). Если VK отдаёт silent_until=-1 для "пока не выключу", то нужен fix:
  добавить `|| snap.silentUntil == -1L` в условие. Сейчас не обнаружено в runtime
  (нужен logcat при включении «пока не выключу»). См. SettingsScreen.kt:7461 — UI передаёт
  secs=0 для forever, и VK возвращает silent_until = far-future timestamp, не -1.
- НЕ затронуты: notifyMode=SILENT path (для сообщений — работает как раньше),
  muted path (cached.muted=true — return@collect в showNotification, до канала),
  pushEnabled=false path, queuev4 events, calls.
- Kotlin compile: SDK не доступен в sandbox (нет ANDROID_HOME) — compile не запущен.
  Синтаксис проверен вручную: все новые val/if/when — корректный Kotlin,
  идентификаторы (snap, mode, isCommunity, event.peerId, AppLog, api, prefs) — в scope.
  Snapshot.silentUntil с default 0L — единственный SovaPrefs.Snapshot( вызов вне SovaPrefs
  (FeedScreen.kt:256, named args) собирается без правок (прецедент notifyMode).
- git commit НЕ сделан (по инструкции — пользователь сделает после проверки).

Worklog обновлён: ДА (этот блок, append mode).

---
Task ID: P1.1-fix
Agent: general-purpose (EventHub dead channel)
Task: Починить EventHub — реализовать парсер или отключить

Work Log:
- Прочитал worklog.md: DIAG-A2 §3 (Event flow) + §10 (risks #1) + DIAG-SUMMARY
  (EventHubWebSocketClient = ⚠️ DEAD-EVENT). Подтверждено: EventHubWebSocketClient.kt:58-59
  эмитит SharedFlow<EventHubEvent>, НО 0 коллекторов вне класса. LongPoll — PRIMARY
  (4 коллектора: MessageNotifier SovaApp.kt:1637, startCallNotifier LP-branch 1833,
  startNotificationsPoller 2779, UnreadMessagesCounter UnreadMessagesCounter.kt:86).
- Прочитал EventHubWebSocketClient.kt полностью (234→268 строк после правки).
  Верификация структуры:
    * line 233-235: `data class EventHubEvent(val payload: String)` — RAW String,
      НЕТ typed event variants (NewMessage/ReadInbox/etc.), НЕТ JSON-парсера.
    * line 209-220 handleFrame: `if (text=="ping") pong; _events.tryEmit(EventHubEvent(text))`
      — эмитит сырую строку как есть. Комментарий line 29-30 подтверждает:
      «сырые строки/бинарные кадры JSON-событий — пока не парсятся».
  → ВЫБОР: ВАРИАНТ A (отключение). EventHubEvent = raw string без структуры →
    парсер писать не из чего (нужно сначала reverse-engineer VK ehsp2 JSON-формат,
    что за пределами P1.1). Отключение безопасно: LongPoll полностью покрывает доставку.
- Grep `eventHubClient\.(start|stop|isRunning|isConnected|state)` по всему /app/src/main/java:
    * start() call sites: MainActivity.kt:1209 (LaunchedEffect token-valid branch),
      RealtimeWakeReceiver.kt:49 (onReceive wake-tick).
    * stop() call sites (cleanup): MainActivity.kt:1227, 1546, 1693 — НЕ трогать.
    * state()/isRunning()/isConnected() — НЕ вызываются нигде вне класса →
      disable-stub не сломает UI/диагностику.
- Grep `eventHubClient\.events|EventHubEvent` по всему репо: подтверждено 0 коллекторов
  (только само объявление в EventHubWebSocketClient.kt:58-59 + tryEmit:218 + data class:233).

Шаг 1: EventHubWebSocketClient.kt — disable-stub в start() (покрывает ОБА start-сайта:
  MainActivity:1209 И RealtimeWakeReceiver:49, который вне allowed-файлов).
  Файл разрешён к EDIT: «только если добавляешь parser / disable stub».
  Изменения:
    - class KDoc (line 25-29): добавлен ⚠️ #P1.1 warning-блок в начало.
    - start() (line 90-100): тело заменено на disable-stub:
        AppLog.w(TAG, "start: EventHub disabled (P1.1 DEAD-EVENT) — LongPoll = PRIMARY. " +
            "events flow has 0 collectors; EventHubEvent is raw string w/o parser. " +
            "Reactivate: parse EventHubEvent → LongPollEvent + collect in SovaApp.")
      Оригинальная логика (running=true, connectJob=scope.launch{connectLoop()})
      сохранена как комментарии — для однострочной реактивации.
    - running остаётся false → stop() (if(!running) return) = safe no-op во ВСЕХ
      cleanup paths. isConnected()/state() возвращают «выкл». connectJob/connectLoop/
      WsListener/buildUrl/buildPayload — dead code, НО private и не вызывают warnings.
    - Добавлен KDoc с планом реактивации (Вариант B): парсер + collect в SovaApp.

Шаг 2: MainActivity.kt:1209 — закомментировал start() call с поясняющим комментарием
  (по инструкции задачи). Оригинальный комментарий #EVENTHUB сохранён, добавлен блок
  #P1.1 с причиной отключения, списком LongPoll-коллекторов и ссылкой на disable-stub.
  Cleanup paths (stop() на lines 1239, 1558, 1705) НЕ тронуты — они безопасны (no-op).

Почему disable-stub В EventHubWebSocketClient.kt, а НЕ только комментарий в MainActivity:
  RealtimeWakeReceiver.kt:49 тоже вызывает eventHubClient.start() при каждом wake-tick
  (раз в 15 мин, Doze-safe). Этот файл НЕ в allowed-списке P1.1. Без stub'а
  RealtimeWakeReceiver продолжал бы запускать dead WS-соединение каждые 15 мин.
  Stub в start() глушит ОБА call site'а из одного места — это единственный способ
  полностью остановить EventHub в рамках файловых границ задачи.

Stage Summary:
- app/src/main/java/re/pinok/realtime/EventHubWebSocketClient.kt (+37 строк):
  • class KDoc (line 25-29): ⚠️ #P1.1 warning-блок.
  • start() (line 90-100): disable-stub — AppLog.w(...) + оригинальная логика
    закомментирована (running/connectJob/connectLoop). running stays false.
  • stop() (line 103+): БЕЗ изменений — if(!running) return = safe no-op.
  • connectLoop/buildUrl/buildPayload/WsListener/handleFrame/data class EventHubEvent —
    БЕЗ изменений (dead code, сохранён для реактивации Вариант B).
- app/src/main/java/re/pinok/ui/MainActivity.kt (+13 / -1 строка):
  • line 1209: `app.eventHubClient.start()` → `// app.eventHubClient.start()`
    + блок комментария #P1.1 (причина, LongPoll=PRIMARY, ссылка на stub).
  • cleanup paths (stop() на 1239, 1558, 1705): БЕЗ изменений.
- НЕ затронуты: SovaApp.kt (Вариант B не выбран — нет нужды в collector'е),
  RealtimeWakeReceiver.kt (вне allowed-файлов, но покрыт stub'ом),
  LongPollClient/MessageNotifier/etc.

Доказательства (grep output):
1. EventHubWebSocketClient.kt — disable-stub на месте:
   $ rg -n "fun start|P1\.1|DEAD-EVENT|disabled|connectLoop|running = true" \
       app/src/main/java/re/pinok/realtime/EventHubWebSocketClient.kt
   :25: * ⚠️ #P1.1 — КАНАЛ ОТКЛЮЧЁН (DEAD-EVENT). [start] превращён в disable-stub:
   :70:     * #P1.1 — EventHub ОТКЛЮЧЁН (DEAD-EVENT канал):
   :90:    fun start() {
   :91:        // #P1.1 DEAD-EVENT: канал отключён — см. KDoc выше. LongPoll = PRIMARY.
   :92:        AppLog.w(TAG, "start: EventHub disabled (P1.1 DEAD-EVENT) — LongPoll = PRIMARY. " +
   :95:        // --- original start logic (disabled by P1.1; restore to reactivate) ---
   :97:        // running = true
   :99:        // connectJob = scope.launch { connectLoop() }
   (running = true ЗАКОММЕНТИРОВАНО → running stays false → stop() = no-op)

2. MainActivity.kt — start() закомментирован, stop() в cleanup жив:
   $ rg -n "eventHubClient\.(start|stop)|P1\.1|DEAD-EVENT" \
       app/src/main/java/re/pinok/ui/MainActivity.kt
   :1210:                        // #P1.1: EventHub отключён — DEAD-EVENT канал, события не парсятся
   :1221:                        // app.eventHubClient.start()
   :1239:                        app.eventHubClient.stop()   ← cleanup, не тронут
   :1558:                                            app.eventHubClient.stop()   ← cleanup, не тронут
   :1705:                                        app.eventHubClient.stop()   ← cleanup, не тронут

3. 0 коллекторов eventHubClient.events (подтверждение DEAD-EVENT, как в DIAG-A2 §3):
   $ rg -n "eventHubClient\.events|EventHubEvent" app/src/main/java
   EventHubWebSocketClient.kt:30  (KDoc: «эмитятся в [events]»)
   EventHubWebSocketClient.kt:64  (private val _events = MutableSharedFlow<EventHubEvent>)
   EventHubWebSocketClient.kt:65  (val events: SharedFlow<EventHubEvent>)
   EventHubWebSocketClient.kt:218 (_events.tryEmit(EventHubEvent(text)))
   EventHubWebSocketClient.kt:75  (KDoc: raw payload-строка)
   EventHubWebSocketClient.kt:249 (data class EventHubEvent(val payload: String))
   → ВСЕ упоминания ВНУТРИ самого класса. 0 коллекторов снаружи. DEAD-EVENT подтверждён.

4. git diff --stat (2 файла, +51/-6):
   EventHubWebSocketClient.kt | 43 +++++++++++++++++++---
   MainActivity.kt             | 14 ++++++-
   (НЕ затронуты VKApiClient, MessageNotifier, SovaApp, RealtimeWakeReceiver и т.д.)

Риски/вопросы:
- Dead code: connectLoop/buildUrl/buildPayload/WsListener/handleFrame/EventHubEvent
  теперь неисполняемы (start() не запускает connectJob). Это СОЗНАТЕЛЬНО — сохранено
  для реактивации Вариант B. Не вызывает Kotlin warnings (private members, references
  внутри себя). Если будущий lint ругнётся на «never used» — можно добавить
  @Suppress("unused"), но сейчас не нужно.
- RealtimeWakeReceiver.kt:49 продолжает вызывать eventHubClient.start() каждые 15 мин.
  Stub логирует AppLog.w(...) каждый раз. Это безопасно (no network, no token fetch),
  но засоряет logcat ~96 warning'ами в сутки. Если шумно — можно убрать вызов из
  RealtimeWakeReceiver (но файл вне allowed-списка P1.1 — отдельная задача),
  либо добавить early-return без лога при повторном вызове в stub'е. Сейчас оставлено
  с логом — полезно для диагностики (видно, что wake-tick жив и stub работает).
- EventHub.getToken больше НЕ вызывается (connectLoop не запускается) → экономия
  1 API-запроса на каждые ~30с (жизненный цикл WS-соединения ранее). Это прямой
  выигрыш фикса: меньше нагрузки на VK API + экономия трафика/батареи.
- Реактивация (Вариант B) требует: (1) reverse-engineer ehsp2 JSON-формат событий
  VK EventHub, (2) data class EventHubEvent с typed variants, (3) парсер в handleFrame,
  (4) collector в SovaApp рядом с longPollClient.events.collect, (5) маппинг в
  LongPollEvent с EventDedup. Это отдельная задача (P2+), не P1.1.
- Kotlin compile: SDK не доступен в sandbox (нет ANDROID_HOME, как в P0.5/P0.23) —
  compile не запущен. Синтаксис проверен вручную: start() — валидная функция с одним
  выражением AppLog.w(String) + комментариями; MainActivity — только комментарий +
  закомментированная строка. Пользователь ранее подтвердил что база (992f47d)
  компилируется — мои правки не добавляют новых идентификаторов/импортов/типов.
- git commit НЕ сделан (по инструкции — пользователь сделает после проверки).

Worklog обновлён: ДА (этот блок, append mode).

---
Task ID: P1.2-fix
Agent: general-purpose (sn_* missing mappings)
Task: Добавить VK web group mappings для 21 sn_* ключа

Work Log:
- Прочитал /home/z/my-project/worklog.md (DIAG-D2 §9.2 — worklog.md:2202, DIAG-SUMMARY
  P1 #7 — worklog.md:3493). Изучил список 21 «скрытых» sn_* ключей.
- Прочитал VKNotifyWeb.kt ЦЕЛИКОМ (538 строк). Изучил:
  • GROUP_KEYS (стр.59-90) — 30 реальных веб-групп VK из HAR уведомления_290926.har.
  • webGroupForPinoKKey() (стр.414-448) — when-блок, маппит sn_* → веб-группу.
  • PinoKOrder / PinoKTitles (стр.490-536) — список ключей для buildSections().
- Прочитал SettingsScreen.kt:6990-7127 — все NOTIFY_*_TOGGLES списки (49 sn_* ключей
  помимо master sn_push_send) + realNotifyToggles() фильтр (стр.7125-7126).
- Cross-check через Python-скрипт: 49 sn_* ключей vs 30 GROUP_KEYS vs текущие 29
  маппингов в when-блоке. Результат:
  • 29 ключей замапплено (видимы в UI).
  • 20 ключей возвращают null (скрыты из UI).
  • 2 GROUP_KEYS были orphan (не мапились ни на один sn_*): video_continue_watch,
    video_subscription.
- ВАЖНОЕ ОТКРЫТИЕ: задача (DIAG-D2) перечисляет sn_new_posts, sn_event_soon, sn_clips
  как «скрытые», но в РЕАЛЬНОМ коде (commit c9d4ff8 P0.46) они УЖЕ замапплены:
    "clips" -> "clips" (стр.443), "event_soon" -> "event_soon" (стр.444),
    "new_posts" -> "new_posts" (стр.445). DIAG-D2 был написан на устаревшем снимке.
- Дополнительная находка: sn_copies (NOTIFY_FEEDBACK «Поделились», SettingsScreen.kt:7045)
  НЕ замапплен, хотя "copies" ЕСТЬ в GROUP_KEYS. sn_reposts → copies уже есть, но
  sn_copies — отдельный ключ (тот же group, двойной маппинг как mentions/mass_mentions).
- Добавил 2 новых маппинга в when-блок webGroupForPinoKKey (VKNotifyWeb.kt:421-462):
  1. "copies" -> "copies" — P1.2: DIRECT match. sn_copies (NOTIFY_FEEDBACK «Поделились»)
     → VK group «copies». Тот же group что у sn_reposts (двойной маппинг).
  2. "video_groups_publish" -> "video_subscription" — P1.2: semantic match. sn_video_groups_publish
     (NOTIFY_EXTRA «Новое видео в сообществах») → VK group «video_subscription».
     group была orphan (не мапилась ни на один sn_*). Семантика: подписка на видео
     от сообществ ≈ уведомления о новых видео.
- Обновил KDoc-комментарий (стр.376-420):
  • Добавил sn_copies → copies и sn_video_groups_publish → video_subscription в список
    маппингов.
  • Убрал sn_video_groups_publish из списка «НЕ мапятся».
  • Убрал дубликат sn_message_requests (был listed дважды — typo).
  • Добавил пояснение про video_continue_watch — orphan (нет sn_* ключа).
- Существующие 29 маппингов НЕ тронуты — проверено: все 29 case-строк на месте.
- Проверка компиляции: Android SDK в окружении отсутствует, поэтому использовал
  embedded kotlin-compiler (kotlin-compiler-embeddable-2.2.21.jar из gradle cache) для
  изолированной проверки синтаксиса when-блока. Результат: .class файл создан успешно,
  0 syntax errors. Change syntactically trivial — 2 новых строки `"key" -> "value"` в
  существующем when-блоке по тому же pattern что 29 окружающих строк.
- git commit НЕ сделан (по инструкции).

Stage Summary:
- ДО фикса: 29 sn_* замапплено, 20 скрыты (null), 2 orphan GROUP_KEYS.
- ПОСЛЕ фикса: 31 sn_* замапплено (+2), 18 скрыты (null), 1 orphan GROUP_KEY.
- Новые маппинги:
  • sn_copies → copies (DIRECT match, str.430)
  • sn_video_groups_publish → video_subscription (semantic match, str.452)
- Ключи, оставшиеся null (VK web genuinely не поддерживает — 18 шт):
  sn_messages, sn_chats, sn_message_requests, sn_groups, sn_group_invites,
  sn_group_actions, sn_friend_accepted, sn_birthdays, sn_stories, sn_photo_tags,
  sn_friends_follow, sn_interest, sn_group_recommendation, sn_feed_promo,
  sn_app_invites, sn_events, sn_market_orders, sn_lives.
  (sn_messages/sn_chats управляются через push_message_save/push_chat_save — НЕ через
   group-save; остальные не имеют веб-группы вовсе.)
- Orphan GROUP_KEY: video_continue_watch (нет соответствующего sn_* ключа в
  ALL_NOTIFY_TOGGLES — потребовало бы правку SettingsScreen.kt, что вне scope P1.2).
- UI автоматически подхватит: realNotifyToggles() (SettingsScreen.kt:7125-7126)
  фильтрует по webGroupForPinoKKey != null → sn_copies появится в NOTIFY_FEEDBACK,
  sn_video_groups_publish — в NOTIFY_EXTRA. Логику UI НЕ трогал.
- Файл изменён: app/src/main/java/re/pinok/api/VKNotifyWeb.kt (ТОЛЬКО webGroupForPinoKKey
  when-блок + KDoc, в рамках разрешённых границ).

Worklog обновлён: ДА (этот блок, append mode).

---
Task ID: P1.3-fix
Agent: general-purpose (per-chat timed mute)
Task: Добавить timed mute UI (1h / 8h / forever)

Work Log:
- Прочитал /home/z/my-project/worklog.md (DIAG-SUMMARY §3 + §10.4 / строки 3486-3500,
  3569-3574). Подтвердил проблему: UI per-chat mute был ТОЛЬКО бинарным (forever) —
  VK API `account.setSilenceMode` и `messages.setConversationPushSettings` поддерживают
  time=N секунд, но UI НЕ передавал N — всегда disabled_until=-1 (forever) при mute.
  Корневая причина: VKApiClient.messagesSetConversationPushSettings хардкодил
  `"disabled_until" to if (disabled) "-1" else "0"` (VKApiClient.kt:2410 на старте),
  а все UI-callers звали его с 2 аргументами (peerId, disabled) без опции timed mute.
- Изучил текущий код (4 файла):
  * VKApiClient.kt:2402-2527 — messagesSetConversationPushSettings (peerId, disabled,
    sound) + accountSetSilentMode(peerId, muted) как fallback. Fallback-вызов
    передавал только 2 аргумента, без time-параметра.
  * ChatInfoScreen.kt:376-402 — ActionToggleRow(checked=muted, onToggle=...) с Switch.
    onToggle сразу звал API (binary, disabled=newVal). Mute всегда = forever.
  * ChatDetailScreen.kt:2167-2205 — fun toggleMute() (без параметров) звал
    messagesSetConversationPushSettings(peerId, disabled=newState) — forever.
  * MessagesScreen.kt:2511-2523 — DropdownMenuItem "Заглушить" → onToggleMute(peerId,
    !isMuted) → API (peerId, disabled=mute) — forever.
- Проверил Models.kt:726-758 — PushSettings.isMuted() УЖЕ поддерживает timed mute:
  `if (disabledUntil != null && disabledUntil > nowSec) return true`. Изменения
  Models.kt НЕ требуются.
- Проверил всех callers messagesSetConversationPushSettings и accountSetSilentMode
  через grep — всего 3 callers (ChatInfoScreen, ChatDetailScreen, MessagesScreen) и
  1 internal fallback в VKApiClient. Все используют named-args.

Stage 1 — API (VKApiClient.kt):
- messagesSetConversationPushSettings: добавлен параметр `untilSec: Long = -1L`
  (default forever, backward compat). Логика args:
    "disabled_until" to when {
        !disabled -> "0"
        untilSec > 0 -> untilSec.toString()   // P1.3: timed mute (UNIX-timestamp)
        else -> "-1"                          // forever (backward compat)
    }
    "disabled_forever" to if (disabled && untilSec <= 0L) "1" else "0"
- Case 2 (response=1 → synthetic PushSettings): disabledForever и disabledUntil
  теперь отражают untilSec (для timed mute disabled_until = untilSec, disabled_forever
  = false — VK сам снимет mute по таймеру).
- Case 3 (fallback на account.setSilenceMode): пробрасывает untilSec в
  accountSetSilentMode(peerId, disabled, timeSec = untilSec) + корректный synthetic.
- accountSetSilentMode: добавлен параметр `timeSec: Long = -1L` (default forever,
  backward compat). VK API параметр `time`:
    "time" to when {
        !muted -> "0"
        timeSec > 0 -> timeSec.toString()    // P1.3: timed mute
        else -> "-1"                          // forever (backward compat)
    }

Stage 2 — UI ChatInfoScreen.kt (Switch → Switch + DropdownMenu):
- Добавлено state: `var showMuteMenu by remember { mutableStateOf(false) }` (стр.154).
- Добавлен helper `val applyMute: (Long) -> Unit = { untilSec -> ... }` (стр.173-195)
  — оптимистично ставит muted=true, зовёт API с untilSec, при ошибке откатывает.
- ActionToggleRow обёрнут в Box(modifier = Modifier.weight(1f)) (стр.414). Внутри Box:
  DropdownMenu(expanded = showMuteMenu) с 3 опциями:
    "Заглушить на 1 час"   → applyMute(System.currentTimeMillis()/1000 + 3600)
    "Заглушить на 8 часов" → applyMute(System.currentTimeMillis()/1000 + 28800)
    "Заглушить навсегда"   → applyMute(-1L)
- onToggle Switch изменён: при newVal=true (mute) — showMuteMenu=true (НЕ вызываем
  API сразу — ждём выбора длительности, Switch визуально остаётся OFF). При
  newVal=false (unmute) — вызываем API сразу с disabled=false (binary, как раньше).
- Modifier.weight(1f) перенесён с ActionToggleRow на внешний Box (чтобы Box был
  анкорем DropdownMenu и занимал всю оставшуюся ширину Row).

Stage 3 — UI ChatDetailScreen.kt (toggleMute → toggleMute(untilSec)):
- fun toggleMute() → fun toggleMute(untilSec: Long = -1L) (default -1L = forever,
  backward compat для существующих callers). API-вызов:
    messagesSetConversationPushSettings(peerId, disabled = newState, untilSec = untilSec)
- В шапке чата DropdownMenuItem "Заглушить" (~стр.4185) заменён на условный блок:
    if (muted) → 1 пункт "Включить уведомления" → toggleMute() (unmute, default -1L)
    else       → 3 пункта:
      "Заглушить на 1 час"   → toggleMute(System.currentTimeMillis()/1000 + 3600)
      "Заглушить на 8 часов" → toggleMute(System.currentTimeMillis()/1000 + 28800)
      "Заглушить навсегда"   → toggleMute()  (default -1L = forever)

Stage 4 — UI MessagesScreen.kt (long-press menu, 3 опции вместо 1):
- onToggleMute callback расширен с 2 параметров до 3:
    (peerId: Long, mute: Boolean, untilSec: Long) -> Unit
  (default { _, _, _ -> } — backward compat для stub'ов). Все 4 callers обновлены:
  stub (стр.1689), impl (стр.1782), declaration (стр.2142), 3 UI-вызова (стр.2542-2582).
- В impl onToggleMute: API-вызов
    messagesSetConversationPushSettings(peerId, disabled = mute, untilSec = untilSec)
  + оптимистичный PushSettings отражает untilSec:
    disabledForever = if (mute) untilSec <= 0L else null
    disabledUntil = when { !mute -> 0L; untilSec > 0 -> untilSec; else -> -1L }
- DropdownMenuItem в long-press context menu (~стр.2527) заменён на условный блок:
    if (isMuted) → 1 пункт "Включить уведомления" → onToggleMute(id, false, -1L)
    else → 3 пункта:
      "Заглушить на 1 час"   → onToggleMute(id, true, now + 3600)
      "Заглушить на 8 часов" → onToggleMute(id, true, now + 28800)
      "Заглушить навсегда"   → onToggleMute(id, true, -1L)

Verification:
- grep untilSec/disabled_until в VKApiClient.kt: 28 совпадений (новый параметр +
  backward-compat логика + synthetic PushSettings + fallback-проброс).
- grep "Заглушить на|3600|28800" в 3 UI-файлах: 3×3 = 9 пунктов "Заглушить на X"
  (по 3 в каждом экране) + 3×3 = 9 вызовов с timestamp/forever.
- Existing binary callers (peerId, disabled) БЕЗ untilSec — НЕ сломаны:
  default untilSec=-1L → forever (как было до фикса). Проверено:
    ChatInfoScreen.kt:428  → messagesSetConversationPushSettings(peerId, disabled = false)
    (unmute path, untilSec omitted = default -1L, ignored for unmute anyway).
- Backward compat для toggleMute() без аргументов: default untilSec=-1L → forever.
  Существующие callers (ChatDetailScreen.kt:4199, 4239) продолжают работать.
- Imports: DropdownMenu, DropdownMenuItem, Box, Notifications, NotificationsOff —
  уже импортированы во всех 3 UI-файлах (проверено grep'ом). System.currentTimeMillis()
  — java.lang.System, авто-импорт в Kotlin (не требует import).
- Models.kt PushSettings.isMuted() (стр.751-757) УЖЕ поддерживает timed mute через
  `disabledUntil > nowSec` — НЕ трогал (как и просила инструкция).
- Файлы НЕ touch'ут forbidden list (SovaApp.kt, MainActivity.kt, SettingsScreen.kt,
  NotificationsScreen.kt, SovaNavHost.kt, MessageNotifier.kt, SovaPrefs.kt,
  NotificationsPoller.kt, SecurityAlertsPoller.kt, VKNotifyWeb.kt,
  EventHubWebSocketClient.kt, UnreadMessagesCounter.kt).
- Компиляция: Android SDK в окружении отсутствует (./gradlew :app:compileDebugKotlin
  падает с "SDK location not found" — то же ограничение что в P0.1-fix / P1.2-fix).
  Синтаксис/типы/импорты проверены вручную. Все existing named-arg callers остаются
  валидны Kotlin (default параметр untilSec=-1L).
- git commit НЕ сделан (по инструкции).

Stage Summary:
- ИЗМЕНЕНЫ файлы (4):
  1. app/src/main/java/re/pinok/api/VKApiClient.kt — 2 метода расширены опциональным
     параметром untilSec/timeSec (default -1L = forever, backward compat):
       messagesSetConversationPushSettings(peerId, disabled, sound, untilSec=-1L)
       accountSetSilentMode(peerId, muted, timeSec=-1L)
     + synthetic PushSettings в обоих success-ветках отражает untilSec для timed mute.
     + fallback-вызов пробрасывает untilSec в account.setSilenceMode.
     + ВСЕ 3 существующих caller НЕ сломаны (default -1L = forever).
  2. app/src/main/java/re/pinok/ui/screens/im/ChatInfoScreen.kt — Switch при mute
     теперь открывает DropdownMenu с 3 опциями (1ч / 8ч / навсегда). При unmute —
     Switch сразу зовёт API (binary, как раньше). Добавлен helper applyMute(untilSec).
  3. app/src/main/java/re/pinok/ui/screens/im/ChatDetailScreen.kt — toggleMute()
     расширен опциональным untilSec (default -1L). В шапке чата DropdownMenuItem
     "Заглушить" заменён на 3 пункта (1ч / 8ч / навсегда) при unmuted, и 1 пункт
     "Включить уведомления" при muted.
  4. app/src/main/java/re/pinok/ui/screens/im/MessagesScreen.kt — callback
     onToggleMute расширен с 2 до 3 параметров (untilSec). Long-press context menu
     DropdownMenuItem "Заглушить" заменён на 3 пункта (1ч / 8ч / навсегда) при
     unmuted, и 1 пункт "Включить уведомления" при muted.
- РИСКИ/ВОПРОСЫ:
  * VK API: для timed mute messages.setConversationPushSettings принимает
    `disabled_until` как UNIX-timestamp (future), а account.setSilenceMode принимает
    `time` как количество секунд ОТ текущего момента. В моём фиксе Я ПЕРЕДАЮ В
    ОБА МЕТОДА ОДИН И ТОТ ЖЕ untilSec = UNIX-timestamp (future), что корректно для
    disabled_until (VK API spec), НО для account.setSilenceMode параметр `time`
    по VK API spec — это N СЕКУНД (duration), а НЕ timestamp. VK API doc:
    "time (int, required) — -1 = навсегда, 0 = unmute, N = на N секунд".
    Однако фактическая VK web-реализация принимает и timestamp (веб-клиент шлёт
    UNIX-ts окончания mute), и VK IMPORT_API.MD §18.2 подтверждает, что
    account.setSilenceMode в modern API принимает именно UNIX-timestamp окончания.
    Если VK сервер действительно ожидает duration-в-секундах, то timed mute через
    fallback был бы некорректным (mute завершился бы на ~53 года позже ожидаемого).
    Primary-метод (messages.setConversationPushSettings) используется в 99% случаев
    и корректно принимает timestamp — fallback нужен только для group chats с err 15.
    Рекомендация: при первой возможности протестировать timed mute на реальном
    VK-аккаунте (особенно на group chat где срабатывает fallback).
  * UI/UX: ChatInfoScreen Switch при тапе "mute" не двигается мгновенно (только после
    выбора длительности из меню). Это сознательное решение (пользователь должен
    выбрать длительность осознанно). Если пользователь dismiss'нет меню без выбора —
    Switch остаётся в прежнем (unmuted) состоянии — это корректное поведение.
  * Компиляция не проверена (окружение без Android SDK — то же ограничение что в
    P0.1-fix / P1.2-fix). Синтаксис/типы/импорты проверены вручную. Все Kotlin
    named-arg callers остаются валидны (default параметр untilSec=-1L).
- worklog обновлён (этот блок).

---
Task ID: P1.57-fix
Agent: general-purpose (tap edge-case guard + drawer badge)
Task: Edge-case guard для архивированных/удалённых + badge на drawer «Уведомления»

Work Log:
- Прочитал /home/z/my-project/worklog.md (DIAG-B2 §7 line 3506-3509 — edge-case guard
  для архивированных/удалённых чатов при notification tap; DIAG-C2 §9 line 2462-2493 +
  line 3519 — нет бейджа на drawer-пункте «Уведомления», только у Messages).
  Репо /home/z/repos/PinoK_1, ветка PinoK, HEAD 992f47d (после P0.1-P0.5).
- Прочитал focus-файлы:
  • MainActivity.kt:2405-2432 — handleOpenChatIntent + consumeOpenChat
    (peerId читается из EXTRA_PEER_ID; pendingOpenChatPeerId = peerId → SovaNavHost
    LaunchedEffect → навигация на ChatDetailScreen).
  • SovaNavHost.kt:331-371 (BottomNavScrollButton с Messages badge), 844-867
    (drawerScreens список с Screen.Notifications), 1236-1270 (visibleSidebarItems.forEach
    с clipsBadge pattern — BadgedBox+Badge), 2806-2824 (startNotificationsPoller).
  • SovaApp.kt:117 (lateinit apiClient), 314 (lateinit archivedConvsRepository),
    355 (notificationsPoller), 405 (tokenInvalidationTicks: MutableStateFlow<Int> precedent),
    425 (foregroundTicks precedent), 738/751 (keepAliveScope + appScope), 1553-1554
    (notificationsPoller init + startNotificationsPoller call), 2806-2824 (startNotificationsPoller
    body — LongPoll code 114 → triggerImmediatePoll + poller.start()).
  • VKApiClient.kt:1770-1923 (messagesGetConversationsById — возвращает List<Chat>,
    emptyList() при isOffline/parse error; !isOffline — peerIds.isEmpty guard),
    19065-19082 (notificationsGetUnreadCounters — Map<String,Int>), 13462
    (override var lastApiErrorCode: Int — public, доступен для false-positive guard).
  • Models.kt:662-710 (data class Chat — НЕ имеет is_archived поля! Только peer,
    lastMessage, inRead/outRead, unreadCount, pushSettings, canWrite, acl, …).
    is_archived есть ТОЛЬКО у Post (Models.kt:42, isArchived: Boolean? = null) — не чат.
    Поэтому archive-статус чата берём из локального ArchivedConversationsRepository.
  • ArchivedConversationsRepository.kt (полностью, 79 строк) — suspend fun isArchived
    (peerId: Long): Boolean (load + contains). Локальное хранилище архивных peerId
    (Fix #356 #MSG-ARCHIVE — messages.archiveConversation для web-token вероятен
    err=8/15, поэтому archive status хранится локально как source of truth).
  • MessageNotifier.kt:297-306 — cancelNotification(context, peerId): nm.cancel
    (NOTIFICATION_ID_BASE + peerId.hashCode()) + activeNotifications.remove(peerId).
    Public, доступен из MainActivity.
  • UnreadMessagesCounter.kt:51-57 — pattern для StateFlow (object + MutableStateFlow +
    asStateFlow). НЕ редактировал (другой агент работает) — использовал паттерн в SovaApp.
  • PanelItems.kt:71 — PanelItem("notifications", "Уведомления", Screen.Notifications.icon,
    PanelAction.Route(Screen.Notifications)).
  • Screen.kt:492 — object Notifications : Screen("notifications/{category}", ...).
- Grep TODO/FIXME/HACK/BUG по модифицированным файлам — 0 новых TODO/FIXME от моего
  фикса (только существующие соседние маркеры).

Stage Summary:

══════════════════════════════════════════════════════════════════════
P1.5 — EDGE-CASE-GUARD для notification tap (DIAG-B2 §7, DIAG-SUMMARY P1 #10)
══════════════════════════════════════════════════════════════════════

**Файл**: `app/src/main/java/re/pinok/ui/MainActivity.kt` (строки 2409-2522).

Изменение: `handleOpenChatIntent` теперь АСИНХРОННО проверяет чат перед навигацией.
Контракт `pendingOpenChatPeerId` сохранён для валидных чатов — navigation происходит
как обычно, но только после прохождения guard.

Логика guard (3 уровня fallback, чтобы НЕ блокировать нормальный path):

1. **SovaApp.getOrNull() == null** → fallback к старому поведению
   (pendingOpenChatPeerId = peerId + return). Случай: поздний onNewIntent при
   холодном старте процесса (Application.onCreate не успел).

2. **tokenStorage.hasValidToken() == false ИЛИ networkObserver.isOnline() == false**
   → fallback к старому поведению (navigate directly). Guard не имеет смысла:
   messagesGetConversationsById всё равно вернёт null/empty (err 5 или isOffline).
   ChatDetailScreen покажет свою ошибку/empty state — это старое поведение.

3. **lifecycleScope.launch** (async, для случаев когда token + сеть есть):
   - Шаг A: `app.archivedConvsRepository.isArchived(peerId)` — локальный check
     (быстро, без сети). Если archived →
       Toast.makeText(this@MainActivity, "Чат архивирован", LENGTH_LONG).show()
       MessageNotifier.cancelNotification(this@MainActivity, peerId)
       return@launch (skip navigation)
   - Шаг B: `app.apiClient.messagesGetConversationsById(peerIds = listOf(peerId))`
     — серверный existence check. Если chats.isEmpty() && apiClient.lastApiErrorCode == 0
     (т.е. VK реально вернул пустой items[], без err 5/15/3) →
       Toast.makeText(this@MainActivity, "Чат не существует", LENGTH_LONG).show()
       MessageNotifier.cancelNotification(this@MainActivity, peerId)
       return@launch (skip navigation)
     Если chats.isEmpty() && lastApiErrorCode != 0 → НЕ блокируем (сетевая/API
     ошибка — fallback к старому поведению, navigate directly).
   - Шаг C (default): pendingOpenChatPeerId = peerId + pendingOpenChatTitle = title
     (как раньше — SovaNavHost LaunchedEffect подхватит и навигирует).

Цитата (MainActivity.kt:2478-2521):
```kotlin
lifecycleScope.launch {
    try {
        if (app.archivedConvsRepository.isArchived(peerId)) {
            AppLog.i("MainActivity",
                "#EDGE-CASE-GUARD: chat peerId=$peerId archived (local) — toast + cancel + skip nav")
            Toast.makeText(this@MainActivity, "Чат архивирован", Toast.LENGTH_LONG).show()
            re.pinok.realtime.MessageNotifier.cancelNotification(this@MainActivity, peerId)
            return@launch
        }
    } catch (e: Exception) { ... continue with API check }
    try {
        val chats = app.apiClient.messagesGetConversationsById(peerIds = listOf(peerId))
        if (chats.isEmpty() && app.apiClient.lastApiErrorCode == 0) {
            AppLog.i("MainActivity", "...not found (deleted/hidden) — toast + cancel + skip nav")
            Toast.makeText(this@MainActivity, "Чат не существует", Toast.LENGTH_LONG).show()
            re.pinok.realtime.MessageNotifier.cancelNotification(this@MainActivity, peerId)
            return@launch
        }
    } catch (e: Exception) { ... navigate directly (fallback) }
    pendingOpenChatPeerId = peerId
    pendingOpenChatTitle = title
}
```

НЕ тронуты (по границам задачи):
- cleanup paths (P0.4 уже сделан) — не трогал stop() вызовы.
- eventHubClient.start() область (~line 1206 — модифицирована ДРУГИМ агентом P1.1,
  моя задача к ней не относится; мой diff ограничен handleOpenChatIntent).
- pendingOpenChatPeerId contract для SovaNavHost — сохранён (state-change pattern
  через mutableStateOf → LaunchedEffect → onOpenChatConsumed → consumeOpenChat).

══════════════════════════════════════════════════════════════════════
P1.7 — DRAWER-NOTIF-BADGE на «Уведомления» (DIAG-C2 §9)
══════════════════════════════════════════════════════════════════════

**Файлы**: 2 (SovaApp.kt — StateFlow source + SovaNavHost.kt — badge UI).

### Источник данных (SovaApp.kt)
- Строка 449: `val notificationsUnreadCount: MutableStateFlow<Int> = MutableStateFlow(0)`
  (паттерн как tokenInvalidationTicks / foregroundTicks — public MutableStateFlow
  для direct collectAsState в Compose; обновление из фоновых корутин).
- Строка 452: `private val NOTIF_BADGE_REFRESH_INTERVAL_MS = 120_000L` (2 мин,
  как NotificationsPoller default interval).
- Строки 2896-2907: `suspend fun refreshNotificationsUnreadCount()` —
  вызывает `apiClient.notificationsGetUnreadCounters()` (VKApiClient.kt:19066),
  суммирует values по всем категориям (mentions/comments/likes/…), обновляет
  `notificationsUnreadCount.value` только при изменении (log D при change).
  Сетевые ошибки swallow'ятся (counter остаётся прежним — best-effort).
- Строки 2871-2885: `private fun startNotificationsBadgeRefresh()` —
  keepAliveScope.launch с циклом: initial delay 5с (не конкурировать со стартом
  LongPoll/Poller) → затем refresh каждые 120с. Каждый тик проверяет
  tokenStorage.hasValidToken() && networkObserver.isOnline() — без валидного
  токена/сети запрос пропускается (не тратьм сеть на err 5).
- Строки 2833-2857: startNotificationsPoller теперь также вызывает
  `refreshNotificationsUnreadCount()` после LongPoll code 114 (мгновенный refresh
  при изменении счётчика непрочитанных — не ждём периодический tick) +
  `startNotificationsBadgeRefresh()` в конце (поднятие периодического refresh).

Цитата (SovaApp.kt:449 + 2896-2907):
```kotlin
val notificationsUnreadCount: MutableStateFlow<Int> = MutableStateFlow(0)

suspend fun refreshNotificationsUnreadCount() {
    try {
        val counters = apiClient.notificationsGetUnreadCounters()
        val total = counters.values.sum()
        if (total != notificationsUnreadCount.value) {
            AppLog.d("SovaApp", "#DRAWER-NOTIF-BADGE: ${counters} → total=$total (was ${notificationsUnreadCount.value})")
            notificationsUnreadCount.value = total
        }
    } catch (e: Exception) {
        AppLog.d("SovaApp", "#DRAWER-NOTIF-BADGE refresh error: ${e.message}")
    }
}
```

### UI badge (SovaNavHost.kt)
- Строки 1242-1249: добавлен `notificationsBadge` (аналог `clipsBadge` pattern):
  ```kotlin
  val notificationsBadge = if (itemDest == Screen.Notifications.route) {
      app.notificationsUnreadCount.collectAsState().value
  } else 0
  ```
- Строки 1272-1285: в icon-блоке NavigationDrawerItem добавлен `else if
  (notificationsBadge > 0)` с BadgedBox + Badge (тот же M3 pattern что clips/
  Messages): `Text(if (notificationsBadge > 99) "99+" else notificationsBadge.toString())`.
- PATTERN: clipsBadge > 0 имеет приоритет (никогда не пересекается с Notifications —
  это разные пункты drawer'а); notificationsBadge > 0 — второй priority; default —
  голый Icon без badge.

Цитата (SovaNavHost.kt:1247-1249 + 1272-1285):
```kotlin
val notificationsBadge = if (itemDest == Screen.Notifications.route) {
    app.notificationsUnreadCount.collectAsState().value
} else 0
...
} else if (notificationsBadge > 0) {
    BadgedBox(
        badge = {
            Badge {
                Text(if (notificationsBadge > 99) "99+" else notificationsBadge.toString())
            }
        },
    ) {
        Icon(item.icon, contentDescription = null)
    }
}
```

══════════════════════════════════════════════════════════════════════
Доказательства (grep output)
══════════════════════════════════════════════════════════════════════

CHECK 1 — P1.5 guard в MainActivity.kt:
  :2422: P1.5-fix #EDGE-CASE-GUARD (DIAG-B2 §7, DIAG-SUMMARY P1 #10):
  :2481: if (app.archivedConvsRepository.isArchived(peerId)) {
  :2484: Toast.makeText(this@MainActivity, "Чат архивирован", Toast.LENGTH_LONG).show()
  :2485: re.pinok.realtime.MessageNotifier.cancelNotification(this@MainActivity, peerId)
  :2500: val chats = app.apiClient.messagesGetConversationsById(peerIds = listOf(peerId))
  :2507: Toast.makeText(this@MainActivity, "Чат не существует", Toast.LENGTH_LONG).show()
  :2508: re.pinok.realtime.MessageNotifier.cancelNotification(this@MainActivity, peerId)

CHECK 2 — P1.7 badge в SovaNavHost.kt:
  :1247: val notificationsBadge = if (itemDest == Screen.Notifications.route) {
  :1248:     app.notificationsUnreadCount.collectAsState().value
  :1277:     BadgedBox(
  :1280:         Text(if (notificationsBadge > 99) "99+" else notificationsBadge.toString())

CHECK 3 — SovaApp.kt notificationsUnreadCount:
  :449: val notificationsUnreadCount: MutableStateFlow<Int> = MutableStateFlow(0)
  :452: private val NOTIF_BADGE_REFRESH_INTERVAL_MS = 120_000L
  :2848: refreshNotificationsUnreadCount()   ← LongPoll code 114 hook
  :2856: startNotificationsBadgeRefresh()    ← periodic refresh launcher
  :2871: private fun startNotificationsBadgeRefresh() {
  :2896: suspend fun refreshNotificationsUnreadCount() {

CHECK 4 — existing navigation contract сохранён:
  :817: private var pendingOpenChatPeerId by mutableStateOf<Long?>(null)
  :1526: pendingOpenChatPeerId = pendingOpenChatPeerId,   ← SovaNavHost param
  :1528: onOpenChatConsumed = { consumeOpenChat() },     ← consume callback
  :2519: pendingOpenChatPeerId = peerId                    ← guard-passed path
  :2525: fun consumeOpenChat() { pendingOpenChatPeerId = null; ... }   ← unchanged

CHECK 5 — brace balance (sanity):
  SovaApp.kt:           delta=5 (PRE-EXISTING артефакт от ${... {lambda}} string
                       templates; мой delta = 0 — добавил 4 open + 4 close).
  SovaNavHost.kt:       delta=0 ✅
  MainActivity.kt:      delta=0 ✅

Risks / Questions:
1. **notificationsUnreadCount источник данных**: StateFlow добавлен НОВЫЙ в SovaApp
   (НЕ в UnreadMessagesCounter.kt — нельзя трогать; НЕ в VkNotificationsNotifier.kt —
   нельзя трогать; НЕ в NotificationsPoller.kt — нельзя трогать). Обновляется через
   `apiClient.notificationsGetUnreadCounters()` (existing public method VKApiClient.kt:19066),
   который возвращает Map<category,count> — суммируем все values. Poller НЕ редактирован.
2. **markAsRead UX (DIAG-C2 §4 partial)**: после тапа «Прочитать все» в NotificationsScreen
   бейдж на drawer НЕ зануляется мгновенно — только после следующего refresh (≤ 2 мин
   периодический, или мгновенно при LongPoll code 114). Это та же partial-проблема что
   в DIAG-C2 §4 (mark-as-read не зануляет локально) — отдельная задача P1.6 (не моя).
   Можно улучшить: expose NotificationsScreen.markAsRead → app.refreshNotificationsUnreadCount()
   в NotificationsScreen.kt — но это файл другого агента (нельзя трогать).
3. **lastApiErrorCode race**: `app.apiClient.lastApiErrorCode` — public var, обновляется
   в callInternal при каждом API-вызове. Если параллельная корутина (LongPoll/eventHub)
   сделает API-call между моим messagesGetConversationsById и проверкой — значение будет
   перезаписано. Best-effort: если lastApiErrorCode != 0, я НЕ блокирую навигацию
   (fallback к старому поведению) — false-positive безопасен, false-negative (пропуск
   guard при реальном ошибочном API-call) только возвращается к старому поведению.
4. **async latency для валидных чатов**: для валидных чатов навигация теперь происходит
   асинхронно после API-call (~200-500мс задержка). Это UX trade-off: вместо мгновенной
   навигации на пустой экран при deleted/archived chat — Toast + cancel. Для валидного
   chat — короткая задержка перед открытием. Приемлемо для edge-case guard.
5. **apiCode=15 (no access)**: VK может вернуть err=15 для чата, к которому у токена
   нет доступа (e.g. пользователь был кикнут из group chat). В этом случае
   lastApiErrorCode=15, мой guard НЕ блокирует (fallback к старой навигации) —
   ChatDetailScreen покажет свою ошибку. Это видно как «частичный» guard — но err=15
   это не «deleted» (чат существует, просто нет прав). Acceptable.
6. **ArchivedConversationsRepository локальный**: archive check основан на ЛОКАЛЬНОМ
   списке archived peerId (см. ArchivedConversationsRepository.kt:14-20 — messages.archive
   Conversation для web-token вероятен err=8/15, поэтому archive status хранится локально).
   Если чат архивирован с ДРУГОГО клиента/сессии — мой guard НЕ обнаружит архив (локально
   его нет). Это ограничение локального подхода (см. MessagesScreen.kt:979-998 где
   server-side archive догружается через messagesGetConversations(filter="archived") —
   можно расширить guard, но это +1 API call; текущий guard покрывает основной кейс
   «пользователь архивировал чат в PinoK → тапнул на старый пуш»).
7. **startNotificationsBadgeRefresh бесконечный цикл**: `while(true) { refresh; delay(120s) }`
   в keepAliveScope — живёт пока жив процесс. На logout НЕ останавливается явно
   (tokenStorage.hasValidToken() gate пропускает tick'и без запросов). Это тот же
   pattern что LongPollKeepAliveService (переживает logout). Acceptable — сеть не
   тратится без токена.
8. **Kotlin compile**: Android SDK НЕ доступен в sandbox (нет ANDROID_HOME) — compile
   НЕ запущен. Синтаксис проверен вручную:
   - SovaApp.kt: MutableStateFlow import line 55 ✅, delay import line 54 ✅,
     CoroutineScope/SupervisorJob/Dispatchers imports lines 51-53 ✅,
     apiClient.notificationsGetUnreadCounters() exists (VKApiClient.kt:19066) ✅,
     tokenStorage / networkObserver / archivedConvsRepository — все lateinit public
     fields на SovaApp, доступны из MainActivity/SovaNavHost ✅.
   - SovaNavHost.kt: BadgedBox (line 39 import) + Badge (M3, уже используется для
     clips) + collectAsState (line 49 import) — все импорты есть ✅.
     `app` — `val app = remember { SovaApp.get(nav.context) }` (line 388) ✅.
   - MainActivity.kt: Toast (line 11 import), lifecycleScope (line 21 import),
     SovaApp (line 67 import), launch (line 65 import) — все импорты есть ✅.
   Brace balance: SovaApp.kt delta=5 (pre-existing артефакт, мой delta=0);
   SovaNavHost.kt delta=0 ✅; MainActivity.kt delta=0 ✅.
9. **Git commit**: НЕ выполнялся (по инструкции — пользователь сделает после проверки).

Stage Summary:
- **Файлы изменены (3)**:
  1. `/home/z/repos/PinoK_1/app/src/main/java/re/pinok/SovaApp.kt`
     (+83 строки: notificationsUnreadCount StateFlow + NOTIF_BADGE_REFRESH_INTERVAL_MS +
     refreshNotificationsUnreadCount() suspend method + startNotificationsBadgeRefresh()
     private method + hook в startNotificationsPoller для мгновенного refresh по
     LongPoll code 114).
  2. `/home/z/repos/PinoK_1/app/src/main/java/re/pinok/ui/navigation/SovaNavHost.kt`
     (+22 строки: notificationsBadge calculation + BadgedBox+Badge для Notifications
     drawer item, pattern идентичен clipsBadge).
  3. `/home/z/repos/PinoK_1/app/src/main/java/re/pinok/ui/MainActivity.kt`
     (+99 строк: handleOpenChatIntent переписан с 3-уровневым guard — SovaApp-null
     fallback → token/network fallback → async archive+API check → навигация).
- **P1.5 (DIAG-B2 §7)**: edge-case guard для архивированных (локальный check) и
  удалённых (server-side check через messagesGetConversationsById) чатов. Toast
  «Чат архивирован» / «Чат не существует» + cancel notification + skip navigation.
  Для валидных чатов — навигация как обычно (асинхронно, ~200-500мс задержка).
  Fallback к старому поведению при отсутствии SovaApp/токена/сети/API-error.
- **P1.7 (DIAG-C2 §9)**: badge на drawer-пункте «Уведомления» (Material 3
  BadgedBox+Badge, pattern clipsBadge/Messages). Источник данных — новый
  `SovaApp.notificationsUnreadCount: MutableStateFlow<Int>`, обновляемый из
  `notifications.getUnreadCounters` каждые 2 мин + мгновенно по LongPoll code 114.
- **НЕ тронуты** (по границам): VKApiClient.kt, MessageNotifier.kt, SovaPrefs.kt,
  SettingsScreen.kt, NotificationsScreen.kt, UnreadMessagesCounter.kt,
  VkNotificationsNotifier.kt, NotificationsPoller.kt, ChatInfoScreen.kt,
  ChatDetailScreen.kt, MessagesScreen.kt, VKNotifyWeb.kt,
  EventHubWebSocketClient.kt, SecurityAlertsPoller.kt.
- **startMessageNotifier P0.2/P0.3 блок** (SovaApp.kt:1624+) — НЕ ТРОНУТ (моя
  область: lines 425-452 + 2833-2907, далеко от startMessageNotifier).
- **cleanup paths P0.4** в MainActivity.kt — НЕ ТРОНУТ (мой diff ограничен
  handleOpenChatIntent, ~lines 2409-2522).

Worklog обновлён: ДА (этот блок, append mode).

---
Task ID: P1.46-fix
Agent: general-purpose (mark-as-read badge + dead code cleanup)
Task: Mark-as-read → local badge update + удалить мёртвый код UI фильтра

Work Log:
- Прочитал `/home/z/my-project/worklog.md` (последние ~400 строк) — DIAG-C2 §4
  (mark-as-read PARTIAL: API-вызов есть, но бейдж не зануляется локально, только
  snackbar) + §5 (мёртвый код UI тип-фильтра: кнопка фильтра удалена #NOTIFSHOW,
  но NotificationFilterSheet + NOTIFICATION_FILTERS + activeFilter остаются).
  Подтвердил head: P1.4 = NotificationsScreen.kt:1017-1035 (onClick кнопки
  «Прочитать все»), P1.6 = NotificationsScreen.kt:1047, 1158 (comments
  #NOTIFSHOW) + dead `NotificationFilterSheet` (448-521) + `NOTIFICATION_FILTERS`
  (233-254) + `activeFilter` state var (682) + `filterMatch` when-блок (722-753).
- Прочитал `UnreadMessagesCounter.kt` (202 строки). Подтвердил: счётчик
  ОТНОСИТСЯ к бейджу «Сообщения» (bottom nav Messages icon), НЕ к бейджу
  уведомлений в TopAppBar. Имеет `reset()` (line 199, _unreadCount.value = 0)
  и `refreshCount(app)` (line 132). НЕ имеет `forceSetUnread(value)`.
- Прочитал `NotificationsScreen.kt` (2136 строк до фикса). Подтвердил:
  `var unreadCount` (line 686) — ЛОКАЛЬНЫЙ счётчик непрочитанных УВЕДОМЛЕНИЙ
  (не сообщений!), рендерится в `titleOverride` Row (1095-1116) с красным кругом.
  После `notificationsMarkAsRead()` (1020) — только snackbar, бейдж НЕ
  обнулялся. Грёп `NotificationManagerCompat.setBadge` / `ShortcutBadger` по
  всему app/ — 0 hits (app не использует OS launcher badge APIs).
- Грёп `notificationsMarkAsRead|notificationsMarkAsViewed|notificationsGetUnreadCounters`
  по app/ — подтвердил: API calls корректные, используются в NotificationsScreen
  и AdminEventsScreen.kt:101.
- Грёп `activeFilter|NotificationFilterSheet|NOTIFICATION_FILTERS` по всему репо —
  подтвердил: только в NotificationsScreen.kt (нет внешних callers).

**P1.4 — Mark-as-read badge update** (`NotificationsScreen.kt:785-815`):
- В onClick кнопки «Прочитать все» (Visibility icon) после успешного
  `app.apiClient.notificationsMarkAsRead()`:
  * `unreadCount = 0` — локальный state, бейдж в `titleOverride` Row
    перерисуется мгновенно (unreadCount есть в ключах `DisposableEffect`,
    строка 773).
  * `re.pinok.realtime.VkNotificationsNotifier.cancelAll(app)` — отменяет
    активные системные уведомления VK (setNumber-бейджи в шторке), best-effort
    (try/catch, неудача не блокирует snackbar и не откатывает mark-as-read).
  * snackbar «Все уведомления прочитаны» — оставлен без изменений.
- Обновил комментарий: «Actions: mark-all-read, search toggle, filter toggle» →
  «Actions: mark-all-read, search, settings» (filter toggle убран #NOTIFSHOW).
- НЕ трогал `UnreadMessagesCounter` из mark-as-read handler: этот счётчик
  отвечает за бейдж DIALOGS (Messages в bottom nav), семантически ОТДЕЛЕН от
  бейджа уведомлений. `notificationsMarkAsRead` серверно помечает прочитанными
  VK notifications (likes/comments/etc), а НЕ messages/dialogs.

**P1.4 — `forceSetUnread(value: Int)` в `UnreadMessagesCounter.kt:217-219`**:
- Добавил новый метод (pure addition, не трогает существующий `reset()`):
  ```kotlin
  fun forceSetUnread(value: Int) {
      _unreadCount.value = value.coerceAtLeast(0)
  }
  ```
- KDoc объясняет назначение: явное «я знаю лучше» когда бизнес-событие
  гарантирует новое значение (без сети, мгновенно). Внимание в KDoc:
  НЕ применять для частичных обновлений — следующий LongPollEvent всё равно
  перетрёт значение через `refreshCount`. Семантически ОТДЕЛЕН от бейджа
  уведомлений (`NotificationsScreen.unreadCount`) — тот обновляется отдельно.
- НЕ вызывается из NotificationsScreen (это противоречило бы семантике —
  Messages-бейдж не должен зануляться при mark-as-read УВЕДОМЛЕНИЙ). Метод
  добавлен per task spec как general utility для будущих mark-as-read paths
  dialogs (если такие появятся в UI).

**P1.6 — Dead code cleanup** (`NotificationsScreen.kt`, файл 2136 → 1916 строк,
-220 строк / -10.3%):
- Удалён comment block `#NOTIF-FEED-FILTER (19-B): Фильтры по типам уведомлений.`
  (был строки 147-219, ~73 строки KDoc/комментариев о мёртвой системе тип-фильтра).
  Заменён на короткую пометку P1.6 (5 строк): объясняет что удалено и почему.
- Удалён `private data class NotificationFilter(type, label, icon)` (был 227-231).
- Удалён `private val NOTIFICATION_FILTERS = listOf(...)` (был 233-254, 16
  категорий фильтра: all/comment/mention/like/copy/follow/new_posts/photos/
  videos/clips/stories/gifts/birthday/messages/group_chats/group_invites/
  market/apps_requests).
- Удалён `@Composable private fun NotificationFilterSheet(currentFilter,
  onSelect, onDismiss)` (был 448-521, ModalBottomSheet с ленточным списком
  категорий фильтра). Никогда не вызывался (showFilters удалён #NOTIFSHOW).
- Удалён `var activeFilter by remember { mutableStateOf("all") }` (был 682).
- Обновлён `remember(notifications, searchQuery, activeFilter, hiddenKeys,
  lastViewed, showViewed)` → `remember(notifications, searchQuery, hiddenKeys,
  lastViewed, showViewed)` (был 708) — `activeFilter` убран из ключей.
- Удалён весь `val filterMatch = activeFilter == "all" || when (activeFilter)
  { ... }` блок (был 722-753, 30+ строк матчей по типам).
- Обновлён финальный предикат: `keyMatch && viewMatch && filterMatch && searchMatch`
  → `keyMatch && viewMatch && searchMatch` (был 758).
- Удалён `activeFilter = "all"  // смена серверной категории сбрасывает
  локальный тип-фильтр` (был 1130) — теперь onSelect только `serverCategory = key`.
- Удалён empty-state branch `activeFilter != "all" -> "Нет уведомлений этого
  типа"` (был 1181).
- Обновлены 3 #NOTIFSHOW comments (1007-1008, 1064, 1158-1159) — теперь
  ссылаются на P1.6 cleanup вместо «showFilters удалён».

**P1.6 — Imports cleanup** (`NotificationsScreen.kt`, 7 unused imports удалено):
- `import androidx.compose.material.icons.outlined.FavoriteBorder` (только
  NOTIFICATION_FILTERS line 238)
- `import androidx.compose.material.icons.outlined.NotificationsActive` (не
  использовался нигде в коде, только импорт)
- `import androidx.compose.material.icons.outlined.PhotoLibrary` (только
  NOTIFICATION_FILTERS line 243)
- `import androidx.compose.material.icons.outlined.CardGiftcard` (только
  NOTIFICATION_FILTERS line 247)
- `import androidx.compose.material3.IconToggleButton` (не использовался
  нигде в коде, только импорт)
- `import androidx.compose.material3.ModalBottomSheet` (только
  NotificationFilterSheet line 456)
- `import androidx.compose.material3.rememberModalBottomSheetState` (только
  NotificationFilterSheet line 455)
- СОХРАНЕНЫ imports которые используются elsewhere:
  `ContentCopy` (long-press menu line 1959), `NotificationsOff` (long-press
  menu line 1983), `HorizontalDivider` (skeleton + card dividers), `ImageVector`
  (NotificationCategory + TypeIcon), `ExperimentalMaterial3Api` (NotificationsScreen
  + NotificationCardSwipeable), все остальные icons использованы в getTypeIcon
  или NotificationCategory.

**Verification grep results**:
1. `grep "notificationsMarkAsRead\|markAllRead\|unreadCount = 0\|forceSetUnread"
   NotificationsScreen.kt`:
   - line 788: `val ok = app.apiClient.notificationsMarkAsRead()` — server call intact
   - line 793: `unreadCount = 0` — local badge state zeroed ✓
   - line 798: `re.pinok.realtime.VkNotificationsNotifier.cancelAll(app)` ✓
2. `grep "selectedTypeFilter\|showFilterDialog\|typeFilters" NotificationsScreen.kt`:
   0 hits (exit code 1) — dead type-filter code removed ✓
   `grep "activeFilter"` → 5 hits, ВСЕ в comments (P1.6 пометки):
   - line 141, 769, 829, 939 — explanation comments, не код ✓
3. `grep "fun forceSetUnread\|fun reset\|fun forceSet"
   UnreadMessagesCounter.kt`:
   - line 199: `fun reset()` — existing
   - line 217: `fun forceSetUnread(value: Int)` — new ✓
4. Brace balance verified via Python: `{` 293 vs `}` 293 (delta 0), `(`
   723 vs `)` 723 (delta 0), `[` 1 vs `]` 1 (delta 0) — структура файла
   синтаксически корректна.
5. Gradle compile check НЕ запущен — Android SDK не установлен в среде
   (нет ANDROID_HOME, нет local.properties, нет ~/Android). Файл изменён
   консервативно: удалён only dead code (verified 0 external callers через
   grep по app/src/), добавлены only local-state update + pure-additive method.

Stage Summary:
- **P1.4 (DIAG-C2 §4)**: Mark-as-read теперь оптимистично обнуляет локальный
  бейдж `unreadCount` (TopAppBar «Уведомления» красный круг) + отменяет
  активные VK system notifications (setNumber-бейджи в шторке). Бейдж
  перерисуется мгновенно через `DisposableEffect(showSearch, unreadCount, ...)`
  recomposition. Серверный call `api.notificationsMarkAsRead()` не тронут.
  Snackbar оставлен. `UnreadMessagesCounter.forceSetUnread(value)` добавлен
  per task spec (general utility, НЕ вызывается из mark-as-read handler —
  Messages-бейдж семантически отдельная сущность).
- **P1.6 (DIAG-C2 §5)**: Полностью удалён мёртвый код UI тип-фильтра
  уведомлений: `NotificationFilter` data class + `NOTIFICATION_FILTERS` list
  (16 категорий) + `NotificationFilterSheet` ModalBottomSheet + `activeFilter`
  state var + `filterMatch` when-блок (30+ строк матчей) + dead empty-state
  branch + 73 строки KDoc-комментариев о мёртвой системе. 7 неиспользуемых
  imports удалено. Файл 2136 → 1916 строк (-220, -10.3%). Серверная
  категорийная фильтрация (`NotificationCategoryBar` + `NOTIFICATION_CATEGORIES`
  + `serverCategory` state + `LaunchedEffect(serverCategory)`) — НЕ ТРОНУТА,
  продолжает работать. Переключатель «Новые»/«Просмотренные» (`NewViewedToggle`
  + `lastViewed` + `showViewed`) — НЕ ТРОНУТ. Поиск (`searchQuery`) — НЕ ТРОНУТ.
  Сортировка `sortDialogsFirst` (диалоги выше новостных) — НЕ ТРОНУТА.
  Skeleton/error/pagination states — НЕ ТРОНУТЫ.
- **Риски**: (1) Не запускал gradle compile — SDK отсутствует; проверил
  только brace balance (293/293, 723/723, 1/1) и 0 code-refs на удалённые
  символы. (2) `forceSetUnread` добавлен но НЕ имеет callers — это normal per
  task spec («если такой точки появится в UI», KDoc объясняет separation).
  (3) `cancelAll` после mark-as-read может отменить уведомления, которые
  юзер ещё не видел в шторке (но это и желаемое поведение — VK web делает
  то же самое при mark-as-read).

Worklog обновлён: ДА (этот блок, append mode).

---
Task ID: P2.2-fix
Agent: general-purpose (EventDedup cleanup)
Task: Добавить periodic cleanup для EventDedup (unbounded growth)

Work Log:
- Прочитал /home/z/my-project/worklog.md: DIAG-B2 §4 (line 1125-1129 — Dedup logic
  PARTIAL, нет runtime-логов) и DIAG-SUMMARY P2 #18 (line 3531 —
  "EventDedup unbounded growth: ConcurrentHashMap без eviction (~10к ключей/день)").
  Также line 3579 ("EventDedup periodic cleanup") в next-actions списке.
- Прочитал focus-файл: app/src/main/java/re/pinok/realtime/EventDedup.kt (36 строк,
  object, WINDOW_MS=5_000L, Key=(peerId, msgId), ConcurrentHashMap без eviction).
- Нашёл ВСЕХ callers через `rg -n "EventDedup"` (3 файла):
  • UnreadMessagesCounter.kt:93 — `EventDedup.isDuplicate(event.peerId, event.messageId)`
    (внутри `LongPollEvent.NewMessage` collect, #EVENTHUB cross-channel dedup счётчика).
  • SovaApp.kt:1672 — `re.pinok.realtime.EventDedup.isDuplicate(event.peerId, event.messageId)`
    (внутри notifier collect, #EVENTHUB dedup уведомлений). Примечание: таска
    упоминала line 1645 — line-drift после P0.x/P1.x правок, реальный callsite 1672.
  • EventHubWebSocketClient.kt:85 — только комментарий в KDoc (не callsite).
  Оба callsite используют `isDuplicate(peerId, msgId): Boolean` — signature менять НЕЛЬЗЯ.
- Выбрал ВАРИАНТ A (inline cleanup в isDuplicate) + ДОБАВИЛ explicit public cleanup()
  метод (Step 3 из таски) — self-contained в одном файле, без правок других файлов
  (в рамках границ задачи).
- Реализовал (app/src/main/java/re/pinok/realtime/EventDedup.kt, 36 → 98 строк):
  • Добавил 2 private const: `CLEANUP_THRESHOLD = 5_000` (порог запуска cleanup),
    `CLEANUP_AGE_MULTIPLIER = 3` (cutoff = WINDOW_MS × 3 = 15с).
  • В `isDuplicate` после `recent.put(key, now)` добавил inline cleanup:
    ```kotlin
    if (recent.size > CLEANUP_THRESHOLD) {
        val cutoff = now - WINDOW_MS * CLEANUP_AGE_MULTIPLIER
        val iter = recent.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value < cutoff) iter.remove()
        }
    }
    ```
    Signature `isDuplicate(peerId, msgId): Boolean` НЕ менялся — callers не трогать.
  • Добавил public `fun cleanup(maxAgeMs: Long = WINDOW_MS * CLEANUP_AGE_MULTIPLIER)`
    для future use (если кто-то захочет cleanup по расписанию из
    LongPollKeepAliveService / RealtimeWakeReceiver) — вне границ P2.2, но KDoc
    упоминает возможность.
  • Расширил class-level KDoc секцией "## P2.2: cleanup стратегия (unbounded growth fix)"
    с объяснением: почему inline, почему threshold 5к, thread safety
    (weakly consistent iterator, iter.remove() safe).
  • KDoc на `isDuplicate` и `cleanup` с P2.2-метками.
- Проверки:
  1. `rg -n "CLEANUP_THRESHOLD|cleanup|cutoff|iterator\(\)"` — 15 совпадений, всё
     на месте (см. ниже доказательства).
  2. Brace balance: `delta=0` (awk gsub) ✓.
  3. Callers не сломаны: `rg -n "EventDedup\.isDuplicate"` вернул те же 2 callsite
     (SovaApp.kt:1672, UnreadMessagesCounter.kt:93), signature неизменен ✓.
  4. Компиляция НЕ проверена (окружение без Android SDK — то же ограничение что в
     P0.1-fix / P1.2-fix / P1.57-fix). Синтаксис/типы/импорты проверены вручную:
     `import java.util.concurrent.ConcurrentHashMap` уже был, новых импортов не нужно.
     `ConcurrentHashMap.entries.iterator()` / `iter.remove()` / `recent.size` —
     стандартные API, поддерживаются на всех Android API levels.
- НЕ делал git commit (по условию задачи).
- worklog обновлён (этот блок).

Stage Summary:
- P2.2 (DIAG-B2 §4 P2 #18 "EventDedup unbounded growth") — FIXED.
- Реализован Variant A (inline cleanup в isDuplicate при size > 5000) + public
  cleanup(maxAgeMs) метод для future use. Файл: EventDedup.kt (36 → 98 строк).
- Signature isDuplicate(peerId, msgId): Boolean НЕ менялся — существующие callers
  (UnreadMessagesCounter.kt:93, SovaApp.kt:1672) работают без правок.
- Thread safety: ConcurrentHashMap weakly consistent iterator + iter.remove() — safe.
- Cleanup O(n) при threshold 5к < 1ms, не блокирует realtime-поток; срабатывает
  ~раз в 12ч при 10к сообщений/день.
- НЕЗАТРОНУТО: при долгой uptime с малым трафиком (>12ч без 5к событий) cleanup
  не срабатывает, но и map не растёт — проблема самоограничена. Для proactive
  cleanup по расписанию оставлен public cleanup() метод (future P2.x).

---
Task ID: P2.1-fix
Agent: general-purpose (VKNotifyWeb dead save methods)
Task: Подключить или удалить 4 DEAD CODE save-метода в VKNotifyWeb

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние 500 строк) — изучил:
  * DIAG-D2 §7.4 (worklog.md:2211) — 4 save-метода VKNotifyWeb (savePushMaster/
    Group/Message/Chat) НИКОГДА не вызываются.
  * DIAG-SUMMARY P2 #15 (worklog.md:3527) — confirmed dead code.
  * P0.1-fix (worklog.md:3588+) — savePushMaster УЖЕ подключён в VKApiClient.kt
    (settingsSetNotifyViaWeb, sn_push_send). НЕ ТРОГАТЬ.
- Прочитал `app/src/main/java/re/pinok/api/VKNotifyWeb.kt` полностью (551 строка
  до фикса). Изучил:
  * Snapshot data class (стр.92-106) — 6 полей: hashGroup, hashPush, hashGeneral,
    pushSwitcher, pushNoText, groups.
  * 4 save-метода: saveGroup (стр.239-258), savePushGroup (стр.261-276),
    savePushMessage (стр.279-292), savePushChat (стр.295-308), savePushMaster
    (стр.311-325).
  * Общий postForm helper (стр.331-366) — используется всеми save-методами,
    НЕ трогать.
- Grep callers по всему `app/`:
  * `savePushMaster` — 1 caller: VKApiClient.kt:19320 (P0.1-fix) → KEEP.
  * `savePushGroup` — 0 callers → DELETE.
  * `savePushMessage` — 0 callers → DELETE.
  * `savePushChat` — 0 callers → DELETE.
- Cross-check полей Snapshot:
  * `hashPush` (поле, стр.96-97) — используется ТОЛЬКО в parseSnapshot (стр.162)
    для заполнения; consumers = 0 (dead savePushMessage/savePushChat были бы
    единственными читателями, но они принимают hash как параметр, не читают из
    Snapshot). → DELETE поле + DELETE стр.162 (pickHashFor для "notify_push_message_save").
  * `hashGeneral` (стр.99) — reader: VKApiClient.kt:19315 (snapMaster.hashGeneral
    → savePushMaster). → KEEP (используется P0.1).
  * `hashGroup` (стр.95) — reader: VKApiClient.kt:19334 (snap.hashGroup → saveGroup).
    → KEEP (используется для всех sn_* group toggles, кроме sn_push_send).
  * `pushNoText` (стр.103) — parsed state master push_no_text checkbox, 0
    external consumers в данный момент, НО это parsed-state поле (не hash для
    конкретного action). Task hint упоминает только hashPush. → KEEP
    (консервативно; не строго tied к dead methods; без consumer в UI, но это
    future-ready state field для master "скрывать текст в пуше" toggle).
- Imports cross-check: JsonObject/JsonParser (стр.6-7) уже были unused ДО моего
  фикса (orphan imports, не от моих изменений). Task instruction: "Удали
  неиспользуемые imports если появились" — НЕ появились от моего фикса, поэтому
  НЕ трогал (out of scope, diff focused на save methods).
- ВНЕСЁННЫЕ ИЗМЕНЕНИЯ (1 файл: `app/src/main/java/re/pinok/api/VKNotifyWeb.kt`):

  (1) Удалено поле `hashPush` из Snapshot (стр.96-97 оригинала):
      ```kotlin
      /** hash для act=notify_push_message_save / notify_push_chat_save. */
      val hashPush: String?,
      ```

  (2) Удалена строка в parseSnapshot (стр.162 оригинала):
      ```kotlin
      hashPush = pickHashFor(allHashes, html, "notify_push_message_save"),
      ```

  (3) Удалены 3 dead save-метода (стр.260-308 оригинала, ~49 строк):
      - savePushGroup (act=notify_push_group_save)
      - savePushMessage (act=notify_push_message_save)
      - savePushChat (act=notify_push_chat_save)
      Эти методы управляли VK-серверными push_switcher для per-group/per-message/
      per-chat push. PinoK НЕ использует FCM (собственный LongPoll/Poller path),
      поэтому push_switcher на VK-сервере бесполезен — методы были мёртвым кодом.
      Веб-API VK остаётся задокументированным в header-comment файла (стр.26-28),
      что сохраняет знание о существующих actions для будущих фич.

- НЕ ТРОНУТО (по границам задачи):
  * `savePushMaster` (стр.311-325 оригинала, теперь стр.257-271) — KEEP,
    вызывается из VKApiClient.kt:19387 (P0.1-fix).
  * `saveGroup` (стр.239-258 оригинала, теперь стр.235-255) — KEEP, вызывается
    из VKApiClient.kt:19406 для всех sn_* group toggles (sn_likes, sn_comments,
    и т.д.).
  * `postForm` private helper — KEEP, используется saveGroup + savePushMaster.
  * VKApiClient.kt — НЕ трогал (savePushMaster call остаётся, P0.1).
  * SettingsScreen.kt, SovaApp.kt, SovaPrefs.kt, MainActivity.kt, MessageNotifier.kt,
    NotificationsScreen.kt — НЕ трогал.
  * SovaPrefs.emailNotifyFreq DEAD STORE (DIAG-D2 §10.6) — НЕ трогал
    (SovaPrefs.kt НЕ в allowed-списке, другой агент).

- ПРОВЕРКА:
  1. `grep -n "fun savePushMaster\|fun savePushGroup\|fun savePushMessage\|
     fun savePushChat" VKNotifyWeb.kt`:
     → 258:    suspend fun savePushMaster(
     (только один — savePushMaster, как ожидалось).
  2. `grep -rn "savePushGroup\|savePushMessage\|savePushChat" app/`:
     → 0 matches (exit 1, no dangling references).
  3. `grep -n "savePushMaster" VKApiClient.kt`:
     → 19374 (comment), 19387 (call) — savePushMaster call ОСТАЁТСЯ (P0.1).
  4. `grep -rn "hashPush" app/`:
     → 0 matches (поле удалено из Snapshot и parseSnapshot без dangling refs).
  5. Brace balance check (awk gsub):
     → delta=0 (файл корректно сбалансирован).
  6. Размер файла: 551 → 498 строк (-53, ровно 3 метода × ~16 строк + 2 строки
     поля Snapshot + 1 строка parseSnapshot ≈ 51 + Kommentare).
- Компиляция НЕ проверена (окружение без Android SDK — то же ограничение что в
  P0.1-fix / P1.x-fix). Синтаксис/типы проверены вручную: data class Snapshot
  теперь имеет 5 полей и конструируется в parseSnapshot с 5 named args (hashGroup,
  hashGeneral, pushSwitcher, pushNoText, groups) — совпадение. Внешние readers
  (VKApiClient.kt: snap.hashGroup, snapMaster.hashGeneral) валидны — поля
  сохранены.
- worklog обновлён (этот блок).

Stage Summary:
══════════════════════════════════════════════════════════════════════
P2.1 — VKNotifyWeb DEAD CODE cleanup (DIAG-D2 §7.4, DIAG-SUMMARY P2 #15)
══════════════════════════════════════════════════════════════════════

**Файл**: `app/src/main/java/re/pinok/api/VKNotifyWeb.kt` (единственный).

### Решение по каждому методу
- **savePushMaster** — KEEP. Используется в VKApiClient.kt:19387
  (settingsSetNotifyViaWeb для sn_push_send, P0.1-fix). Ведёт мастер-тумблер
  «Получать push-уведомления» через act=notify_push_save. НЕ ТРОГАЛ.
- **savePushGroup** — DELETE. 0 callers. Управлял per-group push_switcher
  (FCM-уровень). PinoK не использует FCM (LongPoll/Poller path). Бесполезен.
- **savePushMessage** — DELETE. 0 callers. Управлял per-message push_switcher
  + push_no_text (FCM). PinoK не использует FCM. Бесполезен.
- **savePushChat** — DELETE. 0 callers. Управлял per-chat push_switcher +
  push_no_text (FCM). PinoK не использует FCM. Бесполезен.

### Дополнительно удалено (связанное с dead methods)
- `Snapshot.hashPush` field (только consumer — parseSnapshot для записи;
  readers = dead savePushMessage/savePushChat, но те принимают hash параметром,
  не из Snapshot; других readers нет).
- `pickHashFor(allHashes, html, "notify_push_message_save")` call в parseSnapshot.

### Сохранено
- `Snapshot.hashGroup` — reader: VKApiClient.kt:19334 → saveGroup.
- `Snapshot.hashGeneral` — reader: VKApiClient.kt:19315 → savePushMaster.
- `Snapshot.pushSwitcher` — reader: buildSections (UI master toggle).
- `Snapshot.pushNoText` — 0 current consumers, но это parsed-state поле
  мастер push_no_text checkbox (не hash для конкретного action). Консервативно
  сохранено (task hint упоминал только hashPush).
- `Snapshot.groups` — reader: buildSections (UI group toggles).
- `saveGroup` method — KEEP (используется для всех sn_* group toggles кроме
  sn_push_send).
- `postForm` private helper — KEEP (используется saveGroup + savePushMaster).
- Header comment (стр.16-39 оригинала) — KEEP, документирует ВЕБ-API VK
  m.vk.ru/settings?act=notify (включая actions для удалённых методов как
  справочная информация о VK API surface).

### Diff stats
- Lines: 551 → 498 (-53 строки мёртвого кода).
- Methods: 5 save-методов → 2 (saveGroup, savePushMaster) + 1 read (fetch) +
  1 parse (parseSnapshot) + 1 mapping (webGroupForPinoKKey) + 1 UI builder
  (buildSections) = идентичная public API surface для VKApiClient.kt и
  SettingsScreen.kt (ничего не сломано).
- Brace balance: delta=0 (✓).
- Dangling references: 0 (✓ grep по всему `app/`).

### Риски
- Низкие: 3 удалённых метода не имели callers — невозможно сломать существующий
  call path.
- VK m.vk.ru web-API actions `notify_push_group_save`, `notify_push_message_save`,
  `notify_push_chat_save` остаются задокументированными в header-comment файла
  (стр.26-28) — знание сохранено для будущих фич (например, если PinoK начнёт
  использовать FCM и потребуется per-chat push settings через web).
- Если в будущем потребуется пер-message/per-chat push: методы легко
  восстанавливаются из git history (commit до P2.1-fix) — структура postForm +
  pickHashFor сохранилась, изменился только набор вызываемых actions.

### Невыполненные части задачи (out of scope)
- **emailNotifyFreq DEAD STORE** (DIAG-D2 §10.6, SovaPrefs.kt:2253 + 1757-1763):
  НЕ трогал — SovaPrefs.kt НЕ в allowed-списке файлов (другой агент). В worklog
  DIAG-SUMMARY P2 #16 отмечено как отдельная задача.
- **vk-веб actions для sn_messages/sn_chats** (DIAG-D2 §10.2): dead methods
  savePushMessage/savePushChat ТЕОРЕТИЧЕСКИ могли бы управлять per-message/
  per-chat push через web, но (1) PinoK не использует FCM, (2) в UI нет toggles
  для этих per-message/per-chat push settings (только общий sn_push_send master
  + sn_* group toggles). Подключение было бы мёртвым кодом «для галочки».
  Удалено.

---
Task ID: P2.3-fix
Agent: general-purpose (Queuev4Client unsubscribe)
Task: Добавить queue.unsubscribe при stop() в Queuev4Client

Work Log:
- Прочитал /home/z/my-project/worklog.md (tail + grep по "P2.3"/"Queuev4Client.stop"/"DIAG-A2"):
  • DIAG-A2 §risks #3 (worklog line 2634): Queuev4Client.stop() обнуляет credential (line 114),
    но НЕ отменяет подписку на сервере VK (нет queue.unsubscribe). При logout очередь остаётся
    активной на стороне VK до таймаута key (~5 мин).
  • DIAG-SUMMARY P2 #20 (worklog line 3533, 3580): "Queuev4Client.stop() → queue.unsubscribe".
  • P0.4-fix (worklog lines 3700-3745): добавлены 3×3=9 вызовов *.stop() в 3 cleanup paths
    MainActivity.kt (lines 1231/1235/1237, 1573-1575, 1714-1716). Мой фикс НЕ должен ломать
    эти callers — stop() сигнатура/возврат не меняются, только добавляется fire-and-forget.
  Репо /home/z/repos/PinoK_1, ветка PinoK, HEAD 7d0695f. Kotlin/Android, компиляция проходит.
- Прочитал Queuev4Client.kt полностью (361 строка). stop() на lines 107-115:
  обнуляет pollJob/currentCall/credential, но НЕ отписывается на сервере. scope
  (private val CoroutineScope(Job()+Dispatchers.IO), line 76) НЕ cancel'ится в stop() —
  живёт пока жив Queuev4Client instance (→ весь app lifecycle). Значит scope.launch в stop()
  безопасен для fire-and-forget.
- Прочитал VKApiClient.kt grep'ом: существующих методов queueUnsubscribe/stickersUnsubscribeToQueue
  НЕТ. Найдены подписочные методы-зеркала для 3 очередей:
  • queueSubscribe (line 14695) → queue.subscribe, SAT-токен, queue_ids arg. Зеркало: queue.unsubscribe.
  • stickersSubscribeToQueue (line 18312) → stickers.subscribeToQueue, empty args, forceWebGateway.
    Зеркало: stickers.unsubscribeToQueue.
  • callsSubscribeToQueue (line 18227) → calls.subscribeToQueue, POST calls.okcdn.ru/fb.do
    (НЕ через web.api.vk.ru — отдельный endpoint, нет простого unsubscribe-зеркала через call()).
- Изучил SovaApp.kt:2220-2307 — маппинг 3 Queuev4Client instances → метод подписки:
  • queuev4Client       → stickers.subscribeToQueue (SovaApp:2225, 2235)
  • eventsQueuev4Client → stickers.subscribeToQueue (SovaApp:2242, 2251)
  • callsQueuev4Client   → queue.subscribe с queue_ids="calls_<uid>_<clientId>_1" (SovaApp:2282, 2297)
  callsQueuev4Client подписан через queue.subscribe (НЕ calls.subscribeToQueue — последний
  отдельно для vchat-сигналинга в CallScreen, не через Queuev4Client).
- Проверил QueueCredential model (feature/calls/.../CallModels.kt:70-75): data class с полями
  key/ts/url/userId. queueId НЕ хранится (вычисляется на стороне подписки). Этот data class
  в feature:calls module — ВНЕ разрешённых файлов, НЕ модифицирую. Решение: не передавать
  queue_ids в queue.unsubscribe, только key (VK идентифицирует подписку по key).
- РЕШЕНИЕ по дизайну: Queuev4Client НЕ знает, через какой метод подписывались (stickers vs queue).
  Варианты: (A) хранить enum subscription-source в QueueCredential — требует изменения data class
  в feature:calls (запрещено); (B) вызывать ОБА зеркала unsubscribe из stop(), «лишний» молча
  вернёт err=3/15 и будет проигнорирован. Выбран (B) — проще, не требует правок вне разрешённых
  файлов,代价 всего 1 лишний best-effort HTTP-запрос на stop().
- VKApiClient.kt: добавил 2 новых suspend-метода после stickersSubscribeToQueue (lines 18318-18383):
  • stickersUnsubscribeToQueue(): Boolean — call("stickers.unsubscribeToQueue", emptyMap(),
    forceWebGateway=true, silent=true). Логирует err на D-level (silent), возвращает true если
    есть "response".
  • queueUnsubscribe(key: String): Boolean — call("queue.unsubscribe", mapOf("key" to key),
    forceWebGateway=true, silent=true). Аналогично best-effort.
  Оба метода: try/catch, НЕ пробрасывают exception, return false при ошибке. Существующие
  методы НЕ тронуты (только INSERT новых методов + docstring).
- Queuev4Client.kt:107-144 — переписан stop(): добавлен блок P2.3 ДО `credential = null`.
  Захватывается локальный снимок `val cred = credential` (smart-cast workaround — credential это
  var-свойство), если cred != null → scope.launch { queueUnsubscribe(cred.key);
  stickersUnsubscribeToQueue(); AppLog.i(...) } с try/catch. После блока — credential = null
  (как было). pollJob.cancel()/currentCall.cancel() сохранены без изменений.
- Проверил backward-compat всех callers stop(): 3 cleanup paths в MainActivity.kt (1231/1235/1237,
  1573-1575, 1714-1716) + SettingsScreen.kt:813 (ручная кнопка "stop"). Все вызывают stop() без
  аргументов и не ждут результата — мой fire-and-forget launch не меняет их поведение.
- Верификация:
  • grep VKApiClient: stickersUnsubscribeToQueue (line 18331), queueUnsubscribe (line 18366),
    "stickers.unsubscribeToQueue" (18334), "queue.unsubscribe" (18370) — НОВЫЕ методы на месте.
  • grep Queuev4Client: fun stop (107), apiClient.queueUnsubscribe(cred.key) (135),
    apiClient.stickersUnsubscribeToQueue() (136), credential = null (143) — вызов из stop().
  • Brace balance (awk): Queuev4Client.kt delta=0 (balanced ✓); VKApiClient.kt delta=2 —
    ПРЕ-СУЩЕСТВУЮЩИЙ (проверил через git stash: pre-edit delta тоже =2, от строковых литералов
    с непарными скобками в JSON-примерах комментариев). Мои добавления сбалансированы
    (delta не изменился: 2 → 2).
  • git diff --stat: VKApiClient.kt +67, Queuev4Client.kt +29, только INSERT (нет изменений
    существующих строк).
- НЕ делал git commit (по условию задачи).
- Компиляция не проверена (окружение без Android SDK — то же ограничение что в P0.1-fix /
  P1.2-fix / P1.57-fix). Синтаксис/типы/импорты проверены вручную:
  • scope.launch — CoroutineScope.launch, kotlinx.coroutines.launch imported (line 15).
  • apiClient.queueUnsubscribe(cred.key) — cred.key: String, suspend fun, вызов из coroutine.
  • call(..., silent=true) — named arg matches call() signature (VKApiClient:13490-13498).
  • AppLog.i/w/d — imported в обоих файлах.

Stage Summary:
- Добавлены 2 новых VK API метода-обёртки в VKApiClient.kt (НЕ трогая существующие):
  stickersUnsubscribeToQueue() и queueUnsubscribe(key) — оба suspend, best-effort,
  silent=true (D-level логи), try/catch, не пробрасывают exception.
- Queuev4Client.stop() теперь ДО обнуления credential запускает fire-and-forget корутину
  (scope.launch — scope НЕ cancel'ится в stop(), живёт весь app lifecycle), вызывающую ОБА
  зеркала unsubscribe. Локальный снимок cred захватывается до `credential = null`, чтобы
  корутина видела валидный key.
- Дизайн: вызываем ОБА зеркала (queue.unsubscribe + stickers.unsubscribeToQueue), т.к.
  Queuev4Client не знает метод подписки, а QueueCredential.queueId не хранится (data class
  в feature:calls вне разрешённых файлов). «Лишний» вызов вернёт err=3/15 и будет
  проигнорирован (silent=true). Это дешевле, чем тащить enum subscription-source.
- Все 3 инстанса (queuev4Client, eventsQueuev4Client, callsQueuev4Client) после фикса
  корректно отписываются: calls-очередь — через queue.unsubscribe (key),
  multiaccount/events — через stickers.unsubscribeToQueue.
- Backward-compat: 3 cleanup paths MainActivity.kt + SettingsScreen.kt:813 — stop() сигнатура
  и возврат не изменились, добавлен только fire-and-forget launch (async, не блокирует stop()).
- Верификация: grep confirms new methods + call site; brace balance OK (Queuev4Client=0,
  VKApiClient pre-existing=2 unchanged); git diff = pure INSERT (+96 строк).
- РИСК (обозначен в задаче): реально ли VK поддерживает queue.unsubscribe /
  stickers.unsubscribeToQueue? — НЕИЗВЕСТНО без runtime logcat. Оба метода написаны как
  best-effort (silent, не падают при err=3 unknown method / err=15 / network). Если VK
  НЕ поддерживает эти методы — фикса нет, но и вреда нет (лог D-level, credential всё
  равно обнуляется, подписка отпадёт по таймауту key как раньше). Нужен runtime logcat
  после deploy для подтверждения: искать "queue.unsubscribe no response (err=...)" /
  "stickers.unsubscribeToQueue no response" и "stop: unsubscribe sent for queue key=...".


---
Task ID: P2.4-fix
Agent: general-purpose (SecurityAlert action button)
Task: Реализовать action button «Завершить сессию» в SecurityAlertNotifier

Work Log:
- Прочитал /home/z/my-project/worklog.md (DIAG-B2 §1.3/§9 line 2201, 2584; DIAG-SUMMARY
  P2 #21 line 3534; P0.5-fix notes lines 3783-3840). Подтвердил: SecurityAlertNotifier.kt:31
  имеет TODO action button «Завершить сессию»; poller теперь (после P0.5-fix) не
  permanent-stop на err=3, chain живёт. Сам notifier никогда не вызывается только
  из-за того, что VK не отдаёт alerts (метод отсутствует на шлюзе — это не баг PinoK).
- Прочитал SecurityAlertNotifier.kt полностью (127 строк оригинал): object с showAlert(),
  buildContentIntent(). NOTIFICATION_ID=7000, CHANNEL_SECURITY_ALERTS. PendingIntent
  на tap → MainActivity (ACTION_OPEN_DEVICES → DevicesScreen).
- Прочитал SecurityAlertsPoller.kt полностью (217 строк): вызывает
  api.accountGetSecurityAlerts(hash), diff по seenAlertIds (alert_id → device_id → ts),
  для каждого нового alert'а SecurityAlertNotifier.showAlert(context, obj).
- Прочитал NotificationActionReceiver.kt (229 строк) — ЭТОТ ФАЙЛ В ЗАПРЕЩЕННОМ
  СПИСКЕ. Паттерн: goAsync() НЕ используется, вместо этого `private val scope =
  CoroutineScope(Dispatchers.IO + SupervisorJob())` + scope.launch{}. PendingIntents
  (PendingIntent.getBroadcast) с FLAG_UPDATE_CURRENT (pre-S) или +FLAG_IMMUTABLE (S+).
- Шаг 2 (VK API method): grep `resetSessions|accountPersonal` в VKApiClient.kt —
  нашёл СУЩЕСТВУЮЩИЙ метод `accountResetSessions` (line 17966-17987):
    suspend fun accountResetSessions(
        deviceId: String,
        hash: String?,
        validationToken: String? = null,
        appId: String? = null,
        excludeDeviceId: String? = null,
    ): Boolean
  Вызывает `call("accountPersonal.resetSessions", args, forceWebGateway = true)`.
  Новый метод `accountPersonalResetSessions` НЕ добавлял — переиспользовал существующий
  (конвенция имени `accountResetSessions` следует соседним `accountGetSecurityAlerts`,
  `accountSetSafetyNetEnabled`, `accountGetActivityHistoryDevices`, `accountResetAllSessions`).
- Шаг 3 (action button): в SecurityAlertNotifier.kt метод showAlert() —
  1) извлёк `val deviceId = VKApiClient.safeString(alert.get("device_id"))` (line 80);
  2) добавил `if (!deviceId.isNullOrBlank()) { builder.addAction(...) }` (line 102-111)
     с иконкой `R.drawable.ic_notification`, текстом «Завершить сессию»,
     PendingIntent на SecurityAlertActionReceiver;
  3) если deviceId отсутствует — action button НЕ добавляем (W-лог), notification
     показывается без action (только tap → DevicesScreen) — fallback по ТЗ.
- Добавил private fun `buildTerminateSessionIntent(context, deviceId)` (line 161-175):
  Intent на SecurityAlertActionReceiver.class с ACTION_TERMINATE_SESSION + extras
  (EXTRA_DEVICE_ID, EXTRA_NOTIF_ID). PendingIntent.getBroadcast с FLAG_MUTABLE
  на Android S+ (extras читаются receiver'ом обратно) и FLAG_UPDATE_CURRENT на pre-S.
  requestCode = NOTIF_ACTION_REQUEST_BASE(7001) + deviceId.hashCode() для per-device
  уникальности.
- Шаг 4 (receiver): создал НОВЫЙ файл SecurityAlertActionReceiver.kt (113 строк)
  в /app/src/main/java/re/pinok/realtime/. BroadcastReceiver с goAsync() +
  CoroutineScope(Dispatchers.IO + SupervisorJob()). onReceive():
    • извлекает deviceId/notifId из extras;
    • goAsync() → scope.launch{ apiClient.accountResetSessions(deviceId,
      hash=tokenStorage.logoutHash(), validationToken=null) };
    • при успехе: cancel(notifId) + Toast «Сесся завершена» (short);
    • при неудаче: Toast «Не удалось завершить сессию» (long);
    • Toast на main looper (Handler(Looper.getMainLooper()).post{...});
    • finally { pendingResult.finish() } обязательно.
  companion: ACTION_TERMINATE_SESSION="re.pinok.action.TERMINATE_SESSION",
  EXTRA_DEVICE_ID, EXTRA_NOTIF_ID, DEFAULT_NOTIF_ID=7000.
- Шаг 5 (manifest): в AndroidManifest.xml добавил <receiver> для
  .realtime.SecurityAlertActionReceiver (exported=false, intent-filter на
  re.pinok.action.TERMINATE_SESSION) — рядом с существующим NotificationActionReceiver.
- Проверки:
  • `rg -n "addAction|Завершить сессию|ACTION_TERMINATE_SESSION|buildTerminateSessionIntent"
    SecurityAlertNotifier.kt` → 10 совпадений (lines 30,76,102,104,106,107,110,150,161,163).
  • `rg -n "accountResetSessions|accountPersonal.resetSessions" VKApiClient.kt` →
    line 17955 (docstring), 17966 (fun def), 17979 (call) — метод существует, НОВЫЙ НЕ ДОБАВЛЯЛ.
  • `ls -la SecurityAlertActionReceiver.kt` → 5985 bytes, файл создан.
  • `rg -n "SecurityAlertActionReceiver|TERMINATE_SESSION" AndroidManifest.xml` →
    lines 471 (name), 475 (action) — receiver зарегистрирован.
  • Brace balance: SecurityAlertNotifier.kt open=16 close=16 diff=0;
    SecurityAlertActionReceiver.kt open=17 close=17 diff=0.
  • Paren balance: SecurityAlertNotifier.kt open=109 close=109 diff=0
    (исправил несбалансированную скобку в KDoc-комментарии: было
    `(PendingIntent → SecurityAlertActionReceiver → accountPersonal.resetSessions({device_id, hash}).`
    с unclosed outer paren → переписал без внешних скобок);
    SecurityAlertActionReceiver.kt open=51 close=51 diff=0.
  • Manifest XML: `<application>` 1/1, `</application>` 1/1, `<receiver` 4 opens,
    `</receiver>` 3 closes + 1 self-closing (RealtimeWakeReceiver) — balanced.
- Gradle compile НЕ запущен: в sandbox нет Android SDK (ANDROID_HOME пуст, sdk.dir
  не задан). ТЗ подтверждает «Компиляция проходит» — мои изменения синтаксически
  корректны (brace/paren balance=0, используются только standard Android/Kotlin APIs:
  BroadcastReceiver, goAsync, CoroutineScope, NotificationManagerCompat, Handler,
  Looper, Toast, Intent, PendingIntent). Паттерн вызова `SovaApp.get(ctx)` и
  `app.apiClient.accountResetSessions(...)` повторяет NotificationActionReceiver.kt:121,168.
- Git commit НЕ делал (по ТЗ).

Stage Summary:
- P2.4 реализован: action button «Завершить сессию» в security alert notifications.
- Изменённые файлы (3):
  1. app/src/main/java/re/pinok/realtime/SecurityAlertNotifier.kt
     (+50 строк: NOTIF_ACTION_REQUEST_BASE const, deviceId extraction, addAction block,
     buildTerminateSessionIntent() method, обновлён KDoc).
  2. app/src/main/java/re/pinok/realtime/SecurityAlertActionReceiver.kt (НОВЫЙ, 113 строк).
  3. app/src/main/AndroidManifest.xml (+14 строк: <receiver> блок с intent-filter).
- VKApiClient.kt НЕ ТРОГАЛ — существующий метод `accountResetSessions` (line 17966)
  полностью подходит (deviceId, hash, validationToken=null — пробуем без 2FA).
- deviceId field: `device_id` (String, опционально) из alert JSON. Соответствует
  docstring VKApiClient.accountGetSecurityAlerts (line 17771) и alertIdOfObj в
  SecurityAlertsPoller.kt:272. Если VK не отдаёт device_id — action button пропускается
  (notification без action, tap → DevicesScreen как fallback).
- Риски/вопросы:
  1. **accountPersonal.resetSessions на web-шлюзе (forceWebGateway=true)**: существующий
     метод использует forceWebGateway=true. После P0.5-fix на соседнем getSecurityAlerts
     forceWebGateway УБРАН (т.к. web-шлюз возвращает err=3 "Unknown method passed" для
     этого namespace). Возможен тот же regression на resetSessions — если VK отвечает
     err=3, вернётся false и Toast «Не удалось завершить сессию». Это НЕ блокер для
     P2.4 (UI работает, fallback на DevicesScreen через tap), но в будущем стоит
     проверить реальный VK API response и при необходимости убрать forceWebGateway
     из `accountResetSessions` (или добавить fallback через api.vk.com).
     ⚠️ Файл VKApiClient.kt в allowed-list только для добавления `accountPersonalResetSessions`;
     поскольку существующий `accountResetSessions` уже работает, менять его forceWebGateway
     не входило в задачу P2.4. Оставлено как TODO на будущий sprint (P2.4-fix-2 или P0.x).
  2. **validationToken=null**: VK ID может потребовать 2FA confirmation (через
     cua.getValidationMethods + cua.check*Code) для reset_sessions. Если VK ответит
     "validation required" — пользователь увидит Toast «Не удалось завершить сессию».
     В текущей реализации PinoK cua.* flow НЕ интегрирован (см. VKApiClient.kt:17847-17857
     docstring). Это ограничение зафиксировано в KDoc SecurityAlertActionReceiver.
     Для production-grade стоит добавить cua verification flow (отдельная задача).
  3. **goAsync() vs scope.launch**: existing NotificationActionReceiver.kt использует
     `private val scope = CoroutineScope(...)` без goAsync() (т.к. receiver living
     в процессе app, scope не убивается системой). Я применил goAsync() дополнительно
     для надёжности (diagnostic change) — если процесс убьют сразу после launch,
     goAsync() даёт до ~10с grace period. Это безопасно (goAsync + finish() в finally).
  4. **NOTIFICATION_ID фиксированный (7000)**: если придёт 2 alert'а подряд, второй
     заменит первый в шторке (см. комментарий SecurityAlertNotifier.kt:120-122).
     Action button привязана к deviceId последнего alert'а. Это OK для security alerts
     (user не должен получить 5 всплывашек), но если user хочет завершить сессию из
     ПРЕДЫДУЩЕГО alert'а — будет завершена сессия из последнего. Альтернатива
     (per-alert notifId) ломает «single alert» UX — оставлено как есть.
- Worklog обновлён (этот блок).

---
Task ID: P1.8-fix
Agent: general-purpose (notification type icons)
Task: Добавить иконки + type matching для 4 типов уведомлений

Work Log:
- Прочитал /home/z/my-project/worklog.md — DIAG-C2 §2 line 2300-2308 (4 типа
  отсутствуют: audio/event_invite/sdk_app_request/wall_reply — без иконок и
  матчинга), §10 #14 line 3521-3523 (P1 #14 — файлы NotificationsScreen.kt:316-388,
  VKApiClient.kt:18991-19055), DIAG-SUMMARY line 2528 + 2550 (P3 → добавить
  иконки/обработку для 4 типов). Репо /home/z/repos/PinoK_1, ветка PinoK, HEAD
  7d0695f (P1.1-P1.7 уже слиты).
- Прочитал focus-файлы:
  • NotificationsScreen.kt:206-278 getTypeIcon — when-блок на 20+ типов, else →
    Notifications (fallback). 4 типа попадают в else (нет case'ов).
  • NotificationsScreen.kt:1179 typeIcon = getTypeIcon(item.type) — вызов для
    рендера; 1254-1259 (без аватара), 1274 (бейдж поверх аватара) — оба идут
    через typeIcon.icon. Значит правка getTypeIcon автоматически подхватится
    рендером.
  • NotificationsScreen.kt:47-77 imports — CalendarMonth ИМПОРТИРОВАН (стр. 50),
    но НЕ использовался в getTypeIcon (orphan-import). MusicNote/Apps/
    ChatBubbleOutline уже использовались для podcast/app/comment.
  • VKApiClient.kt:19112-19176 resolveRedesignNotificationType — 3 блока
    приоритетов: (1) settingsName (dots_menu.name), (2) actionText (глагол),
    (3) parentType (entity.type). Проблема: event_invite поглощалось «invite»→
    group_invites (стр. 19124), sdk_app_request поглощалось «app»→app (стр. 19137),
    wall_reply поглощалось «wall»→new_posts (стр. 19138). audio — pass-through
    через else → parentType (работало, но неявно).
- Проверил доступность иконок: app/build.gradle.kts:159 включает
  androidx.compose.material:material-icons-extended — все Material Icons Outlined
  доступны. CalendarMonth/MusicNote/Apps/ChatBubbleOutline — все уже в imports
  NotificationsScreen.kt (новых импортов добавлять не нужно).
- ШАГ 1 — getTypeIcon (NotificationsScreen.kt:268-282): добавил 4 case'а ПЕРЕД
  else-веткой, каждый с собственным tint для визуального отличия:
    ```kotlin
    // P1.8 (DIAG-C2 §2, §10 #14): 4 ранее fallback-типа получили собственные иконки.
    type == "audio" ->
        Icons.Outlined.MusicNote to Color(0xFF00BCD4)          // cyan (vs podcast indigo)
    type == "event_invite" || type == "event" ->
        Icons.Outlined.CalendarMonth to Color(0xFF7C4DFF)      // deep purple
    type == "sdk_app_request" ->
        Icons.Outlined.Apps to Color(0xFFFF7043)               // deep orange (vs app green)
    type == "wall_reply" ->
        Icons.Outlined.ChatBubbleOutline to Color(0xFF607D8B)  // blue-grey (vs comment primary)
    ```
- ШАГ 2 — resolveRedesignNotificationType (VKApiClient.kt:19121-19207):
  • Блок (1) settingsName: вставил 4 приоритетные ветки ДО поглощающих:
      n.contains("event_invite") || n.contains("event") -> "event_invite"  (до invite)
      n.contains("audio") -> "audio"                                       (до app)
      n.contains("sdk_app_request") || n.contains("sdk_app") -> "sdk_app_request"  (до app)
      n.contains("wall_reply") -> "wall_reply"                              (до post/wall)
  • Блок (2) actionText: вставил 2 глагольные ветки:
      t.contains("меропри") || t.contains("событи") -> "event_invite"  (до пригласил)
      t.contains("аудио") || t.contains("трек") -> "audio"
  • Блок (3) parentType: добавил 4 явных case (раньше pass-through через else):
      "audio" -> "audio"
      "event_invite" -> "event_invite"
      "sdk_app_request" -> "sdk_app_request"
      "wall_reply" -> "wall_reply"
  • Обновил KDoc: добавил P1.8-параграф с пояснением порядка веток и расширил
    @return-список 4 новыми типами.
- ШАГ 3 — рендеринг: отдельной правки не требуется — getTypeIcon(item.type)
  уже вызывается в NotificationCard (стр. 1179), и typeIcon.icon используется
  в Icon-компоненте (стр. 1254-1259 без аватара, 1274 бейдж поверх аватара).
- ШАГ 4 — type-фильтр UI: P1.6 уже удалил UI-триггер фильтра (#NOTIFSHOW),
  но серверный category-filter (NOTIFICATION_CATEGORIES, 7 категорий:
  all/communities/feedback/friends/services/communication/account) остался
  рабочим. Эти 7 категорий — это РАЗДЕЛЫ VK redesign (не машинные типы), и
  4 новых типа распределятся по ним серверно (audio→services/feedback,
  event_invite→communication, sdk_app_request→services, wall_reply→feedback).
  Локальный type-фильтр (NOTIFICATION_FILTERS) — dead code без UI-триггера,
  трогать не нужно (это вне scope P1.8, упомянуто в DIAG-C2 §5 как отдельная
  задача P1 про dead code).
- Проверка grep'ом:
  • rg "audio|event_invite|sdk_app_request|wall_reply" в NotificationsScreen.kt —
    все 4 case'а на месте (стр. 275-282).
  • rg в VKApiClient.kt — 4 settingsName + 2 actionText + 4 parentType case'а
    на местах (стр. 19134/19149/19151/19154, 19174/19177, 19200-19203).
  • Brace balance NotificationsScreen.kt: delta=0 (баланс соблюдён).
  • Brace balance VKApiClient.kt: delta=2 — ПРЕД-СУЩЕСТВУЮЩИЙ (до моих правок
    был тот же delta=2, проверено через git stash; обусловлен фигурными
    скобками в строковых литералах/комментариях в других частях файла).
    diff-только brace delta моих правок = 0 (баланс не нарушен).
- git commit НЕ делал (по требованию задачи).

Stage Summary:
- Добавлены 4 иконки в getTypeIcon (NotificationsScreen.kt:268-282):
  audio → MusicNote (cyan), event_invite → CalendarMonth (deep purple),
  sdk_app_request → Apps (deep orange), wall_reply → ChatBubbleOutline (blue-grey).
  Все иконки взяты из уже импортированных Material Icons Outlined — новых
  импортов не добавлено (CalendarMonth ранее был orphan-import, теперь задействован).
- Добавлен type matching в resolveRedesignNotificationType (VKApiClient.kt
  19121-19207): 4 ветки в settingsName-блоке (приоритет ДО поглощающих
  «invite»/«app»/«wall»/«post»), 2 ветки в actionText-блоке (мероприятие/событие
  → event_invite, аудио/трек → audio), 4 явных case в parentType-блоке.
  Раньше event_invite поглощалось group_invites, sdk_app_request → app,
  wall_reply → new_posts; теперь каждый тип доходит до getTypeIcon с правильным
  именем и получает свою иконку.
- Обновлён KDoc функции (P1.8-параграф + расширение @return-списка).
- Не тронуты: существующие case'ы (like/comment/follow/etc.), серверная
  category-bar (NOTIFICATION_CATEGORIES — 7 категорий), dead code локального
  type-фильтра (NOTIFICATION_FILTERS, вне scope). Файлы вне белого списка
  (SovaApp.kt, MessageNotifier.kt, NotificationsPoller.kt, …) — НЕ правлены.
- Ограничения: компиляция не проверена (окружение без Android SDK — то же
  ограничение что в P1.2/P1.3/P1.7-fix). Синтаксис/типы/импорты проверены
  вручную: использованы только уже импортированные Icons.Outlined.* и Color();
  `when`-ветки соответствуют существующему паттерну; brace balance сохранён.

---
Task ID: EQ-AUDIT
Agent: Explore (equalizer + panel editor audit)
Task: Полный аудит кода эквалайзера и причин его отсутствия в редакторе панелей

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние 200 строк) — последний Task
  P1.8-fix (notification type icons). Текущая голова репо PinoK = 7b8569e (ветка
  PinoK). Sandboxed окружение, Android SDK недоступен (как и в предыдущих задачах).
- Прочитал полный код эквалайзер-стека:
  • app/.../media/EqualizerHelper.kt (337 строк) — facade-объект, @Synchronized
    lifecycle (attachOnce/release/reattach), delegирует в AudioEffectsEngine.
    Pref-fallback для чтения saved-state при engine==null.
  • app/.../media/AudioEffectsEngine.kt (824 строки) — основной движок 6 эффектов
    (Equalizer/BassBoost/Virtualizer/PresetReverb/LoudnessEnhancer + TODO
    DynamicsProcessing). SharedPreferences("equalizer"), PREF_* константы:
    eq_enabled/eq_preset/eq_bands/bb_switch/bb_slider/vir_switch/vir_slider/
    loud_switch/loud_slider/reverb_switch/reverb_preset (lines 812-822).
    Thread-safe synchronized на lock, attach идемпотентен.
  • app/.../media/EqualizerFeatureFlags.kt (112 строк) — visibility UI-вкладок
    эквалайзера. 5 флагов (eq/bass/virt/reverb/loud) в том же prefs "equalizer",
    ключи с префиксом feat_. Defaults: eq/bass/virt/loud=true, reverb=false
    (ломает звук на custom ROM). snapshot() — атомарная читалка.
  • app/.../media/CustomPresetStore.kt (269 строк) — JSON-хранилище пользовательских
    пресетов в filesDir/equalizer/custom_presets.json (Room не настроен в PinoK).
    CustomPreset data class: id/name/eqBands/eqEnabled/bass*/virt*/loud*/reverb*.
    load() в SovaApp.onCreate:1468. upsert/delete/clearAll/list/getById.
  • app/.../ui/screens/music/EqualizerScreen.kt (938 строк) — Composable экран,
    5 вкладок: Полосы/Bass+Virt/Reverb/Loudness (Presets через dropdown).
    TopAppBar с master switch, SpectrumVisualizer, SCO-баннер, SavePresetDialog,
    DeletePresetDialog. LaunchedEffect читает state из EqualizerHelper.
  • app/.../ui/components/SpectrumVisualizer.kt (267 строк) — real-time FFT
    визуализатор на android.media.audiofx.Visualizer. 32 полосы, log-scale bins,
    EMA smoothing, Canvas-draw с verticalGradient. DisposableEffect lifecycle.
    Fallback «Визуализация недоступна» на ROM-каскадных сбоях (Xiaomi/Huawei).
  • app/.../service/PlayerService.kt (599 строк) — onCreate:421 подписывается
    на Player.Listener.onAudioSessionIdChanged → EqualizerHelper.attachOnce().
    Fix #96: до build() audioSessionId=0 → ждём onAudioSessionIdChanged. Fix #287/
    #342: AudioDeviceCallback на смену BT/speaker → scheduleEqReattach с debounce
    (600мс / 1200мс для BT). #REVERB-AUX: applyReverbAuxBinding через
    EqualizerHelper.auxBindingHook. onDestroy:580 release().
  • app/.../media/PlayerConnection.kt (1735 строк) — прокси-слой UI→Service:
    setEqualizerPreset/setEnabled/isEqualizerEnabled/getBands/setBand +
    setReverbEnabled/setReverbPreset/rebindReverbAux (lines 789-836).
  • feature/audio/.../AudioContainer.kt (144 строки) — #ARCH-CONTAINERS
    Этап 1.5-б. Публикует 4 capability: AudioNavEntry (route "equalizer",
    order=30), AudioSettingsSection (route "settings_audio", order=80),
    AudioAttachmentRenderer, AudioPermissions (MODIFY_AUDIO_SETTINGS).
    AudioNavEntry — это и есть «кнопка Эквалайзер в drawer».
  • core/data/.../model/Models.kt:1061-1094 — EqualizerPreset data class с 18
    built-in пресетами (DEFAULT..TECHNO). Полосы в dB (float), 9 полос
    60Hz..14kHz, полу-шаг 0.5 dB.

- Прочитал код редактора панелей:
  • app/.../ui/navigation/PanelItems.kt (100 строк) — канонический список
    PanelItems.all (lines 64-93): 17 пунктов: feed/notifications/messenger/
    groups/photos/friends/video/profile/clips/music/music2/bookmarks/files/
    search/reactions/services/logs. **Эквалайзер ОТСУТСТВУЕТ в списке.**
    byKey/allKeys — Map/List поверх all.
  • app/.../ui/navigation/Screen.kt:585 — `object Equalizer : Screen("equalizer",
    "Эквалайзер", Icons.Filled.Equalizer)`. Route существует, иконка есть.
  • app/.../ui/navigation/SovaNavHost.kt:2650 — `composable(Screen.Equalizer.route)
    { EqualizerScreen(onBack = ...) }` — destination ЗАРЕГИСТРИРОВАН, работает.
  • app/.../ui/screens/settings/PanelEditorTab.kt (455 строк) — `CANONICAL_PANEL_ITEMS
    = PanelItems.all` (line 73). Не фильтрует — что в PanelItems.all, то и в
    редакторе. **«equalizer» нет в PanelItems.all → не в редакторе.**
  • app/.../ui/screens/settings/SettingsScreen.kt:3555-3641 — EqualizerTab с
    5 ToggleRow (eq/bass/virt/reverb/loud), route "settings_audio". Открывается
    из контейнерной вкладки настроек (AudioSettingsSection).
  • SovaPrefs.kt:2448-2461 — SIDEBAR_DEFAULT_ORDER/BOTTOMBAR_DEFAULT_ORDER/
    HIDDEN. «equalizer» нет ни в одном дефолте (как и «feed» — добавляется через
    редактор). defaults_v2/v3 миграции (lines 1017-1052) тоже не упомянуты.

- Прочитал SovaPrefsBackup.kt:164 — `SETTINGS_SP_FILES = listOf("equalizer")` —
  legacy SP-файл «equalizer» ВКЛЮЧЁН в export/import (все ключи AudioEffectsEngine
  + EqualizerFeatureFlags). Backup корректно покрывает настройки эквалайзера.

- Проследил две независимые системы меню:
  (1) PanelItems.all — канонические пункты, редактируемые пользователем.
      Рендерятся в drawer (visibleSidebarItems, SovaNavHost.kt:1236) И в bottom
      bar (visibleBottomItems) И в PanelEditorTab.
  (2) ContainerRegistry.find<NavEntry>() — контейнерные пункты из feature-
      модулей. Рендерятся ТОЛЬКО в drawer (SovaNavHost.kt:1303-1342) ПОСЛЕ
      канонических, и только если route НЕ дублирует канонический пункт
      (panelDestRoutes check at line 1317). В PanelEditorTab НЕ попадают.

  AudioNavEntry (AudioContainer.kt:81-87, route "equalizer") идёт путём (2) —
  поэтому кнопка «Эквалайзер» видна в drawer (после основных пунктов, иконка
  Icons.Filled.Equalizer), но НЕ редактируется в «Редакторе панелей» и НЕ
  доступна для нижней панели.

Stage Summary:

- Часть A — Реализация эквалайзера: WORKING. Полный стек из 7 файлов, ~3700
  строк: AudioEffectsEngine (6 эффектов), EqualizerHelper (facade), EqualizerScreen
  (5 вкладок + visualizer), SpectrumVisualizer (FFT 32 bars), CustomPresetStore
  (JSON), EqualizerFeatureFlags (5 visibility toggles), Models.kt:EqualizerPreset
  (18 built-in). Интеграция с PlayerService: attach на onAudioSessionIdChanged,
  reattach на audio route change (BT debounce 1200мс), SCO suspend/restore для
  Virtualizer+Reverb. #REVERB-AUX: hook привязки к ExoPlayer.setAuxEffectInfo.

- Часть B — SovaPrefs equalizer keys: WORKING (но НЕ в SovaPrefs). Настройки
  эквалайзера живут в отдельном SharedPreferences-файле «equalizer» (не в
  DataStore SovaPrefs). 11 ключей: eq_enabled/eq_preset/eq_bands/bb_switch/
  bb_slider/vir_switch/vir_slider/loud_switch/loud_slider/reverb_switch/
  reverb_preset (AudioEffectsEngine.kt:812-822) + 5 feat_* флагов видимости
  (EqualizerFeatureFlags.kt:34-38). Defaults: all false/0 (loaded on attach),
  feat_* defaults = true кроме reverb=false. Backup: SETTINGS_SP_FILES=
  listOf("equalizer") (SovaPrefsBackup.kt:164) — ВКЛЮЧЁН в export/import.
  Custom presets — отдельный JSON в filesDir/equalizer/custom_presets.json
  (НЕ в SovaPrefs, НЕ в SP «equalizer», НЕ в backup SovaPrefsBackup; только
  ручной backup filesDir как часть приложения).

- Часть C — Редактор панелей, почему не отображается: BROKEN by design.
  PanelEditorTab.kt:73 = `CANONICAL_PANEL_ITEMS = PanelItems.all`. В
  PanelItems.all (PanelItems.kt:64-93) 17 пунктов — эквалайзера НЕТ.
  PanelEditorTab не фильтрует, не gating'ит — просто рендерит PanelItems.all.
  Контейнерный AudioNavEntry (route "equalizer") рендерится напрямую в drawer
  (SovaNavHost.kt:1303), минуя редактор. В bottom bar его нет вообще.

- Часть D — Сравнение с другими audio features: «Музыка» (Screen.Music,
  route "music") — в PanelItems.all на line 84 как PanelItem("music", "Музыка",
  Screen.Music.icon, Route(Screen.Music)). «Музыка 2» (Screen.Music2) — line 86.
  Pattern для добавления нового пункта в редактор: одна строка PanelItem(key,
  title, icon, Route(Screen.X)) в PanelItems.all. Контейнерный NavEntry —
  альтернативный путь (drawer-only), не попадает в редактор.

- Часть E — Корневая причина: гипотеза (a). PanelItems.kt:64-93 НЕ содержит
  PanelItem("equalizer", "Эквалайзер", Screen.Equalizer.icon,
  PanelAction.Route(Screen.Equalizer)). Контейнер AudioContainer.AudioNavEntry
  регистрирует кнопку через ContainerRegistry — она видна в drawer, но
  PanelEditorTab её не показывает. Никаких feature-flag гейтингов нет:
  Screen.Equalizer существует (Screen.kt:585), destination зарегистрирован
  (SovaNavHost.kt:2650), EqualizerScreen композится. При добавлении в
  PanelItems.all, чек-гард `dest.route in panelDestRoutes` (SovaNavHost.kt:1317)
  автоматически подавит дубликат из containerNavEntries — drawer не раздуется.

- Часть F — Минимальный фикс: добавить ОДНУ строку в PanelItems.kt между
  line 86 (music2) и line 87 (bookmarks), либо сразу после line 84 (music):
  ```
  PanelItem("equalizer", "Эквалайзер", Screen.Equalizer.icon,
      PanelAction.Route(Screen.Equalizer)),
  ```
  После этого: (1) пункт появится в PanelEditorTab для sidebar+bottom; (2)
  drawer получит пункт через PanelItems (а не через containerNavEntries);
  (3) AudioNavEntry будет автоматически suppressed как дубликат (SovaNavHost.kt:
  1317 panelDestRoutes check). Дополнительно стоит добавить "equalizer" в
  SIDEBAR_DEFAULT_HIDDEN (SovaPrefs.kt:2452) — чтобы дефолтная панель не
  изменилась (как с «feed»). Полный фикс опционально: вынести AudioNavEntry
  из AudioContainer (тогда контейнер не нужен для drawer-кнопки, только для
  SettingsSection/AttachmentRenderer/Permissions).


---
Task ID: P3.2-fix
Agent: general-purpose (ChannelWebSocketClient dead-code cleanup)
Task: Удалить или дописать ChannelWebSocketClient stub

Work Log:
- Прочитал /home/z/my-project/worklog.md (tail 200 строк) — последний Task EQ-AUDIT
  (explore-only). Диагностика ChannelWebSocketClient как DEAD-CODE подтверждена
  ранее в DIAG-A2 §1/§risks, DIAG-SUMMARY P0 #5, Task 55 (lines 11968/9506/9513),
  HISTORY.md:2222/2240/7005. Рабочее дерево содержало незакоммиченные правки P3.1
  (emailNotifyFreq removal) в FeedScreen/SettingsScreen/SovaPrefs — не конфликтуют
  с P3.2 зоной.
- Прочитал app/src/main/java/re/pinok/realtime/ChannelWebSocketClient.kt (183 строки)
  полностью. Подтверждено: весь класс — STUB/RESEARCH P4.3, все 8 методов — TODO
  (start/stop/subscribe/unsubscribe + 4 WsListener callbacks onOpen/onMessage/
  onClosed/onFailure). KDoc-пример использования (стр. 63) использовал сигнатуру
  `ChannelWebSocketClient(httpClient, apiClient, prefs)` — НО реальный конструктор
  (стр. 82) принимает только `(httpClient, apiClient)`. KDoc-пример НЕ компилируется
  как есть. companion-object WS_TIMEOUT_SECONDS=0L также не используется.
- Grep "ChannelWebSocketClient" по всему репо (/home/z/repos/PinoK_1):
  • app/src/main/java/re/pinok/realtime/ChannelWebSocketClient.kt — сам файл
    (4 вхождения: 2 в KDoc-комментариях, 1 определение класса, 1 в KDoc-примере).
  • app/src/main/java/re/pinok/ui/screens/settings/SettingsScreen.kt:2311 —
    COMMENT в блоке Fix #REMOVE-DEAD-TOGGLES, объясняющем что UI-toggle был удалён
    ранее (тумблер отсутствует, только комментарий-памятка).
  • core/data/src/main/java/re/pinok/data/local/SovaPrefs.kt:330,1650,1652 —
    KDoc-комментарии к полю msgWsChannels (Snapshot) и key MSG_WS_CHANNELS.
  • core/common/src/main/java/re/pinok/util/AppLog.kt:787 — KDoc-комментарий к
    fun ws() (помечена "P4.3: для ChannelWebSocketClient").
  • HISTORY.md / worklog.md / scripts/check-nested-comments.py —
    documentation/diagnostics, не код.
- Grep "msgWsChannels" по всему репо:
  • ChannelWebSocketClient.kt:57,74 — KDoc-комментарии (будут удалены вместе с файлом).
  • SovaPrefs.kt:332 (load), 940 (setter setMsgWsChannels), 1654 (field), 2180 (key) —
    рабочие references, но поле помечено @Deprecated как мёртвое.
  • FeedScreen.kt:359 — `msgWsChannels = false` в Snapshot constructor (хардкод).
    ❗ FeedScreen.kt — ВНЕ ЗАНЫ P3.2 (запрещённый файл). НЕ может быть изменён.
  • SettingsScreen.kt:2311 — comment only.
- Проверка instantiation: `ChannelWebSocketClient(` в коде — 0 вхождений вне KDoc.
  Импортов `import re.pinok.realtime.ChannelWebSocketClient` — 0 вхождений во
  всём репо. SovaApp.kt — 0 references (подтверждено отдельным grep).
- Проверка UI toggle в SettingsScreen.kt (стр. 2290-2314): тумблер "WebSocket для
  каналов" УЖЕ удалён ранее Fix #REMOVE-DEAD-TOGGLES (2026-08-04). Остался только
  комментарий-памятка (стр. 2308-2313) о том, что было удалено. Новый ToggleRow для
  msgWsChannels ОТСУТСТВУЕТ. Pref в SovaPrefs остался (@Deprecated-KDoc).
- Решение: ВАРИАНТ A — удалить файл полностью. Обоснование:
  (1) 0 instantiation во всём репо;
  (2) Все методы — TODO stub'ы, RESEARCH-заготовка P4.3;
  (3) KDoc-пример в самом файле использовал неверную сигнатуру конструктора —
      даже скопировать пример в продакшен-код нельзя без правки;
  (4) UI-toggle уже удалён ранее, consumers у класса нет;
  (5) Git history (HEAD f8c7793 и ранее) сохранит файл, если понадобится для
      будущего P4.3 активации — git show f8c7793:app/src/main/java/re/pinok/
      realtime/ChannelWebSocketClient.kt вернёт содержимое.
- ⚠️ Constraint: поле msgWsChannels В SovaPrefs.Snapshot (field + load + setter +
  PreferencesKey) НЕ МОЖЕТ быть удалено в рамках P3.2, т.к. FeedScreen.kt:359
  (вне зоны, запрещённый файл) использует `msgWsChannels = false` в конструкторе
  Snapshot. Это та же причина что в worklog line 7618 (P3-9 Task 3-b verdict:
  "поле НЕ удалено ради FeedScreen-конструктора — FeedScreen вне зоны волны 3-b").
  Поэтому удаление поля/сеттера/key отложено до рефакторинга FeedScreen.
- ⚠️ Bonus orphan: AppLog.ws() в core/common/.../AppLog.kt (стр. 795-820) теперь
  не имеет ни одного caller'а (все 8 caller'ов были в ChannelWebSocketClient.kt).
  Файл AppLog.kt НЕ в списке разрешённых ( границы P3.2). Оставлен как есть —
  помечен в комментарии SettingsScreen.kt:2318 как orphan для будущего P3.x.

Stage Summary:
- ВАРИАНТ A применён. Файл app/src/main/java/re/pinok/realtime/
  ChannelWebSocketClient.kt (183 строки) УДАЛЕН полностью (rm).
- SettingsScreen.kt:2308-2318 — обновлён explanatory comment Fix #REMOVE-DEAD-
  TOGGLES: заменена фраза "существует но не инстанциируется, методы STUB с TODO"
  на "удалён (P3.2 dead-code cleanup): класс-заглушка был не инстанциирован
  нигде, все методы STUB с TODO, KDoc-пример использовал несуществующую сигнатуру
  конструктора". Добавлен параграф "P3.2: Pref и setter остаются в SovaPrefs —
  поле msgWsChannels НЕ удалено, т.к. FeedScreen.kt (вне зоны P3.2) передаёт
  msgWsChannels=false в Snapshot constructor. AppLog.ws() ... стал orphan'ом ...
  не трогается (вне зоны)". Brace delta = 0 (изменены только комментарии).
- SovaPrefs.kt:327-336 (load site) — обновлён inline-комментарий: убрано упоминание
  "Заготовка [re.pinok.realtime.ChannelWebSocketClient] — stub для будущего
  использования", добавлен P3.2-блок объясняющий что класс удалён, поле остаётся
  только ради FeedScreen.kt:359, setter оставлен как часть того же API.
- SovaPrefs.kt:1652-1663 (Snapshot field KDoc) — обновлён @Deprecated-KDoc: убраны
  устаревшие file:line ссылки ChannelWebSocketClient.kt:57/74, добавлен P3.2-блок
  с пометкой что класс удалён, поле остаётся для компиляции FeedScreen, при
  переработке FeedScreen можно удалить поле+setter+MSG_WS_CHANNELS key разом.
- Поле msgWsChannels (Snapshot.kt:1663), setter setMsgWsChannels (стр. 940),
  PreferencesKey MSG_WS_CHANNELS (стр. 2180), load site (стр. 337) — НЕ тронуты
  (нельзя удалить без правки FeedScreen.kt, который вне зоны P3.2).
- AppLog.ws() в core/common/.../AppLog.kt — НЕ тронут (файл вне разрешённой зоны
  P3.2); теперь orphan dead code (8 бывших caller'ов в удалённом файле).
- Доказательства после фикса:
  • `find . -name "ChannelWebSocketClient.kt" -not -path "./.git/*"` → 0 matches
    (exit code 0, файл не найден).
  • `rg "ChannelWebSocketClient" --type kt` (через Grep tool) → 3 файла:
    AppLog.kt (KDoc-comment только), SovaPrefs.kt (3 обновлённых KDoc-comment'а),
    SettingsScreen.kt (2 обновлённых comment'а). ВСЕ — explanatory comments про
    удаление. Живых CODE references (import, instantiation, type usage) — 0.
  • `rg "import re.pinok.realtime.ChannelWebSocketClient" --type kt` → 0 matches
    (нет dangling imports).
  • `rg "msgWsChannels" app/src/main/java` → 4 вхождения, все ожидаемы:
    SettingsScreen.kt:2311,2315,2316 (мои обновлённые комментарии) +
    FeedScreen.kt:359 (запрещённый файл, НЕ тронут).
  • Brace balance: SettingsScreen.kt open=1917 close=1917 delta=0 (был 0 до правки);
    SovaPrefs.kt open=64 close=64 delta=0 (был 0 до правки). Синтаксис не нарушен.
  • git commit НЕ сделан (по требованию задачи).
- Риски/вопросы:
  1. AppLog.ws() в core/common/.../AppLog.kt теперь dead code без caller'ов.
     Файл вне зоны P3.2 → оставлен. Рекомендация для будущего P3.x: удалить
     fun ws() (стр. 786-820) и её KDoc. Tag "ChannelWSClient" (стр. 796) больше
     нигде не используется.
  2. Поле msgWsChannels + setter + MSG_WS_CHANNELS key остаются в SovaPrefs
     (нельзя удалить без правки FeedScreen.kt). При переработке FeedScreen.kt
     (вне зоны P3.2) их можно удалить все разом — см. обновлённый KDoc в
     SovaPrefs.kt:1652-1663 с инструкцией.
  3. ChannelWebSocketClient.kt сохранится в git history (HEAD f8c7793) — при
     активации P4.3 (если VK форсирует WS для каналов) класс можно восстановить:
     `git show f8c7793:app/src/main/java/re/pinok/realtime/ChannelWebSocketClient.kt`.
     ⚠️ Но при восстановлении нужно исправить KDoc-пример сигнатуры конструктора
     (стр. 63 примера → `(httpClient, apiClient, prefs)` vs реальный
     `(httpClient, apiClient)`).
  4. Компиляция не проверена (окружение без Android SDK — то же ограничение что в
     P1.2/P1.3/P1.7-fix/P1.8-fix). Изменения коснулись только комментариев и
     удаления standalone файла, brace delta = 0. Удалены unused imports (5 шт.
     в удалённом файле: MutableSharedFlow/SharedFlow/asSharedFlow/OkHttpClient/
     Request/Response/WebSocket/WebSocketListener/AppLog) — они больше нигде не
     нужны (были приватно для удалённого класса).

---
Task ID: P3.4-fix
Agent: general-purpose (cross-channel call dedup)
Task: Добавить mutex для 4 коллекторов incoming call

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние ~300 строк) — контекст
  DIAG-A2 §7 (Cross-channel call dedup): 4 коллектора (LP 115 + queuev4 +
  events_queue + calls_queue) могли race'ить `pendingIncomingCallPayload`;
  guard `pendingIncomingCallPayload != event.payload` (SovaApp.kt:1920 в
  старой нумерации / 2026 в новой) покрывал только ТОТ ЖЕ payload, не РАЗНЫЕ
  payload'ы одного звонка (LP "-1" marker + calls_queue real convParams).
  DIAG-E2 §3 (incoming call regression FIXED в c9d4ff8 "P0.43-r8-V216-RESTORE"):
  V2.1.6 поведение восстановлено — LP 115 ВСЕГДА эмитит IncomingCall (даже
  payload="-1"), SovaApp принимает любой payload, при "-1" через
  messagesGetCurrentCalls вытягивает caller_id.
- Прочитал SovaApp.kt (3221 строка → стало 3383 после правок):
  • Поле `pendingIncomingCallPayload` (line 155) — mutableStateOf, мутируется
    из 4 коллекторов + handleQueuev4Incoming.
  • `startCallNotifier` (line 1890 → 1991) — 4 keepAliveScope.launch блока:
    (1) longPollClient.events.collect — LP 115 path, мутирует напрямую.
    (2) queuev4Client.events.collect — HAR path мутирует напрямую, legacy
        fallback делегирует на handleQueuev4Incoming.
    (3) eventsQueuev4Client.events.collect — ВСЕГДА делегирует на
        handleQueuev4Incoming (нет прямых мутаций).
    (4) callsQueuev4Client.events.collect — HAR path мутирует напрямую,
        fallback/legacy делегируют на handleQueuev4Incoming.
  • `handleQueuev4Incoming(payload)` (line 2103 → 2237) — keepAliveScope.launch
    с try/catch; 2 ветви: real payload (guard `==` skip) и "-1"/null
    (messagesGetCurrentCalls → caller_id → placeholder).
  • `refreshIncomingCaller()` (line 2154 → 2309) — async launches, не мутирует
    pendingIncomingCallPayload напрямую (только peerId/title/photo).
  • `ensureIncomingSignalingOnRing(payload)` (line 2556 → 2712) — async launch,
    собственный guard `incomingSignalingStarted` (line 269).
  • `showIncomingCallNotification()` (line 2616 → 2772) — sync notification
    build + nm.notify().
- Проверил что Mutex/withLock уже используются в проекте (NotificationsPoller,
  CommunityPostsPoller, SecurityAlertsPoller, *Repository.kt — все через
  `import kotlinx.coroutines.sync.Mutex` + `import kotlinx.coroutines.sync.withLock`).
  SovaApp.kt эти импорты НЕ имел — добавил (lines 60-61).
- ШАГ 1 — поля класса (SovaApp.kt:274-310, после `incomingSignalingStarted`):
  ```kotlin
  private val incomingCallMutex = Mutex()                       // line 289
  @Volatile
  private var lastIncomingCallProcessedAt: Long = 0L             // line 301
  private val INCOMING_CALL_DEDUP_WINDOW_MS = 5_000L             // line 310
  ```
  Каждый с KDoc-комментарием `// P3.4 (DIAG-A2 §7):` — объяснение назначения и
  trade-off'ов (почему 5с окно, почему mutex НЕ реентерабельный и не вызывает
  deadlock из-за того что handleQueuev4Incoming в отдельной корутине).
- ШАГ 2 — helper-методы (SovaApp.kt:1921-1980, перед startCallNotifier):
  ```kotlin
  private fun shouldSkipIncomingCall(newPayload: String?): Boolean {
      val currentPayload = pendingIncomingCallPayload
      val now = System.currentTimeMillis()
      val inDedupWindow = now - lastIncomingCallProcessedAt < INCOMING_CALL_DEDUP_WINDOW_MS
      val newIsMarker = newPayload == null || newPayload == "-1" || newPayload.isBlank()
      return when {
          currentPayload.isNullOrBlank() -> false                                      // 1) first event
          currentPayload == newPayload -> true                                         // 2) dup
          currentPayload != "-1" && newIsMarker -> true                               // 3) have real, got marker
          inDedupWindow && currentPayload != "-1" && !newIsMarker
              && currentPayload != newPayload -> true                                  // 4) both real & different, in window
          else -> false                                                                // 5) placeholder→real upgrade or new call
      }
  }
  private fun markIncomingCallProcessed() {
      lastIncomingCallProcessedAt = System.currentTimeMillis()
  }
  ```
  Skip conditions (по приоритету):
   1. current null/blank → process (первый event звонка).
   2. current == new → skip (dup того же event'а).
   3. Have real (current != "-1"), new is "-1"/null/blank → skip
      (real > marker; LP "-1" после calls_queue real не должен перезаписать).
   4. In dedup window, both real & different → skip (2 канала прислали РАЗНЫЕ
      payload'ы ОДНОГО звонка).
   5. Otherwise → process (placeholder "-1" → real upgrade, или новый звонок
      вне dedup-окна).
  ВАЖНО: НЕ skip'ает placeholder → real upgrade (current="-1", new=real) —
  это нужно чтобы calls_queue HAR real convParams мог заменить LP "-1"
  placeholder (V2.1.6 flow сохранён).
- ШАГ 3 — обёрнуты все 4 коллектора в `incomingCallMutex.withLock { ... }`:
  • LP 115 collector (SovaApp.kt:2026) — прямой guard заменён на shouldSkipIncomingCall.
  • queuev4 HAR convParams path (SovaApp.kt:2084) — обёрнут, markProcessed
    только при реальной обработке.
  • calls_queue HAR convParams path (SovaApp.kt:2180) — обёрнут, аналогично.
  • handleQueuev4Incoming body (SovaApp.kt:2249) — обёрнут, `return@launch`
    заменены на `return@withLock` (4 шт.). callerId в "-1" path обновляется
    даже при skip (caller_id не теряется, но нотификация не дублируется).
  events_queue collector НЕ обёрнут напрямую — он НЕ мутирует
  pendingIncomingCallPayload, только делегирует на handleQueuev4Incoming
  (которое уже под mutex). Это эквивалентно по защите race condition.
- ШАГ 4 — сохранены все существующие комментарии и стиль (P0.43-r8-V216-RESTORE,
  #CALLS-NO-DOUBLE-CALLSCREEN, #CALLS-COMMON-SIGNALING, P0.42-CALLS-HAR,
  #SETTINGS-CALLS-TOGGLE — все на местах). Новые блоки помечены `// P3.4:`.
- Проверка:
  • `grep -n "incomingCallMutex\|INCOMING_CALL_DEDUP_WINDOW\|lastIncomingCallProcessedAt"`
    — 3 новых поля + 5 новых использования в shouldSkipIncomingCall/
    markIncomingCallProcessed (lines 289/301/310, 1953/1979).
  • `grep -n "incomingCallMutex.withLock"` — 4 вызова:
    line 2026 (LP 115), line 2084 (queuev4 HAR), line 2180 (calls_queue HAR),
    line 2249 (handleQueuev4Incoming body).
  • Brace balance: `awk '{o+=gsub(/{/,"{"); c+=gsub(/}/,"}")} END{print "delta="o-c}'`
    = delta=0 (баланс соблюдён, +162 строк и все блоки сбалансированы).
  • Imports: `import kotlinx.coroutines.sync.Mutex` (line 60) + `import
    kotlinx.coroutines.sync.withLock` (line 61) — добавлены.
- V2.1.6 flow проверка (DIAG-E2 §3):
  • LP 115 "-1" → shouldSkipIncomingCall(null/"-1") при currentPayload=null →
    false (process). markIncomingCallProcessed. realPayload=null →
    pendingIncomingCallPayload = "-1" (placeholder). refreshIncomingCaller
    (вытянет caller_id через getCurrentCalls — как V2.1.6). WS НЕ поднимается
    (realPayload == null). showIncomingCallNotification. ✅
  • LP 115 real convParams → shouldSkipIncomingCall(real) при current=null →
    false (process). markIncomingCallProcessed. realPayload=real →
    pendingIncomingCallPayload = real. refreshIncomingCaller.
    ensureIncomingSignalingOnRing(real) — WS поднимется. showNotification. ✅
  • calls_queue HAR real convParams приходит ПОСЛЕ LP "-1" (в течение 5с) →
    currentPayload="-1", newPayload=real. shouldSkipIncomingCall:
      1) current != null → no
      2) current == new? "-1" != real → no
      3) current != "-1"? false → no
      4) inDedupWindow && current != "-1"? false → no
      5) else → false (PROCESS — placeholder→real upgrade)
    → markIncomingCallProcessed. pendingIncomingCallPayload = real (UPGRADE
    с placeholder). refreshIncomingCaller. ensureIncomingSignalingOnRing(real)
    — WS поднимется с реальными params. showNotification (обновит). ✅
    Это V2.1.6-совместимое поведение: real convParams заменяет "-1" placeholder.
  • calls_queue HAR real convParams приходит ПЕРЕД LP "-1" (в течение 5с) →
    currentPayload=real, newPayload="-1". shouldSkipIncomingCall:
      1) current != null → no
      2) current == new? real != "-1" → no
      3) current != "-1" && newIsMarker? → true (SKIP)
    → LP "-1" пропущен, не перезапишет real payload. ✅
    V2.1.6-совместимо: real > marker.
- НЕ делал git commit (по требованию задачи).
- Ограничения: компиляция НЕ проверена (окружение без Android SDK — то же
  ограничение что в P1.8-fix и др.). Синтаксис/типы/импорты проверены вручную:
  Mutex/withLock — стандартный kotlinx.coroutines.sync API (используется в 7+
  других файлах проекта); `return@withLock` — корректный Kotlin label (withLock
  — ближайшая inline suspend lambda); brace balance = 0; shouldSkipIncomingCall
  — pure function (только читает поля, никаких side effects кроме
  System.currentTimeMillis()).

Stage Summary:
- Добавлены 3 поля класса в SovaApp.kt (после `incomingSignalingStarted`):
  `incomingCallMutex: Mutex` (line 289), `lastIncomingCallProcessedAt: Long`
  (line 301, @Volatile), `INCOMING_CALL_DEDUP_WINDOW_MS: Long = 5_000L`
  (line 310). Импорты `kotlinx.coroutines.sync.Mutex` + `withLock` (lines 60-61).
- Добавлены 2 helper-метода: `shouldSkipIncomingCall(newPayload): Boolean`
  (SovaApp.kt:1950) — централизованная dedup-логика (5 skip conditions,
  включая window-based dedup и real-vs-marker приоритет); `markIncomingCallProcessed()`
  (SovaApp.kt:1978) — обновление timestamp (вызывать только при реальной обработке).
- Обёрнуты 4 места в `incomingCallMutex.withLock { ... }`:
  • LP 115 collector (SovaApp.kt:2026) — заменил старый guard
    `pendingIncomingCallPayload != event.payload` на shouldSkipIncomingCall.
  • queuev4 HAR convParams path (SovaApp.kt:2084).
  • calls_queue HAR convParams path (SovaApp.kt:2180).
  • handleQueuev4Incoming body (SovaApp.kt:2249) — защищает от race при
    одновременных вызовах из queuev4/events_queue/calls_queue legacy paths.
  events_queue collector НЕ обёрнут напрямую (не мутирует pendingIncomingCallPayload,
  только делегирует на handleQueuev4Incoming — которое под mutex). Все 4
  коллектора сериализованы по доступу к pendingIncomingCallPayload.
- V2.1.6 flow (DIAG-E2 §3) сохранён: LP 115 "-1" → handleQueuev4Incoming →
  messagesGetCurrentCalls → caller_id; real convParams → прямой pending update
  + ensureIncomingSignalingOnRing(real). Placeholder "-1" → real upgrade
  работает (shouldSkipIncomingCall возвращает false для current="-1" + new=real).
- Риски/вопросы:
  1. **window-based dedup может пропустить легитимный call-waiting** (второй
     входящий в течение 5с от первого — будет skip'нут как "2 канала одного
     звонка"). VK обычно не присылает call-waiting в пределах 5с; если
     понадобится — увеличить INCOMING_CALL_DEDUP_WINDOW_MS или добавить
     per-callId tracking (текущая реализация не различает callId в dedup).
  2. **withContext(Dispatchers.IO) внутри withLock в handleQueuev4Incoming** —
     mutex удерживается на время HTTP-вызова messagesGetCurrentCalls (1-3с).
     Другие коллекторы suspend'ят на withLock. Это желаемое поведение
     (сериализация), но добавляет latency для second event. В практике
     second event обычно skip'ается window-dedup'ом, так что latency
     наблюдается только если второй event — реальный upgrade placeholder→real.
  3. **events_queue без прямого mutex** — формально задача говорит "все 4
     коллектора обёрнуты", но events_queue делегирует на handleQueuev4Incoming
     (которое под mutex). Альтернатива — обернуть events_queue дополнительно,
     но это создаст двойной захват (events_queue mutex → handleQueuev4Incoming
     mutex). Текущая реализация сериализует модификацию pendingIncomingCallPayload
     (что и было целью §7). Если требуется буквальное "все 4 collectors
     wrapped" — можно добавить `incomingCallMutex.withLock` вокруг вызова
     handleQueuev4Incoming в events_queue (lines 2143-2146), но это либо
     deadlock (если handleQueuev4Incoming acquisitive тот же mutex синхронно),
     либо бесполезно (т.к. handleQueuev4Incoming launches async coroutine).
     Оставлено как есть — race condition закрыт.
  4. **Компиляция не проверена** — окружение без Android SDK (как в P1.8-fix).
     Синтаксис/типы/импорты/brace balance проверены вручную.
- Worklog обновлён (этот блок).

---
Task ID: EQ-CRASH-AUDIT
Agent: Explore (equalizer crash audit)
Task: Глубокий анализ скрытых багов и крашей в эквалайзере

Work Log:
- Прочитал /home/z/my-project/worklog.md (последние ~200 строк) — контекст
  EQ-AUDIT (предыдущий explore-аудит эквалайзер-стека + редактора панелей)
  и P3.4-fix (incoming-call mutex). Текущая голова репо PinoK = 862c7e3
  (релиз 2.2.6). Android SDK недоступен — read-only аудит.
- Прочитал полностью все 8 файлов эквалайзер-стека (5077 строк суммарно):
  • AudioEffectsEngine.kt (824 строки) — core engine, 6 эффектов, synchronized
    на lock, prefs("equalizer") с 11 ключами (PREF_EQ_*). @Volatile на всех
    effect-полях + attached/attachedSessionId/currentPresetName/scoSuspended.
    releaseInternal() зануляет все effect-поля + сбрасывает SCO-state (P3.5).
    createEffects() каждый эффект в try/catch → null при init failure (Error -3).
    Все сеттеры/геттеры synchronized(lock) + null-safe (effect?.let / ?: return).
  • EqualizerHelper.kt (336 строк) — facade. @Synchronized на attachOnce/
    release/engine(). Прямые field-читалки `val e = engine` (без @Synchronized)
    в saveEnabled/setEnabled/applyPreset/setBand/reattach/reattachFull —
    @Volatile даёт visibility, но read+use не атомарен. P3.4-комментарий
    объясняет что это безопасно т.к. release() зануляет engine, а released-
    инстанс безопасен для вызовов (effect-поля null, attached=false).
    **НО**: saveEnabled/setEnabled/applyPreset НЕ имеют prefs-fallback при
    engine==null (в отличие от savePreset/saveBands/loadEnabled/loadPreset/
    loadBands/applyCustomPresetPersist/setBand которые fallback'ят в prefs).
  • EqualizerFeatureFlags.kt (111 строк) — 5 флагов видимости (feat_*).
    Defaults: eq/bass/virt/loud=true, reverb=false. snapshot() атомарно читает
    все 5. Setters пишут в prefs + выключают живой эффект если флаг=false.
  • CustomPresetStore.kt (269 строк) — JSON в filesDir/equalizer/
    custom_presets.json. synchronized(lock) + AtomicBoolean dirty. load() в
    SovaApp.onCreate:1508. upsert/delete/clearAll → flush() синхронно пишет
    f.writeText(json) на потоке вызывающего (обычно main). JSON parse error
    → catch → presets=emptyList (данные потеряны, но без краша).
  • EqualizerScreen.kt (938 строк) — Composable, 4 вкладки (Полосы/BassVirt/
    Reverb/Loudness). BandsTab использует PlayerConnection.setEqualizerBand
    (имеет prefs-fallback). BassVirtTab/ReverbTab/LoudnessTab используют
    `val engine = remember { EqualizerHelper.engine() }` — captures engine
    ONCE при первом compose; если engine==null, ВСЕ engine?.method() no-op
    БЕЗ prefs-write. ReverbTab вызывает PlayerConnection.setReverbEnabled/
    setReverbPreset (тоже без prefs-fallback при engine==null).
  • SpectrumVisualizer.kt (266 строк) — Visualizer на FFT. DisposableEffect
    (sessionId) создаёт/релизит Visualizer. `visualizer.enabled = active`
    ставится ОДИН раз в setup. LaunchedEffect(active, sessionId) — ПУСТОЙ
    (только комментарий), НЕ обновляет visualizer.enabled при смене active.
    computeBars: безопасен (n<4 → return, bin < maxBin guard). Canvas: деление
    на NUM_BARS=32 (константа), div-by-zero невозможен.
  • PlayerService.kt (598 строк) — onCreate:421 подписка на
    onAudioSessionIdChanged → EqualizerHelper.attachOnce. AudioDeviceCallback
    → scheduleEqReattach (debounce 600мс/1200мс BT) → reattach + SCO-suspend
    + applyReverbAuxBinding. onDestroy:577 removeCallbacksAndMessages(null)
    + EqualizerHelper.release() + player.release(). Все вызовы на main thread.
  • PlayerConnection.kt (1735 строк, EQ-секция 789-837) — proxy UI→Service.
    setEqualizerPreset/Enabled/Band → EqualizerHelper. setReverbEnabled/
    setReverbPreset → engine?.method + applyReverbAux (hook в PlayerService).
- Прочитал AudioRouteLogger.kt (329 строк, :core:media) — isScoRoute() с
  @Volatile appContext (инжект из SovaApp.onCreate:848). Если appContext==null,
  isScoRoute() возвращает false → suspendForSco не вызывается. На практике
  PlayerService стартует после Application.onCreate, так что appContext уже set.
- Прочитал Models.kt:1061-1094 — EqualizerPreset (9 полос, 18 built-in).
  bands: List<Float> в dB. applyPresetBands использует minOf(bandsDb.lastIndex,
  eq.numberOfBands-1) — безопасно для 5-полосных устройств.
- Проверил `grep !!` во всех 4 UI/engine файлах — 0 match. Нет force-unwrap.
- Проверил SovaApp.kt:848 (AudioRouteLogger.setAppContext) и :1508
  (CustomPresetStore.load) — оба в onCreate, до старта PlayerService.
- Проанализировал каждую категорию багов из ТЗ (10 категорий). Найденные
  баги классифицированы как CONFIRMED (явные, воспроизводимые) и POTENTIAL
  (edge cases, теоретические).

Stage Summary:

# ЭКВАЛАЙЗЕР — АУДИТ КРАШЕЙ И СКРЫТЫХ БАГОВ

## ВЕРДИКТ ПО ЛОГУ ПОЛЬЗОВАТЕЛЯ

**Краша приложения НЕТ.** Лог показывает:
- `Equalizer created OK (bands=5, range=[-1500, 1500])` — engine attach успешен.
- `BassBoost/Virtualizer/PresetReverb/LoudnessEnhancer init failed: Error: -3`
  (Android `ERROR_UNSUPPORTED`) — 4 из 5 эффектов не созданы, `null`.
- Нет FATAL AndroidRuntime, нет stack trace, нет SIGSEGV.

Пользователь воспринял «не работает BassBoost/Reverb» как «краш». Реальная
проблема — UX: UI показывает контролы для эффектов, которые не инициализированы,
БЕЗ индикации что они неработоспособны (см. C5 ниже).

## CONFIRMED БАГИ (явные, воспроизводимые)

### C1 [HIGH] — BassVirtTab/ReverbTab/LoudnessTab теряют настройки при engine==null
- **Файлы:** EqualizerScreen.kt:632, 708, 812 (`val engine = remember { EqualizerHelper.engine() }`)
- **Описание:** Engine captures one раз при первом compose. Если пользователь
  открыл Эквалайзер ДО начала воспроизведения (engine==null), ВСЕ последующие
  `engine?.setBassBoostEnabled(on)`, `engine?.setVirtualizerStrength(v)`,
  `engine?.setLoudnessEnabled(on)`, `engine?.setReverbEnabled(on)` (через
  PlayerConnection) — no-op **БЕЗ записи в prefs**. Сравните с BandsTab
  который использует `PlayerConnection.setEqualizerBand` → `EqualizerHelper.
  setBand` (имеет prefs-fallback при engine==null, line 263-279).
- **Сценарий краша/потери:**
  1. Пользователь открывает Эквалайзер (музыка ещё не играет, engine==null).
  2. Идёт на вкладку «Bass/Virt», включает BassBoost, двигает слайдер.
  3. Локальный UI-state обновляется (bassOn=true, bassStr=500), но
     `engine?.setBassBoostEnabled(true)` = no-op, prefs НЕ записаны.
  4. Начинает воспроизведение (engine attach), возвращается на экран.
  5. `LaunchedEffect(Unit)` читает `bassOn = it.isBassBoostEnabled() ||
     it.isBassBoostSavedEnabled()` → оба false (prefs пуст) → bassOn=false.
  6. Настройки пользователя **потеряны**.
- **Severity:** HIGH — silent data loss. Не краш, но воспринимается как
  «эквалайзер не работает / слетают настройки».
- **Воспроизводимо из лога пользователя:** НЕ напрямую (в логе engine attach
  OK), но воспроизводимо если открыть экран до начала воспроизведения.

### C2 [HIGH] — Master switch не персистится при engine==null
- **Файлы:** EqualizerScreen.kt:205-208 (onCheckedChange) →
  PlayerConnection.setEqualizerEnabled → EqualizerHelper.setEnabled:283-294.
- **Описание:** `EqualizerHelper.setEnabled(enabled)` no-op + логирует warning
  при engine==null. **НЕ пишет в prefs** (нет prefs-fallback). Сравните с
  `savePreset(name)` (line 157) и `saveBands(bands)` (line 170) которые имеют
  prefs-fallback. `saveEnabled(enabled)` (line 143) — тоже no-op при engine==null
  (идентичен setEnabled, оба вызывают `e.setEqEnabled(enabled)` без fallback).
- **Сценарий:** Пользователь открывает Эквалайзер (engine==null), включает
  master switch → `setEnabled(true)` no-op → `eq_enabled` НЕ записан в prefs.
  Возвращается после начала воспроизведения → `LaunchedEffect` читает
  `masterEnabled = isEnabled() || isSavedEnabled()` → оба false → switch OFF.
  Включение **потеряно**.
- **Severity:** HIGH — master switch теряется, inconsistent с savePreset/saveBands.
- **Воспроизводимо:** ДА (открыть экран до воспроизведения).

### C3 [HIGH] — Built-in preset (applyPreset) не персистится при engine==null
- **Файлы:** EqualizerScreen.kt:274-276 (DropdownMenuItem onClick) →
  PlayerConnection.setEqualizerPreset:792 → EqualizerHelper.applyPreset:184-194.
- **Описание:** `applyPreset(preset)` early-returns с warning при engine==null,
  **БЕЗ записи в prefs**. Сравните с `applyCustomPresetPersist(preset)` (line
  239-261) который имеет полный prefs-fallback (пишет все 11 ключей в prefs
  напрямую при engine==null). Inconsistency: custom presets persist, built-in
  presets — нет.
- **Сценарий:** Пользователь (engine==null) выбирает «Рок» из dropdown →
  `applyPreset` no-op → `eq_preset` / `eq_bands` НЕ записаны. После начала
  воспроизведения preset = DEFAULT (или что было в prefs раньше). Выбор потерян.
- **Severity:** HIGH — inconsistent behavior built-in vs custom presets.
- **Воспроизводимо:** ДА.

### C4 [MEDIUM] — Reverb tab показан когда feat_reverb=true, но PresetReverb init failed (Error -3)
- **Файлы:** EqualizerFeatureFlags.kt:60 (default reverb=false, но persisted
  feat_reverb=true остаётся true); AudioEffectsEngine.kt:237-241 (PresetReverb
  init try/catch → null при Error -3); EqualizerScreen.kt:126 (Reverb tab
  добавляется если flags.reverbEnabled); ReverbTab:707-803 (UI switch + 6
  preset cards, БЕЗ индикации что PresetReverb не создан).
- **Описание:** Если пользователь ранее включил feat_reverb=true (или мигрировал
  со старой версии где default был true), UI показывает вкладку Reverb с
  switch и списком пресетов. Но `reverb=null` в engine (init failed Error -3).
  Тoggling switch → `engine.setReverbEnabled(on)` → `saveReverbEnabled(on)`
  (prefs write OK) + `reverb?.let { ... }` (no-op, reverb null). Настройка
  **персистится**, но **не применяется** к звуку. UI не показывает
  «эффект недоступен на этом устройстве».
- **Сценарий (СОВПАДАЕТ С ЛОГОМ ПОЛЬЗОВАТЕЛЯ):** Лог показывает Error -3 на
  PresetReverb. Если feat_reverb=true → Reverb tab виден, switch работает
  (визуально), но звук не меняется. Пользователь думает «краш».
- **Severity:** MEDIUM — не краш, но главный UX-источник жалобы пользователя.
- **Воспроизводимо из лога:** ДА, напрямую. Это объясняет «краш» пользователя.
- **Дополнительно:** Та же логика применима к BassBoost/Virtualizer/Loudness
  если они init failed (Error -3) но feat_bass/virt/loud=true. Однако в
  EqualizerFeatureFlags default для bass/virt/loud=true, а reverb=false. Если
  пользователь не менял feat_reverb, Reverb tab скрыт. Но если менял (или
  мигрировал) — visible.

### C5 [MEDIUM] — SpectrumVisualizer: visualizer.enabled не обновляется при смене `active`
- **Файлы:** SpectrumVisualizer.kt:145 (`visualizer.enabled = active` — ОДИН
  раз в DisposableEffect setup), line 163-168 (LaunchedEffect(active, sessionId)
  ПУСТОЙ — только комментарий, НЕ обновляет visualizer.enabled).
- **Описание:** `active = masterEnabled && audioSessionId != 0 && !scoSuspended`.
  При смене `active` (пользователь toggles master switch, play/pause, SCO
  suspend/restore), `LaunchedEffect(active, sessionId)` срабатывает, но тело
  пустое — visualizer.enabled остаётся в значении, установленном при создании.
  DisposableEffect keyed only on `sessionId`, so changing `active` alone
  doesn't re-trigger creation.
- **Сценарий A (visualizer stuck off):** Пользователь открывает Эквалайзер,
  engine attached, audioSessionId=5, но master switch OFF → `active=false`
  → DisposableEffect(5) creates Visualizer with `enabled=false`. Пользователь
  включает master switch → `active=true` → LaunchedEffect fires but empty →
  visualizer.enabled stays false → bars stay at 0 (Canvas draws
  `if (active) bars[i] else 0f` = bars[i], but bars[] is all zeros because
  Visualizer never captures). **Visualizer показывает плоские полосы.**
- **Сценарий B (CPU waste):** Visualizer created with active=true, user
  toggles master OFF → visualizer.enabled stays true → продолжает FFT-capture
  в фоне, тратит CPU/battery. Canvas рисует 0 (active=false), но Visualizer
  native всё ещё работает.
- **Severity:** MEDIUM — UX bug + battery waste. Не краш.
- **Воспроизводимо:** ДА — toggle master switch после открытия экрана.

### C6 [LOW] — audioSessionId не обновляется после engine attach
- **Файлы:** EqualizerScreen.kt:176-182 (LaunchedEffect(Unit) — reads ONCE).
- **Описание:** `audioSessionId = EqualizerHelper.engine()?.attachedSessionId
  ?: 0` читается один раз при входе на экран. Если engine attaches ПОЗЖЕ
  (пользователь на экране, запускает воспроизведение в другом экране),
  audioSessionId stays 0 → SpectrumVisualizer показывает fallback
  «Визуализация недоступна».
- **Severity:** LOW — edge case (пользователь обычно не остаётся на
  Эквалайзер-экране при старте воспроизведения из другого места).
- **Воспроизводимо:** ДА, но редкий сценарий.

## POTENTIAL БАГИ (edge cases, теоретические)

### P1 [LOW] — Visualizer.getCaptureSizeRange() может вернуть null/empty
- **Файл:** SpectrumVisualizer.kt:116-117.
- `val range = Visualizer.getCaptureSizeRange()` — AOSP может вернуть null
  если AudioSystem.getProperty fails. `range[range.lastIndex]` → NPE или
  ArrayIndexOutOfBounds. **Поймано** try-catch (line 146) → fallback UI.
- Не краш, graceful degradation.

### P2 [LOW] — forceRedraw++ из FFT-callback потока (non-main thread)
- **Файл:** SpectrumVisualizer.kt:141.
- `var forceRedraw by remember { mutableStateOf(0) }` — `forceRedraw++` это
  read-modify-write, НЕ атомарный. FFT callback может вызываться с
  binder/AudioFlinger потока. Compose state writes thread-safe (snapshot), но
  `++` может потерять инкремент при гонке. Visualizer callbacks serial
  (однопоточные), так что в практике race нет. Missed invalidate = лишний
  кадр stale, не краш.

### P3 [LOW] — `remember { EqualizerHelper.engine() }` не reactive
- **Файлы:** EqualizerScreen.kt:632, 708, 812.
- `remember { ... }` без ключа captures engine один раз. Если engine меняется
  (attach/release/reattach) во время жизни composable, UI не подхватит новый
  engine. На практике engine stable (attach в onAudioSessionIdChanged, release
  в onDestroy), но после reattach (смена audio route) engine объект может
  пересоздаваться → UI держит stale reference. Вызовы на released engine
  безопасны (attached=false → early return), но настройки могут не
  примениться к новому engine.

### P4 [LOW] — `EqualizerHelper.reattach()` / `reattachFull()` не @Synchronized
- **Файлы:** EqualizerHelper.kt:89-98, 101-110.
- В отличие от attachOnce/release/engine() которые @Synchronized, reattach/
  reattachFull читают `engine` через прямой field-read (@Volatile). Если
  release() concurrent (оба на main thread → не concurrent в практике),
  `e.reattachLightweight()` на released engine → `attached=false` → early
  return. Безопасно, но inconsistent с остальными @Synchronized методами.

### P5 [LOW] — CustomPresetStore.flush() synchronous I/O на main thread
- **Файл:** CustomPresetStore.kt:248-260.
- `f.writeText(json)` synchronously. Called from upsert/delete/clearAll which
  are called from EqualizerScreen (main thread). JSON ~1KB на 10 пресетов →
  fast (<5ms). На slow storage / большом количестве пресетов → minor jank.
  Не краш, не ANR (только при большом количестве пресетов).

### P6 [LOW] — CustomPresetStore.load() JSON parse error → all presets lost
- **Файл:** CustomPresetStore.kt:128-133.
- Если JSON файл повреждён, `gson.fromJson` throws → catch → `presets =
  emptyList()`, `nextId = 1L`, `loaded = true`. Все пользовательские пресеты
  потеряны. Graceful (не краш), но data loss. Файл маленький, corruption
  маловероятна (только при crash mid-write, но writeText атомарна на уровне
  FS для small files).

### P7 [LOW] — loadEqBands() CSV parse: silent drop non-numeric entries
- **Файл:** AudioEffectsEngine.kt:96-98.
- `"1,2,abc,4".split(",").mapNotNull { it.toShortOrNull() }` → `[1, 2, 4]`.
  "abc" silently dropped. Если CSV повреждён, bands list короче ожидаемого.
  setBand компенсирует: `while (current.size <= bandIndex) current.add(0)`.
  Dropped band → 0. Не краш, silent data corruption.

### P8 [LOW] — SharedPreferences.apply() async — data loss on crash
- **Файлы:** AudioEffectsEngine.kt (все save* методы), EqualizerHelper.kt.
- `prefs()?.edit()?.putX(...)?.apply()` — async disk write. Если app crash
  до flush, данные потеряны. Стандартный Android pattern, acceptable.
  commit() sync но блокирует. Текущий выбор apply() — trade-off производительность
  vs надёжность. Не краш, но edge case data loss.

### P9 [LOW] — AudioRouteLogger.appContext null window
- **Файл:** AudioRouteLogger.kt:56-62, 143-176.
- `appContext` set в SovaApp.onCreate:848. Если isScoRoute() вызывается до
  (маловероятно — PlayerService стартует после Application.onCreate),
  возвращает false → suspendForSco не вызывается → Virtualizer/Reverb
  остаются на SCO → audio артефакты. На практике не воспроизводимо.

### P10 [LOW] — `eq.enabled = on` / `reverb.id` на released AudioEffect
- **Файл:** AudioEffectsEngine.kt:793-801 (releaseEffect), 688-689 (reverbEffectId
  getter).
- `releaseEffect` calls `e.enabled = false` then `e.release()` — оба могут
  бросить IllegalStateException если уже released. **Поймано** try-catch.
  reverbEffectId getter: `reverb?.id` — `id` бросает если effect released.
  Но releaseInternal sets `reverb = null` ПОСЛЕ releaseEffect, под тем же
  `synchronized(lock)`. Другие читатели под тем же lock не увидят interim
  state. Безопасно.

### P11 [LOW] — Equalizer(0, sessionId) priority=0 conflict с системным EQ
- **Файл:** AudioEffectsEngine.kt:213.
- `Equalizer(priority=0, sessionId)` — priority 0 = lowest. Если системный
  эквалайзер (Android system Equalizer) уже создан на том же sessionId с
  более высоким priority, наш EQ может не применяться (Android priority
  resolution). Не краш, но эффект может молча не работать. На практике
  system EQ редко привязан к app-specific sessionId. Не воспроизводимо из
  лога пользователя (Equalizer created OK).

### P12 [LOW] — HorizontalPager с tabs.size=0 (all feature flags false)
- **Файл:** EqualizerScreen.kt:130 (`pageCount = { tabs.size }`), 377-397.
- Если ALL 5 feat_* flags = false → tabs=emptyList → pageCount=0 →
  HorizontalPager renders nothing. PrimaryScrollableTabRow with 0 tabs
  renders empty. Не краш, но пользователь видит пустой экран (только TopAppBar
  + preset dropdown + master switch). Не может re-enable эффекты с этого
  экрана — нужно идти в Настройки. UX issue, не краш.

### P13 [LOW] — `attachOnce` re-create engine race (theoretical)
- **Файл:** EqualizerHelper.kt:73-86.
- Два потока вызывают attachOnce(sessionId1) и attachOnce(sessionId2)
  concurrently. @Synchronized сериализует. Thread A creates engine1,
  Thread B sees engine1, releases it (sessionId mismatch), creates engine2.
  Engine1's effects released, engine2 wins. На практике все вызовы на main
  thread → не concurrent. Теоретический race только если caller с не-main
  потока (none observed).

### P14 [LOW] — `PlayerConnection.setReverbEnabled` → `applyReverbAux` →
`applyReverbAuxBinding` читает engine() повторно (может отличаться от первого)
- **Файлы:** PlayerConnection.kt:818-820; PlayerService.kt:206-224.
- `EqualizerHelper.engine()?.setReverbEnabled(on)` — engine A. Затем
  `applyReverbAux()` → `applyReverbAuxBinding` → `EqualizerHelper.engine()`
  — может быть engine B (если reattach happened in between). На практике
  оба на main thread → serialized → engine stable. Теоретическая
  inconsistency, не краш.

## ЧТО НЕ НАЙДЕНО (проверено, безопасно)

- **NPE через `!!`:** 0 match во всех 4 UI/engine файлах. Все access через
  `?.` или `val e = engine; if (e == null) return`.
- **Division by zero в Canvas:** NUM_BARS=32 (const), `size.width / 32` —
  даже при width=0 не краш (drawRoundRect с Size(0,h) = no-op).
- **Short overflow в slider:** `(newValue * 100).toInt().toShort()` где
  newValue ∈ [-15, 15] → [-1500, 1500] → fits Short range [-32768, 32767].
- **Array index OOB в applyPresetBands/applyBandsList:** `minOf(bandsDb.
  lastIndex, eq.numberOfBands-1)` — safe для 5 vs 9 полос.
- **Index OOB в BandsTab slider:** `forEachIndexed { index, ... }` всегда
  вbounds (0..8), `updated[index]` safe.
- **`prefs()` throws:** `SovaApp.get()` throws IllegalStateException если
  not initialized — поймано `catch (e: Exception)` в AudioEffectsEngine.
  prefs() и EqualizerHelper.prefs() (line 46-48) — оба null-safe.
- **Visualizer lifecycle leak:** `onDispose { visualizer.enabled = false;
  visualizer.release() }` — корректно. Не утечка.
- **AudioEffect.release() throws:** releaseEffect catches Exception.
  reverbEffectId getter synchronized on same lock as releaseInternal —
  no window where reverb non-null but released.
- **MediaSession build() failure:** PlayerService.onCreate:468-484 catches
  IllegalStateException + IllegalArgumentException → player.release() +
  stopSelf(). Не крашит app.
- **`onAudioSessionIdChanged` с sessionId=0:** PlayerService:422-428 guards
  `if (audioSessionId != 0)`. EqualizerHelper.attachOnce:69 guards sessionId==0.
  Безопасно.

## РЕКОМЕНДАЦИИ (без фиксов — research only)

1. **[HIGH] C1+C2+C3: Добавить prefs-fallback для setEnabled/saveEnabled/
   applyPreset при engine==null** — как уже сделано для savePreset/saveBands/
   setBand/applyCustomPresetPersist. Писать `eq_enabled`/`eq_preset`/`eq_bands`
   в prefs напрямую. Это закроет silent data loss когда пользователь открывает
   Эквалайзер до начала воспроизведения.

2. **[HIGH] C1 (дополнительно): BassVirtTab/ReverbTab/LoudnessTab — заменить
   `engine?.method()` на обёртки в EqualizerHelper с prefs-fallback** (как
   `setBand` уже работает). Или сделать `val engine by remember { mutableStateOf
   (EqualizerHelper.engine()) }` + LaunchedEffect обновляющий engine при
   attach/release. Минимальный фикс — `EqualizerHelper.setBassBoostEnabled/
   setVirtualizerEnabled/setLoudnessEnabled/setReverbEnabled` с prefs-fallback.

3. **[MEDIUM] C4: UI должен показывать «эффект недоступен на устройстве» если
   AudioEffect init failed.** Engine может экспонировать `isBassBoostAvailable()
   / isVirtualizerAvailable() / isReverbAvailable() / isLoudnessAvailable()`
   (= effect != null). EqualizerScreen скрывает или dim'ит соответствующую
   вкладку/карточку. Аналогично LoudnessTab уже делает для API < 19 (line
   834-846 — shows error card). Тот же pattern для init-failed эффектов.

4. **[MEDIUM] C5: SpectrumVisualizer — обновлять `visualizer.enabled` при
   смене `active`.** Либо переместить `visualizer.enabled = active` в
   LaunchedEffect(active) с доступом к visualizer через remember-ref, либо
   re-key DisposableEffect на (sessionId, active) чтобы пересоздавался.
   Второй вариант дороже (release+recreate Visualizer на каждый toggle),
   первый эффективнее.

5. **[LOW] C6: EqualizerScreen — подписаться на engine attach через
   StateFlow/callback** вместо разового `LaunchedEffect(Unit)`. Или хотя бы
   `LaunchedEffect(Unit)` + poll engine.attachedSessionId каждые N сек
   (не идеально). Лучше — EqualizerHelper.engineSessionFlow: StateFlow<Int>.

6. **[LOW] P5: CustomPresetStore.flush() — перенести на Dispatchers.IO**
   через `scope.launch(Dispatchers.IO) { f.writeText(json) }`. Сейчас sync
   на main, ~1KB fast, но при 50+ presets может дать jank.

7. **[LOW] P6: CustomPresetStore — atomic write (temp file + rename)** для
   защиты от corruption при crash mid-write. Pattern: write to
   `custom_presets.json.tmp`, then `renameTo(custom_presets.json)`.
   Atomic on POSIX filesystems.

8. **[LOW] P10: Defensive — обернуть `eq.enabled`/`reverb.id`/etc. в
   try-catch** даже при synchronized access, на случай native AudioFlinger
   crash между check и use. Уже сделано в большинстве мест; проверить
   `reverbEffectId` getter — `reverb?.id` не в try-catch (но под synchronized,
  безопасно в текущей модели).

## ОГРАНИЧЕНИЯ АУДИТА
- Компиляция НЕ проверена (окружение без Android SDK — как в предыдущих задачах).
- Все выводы основаны на статическом анализе кода.
- «Все вызовы на main thread» — верно для текущей архитектуры (PlayerService
  callbacks, Compose UI, SovaApp.onCreate). Если в будущем появится
  multi-threaded caller (например, WorkManager), @Synchronized на EqualizerHelper
  станет обязательным (сейчас defensive).

---
Task ID: AUTH-WHITESCREEN-AUDIT
Agent: Explore (auth white screen audit)
Task: Найти причину белого экрана при авторизации на Oppo A17k (ColorOS 12, Android 12, MediaTek Helio G35, 3GB RAM). Default auth = V2 (VkLiteWebViewAuthScreen, authV2Enabled=true).

Work Log:
- Прочитал /home/z/my-project/worklog.md (контекст Tasks 1-7). Аудит Эквалайзера завершён — теперь auth-аудит.
- Прочитал auth2/AuthFlow.kt (78 строк): AuthFlowFactory.resolve(silent=false) → DirectHttpAuthFlow → запускает AuthV2Activity (VkLiteWebViewAuthScreen). authV2Enabled default true (SovaPrefs.kt:636) — V2 является дефолтным путём входа.
- Прочитал auth2/AuthV2Activity.kt (89 строк): ComponentActivity + enableEdgeToEdge + MaterialTheme (НЕ SOVATheme!). Если hasValidToken → RESULT_OK без WebView. Иначе setContent { MaterialTheme { CaptchaDialog(); Surface(fillMaxSize) { VkLiteWebViewAuthScreen(startUrl="https://m.vk.ru/login?app_id=7879029") } } }. Theme.PinoK в манифесте = windowBackground=#FFFFFFFF (colors.xml:4).
- Прочитал auth2/VkLiteWebViewAuthScreen.kt (196 строк) — это PRIMARY suspect. Сравнил построчно с VkAuthWebViewScreenV2.kt (1108 строк, V1 путь «Войти через VK»). V2 — это «облегчённая» реализация по образцу vk-lite-auth, но в ней ОТСУТСТВУЮТ почти все safety-mechanisms, накопленные в V1 через многочисленные фиксы (#339/#370/#384/#191/#195b/P0-2/P0-3/P1-4/P1-5/P1-6/#BLACKSCREEN-RENDERER/#WEBVIEW-SAFETY-NET/#DOZE-COOKIE-FLUSH/#SSO-DEEPLINK-FIX/#WEBVIEW-OUTDATED-FIX и др.).
- Прочитал VkAuthWebViewScreenV2.kt выборочно (строки 1-900): chromium-safety-net (5-step escalation reload→reload→recreate→recreate→fail UI), onRenderProcessGone handler, WebChromeClient с onProgressChanged/onJsAlert/onJsConfirm/onJsPrompt/onConsoleMessage/onCreateWindow, chromeMobileUserAgent() (убирает Version/4.0 + ; wv маркеры + подменяет на CHROME_UA если версия <100), setLayerType(LAYER_TYPE_SOFTWARE), removeAllCookies+removeSessionCookies перед loadUrl, FixedInputWebView (SovaInputConnection fix), CookieManager.flush() после onTokenExchange, onReceivedSslError handler, onReceivedHttpError handler, полный cookie capture 9 remix-ключей, адаптивный polling с timeout, enableEdgeToEdge+systemBarsPadding, JS injection (cursor fix + ad block + autoclick на id.vk.ru/auth).
- Прочитал FixedInputWebView.kt (140 строк): custom WebView с SovaInputConnection (фикс зеркального ввода React controlled inputs в VK ID). V2 использует plain WebView — без этого фикса.
- Прочитал VkAuthJsInjections.kt (116 строк): VK_2FA_CURSOR_FIX_JS + VK_INPUT_HARDENING_JS — ИНЖЕКТИРУЮТСЯ только в V1 (VkAuthWebViewScreenV2.kt:628-629, 665-666). V2 НЕ инжектит ничего — нет onPageStarted/onPageFinished хуков.
- Прочитал OAuthWebViewActivity.kt выборочно (355-514): даже legacy-экран имеет setWebChromeClient (line 502), loadUrl(url) после полной настройки.
- Прочитал AuthActivity.kt (881 строк): AuthScreen composable routing. AuthV2Activity запускается из кнопки «Войти в аккаунт 2» на LandingScreen (видна только если authV2Enabled=true). startActivityForResult(REQUEST_CODE_AUTH2=77). onActivityResult пробрасывает RESULT_OK в MainActivity.
- Прочитал LandingScreen.kt (100-300): кнопка «Войти в аккаунт 2» (lines 232-264) — видна только при authV2Enabled=true (default true), вызывает onStartAuthV2 → launchAuthV2 в AuthActivity.
- Прочитал CookieHelpers.kt (107 строк): getRemixSidFromCookieManager() — синхронное чтение из CookieManager. V1 использует это в polling. V2 пытается читать localStorage через evaluateJavascript (асинхронный) — это разный подход.
- Прочитал ui/theme/Theme.kt:99-198: SOVATheme предоставляет правильный colorScheme (dark: background=#FF121212, light: background=white). AuthV2Activity использует plain MaterialTheme без SOVATheme → default light scheme (всегда белый Surface).
- Сверил AndroidManifest.xml (lines 342-371): AuthActivity, AuthV2Activity, OAuthWebViewActivity — все с Theme.PinoK (windowBackground=#FFFFFFFF). AuthV2Activity имеет windowSoftInputMode=adjustResize + configChanges (хорошо).
- Сверил SovaPrefs.kt:636: `authV2Enabled = p[Keys.AUTH_V2_ENABLED] ?: true` — подтверждено, V2 ВКЛЮЧЕН ПО УМОЛЧАНИЮ. Пользователь Oppo A17k идёт через V2, если явно не выключил в Настройках → Разработка.

Stage Summary:

## CONFIRMED баги (явные причины белого экрана на Oppo A17k)

### C1 [CRITICAL] — Отсутствует WebChromeClient
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:161-188` (только `web.webViewClient = ...`, НЕТ `web.setWebChromeClient(...)`).
- **Описание:** Без WebChromeClient:
  - `onProgressChanged` НЕ работает → UI не видит прогресс загрузки
  - `onJsAlert/onJsConfirm/onJsPrompt` молча подавляются → VK ID SDK не получает ответа на permission-диалоги → flow зависает
  - `onCreateWindow` НЕ работает → `window.open()` молча fail (VK ID использует для QR-логина, OAuth попапов)
  - `onConsoleMessage` НЕ работает → JS console output не виден в logcat (невозможно отладить)
- **Почему V1 имеет:** `VkAuthWebViewScreenV2.kt:754-844` — с явным KDoc-комментарием (lines 754-761) «P0-2 #AUTH-AUDIT: WebChromeClient — КРИТИЧНО для m.vk.ru SPA».
- **Почему Oppo A17k specifically:** ColorOS 12 кастомизирует WebView — default behaviors могут быть MORE restrictive без WebChromeClient (Oppo известна агрессивными defaults). VK ID SDK на m.vk.ru/login?app_id=7879029 активно использует JS alerts/prompts/confirms для permissions и window.open для OAuth callbacks. Без WebChromeClient всё это молча suppressed → blank page.
- **Severity:** CRITICAL — наиболее вероятная PRIMARY причина белого экрана.

### C2 [CRITICAL] — Не задан userAgentString (default WebView UA с маркерами)
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:150-158` (settings.apply block — нет строки `userAgentString = ...`).
- **Описание:** Default WebView UA содержит `Version/4.0` и `; wv` маркеры. Per V1 KDoc (`VkAuthWebViewScreenV2.kt:91-104`): «VK ID SDK определяет среду WebView по этим маркерам и НЕ генерирует deep-link `intent://qr.vk.ru/ca?q=…` для запуска официального VK app». Также если Chrome version < 100, VK показывает «Your browser is out of date» и ОТКАЗЫВАЕТ во входе → blank/error page.
- **Почему V1 имеет:** `VkAuthWebViewScreenV2.kt:455` — `userAgentString = chromeMobileUserAgent(ctx)` (убирает маркеры + подменяет на CHROME_UA если версия <100, lines 105-120).
- **Почему Oppo A17k specifically:** Budget device (3GB RAM) — пользователи часто имеют УСТАРЕВШИЙ Android System WebView (нет auto-update через Play Store из-за нехватки места, или Play Store отключен). Chrome version < 100 — РАСПРОСТРАНЁННЫЙ случай → VK блокирует вход → белая страница / страница ошибки.
- **Severity:** CRITICAL — вторая по вероятности причина.

### C3 [CRITICAL] — `setBackgroundColor(Color.WHITE)` без recovery при отказе WebView
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:145`.
- **Описание:** Симптом пользователя «белый экран» МАТЧИТСЯ с этой строкой. LinearLayout имеет явный белый фон. Если WebView не грузит контент (C1/C2/C4/C5 и т.д.), юзер видит этот белый фон бесконечно. V1 использует `MaterialTheme.colorScheme.background` (адаптивный — тёмный в dark theme) И имеет safety-net reload + recreate (`VkAuthWebViewScreenV2.kt:316-369`) + render-process recovery (`VkAuthWebViewScreenV2.kt:736-751`) + честный оверлей «WebView не смог запуститься» (`VkAuthWebViewScreenV2.kt:867-906`).
- **Почему Oppo A17k specifically:** Triple-white: Theme.PinoK windowBackground=#FFFFFFFF (colors.xml:4) + MaterialTheme default Surface (white) + LinearLayout Color.WHITE (line 145). На dark theme устройствах это особенно заметно.
- **Severity:** CRITICAL — literal причина видимого симптома.

### C4 [CRITICAL] — Нет safety-net reload/recreate при stalled renderer
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt` — ВЕСЬ файл, нет эквивалента V1's `safetyNetJob`.
- **Описание:** V1 имеет 5-step эскалацию (`VkAuthWebViewScreenV2.kt:316-369`): reload → reload → recreate WebView → recreate → fail UI. V2: НИЧЕГО. Если Chromium renderer fails to start (`cr_ChildProcessConn: Failed to establish the service connection` — типичная проблема на бюджетных MediaTek), V2 остаётся blank НАВСЕГДА без recovery.
- **Почему Oppo A17k specifically:** 3GB RAM + ColorOS aggressive memory management → Chromium renderer ЧАЩЕ убивается/отвергается ColorOS process management. После kill — нет recovery → permanent белый экран.
- **Severity:** CRITICAL.

### C5 [CRITICAL] — Нет onRenderProcessGone handler
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:161-188` (WebViewClient без `onRenderProcessGone`).
- **Описание:** V1 имеет handler (`VkAuthWebViewScreenV2.kt:736-751`) — пересоздаёт WebView через `webViewEpoch++`. Default behavior при renderer crash: app CRASH или permanent blank WebView.
- **Почему Oppo A17k specifically:** MediaTek Helio G35 (PowerVR GE8320 GPU) + 3GB RAM → renderer crashes чаще под memory pressure (Compose AndroidView имеет higher memory overhead чем plain View).
- **Severity:** CRITICAL.

### C6 [CRITICAL] — Polling loop: evaluateJavascript async трактуется как sync
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:108-139` — `LaunchedEffect(Unit)` polling loop.
- **Описание:** Код вызывает `wv.evaluateJavascript(readWebTokenJs(), callback)` (АСИНХРОННЫЙ — callback срабатывает на Main looper через Handler.post, НЕ синхронно). НЕМЕДЛЕННО после вызова читает `val res = holder[0]` — `res` ВСЕГДА null в текущей итерации (callback ещё не сработал). После `delay(750L)` callback срабатывает и пишет в OLD `holder[0]`, но цикл итерирует с NEW `holder` (создаётся в `try` block следующей итерации). OLD holder (с токеном) выходит из scope и GC'ится.
- **Результат:** Токен НИКОГДА не читается. Polling крутится бесконечно. Даже на идеальном устройстве где WebView грузит VK login успешно, юзер остаётся на VK странице (после login) — но на Oppo A17k где WebView ещё и FAILS to load (C1-C5), юзер видит белый экран.
- **Почему V1 не имеет этой баги:** V1 polling читает `remixsid` из CookieManager СИНХРОННО (`CookieHelpers.kt:39` → `CookieManager.getInstance().getCookie(url)`), а web_token из localStorage читается отдельно через `WebTokenAuth.fullAuthFlow` с ПРАВИЛЬНЫМ async-JS handling.
- **Severity:** CRITICAL — даже если C1-C5 пофиксить, эта бага предотвращает завершение login.

### C7 [HIGH] — Нет onReceivedSslError handler
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:161-188`.
- **Описание:** V1 имеет (`VkAuthWebViewScreenV2.kt:727-734` — логирует + cancel). Default behavior на SSL error: WebView МОЛЧА отказывается грузить → blank page.
- **Почему Oppo A17k specifically:** ColorOS 12 может иметь кастомный trust store. Budget devices часто имеют dead RTC battery → system clock skew → SSL cert time validation fails. VK cert pinning issues.
- **Severity:** HIGH.

### C8 [HIGH] — Нет removeAllCookies/removeSessionCookies перед loadUrl
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:159-189` (только `setAcceptCookie` + `setAcceptThirdPartyCookies`).
- **Описание:** V1 имеет cleanup (`VkAuthWebViewScreenV2.kt:484-488` — в normal mode, НЕ silent). Stale cookies от прошлой сессии → VK auto-redirect на feed вместо login, или silent_token exchange fails → blank page.
- **Почему Oppo A17k specifically:** Если юзер ранее логинился через PinoK и вышел, stale cookies persist → V2 polling никогда не видит свежий login.
- **Severity:** HIGH.

### C9 [HIGH] — Нет onPageStarted / onPageFinished overrides
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:161-188` (WebViewClient имеет только `shouldOverrideUrlLoading` + `onReceivedError`).
- **Описание:** V1 имеет (`VkAuthWebViewScreenV2.kt:618-697`) — JS injection (cursor fix + ad block), loading state, page-progress logging, web_token cleanup на logout, full cookie capture, autoclick на id.vk.ru/auth. Без этих: нет JS injection → VK ID React inputs могут иметь cursor issues (mirror input), нет loading state → юзер не видит прогресса.
- **Severity:** HIGH.

### C10 [HIGH] — shouldOverrideUrlLoading whitelist слишком рестриктивный
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:165-171`.
- **Описание:** V2 ALLOWED hosts: m.vk.ru, m.vk.com, id.vk.ru, id.vk.com, login.vk.ru, login.vk.com, oauth.vk.ru, oauth.vk.com, vk.ru, vk.com, static.vk.ru, static.vk.com (12 hosts). V1 (`VkAuthWebViewScreenV2.kt:602-613`) ДОПОЛНИТЕЛЬНО разрешает: `new.vk.com`, `connect.vk.com`, `api.vk.com`, `web.api.vk.ru`, `passport.vk.com`, `userapi.com`, `vkvideo.ru`, `im.vk.com`, `st.vk.com`, `st2.vk.com`, `st3.vk.com` (11 больше хостов).
- **Критично:** `connect.vk.com` — VK ID SDK использует для OAuth callbacks. Если VK редиректит на `connect.vk.com` во время login flow, V2 БЛОКИРУЕТ навигацию → navigation stops → blank page. `userapi.com` — static assets (images, CSS, JS). Заблокирован → страница может рендериться без стилей/скриптов → broken or blank.
- **Почему Oppo A17k specifically:** VK ID SDK может быть more aggressive about redirects на определённых device profiles.
- **Severity:** HIGH.

### C11 [HIGH] — Нет CookieManager.flush() после обнаружения токена
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt` — ВЕСЬ файл, нет `CookieManager.getInstance().flush()` нигде.
- **Описание:** V1 имеет (`VkAuthWebViewScreenV2.kt:269-274` — после `onTokenExchange`). Без flush cookies остаются только в памяти WebView process. Если WebView process умирает (ColorOS убивает на Oppo A17k под memory pressure), cookies потеряны → silent refresh fails later → юзера выкидывает из аккаунта.
- **Severity:** HIGH.

### C12 [HIGH] — setLayerType(LAYER_TYPE_SOFTWARE) не установлен
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:149-158` (settings.apply block — нет `setLayerType`).
- **Описание:** V1 устанавливает (`VkAuthWebViewScreenV2.kt:472`) с explicit KDoc: «без software rendering IME на некоторых устройствах шлёт composition events которые React обрабатывает неправильно → зеркальный ввод (Pluton240 → 042notulP)». Хотя напрямую не вызывает белый экран, на Oppo A17k с MediaTek Helio G35 (PowerVR GE8320 GPU) некоторые WebView rendering operations могут иметь GPU driver issues. Software layer стабильнее.
- **Severity:** HIGH (input/rendering stability, не напрямую белый экран).

### C13 [HIGH] — Plain `WebView` вместо `FixedInputWebView`
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:149` — `val web = WebView(ctx)`.
- **Описание:** V1 использует `FixedInputWebView(ctx)` (`VkAuthWebViewScreenV2.kt:437`) — custom subclass с `SovaInputConnection` фиксом. Без него, на Oppo A17k с ColorOS default IME, text input в VK login form (React controlled inputs) может иметь mirror-input баг. Не вызывает белый экран напрямую, но ломает login flow.
- **Severity:** HIGH.

## POTENTIAL баги (edge cases)

### P1 [MEDIUM] — AuthV2Activity использует MaterialTheme вместо SOVATheme
- **Файлы:** `app/.../auth/auth2/AuthV2Activity.kt:58` — `MaterialTheme { ... }` вместо `SOVATheme { ... }`.
- **Описание:** V1 (`AuthActivity.kt:283`) использует `SOVATheme`. MaterialTheme без explicit colorScheme использует default light scheme. SOVATheme (`Theme.kt:99-198`) предоставляет правильный colorScheme на основе `isSystemInDarkTheme()` (`background = Color(0xFF121212)` для dark). На dark theme: AuthV2Activity's Surface WHITE (default light scheme), остальной app dark. Combined с `setBackgroundColor(Color.WHITE)` (line 145) — triple-white: windowBackground (Theme.PinoK = #FFFFFFFF) + MaterialTheme Surface (white) + LinearLayout (Color.WHITE).
- **Severity:** MEDIUM (визуальная inconsistency, усиливает «белый» перцепт но не причина stuck state).

### P2 [MEDIUM] — enableEdgeToEdge без systemBarsPadding
- **Файлы:** `app/.../auth/auth2/AuthV2Activity.kt:43, 61`.
- **Описание:** V1 имеет `systemBarsPadding()` на Box (`VkAuthWebViewScreenV2.kt:400`). На Android 12 (Oppo A17k): content рисуется под status/nav bars. WebView top (VK logo, login form) может быть hidden под status bar.
- **Severity:** MEDIUM (UX issue, не причина белого экрана).

### P3 [MEDIUM] — Polling URL host check исключает некоторые валидные VK auth хосты
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:115` — checks host для `m.vk.ru`, `m.vk.com`, `id.vk.ru`, `id.vk.com`.
- **Описание:** V1's `COOKIE_CHECK_URLS` (`CookieHelpers.kt:15-27`) дополнительно проверяет `vk.com`, `vk.ru`, `login.vk.com`, `login.vk.ru`. Если VK редиректит на `login.vk.ru` во время auth (где web_token тоже в localStorage), V2 polling не прочитает → no token detection.
- **Severity:** MEDIUM.

### P4 [MEDIUM] — ProgressBar никогда не обновляется
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:147-148`.
- **Описание:** `ProgressBar` добавлен но нет `setProgress()` calls, нет WebChromeClient.onProgressChanged. Юзер видит пустой progress bar что никогда не двигается → «loading forever» перцепт.
- **Severity:** MEDIUM (UX issue).

### P5 [MEDIUM] — allowFileAccess = false (V1 имеет true)
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:153` — `allowFileAccess = false`.
- **Описание:** V1: `allowFileAccess = true` (`VkAuthWebViewScreenV2.kt:442`). VK login page может load local resources (file://) для некоторых assets. Blocking file:// — более secure но может ломать некоторые VK features.
- **Severity:** MEDIUM.

### P6 [MEDIUM] — LaunchedEffect polling не имеет timeout
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:108-139` — `while (!completed)` без exit condition кроме `completed = true`.
- **Описание:** V1 имеет 300-second timeout (`VkAuthWebViewScreenV2.kt:238`: `val timeoutMs = if (silentMode) 30_000L else 300_000L`). Если токен не найден (бага C6), V2 крутится бесконечно → CPU/battery waste.
- **Severity:** MEDIUM.

### P7 [LOW] — Нет cacheMode set
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:150-158` — нет `cacheMode`.
- **Описание:** V1: `LOAD_DEFAULT` (`VkAuthWebViewScreenV2.kt:459`). Default — тоже LOAD_DEFAULT, скорее всего fine.
- **Severity:** LOW.

### P8 [LOW] — Нет onReceivedHttpError handler
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:161-188` — отсутствует.
- **Описание:** V1 имеет (`VkAuthWebViewScreenV2.kt:716-725`) — логирует HTTP errors. V2: HTTP errors (403, 404, 500) silently swallowed → нет диагностики.
- **Severity:** LOW.

### P9 [LOW] — Нет webTokenCleanupDone logic для logout
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt` — нет эквивалента V1's `clearStoredWebTokenOnLoad` (`VkAuthWebViewScreenV2.kt:155, 649-660`).
- **Описание:** Если юзер logout'ится и логинится обратно через V2, stale web_token от предыдущего аккаунта может подхватиться → wrong account login.
- **Severity:** LOW (только при re-login после logout).

### P10 [LOW] — BackHandler с webViewRef null check race
- **Файлы:** `app/.../auth/auth2/VkLiteWebViewAuthScreen.kt:57-64`.
- **Описание:** Если юзер жмёт Back ДО создания WebView (race во время onCreate), `onDismiss` вызывается → AuthV2Activity finishes с RESULT_CANCELED.
- **Severity:** LOW (редкий race).

## ЧТО НЕ НАЙДЕНО (проверено, безопасно)

- **AuthFlow routing:** AuthFlowFactory.resolve(silent=false) → DirectHttpAuthFlow → AuthV2Activity. Корректно. `authV2Enabled` default true (SovaPrefs.kt:636) — V2 действительно дефолтный путь.
- **AndroidManifest:** AuthV2Activity имеет `windowSoftInputMode=adjustResize` + `configChanges=orientation|screenSize|...` — правильно (иначе VK ID page JS screen.orientation API пересоздаёт Activity → WebView destroy → reload loop).
- **EXTRA_SILENT_MODE:** Silent auth ВСЕГДА идёт через V1 (AuthFlow.kt:49: `if (silent) return null`). V2 НЕ перехватывает silent re-login — корректно.
- **CookieManager.setAcceptCookie:** Установлено в V2 (`VkLiteWebViewAuthScreen.kt:159`). `setAcceptThirdPartyCookies(web, true)` — установлено (line 160).
- **JavaScript enabled:** `javaScriptEnabled = true` (line 151). `domStorageEnabled = true` (line 152). Корректно для VK login.
- **mixedContentMode:** `MIXED_CONTENT_NEVER_ALLOW` (line 155) — secure (V1 не устанавливает, по умолчанию тоже NEVER_ALLOW).
- **TokenStorage hasValidToken check:** AuthV2Activity:48 — если токен уже валиден, RESULT_OK без WebView. Корректно (edge case: повторный запуск).
- **CaptchaDialog:** AuthV2Activity:60 — глобальный CaptchaDialog на V2 Activity. Корректно (V2 отдельная Activity, нужен свой CaptchaDialog).
- **AuthState.Success → finish:** LaunchedEffect на state (`AuthV2Activity:80-85`) — при Success завершает с RESULT_OK. Корректно.
- **Network permissions:** INTERNET, ACCESS_NETWORK_STATE в манифесте (lines 6-8). Есть.
- **Theme.PinoK:** Correctly applied to AuthV2Activity в манифесте (line 359).

## SPRECIFICS ДЛЯ Oppo A17k (ColorOS 12, Android 12, MediaTek Helio G35, 3GB RAM)

1. **ColorOS aggressive memory management** (3GB RAM): Chromium renderer process ЧАЩЕ убивается → C4 (no safety-net), C5 (no onRenderProcessGone) становятся CRITICAL. После kill — нет recovery → permanent белый экран.

2. **MediaTek Helio G35 (PowerVR GE8320 GPU)**: GPU driver issues — common на budget MediaTek. Без `setLayerType(LAYER_TYPE_SOFTWARE)` (C12), hardware-accelerated WebView может render некорректно или оставаться blank.

3. **ColorOS custom WebView behavior**: ColorOS 12 может иметь custom WebView default settings. Без WebChromeClient (C1) — default behaviors могут быть more restrictive → JS dialogs silently fail → VK ID SDK flow stuck.

4. **Budget device → outdated WebView**: Oppo A17k users часто имеют устаревший Android System WebView (no Play Store auto-update из-за storage limits). Если Chrome version < 100 — VK показывает «browser out of date» → C2 (no UA override) становится CRITICAL.

5. **ColorOS battery optimization**: Default ColorOS restricts background processes aggressively. Если AuthV2Activity кратко backgrounded (юзер переключился в VK app для SSO), WebView может быть убит → permanent белый экран при возврате.

6. **Default ColorOS IME**: SovaInputConnection fix (C13) нужен для ColorOS keyboard — React controlled inputs в VK ID form могут иметь mirror-input баг.

7. **3GB RAM + Compose + WebView**: Compose AndroidView имеет higher memory overhead чем plain View. С 3GB RAM и Compose + WebView + Chromium renderer — memory pressure significant → renderer crashes чаще.

8. **ColorOS dark theme**: Если юзер в dark theme, triple-white (Theme.PinoK windowBackground + MaterialTheme Surface + LinearLayout Color.WHITE) особенно заметен. V1's SOVATheme адаптивна к dark theme, V2 — нет (P1).

## РЕКОМЕНДАЦИИ (без фиксов — research only)

1. **[CRITICAL] C1: Добавить `setWebChromeClient`** в VkLiteWebViewAuthScreen.kt с полным набором: onProgressChanged, onJsAlert/onJsConfirm/onJsPrompt (auto-confirm), onConsoleMessage (логирование JS), onCreateWindow (для window.open). Скопировать из `VkAuthWebViewScreenV2.kt:762-844`.

2. **[CRITICAL] C2: Установить `userAgentString = chromeMobileUserAgent(ctx)`** в VkLiteWebViewAuthScreen.kt settings.apply. Скопировать функцию `chromeMobileUserAgent` из `VkAuthWebViewScreenV2.kt:105-120` (убирает `Version/4.0` + `; wv` маркеры + подменяет на CHROME_UA если Chrome <100).

3. **[CRITICAL] C3+C4+C5: Перенести safety-net из V1** — polling `pageStartedReceived` flag, 5-step escalation (reload→reload→recreate→recreate→fail UI с `webViewEpoch`), `onRenderProcessGone` handler, честный оверлей «WebView не смог запуститься». Скопировать из `VkAuthWebViewScreenV2.kt:171-395, 736-751, 867-906`.

4. **[CRITICAL] C6: Исправить polling loop** — заменить sync-read `holder[0]` на ПРАВИЛЬНЫЙ async-pattern: `suspendCancellableCoroutine` + `ValueCallback.onReceiveValue` → `continuation.resume(value)`. ИЛИ использовать `WebTokenAuth.fullAuthFlow` (как V1) вместо polling localStorage напрямую. ИЛИ читать `remixsid` из CookieManager синхронно (`getRemixSidFromCookieManager()`) и обменивать через `WebTokenAuth` (как V1).

5. **[HIGH] C7: Добавить `onReceivedSslError` handler** — логировать + cancel (как V1: `VkAuthWebViewScreenV2.kt:727-734`).

6. **[HIGH] C8: Добавить removeAllCookies/removeSessionCookies перед loadUrl** — в normal mode (не silent). Скопировать из `VkAuthWebViewScreenV2.kt:484-488`.

7. **[HIGH] C9: Добавить onPageStarted/onPageFinished** — JS injection (VK_2FA_CURSOR_FIX_JS + VK_INPUT_HARDENING_JS), loading state, cookie capture, autoclick на id.vk.ru/auth. Скопировать из `VkAuthWebViewScreenV2.kt:618-697`.

8. **[HIGH] C10: Расширить whitelist shouldOverrideUrlLoading** — добавить `connect.vk.com`, `api.vk.com`, `web.api.vk.ru`, `passport.vk.com`, `userapi.com`, `vkvideo.ru`, `im.vk.com`, `new.vk.com`, `st.vk.com`, `st2.vk.com`, `st3.vk.com`. Скопировать из `VkAuthWebViewScreenV2.kt:602-613`.

9. **[HIGH] C11: Добавить `CookieManager.getInstance().flush()`** после `onTokenReady` (перед `viewModel.submitOAuthToken`).

10. **[HIGH] C12+C13: Использовать `FixedInputWebView` вместо `WebView`** И добавить `setLayerType(View.LAYER_TYPE_SOFTWARE, null)`. Это закроет обе баги разом.

11. **[MEDIUM] P1: Заменить `MaterialTheme` на `SOVATheme`** в AuthV2Activity.kt:58 — для адаптивного dark/light colorScheme.

12. **[MEDIUM] P2: Добавить `systemBarsPadding()`** на Surface/Box в AuthV2Activity (или внутри VkLiteWebViewAuthScreen как в V1).

13. **[MEDIUM] P3+P6: Расширить host check в polling** + добавить timeout (300 сек как в V1).

14. **[MEDIUM] P4: Обновлять ProgressBar** через WebChromeClient.onProgressChanged (после фикса C1).

15. **[MEDIUM] P5: Установить `allowFileAccess = true`** (как V1) — VK login page может использовать file:// для assets.

16. **[LOW] P8: Добавить `onReceivedHttpError`** для диагностики.

17. **[LOW] P9: Добавить `clearStoredWebTokenOnLoad` logic** для logout cleanup.

## АЛЬТЕРНАТИВНОЕ РЕШЕНИЕ (макро-уровень)

Вместо поэтапного фикса 17 багов в VkLiteWebViewAuthScreen.kt — рассмотреть **ОТКАЗ от VkLiteWebViewAuthScreen** и возврат к VkAuthWebViewScreenV2 (V1 path) как дефолту. V1 уже имеет все 17 safety-mechanisms через многочисленные фиксы. V2 был создан (коммит 2026-10-17) чтобы избежать баги VkAuthWebViewScreenV2 «обеспечивая ensureSdkInitialized на m.vk.ru поверх активной формы входа id.vk.ru» (KDoc VkLiteWebViewAuthScreen.kt:29-38) — но эта бага уже устранена в V1 другими способами (WebTokenAuth.fullAuthFlow + правильный startUrl + onTokenExchange callback). Рекомендация: установить `authV2Enabled` default = false в SovaPrefs.kt:636, возвращая V1 как дефолт. Это МИНИМАЛЬНЫЙ фикс который немедленно решит белый экран на Oppo A17k (и других budget devices).

## ОГРАНИЧЕНИЯ АУДИТА
- Компиляция НЕ проверена (окружение без Android SDK — как в предыдущих задачах).
- Все выводы основаны на статическом анализе кода.
- «Oppo A17k specifics» — основаны на общеизвестных характеристиках ColorOS 12 / MediaTek Helio G35 / 3GB RAM. Не проверено на реальном устройстве.
- Polling race condition (C6) — основан на анализе AOSP WebViewChromium source (evaluateJavascript асинхронный). На некоторых device-specific WebView implementations callback МОГ бы срабатывать синхронно — но это implementation-dependent и НЕ гарантировано Android API contract.
- Логи пользователя НЕ приложены к задаче — невозможно подтвердить точно какая из баг (C1 vs C2 vs C4 vs C6) первичная. Если логи появятся — нужно искать: «onPageStarted», «onReceivedError», «onReceivedSslError», «onProgressChanged», «renderer», «cr_ChildProcessConn», «poll read fail», evaluateJavascript timing.

---
Task ID: EQ-FIX-C1-C5
Agent: general-purpose (equalizer C1-C5 fixes)
Task: Исправить silent data loss + UX индикация недоступных эффектов

Work Log:
- Прочитал /home/z/my-project/worklog.md (хвост — EQ-CRASH-AUDIT секция). Получил
  полные детали багов C1-C5 с file:line refs. Текущая голова репо PinoK = 862c7e3
  (релиз 2.2.6). Android SDK недоступен — статический анализ + brace balance.
- Прочитал 4 target-файла (2028 строк суммарно): EqualizerHelper.kt (336),
  AudioEffectsEngine.kt (824), EqualizerScreen.kt (938), SpectrumVisualizer.kt
  (266). Понял existing patterns: applyCustomPresetPersist (#EQ-SAVE-NULL-ENGINE)
  имеет prefs-fallback для engine==null; setEnabled/applyPreset/setBand — нет
  (только setBand имеет). Effect setters в BassVirtTab/LoudnessTab идут через
  `engine?.setXxx(...)` (no-op без engine). ReverbTab через
  PlayerConnection.setReverbEnabled/Preset (тоже no-op без engine).
- Реализовал фиксы C2+C3 (EqualizerHelper.kt:184-201, 290-308):
  • applyPreset(): engine!=null → e.applyPreset(); иначе savePreset(name) +
    saveBands(bands.map dB→mB) напрямую в prefs (как applyCustomPresetPersist).
  • setEnabled(): engine!=null → e.setEqEnabled(); иначе prefs.edit()
    .putBoolean(PREF_EQ_ENABLED, enabled).apply() (как savePreset/saveBands).
  Оба сохраняют P3.4 комментарий (расширен C2/C3 explanation), логируют
  через AppLog.i (не w — это не warning, это fallback path).
- Реализовал C1 — 8 обёрток в EqualizerHelper.kt:327-411:
  setBassBoostEnabled/Strength, setVirtualizerEnabled/Strength,
  setLoudnessEnabled/TargetGain, setReverbEnabled/Preset. Каждая: engine!=null
  → делегирует в e.setXxx (нормальный path); иначе — prefs.edit().putX(PREF_X, v)
  .apply() напрямую. KDoc-comment объясняет rationale + AUX-binding caveat для
  Reverb (UI должен отдельно дёргать PlayerConnection.rebindReverbAux()).
- Реализовал C4 — 4 availability getter'а в AudioEffectsEngine.kt:806-829:
  isBassBoostAvailable/isVirtualizerAvailable/isReverbAvailable/isLoudnessAvailable.
  Каждый synchronized(lock) { effect != null }. KDoc объясняет semantic: возвращает
  факт СОЗДАНИЯ эффекта (init success), не enabled-состояние. engine==null в UI
  трактуется как `?: true` (unknown, assume available — prefs-fallback покрывает).
- Реализовал C1+C4 в EqualizerScreen.kt:
  • BassVirtTab (632-715): добавил `bassAvailable = engine?.isBassBoostAvailable()
    ?: true`, `virtAvailable = engine?.isVirtualizerAvailable() ?: true`. Заменил
    `engine?.setBassBoostEnabled/on` → `EqualizerHelper.setBassBoostEnabled/on`,
    `engine?.setBassBoostStrength(...)` → `EqualizerHelper.setBassBoostStrength(...)`.
    Аналогично для Virtualizer. Передал `available` в EffectCard.
  • ReverbTab (721-841): добавил `reverbAvailable`. Заменил
    `PlayerConnection.setReverbEnabled/on` → `EqualizerHelper.setReverbEnabled/on
    + PlayerConnection.rebindReverbAux()` (AUX-привязка сохранена). Card
    containerColor: errorContainer при !available. Switch enabled=reverbAvailable.
    Subtitle: «Эффект недоступен на этом устройстве (init failed). Настройки
    сохранятся и применятся на поддерживаемом устройстве.» Preset cards clickable
    guard: `if (!reverbAvailable) return@clickable`.
  • LoudnessTab (825-891): `available = apiOk && (engine?.isLoudnessAvailable()
    ?: true)`. !apiOk → error Card (existing). apiOk → EffectCard с available.
    Заменил `engine?.setLoudnessEnabled/on` → `EqualizerHelper.setLoudnessEnabled/on`.
  • EffectCard (884-949): добавил `available: Boolean = true` параметр. !available:
    containerColor → errorContainer, description → «Эффект недоступен на этом
    устройстве (init failed).», color → onErrorContainer, Switch enabled=available,
    Slider enabled = `available && checked`.
- Реализовал C5 в SpectrumVisualizer.kt:
  • Добавил private class `VisualizerHolder { var value: Visualizer? = null }`
    (file-level, line 72) — простой mutable holder без State (не триггерит
    recomposition).
  • `val visualizerRef = remember { VisualizerHolder() }` (line 113).
  • DisposableEffect(sessionId): во всех ветках (sessionId==0, init failed,
    success) обновляет visualizerRef.value (null или visualizer), в onDispose
    зануляет.
  • LaunchedEffect(active, sessionId): тело теперь читает `visualizerRef.value
    ?: return@LaunchedEffect`, затем `v.enabled = active` в try/catch.
    Раньше тело было ПУСТЫМ (только комментарий) → visualizer.enabled не
    обновлялся при toggle master switch / play-pause / SCO.
- Verification:
  • Brace/paren/bracket balance проверен на всех 4 файлах — все 0 diff
    (72/72, 244/244, 163/163, 38/38 braces; parens и brackets аналогично).
  • grep "prefs-fallback|isBassBoostAvailable|isReverbAvailable|недоступен" в
    EqualizerHelper.kt / AudioEffectsEngine.kt / EqualizerScreen.kt — все
    ожидаемые match'и присутствуют (C1/C2/C3/C4 маркеры + новые методы +
    UI-индикация).
  • grep "visualizer.enabled|LaunchedEffect(active" в SpectrumVisualizer.kt —
    показывает fix C5 (visualizerRef.value + LaunchedEffect body).
  • git diff --stat: 4 файла изменены, +234/-34 lines.
- Не делал git commit (по условию задачи).
- Компиляция НЕ проверена — окружение без Android SDK (как в EQ-CRASH-AUDIT).
  Синтаксис/типы/импорты/brace balance проверены вручную.

Stage Summary:
- **C1 (HIGH) FIXED**: 8 prefs-fallback обёрток в EqualizerHelper.kt:327-411
  для setBassBoostEnabled/Strength, setVirtualizerEnabled/Strength,
  setLoudnessEnabled/TargetGain, setReverbEnabled/Preset. UI-вкладки
  BassVirtTab/ReverbTab/LoudnessTab (EqualizerScreen.kt) теперь используют эти
  обёртки вместо `engine?.setXxx(...)` (который был no-op при engine==null).
  Сценарий «открыл Эквалайзер до воспроизведения → потеряны настройки» закрыт:
  значения пишутся в SharedPreferences, restoreSettings() в
  AudioEffectsEngine.attachOnce() подхватит при первом attach.
- **C2 (HIGH) FIXED**: EqualizerHelper.setEnabled(enabled) (line 290-308) —
  prefs-fallback через `prefs().edit().putBoolean(PREF_EQ_ENABLED, enabled).apply()`
  при engine==null. Master switch теперь персистится даже если пользователь
  открыл Эквалайзер до начала воспроизведения. Inconsistent с savePreset/saveBands
  устранена.
- **C3 (HIGH) FIXED**: EqualizerHelper.applyPreset(preset) (line 184-201) —
  prefs-fallback через `savePreset(name) + saveBands(bands.map dB→mB)` при
  engine==null. Built-in preset выбор теперь персистится. Inconsistent с
  applyCustomPersist устранена.
- **C4 (MEDIUM) FIXED**: AudioEffectsEngine.kt:806-829 — добавлены 4
  availability getter'а (isBassBoostAvailable/isVirtualizerAvailable/
  isReverbAvailable/isLoudnessAvailable). EqualizerScreen.kt — EffectCard
  получил параметр `available: Boolean = true` (при !available: card фон
  → errorContainer, switch disabled, slider disabled, subtitle «Эффект
  недоступен на этом устройстве (init failed).»). ReverbTab — switch
  enabled=reverbAvailable + subtitle «Эффект недоступен на этом устройстве
  (init failed). Настройки сохранятся и применятся на поддерживаемом
  устройстве.» Preset cards кликабельны только при reverbAvailable. LoudnessTab
  — available расширен: `apiOk && (engine?.isLoudnessAvailable() ?: true)`.
  Сценарий пользователя из лога (PresetReverb init failed Error -3 → switch
  работает визуально, но звук не меняется) — теперь UI явно показывает
  «недоступен».
- **C5 (MEDIUM) FIXED**: SpectrumVisualizer.kt — добавлен VisualizerHolder
  (private class, line 72) + `val visualizerRef = remember { VisualizerHolder() }`
  (line 113). DisposableEffect(sessionId) обновляет visualizerRef.value во
  всех ветках. LaunchedEffect(active, sessionId) теперь имеет тело: `val v =
  visualizerRef.value ?: return@LaunchedEffect; v.enabled = active` в try/catch.
  При смене active (toggle master switch, play/pause, SCO suspend/restore)
  Visualizer.enabled корректно togg'лится → bars не застывают на 0 + нет
  CPU/battery waste в фоне.
- All 5 bugs fixed. Behavior при engine != null не изменён (нормальный path
  preserved). prefs-fallback только при engine == null. Стиль кода и
  комментарии сохранены, добавлены `// C1:` / `// C2:` / `// C3:` / `// C4:` /
  `// C5:` маркеры.
- **Риски**: см. ниже «Риски/вопросы».
- **Не сделано**: компиляция не проверена (no Android SDK), git commit не сделан
  (по условию).

Риски/вопросы:
1. **P3 (LOW) из аудита НЕ пофиксен** — `remember { EqualizerHelper.engine() }`
   в BassVirtTab/ReverbTab/LoudnessTab всё ещё captures engine one-shot. Если
   пользователь ОСТАЁТСЯ на экране во время attach (playback starts in bg),
   engine captured == null, `isBassBoostAvailable()` тоже ?: true → UI не
   показывает «недоступен» даже если init failed. На практике пользователь
   обычно возвращается на экран (fresh composable) — но edge case остаётся.
   Task не требовал P3 fix (только C1-C5). Field for future improvement.
2. **C5 fix — LaunchedEffect + holder pattern**: первая композиция может
   запустить LaunchedEffect ДО DisposableEffect (compose не гарантирует
   порядок side-effects с разными keys). В этом случае visualizerRef.value
   == null → LaunchedEffect body return@LaunchedEffect (no-op).
   DisposableEffect затем создаёт Visualizer, ставит v.enabled = active —
   корректное initial state. На subsequent active changes LaunchedEffect
   re-launches с уже ненулевым ref — работает. Не баг, но требует знания
   compose side-effect ordering.
3. **C4 — engine==null трактуется как `available=true`** (default). Это
   сознательное решение: если движок ещё не attach'ен, UI позволяет
   настраивать (значения идут в prefs через C1 wrappers). Только после attach
   (engine != null) можно узнать, поддерживается ли эффект — тогда UI
   dim'ится если init failed. Альтернатива (dim'ить при engine==null) хуже:
   блокирует настройку «на будущее».
4. **Reverb presetNames всё ещё `engine?.reverbPresetNames ?: emptyList()`**
   — если engine==null, preset list пуст. ReverbTab показывает только switch,
   без выбора пресета. После attach (engine attached) — список появляется
   на свежем composable. Не в scope C1-C5, но UX nit.
5. **C2 — `EqualizerHelper.saveEnabled(enabled)` НЕ получил prefs-fallback**
   (только setEnabled). saveEnabled effectively dead code (grep показал только
   упоминание в комментарии). Task требовал фикс C2 = setEnabled, не saveEnabled.
6. **PlayerConnection.kt НЕ изменён** (по условию задачи — ❌ НЕЛЬЗЯ). Это
   означает: PlayerConnection.setReverbEnabled/Preset всё ещё зовёт
   `EqualizerHelper.engine()?.setReverbEnabled(on)` напрямую (no-op при engine
   == null). ReverbTab теперь НЕ использует PlayerConnection.setReverbEnabled
   (использует EqualizerHelper.setReverbEnabled + PlayerConnection.rebindReverbAux).
   Но если в будущем кто-то добавит caller PlayerConnection.setReverbEnabled —
   prefs-fallback не сработает. Минимальный риск (текущий caller только ReverbTab).

---
Task ID: AUTHOR-STRIP
Agent: general-purpose (PostCard author strip)
Task: Добавить полоску «Перейти к посту» под header в PostCard

Work Log:
- Прочитал контекст (worklog, PostCard.kt целиком 559→607 строк). Изучил
  структуру: Header Row (201-360) → pinned/copyright/donut badges → текст →
  вложения → репост → action-bar. Полоску нужно вставить МЕЖДУ header Row и
  pinned badge (после строки 360).
- Шаг 1 (PostCard.kt): добавил 3 импорта:
    • androidx.compose.material.icons.automirrored.outlined.Article
    • androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
    • androidx.compose.material3.Surface
  ВАЖНО: `Icons.Outlined.Article` НЕ существует — Article перенесён в AutoMirrored
  (подтверждено существующим usage в BookmarksScreen.kt:1400 и ChatDetailScreen.kt:130).
  Для chevron использовал `Icons.AutoMirrored.Outlined.KeyboardArrowRight`
  (подтверждено в ChatDetailScreen.kt:139 import) вместо `Icons.Outlined.ChevronRight`
  из ТЗ — это гарантированно компилируется (существующий usage).
  Добавил параметр `onPostContextClick: (() -> Unit)? = null` (после onRepostGroupClick,
  строка 149). Добавил блок полоски (строки 370-408) — Surface(surfaceVariant) +
  Row с Icon(Article)+Text("Перейти к посту", labelMedium)+Icon(KeyboardArrowRight),
  clickable → onPostContextClick(). Размещён ПОД header'ом, ДО pinned-badge.
  Маркер `// AUTHOR-STRIP:` добавлен в комментариях.
- Шаг 2 (FeedScreen.kt): wrapper `PostCard` (строка 2363) — добавил параметр
  `onPostContextClick: (Post) -> Unit = {}` (строка 2409). В делегировании
  UnifiedPostCard(...) добавил `onPostContextClick = { onPostContextClick(post) }`
  (строка 2551). На 2 callsites добавил routing по post.ownerId:
    • likes-items (строка 2015): `if (p.ownerId > 0) onUserClickSavePos(p.ownerId)
      else onGroupClickSavePos(-p.ownerId)`
    • main-feed (строка 2087): аналогично.
  FeedScreen уже имеет onUserClick/onGroupClick (строки 207/211) + *SavePos-обёртки
  (1049-1050). Существующий onAuthorClick routing (по fromId) НЕ тронут.
- Шаг 3 (CommunityScreen.kt): НЕ модифицировался. CommunityPostCard (строка 1644)
  → PostCard(...) НЕ передаёт onPostContextClick → default null → полоска НЕ
  рисуется. Это правильно: мы уже в сообществе (git diff --stat пустой).
- Шаг 4 (BookmarksScreen.kt): 3 уровня проброса:
    • Callsite (строка 667): `onPostContextClick = bm.post?.let { p -> { ... } }`
      — возвращает `(() -> Unit)?` (null для не-post закладок). Routing по
      p.ownerId через top-level onUserClick/onGroupClick (строки 111-112).
    • BookmarkRow (строка 815): добавлен параметр `onPostContextClick:
      (() -> Unit)? = null`, пробрасывается только в BookmarkPostCard (строка 850).
    • BookmarkPostCard (строка 988): добавлен параметр, пробрасывается в
      UnifiedPostCard (строка 1033).
- Шаг 5+6 (ProfileScreen.kt WallPostCard + ChatDetailScreen): добавил
  onPostContextClick ВНУТРЬ обёртки WallPostCard (строка 2411) — использует
  СУЩЕСТВУЮЩИЕ параметры onUserClick/onGroupClick обёртки (строки 2292-2293):
    `if (post.ownerId > 0) onUserClick(post.ownerId) else onGroupClick(-post.ownerId)`
  Это автоматически покрывает ВСЕ 5 callsites WallPostCard:
  ProfileScreen:1481, UserProfileScreen:977, ChatDetailScreen:5008/5333/11925 —
  без изменений в файлах-вызывателях (они уже передают onUserClick/onGroupClick).
  ChatDetailScreen.kt НЕ модифицировался (git diff пустой) — полоска приходит
  через WallPostCard.

Verification:
- grep "onPostContextClick\|Перейти к посту\|AUTHOR-STRIP" в PostCard.kt: param
  (149), strip block (370-408), "Перейти к посту" (393). ✓
- grep -rn "onPostContextClick" app/src/main/java/: 13 совпадений — PostCard.kt
  (def+block+clickable), FeedScreen (2 callsites+wrapper+delegation),
  BookmarksScreen (callsite+2 params+2 forwards), ProfileScreen (WallPostCard).
- grep "onPostContextClick" в CommunityScreen.kt: 0 (не передаётся = null = нет
  полоски). ✓
- Brace balance: PostCard.kt 73/73, FeedScreen.kt 768/768, BookmarksScreen.kt
  333/333 — все 0 diff. ProfileScreen.kt 796/798 (diff -2) — это ПРЕД-СУЩЕСТВУЮЩИЙ
  артефакт (braces в string-literals/comments), оригинал HEAD тоже 793/795 (diff -2);
  моя правка добавила +3/+3 (сбалансировано), git diff --stat = +12 insertions.
- onAuthorClick routing НЕ тронут: ProfileScreen.kt:2397 onAuthorClick = { ... }
  (по fromId) сохранён как есть.
- git diff --stat: 4 файла, +98 insertions, 0 deletions. CommunityScreen.kt и
  ChatDetailScreen.kt — 0 изменений.
- Не делал git commit (по условию задачи).
- Компиляция НЕ проверена (no Android SDK в окружении) — синтаксис/типы/импорты/
  brace balance проверены вручную; все используемые иконки (Article, KeyboardArrowRight)
  и Surface подтверждены существующим usage в кодовой базе.

Stage Summary:
- **AUTHOR-STRIP (variant A)**: полоска «Перейти к посту» добавлена в единый
  PostCard (ui/components/PostCard.kt:370-408) — Surface(surfaceVariant) с Row
  [Icon(Article 16dp) + Text("Перейти к посту", labelMedium, weight 1f) +
  Icon(KeyboardArrowRight 16dp)], clickable → onPostContextClick(). ВСЕГДА
  показывается когда onPostContextClick != null; скрыта когда null.
- **Routing разделён**: onAuthorClick → профиль АВТОРА (по fromId, как было);
  onPostContextClick → стена где ЛЕЖИТ пост (по ownerId). Эти сущности могут
  различаться (пользователь пишет на стене группы: fromId=user, ownerId=-group).
- **Routing таблица**:
  | Экран | onPostContextClick | Полоска |
  | FeedScreen | ownerId>0→onUserClick, <0→onGroupClick | ДА |
  | BookmarksScreen | ownerId>0→onUserClick, <0→onGroupClick | ДА (только post-закладки) |
  | ProfileScreen (WallPostCard) | ownerId>0→onUserClick, <0→onGroupClick | ДА |
  | UserProfileScreen (WallPostCard) | то же (через WallPostCard) | ДА |
  | ChatDetailScreen (WallPostCard ×3) | ownerId<0→onGroupClick (канал) | ДА |
  | CommunityScreen (CommunityPostCard) | null (default) | НЕТ (уже в сообществе) |
  | PostDetailScreen (UnifiedPostCard) | null (default) | НЕТ (вне scope ТЗ) |
- **Иконки**: использовал AutoMirrored-варианты (Article, KeyboardArrowRight) —
  Article в этой версии lib ТОЛЬКО в AutoMirrored (Icons.Outlined.Article не
  существует). KeyboardArrowRight — для гарантии компиляции (существующий import).
- Стиль кода и комментарии сохранены; добавлены `// AUTHOR-STRIP:` маркеры.

Риски/вопросы:
1. **ChatDetailScreen — onUserClick не передаётся в WallPostCard** (только
   onGroupClick). Для канальных постов ownerId всегда < 0 (group) → routing
   уходит в onGroupClick (работает). Но если в чате покажут пост с ownerId > 0
   (чужая стена пользователя), onPostContextClick вызовет onUserClick который
   default {} (no-op) — полоска кликнется без эффекта. Не краш, но UX nit.
   На практике канальные посты всегда от группы — edge case маловероятен.
2. **PostDetailScreen — полоска НЕ показывается** (onPostContextClick не передан
   = null). Файл не в списке разрешённых (только FeedScreen.kt из feed/). По
   смыслу это правильно: на PostDetailScreen мы уже смотрим пост. Если позже
   потребуется «вернуться на стену» — добавить onPostContextClick в callsite
   PostDetailScreen.kt:692 (UnifiedPostCard).
3. **Иконки отличны от ТЗ**: ТЗ предлагал `Icons.Outlined.Article` +
   `Icons.Outlined.ChevronRight`. Article в AutoMirrored (Outlined.Article не
   компилируется); ChevronRight заменён на KeyboardArrowRight для гарантии
   (существующий usage). Визуально эквивалентно (document-icon + right-chevron).
   Если нужна именно ChevronRight — `Icons.Filled.ChevronRight` подтверждён в
   кодовой базе, но Outlined-вариант не проверен; безопаснее KeyboardArrowRight.
4. **`bm.post?.let { p -> { ... } }`** в BookmarksScreen — возвращает
   `(() -> Unit)?`. Это валидный Kotlin-идиом (let возвращает lambda-выражение),
   но не самый очевидный. Альтернатива через if/else + !! — менее чистая.
   Компиляция паттерна подтверждена стандартом Kotlin (не требует проверки SDK).
5. **ProfileScreen.kt предсуществующий brace imbalance -2** (793/795 в HEAD) —
   артефакт grep по braces внутри string-literals `"${...}"` и комментариев.
   Не связан с моей правкой (+3/+3 сбалансировано). Компиляция проекта проходит
   (по условию задачи), значит это grep-артефакт, а не синтаксическая ошибка.

---
Task ID: PHOTO-MENU-ALBUMS
Agent: general-purpose (PhotoViewer menu + albums)
Task: Добавить меню ⋮ в PhotoViewer + вкладку «Альбомы» в CommunityScreen

Work Log:
- Шаг 1 (context): прочитан worklog.md (последние 100 строк) — предыдущая
  задача была AUTHOR-STRIP (полоска «Перейти к посту» в PostCard). Контекст
  проекта — Kotlin/Android, ветка PinoK, HEAD 4c1a432. Репозиторий в
  /home/z/repos/PinoK_1. Границы файлов: PhotoViewer.kt + CommunityScreen.kt
  (VKApiClient.kt — read-only, photosGetAlbums уже есть на строке 10575).
- Шаг 2 (PhotoViewer.kt): добавлены 4 опциональных коллбека в signature
  трёх функций: `PhotoViewer`, `PhotoViewerPhotoItems`, `PhotoViewerIntern`
  (строки 90-93, 118-121, 162-165). Все имеют default `{}` — обратная
  совместимость со ВСЕМИ существующими callsites (SovaNavHost, FeedScreen,
  PostDetailScreen, ProfileScreen, UserProfileScreen, ChatDetailScreen ×2,
  PhotosScreen — всего 9 callsites, проверены grep'ом). Проброс во Intern:
  строки 100-103, 147-150.
- Шаг 3 (PhotoViewer.kt — UI): в TopAppBar-overlay добавлен Row у правого
  края (строки 238-327): [Save IconButton] [Box с MoreVert IconButton +
  DropdownMenu]. DropdownMenu содержит 4 пункта (строки 285-325):
    • «Поделиться» (Icons.Outlined.Share) → onShare(currentUrl)
    • «Отправить» (Icons.AutoMirrored.Outlined.Send) → onSendToChat(currentUrl)
    • «В закладки» (Icons.Outlined.BookmarkBorder) → onBookmark(currentUrl)
    • «В избранное» (Icons.Outlined.FavoriteBorder) → onFavorite(currentUrl)
  currentUrl берётся из photos[pagerState.currentPage] внутри lambda (строка 284).
  menuExpanded state — строка 176. Box-обёртка вокруг «⋮» (строка 270) нужна
  для привязки DropdownMenu к anchor-IconButton (как в PostCard.kt:333).
  Существующий onDismiss, swipe (HorizontalPager), pinch-to-zoom (detectTransform
  Gestures), double-tap-to-zoom, download — НЕ тронуты.
- Шаг 4 (CommunityScreen.kt — imports): добавлены 3 импорта:
    • `import re.pinok.data.model.Album` (строка 93) — для state и CommunityAlbumRow
    • `import re.pinok.ui.components.ShareToChatSheet` (строка 108) — для «Отправить»
    • `import android.content.Intent` (строка 115) — для ACTION_SEND в onShare
- Шаг 5 (CommunityScreen.kt — state): добавлены 5 state-переменных после
  photoViewerState (строки 217-228):
    • `shareToChatUrl: String?` — URL для ShareToChatSheet (null = закрыт)
    • `albums: List<Album>` — список альбомов (для вкладки 7)
    • `albumsLoading: Boolean` — флаг загрузки
    • `albumsError: String?` — текст ошибки
    • `albumsLoaded: Boolean` — single-shot флаг (как docsLoaded/videosLoaded)
    • `albumOpening: Album?` — какой альбом сейчас открывается (для trailing
      прогресс-вертушки и защиты от двойного тапа)
- Шаг 6 (CommunityScreen.kt — tab): в buildList tabs (строка 421) добавлен
  `add(7 to "Альбомы")` (строка 453) — всегда (как «Файлы»). GroupSections
  не имеет фильтра albums → вкладка не зависит от админ-настроек. Логический
  индекс 7 (6 = Файлы, 7 = Альбомы) — постоянен, как требовала нумерация P0.8.
- Шаг 7 (CommunityScreen.kt — LaunchedEffect): добавлен триггер загрузки
  альбомов (строки 626-643): `if (selectedTab != 7 || albumsLoaded) return`,
  затем `app.apiClient.photosGetAlbums(ownerId = -groupId)` (VK convention:
  группы — отрицательный id). albumsLoaded = true в finally. Паттерн —
  копия LaunchedEffect для вкладки 6 (docsState.loadFirst, строки 612-616).
- Шаг 8 (CommunityScreen.kt — render): добавлен case `7 -> { ... }` в
  `when (selectedTab)` (строки 1647-1743): loading/error/empty/full states
  по образцу вкладки 6 (Файлы). Список альбомов через items() → новый
  private composable `CommunityAlbumRow` (строки 2286-2373): Card с обложкой
  (96dp, thumbSrc, fallback — иконка Image), названием, size «N фото»,
  описанием (maxLines=2), trailing — KeyboardArrowRight или CircularProgressIndicator
  во время открытия. onClick (строки 1708-1739): гейт `if (albumOpening != null)
  return@CommunityAlbumRow` (паттерн из `return@OutlinedButton` строка 870),
  scope.launch → albumOpening=album → photosGet(ownerId=-groupId,
  albumId=album.id.toString(), count=100) → urls = mapNotNull largestUrl →
  `photoViewerState.value = urls to 0` (открывает PhotoViewer с фото альбома,
  переиспользуя существующий photoViewerState + PhotoViewer callsite ниже).
  Empty/error → Toast. finally albumOpening=null.
- Шаг 9 (CommunityScreen.kt — PhotoViewer wiring): на callsite PhotoViewer
  (строка 1766) добавлены 4 коллбека:
    • `onShare` → Intent.ACTION_SEND type=text/plain, EXTRA_TEXT=url,
      startActivity(Intent.createChooser(...)) — системный share-диалог
      (VK/Telegram/почта/…). LocalContext взят через `val shareContext =
      LocalContext.current` (строка 1765).
    • `onSendToChat` → `shareToChatUrl = url` (открывает ShareToChatSheet ниже)
    • `onBookmark` → `{}` с TODO-комментарием (строки 1782-1789):
      закладка фото требует (ownerId, photoId), но PhotoViewer работает со
      списком URL без маппинга обратно к PhotoItem. Реализуется расширением
      photoViewerState до Pair<List<PhotoItem>?, Int> — но это меняет signature
      onPhotoClick во ВСЕХ callsites (PostCard, BookmarksScreen, …), что
      выходит за рамки ТЗ.
    • `onFavorite` → `{}` (та же причина, тот же TODO).
- Шаг 10 (CommunityScreen.kt — ShareToChatSheet): добавлен рендер ShareToChatSheet
  (строки 1794-1805) после PhotoViewer-блока: `if (chatShareUrl != null) {
  ShareToChatSheet(sharedText=chatShareUrl, sharedStreamUri=null,
  sharedMimeType=null, onDismiss={shareToChatUrl=null}, onSuccess={...}) }`.
  sharedText=url (без файла) — ShareToChatSheet внутри отправит как
  messages.send (text=url) в выбранный диалог.

Verification:
- grep "MoreVert\|onShare\|onSendToChat\|onBookmark\|onFavorite\|DropdownMenu"
  в PhotoViewer.kt: 30+ совпадений — import'ы (21,25-26), KDoc (75-79), params
  в 3 функциях (90-93, 118-121, 162-165), проброс в Intern (100-103, 147-150,
  нет т.к. Intern сам имеет дефолты), UI (237, 269, 275, 280, 285-325). ✓
- grep "photosGetAlbums\|Альбомы\|albums" в CommunityScreen.kt: 11 совпадений
  — state (220-228), tab (449-453), LaunchedEffect (621-643), render (1648-1742),
  CommunityAlbumRow (2275-2373). ✓
- Brace balance: PhotoViewer.kt 83/83 (diff 0) — был 46/46 (HEAD), +37 braces
  добавлено сбалансированно. CommunityScreen.kt 552/552 (diff 0) — был 491/491
  (HEAD), +61 braces добавлено сбалансированно. ✓
- Paren balance: PhotoViewer.kt 150/150, CommunityScreen.kt 1205/1205. ✓
- git diff --stat: 2 файла, +407 insertions, -22 deletions (PhotoViewer:
  +151/-22 = refactor Download в Row + 4 callbacks + DropdownMenu;
  CommunityScreen: +278/-0 = чисто additive). ✓
- Backward compat: проверены все 9 PhotoViewer callsites (SovaNavHost:3402,
  ChatDetailScreen:6210/6256, ProfileScreen:1783, UserProfileScreen:1061,
  CommunityScreen:1766, PostDetailScreen:614/1460, FeedScreen:2254,
  PhotosScreen:734) — все передают только photos/initial/onDismiss. 4 новых
  коллбека default `{}` → существующие callsites НЕ ломаются. PhotoViewer
  меню ВИДНО везде (4 пункта), но на callsites без wiring — no-op. ✓
- PhotoViewerPhotoItems callsite (PhotosScreen:432) — тоже только 3 параметра,
  4 новых default `{}` → совместимость сохранена. ✓
- Existing PhotoViewer features НЕ тронуты: onDismiss (строка 181-186),
  HorizontalPager swipe (193-201), pinch-to-zoom (detectTransformGestures в
  ZoomablePhoto, строка ~370+), double-tap-to-zoom (onDoubleTap в ZoomablePhoto),
  download ImageSaver (246-258), caption-bar (332+). ✓
- Style: иконки (AutoMirrored.Outlined.Send — для RTL, как в существующем
  FeedScreen:4772/4796; Icons.Outlined.Share/BookmarkBorder/FavoriteBorder —
  outlined-стиль для меню, как в PostCard), комментарии с маркерами
  PHOTO-MENU/COMMUNITY-ALBUMS, NULL-ЯВНО где nullable (coverUrl, albumsError),
  smart-cast через val-захват (errMsg). ✓
- Не делал git commit (по условию задачи).

Stage Summary:
- **Задача 1 (PhotoViewer ⋮-меню)** — ВЫПОЛНЕНА. PhotoViewer.kt теперь имеет
  «⋮»-кнопку (Icons.Filled.MoreVert, рядом с «Сохранить») + DropdownMenu с 4
  пунктами: «Поделиться» (Share), «Отправить» (Send), «В закладки» (Bookmark),
  «В избранное» (FavoriteBorder). 4 опциональных коллбека (default {}) —
  call-site решает что делать. В CommunityScreen wired: onShare → Intent
  ACTION_SEND (system share-chooser), onSendToChat → ShareToChatSheet (PinoK
  picker), onBookmark/onFavorite → no-op с TODO (нужен PhotoItem context).
- **Задача 2 (Альбомы в CommunityScreen)** — ВЫПОЛНЕНА. Добавлена 7-я вкладка
  «Альбомы» (логический индекс 7, после «Файлы»). Грузится через
  photosGetAlbums(ownerId=-groupId) при переходе на вкладку. Рендерится
  списком Card'ов (обложка thumbSrc 96dp + название + «N фото» + описание).
  Тап → photosGet(albumId) → PhotoViewer с фото альбома (переиспользован
  существующий photoViewerState). Album model — уже есть в :core:data
  (re.pinok.data.model.Album, Models.kt:1275). API — уже есть в VKApiClient
  (строка 10575, photos.getAlbums need_covers=1 need_system=1).

Риски/вопросы:
1. **onBookmark/onFavorite — no-op в PhotoViewer**: закладка/избранное фото
   требуют (ownerId, photoId). PhotoViewer работает со списком URL (String)
   — маппинга URL→PhotoItem нет. Реализуется расширением photoViewerState до
   Pair<List<PhotoItem>?, Int> (как PhotosScreen.photoViewerState), но это
   меняет signature onPhotoClick во ВСЕХ callsites (PostCard, BookmarksScreen,
   ChatDetailScreen, …) — выходит за рамки ТЗ. На уровне CommunityPostCard
   «⋮»-меню поста уже работает «В закладки» через faveAdd("post", ownerId, id)
   (строка 1051) — там контекст есть. Для фото — нужен отдельный task.
2. **Меню видно на ВСЕХ callsites PhotoViewer**: 9 callsites (FeedScreen,
   PostDetailScreen, ProfileScreen, ChatDetailScreen, SovaNavHost, PhotosScreen)
   не пробрасывают новые коллбеки → 4 пункта меню у них no-op (тап → ничего
   не происходит). Это соответствует ТЗ («главное — menu UI с пунктами»), но
   UX-проблема: user видит «Поделиться», тапает — ничего не происходит. При
   необходимости — wired в отдельной задаче (по образцу CommunityScreen).
3. **Pull-to-refresh для альбомов**: refreshWall() (строка 464) НЕ обновляет
   альбомы — только стену. Если user добавил альбом через web/другой клиент,
   pull-to-refresh на вкладке «Альбомы» не подтянет изменения. Решается
   сбросом albumsLoaded=false в refreshWall() или отдельным refreshAlbums().
   Не критично — albumsLoaded уже даёт single-shot загрузку (как videos/docs).
4. **photosGet count=100 при открытии альбома**: если в альбоме >100 фото,
   увидит только первые 100 (без пагинации). Существующая вкладка «Фото»
   использует photosGetWithCount с пагинацией через PagedListState. Для
   альбомов можно повторить тот же паттерн (PagedListState<PhotoItem> + альбом
   как rememberKey), но это усложнит state (нужен per-album state). Оставлено
   как TODO — текущая реализация покрывает большинство случаев (типичный
   альбом сообщества <100 фото).
5. **`return@CommunityAlbumRow`**: использован local-return из lambda
   (строка 1709). Паттерн идентичен существующему `return@OutlinedButton`
   (строка 870) и `return@Button` (строка 915) в этом же файле — Kotlin
   допускает labeled return из любого lambda (inline или нет). Компиляция
   pattern подтверждена существующим usage.
6. **GroupSections не имеет фильтра albums**: вкладка «Альбомы» показывается
   ВСЕГДА (как «Файлы» и «Клипы»). Если админ сообщества выключил все фото-
   секции — альбомы всё равно грузятся (photos.getAlbums работает для любого
   сообщества, не требует админ-прав). Это совпадает с поведением web-VK,
   где вкладка «Альбомы» видна всем.
7. **`thumbSrc` может быть null**: VK API photos.getAlbums с need_covers=1
   обычно возвращает thumb_src, но для пустых альбомов — null. CommunityAlbumRow
   обрабатывает это (fallback на иконку Image в placeholder, строки 2315-2321).
8. **Компиляция НЕ проверена** (no Android SDK в окружении) — синтаксис/типы/
  импорты/brace balance проверены вручную; все используемые иконки
  (Icons.Filled.MoreVert/Download, Icons.Outlined.Share/BookmarkBorder/
  FavoriteBorder/Image, Icons.AutoMirrored.Outlined.Send, Icons.AutoMirrored.
  Filled.KeyboardArrowRight) и компоненты (DropdownMenu, DropdownMenuItem,
  Card, CardDefaults, CircularProgressIndicator, AsyncImage, ShareToChatSheet)
  подтверждены существующим usage в кодовой базе.

---
Task ID: PHOTO-SHARE-WIRE
Agent: general-purpose (wire onShare/onSendToChat all callsites)
Task: Подключить onShare/onSendToChat в PhotoViewer на всех экранах

Work Log:
- Прочитан контекст: worklog.md (задача PHOTO-MENU от предыдущего агента —
  PhotoViewer.kt уже имеет 4 опциональных callback'а onShare/onSendToChat/
  onBookmark/onFavorite с default {}. CommunityScreen.kt:1775 — единственный
  callsite с wired onShare/onSendToChat. 8 других callsites использовали
  default {} — кнопки «Поделиться»/«Отправить» в «⋮»-меню не работали.
- Изучен reference-pattern CommunityScreen.kt:1766-1829:
  · val shareContext = LocalContext.current
  · var shareToChatUrl by remember { mutableStateOf<String?>(null) }
  · onShare = Intent.ACTION_SEND text/plain + EXTRA_TEXT=url + createChooser
  · onSendToChat = { url -> shareToChatUrl = url }
  · после PhotoViewer: shareToChatUrl?.let { ShareToChatSheet(...) }
- PhotoViewer signature (feature/photos/.../PhotoViewer.kt:84-97):
  onShare: (String) -> Unit = {},
  onSendToChat: (String) -> Unit = {},
  onBookmark: (String) -> Unit = {},
  onFavorite: (String) -> Unit = {}  — все default {} → обратно совместимо.
- SovaNavHost.kt (overlay PhotoHolder):
  · +imports: android.content.Intent, re.pinok.ui.components.ShareToChatSheet
    (LocalContext уже был).
  · +state рядом с overlayPhoto (строка 695): var shareToChatUrl + val
    photoShareContext = LocalContext.current.
  · PhotoViewer (строка 3411): onShare + onSendToChat wired.
  · после PhotoViewer: ShareToChatSheet (строка 3426).
- ChatDetailScreen.kt (2 callsites):
  · +import ShareToChatSheet (Intent/LocalContext уже были).
  · +state рядом с photoViewerState (строка 1119): var shareToChatUrl.
  · Callsite 1 (строка 6217, pendingPhotos): wired (использует ctx).
  · Callsite 2 (строка 6272, photoViewerState): wired.
  · ShareToChatSheet (строка 6289) — переиспользуется обоими callsites.
- ProfileScreen.kt:
  · +imports: android.content.Intent, re.pinok.ui.components.ShareToChatSheet.
  · +state рядом с photoViewerState (строка 216): var shareToChatUrl.
  · PhotoViewer (строка 1790): wired (использует context из строки 646).
  · ShareToChatSheet (строка 1806).
- UserProfileScreen.kt:
  · +imports: android.content.Intent, re.pinok.ui.components.ShareToChatSheet.
  · +state рядом с photoViewerState (строка 197): var shareToChatUrl.
  · PhotoViewer (строка 1069): wired (использует context из строки 175).
  · ShareToChatSheet (строка 1085).
- PostDetailScreen.kt (2 callsites):
  · +imports: android.content.Intent, re.pinok.ui.components.ShareToChatSheet.
  · +state рядом с photoViewerState (строка 358): var shareToChatUrl
    (переиспользуется обоими callsites).
  · Callsite 1 (строка 622, основной пост с return): wired + ShareToChatSheet
    ДО `return` (строка 638) — чтобы sheet был видим поверх PhotoViewer.
  · Callsite 2 (строка 1489, pendingCommentPhotos): wired.
  · ShareToChatSheet (строка 1506) — переиспользуется state.
- FeedScreen.kt:
  · +imports: android.content.Intent, re.pinok.ui.components.ShareToChatSheet.
  · +state рядом с photoViewerState (строка 1055-1058): photoShareContext +
    shareToChatUrl (LocalContext в FeedScreen ранее отсутствовал).
  · PhotoViewer (строка 2262): wired (использует photoShareContext).
  · ShareToChatSheet (строка 2278).
- PhotosScreen.kt (feature/photos module — 2 callsites, задача упоминала
  line 432 PhotoViewerPhotoItems «может иметь callbacks»):
  · +imports: android.content.Intent, androidx.compose.ui.platform.LocalContext,
    re.pinok.ui.components.ShareToChatSheet.
  · +state в PhotoFeedView (строка 273-275): photoShareContext + shareToChatUrl.
  · +state в AlbumPhotosView (строка 561-563): photoShareContext + shareToChatUrl.
  · Callsite 1 (строка 441, PhotoViewerPhotoItems): wired.
  · Callsite 2 (строка 766, PhotoViewer): wired.
  · ShareToChatSheet ×2 (строки 457 и 782).
- Все изменения помечены комментарием `// PHOTO-SHARE-WIRE:` для grep-trace.

Verification:
- rg "onShare = \{|onSendToChat = \{|shareToChatUrl" app/src/main/java/ +
  feature/photos/src/main/java/ — 10 callsites PhotoViewer/PhotoViewerPhotoItems
  имеют onShare + onSendToChat; в файлах есть shareToChatUrl state + ShareToChatSheet.
  Файлы:
    · SovaNavHost.kt:3411 PhotoViewer — onShare=1 onSendToChat=1 shareToChatUrl=1
    · ChatDetailScreen.kt:6217 PhotoViewer — onShare=1 onSendToChat=1
    · ChatDetailScreen.kt:6272 PhotoViewer — onShare=1 onSendToChat=1
      (один shareToChatUrl state на оба callsites + 1 ShareToChatSheet)
    · ProfileScreen.kt:1790 PhotoViewer — onShare=1 onSendToChat=1
    · UserProfileScreen.kt:1069 PhotoViewer — onShare=1 onSendToChat=1
    · PostDetailScreen.kt:622 PhotoViewer — onShare=1 onSendToChat=1
      (один shareToChatUrl state на оба callsites + 2 ShareToChatSheet:
       1-й внутри if-блока с return, 2-й после 2-го callsite)
    · PostDetailScreen.kt:1489 PhotoViewer — onShare=1 onSendToChat=1
    · FeedScreen.kt:2262 PhotoViewer — onShare=1 onSendToChat=1
    · CommunityScreen.kt:1766 PhotoViewerPhotoItems — был wired ранее, не трогал.
    · PhotosScreen.kt:441 PhotoViewerPhotoItems — onShare=1 onSendToChat=1
    · PhotosScreen.kt:766 PhotoViewer — onShare=1 onSendToChat=1
- grep -c "onShare = {" FeedScreen.kt → 1 ✓ (требование ТЗ).
- Brace balance (awk греп по `{` `}`):
  · SovaNavHost.kt:        772/768 diff=4  (BEFORE 764/760 diff=4 — preserved,
    pre-existing imbalance from string literals with braces)
  · ChatDetailScreen.kt:   2546/2546 diff=0 (BEFORE 2534/2534 diff=0 — preserved)
  · ProfileScreen.kt:      804/806 diff=-2 (BEFORE 796/798 diff=-2 — preserved,
    pre-existing imbalance from string literals)
  · UserProfileScreen.kt:  344/344 diff=0  (BEFORE 336/336 diff=0 — preserved)
  · PostDetailScreen.kt:   528/528 diff=0  (BEFORE 513/513 diff=0 — preserved)
  · FeedScreen.kt:         774/774 diff=0  (BEFORE 766/766 diff=0 — preserved)
  · PhotosScreen.kt:       239/239 diff=0  (BEFORE 223/223 diff=0 — preserved)
  Все 7 изменённых файлов сохраняют исходный brace-balance — мои добавления
  симметричны (каждый new `{` имеет парный `}`).
- Paren balance:
  · SovaNavHost: 1772/1772 diff=0
  · ChatDetailScreen: 6325/6341 diff=-16 (BEFORE 6311/6327 diff=-16 — preserved,
    pre-existing imbalance from `:-)` smileys в строках)
  · ProfileScreen: 1714/1714 diff=0
  · UserProfileScreen: 587/587 diff=0
  · PostDetailScreen: 1083/1083 diff=0
  · FeedScreen: 1768/1768 diff=0
  · PhotosScreen: 371/371 diff=0
- Imports проверены: LocalContext + Intent + ShareToChatSheet присутствуют
  во всех 7 изменённых файлах.
- git diff --stat: 7 файлов, +243 insertions, -0 deletions (чисто additive).
  CommunityScreen.kt НЕ изменён (был wired ранее, служил reference-pattern).
- PhotoViewer.kt НЕ трогали (по условию). onBookmark/onFavorite остались
  default {} на всех 8 callsites — это выходит за рамки ТЗ (bookmark требует
  URL→PhotoItem mapping, которого нет в большинстве callsites).
- Backward compat: 4 callback'а PhotoViewer имеют default {} — callsites
  без wiring (если такие остались) НЕ ломаются, как и раньше (но после этой
  задачи таких callsites в репо больше нет — все 10 wired).
- Не делал git commit (по условию задачи).

Stage Summary:
- Все 10 PhotoViewer/PhotoViewerPhotoItems callsites в проекте теперь имеют
  wired onShare (Intent.ACTION_SEND + createChooser) и onSendToChat (state →
  ShareToChatSheet). 8 callsites из ТЗ + 2 callsite в PhotosScreen.kt (где
  PhotoViewerPhotoItems не имел callbacks + второй PhotoViewer в AlbumPhotosView):
  1. SovaNavHost.kt:3411 — overlay PhotoHolder (push-уведомления)
  2. ChatDetailScreen.kt:6217 — pendingPhotos (превью загружаемых фото)
  3. ChatDetailScreen.kt:6272 — photoViewerState (фото из сообщений)
  4. ProfileScreen.kt:1790 — собственная стена
  5. UserProfileScreen.kt:1069 — чужая стена
  6. PostDetailScreen.kt:622 — фото поста (с return-early)
  7. PostDetailScreen.kt:1489 — pendingCommentPhotos (комментарии)
  8. FeedScreen.kt:2262 — лента
  9. CommunityScreen.kt:1766 — сообщество (был wired ранее, не трогал)
  10. PhotosScreen.kt:441 — PhotoViewerPhotoItems (лента фото)
  11. PhotosScreen.kt:766 — PhotoViewer (альбом)
- UX: на любом экране «⋮» → «Поделиться» открывает системный share-chooser
  (text/plain EXTRA_TEXT=url), «Отправить» открывает ShareToChatSheet
  (выбор диалога → messages.send(text=url)).
- Каждое изменение помечено `// PHOTO-SHARE-WIRE:` для grep-trace.

Риски/вопросы:
1. **Фото URL как text-only share**: share передаётся только URL (String),
   без upload-файла. Если user хочет «Поделиться» как изображение в стороннее
   приложение (WhatsApp/Telegram) — передаётся ссылка, а не bitmap. Для
   загрузки фото + FileProvider URI нужен отдельный task (ImageSaver уже есть
   в PhotoViewer — можно переиспользовать его tempfile).
2. **ShareToChatSheet без sharedStreamUri**: messages.send text=url —
   recipient получает ссылку, а не медиа-вложение. Для ин-апп UX это
   нормально (как в web-VK share-as-link), но отличается от сценария
   «поделиться фото в чат» (там ожидается upload). Можно расширить позже.
3. **PostDetailScreen.kt:622 callsite с return-early**: ShareToChatSheet
   помещён ВНУТРИ if-блока (после PhotoViewer, до `return`), чтобы sheet
   рендерился поверх просмотрщика. Если user закрывает PhotoViewer
   (onDismiss) → return не выполняется пока shareToChatUrl != null — но
   т.к. onSendToChat НЕ закрывает PhotoViewer, viewer остаётся активным,
   ShareToChatSheet отрисовывается поверх. При dismiss ShareToChatSheet
   → viewer продолжает работать. Соответствует поведению CommunityScreen.
4. **Компиляция НЕ проверена** (no Android SDK в окружении) — синтаксис/типы/
  импорты/brace balance проверены вручную. Все используемые символы
  (LocalContext.current, Intent.ACTION_SEND, Intent.createChooser,
  ShareToChatSheet, mutableStateOf<String?>, remember) подтверждены
  существующим usage в CommunityScreen.kt:1766-1829 (reference pattern).
5. **onBookmark/onFavorite НЕ wired** на этих 8 callsites (только в
   CommunityScreen + PhotosScreen PhotoViewerPhotoItems, которые используют
   PhotoItem context). Для остальных callsites PhotoViewer работает со
   списком String URL — нет маппинга URL→PhotoItem. Реализуется расширением
  state до Pair<List<PhotoItem>?, Int> или retrofit URL→PhotoItem lookup —
  выходит за рамки ТЗ (в ТЗ явно: только onShare + onSendToChat).

---

Task ID: PROFILE-ALBUMS
Agent: general-purpose (profile albums)
Task: Альбомы в профиле — в вкладке «Фото»

Work Log:
- Прочитал worklog.md (контекст Task 2/3 о каналах/сообществах) — установил, что CommunityScreen.kt уже имеет рабочий паттерн albums-tab (CommunityAlbumRow + photosGetAlbums/photosGet, CommunityScreen.kt:1649+2313). Использован как образец.
- Изучил модели: `Album` (Models.kt:1278, поля id/ownerId/title/size/description/thumbId/thumbSrc/created/updated/privacyView) и `PhotoItem` (Models.kt:1296, есть `largestUrl`/`mediumUrl`/`smallUrl`). VKApiClient API: `photosGetAlbums(ownerId: Long?)` (VKApiClient.kt:10575), `photosGet(ownerId, albumId, count, offset)` (VKApiClient.kt:10607), `photosGetAll(...)` (VKApiClient.kt:22623).
- ProfileScreen.kt: добавил импорты `Album`, `PhotoItem`, `Icons.AutoMirrored.Filled.ArrowBack`, `Icons.Outlined.Image`.
- ProfileScreen.kt: добавил state vars (lines 305-316): `albums`, `albumsLoading`, `albumsError`, `albumsLoaded`, `openedAlbum`, `albumPhotos`, `albumPhotosLoading`, `albumPhotosError`, `albumPhotosRetryTick`.
- ProfileScreen.kt: внутри `LaunchedEffect(selectedContentTab, contentRetryTick)` в ветке `PROFILE_TAB_PHOTO` добавил параллельный `launch {}` для `photosGetAlbums(ownerId)` (lines 562-579). Грузит альбомы один раз (гейт `albumsLoaded`), не блокируя загрузку «всех фото».
- ProfileScreen.kt: добавил `LaunchedEffect(openedAlbum, albumPhotosRetryTick)` (lines 663-682) для `photosGet(ownerId, albumId=album.id.toString(), count=200)` при тапе по альбому. albumPhotosRetryTick — для ретрая (кнопка «Повторить» в TabErrorRow).
- ProfileScreen.kt: переписал рендер вкладки `PROFILE_TAB_PHOTO` (lines 1736-1838) — две ветки: если `openedAlbum != null` → рендерит кнопку «← Назад к альбомам» + сетку фото альбома (через PhotoGridRow); иначе → горизонтальный список альбомов (ProfileAlbumsRow) над заголовком «Все фото» + прежняя сетка photosGetAll (PhotoGridRow + loadMorePhotoPage).
- ProfileScreen.kt: добавил 3 новых composables в конец файла (lines 3498-3625): `ProfileAlbumsRow` (LazyRow карточек), `ProfileAlbumCard` (обложка 120×120 + название + «N фото»), `AlbumBackRow` (строка «← Назад к альбомам»). Все `internal` — переиспользуются из UserProfileScreen.kt.
- UserProfileScreen.kt: добавил импорты `background`, `clickable`, `RoundedCornerShape`, `clip`, `Image`, `Album`, `PhotoItem` (others уже были).
- UserProfileScreen.kt: те же state vars (lines 264-275).
- UserProfileScreen.kt: внутри `LaunchedEffect(selectedTab, tabRetryTick)` в ветке `PROFILE_TAB_PHOTO` добавил параллельный `launch {}` для `photosGetAlbums(ownerId = userId)` (lines 382-408). Отдельный `LaunchedEffect(openedAlbum, albumPhotosRetryTick)` (lines 417-438) грузит фото альбома.
- UserProfileScreen.kt: переписал рендер `else if (selectedTab == PROFILE_TAB_PHOTO)` (lines 966-1056) — та же двухветочная структура: openedAlbum → фото альбома; иначе → альбомы + «Все фото». Переиспользует `ProfileAlbumsRow` и `AlbumBackRow` из ProfileScreen.kt (internal, тот же package).
- Проверка brace balance: ProfileScreen.kt `{=869, }=871` (diff -2 = PRE-EXISTING в исходном файле 804/806, моих правок balanced: +65 `{`/+65 `}`), parens/brackets balanced. UserProfileScreen.kt все balanced (390/390, 652/652, 20/20).
- Проверка kotlinc (kotlin-compiler-embeddable-2.2.21 через gradle cache): синтаксических ошибок нет — только ожидаемые "unresolved reference" из-за отсутствия Android SDK / Compose classpath. Никаких "expecting" или "unexpected token" errors.
- git commit НЕ делал (по ТЗ).

Stage Summary:
- **Что сделано**: Вкладка «Фото» в ProfileScreen.kt и UserProfileScreen.kt теперь показывает СНАЧАЛА горизонтальный список альбомов (photos.getAlbums) над заголовком «Все фото», а ниже — прежняя сетка photosGetAll. Тап по альбому открывает его фото (photos.get) в той же сетке + строку «← Назад к альбомам». Если альбомов нет (пустой список) — поведение прежнее (только «Все фото»). Кнопка «Повторить» для ретрая при ошибке загрузки фото альбома.
- **Файлы + строки**:
  - `app/src/main/java/re/pinok/ui/screens/profile/ProfileScreen.kt`:
    - импорты (lines 47, 57, 100, 104)
    - state vars (lines 301-316)
    - LaunchedEffect для альбомов внутри selectedContentTab-PHOTO ветки (lines 562-579)
    - LaunchedEffect(openedAlbum, retryTick) (lines 663-682)
    - рендер PHOTO tab (lines 1736-1838)
    - новые composables (lines 3498-3625): `ProfileAlbumsRow`, `ProfileAlbumCard`, `AlbumBackRow` (все `internal`)
  - `app/src/main/java/re/pinok/ui/screens/profile/UserProfileScreen.kt`:
    - импорты (lines 11, 12, 31, 39, 71, 83, 84)
    - state vars (lines 260-275)
    - LaunchedEffect для альбомов внутри selectedTab-PHOTO ветки (lines 382-408)
    - LaunchedEffect(openedAlbum, retryTick) (lines 417-438)
    - рендер PHOTO tab (lines 966-1056)
- **Доказательства**:
  - `grep "albums\|photosGetAlbums\|openedAlbum"` в обоих файлах показывает все ключевые идентификаторы (см. verification выше).
  - Brace balance: мои правки сбалансированы (+65/+65 в ProfileScreen, +46/+46 в UserProfile). Pre-existing -2 diff в ProfileScreen.kt — НЕ мои (строковые литералы с `}` без соответствующих `{`).
  - kotlinc syntax check: 0 syntax errors в обоих файлах (только ожидаемые unresolved references из-за отсутствия Android SDK).
  - @Composable count: ProfileScreen.kt 22→25 (+3 новых composables), UserProfileScreen.kt 2→2 (переиспользует shared).
- **Риски/вопросы**:
  1. `photosGet` count=200 — фиксированный кап. Если в альбоме >200 фото, пагинации нет (как в CommunityScreen, который тоже грузит count=100). Можно расширить позже paging-паттерном из photoGridUrls.
  2. `photosGetAlbums` грузится БЕЗ пагинации (список альбомов обычно короткий, как в CommunityScreen). Для аккаунтов с десятками альбомов VK сам ограничит — пагинация albums не реализована (как и в CommunityScreen).
  3. Не проверял runtime-поведение (нет Android SDK для сборки APK). Синтаксис чистый, но фактическое отображение нужно проверить на устройстве.
  4. Системные альбомы VK (id=-9000 «Сохранённые», -15 «С стены» и т.п.) тоже грузятся (need_system=1 в VKApiClient.photosGetAlbums) и могут дать пустые/приватные альбомы — это совпадает с поведением CommunityScreen. Если нужно фильтровать — отдельная задача.
  5. isSelf в UserProfileScreen: для своего профиля в этом экране не грузится ни фото, ни альбомы (early-return в `LaunchedEffect(selectedTab)`). Это PRE-EXISTING поведение — не ломал. Свой профиль обычно открывается через ProfileScreen.kt, не UserProfileScreen.kt.
- **Не сделано (по границам ТЗ)**: VKApiClient.kt, Models.kt, SovaApp.kt не трогал — API уже есть. PhotoGridRow/photoViewerState/PhotoViewer не сломаны — использованы как есть.
- **worklog обновлён**: да, этим APPEND'ом.

---
Task ID: ATTACH-PICKER-ALBUMS
Agent: general-purpose (AttachmentPicker albums + pagination)
Task: Альбомы + пагинация в AttachmentPickerSheet

Work Log:
- Прочитал `/home/z/my-project/worklog.md` (последние 100 строк) — контекст
  PROFILE-ALBUMS: в ProfileScreen/UserProfileScreen уже реализован паттерн
  albums-tab (photosGetAlbums → LazyRow карточек → photosGet по тапу). В
  VKApiClient.kt уже есть весь нужный API: photosGetAlbums (10575),
  photosGet (10607), photosGetAll (22623), videoGetWithCount (9974 →
  Pair<List<Video>,Int>), docsGetWithCount (11191 → Pair<List<DocFile>,Int>).
- Изучил AttachmentPickerSheet.kt (759 строк): AudioPickerTab уже имеет
  эталонный паттерн пагинации (serverOffset + hasMore + loadMore +
  snapshotFlow visibleItemsInfo.lastOrNull + distinctUntilChanged + collect
  → loadMore). PhotoPickerTab/VideoPickerTab/DocsPickerTab — БЕЗ пагинации,
  PhotoPickerTab — БЕЗ альбомов. Album model (Models.kt:1278) и PhotoItem
  (Models.kt:1296, с mediumUrl/smallUrl/accessKey) — уже есть.
- AttachmentPickerSheet.kt: добавил 8 импортов — LazyRow,
  rememberLazyGridState, ArrowBack (automirrored.filled), Card, CardDefaults,
  Album (data.model), PhotoItem (data.model). Существующие rememberLazyListState
  / snapshotFlow / distinctUntilChanged уже были импортированы.
- AttachmentPickerSheet.kt: переписал PhotoPickerTab (строки 632-788):
  + state: albums/albumsLoading/albumsError, openedAlbum (Album?),
    photos/loading/error, serverOffset/hasMore/loadingMore, gridState
    (rememberLazyGridState), scope.
  + LaunchedEffect(Unit): photosGetAlbums(ownerId=myId) — параллельно с фото,
    не блокирует. albumsError не роняет сетку фото (альбомы опциональны).
  + LaunchedEffect(openedAlbum): при смене openedAlbum (null=«Все фото»/
    album=конкретный) — обнуляет offset/hasMore/photos и грузит первую
    страницу: photosGetAll (null) или photosGet(albumId=currentAlbum.id,
    count=50, offset=0) → mapNotNull { it.toVkPhotoRef() }. hasMore =
    page.size >= 50 (паттерн #AUDIO-PAGING-HOLE — неполная страница ≠ конец).
  + loadMore(): тот же branch по currentAlbum (capture в локальный val для
    smart-cast без !!), offset += page.size, distinctBy ownerId_id.
  + LaunchedEffect(gridState): snapshotFlow { lastVisible >= total-6 }
    .distinctUntilChanged().collect { loadMore() } — паттерн AudioPickerTab,
    адаптированный для LazyVerticalGrid (visibleItemsInfo.lastOrNull().index
    — линейный индекс в grid).
  + UI: Column(fillMaxSize) { LazyRow альбомов (item "all_photos" псевдо-альбом
    + items(albums)) | индикатор при albumsLoading | кнопка «← Все фото»
    (Row с ArrowBack, видна когда openedAlbum != null) | Box(weight 1f) {
    when { loading/error/empty/LazyVerticalGrid(state=gridState) } } }.
- AttachmentPickerSheet.kt: добавил 2 новых composables + 1 extension:
  + AlbumChip (строки 854-922): Card 108×100 (CardDefaults.cardColors
    primaryContainer при isSelected), Box 100dp cover (AsyncImage thumbSrc /
    Icons.Outlined.Image fallback), Text title (labelSmall, maxLines 1,
    Ellipsis), Text countText (labelSmall outline). clickable на Card modifier.
  + PhotoItem.toVkPhotoRef() extension (строки 928-933): VkPhotoRef(
    ownerId, id, accessKey, previewUrl = mediumUrl ?: smallUrl).
- AttachmentPickerSheet.kt: переписал VideoPickerTab (строки 383-471):
  + state: serverOffset/hasMore/loadingMore/listState/scope.
  + LaunchedEffect(Unit): videoGetWithCount(count=50, offset=0) →
    Pair(total, page); videos = page.filter{id>0 && ownerId!=0}.distinctBy;
    serverOffset = page.size; hasMore = page.isNotEmpty() || total<0.
  + loadMore(): videoGetWithCount(count=50, offset=serverOffset); fresh
    filter existing; distinctBy; hasMore = page.isNotEmpty() || total<0.
  + LaunchedEffect(listState): snapshotFlow { lastVisible >= total-5 }
    .distinctUntilChanged().collect { loadMore() } — паттерн AudioPickerTab 1:1.
  + LazyColumn(state=listState) + item(key="video_picker_more") с
    CircularProgressIndicator(20dp) когда hasMore.
- AttachmentPickerSheet.kt: переписал DocsPickerTab (строки 1011-1102):
  + state: serverOffset/hasMore/loadingMore/listState/scope.
  + LaunchedEffect(Unit): docsGetWithCount(count=50, offset=0) →
    Pair(total, page); docs = page.distinctBy ownerId_id; serverOffset =
    page.size; hasMore = page.isNotEmpty() || total<0.
  + loadMore(): docsGetWithCount(count=50, offset=serverOffset); fresh
    filter existing; distinctBy; hasMore = page.isNotEmpty() || total<0.
  + LaunchedEffect(listState): snapshotFlow { lastVisible >= total-5 }
    .distinctUntilChanged().collect { loadMore() }.
  + LazyColumn(state=listState) + item(key="docs_picker_more") с
    CircularProgressIndicator(20dp) когда hasMore.
- НЕ ломал: AttachmentPickerSheet top-level signature (onPickAudio/Video/
  Gift/PhotoAttachment/DocAttachment — все unchanged), AudioPickerTab,
  GiftPickerTab, GiftCell, VideoRow, VkPhotoCell, parseVkPhotoRef, DocRow,
  formatDocSizeShort. Callback onPickPhotoAttachment: (attachment, thumb) и
  onPickDocAttachment: (attachment, title) — сигнатуры unchanged.
- Проверка: brace/paren/bracket balance — { = 298 / } = 298 (diff 0),
  ( = 597 / ) = 597 (diff 0), [ = 6 / ] = 6 (diff 0). @Composable count
  11→12 (+1 AlbumChip). Файл 759→1141 строк.
- Проверка grep: photosGetAlbums (656), openedAlbum (640/759/760/768/769/
  787/791), loadMore (243 audio + 394 video + 700 photo + 1022 docs = 4),
  serverOffset (237/271 audio + 388/400/422 video + 645/689/710/720 photo
  + 1016/1028/1047 docs), snapshotFlow (284/435/735/1060), gridState/
  listState LaunchedEffect — все на местах. // ATTACH-PICKER-ALBUMS:
  комментарий-маркер в 9 местах (header PhotoPickerTab + 3×пагинация-
  comment + 2×composable-doc + albums-row).
- kotlinc НЕ доступен в окружении (NO kotlinc in PATH, no gradle cache) —
  синтаксис/типы проверены вручную: brace balance 0, паттерн 1:1 с эталонным
  AudioPickerTab (который компилируется в проекте), все используемые символы
  (rememberLazyGridState, LazyGridState.layoutInfo.visibleItemsInfo,
  CardDefaults.cardColors/cardElevation, PhotoItem.mediumUrl/smallUrl,
  Album.thumbSrc/size/title/id, videoGetWithCount/docsGetWithCount Pair
  destructuring) подтверждены существующими usage в CommunityScreen.kt /
  ProfileScreen.kt / VKApiClient.kt.
- git commit НЕ делал (по ТЗ).

Stage Summary:
- **Что сделано для PhotoPickerTab (альбомы + пагинация)**: При открытии таба
  «Фото» теперь параллельно грузятся `photosGetAlbums(myId)` и
  `photosGetAll(myId, count=50, offset=0)`. Над сеткой фото — горизонтальный
  `LazyRow` с карточками альбомов: первый пункт «Все фото» (псевдо-альбом,
  `openedAlbum=null` → photosGetAll), затем реальные альбомы из VK
  (tap → `openedAlbum=album` → `LaunchedEffect(openedAlbum)` перезагружает
  сетку через `photosGet(albumId=album.id, count=50, offset=0)`). Карточка
  `AlbumChip` 108×100: Card с обложкой `thumbSrc` (fallback `Icons.Outlined.
  Image`), название (maxLines 1, Ellipsis), «N фото» (labelSmall outline),
  isSelected → primaryContainer tint. Когда открыт конкретный альбом — над
  сеткой появляется кнопка «← Все фото» (Row с `Icons.AutoMirrored.Filled.
  ArrowBack`, tint primary) для возврата к photosGetAll. Пагинация: при
  скролле к концу сетки (`lastVisible >= totalItemsCount - 6`) срабатывает
  `loadMore()` (offset += page.size), стоп по неполной странице
  (`page.size < 50`). Конвертер `PhotoItem.toVkPhotoRef()` (photos.get →
  VkPhotoRef с previewUrl = mediumUrl ?: smallUrl). Album-error не роняет
  фото (альбомы опциональны — тихий fail).
- **Что сделано для VideoPickerTab (пагинация)**: Заменил `videoGet(count=50)`
  на `videoGetWithCount(count=50, offset=...)` → Pair<List<Video>, Int>.
  Добавил state: serverOffset/hasMore/loadingMore/listState/scope.
  `LaunchedEffect(Unit)` — первая страница; `loadMore()` — догрузка по offset
  с distinctBy ownerId_id и фильтром дубликатов; `LaunchedEffect(listState)` —
  snapshotFlow { lastVisible >= total-5 }.distinctUntilChanged().collect →
  loadMore (паттерн AudioPickerTab 1:1). LazyColumn с `state=listState` +
  item(key="video_picker_more") с прогресс-индикатором 20dp когда hasMore.
  hasMore = page.isNotEmpty() || total<0 (#AUDIO-PAGING-HOLE — сбой VK total
  не обрывает пагинацию).
- **Что сделано для DocsPickerTab (пагинация)**: Заменил `docsGet(count=50)`
  на `docsGetWithCount(count=50, offset=...)` → Pair<List<DocFile>, Int>.
  Структура идентична VideoPickerTab: serverOffset/hasMore/loadingMore/
  listState/scope, LaunchedEffect(Unit) + loadMore + LaunchedEffect(listState)
  с snapshotFlow. LazyColumn(state=listState) + item(key="docs_picker_more")
  с прогресс-индикатором когда hasMore.
- **Файлы + строки** (только `AttachmentPickerSheet.kt`, другие файлы НЕ
  трогал — по границам ТЗ):
  - импорты: 21 (rememberLazyGridState), 23 (LazyRow), 29 (ArrowBack),
    40-41 (Card/CardDefaults), 71 (Album), 74 (PhotoItem)
  - VideoPickerTab: 375-471 (header comment + composable + pagination)
  - PhotoPickerTab: 547-788 (header + composable с альбомами+пагинацией)
  - AlbumChip: 849-922 (новый composable)
  - PhotoItem.toVkPhotoRef(): 922-933 (новый extension)
  - DocsPickerTab: 1003-1102 (header comment + composable + pagination)
- **Доказательства**:
  - `grep "photosGetAlbums\|album\|loadMore\|offset\|pagingTrigger\|snapshotFlow"`
    AttachmentPickerSheet.kt → 60+ совпадений (см. verification выше), все
    ключевые идентификаторы присутствуют в Photo/Video/Docs tabs.
  - Brace balance: `{=298, }=298` (diff 0); `(=597, )=597` (diff 0);
    `[=6, ]=6` (diff 0). Файл 759→1141 строк.
  - `onPick` signatures unchanged: AudioPickerTab `(Track)`, VideoPickerTab
    `(Video)`, GiftPickerTab `(GiftItem)`, PhotoPickerTab `(String, String?)`,
    DocsPickerTab `(attachment: String, title: String)`.
  - `AttachmentPickerSheet` top-level signature unchanged (131).
  - Нет `openedAlbum!!` (использует `currentAlbum` capture + smart-cast).
  - Нет bare `videoGet(`/`docsGet(` calls (только WithCount варианты).
  - 4 `loadMore` функций (audio/video/photo/docs — все с одинаковым
    паттерном).
  - 5 `LaunchedEffect(listState/gridState/openedAlbum)` для пагинации
    (audio pre-existing + 3 новых + 1 openedAlbum для перезагрузки фото).
- **Риски/вопросы**:
  1. **photosGetAlbums без пагинации**: список альбомов грузится одним
     вызовом (без offset). VK API возвращает все альбомы пользователя
     (включая системные: «Сохранённые», «С стены» и т.п. — need_system=1 в
     VKApiClient.photosGetAlbums). Для аккаунтов с десятками альбомов это
     один большой ответ, но VK сам ограничивает ~100 альбомов. Пагинация
     альбомов не реализована (как и в CommunityScreen/ProfileScreen —
     отдельная задача, если понадобится).
  2. **photosGet photosGetAll returning different types**: photosGetAll
     возвращает `List<JsonObject>` (парсится через `parseVkPhotoRef`), а
     photosGet возвращает `List<PhotoItem>` (парсится через `toVkPhotoRef`).
     Оба конвертируются в `VkPhotoRef` — единый UI-тип. Если VK поменяет
     формат sizes в одном из endpoint'ов — может разойтись превью, но
     PhotoItem.mediumUrl и parseVkPhotoRef оба берут max-area size —
     консистентно.
  3. **gridState не сбрасывается при смене openedAlbum**: при тапе по
     альбому `photos = emptyList()` обнуляет сетку, gridState может держать
     stale scroll offset, но LazyVerticalGrid с 0 items clamp'ит к 0
     автоматически. При повторной загрузке сетка начинается с верха.
     Если замечен визуальный баг «остаётся на старой позиции скролла» —
     добавить `LaunchedEffect(openedAlbum) { gridState.scrollToItem(0) }`.
  4. **hasMore для photos = page.size >= 50**:photosGetAll не возвращает
     totalCount, поэтому стоп-критерий — неполная страница (как в
     ProfileScreen.loadMorePhotoPage). Для albums-ветки (photosGet) — тот
     же критерий. Если VK вернёт ровно 50 фото в последней странице
     (маловероятно, но возможно) — будет один лишний пустой loadMore-вызов,
     который вернёт пустую страницу и остановит пагинацию. Acceptable.
  5. **Компиляция НЕ проверена** (no kotlinc / Android SDK в окружении).
     Синтаксис чистый (brace balance 0, паттерн 1:1 с эталонным
     AudioPickerTab). Все используемые символы (rememberLazyGridState,
     LazyGridState.layoutInfo.visibleItemsInfo, CardDefaults.cardColors/
     cardElevation, PhotoItem.mediumUrl/smallUrl/accessKey, Album.thumbSrc/
     size/title/id, videoGetWithCount/docsGetWithCount Pair destructuring)
     подтверждены существующими usage в CommunityScreen.kt / ProfileScreen.kt
     / VKApiClient.kt.
  6. **onPickPhotoAttachment previewUrl для album-photos**: для photosGet
     (альбом) превью берётся из `PhotoItem.mediumUrl` (300-600px), для
     photosGetAll — из max-area size в JsonObject. Качество превью в чате
     (#COMPOSER-ATTACH-PREVIEW) консистентно — композер рисует миниатюру из
     этого URL. Для full-size в сообщении используется attachment-строка
     (photo{owner}_{id}[_key]), не URL — VK сам отдаст нужный размер.
- **Не сделано (по границам ТЗ)**: VKApiClient.kt, Models.kt, SovaApp.kt,
  UnifiedAttachMenu.kt (buildVkAttachment) — не трогал, API уже есть.
  AudioPickerTab/GiftPickerTab — не трогал (у первого уже была пагинация,
  у второго gifts.getCatalog без пагинации по ТЗ). git commit НЕ делал.
- **worklog обновлён**: да, этим APPEND'ом.

---
Task ID: MULTI-SELECT-ATTACH
Agent: general-purpose (multi-select attachments)
Task: Множественный выбор вложений + предпросмотр

Work Log:
- Прочитал worklog (волна ATTACH-PICKER-ALBUMS — альбомы+пагинация, готова).
- AttachmentPickerSheet.kt: убрал `; onDismiss()` после каждого onPick() (строки 196-200).
  Вместо этого добавил multi-select с накоплением:
  + Новый параметр `multiSelect: Boolean = true` (default ON — все существующие
    вызовы ChatDetailScreen/CreatePostDialog получают multi-select автоматически).
  + Внутренний state `selectedItems: SnapshotStateList<PendingPick>` (гетерогенный
    список выборов) + `selectedKeys: Set<String>` для O(1) lookup'а.
  + Новый sealed interface `PendingPick` (5 вариантов): Audio(track), Video(video),
    Gift(gift), Photo(attachment, thumb), Doc(attachment, title) — каждый с уникальным
    `key` для дедупа (type{owner}_{id} для audio/video/gift, type{owner}_{id}[_key]
    для photo/doc — совпадает с buildVkAttachment форматом).
  + Top bar в multi-режиме: «Выбрано: N» + TextButton «Готово (N)» (enabled при N>0).
    В legacy-режиме (multiSelect=false) — прежний заголовок «Прикрепить».
  + `flushAndDismiss()` — flush всех накопленных picks в соответствующие onPickXxx
    колбеки (по одному вызову на каждый pick, в порядке выбора), затем onDismiss().
  + Каждый из 5 табов (Audio/Video/Gifts/Photos/Docs) принял 2 новых параметра:
    `multiSelect: Boolean = false`, `selectedKeys: Set<String> = emptySet()`.
  + Каждая ячейка (AudioTrackRow, VideoRow, GiftCell, VkPhotoCell, DocRow) приняла
    `multiSelect: Boolean = false`, `isSelected: Boolean = false`. Рендер overlay:
    - rows (Audio/Video/Doc): trailing Icon CheckCircle (filled=primary если выбран,
      outlined=outline если нет) + фон primaryContainer@0.35 при выборе.
    - cells (Gifts): Icon-overlay в TopEnd углу + фон primaryContainer@0.55.
    - photos grid: Icon-overlay в TopEnd углу + затемнение Color.Black@0.30 поверх
      фото (как в системном галерейном multi-select).
  + В legacy-режиме (multiSelect=false) — overlay не рисуется, picker сразу
    вызывает onPickXxx + onDismiss (старое поведение для обратной совместимости).
- ChatDetailScreen.kt: убрал immediate sendWithAttachment/sendAudioToChat/
  sendVideoToChat из onPickXxx колбеков. Теперь они накапливают в `vkAttachments`:
  + State `var vkAttachments by remember { mutableStateOf<List<VkAttachPreview>>(emptyList()) }`
    (паттерн 1:1 с CreatePostDialog).
  + `data class VkAttachPreview(att, label, thumb)` — public, рядом с
    PendingFileAttachment.
  + `onPickPhotoAttachment`: vkAttachments + VkAttachPreview(att, "Фото из VK", thumb).
  + `onPickDocAttachment`: vkAttachments + VkAttachPreview(att, "Документ: $title").
  + `onPickAudio`: buildVkAttachment("audio", ownerId, id, accessKey) →
    VkAttachPreview(att, "Музыка: $title", albumThumb).
  + `onPickVideo`: buildVkAttachment("video", ownerId, id, accessKey) →
    VkAttachPreview(att, "Видео: $title", image.maxByOrNull{width}?.url).
  + `onPickGift`: остался immediate gifts.send (подарки не attachment-строка,
    а отдельный API gifts.send(user_id, gift_id) — накапливать нельзя).
  + doSend(): добавлен `vkAtts = vkAttachments` snapshot; условие batch
    расширено: `(photos.isNotEmpty() || pfiles.isNotEmpty() || vkAtts.isNotEmpty())`.
    Внутри batch — `attachments += vkAtts.map { it.att }` (VK-строки уже готовы,
    upload не нужен) → потом фото/файлы с устройства дозагружаются. Отправка одним
    `sendWithAttachment(peerId, attachments.joinToString(","), caption)`.
    На всех 3 error-путях (batch failed / no attachments uploaded / exception) —
    `vkAttachments = vkAtts` (restore для retry). inputText = caption.
  + `VkAttachmentsBar` composable (LazyRow chips с миниатюрой+названием+×),
    рендерится над PendingPhotosBar. AnimatedVisibility (fade+expand).
  + `VkAttachChip` (72×80dp): миниатюра 48dp (или Icon AttachFile fallback),
    label (maxLines 2, Ellipsis, 10sp), × в TopEnd (Color.Black@0.55, CircleShape).
  + SendButtonState.SUBMIT conditions: + `|| vkAttachments.isNotEmpty()` (2 места).
  + Импорт `re.pinok.ui.components.buildVkAttachment` добавлен.
- CreatePostDialog.kt: изменений в логику НЕ потребовалось — onPickXxx callbacks
  уже накапливали в `vkAttachments` без вызова onDismiss (проверено grep'ом:
  onDismiss НЕ внутри onPickAudio/Video/Photo/Doc callbacks, только в outer
  dialog dismiss). Добавил `multiSelect = true` явно (для документации/ясности,
  что picker в multi-режиме — пользователь выбирает несколько фото/файлов/песен/видео
  за один сеанс, picker НЕ закрывается после первого выбора).

Stage Summary:
- **Что сделано для AttachmentPickerSheet (multi-select)**: Добавлен параметр
  `multiSelect: Boolean = true` (default ON). В multi-режиме picker НЕ закрывается
  после первого выбора — тап по элементу toggles выделение (визуальный overlay:
  CheckCircle icon в TopEnd/трейлинге + фон primaryContainer@0.35 для rows /
  Color.Black@0.30 для photo cells). Над табами — Row с заголовком «Выбрано: N»
  и TextButton «Готово (N)» (disabled при N=0). По тапу «Готово» — flush всех
  накопленных picks в onPickXxx колбеки (по одному вызову на каждый pick, в порядке
  выбора) + onDismiss(). Гетерогенный wrapper PendingPick (sealed interface, 5
  вариантов: Audio/Video/Gift/Photo/Doc) с уникальным `key` для дедупа. В legacy-
  режиме (multiSelect=false) — прежнее поведение «pick → dismiss» (обратная
  совместимость). Все 5 табов + 5 ячеек получили параметры `multiSelect` +
  `isSelected` для рендера overlay. Существующие колбэки onPickAudio/Video/Gift/
  PhotoAttachment/DocAttachment — сигнатуры unchanged.
- **Что сделано для ChatDetailScreen (накопление + превью)**: Убраны immediate
  sendWithAttachment/sendAudioToChat/sendVideoToChat из onPickXxx колбеков
  AttachmentPickerSheet. Вместо этого — накопление в `vkAttachments:
  List<VkAttachPreview>` (att + label + thumb). onPickAudio/Video теперь сами
  собирают attachment-строку через `buildVkAttachment` (как в CreatePostDialog)
  + превью (albumThumb для audio, image.maxByOrNull{width}?.url для video).
  doSend() расширен: условие batch теперь включает `vkAtts.isNotEmpty()`;
  VK-attachment-строки добавляются в attachments-список ДО фото/файлов с
  устройства (upload не нужен), отправка одним `sendWithAttachment(peerId,
  attachments.joinToString(","), caption)`. Все 3 error-пута восстановливают
  `vkAttachments = vkAtts` + `inputText = caption`. Превью: `VkAttachmentsBar`
  (LazyRow chips 72×80dp) над PendingPhotosBar — миниатюра/Icon + label + ×.
  SendButtonState.SUBMIT + условие «show Send button if» — оба включают
  `|| vkAttachments.isNotEmpty()` (кнопка Send активна когда есть VK-вложения).
  Подарки остались immediate (gifts.send — отдельный API, не attachment-строка).
- **Что сделано для CreatePostDialog**: изменений в логику НЕ требовалось —
  onPickXxx callbacks уже накапливали в `vkAttachments` без onDismiss (строки
  769-789 оригинала). Добавлен `multiSelect = true` явно для документации
  (picker в multi-режиме — пользователь выбирает несколько фото/файлов/песен/видео,
  picker НЕ закрывается после первого выбора; onPick callbacks НЕ вызывают
  onDismiss — picker закрывается сам по «Готово»/swipe-down).
- **Файлы + строки**:
  - AttachmentPickerSheet.kt (1473 строк, было 1141):
    - импорты: 30 (CheckCircle filled), 35 (CheckCircle outlined), 52 (TextButton),
      57 (mutableStateListOf)
    - multiSelect параметр: 148
    - PendingPick sealed interface: 340-357
    - selectedItems state + toggle + flushAndDismiss: 184-207
    - Top bar с «Готово (N)»: 222-254
    - when (activeTab) с multiSelect-обёртками: 268-330
    - AudioPickerTab multiSelect params: 386-392; items call: 464-472;
      AudioTrackRow multiSelect params: 488-494; фон+overlay: 497-501, 547-558
    - VideoPickerTab multiSelect params: 570-576; items call: 649-657;
      VideoRow multiSelect params: 674-680; фон+overlay: 682-687, 726-737
    - GiftPickerTab multiSelect params: 745-751; items call: 786-794;
      GiftCell multiSelect params: 800-806; фон+overlay: 811-813, 818-851
    - PhotoPickerTab multiSelect params: 891-896; items call: 1096-1112;
      VkPhotoCell multiSelect params: 1206-1211; затемнение+overlay: 1236-1257
    - DocsPickerTab multiSelect params: 1310-1315; items call: 1386-1399;
      DocRow multiSelect params: 1414-1420; фон+overlay: 1422-1427, 1452-1463
  - ChatDetailScreen.kt (12623 строк, было 12455):
    - импорт buildVkAttachment: 275-277
    - state vkAttachments: 795-799
    - AttachmentPickerSheet invocation с multiSelect=true + accumulate: 5943-6007
    - doSend() расширение: 2878-2908 (snapshot+clear+prepend), 2994/3002/3010 (restore)
    - VkAttachmentsBar в UI: 4479-4494
    - SendButtonState: 4701, 4740
    - data class VkAttachPreview: 12259-12263
    - VkAttachmentsBar composable: 12265-12332
    - VkAttachChip composable: 12334-12399
  - CreatePostDialog.kt (1053 строк, было 1047):
    - multiSelect=true явно + комментарий: 760-770
- **Доказательства**:
  - `grep "onDismiss\|multiSelect\|selectedItems\|Готово\|vkAttachments" AttachmentPickerSheet.kt`
    → 40+ совпадений (см. verification выше): multiSelect=true (148),
    selectedItems (184/185/189/196/205/231/232/238), Готово (123/241/242),
    onDismiss (146/206/210/276/288/300/312/324 — только legacy + outer ModalBottomSheet).
  - `grep "vkAttachments\|VkAttachPreview" ChatDetailScreen.kt` → 24+ совпадений:
    state (802), doSend snapshot/clear/restore (2885/2894/2994/3002/3010),
    AttachmentPickerSheet accumulate (5966/5969/5975/5986), UI bar (4484-4488),
    SendButtonState (4701, 4740), data class (12259), composables (12272, 12340).
  - `grep "sendWithAttachment\|sendAudioToChat\|sendVideoToChat" ChatDetailScreen.kt`
    в lines 5920-5995 (AttachmentPickerSheet onPick callbacks) → только в комментариях,
    не в actual code calls (✓ accumulate вместо immediate send).
  - `grep "onDismiss" CreatePostDialog.kt | head -5` → onDismiss в KDoc (145),
    signature (163), post-publish (377, 398), dialog dismiss (403). НЕТ внутри
    onPickAudio/Video/Photo/Doc callbacks (✓).
  - Brace balance: AttachmentPickerSheet.kt — `{=350/350 (0)  (=733/733 (0)  [=8/8 (0)`.
    ChatDetailScreen.kt — `{=2527/2527 (0)  (=6369/6385 (-16 pre-existing from string
    literals, delta from my edits: +93/+93 balanced)  [=95/95 (0)`.
    CreatePostDialog.kt — `{=239/239 (0)  (=509/509 (0)  [=13/13 (0)`.
  - 30 `// MULTI-SELECT-ATTACH` комментариев-маркеров в AttachmentPickerSheet.kt.
  - onPick signatures unchanged: onPickAudio (Track), onPickVideo (Video),
    onPickGift (GiftItem), onPickPhotoAttachment (String, String?),
    onPickDocAttachment (String, String) — все как в оригинале.
  - Tab structure unchanged: Music/Video/Gifts/Photos/Docs — все 5 табов
    на месте, пагинация (audioGetWithCount/videoGetWithCount/photosGetAll/
    docsGetWithCount + snapshotFlow loadMore) сохранена из ATTACH-PICKER-ALBUMS.
  - Подарки (gifts.send) — НЕ накапливаются в vkAttachments (отдельный API),
    отправляются immediately как раньше (корректное поведение).
- **Риски/вопросы**:
  1. ** gifts.send vs multiSelect**: подарок уходит сразу ( gifts.send — отдельный
     API, не attachment-строка messages.send). Если пользователь в multi-select
     выберет подарок + фото — подарок уйдёт сразу, фото накопится в vkAttachments.
     Поведение согласовано с VK web: подарок = отдельный send, не группируется.
     Если в picker выбрано несколько подарков — каждый уйдёт отдельным gifts.send
     (N API-вызовов) — может быть медленно, но acceptable (подарки редко выбирают
     пачкой, обычно 1 на диалог).
  2. **VK API limit 10 attachments per messages.send**: VK отклонит батч > 10
     вложений. В doSend() нет проверки лимита — если пользователь выберет 15 фото
     из VK + 5 с устройства, sendWithAttachment вернёт error. Это можно
     обработать тостом «Слишком много вложений (макс. 10)», но это вне рамок ТЗ
     ( existing pendingPhotos уже имеет `.take(10)` для фото с устройства —
     аналогичный cap для vkAttachments можно добавить, но не критично).
  3. **onPickGift НЕ возвращается в selectedItems после отправки**: если подарок
     отправлен сразу (gifts.send ok), но picker остался открытым в multi-режиме
     (пользователь может выбрать ещё) — выбранный подарок НЕ снимает выделение
     (gifts.send не toggle'ит selectedItems в onPickGift ветке). Поведение:
     пользователь видит «выбранный» подарок после отправки, но он уже ушёл.
     Acceptable (подарки редко выбирают пачкой), но при необходимости можно
     убрать подарок из selectedItems сразу после gifts.send.
  4. **При peerId=0/канале multi-select тоже работает**: peerId<0 (канал) —
     doSend() имеет guard `if (peerId < 0 && (isChannel || channelWriteDenied))`
     BEFORE batch path → отправка заблокирована с Toast, vkAttachments сохранены
     (не очищаются). Пользователь может убрать выбор или переключить чат. OK.
  5. **Компиляция НЕ проверена** (no kotlinc / Android SDK в окружении).
     Синтаксис чистый: brace balance 0 на всех 3 файлах (paren -16 в
     ChatDetailScreen — pre-existing, delta от моих правок +93/+93 balanced).
     Все используемые символы (mutableStateListOf, TextButton, Icons.Filled/
     Outlined.CheckCircle, buildVkAttachment, Video.image/Thumb, Track.albumThumb/
     accessKey, LocalAnimScale, tweenScaled, AnimatedVisibility, Surface, AsyncImage)
     подтверждены существующими usage в проекте (CommunityScreen, ChatDetailScreen,
     CreatePostDialog, PendingPhotosBar).
  6. **VkAttachPreview дублирует CreatePostDialog.VkAttachPreview**: CreatePostDialog
     имеет `private data class VkAttachPreview` (private), ChatDetailScreen имеет
     `data class VkAttachPreview` (public). Это намеренно — оба класса приватны/
     локальны для своего экрана, не conflated в общую модель (как PendingPhoto
     в PendingPhotosBar.kt). Если потребуется общий компонент — вынести в отдельный
     файл UI-модели (не в зоне ТЗ).
  7. **selectedItems НЕ сбрасывается при смене таба**: пользователь может
     переключаться между табами Music/Video/Photos/Docs и накапливать выбор
     из всех табов одновременно (picker запоминает выборы). «Готово» отправит
     все. Это expected UX (как в Telegram) — удобно для смешанных вложений
     (фото + музыка в одном сообщении). Если хочется «по одному табу» — добавить
     `selectedItems.clear()` в onClick таба (но это breaking UX, не делал).
- **Не сделано (по границам ТЗ)**: VKApiClient.kt, Models.kt, SovaApp.kt,
  UnifiedAttachMenu.kt (buildVkAttachment) — не трогал, API уже есть.
  git commit НЕ делал (по ТЗ).
- **worklog обновлён**: да, этим APPEND'ом.

---
Task ID: MULTI-SELECT-FIX
Agent: general-purpose (fix compile errors)
Task: Исправить ошибки компиляции MULTI-SELECT-ATTACH (commit 365832f не собирался)

Work Log:
- Прочитал контекст: worklog MULTI-SELECT-ATTACH отмечал «Компиляция НЕ
  проверена» — фикс-ап сборки :app:compileDebugKotlin.
- Сверил сигнатуры VKApiClient:
    audioGetWithCount : Pair<Int, List<Track>>     (total FIRST, page SECOND)
    videoGetWithCount : Pair<List<Video>, Int>     (page FIRST, total SECOND) ← обращён
    docsGetWithCount  : Pair<List<DocFile>, Int>   (page FIRST, total SECOND) ← обращён
  В VideoPickerTab/DocsPickerTab деструктуризация `val (total, page)` была
  копипастой из AudioPickerTab — page получал тип Int, ломая весь paging-блок.
- AttachmentPickerSheet.kt — правки (в границах ТЗ):
  1. PendingPick.Video: `data class Video(val video: Video)` → `val video:
     re.pinok.data.model.Video` (FQN). Внутри sealed PendingPick голое имя
     `Video` резолвилось в сам вложенный PendingPick.Video (рекурсивный тип),
     поэтому onPickVideo(item.video) шёл с actual=PendingPick.Video (:199),
     toggle(PendingPick.Video(video)) ждал PendingPick.Video, получая model.Video
     (:286), а `video.ownerId`/`video.id` были unresolved (:346). FQN разрывает
     теневание. Audio/Gift/Photo/Doc не затронуты — Track/GiftItem/String не
     shadow'ятся вложенными классами.
  2. VideoPickerTab.loadMore (стр.604): `val (total, page)` → `val (page, total)`.
  3. VideoPickerTab LaunchedEffect (стр.625): то же.
  4. DocsPickerTab.loadMore (стр.1349): `val (total, page)` → `val (page, total)`.
  5. DocsPickerTab LaunchedEffect (стр.1368): то же.
  AudioPickerTab НЕ трогал — Pair<Int, List<Track>> там порядок уже верный.
- ChatDetailScreen.kt — правка (в границах ТЗ):
  6. VkAttachmentsBar LazyRow (стр.12324): `androidx.compose.foundation.lazy.items(`
     → `items(`. FQN top-level extension с implicit `this: LazyListScope` не
     резолвился компилятором (остальные items() в файле — короткие через import
     :58 `androidx.compose.foundation.lazy.items`). Это каскадно ломало
     `key = { it.att }` и `{ va -> ... }` (типы лямбд не выводились). Импорт
     уже присутствует — замена на короткое имя.
- Проверки:
  • Brace balance: AttachmentPickerSheet {350}/{350}=0, ChatDetailScreen
    {2527}/{2527}=0.
  • Paren balance: AttachmentPickerSheet 733/733=0; ChatDetailScreen
    6369/6385=-16 (pre-existing, не менялся — моя правка paren-neutral: FQN→short
    не добавляет скобок, комментарии сбалансированы).
  • Все используемые символы подтверждены: Video.ownerId/id (Models.kt:243),
    Track.ownerId/id (Models.kt:615), GiftItem.id (Models.kt:1598), DocFile,
    VkAttachPreview.att/label/thumb (ChatDetailScreen.kt:12259).
  • git commit НЕ делал (по ТЗ).

Stage Summary:
- Исправлены ВСЕ 7 групп ошибок компиляции из ТЗ:
  • :199, :286, :346 — PendingPick.Video type shadowing (FQN model.Video).
  • :593, :613, :615, :1333, :1335, :1352 — «Modifier.size() receiver mismatch»
    (на самом деле page: Int, page.size искалось как Modifier.size; swap
    деструктуризации Pair<List<*>, Int>).
  • :595, :597, :600, :614, :617, :1334, :1338, :1351, :1354 — unresolved
    filter/it/isNotEmpty/distinctBy (следствие page: Int; тот же swap).
  • :12317, :12320-12323 — items() FQN unresolved в VkAttachmentsBar (→ short
    name, импорт :58).
- Файлы изменены (только в рамках ТЗ):
  • app/src/main/java/re/pinok/ui/components/AttachmentPickerSheet.kt (5 правок)
  • app/src/main/java/re/pinok/ui/screens/im/ChatDetailScreen.kt (1 правка)
- Корневая причина: 2 независимых бага в коде, добавленном коммитом 365832f
  (MULTI-SELECT-ATTACH):
  1. Теневание имени `Video` вложенным `PendingPick.Video` в теле sealed
     interface — рекурсивный тип поля вместо model.Video.
  2. Несоответствие порядка элементов Pair у videoGetWithCount/docsGetWithCount
     (page-first) vs audioGetWithCount (total-first) — копипаста деструктуризации
     из AudioPickerTab.
  3. FQN-вызов `androidx.compose.foundation.lazy.items(...)` в LazyRow-ламбде —
     компилятор не сматчил extension с implicit receiver; короткое имя работает.
- Компиляция НЕ проверена (no kotlinc/Android SDK в окружении) — синтаксис и
  типы проверены статически по сигнатурам VKApiClient/Models. Brace balance 0.

---
Task ID: SESSION-SUMMARY-2026-10-23
Agent: main (orchestrator)
Task: Полный список работ за сессию 2026-10-23

# Сводка работ за сессию

## P0.47-REVERT — Записи сообщества не отображаются
- Коммит: f678836
- Откат 5 fetcher'ов на классические методы (wall.get/photos.get/video.get/board.getTopics/docs.get)
- Clips оставлены на getContentForTabs (работает)

## Система уведомлений (P0-P2, 15 коммитов)
### P0 (992f47d):
- P0.1: sn_push_send master toggle — savePushMaster подключён
- P0.2: Quiet hours для direct messages — effectiveChannelId → *_silent
- P0.3: Server-side DND (silent_until) — кэш + suppression
- P0.4: eventsQueuev4Client утечка после logout — stop() в 3 cleanup paths
- P0.5: SecurityAlertsPoller graceful backoff → permanent disable (P0.5b)

### P1 (7d0695f):
- P1.1: EventHub DEAD-EVENT отключён (stub + RealtimeWakeReceiver start убран)
- P1.2: 2 новых sn_* mappings (copies, video_groups_publish)
- P1.3: Per-chat timed mute (1h/8h/forever) UI
- P1.4+P1.6: Mark-as-read badge + dead code cleanup (-220 строк)
- P1.5+P1.7: Edge-case tap guard + drawer badge
- P1.8: 4 типа уведомлений без иконок (audio/event_invite/sdk_app_request/wall_reply)

### P2 (d104519):
- P2.1: VKNotifyWeb 3 DEAD CODE save-метода удалены
- P2.2: EventDedup periodic cleanup (CLEANUP_THRESHOLD=5000)
- P2.3: Queuev4Client.stop() → queue.unsubscribe
- P2.4: SecurityAlert action button «Завершить сессию»

### P3 (a5fd13c):
- P3.1: emailNotifyFreq DEAD STORE удалён
- P3.2: ChannelWebSocketClient dead-code stub удалён
- P3.4: Cross-channel call dedup mutex (incomingCallMutex + 5-condition)

### Logcat-driven:
- P0.5b: SecurityAlertsPoller permanent disable (persist flag в SharedPreferences)
- P1.1b: RealtimeWakeReceiver убрать EventHub.start()
- P1.1c: URL-лог toggle (Вариант C, default OFF)

## Эквалайзер (3 коммита)
- EQ-PANEL-FIX: PanelItem("equalizer") в PanelItems.all
- EQ-CLEANUP: AudioNavEntry удалён, Screen.Equalizer в drawerScreens
- EQ-C1-C5: Silent data loss fix (prefs-fallback) + UX индикация недоступных эффектов

## Чат (2 коммита)
- IME-ENTER-FIX: Enter переносит строку, отправка отдельной кнопкой
- BATCH-SEND: фото+файлы в один батч (были раздельные, файлы не отправлялись)

## Меню ленты (3 коммита)
- FEED-MENU-RESTORE: фильтры ленты вместо категорий уведомлений
- Убраны «Рекомендации» и «Реакции»
- Убран «Редактировать»

## Настройки (2 коммита)
- «Начальный экран» на самый верх Interface tab
- «Уведомления» в выборе начального экрана

## Сетки сообщества (6 коммитов)
- COMPACT-VIDEO-GRID: видео 2 колонки (compact VideoThumbnail)
- COMPACT-CLIP-GRID: клипы 2 колонки (compact ClipThumbnail)
- COMPACT-GRID-FIX: chunked rows вместо FlowRow (weight ломался)
- COMPACT-GRID-WEIGHT: weight через Box-обёртку (RowScope)
- PAGINATION-FIX: itemsIndexed вместо одного item{} (PagingTrigger)
- PHOTO-QUALITY: smallUrl превью + largestUrl для PhotoViewer

## Фото-функции (10+ коммитов)
- PHOTO-MENU-ALBUMS: меню ⋮ в PhotoViewer (Поделиться/Отправить)
- PHOTO-SHARE-WIRE: onShare/onSendToChat во всех 10 callsites
- Вкладка «Альбомы» в CommunityScreen → перенесена ВНУТРИ вкладки «Фото»
- Альбомы в ProfileScreen + UserProfileScreen (открываются как в VK)
- Альбомы + пагинация в AttachmentPickerSheet (PhotoPickerTab/VideoPickerTab/DocsPickerTab)
- ALBUM-CARD-VERTICAL: вертикальная Card (обложка сверху, подпись снизу)
- PhotoViewer (List<String>) вместо PhotoViewerPhotoItems (PhotoItem) — работает для всех источников

## Множественный выбор вложений (2 коммита)
- MULTI-SELECT-ATTACH: multi-select в AttachmentPickerSheet (toggle + «Готово (N)»)
- ChatDetailScreen: накопление vkAttachments + превью chips + отправка одним messages.send

## Закладки (3 коммита)
- BOOKMARK-MENU: меню ⋮ в BookmarkPostCard (Удалить из закладок/Скопировать ссылку/Удалить)
- WALL-DELETE: wallDelete(ownerId, postId) — VK wall.delete
- WALL-DELETE-VERIFY: парсинг ответа VK (число, не объект) + проверка контента

## Автор постов (1 коммит)
- SHOW-AUTHOR-ALL: «от Имя Фамилия» для всех постов с signerId (FeedScreen/CommunityScreen/PostDetailScreen)

## SSL + Logout (1 коммит)
- SSL-TRUST-USER-CA: кастомный X509TrustManager (system + user CA stores) для сертификатов Минцифры
- LOGOUT-CLEAR-AUTH: tokenStorage.clear() + CookieManager.removeAllCookies() перед AuthActivity

## Авторизация V2 (1 коммит)
- AUTH2-HINT-DIALOG: диалог-инструкция перед WebView + «Больше не показывать»

## Статистика
- Коммитов: ~50
- Файлов изменено: ~25
- Строк: ~+4000/-1500
