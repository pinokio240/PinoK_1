# Справочник по VK API для проекта «Pinok»

> **Важно про источник.** Этот документ собран со страницы
> `https://vk-api.readthedocs.io/en/latest/` — это документация **Python-обёртки**
> `vk_api` (библиотека `python273/vk_api`), а **НЕ** официальная документация
> сырого HTTP API ВКонтакте (`vk.com/dev`). Поэтому:
> * ясно указано, что именно подтверждено этим источником;
> * детали, которые в этой документации **отсутствуют** (формат attachment-строки,
>   поведение `messages.edit` при правке вложений, адреса upload-серверов `pu.vk.ru`
>   и т.п.), честно помечены как **«не найдено в источнике»** и **не выдуманы**.
>
> Если нужны сами сигнатуры HTTP-методов `messages.*` — смотри официальную
> документацию ВКонтакте (ссылки в конце), этот документ их не покрывает.

---

## 1. Общая информация (по данным источника)

### 1.1. Что такое `vk_api` в этом источнике
Python-модуль для создания скриптов к VK.com API. Ставится через pip
(`pip3 install vk_api`), исходники на GitHub: `python273/vk_api`.

Основной класс — `VkApi`. Точка входа для вызова любой методы API:
`vk = vk_session.get_api()`, далее методы доступны как атрибуты, например
`vk.wall.post(message='Hello world!')`. Для мессенджера Pinok это значит конвенцию
`vk.messages.*`.

### 1.2. Формат запроса / вызов метода
Внутри библиотеки все вызовы идут через `VkApi.method(method, values, ...)`:
* `method` (str) — название метода, например `messages.send`;
* `values` (dict) — параметры запроса (отправляются как form-urlencoded POST);
* `captcha_sid`, `captcha_key` — повтор запроса с ответом капчи;
* `raw` (bool) — при `False` возвращается `response['response']`, при `True` —
  весь ответ (нужно для `execute`, чтобы получить `execute_errors`).

То есть фактически источник подтверждает схему: **метод + параметры-словарём +
POST form-urlencoded**, но сами адреса HTTP-эндпоинтов (`https://api.vk.com/method/...`)
здесь **не найдено в источнике**.

### 1.3. Версия API и авторизация
> Параметры по умолчанию конструктора `VkApi`.
* `api_version` (str) — **версия API**, в конструкторе по умолчанию `'5.92'`.
  В реальных проектах параметр обычно передаётся как `v=` в каждом запросе —
  но именно про поле `v` этот источник **не упоминает** (не найдено в источнике).
* `token` — **access_token**. Если передан, используется для авторизации
  (без логина/пароля).
  * `auth(reauth=False, token_only=False)` — если `token_only=True`, используется
    оптимальная стратегия: проверяется валидность токена, куки игнорируются.
    Для приложения, которое ходит только в API (без веб-версии), рекомендуется
    `token_only=True`.
* `login` / `password` (str) — нужны для автоматического получения токена через
  Implicit Flow (логин лучше номером телефона для обхода проверки безопасности).
* `app_id` (int) — `app_id` Standalone-приложения; по умолчанию `6222115`
  (это app_id самой библиотеки-пример, не приложения Pinok).
* `scope` (int|str) — запрашиваемые **права**; по умолчанию `140492255`.
  Для мессенджера критично право `MESSAGES = 4096` (доступ к расширенным методам
  работы с сообщениями) и `OFFLINE = 65536` (доступ к API в любое время;
  источник рекомендует его при работе с библиотекой). Полный список прав — в §6.3.
* `client_secret` — секретный ключ для Client Credentials Flow.
  **Источник прямо предупреждает:** этот способ авторизации **устарел**,
  рекомендуется использовать сервисный ключ приложения.

### 1.4. Ошибки: `error_code` / `error_msg`
Источник описывает ошибки **через Python-исключения**, а не через ответ
`error_code`/`error_msg`. Иерархия (класс `VkApiError(Exception)` — базовый):
* `VkApiError` — базовый;
* `AccessDenied` — нет доступа;
* `AuthError` — ошибка авторизации; подтипы:
  * `LoginRequired`, `PasswordRequired`, `BadPassword`, `AccountBlocked`,
    `TwoFactorError` (двухфакторная аутентификация), `SecurityCheck` (проверка
    безопасности/капча при входе) — с полями `phone_prefix`, `phone_postfix`;
* `ApiError(vk, method, values, raw, error)` — ошибка, возвращённая API на запрос.
  Метод `try_method()` повторяет запрос;
* `ApiHttpError(vk, method, values, raw, response)` — из уровня HTTP.
  Метод `try_method()` повторяет запрос;
* `Captcha` — капча при запросе: `get_url()`, `get_image()` (jpg), `try_again(key)`
  повторяет запрос с ответом капчи;
* `VkAudioException` / `VkAudioUrlDecodeError` — ошибки аудио-модуля;
* `VkToolsException`, `VkRequestsPoolException`.

Справочник кодов `error_code` в стандартном ответе API (`{"error": {"error_code": ...,
"error_msg": ...}}`) в этом источнике **не найден** — это уровень сырого API.

### 1.5. Обработчики событий базового класса (полезно для стабильности)
У `VkApi` есть переопределяемые обработчики, на которые стоит повесить свою логику
в Pinok:
* `captcha_handler(captcha)` — обработка капчи; по умолчанию бросает `Captcha`;
* `need_validation_handler(error)` — проверка безопасности при запросе (`need_validation`);
* `http_handler(error)` — обработка ошибок соединения;
* `too_many_rps_handler(error)` — **«слишком много запросов в секунду»**:
  по умолчанию ждёт **полсекунды** и повторяет запрос (релевантно для лимитов);
* `auth_handler()` — двухфакторная аутентификация.

---

## 2. Методы сообщений `messages.*`

### 2.1. Что реально покрывает источник
Источник **не документирует** сигнатуры самих HTTP-методов `messages.send`,
`messages.edit`, `messages.delete` — это документация Python-обёртки, которая
вызывает их через `vk.method('messages.send', {...})` / `vk.messages.send(...)`.
Поэтому детальные сигнатуры (`peer_id`, `random_id`, `cmid`/`conversation_message_id`
и т.д.) в этом источнике **не найдены**.

Параметры, которые источник **подтверждает** косвенно:
* названия методов вызываются строками вида `messages.<method>` (`VkApi.method`);
* ответы ходят через `response['response']` (или весь ответ при `raw=True`);
* повтор проблемного запроса — `ApiError.try_method()`.

### 2.2. `messages.edit` и attachment (важно для Pinok)
Поведение `messages.edit` при правке вложений (**перезапись списка**; эффект
отсутствия параметра `attachment` vs пустой строки `""`; параметры `keep_snippets`,
`keep_forward_messages`) в этом источнике **не описано**. Данные пункты берутся
из фактов, полученных вне этого источника (см. раздел «Источники не найдены»),
и в этой документации отсутствуют. Не выдумано здесь — смотреть первоисточник,
либо принятые в кодовой базе Pinok решения (см. память проекта: `messagesEdit` /
`messagesEdit(attachment="")`).

---

## 3. Формат attachment-строки

Формат `{type}{owner_id}_{id}[_{access_key}]`, перечень допустимых типов
вложений (photo, video, audio, doc, link, wall, sticker, audio_message, gift,
poll и др.) и примеры строк в этом источнике **не найдены** — документация
Python-обёртки не описывает синтаксис attachment. Рекомендуемый первоисточник:
официальная документация ВКонтакте (`vk.com/dev/objects/attachments`).

Единственное, что источник даёт по вложениям: на уровне Long Poll есть флаг
режима, который заставляет сервер **возвращать вложения в событиях** —
`VkLongpollMode.GET_ATTACHMENTS = 2` (см. §5). Это важно для Pinok: при
получении события `MESSAGE_NEW`/`MESSAGE_EDIT` вложения приходят в теле события
только если у Long Poll включён этот режим.

---

## 4. Загрузка файлов (upload-воркфлоу)

> Источник описывает загрузку **на уровне готовых методов `VkUpload`**, то есть
> подтверждает общий паттерн «получить upload-сервер → загрузить файл → сохранить»,
> но конкретные HTTP-эндпоинты (`docs.getUploadServer`, `docs.getMessagesUploadServer`,
> `photos.getMessagesUploadServer`, `docs.save`, `photos.save`, адреса `pu.vk.ru`)
> в нём **не перечислены** — это уровень сырого API. Ниже — то, что подтверждено.

Модуль `VkUpload(vk)` — «Загрузка файлов через API» (ссылка на `vk.com/dev/upload_files`).
Методы, релевантные мессенджеру Pinok:

| Метод VkUpload | Назначение | Ключевые параметры |
|---|---|---|
| `photo_messages(photos, peer_id=None)` | Загрузка изображений **в сообщения** | путь/файл(ы), `peer_id` беседы |
| `document_message(doc, title=None, tags=None, peer_id=None)` | Загрузка **документа для личного сообщения** | файл, `title`, `tags`, `peer_id` |
| `audio_message(audio, peer_id=None, group_id=None)` | Загрузка **голосового сообщения** | файл, `peer_id` (или `group_id` для токена группы) |
| `graffiti(image, peer_id=None, group_id=None)` | Загрузка **граффити** | PNG-файл, `peer_id` |
| `document_wall(doc, ...)` | Документ в «Отправленные» для последующей отправки на стену/сообщением | файл |
| `audio(audio, artist, title)` | Загрузка аудио | файл, исполнитель, название |
| `video(video_file/link, ...)` | Загрузка видео (`is_private=1` — приватно для ЛС) | файл/ссылка |
| `photo_chat(photo, chat_id)` | Смена обложки беседы | файл, `chat_id` |

Общий паттерн загрузки (подтверждается механикой этих методов, детали не в источнике):
1. получить upload-сервер (у метода),
2. загрузить файл на `upload_url`,
3. сохранить (метод возвращает сохранённый объект, который потом кладётся в
   attachment при `messages.send`).

Конкретные имена методов API, через которые это работает, источник **не перечисляет**
(как и хосты `pu.vk.ru`).

---

## 5. Мессенджер поверх API: ограничения и режимы

### 5.1. Long Poll (User Long Poll) — подтверждено источником
Класс `VkLongPoll(vk, wait=25, mode=234, preload_messages=False, group_id=None)`.
`mode` собирается побитово из флагов `VkLongpollMode`:
* `GET_ATTACHMENTS = 2` — получать вложения в событиях;
* `GET_EXTENDED = 8` — расширенный набор событий;
* `GET_PTS = 32` — возвращать `pts` для `messages.getLongPollHistory`;
* `GET_EXTRA_ONLINE = 64` — доп. данные в `extra` для события «друг стал онлайн»;
* `GET_RANDOM_ID = 128` — возвращать поле **`random_id`** в событиях.

`preload_messages` / `preload_message_events_data()` — предзагрузка сообщений из API
для получения ссылок на прикреплённые файлы. События, подлежащие предзагрузке:
`PRELOAD_MESSAGE_EVENTS = [MESSAGE_NEW, MESSAGE_EDIT]`.

События Long Poll `VkEventType` (полезные для Pinok):
* `MESSAGE_NEW = 4` — новое сообщение;
* `MESSAGE_EDIT = 5` — **редактирование сообщения**;
* `READ_ALL_INCOMING_MESSAGES = 6`, `READ_ALL_OUTGOING_MESSAGES = 7` — прочтение;
* `USER_TYPING = 61` — собеседник набирает текст (приходит ~раз в 5 секунд);
* `USER_TYPING_IN_CHAT = 62` — набор в беседе;
* `USER_RECORDING_VOICE = 64` — запись голосового;
* `USER_CALL = 70` — звонок;
* `CHAT_EDIT = 51`, `CHAT_UPDATE = 52` — изменение темы/состава/флагов беседы;
* `PEER_DELETE_ALL = 13`, `PEER_RESTORE_ALL = 14` — удаление/восстановление всех
  сообщений диалога до `local_id`.

Поля события для `MESSAGE_NEW`/`MESSAGE_EDIT` (среди прочих):
`text` — **экранированный** (HTML-мнемоники) текст, `message` — оригинальный текст.
При наличии `timestamp` события добавляют поле `datetime`.

Флаги сообщений `VkMessageFlag` (значения-битовые, пригодны для фильтрации):
`UNREAD=1`, `OUTBOX=2` (исходящее), `REPLIED=4` (на сообщение создан ответ),
`IMPORTANT=8`, `CHAT=16` (из чата), `SPAM=64`, `DELETED=128` (удалено в корзину),
`MEDIA=512` (содержит медиа), `HIDDEN=65536` (приветственное от сообщества),
`DELETED_ALL=131072` (**удалено для всех** — важно для синхронизации в Pinok).

Флаги диалога `VkPeerFlag`: `IMPORTANT=1`, `UNANSWERED=2`.

Идентификаторы платформ `VkPlatform` (полезно для отображения «откуда сидит
пользователь»): `MOBILE=1`, `IPHONE=2`, `IPAD=3`, `ANDROID=4`, `WPHONE=5`,
`WINDOWS=6`, `WEB=7`.

### 5.2. random_id и дедупликация
Источник подтверждает **существование и важность `random_id`** косвенно:
* режим Long Poll `GET_RANDOM_ID = 128` возвращает `random_id` в событиях;
* соединение с `messages.getLongPollHistory`.

Прямое описание «`random_id` передаётся в `messages.send` для дедупликации сообщения»
в этом источнике **не найдено** — это уровень сырого API (первоисточник: `vk.com/dev/messages.send`).

### 5.3. Лимиты вложений на сообщение, keep_snippets, keep_forward_messages
Эти ограничения и параметры в этом источнике **не найдены** (не уровень Python-обёртки).

### 5.4. execute и батчинг (подтверждено источником)
* `VkFunction(code, args=None, clean_args=None, return_raw=False)` — обёртка над методом
  `execute` (VKScript). `args` конвертируются в JSON, `clean_args` вставляются как строки.
* `VkRequestsPool(vk_session)` — объединяет **несколько вызовов API в один запрос**
  `execute`. Способы: менеджер контекста (запросы накапливаются, выполняются при
  закрытии) или объект-пул с `execute()`.
  * `pool.method(method, values)` — добавить запрос, возвращает `RequestResult`
    с полями `ok` (bool), `result`, `error`.
  * `vk_request_one_param_pool(vk_session, method, key, values, default_values=None)` —
    если меняется только один параметр; `values` максимум **25** элементов.
* `VkTools.get_all_iter/get_all/get_all_slow_iter/get_all_slow` — выгрузка всех
  элементов пагинации (`count`+`items`/`users`), с предупреждением использовать
  итераторы, а не загрузку всего в память.

---

## 6. Недокументированное / экспериментальное (по источнику)

1. `NOTIFY` право (`VkUserPermissions.NOTIFY = 1`) — право объявлено, но помечено:
   **«Не работает с этой библиотекой»**. Если Pinok наткнётся на проблемы с
   уведомлениями через `vk_api` — это ожидаемо для этого модуля.
2. `client_secret` (Client Credentials Flow) — источник официально помечает как
   **устаревший** способ авторизации, рекомендует сервисный ключ.
3. `VkAudio` (аудио-модуль с `get_url`-, `upload_audio`, `edit_audio`-, `search`
   методами) — работает через **закрытый/неофициальный** аудио-API;
   право `AUDIO` при отсутствии доступа к закрытому аудио-API позволяет **только
   загрузку аудио**.
4. `raw=True` для `execute` — позволяет прочитать массив `execute_errors` (деталь,
   которую в обычном вызове обёртка скрывает).

---

## 7. Полезные ссылки

**Первоисточник (этот документ):**
* `https://vk-api.readthedocs.io/en/latest/` — оглавление (index);
* `.../vk_api.html` — класс `VkApi`, `VkApi.method`, `VkUserPermissions`;
* `.../upload.html` — класс `VkUpload` (загрузка файлов);
* `.../longpoll.html` — `VkLongPoll`, события, флаги, режимы;
* `.../exceptions.html` — иерархия исключений;
* `.../tools.html` — `VkTools` (пагинация);
* `.../requests_pool.html` — `VkRequestsPool` (батчинг через execute);
* `.../execute.html` — `VkFunction`.

**Ссылки на официальную документацию ВКонтакте, на которые указывает источник**
(и где лежат детали, отсутствующие в этой обёртке):
* `https://vk.com/dev/messages.send` — сигнатура `messages.send`, `random_id`;
* `https://vk.com/dev/messages.edit` — правка сообщений, поведение `attachment`;
* `https://vk.com/dev/upload_files` — загрузка файлов;
* `https://vk.com/dev/using_longpoll` и `.../using_longpoll_2?f=3...` — Long Poll,
  структура событий;
* `https://vk.com/dev/permissions` — список прав;
* `https://vk.com/dev/captcha_error`, `https://vk.com/dev/need_validation` — капча,
  проверка безопасности;
* `https://vk.com/dev/objects/attachments` — формат attachment-строк (в этом
  источнике отсутствует);
* `https://vk.com/dev/client_cred_flow` — устаревший Client Credentials Flow;
* GitHub-исходники библиотеки: `https://github.com/python273/vk_api`.

---

## 8. Честный реестр «не найдено в источнике»

| Пункт | Статус в этом источнике |
|---|---|
| Базовый URL `https://api.vk.com/method/...` | не найдено |
| Параметр `v=` (версия в каждом запросе) | не найдено (есть `api_version='5.92'` в конструкторе) |
| Ответ `error_code` / `error_msg` | не найдено (ошибки — через исключения) |
| Сигнатуры `messages.send/edit/delete` (peer_id, cmid/conversation_message_id) | не найдено |
| `random_id` в `messages.send` | не подтверждено (есть только флаг получения `random_id` в Long Poll) |
| Реакция `messages.edit` на отсутствие vs `""` в `attachment` | не найдено |
| `keep_snippets`, `keep_forward_messages` | не найдено |
| Формат attachment-строки и полный перечень типов | не найдено |
| Имена upload-методов (`docs.getMessagesUploadServer`, `photos.getMessagesUploadServer`) | не найдено (паттерн загрузки подтверждён через `VkUpload`) |
| Хосты upload-серверов `pu.vk.ru` | не найдено |
| Лимит вложений на сообщение | не найдено |

Все строки выше требуют первоисточника `vk.com/dev` и/или проверки на живом API.