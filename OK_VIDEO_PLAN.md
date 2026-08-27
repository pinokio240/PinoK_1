# План внедрения: OK.ru video + кросс-платформенное воспроизведение без рекламы

**Связанные документы:**
- `VK_IMPORT_API.MD §39` — детальный разбор OK player (HTML/JS/CSS/API/ads)
- `VK_IMPORT_API.MD §10.5` — дополнение по push-уведомлениям
- `WORKLOG_ARCHIVE.md` Task IDs OK-HTML-1, OK-JS-2 — анализ архива

## Контекст проблемы

VK-лента встраивает видео от одноклассников (OK.ru) через iframe
`https://ok.ru/videoembed/<movieId>`. Внутри iframe — Svelte Web Component
`<vk-video-player>` (v0.3.57) с Shadow DOM, MSE-pipeline, Adman-рекламой
(Mail.ru/VK Ads), Chromecast, PiP, rotate, loop, timeline hover-preview collage.

**Текущее состояние в приложении:**
- `VideoPlayerScreen` воспроизводит только VK-native видео (mp4/HLS/DASH из
  `video.files`).
- OK-видео в ленте показываются как `VideoOverlayPlayButton`, но при тапе
  открывается `VideoPlayerScreen` с `null` URL → «No video URL — ExoPlayer not
  created».
- `videoGetById` возвращает `files: null` для OK-crossposted видео (есть только
  `player` field с iframe embed URL).

**Цель:** воспроизводить OK.ru видео в нативном плеере (ExoPlayer), без рекламы,
с выбором качества, с сохранением позиции, с применением Fix #341 (HEVC filter).

## Карта содержимого архива (summary)

| Категория | Что найдено |
|-----------|-------------|
| HTML | VK host page + OK iframe `16108201904696.html` (296 строк) + ad-tag injector iframe |
| CSS | 1 external (`videoembed_ngdcro6l.css`) + 48 inline Svelte `<style>` blocks |
| JS | `one-video-player.js` (1.5MB) + `one-video-player-ui.js` (199KB) + OKVideo/VideoEmbed/StickyPlayer/EventBuses (RequireJS) |
| Метаданные | `data-options` JSON 6.5KB на `div.vid-card_cnt` (clipId, contentId, groupId, videos[], hlsManifestUrl, metadataUrl, admanMetadata, adLogic) |
| CDN | `ok8-8.vkuser.net` (video), `iv.okcdn.ru` (thumbnails), `st-ok.cdn-vk.ru` (static) |
| Реклама | Adman SDK (Mail.ru) — `ad.mail.ru/static/admanhtml/rbadman-html5.min.js` |
| Трекеры | Yandex Metrika 87663567, Mail.ru counter, gtmpx.com ad-tag injector |
| Cast | `cast_framework.js` + `cast_sender.js` (Chromecast) |
| P2P | WebRTC STUN `videostun.okcdn.ru:19302` (disabled в примере) |

## Карта API OK.ru

| Endpoint | Auth | Назначение |
|----------|------|-----------|
| `GET https://ok.ru/videoembed/<movieId>` | anonymous (no TKN) | HTML embed (парсинг `data-options`) |
| `POST https://api.mycdn.me/dk?cmd=videoPlayerMetadata` | TKN header | JSON metadata (нужен `OK.tkn.get()`) |
| `POST /dk?cmd=videoCommand&a=getVideoPlayerAttributes` | TKN | Player attributes |
| `GET https://ok8-8.vkuser.net/video.m3u8?...` | signed URL (sig+expires+srcIp) | HLS |
| `GET https://ok8-8.vkuser.net/?...&type=1&ct=6` | signed URL | DASH MPD |
| `GET https://ok8-8.vkuser.net/?...&type=0..5` | signed URL | Progressive MP4 |
| `POST https://ok8-8.vkuser.net/usr_login` | cookie `vdsig` | CDN auth |

## Меню/кнопки плеера (для Compose parity)

| Контрол | OK testid | Назначение | Реализовать |
|---------|-----------|-----------|------------|
| Play/Pause | `btn-play` | play/pause/replay (3-state) | ✅ уже есть |
| Volume | `btn-volume-horizontal` | mute + slider | ✅ уже есть |
| Settings | `btn-settings` | quality/speed menu | ✅ уже есть |
| Context menu | `btn-context-menu` | dropdown 7 items | ⬜ TODO |
| Fullscreen | `btn-fullscreen` | enter/exit | ✅ уже есть |
| OK logo | `one-btn_logo-ok` | external link | ⬜ опционально |
| Timeline preview | `tooltip-wrapper` + `timeline-preview` | hover collage | ⬜ TODO |
| Loop | `video-loop` (context) | toggle repeat | ⬜ TODO |
| PiP | `pip` (context) | picture-in-picture | ⬜ TODO |
| Rotate | `rotate` (context) | rotate 90° | ⬜ TODO |
| Copy link | `ok_context_share` (context) | share URL | ⬜ TODO |
| Copy time-link | `ok_context_copy-link` (context) | share URL+?time=N | ⬜ TODO |
| Debug info | `debug-info` (context) | dev overlay | ⬜ опционально |
| Thumb timer | `thumb-timer` | scrub-time indicator | ⬜ TODO |
| Hotkey helper | `hot-key-helpers-container` | keyboard feedback | ⬜ опционально |

## План работ (этапы)

### Этап 1 — Discovery: определение платформы видео (1-2 ч)

**Задачи:**
1. Расширить `Video` model: добавить `enum class VideoPlatform { VK, OK, YOUTUBE, EXTERNAL_IFRAME, UNKNOWN }` и поле `platform: VideoPlatform`.
2. В `VKApiClient.videoGetById` / `videoGetCatalogItem` — определить платформу:
   - `files != null && files.isNotEmpty()` → `VK`
   - `player` содержит `ok.ru/videoembed/` или `ok.ru/video/` → `OK`
   - `player` содержит `youtube.com/embed/` или `youtu.be/` → `YOUTUBE`
   - `player` содержит `iframe` (другой домен) → `EXTERNAL_IFRAME`
   - иначе → `UNKNOWN`
3. Парсинг OK `movieId` из URL: regex `ok\.ru/(?:videoembed|video)/(\d+)`.

**Файлы:**
- `data/model/Models.kt` — `VideoPlatform` enum + поле.
- `api/VKApiClient.kt` — парсинг в videoGetById.

**Критерий готовности:** для OK-видео `video.platform == OK`, `video.externalId == "16108201904696"`.

---

### Этап 2 — WebView fallback (минимальный MVP, 2-3 ч)

**Задачи:**
1. Создать `OkWebViewPlayer` composable — `AndroidView(WebView)`:
   - `settings.javaScriptEnabled = true`, `domStorageEnabled = true`, `mediaPlaybackRequiresUserGesture = false`.
   - Загружает `https://ok.ru/videoembed/<movieId>?autoplay=true&__ref=vk.mvk`.
2. Ad-blocking через `WebViewClient.shouldInterceptRequest`:
   - Блокировать `*.mail.ru/adman*`, `gtmpx.com/*`, `top-fwz1.mail.ru/*`, `mc.yandex.ru/metrika/*`.
3. JS stub для `AdmanHTML` (двойная защита):
   - `onPageFinished` → `evaluateJavascript("window.AdmanHTML = class { init(){} start(s){ if(this.onCompleted) this.onCompleted(); } onReady(){} onStarted(){} onPlayed(){} onPaused(){} onClosed(){} onSkipped(){} onClicked(){} onTimeRemained(){} onError(){} onCompleted(){} }; void(0);")`.
4. Layout: `Box(Modifier.fillMaxSize()) { AndroidView(factory = { WebView(context).apply { ... } }) }`.
5. Fullscreen: при `fullscreen` toggle — убрать aspectRatio, `WebView.layoutParams = MATCH_PARENT`.

**Файлы:**
- `ui/screens/videoplayer/OkWebViewPlayer.kt` (НОВЫЙ, ~150 строк).
- `ui/screens/videoplayer/VideoPlayerScreen.kt` — диспетчеризация по `platform`.

**Критерий готовности:** OK-видео в ленте открывается в WebView, без рекламы, с управлением (play/pause/fullscreen внутри WebView).

**Ограничения:** нет нативного quality menu, нет интеграции с `PlayerConnection`, нет `PlaybackPositionStore`.

---

### Этап 3 — Нативный OK player через парсинг metadata (4-6 ч)

**Задачи:**
1. Создать `OkVideoRepository`:
   - `suspend fun fetchMetadata(movieId: String): OkVideoMetadata?`
   - Стратегия 1 (предпочтительная): HTTP GET `https://ok.ru/videoembed/<movieId>` → парсинг `data-options` JSON из HTML.
   - Стратегия 2 (fallback): `POST https://api.mycdn.me/dk?cmd=videoPlayerMetadata` (если TKN доступен — иначе fallback на Strategy 1).
2. Парсинг метаданных:
   - `flashvars.metadata.videos[]` → `List<QualityOption>` (mapping `mobile→144p`, `lowest→240p`, `low→360p`, `sd→480p`, `hd→720p`, `full→1080p`, `quad→1440p`, `ultra→2160p`).
   - `flashvars.metadata.hlsManifestUrl` → HLS опция «Авто».
   - `flashvars.metadata.metadataUrl` → DASH (опционально, ExoPlayer adaptive).
   - `flashvars.metadata.movie.poster` → thumbnail.
   - `flashvars.metadata.movie.duration` → duration.
   - `flashvars.metadata.movie.title` → title.
   - `flashvars.metadata.collageInfo` → timeline preview sprite sheet.
3. Расширить `VideoPlayerScreen`:
   - `platform == OK` → `qualityOptions` строится из OK `videos[]`.
   - ExoPlayer играет `ok8-8.vkuser.net` MP4/HLS через `DefaultHttpDataSource.Factory` с VK User-Agent (OK CDN принимает).
   - Apply Fix #341: если HEVC не поддерживается — отфильтровать `full`/`quad`/`ultra` (они HEVC).
   - `PlaybackPositionStore` сохраняет/восстанавливает позицию (key: `ok_<movieId>`).
4. URL signing workaround:
   - OK URL подписаны `sig=` + IP-bound (`srcIp=<viewerIp>`).
   - Для воспроизведения: использовать URL как есть (OK сервер отдаёт, если `srcIp` в подписи совпадает с клиентским IP — для мобильных сетей это обычно работает, т.к. IP берётся из HTTP запроса).
   - Если 403: fallback на iframe embed (Этап 2).
   - Если `expires` истёк: re-fetch metadata через `OkVideoRepository`.
5. Ad-free гарантия: нативный ExoPlayer НЕ загружает Adman (JS-only SDK) → реклама не показывается by design.

**Файлы:**
- `api/OkVideoRepository.kt` (НОВЫЙ, ~250 строк).
- `data/model/Models.kt` — `OkVideoMetadata`, `OkQuality` модели.
- `ui/screens/videoplayer/VideoPlayerScreen.kt` — расширить для `platform == OK`.

**Критерий готовности:** OK-видео воспроизводится в нативном ExoPlayer, без рекламы, с выбором качества (6 уровней + «Авто» HLS), с сохранением позиции.

**Риски:**
- OK меняет подпись URL → fallback на WebView (Этап 2).
- IP-bound URL не работает с прокси/VPN → fallback на WebView.
- `srcIp` в подписи не совпадает с клиентским IP (NAT, carrier-grade NAT) → fallback.

---

### Этап 4 — YouTube integration (2-3 ч, опционально)

**Задачи:**
1. Определить YouTube `videoId` из `video.player` URL (`youtube.com/embed/<id>`, `youtu.be/<id>`).
2. Варианты воспроизведения:
   - **Вариант A (простой):** WebView с `youtube.com/embed/<videoId>` + ad-blocking (как Этап 2). Реклама YouTube НЕ блокируется полностью (AdSense).
   - **Вариант B (lib):** библиотека `androidx.media3:media3-exoplayer` + `YouTubeExtractor` (deprecated, но работает). Рекламы нет, но Extractor ломается при изменении YouTube internal API.
   - **Вариант C (Invidious/Piped):** self-hosted инстансы (invidious.io, piped.video) без рекламы. Нестабильно (инстансы падают).
3. Рекомендация: Вариант A (WebView) для MVP, с пометкой «Реклама YouTube не контролируется приложением».

**Файлы:**
- `ui/screens/videoplayer/YouTubeWebViewPlayer.kt` (НОВЫЙ, ~100 строк — re-use OkWebViewPlayer pattern).

**Критерий готовности:** YouTube-видео открывается в WebView, управление внутри WebView.

---

### Этап 5 — Cross-platform dispatcher (1 ч)

**Задачи:**
1. Создать `VideoPlatformRouter` composable:
   ```kotlin
   @Composable
   fun VideoPlatformRouter(video: Video, ...) {
       when (video.platform) {
           VK, OK -> VideoPlayerScreen(video, ...)  // нативный ExoPlayer
           YOUTUBE -> YouTubeWebViewPlayer(video, ...)
           EXTERNAL_IFRAME -> OkWebViewPlayer(url = video.player, ...)
           UNKNOWN -> UnsupportedVideoPlaceholder(onOpenInBrowser = { ... })
       }
   }
   ```
2. Заменить вызовы `VideoPlayerScreen` в `SovaNavHost` на `VideoPlatformRouter`.

**Файлы:**
- `ui/screens/videoplayer/VideoPlatformRouter.kt` (НОВЫЙ, ~80 строк).
- `ui/navigation/SovaNavHost.kt` — заменить вызовы.

**Критерий готовности:** единая точка входа в видеоплеер, диспетчеризация по платформе.

---

### Этап 6 — UI/UX parity с OK (3-4 ч, опционально)

**Задачи:**
1. **Rotate control** — `Box(Modifier.graphicsLayer { rotationZ = rotation })` на `PlayerView`. Кнопка в context menu.
2. **Loop toggle** — `exoPlayer.repeatMode = Player.REPEAT_MODE_ONE`. Кнопка в context menu.
3. **PiP** — `enterPictureInPictureMode()` на API 26+. Кнопка в context menu.
4. **Timeline hover-preview** — парсинг `collageInfo.url` (sprite sheet). Кастомный `Timeline` с `onValueChange` → показать `AsyncImage` из collage по координате `(idx % tileWidth) * tileW, (idx / tileWidth) * tileH`.
5. **Context menu** — `DropdownMenu` с 7 пунктами (как OK: share, copy-link-with-time, pip, loop, rotate, save-debug, debug-info).
6. **Hotkey helpers** — `Modifier.onKeyEvent` + overlay с иконкой (2× scale animation) для `k`/`m`/`f`.
7. **Thumb timer** — overlay с `currentPosition` + 3-bar equalizer animation во время scrub.

**Файлы:**
- `ui/screens/videoplayer/VideoPlayerScreen.kt` — добавить context menu + rotate + loop.
- `ui/screens/videoplayer/VKTimeline.kt` — расширить для hover-preview collage.
- `ui/screens/videoplayer/components/PipController.kt` (НОВЫЙ).
- `ui/screens/videoplayer/components/HotkeyOverlay.kt` (НОВЫЙ).

**Критерий готовности:** UI/UX плеера соответствует OK player (rotate, loop, PiP, hover-preview, context menu).

---

### Этап 7 — Ad-free гарантии + UX (1 ч)

**Задачи:**
1. **Badge «Без рекламы»** в `VideoPlayerScreen` — для `platform == OK || VK` (иконка `shield_check`, subtitle «Реклама отключена»).
2. **YouTube disclaimer** — для `platform == YOUTUBE`: «Реклама YouTube не контролируется приложением».
3. **Логирование:** при загрузке OK metadata логировать `showAd` поле (для диагностики).
4. **Settings toggle:** «Внешние видео» → on/off (если off — OK/YouTube видео показываются как «Открыть в браузере»).

**Файлы:**
- `ui/screens/videoplayer/VideoPlayerScreen.kt` — badge.
- `data/local/SovaPrefs.kt` — `externalVideosEnabled: Boolean` (default true).

**Критерий готовности:** пользователь видит явный индикатор ad-free статуса.

---

## Метрики успеха

| Метрика | Цель | Измерение |
|---------|------|-----------|
| OK-видео воспроизводится нативно | 100% OK-видео в ленте | Ручной тест 10 видео |
| Реклама не показывается | 0 показов | Лог + визуально |
| Время до первого кадра | < 2 сек (vs iframe 3-5 сек) | `System.nanoTime` в onPlayerStateChanged |
| Quality selection работает | 6 уровней + «Авто» | Ручной тест menu |
| Position сохраняется | после restart продолжает с места | `PlaybackPositionStore.getPosition` |
| HEVC filter (Fix #341) применяется | 4K/1440/1080 скрыты на HEVC-unsupported | Лог `HevcSupport.filterKeys` |
| WebView fallback срабатывает | при 403/changed-sig | Лог «OK metadata failed → WebView» |

## Риски и митигации

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| OK меняет sig algorithm | Средняя | WebView fallback (Этап 2) — всегда работает |
| OK блокирует не-VK referer | Низкая | OkHttp interceptor `Referer: m.vkvideo.ru` |
| OK требует TKN для metadata | Высокая | Использовать HTML parsing (Strategy 1) — без TKN |
| IP-bound URL не работает | Средняя | Re-fetch metadata (если `expires` истёк) / WebView fallback |
| YouTube ad-blocking нестабилен | Высокая | Документировать как known limitation |
| OK embedding TOS violation | Низкая | Проверить terms; personal-use app допустимо |

## Приоритеты для внедрения

| Этап | Приоритет | Зависимости |
|------|-----------|-------------|
| 1. Discovery | P0 (блокирующий) | — |
| 2. WebView fallback | P0 (минимальный MVP) | Этап 1 |
| 3. Нативный OK player | P1 (full experience) | Этап 1, 2 |
| 4. YouTube | P2 (опционально) | Этап 1, 5 |
| 5. Dispatcher | P0 (обязательный) | Этап 1 |
| 6. UI/UX parity | P2 (улучшение) | Этап 3 |
| 7. Ad-free badge | P1 (UX clarity) | Этап 3 |

## Итоговая оценка времени

| Этап | Часы |
|------|------|
| 1. Discovery | 1-2 |
| 2. WebView fallback | 2-3 |
| 3. Нативный OK player | 4-6 |
| 4. YouTube | 2-3 |
| 5. Dispatcher | 1 |
| 6. UI/UX parity | 3-4 |
| 7. Ad-free badge | 1 |
| **Итого (P0+P1)** | **9-13 часов** |
| **Итого (всё)** | **14-20 часов** |

## Связанные документы (обновлены)

- `VK_IMPORT_API.MD §39` — полный разбор OK player (HTML/JS/CSS/API/ads/cross-platform).
- `VK_IMPORT_API.MD §10.5` — дополнение по push-уведомлениям (channels, LongPoll mapping, backfill, headless re-login, preview TODO, mute-state, battery optimization).
- `WORKLOG_ARCHIVE.md` Task IDs OK-HTML-1, OK-JS-2 — детальный анализ архива.
