# Fix #281 — Музыка: ANR и падения при поиске

> Отчёт по задаче «Есть проблемы в разделе музыка, при поиске может закрыться
> приложение или выдает ANR» (2026-09-03).
> Ветка: `PinoK` · зона: `MusicScreen` (поиск), `VKApiClient` (audio.search*),
> `AppLog` (:core:common). Звонковая логика НЕ затронута — BuildStamp
> `calls-2026.09.06-5` (текущий, волны звонков) в силе, bump не требуется.

---

## 1. Симптомы (от пользователя)

1. **Приложение может закрыться** при поиске в разделе «Музыка»
   (вкладка «Моя музыка», inline-поле поиска).
2. **ANR** («Приложение не отвечает») во время поиска.

---

## 2. Диагноз — три независимых корня

### 2.1. CRASH: дубликаты ключей LazyRow/LazyColumn («Key was already used»)

**Цепочка падения:**

```
MusicScreen → audioSearchWithSections()
  └─ catalog.getAudioSearch вернул 0 блоков (need_blocks отдал пустые blocks)
     └─ fallback: audioSearchArtists() → парсит response.links[] (content_type=artist)
        └─ VK в links[] НЕ отдаёт числовой id → КАЖДЫЙ артист создаётся с id = 0L
           └─ два и более артиста в результате
              └─ LazyRow: items(resultArtists, key = { "artist_${it.id}" })
                 → два item с key="artist_0"
                 → IllegalArgumentException: Key "artist_0" was already used
                    → процесс ПАДАЕТ (пользователь: «приложение закрылось»)
```

Тот же риск для плейлистов: один и тот же плейлист VK мог вернуть и в
`albums[]`, и в `playlists[]` → дубликат ключа `"pl_<owner>_<id>"`.

**Файл:** `app/src/main/java/re/pinok/api/VKApiClient.kt`,
`app/src/main/java/re/pinok/ui/screens/music/MusicScreen.kt`.

### 2.2. ANR: синхронная дисковая запись логов на потоке вызывающего (включая main)

`AppLog.appendToFile()` (:core:common) выполнял на **вызывающем потоке**:

- `synchronized(persistLock)` — ожидание лока, который держит любой фоновый
  поток во время записи;
- `writer.write(line)` + **`writer.flush()`** — дисковый I/O на каждой строке;
- `file.length()` — stat-сисвызов на каждой строке (проверка rotation);
- плюс `callerLocation()` — построение полного stack trace (`Throwable().stackTrace`)
  на каждую запись (в Compose стеки глубокие).

Во время поиска лог-трафик взлетает: `AppLog.api` на каждый запрос
(REQUEST + результат), W-логи fallback-веток, I-логи результатов, D-логи
per-track в `playTrackList` — и всё это параллельно с постоянным фоном
поллеров (LongPoll, NotificationsPoller, MessageNotifier). Итог: main-поток
регулярно (а) сам делал дисковый I/O и (б) ждал `persistLock` за фоновыми
писателями. На бюджетных устройствах с медленной флеш-памятью всплеск
записей при поиске → блокировки кадров → ANR «Input dispatching timed out».

**Файл:** `core/common/src/main/java/re/pinok/util/AppLog.kt`.

### 2.3. ANR-контрибьютор + батарея: до 5 HTTP-вызовов на один запрос, 3× повторное скачивание одного JSON

Старый `audioSearchWithSections()` на каждый поисковый запрос:

```
catalog.getAudioSearch            (200-500KB)
  └─ если пусто:
     ├─ audio.search → при ошибке audioSearchCatalogFallback → catalog.getAudioSearch (ЕЩЁ РАЗ, те же 200-500KB)
     ├─ audioSearchArtists → catalog.getAudioSearch (ЕЩЁ РАЗ)
     └─ audioSearchPlaylists
```

= до 3 повторных скачиваний и парсингов одного и того же ответа, всё через
rate-limiter 3 rps (`delay()`) → до ~2 с лишних задержек на запрос, лишний
CPU (parse 300KB×3) и трафик. При наборе текста с дебаунсом 500мс это
давало очереди запросов и ощущение «поиск виснет», подкладываясь под ANR.

Плюс UI-баг: `LaunchedEffect(searchQuery)` перезапускал эффект на каждом
нажатии клавиши, а `collect` ловил `CancellationException` в `catch(Exception)`
и **затирал searchResult пустым AudioSearchResult()** — вспышка «Ничего не
найдено» при наборе и потеря уже загруженных результатов.

---

## 3. Что изменено

### 3.1. `VKApiClient.kt` — один catalog-запрос + дедупликация

| Было | Стало |
|---|---|
| catalog.getAudioSearch + до 3 повторов того же метода в fallback-ветках | РОВНО ОДИН вызов через новый `catalogGetAudioSearchRaw()`; артисты добираются из `links[]`, плейлисты из `albums[]/playlists[]` ТОГО ЖЕ ответа |
| Артисты из links[] без дедупа (у всех id=0) | `finalizeAudioSearchResult()`: артисты `distinctBy { id>0 → "id_X", иначе "name_<имя.lowercase()>" }`; плейлисты/треки — `distinctBy (ownerId, id)`; треки обрезаются до count |
| `audioSearch()` всегда мог уйти в catalog-fallback | Параметр `allowCatalogFallback: Boolean = true`; из `audioSearchWithSections` передаётся `false` (catalog уже запрошен) |
| — | `audioSearchArtists/audioSearchAlbums` — публичные сигнатуры НЕ изменены (экраны библиотеки не тронуты); тела вынесены в `parseArtistsFromCatalogSearchLinks` / `parsePlaylistsFromCatalogSearch` |
| `audio.searchPlaylists` вызывался всегда в fallback | Вызывается если плейлисты ещё пусты — это ДРУГОЙ эндпоинт, полезен для direct-auth токенов |

Худший сценарий «catalog пуст»: было 4-5 вызовов / 3×300KB — стало 2 вызова
(catalog + audio.search) / 1×300KB + audio.searchPlaylists только при
пустых плейлистах.

### 3.2. `MusicScreen.kt` — crash-proof ключи + корректная отмена

1. **Ключи с индексом** (гарантированная уникальность, defense-in-depth к
   дедупу в API):
   - артисты: `itemsIndexed(resultArtists, key = { i, a -> "artist_${i}_${a.id}" })`
   - плейлисты: `itemsIndexed(resultPlaylists, key = { i, p -> "pl_${i}_${p.ownerId}_${p.id}" })`
   - треки: `itemsIndexed(resultTracks, key = { i, t -> "track_${i}_${t.ownerId}_${t.id}" })`
2. **Дебаунс-эффект**: `LaunchedEffect(searchQuery)` + `collect` →
   `LaunchedEffect(Unit)` + `snapshotFlow { searchQuery }.debounce(500)
   .collectLatest` — эффект стартует один раз; `collectLatest` отменяет
   предыдущий in-flight запрос при новом вводе (OkHttp-cancel уже подключён
   через `suspendCancellableCoroutine` в `call()`).
3. **CancellationException больше не глотается**: в поиске и в
   `loadMoreTracksSuspend` добавлен явный
   `catch (e: CancellationException) { throw e }` перед `catch (e: Exception)`.
   Результаты поиска больше не затираются пустым результатом при наборе.

### 3.3. `AppLog.kt` (:core:common) — асинхронный writer

| Было | Стало |
|---|---|
| write + flush + file.length() на потоке вызывающего (включая main) | Файловая запись только на однопоточном executor `PinoK-LogWriter` (daemon); вызывающий поток лишь ставит задачу в очередь |
| flush на КАЖДУЮ строку | Batch-flush: flush когда очередь задач исчерпана (`pendingPersistWrites` → 0) — при burst'е N строк = 1 flush |
| Rotation в произвольном потоке (гонки) | Rotation только на writer-потоке под `persistLock` |
| — | Новый `flushSync(timeoutMs=2000)`: синхронный дренаж очереди для критичных путей; подключён в `clear()` перед закрытием writer |

Не изменилось: in-memory буфер (4000 записей) — синхронный и мгновенный,
LogViewer/snapshot/exportDetailed работают как раньше; logcat-вывод не
тронут; формат persistent.log не изменился.

**Компромиссы** (осознанные): при жёстком крэше процесса последние
не-flush'нутые строки файла могут потеряться (буфер в памяти при этом
полный — exportDetailed не страдает); `flushSync` в `clear()` может
подождать до 2с — вызывается только из UI «Очистить лог».

---

## 4. Поведение после фикса (что проверить пользователю)

Сборка: собрать debug-APK на своей машине (`assembleDebug`), лог старта
должен показать текущий stamp `calls-2026.09.06-5` (звонки/музыка не пересекаются).

Матрица проверки музыки (теги в логе для сверки: `MusicScreen`, `VKApiClient`):

1. **Поиск не падает**: Музыка → «Моя музыка» → ввести запрос из 2-4 букв
   частями («кри», «крист», «кристалл») быстро — приложение живо, никаких
   «Key was already used» в логе, вспышек «Ничего не найдено» между буквами нет.
2. **ANR ушёл**: длительный активный набор запросов (10+ символов,
   исправления по буквам) — интерфейс не замирает, ANR-диалог не появляется.
3. **Секции поиска**: у web-токена в результатах есть Артисты/Плейлисты/Треки;
   в логе одна строка `audioSearchWithSections(catalog): query='...' → N tracks,
   M artists, K playlists` на запрос (не по одной на каждый подзапрос).
4. **Дубликаты**: в секции «Артисты» нет одинаковых карточек; в «Плейлисты»
   нет повторов одного плейлиста.
5. **Отмена запроса**: начать вводить длинный запрос и сразу стереть —
   в логе НЕТ `Search error ... CancellationException` (раньше отмены
   логировались как ошибки), результаты очищаются мгновенно.
6. **Классика (direct-auth)**: если есть аккаунт с direct-токеном — поиск
   работает, в логе fallback-ветка `audio.search` при недоступном catalog.
7. **Регресс-дым**: воспроизведение из поиска (тап по треку), «перемешать
   все», скачивание трека, LogViewer и «Очистить лог» в настройках.

Логи для диагностики, если что-то не так: экспорт из Настройки → Лог
(detailed dump включает thread+caller на каждой строке — видно, что
записи файла больше не блокируют main: поток `main` в дампе есть,
дисковых `flush` от него больше нет — строки пишутся от `PinoK-LogWriter`).

---

## 5. Технические детали

- **Rate-limiter** (`rateLimitWait`): использует `CopyOnWriteArrayList` +
  `delay()` — потокобезопасен и не блокирует потоки; не менялся.
- **OkHttp**: `call()` — `enqueue` + `suspendCancellableCoroutine` +
  `invokeOnCancellation { call.cancel() }` внутри `withContext(Dispatchers.IO)`
  — отмена уже корректная; не менялся.
- **Почему ключи с индексом, а не только дедуп данных**: дедуп гарантирует
  уникальность для ТЕКУЩИХ источников данных; индекс в ключе делает краш
  «Key was already used» невозможным в принципе, даже если будущие
  источники принесут новые типы дублей. Для поисковой выдачи (список
  полностью заменяется на каждый запрос) индексные ключи семантически
  корректны.
- **Почему collectLatest, а не collect**: collect не отменял предыдущую
  обработку — при быстром наборе запросы выполнялись последовательно
  (каждый ждёт rate-limiter), поздние результаты перезаписывали ранние;
  collectLatest отменяет неактуальную обработку немедленно.
- **NULL-политика (#NULL-EXPLICIT)**: в новом коде `?.`/`?:` не добавлены
  (проверка grep по диффу; операторы в перенесённых телах парсеров —
  существующие, перемещены 1:1).
- **Потоки AppLog**: `persistWriter`/`persistFile` — @Volatile; запись и
  rotation только на writer-потоке; `init()` (SovaApp.onCreate, main) и
  `clear()` берут `persistLock` краткосрочно; дедлока нет (flushSync
  вызывается ДО захвата persistLock в clear()).

---

## 6. Файлы изменения

| Файл | Что |
|---|---|
| `app/src/main/java/re/pinok/api/VKApiClient.kt` | audioSearchWithSections: 1 catalog-запрос, finalizeAudioSearchResult, catalogGetAudioSearchRaw, allowCatalogFallback, парсеры-хелперы |
| `app/src/main/java/re/pinok/ui/screens/music/MusicScreen.kt` | itemsIndexed-ключи×3, LaunchedEffect(Unit)+collectLatest, CancellationException rethrow ×2 |
| `core/common/src/main/java/re/pinok/util/AppLog.kt` | persistExecutor + batch-flush + flushSync + rotation на writer-потоке |
| `MUSIC_SEARCH_ANR_FIX.md` | этот документ |
| `HISTORY.md`, `worklog.md` | журналы (append-only) |
