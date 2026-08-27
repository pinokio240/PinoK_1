# VK Web Audio — Reference Material

Источник: `css_js_Html.7z` (дамп сохранённых страниц m.vk.com / vk.com из браузера,
июнь 2026). 2076 файлов: HTML-страницы + сопутствующие JS/CSS/картинки.

Сюда скопированы **только audio-релевантные** файлы (2.6 MB), без тысяч картинок
и видеоплеера (`vendors~videoplayer*.js` 1.2 MB — это video, не audio).

## Структура

```
reference/vk_web_audio/
├── js/
│   ├── AudioCatalog.c3a3baf052cd18c6.js   (60 KB) — главная логика каталога музыки
│   ├── audio_catalog.e79a6e44d2308a91.js   (9.5 KB) — модуль каталога
│   ├── audio.fd1671935f0feb93.js           (375 B) — stub/loader
│   ├── audio_config_legacy.c3ba11e040077c62.js    (65 KB) — legacy config + i18n
│   ├── audio_config_overrides.*.js         (2–3 KB) — i18n overrides
│   ├── audio_onMediaAttachmentPlayer.js    (18 KB) — player для вложений
│   ├── audio_player_bottom.403c4dab3b5c4e8f.js    (337 B) — stub bottom player
│   ├── audio_player_mini.5dc713de9ffbca5f.js      (770 B) — stub mini player
│   ├── audio_postingPlayer.56c8f2e578c4cc57.js    (18 KB) — player для постинга
│   └── mvk-left-menu-player.bfc3c2c4bbd38555.js   (268 B) — i18n mini player
├── css/
│   ├── audio.db7b1cc29056918a5c6f.css           (132 KB) — стили audio-секции
│   ├── audio_player_bottom.377af502482d42ef0dbb.css (14 KB)
│   └── audio_player_mini.8b49f486b7129b7d4f4c.css  (2.5 KB)
└── pages/
    ├── Главная музыка.html        (1.1 MB) — главная страница музыки
    └── музыка Обзор.html          (1.2 MB) — раздел «Обзор» музыки
```

## Ключевые выводы (Fix #63)

### 1. `login.vk.ru` — НЕСУЩЕСТВУЮЩИЙ домен (главный баг)

VK web JS использует `login.vk.com` **110 раз** и **НИ РАЗУ** `login.vk.ru`
(проверено `grep -rohE "login\.vk\.[a-z]+" --include="*.js" .`).

В нашем `WebTokenAuth.kt` был fallback-список:
```kotlin
private val LOGIN_DOMAINS = listOf("https://login.vk.com", "https://login.vk.ru")
```
`login.vk.ru` не резолвится DNS → `UnknownHostException`. Retry-цикл перебирал
домены × app_id, и при любой ошибке на `login.vk.com` (401, invalid_request,
истёкший remixsid) падал на `login.vk.ru` с DNS-ошибкой, которая **затирала**
реальную причину (`lastError = e` в каждой итерации).

Пользователь видел:
```
Не удалось получить токен: Unable to resolve host "login.vk.ru"
```
вместо настоящей ошибки (например «get_anonym_token error: invalid_request»).

**Fix #63**: `login.vk.ru` убран из `LOGIN_DOMAINS`, `vkDomains` (AuthActivity),
`COOKIE_CHECK_URLS`. Добавлен `isNetworkError()` + `firstMeaningfulError` —
сетевые ошибки больше не затирают осмысленные HTTP/API ошибки.

> ⚠️ Остальные `.ru` домены РЕАЛЬНЫЕ: `vk.ru`, `m.vk.ru`, `id.vk.ru` (VK ID
> OAuth 2.1), `api.vk.ru` (mirror). Только `login.vk.ru` — фейк.

### 2. `app_id=7310670` — это iframe app_id, не auth

В HTML страниц музыки встречается `vk_app_id=7310670` (10 раз) — это
launch-параметр VK IFrame App для контекста m.vk.com. **НЕ** auth app_id.

Наши auth app_id (7879029 mobile web, 6287487 desktop) подтверждены дампом
`ВК.txt` и рабочие. **Не менять.**

### 3. Audio API методы (VK web)

Из `AudioCatalog.js` и HTML видны методы VK web audio API:
- `audio.get`, `audio.getById`, `audio.search`
- `audio.getAudiosByArtist`, `audio.getPlaylists`, `audio.getPlaylistById`
- `audio.getLyrics`, `audio.getCurrent`, `audio.getPosition`
- `audio.getCatalog` ( discovery-лента, для web-токенов)

`AudioCatalog.js` использует `offset` (4 упоминания) — пагинация через offset
поддерживается на уровне каталога.

### 4. Потоковое воспроизведение

VK web audio использует `streamingServiceUrl` (HLS/m3u8) для DRM-треков и
прямые `vkuseraudio.net` URL для обычных. Наш `PlayerService` (Media3 +
OkHttpDataSource с VK UA) и `PlayerConnection` (HTTP→HTTPS rewrite) —
архитектурно корректны. **Проблема была в auth, не в плеере.**

## Связь с Fix #62 (audio playback + infinite scroll)

Fix #62 добавил:
- ExoPlayer с OkHttpDataSource (VK User-Agent) в `PlayerService.kt`
- HTTP→HTTPS rewrite в `PlayerConnection.kt`
- Пагинацию `audio.get(offset=tracks.size)` в `MusicScreen.kt`
- `audioGet(count, offset)` в `VKApiClient.kt`

Fix #62 был **архитектурно прав**, но не мог заработать — auth падал на
`login.vk.ru` раньше, чем плеер вообще получал треки. Fix #63 разблокирует
auth → Fix #62 начнёт работать.

## Limitations (для web-токенов vk1.a.*)

- `audio.get` → error 3 (unknown method) → fallback на `audio.getCatalog`
- `audio.getCatalog` НЕ поддерживает offset → бесконечная лента для web-токенов
  выдаёт только первую страницу (catalog). Это ограничение VK API, не баг.
- Для Direct Auth токенов (`audio.get` работает) — пагинация работает полностью.

## Источник архива

`/home/z/my-project/upload/css_js_Html.7z` (31 MB, 2076 файлов).
Полная распаковка: `/home/z/my-project/upload/css_js_Html_extract/`.
