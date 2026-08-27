# ПЛАН: Кэширование видео из историй + просмотр через офлайн-менеджер

> Цель: сохранить видео из stories в кэш устройства и дать просматривать их через
> Offline Manager без интернета (и без поломки ленты).
>
> Источники исследования: `FEED_RESEARCH.md` (карта DOM + API из архива `лента.zip`),
> `VK_IMPORT_API.MD` ЧАСТЬ 20, `/home/z/my-project/worklog.md` (RESEARCH-JS-1,
> RESEARCH-APP-1).
>
> Дата: 2026-07-18 (UTC+3, Europe/Moscow). Ветка: `PinoK`.

---

## 1. Контекст и предпосылки

### 1.1. Что уже работает (НЕ трогать)
- `Story` data model имеет `type`, `video: StoryVideo?` с `files: Map<String,String>?`
  (`Models.kt:821, 842-850`).
- `parseStory()` извлекает `video.video_files` → `files` (`VKApiClient.kt:6088-6124`).
- `storiesGet()` возвращает видео с inline MP4 URL — отдельный `video.get` НЕ нужен.
- `StoryViewerScreen` играет video stories через per-story ExoPlayer
  (`StoryViewerScreen.kt:222-271`), URL resolution `mp4_720→480→360→240→144→hls`.
- `VideoDownloadManager` (образец) — storage `filesDir/video_downloads/`,
  key `"${ownerId}_${videoId}"`, file `"${key}.mp4"` + `"${key}.meta"` sidecar,
  range-resume (3 retry, 1/3/9s backoff).
- `TrackDownloadManager` имеет `enqueueDownload(track, silent=true)` для
  auto-cache-on-play (mirror этого паттерна).
- `OfflineManagerScreen` — 2 таба (Аудио/Видео), `VideoPlayerScreen` уже делает
  `file://` substitution (`VideoPlayerScreen.kt:301-302`).

### 1.2. Чего НЕТ (gaps)
1. ❌ `StoryVideoDownloadManager` — story-aware менеджер загрузок
2. ❌ `file://` URL substitution в `StoryViewerScreen` (всегда CDN URL)
3. ❌ Story ID namespace isolation (нужен prefix `s_` + отдельная директория)
4. ❌ Story TTL eviction (24h истории → авто-удаление)
5. ❌ URL-refresh hook for 403 (CDN URL истекают через ~часы)
6. ❌ Tab «Истории» в `OfflineManagerScreen`
7. ❌ Auto-cache-on-play trigger для stories
8. ❌ `silent` параметр у `VideoDownloadManager.enqueueDownload`

---

## 2. Архитектурное решение

### 2.1. Новый `StoryVideoDownloadManager` (рекомендуемый вариант C)

**Почему отдельный класс (не расширение `VideoDownloadManager`)?**
- `Video` имеет `Long videoId`, `Story` имеет `Int storyId` — разные ID-пространства.
- Stories имеют TTL 24h (нужен eviction), catalog videos — нет.
- Stories требуют URL-refresh (CDN URL истекают), catalog videos — нет.
- Чистое разделение = меньше риск поломать существующий video download flow.

**Расположение**: `app/src/main/java/re/pinok/media/StoryVideoDownloadManager.kt`

### 2.2. Хранение

```
filesDir/story_video_downloads/
  ├── s_-43618728_456358682.mp4      ← видео файл
  ├── s_-43618728_456358682.meta     ← JSON sidecar (StoryVideoMeta)
  ├── s_523549648_12345.mp4
  └── s_523549648_12345.meta
```

**Key**: `"s_${ownerId}_${storyId}"` (String, prefix `s_` = story, чтобы избежать
коллизии с catalog videos `"${ownerId}_${videoId}"`).

**Sidecar JSON** (`StoryVideoMeta`):
```kotlin
data class StoryVideoMeta(
    val ownerId: Long,
    val storyId: Int,
    val ownerName: String,          // для отображения в OfflineManager
    val ownerPhoto100: String?,     // аватар автора (для UI)
    val thumbUrl: String?,          // preview URL (story.video.preview)
    val duration: Int,              // секунды
    val storyDate: Long,            // дата создания story (для TTL)
    val downloadedAt: Long,         // timestamp загрузки
    val expiresAt: Long,            // storyDate + 24h (для eviction)
    val sourceUrl: String,          // оригинальный CDN URL (для re-validate)
    val fileSize: Long,             // размер файла
)
```

### 2.3. API `StoryVideoDownloadManager`

```kotlin
object StoryVideoDownloadManager {
    fun init(context: Context)
    fun reconfigurePath(newPath: String)   // если пользователь сменит путь в настройках

    // Основной API
    fun enqueueDownload(story: Story, ownerName: String, silent: Boolean = false)
    fun removeDownload(ownerId: Long, storyId: Int)
    fun isDownloaded(ownerId: Long, storyId: Int): Boolean
    fun getLocalFile(ownerId: Long, storyId: Int): File?
    fun getDownloadState(ownerId: Long, storyId: Int): DownloadState?
    fun storyKey(ownerId: Long, storyId: Int): String = "s_${ownerId}_${storyId}"

    // Для OfflineManagerScreen
    val downloads: StateFlow<Map<String, DownloadState>>

    // Внутреннее
    fun refreshFromDisk()                   // при старте app — прочитать .meta файлы
    fun evictExpired()                      // удалить story где expiresAt < now
}
```

### 2.4. TTL eviction (24h stories)

```kotlin
// В refreshFromDisk() и периодически (WorkManager / при открытии OfflineManager):
fun evictExpired(now: Long = System.currentTimeMillis()) {
    for ((key, meta) in metaFiles) {
        if (meta.expiresAt < now) {
            removeDownload(meta.ownerId, meta.storyId)
            AppLog.i("StoryVideoDownloadManager",
                "Evicted expired story: $key (expired ${Date(meta.expiresAt)})")
        }
    }
}
```

### 2.5. URL-refresh hook for 403

```kotlin
// При 403 в downloadWithResume:
// 1. Re-fetch stories через storiesGet() для owner
// 2. Найти story по (ownerId, storyId)
// 3. Извлечь свежий files map
// 4. Retry с новым URL
suspend fun refreshStoryUrl(ownerId: Long, storyId: Int): String? {
    return try {
        val groups = app.apiClient.storiesGet(count = 50)
        // ищем группу с ownerId, в ней story с storyId
        val story = groups.find { it.ownerId == ownerId }
            ?.stories?.find { it.id == storyId }
        story?.video?.files?.let { pickBestMp4(it) }
    } catch (e: Exception) {
        AppLog.w("StoryVideoDownloadManager", "URL refresh failed", e)
        null
    }
}
```

---

## 3. Интеграция в `StoryViewerScreen` (БЕЗ поломки ленты)

### 3.1. file:// URL substitution (mirror `VideoPlayerScreen.kt:301-302`)

**Было** (`StoryViewerScreen.kt:223-228`):
```kotlin
val videoUrl: String? = if (storyVideo != null) {
    val f = storyVideo.files
    f?.get("mp4_720") ?: f?.get("mp4_480") ?: ...
} else null
```

**Стало**:
```kotlin
val videoUrl: String? = if (storyVideo != null) {
    // Fix #100: приоритет — локальный кэш, потом CDN.
    val localFile = StoryVideoDownloadManager.getLocalFile(story.ownerId, story.id)
    if (localFile != null && localFile.exists()) {
        "file://${localFile.absolutePath}"
    } else {
        val f = storyVideo.files
        f?.get("mp4_720") ?: f?.get("mp4_480") ?: f?.get("mp4_360")
            ?: f?.get("mp4_240") ?: f?.get("mp4_144") ?: f?.get("hls")
            ?: storyVideo.player?.takeIf { it.isNotBlank() }
    }
} else null
```

### 3.2. Auto-cache-on-play (mirror `PlayerConnection` pattern)

**В `StoryViewerScreen` ExoPlayer listener** (после `Player.STATE_READY`):
```kotlin
override fun onPlaybackStateChanged(state: Int) {
    if (state == Player.STATE_READY && !story.isSeenBool) {
        // Auto-cache: если story video ещё не скачана — скачать тихо
        if (!StoryVideoDownloadManager.isDownloaded(story.ownerId, story.id)) {
            val ownerName = currentGroup?.name ?: ""
            StoryVideoDownloadManager.enqueueDownload(story, ownerName, silent = true)
        }
    }
    if (state == Player.STATE_ENDED) goToNext()
}
```

### 3.3. КРИТИЧНО: ExoPlayer premature release (Risk #2)

`remember(videoUrl)` (line 232) — если `videoUrl` flip-нет с CDN → `file://`
mid-playback (когда download завершится), player пересоздастся → чёрный кадр.

**Решение**: compute `resolvedUrl` ONCE per story (snapshot at composition):
```kotlin
// Вычислить ОДИН раз при входе в story, не подписываться на live download state
val resolvedUrl by remember(story.id, story.ownerId) {
    derivedStateOf { resolveVideoUrl(story) }  // snapshot, не reactive
}
val exoPlayer = remember(resolvedUrl) { ExoPlayer.Builder(context).build().apply { ... } }
```

---

## 4. Интеграция в `OfflineManagerScreen`

### 4.1. Третий таб «Истории»

```kotlin
// OfflineManagerScreen.kt
val storyCount by StoryVideoDownloadManager.downloads
    .map { it.values.count { s -> s.status == DownloadStatus.COMPLETED } }
    .collectAsState()

// Tabs:
Tab("Аудио ($audioCount)")
Tab("Видео ($videoCount)")
Tab("Истории ($storyCount)")   // ← NEW
```

### 4.2. `StoryOfflineTab` composable

```kotlin
@Composable
private fun StoryOfflineTab(
    onPlay: (ownerId: Long, storyId: Int) -> Unit,
) {
    val downloads by StoryVideoDownloadManager.downloads.collectAsState()
    val completed = downloads.values.filter { it.status == DownloadStatus.COMPLETED }
    // LazyColumn с item'ами:
    //  - аватар автора (AsyncImage photo100)
    //  - имя автора
    //  - длительность (formatDuration)
    //  - badge "История истекает через Xч"
    //  - кнопка Play → onPlay(ownerId, storyId)
    //  - кнопка Delete → StoryVideoDownloadManager.removeDownload(ownerId, storyId)
}
```

### 4.3. Playback entrypoint

При тапе Play в StoryOfflineTab:
- Если онлайн → открыть `StoryViewerScreen` с этим story (передать `(ownerId, storyId)`,
  viewer найдёт локальный файл через `getLocalFile`).
- Если офлайн → открыть упрощённый `VideoPlayerScreen` с `file://` URL
  (переиспользовать существующий `VideoPlayerScreen` с `getLocalFile`).

---

## 5. Пошаговый план внедрения

### Этап 1: `StoryVideoDownloadManager` (foundation)
1. Создать `media/StoryVideoDownloadManager.kt` (mirror `VideoDownloadManager.kt`)
2. Реализовать `StoryVideoMeta` data class + JSON sidecar read/write
3. Реализовать `enqueueDownload`, `removeDownload`, `getLocalFile`, `isDownloaded`
4. Реализовать `refreshFromDisk()` (чтение `.meta` файлов при старте)
5. Реализовать `evictExpired()` (TTL 24h)
6. Реализовать `downloadWithResume` (range-resume, 3 retry, 1/3/9s backoff)
7. Реализовать `refreshStoryUrl()` (403 handler — re-fetch через `storiesGet`)
8. Создать `StoryVideoDownloadService` (foreground service, notif channel
   `story_video_downloads`, ID 2002 — не конфликтовать с 2001 video)
9. Инициализация в `SovaApp.onCreate()` после `VideoDownloadManager.init()`

**Тест**: unit-проверка, что `enqueueDownload` создаёт файл и `.meta`.

### Этап 2: Интеграция в `StoryViewerScreen` (минимальные правки)
10. Добавить `file://` substitution перед CDN URL resolution (§3.1)
11. Использовать `derivedStateOf` snapshot для `resolvedUrl` (§3.3, Risk #2)
12. Добавить auto-cache-on-play в `onPlaybackStateChanged(STATE_READY)` (§3.2)
13. Skip `stories.view` side-effect если playing from local cache (Risk #7)

**Тест**: открыть story video → играет с CDN → через ~секунд играет из кэша при
повторном открытии. Лента не ломается (StoriesHolder не мутируется).

### Этап 3: Tab «Истории» в `OfflineManagerScreen`
14. Добавить третий таб с count из `StoryVideoDownloadManager.downloads`
15. Создать `StoryOfflineTab` composable (§4.2)
16. Подключить playback entrypoint (§4.3)
17. Добавить в footer общий размер (`audioBytes + videoBytes + storyBytes`)

**Тест**: скачать story → открыть Offline Manager → видна в табе «Истории» →
проигрывает офлайн.

### Этап 4: URL-refresh и edge-cases
18. Реализовать `refreshStoryUrl()` (§2.5) — re-fetch через `storiesGet`
19. В `downloadWithResume` on 403: вызвать `refreshStoryUrl`, retry с новым URL
20. Добавить `silent` параметр (skip foreground notif для auto-cache)

### Этап 5: Настройки и UX
21. Добавить `storyDownloadPath` в `SovaPrefs` (опционально, default
    `filesDir/story_video_downloads/`)
22. Добавить toggle «Автокэшировать истории» в Settings (default ON)
23. Добавить cap на размер кэша stories (200 МБ LRU, Risk #4)
24. При достижении cap — удалить самые старые по `downloadedAt`

### Этап 6: Тестирование и полировка
25. Проверить: лента НЕ ломается (StoriesHolder кэш валиден)
26. Проверить: ExoPlayer не пересоздаётся mid-playback
27. Проверить: TTL eviction работает (создать meta с истёкшим `expiresAt`)
28. Проверить: 403 refresh работает (искусственно истечь URL)
29. Проверить: offline playback работает (включить airplane mode)
30. Проверить: нет утечек ExoPlayer (DisposableEffect release)

---

## 6. Риски и митигации (без поломки ленты)

| # | Риск | Митигация |
|---|---|---|
| 1 | StoriesHolder cache invalidation — мутация `Story` после parse | Кэш live в `StoryVideoDownloadManager` (lookup by key), НЕ мутировать `Story` |
| 2 | ExoPlayer premature release при flip CDN→file:// | `derivedStateOf` snapshot `resolvedUrl` ONCE per story (§3.3) |
| 3 | Story ID / Video ID collision | distinct dir `story_video_downloads/` + key prefix `s_${ownerId}_${storyId}` |
| 4 | Cache size bloat (5-30 МБ/story) | Cap 200 МБ LRU по `downloadedAt`, удалять старые |
| 5 | Story URL expiry mid-download (403) | `refreshStoryUrl()` re-fetch через `storiesGet()`, retry |
| 6 | `stories.view` on cached/expired | Skip когда playing from local cache |
| 7 | Foreground service overload | `silent=true` skip notif для auto-cache, batch enqueue |
| 8 | VK ToS (stories 24h) | Operationally accepted (app уже кэширует audio + catalog video) |

---

## 7. Файлы для создания/изменения

### Создать (3 файла)
- `app/src/main/java/re/pinok/media/StoryVideoDownloadManager.kt` (new, ~500 строк)
- `app/src/main/java/re/pinok/service/StoryVideoDownloadService.kt` (new, ~80 строк)
- (опционально) `app/src/main/java/re/pinok/media/StoryVideoMeta.kt` (new, data class)

### Изменить (4 файла)
- `app/src/main/java/re/pinok/ui/screens/feed/StoryViewerScreen.kt`
  - `file://` substitution (§3.1)
  - `derivedStateOf` snapshot (§3.3)
  - auto-cache-on-play (§3.2)
  - skip `stories.view` for cached (Risk #7)
- `app/src/main/java/re/pinok/ui/screens/offline/OfflineManagerScreen.kt`
  - третий таб «Истории» (§4.1)
  - `StoryOfflineTab` composable (§4.2)
  - footer с storyBytes (§4.1)
- `app/src/main/java/re/pinok/SovaApp.kt`
  - `StoryVideoDownloadManager.init(this)` в `onCreate()`
- `app/src/main/java/re/pinok/data/local/SovaPrefs.kt`
  - `storyDownloadPath: String` (default `filesDir/story_video_downloads/`)
  - `autoCacheStories: Boolean` (default true)
  - `storyCacheLimitMb: Int` (default 200)

### НЕ ТРОГАТЬ (чтобы не поломать ленту)
- `StoriesRow.kt` — лента stories (не viewer)
- `VKApiClient.kt` — `storiesGet()`, `parseStory()` уже корректны
- `Models.kt` — `Story`, `StoryVideo` уже имеют нужные поля
- `VideoDownloadManager.kt` — catalog videos (отдельная логика)
- `TrackDownloadManager.kt` — audio (отдельная логика)
- `PlayerService.kt` — audio background (не используется для stories)

---

## 8. Критерии готовности

- [ ] `StoryVideoDownloadManager` создаёт/читает/удаляет `.mp4` + `.meta`
- [ ] TTL eviction удаляет истории старше 24h при старте app
- [ ] `StoryViewerScreen` играет из `file://` если кэш есть, иначе CDN
- [ ] Auto-cache срабатывает на `STATE_READY` (silent, без notif)
- [ ] ExoPlayer НЕ пересоздаётся mid-playback (snapshot URL)
- [ ] `OfflineManagerScreen` показывает таб «Истории» с count
- [ ] Offline playback работает в airplane mode
- [ ] 403 refresh: retry с новым URL после re-fetch
- [ ] Cache cap 200 МБ: LRU eviction по `downloadedAt`
- [ ] Лента НЕ ломается: StoriesHolder кэш валиден, stories загружаются
- [ ] Нет утечек ExoPlayer (DisposableEffect release on dispose)
- [ ] HISTORY.md обновлён, коммит запушен в `origin/PinoK`

---

## 9. Оценка трудозатрат

| Этап | Описание | Оценка |
|---|---|---|
| 1 | `StoryVideoDownloadManager` + service | ~500 строк, основная работа |
| 2 | Интеграция в `StoryViewerScreen` | ~30 строк правок |
| 3 | Tab «Истории» в `OfflineManagerScreen` | ~150 строк (tab + items) |
| 4 | URL-refresh + edge-cases | ~80 строк |
| 5 | Настройки + UX | ~50 строк |
| 6 | Тестирование | ручное (Android SDK нет на сервере) |
| **Итого** | | **~810 строк, 3 new + 4 modified файла** |

---

## 10. Порядок внедрения (без поломки ленты)

**Принцип**: каждый этап — отдельный коммит, проверяемый пользователем локально.

1. **Коммит 1** (Этап 1): `StoryVideoDownloadManager` + service + init в SovaApp.
   Проверка: app собирается, менеджер инициализируется (лог в LogCat).
2. **Коммит 2** (Этап 2): Интеграция в `StoryViewerScreen`.
   Проверка: открыть story video → играет → повторно открыть → играет из кэша.
3. **Коммит 3** (Этап 3): Tab «Истории» в `OfflineManagerScreen`.
   Проверка: скачать story → видна в Offline Manager → проигрывает офлайн.
4. **Коммит 4** (Этап 4-5): URL-refresh, silent, cap, настройки.
   Проверка: edge-cases, 403 refresh, cache cap.
5. **Коммит 5** (Этап 6): HISTORY.md, финальная проверка.

Каждый коммит — atomic, можно откатить без потери предыдущих этапов.
