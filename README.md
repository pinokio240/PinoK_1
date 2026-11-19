# PinoK

Неофициальный клиент VK для Android на Kotlin + Jetpack Compose. Авторизация через VK ID (web-токен `vk1.a.*`), доступ к ленте, сообщениям, музыке, видео, сообществам, плейлистам с поддержкой платного контента (VK Donut).

- **Пакет:** `re.pinok`
- **Версия:** 2.3.2 (`versionCode` 232)
- **Ветка:** `PinoK`

---

## Содержание
1. [Среда сборки и компиляторы](#среда-сборки-и-компиляторы)
2. [Версии библиотек](#версии-библиотек)
3. [Поддерживаемые устройства](#поддерживаемые-устройства)
4. [Возможности приложения](#возможности-приложения)
5. [Недостатки и ограничения](#недостатки-и-ограничения)

---

## Среда сборки и компиляторы

| Компонент | Версия |
|:---|:---|
| **Android Gradle Plugin (AGP)** | 9.1.1 (встроенная поддержка Kotlin, плагин `kotlin-android` НЕ нужен) |
| **Kotlin** | 2.4.0 |
| **Gradle** | 9.3.1 (`gradle-wrapper.properties`) |
| **JDK** | 21 (`JavaVersion.VERSION_21`, `jvmTarget` 21) |
| **compileSdk** | 36 (Android 16) |
| **minSdk** | 24 (Android 7.0 Nougat) |
| **targetSdk** | 36 (Android 16) |
| **Compose compiler** | встроен в Kotlin 2.4.0 (плагин `kotlin.compose`) |
| **Build features** | Compose enabled, BuildConfig enabled |
| **Variant** | `debug` (suffix `.debug`), `release` |

Сборка: `./gradlew :app:assembleDebug` (или `assembleRelease`). Требуется JDK 21 на машине сборки.

---

## Версии библиотек

Из `gradle/libs.versions.toml`:

### AndroidX / Compose
| Библиотека | Версия |
|:---|:---|
| Compose BOM | 2025.06.00 |
| Material3 | (из BOM) |
| Activity Compose | 1.11.0 |
| Lifecycle | 2.9.4 (runtime-ktx, viewmodel-compose, runtime-compose, process) |
| Navigation Compose | 2.9.4 |
| Fragment | 1.8.8 |
| Core KTX | 1.17.0 |
| Annotation | 1.9.1 |
| DataStore | 1.1.7 |
| WebKit | 1.14.0 |
| Browser | 1.9.0 |
| DocumentFile | 1.1.0 |
| Splashscreen | 1.2.0-rc01 |
| Security Crypto | 1.1.0-beta01 |
| Biometric | 1.4.0-alpha02 |
| Profileinstaller | 1.4.1 |

### Медиа / Графика
| Библиотека | Версия |
|:---|:---|
| Media3 (ExoPlayer) | 1.8.0 |
| Coil | 3.3.0 (compose, network-okhttp, gif) |
| Lottie Compose | 6.4.0 (VK TGS-стикеры — gunzip + Lottie JSON) |
| CameraX | 1.4.2 (запись клипов) |
| WebRTC | 1.3.10 (звонки, сборка Stream `org.webrtc.*`) |
| ffmpeg-kit | 1.0.7 (community fork `VineshChauhan24/ffmpeg-kit-android-16kb-lts-full-gpl`, NDK r27d, ffmpeg 6.0, Full-GPL, 16KB page-aligned) |

### Сеть / Сериализация / Крипто
| Библиотека | Версия |
|:---|:---|
| OkHttp | 4.12.0 (core, logging-interceptor, brotli) |
| Gson | 2.13.1 |
| Kotlinx Coroutines | 1.10.2 |
| BouncyCastle | 1.78.1 (jdk15to18 — Ed25519 верификация подписи обновлений) |

### Фон / Производительность
| Библиотека | Версия |
|:---|:---|
| WorkManager | 2.10.2 (periodic cookie sync каждые 6ч) |
| Baseline Profile | 1.4.1 (плагин + profileinstaller) |
| Macrobenchmark | 1.3.4 |
| UIAutomator | 2.3.0 |

> Единственная добавленная внешняя зависимость вне стандартного стека — `lottie-compose:6.4.0` (для анимированных TGS-стикеров VK). Остальные — стандартный AndroidX/Compose/Media3.

---

## Поддерживаемые устройства

| Параметр | Значение |
|:---|:---|
| **minSdk** | 24 (Android 7.0 Nougat) |
| **targetSdk** | 36 (Android 16) |
| **compileSdk** | 36 |
| **Архитектуры** | ARM64, ARMv7, x86, x86_64 (по умолчанию все; `splits.abi` не включён) |
| **Экраны** | все (normal / large / xlarge — телефон, планшет, fold) |

### Hardware features (все `required="false"`)
- `android.hardware.camera.any` / `android.hardware.camera` — камера (запись клипов, фото-вложения).
- `android.hardware.microphone` — микрофон (голосовые сообщения, звонки).
- `android.hardware.bluetooth` — Bluetooth (аудио-маршрутинг для звонков).

Приложение работает на устройствах БЕЗ камеры/микрофона (функциональность этих модулей недоступна, остальное работает).

### Разрешения (30 `<uses-permission>`)
INTERNET, ACCESS_NETWORK_STATE, CAMERA, RECORD_AUDIO, READ/WRITE_MEDIA (фото/видео/аудио), POST_NOTIFICATIONS, FOREGROUND_SERVICE (+ types: mediaPlayback, dataSync, phoneCall, microphone, camera), WAKE_LOCK, VIBRATE, RECEIVE_BOOT_COMPLETED, USE_BIOMETRIC, MODIFY_AUDIO_SETTINGS, BLUETOOTH_CONNECT, и др.

### Подтверждённые сценарии
- Телефоны Android 7.0–16.
- Планшеты (adaptive layouts через Compose `WindowSizeClass`).
- Aggressive ROM (MIUI/Xiaomi/Energy) — обработаны `stopForeground` исключения, foreground service types, battery exemption hint.

---

## Возможности приложения

### Авторизация (3 метода)
- **«Войти в аккаунт 2»** (по умолчанию) — vk-lite WebView-флоу (m.vk.ru, чтение `web_token` из localStorage). Токен `vk1.a.*`.
- **«Войти через VK»** (тумблер «Авторизация» в Разработке, по умолч. выкл) — VK ID WebView (app_id 6287487, remixsid → web_token exchange, silent refresh при смене IP).
- **«Войти через Яндекс / Chrome»** — внешний браузер OAuth implicit (oauth.vk.com/blank.html).
- **Сессия**: `CookieRefreshWorker` (каждые 6ч), `silentRefreshViaRemixsid`, Android Keystore для токена.
- **Multi-account**: 2 аккаунта (основной + authV2).

### Лента (Feed)
- Посты: текст (linkify, разворачивание), фото-сетка, видео-вложения, аудио, опросы, ссылки, репосты (copy_history), реакции.
- **Видео-карточки**: `type:"video"` из `owners.getMainTab` (пост, состоящий из видео) — восстановлены.
- **Donut (платный контент)**: бейдж «Платный пост · 299 ₽/мес» (цена из `video.restriction.button.title`), замок на превью, paywall-заглушка при открытии.
- Закреплённые посты, дедупликация, сортировка по дате.

### Сообщения (IM)
- Чаты: текст, фото (мульти до 10, delay 300мс между загрузками для VK rate-limit), файлы (стриминговая загрузка Uri→VK без temp-копий), голосовые (audio_message), видео-сообщения (кружки), стикеры (вкл. анимированные TGS/Lottie).
- Вложения через AttachmentPickerSheet с поиском (Music/Video серверный, Docs локальный).
- Редактирование комментариев в ленте, forward, reply.
- Уведомления: канал `messages_priority` (`setBypassDnd`, high priority).

### Музыка
- Треки: HLS/DASH/прогрессив mp4, очередь, перемотка, скорость.
- **#AUDIO-INTRO-FIX**: детекция premature-end (`ctrl.duration < 80% × Track.duration`) → refresh URL через `audioGetById` (внутри AL-audio fallback) → `replaceMediaItem` — треки доигрывают даже при короткоживущих web-URL.
- **Плейлисты (полный CRUD)**: `audio.getPlaylists`, `getPlaylistById`, `createPlaylist`, `editPlaylist`, `deletePlaylist`, `followPlaylist`, `savePlaylistAsCopy` (clone), `addToPlaylist`, `removeFromPlaylist`, `reorder`, `setPlaylistCoverPhoto` (обложка).
- WRITE-операции через `web.api.vk.ru` (`forceWebGateway=true`) — web-токен требует web gateway.
- Артисты, альбомы, каталог (catalog_v2), поиск.
- Офлайн плеер (загрузка треков).

### Видео
- Плеер: HLS auto-promo для длинных, DASH (dash_sep/dash_webm/dash_webm_av1), fMP4 HLS, first_frames постеры.
- **Серии/плейлисты**: `Video.albumId/albumTitle`, `setMediaItems` + SkipPrev/Next, overlay «Эпизод N/M».
- **PiP**: отдельная `VideoPipActivity` (singleTask, supportsPictureInPicture). Back-стрелка/системный back при воспроизведении → минимизация в PiP (не закрытие). Return из fullscreen PiP → возврат в пост (`returnToApp`).
- VK ID SDK QR-логин (onCreateWindow для `window.open`).
- AL-audio fallback, http400FreshAttempts, LiveConfiguration для live.

### Сообщества
- Стена: `owners.getMainTab` (cursor-based через `start_from`/`next_from`) на ВСЕХ страницах — donut-посты везде. Fallback на `wall.get` (offset-based).
- Сортировка `sortedByDescending { it.date }` (newest-first).
- Вкладки: Фото (альбомы), Видео, Клипы (short_videos через getContentForTabs), Обсуждения, Участники.
- **Админка**: настройки, статистика, люди, ссылки, приглашения, отложенные/предложенные посты, адреса.
- Поиск по постам (wall.search через web gateway).

### Профиль
- Просмотр/редактирование, обложка (`uploadCoverMultipart` — стриминг UriStreamRequestBody), аватар, друзья, подписки, закладки, документы.

### Клипы
- Вертикальный pager, запись клипов (CameraX), музыка клипа (music_info), nearest_clips.

### Прочее
- **Поиск**: глобальный (люди, сообщества, новости, музыка, видео, плейлисты).
- **Закладки**: посты, фото, видео, статьи.
- **Документы**: загрузка/просмотр.
- **Браузер**: внутренний WebView (Chrome UA, viewport, zoom) + открытие во внешнем.
- **Офлайн**: плеер для треков/историй/клипов.
- **Настройки**: вкладка «Разработка» (тумблеры: Авторизация, Авторизация 2, логирование, аудио-шлюз, network switch, AMOLED и др.), «Аудио» (6 browser headers для web gateway), «Безопасность» (biometric, backup, crypto), «О приложении» (versionCode).
- **Обновления**: ed25519-подпись манифеста (BouncyCastle), скачивание APK.
- **StoryViewer**: pinch-zoom, swipe между историями.

---

## Недостатки и ограничения

### Auth / Токены
- **Web-token `vk1.a.*`** (не VK Android SDK sig) → audio/video URL'ы короткоживущие (часы), требуют `#AUDIO-INTRO-FIX` (refresh через `audioGetById` при обрыве). Classic `wall.get` НЕ отдаёт donut-посты не-подписчику → стена сообществ только через `owners.getMainTab` (web gateway).
- **WebView-зависимость**: VK ID flow зависит от рендеринга системного WebView (на агрессивных ROM / устаревшем WebView — белый экран, требуется `FixedInputWebView` + safety-net reload).
- **Multi-account**: только 2 аккаунта (основной + authV2), не более.
- **Captcha**: не автоматизирована — требует ручного ввода в WebView.

### Медиа
- **ffmpeg-kit full-gpl** (~30–40 MB) — большой размер APK (вместо audio-only ~15–20 MB). Нет `splits.abi`.
- **PiP**: `VideoPipActivity` в отдельном task (`singleTask`) — при back из fullscreen возвращается в app через `getLaunchIntentForPackage`, не через back-stack navigation.
- **JS-диалоги** во внутреннем браузере — default system `AlertDialog` (не стилизованы под Material3, нет анти-спам чекбокса).
- **Audio broadcast** «сейчас слушаю» — только при включённом `isAudioBroadcastActive`.

### Контент
- **Donut-видео**: открываются с paywall-заглушкой (нельзя воспроизвести без подписки) — это намеренно (честный paywall), но пользователь не может посмотреть превью.
- **Видео в группах**: только через `owners.getMainTab` — если метод не поддерживается (старые группы / профиль), fallback на `wall.get` БЕЗ видео-постов.
- **Stories**: нет загрузки/создания (только просмотр).
- **VK Mini Apps**: не все проверены (HAR отмечает «отдельный тест всех Mini Apps не подтверждён»).

### UI / UX
- **Internal browser**: нет вкладок, истории, закладок браузера. Нет permission prompt для Notification API.
- **Compose-only**: нет XML-лендинга для старых устройств (minSdk 24, но Compose на Android 7 может тормозить на сложных экранах).
- **AMOLED-режим**: открытая задача (заявлен, не завершён).

### Инфра
- **Нет unit/UI тестов** в репозитории (только `check-nested-comments.py` статический анализатор).
- **Нет CI** для сборки/теста (только GitHub Actions для релизного APK подписания).
- **Секреты**: `VK_CLIENT_SECRET` (hHbZxrka2uZ6jB1inYsH) в `build.gradle.kts` — публичный (VK Android client), но приложение имперсонифицирует VK Android app.
- **Размер APK**: большой из-за ffmpeg-kit full-gpl + WebRTC + всех ABI в одном APK.

---

## Структура проекта

```
PinoK_1/
├── app/                          # Основное приложение (re.pinok)
│   ├── src/main/java/re/pinok/
│   │   ├── api/                  # VKApiClient, VKEndpoints, VkSigner, UriStreamRequestBody
│   │   ├── auth/                 # AuthActivity, LandingScreen, VkAuthWebViewScreenV2, auth2/, exchange/
│   │   ├── media/                # VideoDownloadManager, PlaybackPositionStore, PlayerConnection
│   │   ├── ui/
│   │   │   ├── components/       # PostCard, VideoThumbnail, PagedListState, AttachmentPickerSheet
│   │   │   ├── screens/          # feed, im, music2, videoplayer, community, profile, settings, ...
│   │   │   ├── videoplayer/      # VideoPipActivity, VideoPlayerScreen, VideoPlatformRouter
│   │   │   └── navigation/       # SovaNavHost, Screen, PanelItems
│   │   ├── mods/network/         # OkHttp interceptors, VkCookieJar
│   │   ├── service/              # VideoPlaybackService, FCM
│   │   └── updater/              # ed25519 APK updater
│   └── build.gradle.kts
├── core/
│   ├── data/                     # SovaPrefs, Models (Post, Video, Audio, ...)
│   ├── common/                   # BuildStamp, общие утилиты
│   └── media/                    # VideoPipController (media module)
├── gradle/libs.versions.toml     # Version catalog
├── prisma/schema.prisma          # (если используется)
└── worklog.md                    # История изменений (обновляется в каждом коммите)
```

---

## Сборка

```bash
# Требуется JDK 21
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:assembleRelease
# Требует keystore (release signing config)
```

Проверка кода: `bun run lint` (ESLint) или `./gradlew :app:lint`.
Статический анализатор KDoc: `python3 scripts/check-nested-comments.py`.
