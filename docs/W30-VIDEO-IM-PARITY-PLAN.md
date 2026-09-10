# Волна 30 — план изученного и план внедрения: паритет видео-плеера и мессенджера с VK web

> Источники изучения: архив «Видео плеер.zip» (снапшот страницы видео VK web с
> data-testid-паттернами, файлы «видео.md» и «интеграция.план.md» из архива,
> UnifiedVideoPlayer.kt/VideoPlayerContainer.kt как референс), ТЗ пользователя
> (2026-09-10), тотальный обход текущего кода PinoK (HEAD cff4b0d4).
> Правила: API и кнопки БЕЗ ЗАГЛУШЕК; NULL-ЯВНО; документация и код — на git.

---

## ЧАСТЬ 1. ПЛАН ИЗУЧЕНОГО (что есть в VK web и в PinoK сейчас)

### 1.1 Паттерны VK web со страницы видео (снапшот, data-testid)

| data-testid | Элемент | VK API под капотом | Статус в PinoK |
|---|---|---|---|
| `video_page_like_button` | Лайк | likes.add/delete type=video | ✅ есть (Fix #383, VideoActionsRow) |
| `video_page_share_button` | Поделиться | messages.send + wall.repost + ссылка | ✅ есть (Fix #383, VideoShareSheet) |
| `video_page_add_to_my_playlist` | **«Добавить»** (add_24) | **video.add / video.delete** | ❌ **нет** → W30-2 |
| `video_page_more_button` | **«Ещё»** (more_horizontal_24) | fave.addVideo/removeVideo, video.report, скачивание | ❌ **нет** → W30-2 |
| `video_page_title`, `video_page_additional_info` | Название, просмотры | video.get extended | ✅ есть |
| `video_owner_container/subscribes` | Автор, подписка | groups.getById | частично (заголовок есть) |
| `videoplayer_pip_btn` | PiP | PiP-Activity | ✅ есть |
| `volume-slider` | Громкость | — | ✅ есть |
| `video-layout-scroll-top` | Наверх | — | ✅ есть (Fix #389) |
| comment-* (20 шт.) | Комментарии | video.getComments/createComment | ✅ есть (Fix #383, VideoCommentsSheet) |

Вывод: из футера страницы видео VK web в PinoK не хватает **«Добавить»** и **«Ещё»**
— в недривать при открытии КАЖДОГО видео (и стрип действий, и immersive-режим).

### 1.2 Поиск в разделе видео (ТЗ: «поиск не глобальный, нужно чтобы видео добавлялись»)

- `VKApiClient.videoSearch` (:8126) вызывает `video.search { q, count, extended, offset }`
  **БЕЗ `search_global=1`** → VK ищет в у́зком скоупе (свои/друзья), а не по всей базе.
  Это и есть «поиск не глобальный». → W30-API: добавить `search_global=1`.
- Результаты поиска (VideoScreen, фильтр :295 + дебаунс :303-322) рендерятся
  VKVideoCard без действия «Добавить себе» → видео из поиска нельзя сохранить.
  → W30-1: тумблер «Добавить себе» (videoAdd/videoDelete уже в клиенте :4122/:4134).

### 1.3 Диалоги: «Отметить непрочитанным» и «Включение-отключение уведомлений»

Проверка показала — **оба работают** с реальными API и полным UX-циклом:

| Действие | API | Где в UI | Состояние |
|---|---|---|---|
| Заглушить/Включить уведомления | `messages.setConversationPushSettings` (Fix #122, :2097) | долгий тап на диалоге → «Заглушить»/«Включить уведомления» (:1932); меню шапки чата (:2838, преф msgMute) | ✅ оптимистичный апдейт, откат при ошибке, синк MessageNotifier (Fix #285), Toast |
| Отметить непрочитанным/прочитанным | `messages.markAsUnreadConversation` (Fix #274, :2029) + `messages.markAsRead(force)` для снятия бейджа (#MARK-READ-REVERT) | долгий тап на диалоге (:1948) | ✅ оптимистичный бейдж, откат, Toast |
| Закрепить/Открепить (Fix #392) | локальный пин + best-effort API | долгий тап (:1914) | ✅ |

Пробел паритета: в **меню шапки чата** (kebab, ChatDetailScreen :2774) пункта
«Отметить непрочитанным» НЕТ (в VK web он есть в меню чата). → W30-1 добавить.

### 1.4 Плеер: стабильность качества/скорости при длительном воспроизведении

- Скорость: `exoPlayer.setPlaybackSpeed(rate)` (:1745) применяется к инстансу;
  при пересборке источника (смена качества :840-860, fallback codec :710-763,
  Lifecycle restart :963) ExoPlayer пересоздаётся/перезагружает источник →
  **скорость сбрасывается в 1.0** («скачки скорости»).
- Качество: выбор из qualityOptions (:456) подменяет URL источника; между
  пользовательскими сменами включён адаптивный ABR — на длинных сессиях
  **прыгает по битрейту** («скачки качества»).
- Фон: `LifecycleStartEffect` :969 — `onStopOrDispose { exoPlayer.playWhenReady = false }`
  → при уходе из активности воспроизведение СТАНАВЛИВАЕТСЯ, lock-screen плеера нет.
  У аудио есть эталон: `PlayerService` (media3 MediaSessionService, PlayerService.kt:77)
  с кастомной кнопкой в layout (Fix #138) — паттерн переиспользуем.

### 1.5 Lock-screen плеер для видео (ТЗ: «как аудио, без видео, прокрутка ±N сек»)

- Нужен foreground MediaSessionService для видео-ExoPlayer: уведомление MediaStyle
  БЕЗ видео (аудио продолжается), кнопки «−N сек / Play / +N сек» (custom layout),
  шаг N — настройка 5/10/15 сек (Настройки → Видео).
- Контракт-хуки из VideoPlayerScreen описаны в §2.4 (единый для W30-2 и W30-3).

### 1.6 API-инвентарь (что уже в VKApiClient — без новых заглушек)

| Метод | Готов | Использовать в |
|---|---|---|
| `video.add` / `video.delete` (:4122/:4134) | ✅ | W30-1 (поиск), W30-2 («Добавить») |
| `fave.add` / `fave.remove` type="video" (:6059/:6092) | ✅ | W30-2 («Ещё» → «В закладки») |
| `video.search` (:8126) | ✅ (+параметр W30-API) | W30-1 |
| `video.report` | ❌ **нет** | W30-API добавляет; W30-2 («Ещё» → «Пожаловаться») |
| `messages.markAsUnreadConversation` (:2029) | ✅ | W30-1 (меню шапки) |
| `VideoDownloadManager` | ✅ | W30-2 («Ещё» → «Скачать») |

---

## ЧАСТЬ 2. ПЛАН ВНЕДРЕНИЯ (волна 30)

### 2.0 W30-API (мейн-агент, до субагентов — VKApiClient.kt единолично)
1. `videoSearch`: + параметр `search_global = 1` (аргумент + WHY-комментарий).
2. Новый метод `videoReport(videoId, ownerId, reason: Int = 5): Boolean` —
   `video.report { owner_id, video_id, reason }`, false при offline/ошибке.

### 2.1 W30-1 — Мессенджер + глобальный поиск видео (ChatDetailScreen.kt, VideoScreen.kt)
1. Меню шапки чата: пункт «Отметить непрочитанным»/«Отметить прочитанным»
   (как в списке: markAsUnreadConversation + markAsRead(force) при снятии).
2. VideoScreen: результаты поиска — кнопка-тумблер «Добавить себе» (videoAdd →
   videoDelete), optimistic-иконка AddCircle→Check, откат при ошибке, Toast.
3. Критерий приёмки: поиск находит видео по всей VK; из результатов можно
   добавить/убрать видео у себя; в меню чата есть «непрочитанным».

### 2.2 W30-2 — Паритет футера видео-страницы + стабильность плеера (VideoPlayerScreen.kt)
1. VideoActionsRow и immersive-стрип: кнопка **«Добавить»** (add_24 → Check_24,
   videoAdd/videoDelete, optimistic + откат) и **«Ещё»** (DropdownMenu:
   «В закладки» faveAdd/faveRemove type="video"; «Скачать» VideoDownloadManager;
   «Пожаловаться» videoReport + confirm-диалог; «Копировать ссылку»).
2. Стабильность: скорость сохраняется в remember-стейт и reapplies после КАЖДОЙ
   смены качества/fallback/пересоздания источника; при выбранном пользователем
   качестве ABR пинится через trackSelectionParameters (maxVideoHeight/Bitrate
   по выбранной опции) — скачков качества между сменами нет.
3. Фон/lock-screen: подключение контроллера W30-3 (контракт §2.4): при onStop
   НЕ паузить (аудио продолжает играть), запускать foreground-сервис с
   MediaStyle-уведомлением; при onStart — возвращать.
4. Критерий приёмки: под каждым видео (и в immersive) есть «Добавить» и «Ещё»;
   скорость выживает смену качества; сворачивание приложения не глушит звук.

### 2.3 W30-3 — Сервис фон-воспроизведения видео + настройка шага (service/, SovaPrefs.kt, SettingsScreen.kt, AndroidManifest.xml)
1. Новый `VideoPlaybackService` (media3 MediaSessionService, паттерн PlayerService
   Fix #138): MediaSession поверх video-ExoPlayer (singleton-холдер), кастомная
   layout «−N сек | Play/Pause | +N сек» (custom commands seekTo), MediaStyle
   notification БЕЗ видео, stopSelf при detach/останове.
2. SovaPrefs: `video_seek_step_sec` (Int, default 10) — ключ + Snapshot + setter
   (паттерн notify_mode волны 29-d; DataStore-дефолты — прецедент).
3. SettingsScreen вкладка «Видео»: секция «Фоновое воспроизведение» — выбор шага
   прокрутки 5с/10с/15с (радио-строки, применяется немедленно).
4. AndroidManifest: регистрация сервиса (foregroundServiceType="mediaPlayback").
5. Критерий приёмки: свернутое приложение продолжает звук; на lock-screen —
   плеер без видео с рабочими кнопками ±N сек (шаг из настроек).

### 2.4 Контракт хуков VideoPlayerScreen ↔ VideoPlaybackService (ЕДИНЫЙ, правит оба)
```kotlin
// app/src/main/java/re/pinok/service/VideoPlaybackBus.kt (новый, W30-3)
object VideoPlaybackBus {
    fun onPlayerReady(context: Context, player: ExoPlayer, title: String)  // создать session
    fun onBackgrounded(context: Context)   // onStart-уход: startForegroundService
    fun onForegrounded()                   // возврат: stopForeground (не убивать звук)
    fun onPlayerReleased()                 // dispose плеера: stopSelf + release session
}
```
W30-2 вызывает хуки в точках Lifecycle (STOP/START) и создания/релиза ExoPlayer.

### 2.5 Не входит в волну (осознанно)
- Реклама (ТЗ: «кроме рекламы»); субтитры и live-чат VK (нет в скоупе parity-минимума);
  рекомендации под плеером (отдельная волна — тяжёлая UI-часть, API есть).
- Клипы: шера/закладки в клипах уже есть (ClipShareSheet/ClipInteractionsSheet) —
  «Добавить себе» для клипов VK не предусматривает (clips ≠ video), не изобретаем.

### 2.6 Порядок и контроль
1. Мейн-агент: W30-API + этот документ → commit+push (планы на git).
2. Параллельно W30-1, W30-2, W30-3 (файлы НЕ пересекаются).
3. Мейн-агент: ревью, NULL-ЯВНО/сканеры, дельта скобок, commit+push кода.
4. Мейн-агент: HISTORY.md + worklog.md → push.
