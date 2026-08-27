# Карта ленты VK (m.vk.ru) — исследование архива `лента.zip`

> Источник: `/home/z/my-project/upload/лента.zip` — сохранённая веб-страница
> `Лента.html` (3.7 МБ, 18591 строка) + `Лента_files/` (46 JS, 20 CSS, 113 JPG,
> 9 PNG, 1 `getVideoPreview` JPEG 720×405).
>
> Дата исследования: 2026-07-18 (UTC+3, Europe/Moscow).
> Связанные документы: `VK_IMPORT_API.MD` (обновлён ЧАСТЬЮ 20),
> `STORY_VIDEO_CACHE_PLAN.md` (план внедрения story video cache).

---

## ЧАСТЬ A. КАРТА DOM ЛЕНТЫ

### A.1. Корневая структура

```
<body>
  <div id="app" class="vk-app">                    ← AppRoot
    <nav data-testid="leftmenu">                   ← левое меню (§A.2)
    <div role="feed" data-testid="feed-items">     ← виртуализированный контейнер ленты
      <div data-testid="post" ...>                 ← пост (§A.3)
      ...
      <div data-testid="grid-horizontalscroll">    ← блок Stories (§A.4)
</body>
```

### A.2. Левое меню (`leftmenu`)

| testid | Count | Элемент | Назначение |
|---|---|---|---|
| `leftmenu` | 1 | `<nav>` | корень левого меню (18 пунктов) |
| `leftmenuitem` | 18 | `<li>` | пункт меню |
| `leftmenuitem-label` | 18 | `<div>` | контейнер label |
| `leftmenuitem-text` | 18 | `<span>` | видимый текст |
| `leftmenuitem-counter` | 1 | `<badge>` | badge непрочитанных (уведомления/сообщения) |

### A.3. Пост (`data-testid="post"`) — полная DOM-карта

Один пост (`Лента.html`, первый пост):

```html
<article data-testid="post"
         data-post-id="-227142522_3275"           ← owner_id_post_id (signed owner = группа)
         data-post-nesting-lvl="0"                 ← уровень вложенности (0 = корневой)
         data-spa-post-legacy-selector="1">        ← SPA-селектор для навигации
  <div class="vkuiDiv__host" style="padding: 8px;">

    <!-- HEADER: аватар + имя + дата + меню -->
    <div data-testid="post-header"
         class="vkit-OaLCik vkuiFlex__host"
         style="--vkui_internal--row_gap: 8px; --vkui_internal--column_gap: 8px;">

      <a data-testid="post-header-avatar"
         class="vkuiAvatar__host"
         href="https://m.vk.ru/vkvideokids"
         data-allow-link-onclick-web="1"
         style="width: 36px; height: 36px;">
        <img class="vkuiImageBase__img vkuiImageBase__imgObjectFitCover"
             width="36" height="36" .../>
      </a>

      <h3 data-testid="post-header-title"
          class="vkit-j4HGvP vkuiFlex vkuiHeadline__level1 vkuiTypography__weight3">
        <!-- имя + верификация + дата в одном Flex -->
      </h3>
    </div>

    <!-- КОНТЕНТ: текст + вложения -->
    <div data-testid="post-content-container"> ... </div>

    <!-- PRIMARY ATTACHMENT (главное вложение) -->
    <div data-testid="primary-attachment-interactive-wrapper"
         data-list="c1009eb5d720186bf0"            ← tracking list ID
         data-video="-43618728_456358682"          ← owner_id_video_id (видео-вложение)
         data-duration="240"                       ← длительность в секундах (4:00)
         data-track-code="video_301415bcz2OxHmGGUiTgaLcwOoIaS_M4FQ3o...">
      <div data-testid="primary-attachment-image-content">
        <div data-testid="primary-attachment-photo"> ... </div>
        <button data-testid="videooverlay-playbutton"> ▶ </button>
      </div>
    </div>

    <!-- MEDIA GRID (несколько фото/видео) -->
    <div data-testid="media-grid-item">
      <img data-testid="media-grid-image" .../>
    </div>

    <!-- ТЕКСТ-обёртка (показать ещё) -->
    <div data-testid="showmoretext">
      <div data-testid="showmoretext-in"> ... зажатый текст (3 строки) ... </div>
      <a data-testid="showmoretext-after">Показать полностью</a>
    </div>

    <!-- ДАТА -->
    <a data-testid="post_date_block_preview"> ... </a>

    <!-- КОНТЕКСТНОЕ МЕНЮ -->
    <button data-testid="post_context_menu_toggle"> ⋯ </button>

    <!-- FOOTER: лайк / коммент / поделиться -->
    <div>
      <button data-testid="post_footer_action_like"> ♥ </button>
      <button data-testid="post_footer_action_comment"> 💬 </button>
      <button data-testid="post_footer_action_share"> ↗ </button>
    </div>
  </div>
</article>
```

#### A.3.1. Полный список testids поста

| testid | Count | Назначение |
|---|---|---|
| `post` | 5 | корневой `<article>` поста |
| `post-header` | 5 | шапка (аватар + имя + дата) |
| `post-header-avatar` | 5 | аватар автора (36×36, `vkuiAvatar`) |
| `post-header-title` | 5 | имя автора (`h3.vkuiHeadline__level1`) |
| `post-content-container` | 5 | контейнер контента (текст + вложения) |
| `post_date_block_preview` | 5 | дата поста |
| `post_context_menu_toggle` | 6 | кнопка контекстного меню (⋯) |
| `post_footer_action_like` | 5 | кнопка лайка |
| `post_footer_action_comment` | 4 | кнопка комментария |
| `post_footer_action_share` | 5 | кнопка поделиться |
| `primary-attachment-interactive-wrapper` | 6 | обёртка главного вложения |
| `primary-attachment-image-content` | 6 | image-content внутри обёртки |
| `primary-attachment-photo` | 5 | фото-вложение |
| `media-grid-item` | 4 | ячейка media-grid (несколько фото) |
| `media-grid-image` | 4 | изображение в media-grid |
| `videooverlay-playbutton` | 2 | play-36 иконка на видео |
| `showmoretext` | 5 | внешний «показать ещё» |
| `showmoretext-in` | 5 | зажатый текст (3 строки) |
| `showmoretext-after` | 5 | ссылка «Показать полностью» |

#### A.3.2. `data-*` атрибуты поста

| Атрибут | Формат | Пример | Назначение |
|---|---|---|---|
| `data-post-id` | `{owner_id}_{post_id}` | `-227142522_3275` | глобальный ID поста (signed owner = группа) |
| `data-post-nesting-lvl` | int | `0` | уровень вложенности (0=корень, 1+ для комментов) |
| `data-spa-post-legacy-selector` | `1` | `1` | SPA-совместимый селектор |
| `data-video` | `{owner_id}_{video_id}` | `-43618728_456358682` | видео-вложение (только для video-attachment) |
| `data-duration` | int (секунды) | `240` | длительность видео |
| `data-list` | hex string | `c1009eb5d720186bf0` | tracking list ID (аналитика показов) |
| `data-track-code` | string | `video_301415bc...` | analytics track code (уникален для показа) |
| `data-skiponclick` | `1` | `1` | не триггерить onClick-обработчик (для аватаров и т.п.) |
| `data-index` | int | — | индекс элемента в списке |
| `data-id` | string | — | локальный ID |
| `data-link` | `#share-{provider}` | `#share-vk` | share-провайдер (vk/facebook/telegram/twitter) |
| `data-type` | string | — | тип элемента |
| `data-section` | string | — | секция |
| `data-role` | string | — | ARIA-роль override |
| `data-alias` | string | `300x250 Ad 1` | алиас (реклама) |
| `data-openuri` | string | `\|BTN_URL\|` | URI для открытия (ads) |
| `data-allow-link-onclick-web` | `1` | `1` | разрешить переход по ссылке |

### A.4. Блок Stories (`grid-horizontalscroll`)

```html
<div data-testid="grid-horizontalscroll" class="vkit-Os7reT">
  <!-- creator (Моя история) -->
  <div data-testid="stories_creator" role="button" tabindex="0"
       data-skiponclick="1"
       class="vkuiHorizontalCell__host">
    <div class="vkuiHorizontalCell__image">
      <div class="vkuiAvatar__host" style="...">
        <!-- синий круг с "+" -->
      </div>
    </div>
    <div class="vkuiHorizontalCell__title">История</div>
  </div>

  <!-- истории от пользователей/сообществ (86 шт.) -->
  <div data-testid="stories-owner-{owner_id}"
       role="button" tabindex="0">
    <div class="vkit-mYAGF8 vkuiFlex">                ← RichAvatar wrapper
      <div class="vkit-G3wPyM">                        ← ring container
        <div class="vkit-1DNIlg richavatar-outline-accent">  ← градиент (непросмотрено)
          <!-- ИЛИ: vkit-VusC32 richavatar-outline-gray">    ← серое (просмотрено) -->
        </div>
        <div class="vkit-Oz2DdY">                      ← inner avatar
          <img src="...photo100.jpg" .../>
        </div>
      </div>
    </div>
    <div class="vkuiHorizontalCell__title">{name}</div>
  </div>
</div>
```

**Формат `stories-owner-{owner_id}`**:
- `stories-owner523549648` — **положительный** owner_id (без дефиса → пользователь)
- `stories-owner-99864184` — **отрицательный** owner_id (с дефисом → сообщество)

#### A.4.1. RichAvatar классы

| Класс | testid | Count | Назначение |
|---|---|---|---|
| `richavatar-outline-accent` | `richavatar-outline-accent` | 98 | синее градиентное кольцо (непросмотрено) |
| `richavatar-outline-gray` | `richavatar-outline-gray` | 1 | серое кольцо (просмотрено) |
| `vkit-mYAGF8` | — | — | RichAvatar wrapper (Flex) |
| `vkit-G3wPyM` | — | — | ring container |
| `vkit-1DNIlg` | — | — | accent ring (голубой→фиолетовый градиент) |
| `vkit-VusC32` | — | — | gray ring (просмотрено) |
| `vkit-Oz2DdY` | — | — | inner avatar (фото 58dp) |

### A.5. Поиск (`search_top_input`)

| testid | Count | Назначение |
|---|---|---|
| `search_top_input` | 2 | `<input type="search">` в шапке |
| `quicksearch-portal` | 2 | портал для dropdown поиска |

---

## ЧАСТЬ B. КАРТА API

### B.1. Домены (`window.vk.apiConfigDomains`)

```json
{
  "domain": "m.vk.ru",              ← основной домен (UI)
  "apiDomain": "web.api.vk.ru",     ← API gateway (методы VK API)
  "loginDomain": "login.vk.ru",     ← авторизация (web_token, exchange)
  "connectDomain": "id.vk.ru"       ← OAuth connect (exchange_token/hash)
}
```

**API gateway pattern**: `https://web.api.vk.ru/method/{method}?{queryParams}`

### B.2. CDN хосты для видео

| Хост | Назначение |
|---|---|
| `m.vkvideo.ru` | основной видео-домен (`window.vk.vkVideoDomain`) |
| `vkvideo.ru` | десктопный видео-домен |
| `userapi.com` | CDN для файлов (mp4, превью) |
| `vk.me` | альтернативный CDN |
| `vkontakte.ru` | legacy CDN |

### B.3. Ключевые API методы (из JS-бандлов)

> Источник: анализ 46 минифицированных JS-бандлов. Паттерн VK web —
> `ApiNamespace` base class с `namespace` getter + `makeMethod()`.

#### B.3.1. Stories (критично для кэширования)

| Метод | Назначение | Возвращает |
|---|---|---|
| `stories.get` | получить истории (extended=1) | `{items:[{owner_id, has_unseen, stories:[...]}]}` |
| `stories.getById` | получить историю по ID | `Story` с полным `video.files` |
| `stories.getVideoUploadServer` | URL для загрузки story video | `upload_url` |
| `stories.markSeen` | отметить просмотренной | `{}` |
| `stories.markSkipped` | отметить пропущенной | `{}` |
| `stories.view` | side-effect просмотра (stats) | `{}` (fire-and-forget) |

**Story type enum** (из JS): `PHOTO="photo"`, `VIDEO="video"`,
`LIVE_ACTIVE="live_active"`, `LIVE_FINISHED="live_finished"`.

**Story video payload** (в `stories.get` ответе, поле `video`):
```json
{
  "video": {
    "duration": 15,
    "video_files": {              ← предпочтительное поле (парсер читает первым)
      "mp4_144": "https://...mp4",
      "mp4_240": "https://...mp4",
      "mp4_360": "https://...mp4",
      "mp4_480": "https://...mp4",
      "mp4_720": "https://...mp4",
      "hls":     "https://...m3u8"
    },
    "files": { ... },            ← legacy fallback
    "player": "<html>..."         ← HTML-фолбэк
  }
}
```

#### B.3.2. Video (общее)

| Метод | Назначение |
|---|---|
| `video.get` | получить видео-объект (с `trailer.mp4_240..mp4_1080`) |
| `video.getPlayerConfig` | **возвращает playable CDN URLs** (не реализован в app) |
| `video.getWebToken` | auth token для web video player |
| `video.getStatsToken` | токен для статистики просмотров |
| `video.getUVStatsToken` | UV-статистика |

#### B.3.3. Feed

| Метод | Назначение | Параметры | Возвращает |
|---|---|---|---|
| `newsfeed.getFeed` | основная лента | `start_from` (pagination cursor) | `{items, groups, profiles, stories, ads, next_from}` |
| `newsfeed.getFeedExp` | экспериментальная лента | `start_from` | то же |
| `shortVideo.getRecom` | клипы/short-video | `page_anchor` | список клипов |

**Feed sections** (`?section=` на `/feed`): `top`, `recent`, `recommended`,
`news`, `photos`, `articles`, `videos`, `audios`, `clips`, `stories`,
`narratives`, `subscribed`, `likes`, `mentions`, `friends`, `groups`,
`widgets`, `search`, `market`, `podcasts`, `notifications`, `people`,
`online`, `services`, `statuses`, `genre`, `games`, `channels`,
`recommendations`, `communities`, `collection`, `installed`, `requests`.

#### B.3.4. Auth (web_token flow)

| URL | Назначение |
|---|---|
| `login.vk.ru/?act=web_token` | получить access_token (CORS + credentials) |
| `login.vk.ru/?act=connect_exchange_token` | OAuth exchange |
| `login.vk.ru/?act=connect_exchange_hash` | hash exchange |
| `al_video.php` | token-fetch для видео на vkvideo.ru |

#### B.3.5. Прочие namespaces (18 всего)

`AudioApi`, `StoriesApi`, `VideoApi`, `NewsfeedApi`, `WallApi`, `PhotosApi`,
`UsersApi`, `GroupsApi`, `MessagesApi`, `AccountApi`, `FaveApi`, `AppsApi`,
`StatsApi`, `LikesApi`, `ShortVideoApi` (clips), `NotificationsApi`,
`ExecuteApi` (batching), `AuthApi`.

**Execute batching**: `method:"execute", params:{code:"return [...];"}` —
мультивызов в одном запросе.

### B.4. `window.vk` конфиг (полная структура)

```json
{
  "id": 171093180,                         ← текущий user ID
  "age": 34,
  "pabs": 609,
  "__domain": "vk.ru",
  "main_platform": "mvk",                  ← mobile VK
  "platform": "mvk",
  "isWebView": false,
  "isCyrillic": true,
  "countryISO": "RU",
  "lang": 0,
  "rv": 2728,                              ← release version
  "ts": 1784309349,                        ← server timestamp
  "vkVideoDomain": "m.vkvideo.ru",
  "isVideoStandalone": false,
  "apiConfigDomains": { ... },             ← §B.1
  "versionInfo": {
    "static_hash": "8af801eb3b588c60f2e154f63346fab47c54c858b87509260210d870f11a4377",
    "release_version": 2728,
    "force_reload_version": 1
  },
  "preloadTabbarStaticConfig": {           ← какие JS предзагружать для вкладок
    "feed":  ["profile_redesign.js", "group_redesign.js", "video.js",
               "video_showcase.js", "short_video.js", "mail.js"],
    "video": ["feed.js", "stories_feed_block.js", "mail.js", "short_video.js"],
    "mail":  ["feed.js", "stories_feed_block.js", "video.js",
               "video_showcase.js", "short_video.js"],
    "clips": ["feed.js", "stories_feed_block.js", "mail.js", "video.js",
               "video_showcase.js"]
  },
  "statsMeta": {
    "platform": "mvk",
    "id": 171093180,
    "time": 1784309349,
    "hash": "B5ceoO0QngLndJeRcphtF9p7j3SjgcrTlqwtDR7a1ww",
    "reloadVersion": 42
  },
  "logoutUrl": "https://login.vk.ru/?act=logout_mobile&hash=...&_origin=https%3A%2F%2Fm.vk.ru&reason=",
  "sw": {                                  ← Service Worker
    "url": "/js/sw.js",
    "push_hash": "625da888fbe90ab642",
    "stat_hash": "b7b2b36ee6a734a6f2"
  },
  "wsTransport": "https://stats.vk-portal.net",  ← WebSocket stats
  "pe":    { ... 523 feature flags ... },
  "toggles": { ... 41 toggles ... },
  "cfg":   { ... 26 configs ... },
  "static": {
    "js_stage", "css_stage", "flushed_assets", "domain",
    "nav_map", "assets", "async_assets", "init_entries"
  }
}
```

### B.5. Client-side кэширование (3 механизма из JS)

#### B.5.1. `OfflineAudioStorage` — IndexedDB (образец для story video cache)

- БД: `pwa_music_storage` (v1)
- Object stores: `tracks`, `users`, `playlists`, `tracks_by_users`,
  `tracks_by_playlists`, `users_by_playlists`
- Flow: `fetch(url) → blob → URL.createObjectURL → IDB.put`
- Events: `DOWNLOADING_TRACK_START/END/ERROR`, `REMOVING_TRACK_*`
- `QuotaExceededError` handling
- Companion Blob URL cache с `releaseAll()` revoke

> **Рекомендация для Android**: этот паттерн = наш `VideoDownloadManager`
> (file storage + `.meta` sidecar). IndexedDB ↔ Room/file, Blob ↔ File.

#### B.5.2. `VideoDownloadImpl` — anchor-click download

```js
const a = document.createElement("a");
a.href = href; a.download = `${title}.mp4`;
document.body.appendChild(a); a.click();
```

#### B.5.3. `apiPrefetchCache` — SSR hydration

- VK инжектит HTML-encoded JSON в `window.cur.apiPrefetchCache`
- Формат: `{method, version, request, response}` tuples
- `loadPrefetchCache(method, version, request)` — потребляет once
- In-memory LRU cache (`maxSize: 10485760` = 10 МБ) для whitelisted GET-методов
  включая `newsfeed.getFeed`, `shortVideo.getRecom`

### B.6. Story video playback (web)

- **Story player chunk**: webpack chunk `11313:"StoriesViewerService"`
  (файл `02ea25b12ae3f60b`) — динамически импортируется, НЕ в архиве
- **Video source**: plain MP4 (НЕ HLS). HLS только для audio.
- **URL resolution** (приоритет): `mp4_1080 → mp4_720 → mp4_480 → mp4_360 → mp4_240 → mp4_144 → hls → player`
- **StoryPreviewMinSizes**: SMALL=150, MEDIUM=375, BIG=500, MAX=∞
- **Narratives**: коллекции историй с `narrative_id`, `cover_story_id`, `story_ids`
- **Clips**: отдельны от story videos, served via `shortVideo.getRecom` на `/clips`

### B.7. `audioUnmaskSource` (audio URL deobfuscation)

VK возвращает audio URL с literal `audio_api_unavailable` + `?extra=#` payload.
Нужно 4 трансформации:
1. `v=` — reverse строки
2. `r=` — caesar-shift по алфавиту `abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0PQRSTUVWXYZO123456789+/=`
3. `s=` — BigInt-seeded permutation, key = `vk.id XOR t`
4. `x=` — per-char XOR

> **Важно**: video URL НЕ обфусцированы этим способом. Story videos = прямые MP4.

### B.8. `getVideoPreview` endpoint

- Возвращает **бинарный JPEG** (720×405, baseline, JFIF)
- НЕ JSON
- Используется как превью для видео в ленте (lazy-loaded thumbnail)

---

## ЧАСТЬ C. СВОДКА НАХОДОК ДЛЯ ВНЕДРЕНИЯ

### C.1. Что уже есть в приложении (от RESEARCH-APP-1)

| Аспект | Статус | Локация |
|---|---|---|
| Story data model с `video: StoryVideo?` | ✅ | `Models.kt:821, 842-850` |
| `StoryVideo.files: Map<String,String>?` (mp4_*) | ✅ | `Models.kt:845` |
| `parseStory()` извлекает `video.video_files` | ✅ | `VKApiClient.kt:6088-6124` |
| `storiesGet()` возвращает видео с inline MP4 URL | ✅ | `VKApiClient.kt:5965` |
| `StoryViewerScreen` играет video stories (ExoPlayer) | ✅ | `StoryViewerScreen.kt:222-271` |
| URL resolution: mp4_720→480→360→240→144→hls | ✅ | `StoryViewerScreen.kt:223-228` |
| ExoPlayer lifecycle (per-story, remember+DisposableEffect) | ✅ | `StoryViewerScreen.kt:232, 274-278` |
| `stories.view` side-effect для unseen | ✅ | `StoryViewerScreen.kt:188-205` |
| `VideoDownloadManager` (образец для копирования) | ✅ | `media/VideoDownloadManager.kt` |
| `TrackDownloadManager.enqueueDownload(track, silent=true)` | ✅ | `media/TrackDownloadManager.kt` |
| `OfflineManagerScreen` (2 таба: Аудио/Видео) | ✅ | `ui/screens/offline/OfflineManagerScreen.kt` |
| `VideoPlayerScreen` file:// substitution pattern | ✅ | `VideoPlayerScreen.kt:301-302` |

### C.2. Чего НЕТ (gaps для story video cache)

1. ❌ `StoryVideoDownloadManager` — нет story-aware менеджера загрузок
2. ❌ `file://` URL substitution в `StoryViewerScreen` (всегда CDN URL)
3. ❌ Story ID namespace isolation (нужен prefix `s_` + dir `story_video_downloads/`)
4. ❌ Story TTL eviction (24h истории → авто-удаление)
5. ❌ URL-refresh hook for 403 (CDN URL истекают)
6. ❌ Tab «Истории» в `OfflineManagerScreen`
7. ❌ Auto-cache-on-play trigger (mirror `PlayerConnection` pattern)
8. ❌ `video.getPlayerConfig` API метод (не реализован, опционально)

### C.3. Риски внедрения (без поломки ленты)

1. **StoriesHolder cache invalidation** — не мутировать `Story` после parse
2. **ExoPlayer premature release** — compute `resolvedUrl` ONCE перед `remember`
3. **ID collision** — distinct dir + key prefix `s_${ownerId}_${storyId}`
4. **Cache size bloat** — cap (200 МБ LRU) или только по явному действию
5. **Story URL expiry mid-download** — 403 handler с re-fetch через `storiesGet()`
6. **`stories.view` on cached** — skip когда playing from local cache
7. **Foreground service overload** — batch enqueue, `silent=true` flag

---

## ЧАСТЬ D. ИСТОЧНИКИ

- `Лента.html` (3.7 МБ) — основной HTML
- `Лента_files/*.js` (46 файлов, ~6.8 МБ) — JS-бандлы
- `Лента_files/getVideoPreview` — JPEG 720×405 (бинарный endpoint)
- `window.vk` config (inline JSON в HTML)
- `/home/z/my-project/worklog.md` — записи RESEARCH-JS-1, RESEARCH-APP-1
