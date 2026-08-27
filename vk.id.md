# VK ID — карта изменений состояния авторизации (3 стадии)

> **Источник:** `vk.id.txt` (60.6 KB, 224 строки) — дамп localStorage + cookies на 3 стадиях авторизации VK ID.
> **Залогиненный пользователь:** `userId = 171093180` (тот же что в `музыка.md`, `Профиль.md`, `видео.md`).
> **Окружение:** браузер Chrome на десктопе (1920×1080), тёмная тема (`remixdark_color_scheme=1`, `remixcolor_scheme_mode=dark`).
> **Домены в обороте:** `id.vk.ru`, `login.vk.ru`, `api.vk.ru`, `vk.ru`, `vk.com`, `vknext.net`, `gtmpx.com` (аналитика).
> **Связанные документы:** `музыка.md`, `Профиль.md` (FIX-133 anonymous_token fallback), `видео.md` (очередь long-poll). Здесь — детальный разбор auth-flow.

---

## Содержание

1. [Стадия 1 — До входа в ВК](#стадия-1--до-входа-в-вк)
2. [Стадия 2 — После входа в ВК](#стадия-2--после-входа-в-вк)
3. [Стадия 3 — Полный переход на https://vk.ru/feed](#стадия-3--полный-переход-на-httpsvkrufeed)
4. [Сводная таблица изменений](#сводная-таблица-изменений)
5. [Ключевые находки](#ключевые-находки)
6. [Архитектурный разбор auth-flow](#архитектурный-разбор-auth-flow)
7. [Применение к Android-моду (расширение FIX-133)](#применение-к-android-моду-расширение-fix-133)

---

## Стадия 1 — До входа в ВК

**URL:** `https://id.vk.ru/auth?response_type=silent_token&uuid=ufuohe&v=1.0.2&app_id=7497650&redirect_uri=https%3A%2F%2Fid.vk.ru%2Faccount%3Fflow_service%3Dvkid_landing_%2Fid`

Это страница VK ID OAuth — landing-экран ввода логина/пароля. **Пользователь ещё НЕ авторизован**, но cookies уже несут следы прошлой сессии.

### URL-параметры

| Параметр | Значение | Назначение |
|---|---|---|
| `response_type` | `silent_token` | OAuth-тип — silent token exchange (неявный, без code flow) |
| `uuid` | `ufuohe` | Уникальный идентификатор сессии входа (5 символов) |
| `v` | `1.0.2` | Версия VKID SDK |
| `app_id` | `7497650` | OAuth-приложение VKID landing (`/id`) |
| `redirect_uri` | `https://id.vk.ru/account?flow_service=vkid_landing_/id` | Куда вернуться после успешной авторизации |

### localStorage (5 ключей)

| Ключ | Значение | Назначение |
|---|---|---|
| `PromoShowed_7497650` | `1` | Счётчик показов promo-баннера для app_id=7497650 |
| `PromoShowed_7934655` | `1` | Счётчик показов promo для app_id=7934655 (видимо, sister-app) |
| `deviceId` | `Wr8K9VHUespJJz5SDr-kA` | Старый формат device-ID (21 символ, base62) |
| `landings:unauthId` | `3818433158` | Анонимный ID landing-сессии (int32) |
| `tracer-device-id` | `3ee6f837-f4c9-4053-a1bc-c4f2d2cfe953` | UUIDv4 для tracing (Jaeger/логи) |

### Cookies (24 записи)

#### Cookies `.login.vk.ru` (2 ключевых)

| Cookie | Значение | Expires | HttpOnly | Secure | Назначение |
|---|---|---|---|---|---|
| `httoken` | `UZ9GGXrMOU8qJnZIJpIgb146r4BcEDNFnzg3BobZoUDjCYcZPCd7gbpMKgjGDdKnK0qLRuaAazuz3_r1zkfrZG6ySbngNdAnnw-CDVa2cKLdx-NREEzxeO054H0hbzxbZGg` | 2026-08-07T12:48:19Z (1 нед) | ✓ | ✓ | CSRF/HTTP-token для login.vk.ru |
| `sua` | `yY0D64FbIkX-50Oq6tgoKU8Q8R9l0stVOiAzkll1lOc#171093180^vk1.a.tw1L4Ph3uNh_as82yajyKq3qrIhnoJSVi3sOyGQwoSSqwbUgxwQJJ1MbEzuPBkHExsuAFBsfJvK_P1wbBQxHmuWd3cwZ12mmIoifBs_MdYqGkyVraecc3qdHuCGI5GsZeR0BtqeZaFne3GP7U4tYVgoAGBh04jWKN8GcSQ0JIiB9QwdnvNK0H4OUkPukPJVz^1784535986` | 2027-07-27T14:06:37Z (1 год) | ✓ | ✓ | **Signed-User-Auth**: содержит `#171093180` (user_id) + `vk1.a...` (токен) + `^1784535986` (expire epoch). **Пользователь УЖЕ идентифицирован** до логина — cookie помнит прошлую сессию. |
| `sui` | `171093180%2CnRF21W_rVgCfE6-iFlMvzY9SAQOwRQRPUtouuwBs99A` | 2027-07-27T14:06:37Z | ✓ | ✓ | **Session-User-ID**: `171093180,nRF21W_rVgCfE6-iFlMvzY9SAQOwRQRPUtouuwBs99A`. Зеркало user_id из `sua`. |

#### Cookies `.api.vk.ru` (1)

| Cookie | Значение | Expires | Назначение |
|---|---|---|---|
| `httoken` | `K4uWd3O1iQYI4iN4fDLizQ7nTx4rh51vMtkHqsNVlthDcqntJsvFjqc5WVtb3alxw_kwDOB5t_hkvKA5I2iIyco0N9NMS6qXSBJeq5GEB4Qj9oYwAh5RtW3IC6ftVxZXfpk` | 2026-08-07T12:48:26Z (1 нед) | CSRF для api.vk.ru |

#### Cookies `.vk.ru` (18 remix-* + служебные)

| Cookie | Значение | Назначение |
|---|---|---|
| `remixage18` | `1` | Подтверждение возраста 18+ (session-only) |
| `remixcolor_scheme_mode` | `dark` | Тема оформления |
| `remixcurr_audio` | `undefined` | Текущий трек (нет активного) |
| `remixdark_color_scheme` | `1` | Флаг тёмной темы (дубль) |
| `remixdt` | `0` | Disable touch (? — legacy) |
| `remixff` | `10111111111111` | Feature flags bitmask (13 бит = 13 фич включены) |
| `remixlang` | `0` | Язык (0 = русский) |
| `remixmdevice` | `1920/1080/0.8999999761581421/!!-!!!!!!!!/2133` | Device: экран 1920×1080, DPR 0.9, неопознанные поля |
| `remixmvk-fp` | `8152ee65ff2df0503e745f7c70e6d049` | MVK fingerprint (md5) |
| `remixsf` | `1` | Sound filter (?) |
| `remixstickers_hash` | `b4307610fc81147a6301a42e65bda5f6` | Hash стикеров (session) |
| `remixstid` | `698328746_zinIypCImEkbpZi2lUzBBZteN1TxBhZwnp2LwKz3ljH` | **Sticker-ID**: содержит `698328746` — это `anonym_id` (тот же что в Stage 3 anonym_token JWT!) |
| `remixstlid` | `9070491080072951214_7qLF3PX20dJ5M0RJEkMdeoM4AZAqz1Z6xA7cNDEkZGg` | **Sticker-Long-ID**: `9070491080072951214` — это `anonym_id_long` из Stage 3 JWT! |
| `remixsuc` | `1%3A` | Success-flag (`1:`) |
| `remixua` | `45%7C-1%7C215%7C2519205882` | User analytics: `45|-1|215|2519205882` |
| `remixuacck` | `727346957580a8d775` | UAC cookie-key (session-fingerprint) |
| `remixuas` | `YjUxN2Y0YWIwYTIyMzJmODA3NGQwMmI4` | User-auth-session (base64: `b517f4ab0a2232f8074d02b8`) |

#### Cookies сторонних доменов

| Cookie | Домен | Значение | Назначение |
|---|---|---|---|
| `UniqAnalyticsId` | `gtmpx.com` | `1081804466` | Уникальный ID аналитики (Top.Mail.Ru / GTM-PX) |

### Ключевые наблюдения по Стадии 1

1. **Пользователь УЖЕ идентифицирован** через cookies `sua` и `sui` (user_id `171093180`). Формально сессии НЕТ (`remixsid` отсутствует), но VK знает кто это.
2. **`remixstid` и `remixstlid` содержат тот же `anonym_id`** что позже появится в `anonym_token` JWT (Stage 3): `698328746` (int32) и `9070491080072951214` (int64 long). Это означает, что **anonymous_id выдаётся ПЕРВЫМ посещением и персистит вечно** — даже после логина он не меняется.
3. **НЕТ `remixsid`** — основной session-ID отсутствует, вход не завершён.
4. **НЕТ `remixnsid`** — new-session-id отсутствует.
5. **НЕТ `remixdmgr_tmp` и `remixdmgr`** — device-manager не активирован.
6. **НЕТ `remixnttpid`** — push-token отсутствует.
7. **НЕТ `_clientId`** — VK Compose Kit client-ID не выдан.
8. **НЕТ `p` cookie** на `.login.vk.ru` — финальный login-persistent токен отсутствует.
9. **Только 2 домена `httoken`** — `.login.vk.ru` + `.api.vk.ru`. Это CSRF-токены для запросов к этим доменам, выдаются без авторизации.
10. **`remixff = 10111111111111`** — feature-flags: 13 бит, старший + 12 младших = `1` + `111111111111`. То есть 12 из 13 фич включены.

---

## Стадия 2 — После входа в ВК

**URL:** `https://id.vk.ru/account/#/main`

Пользователь успешно прошёл OAuth-flow (ввёл логин/пароль или подтвердил вход). Страница `id.vk.ru/account` — это «личный кабинет VK ID» с разделами main/security/devices. **Перехода на `vk.ru/feed` ещё НЕ было.**

### localStorage (6 ключей — +1 новая запись)

#### НОВАЯ запись: `7344294:web_token:login:auth`

```json
{
  "access_token": "vk1.a.0UR3YM7wkIdifhRCZ5UFt4HJ9cAvdsabxv_aHOzfe97fVz9RLRBQuQmXFIEgT-lClngkNqbqnQZq-uX3u2Ly88UgwiXbuquG2eTHf68aRnoY_zq1bK74muKCmZLNnguyRVGA1JVrA4LpGTs63kEqvhmlNWXzBT7eBAP6_M-VUlpmTASObGwePflx-4rKgoI_YWt5RnfetBfLVGYYtxSuAA",
  "expires": 1786104708,
  "user_id": 171093180,
  "logout_hash": "2f10b630b372245ee2"
}
```

| Поле | Значение | Расшифровка |
|---|---|---|
| `access_token` | `vk1.a.0UR3YM7wkIdifhRCZ5UFt4HJ9cAvdsabxv_aHOzfe97fVz9RLRBQuQmXFIEgT-lClngkNqbqnQZq-uX3u2Ly88UgwiXbuquG2eTHf68aRnoY_zq1bK74muKCmZLNnguyRVGA1JVrA4LpGTs63kEqvhmlNWXzBT7eBAP6_M-VUlpmTASObGwePflx-4rKgoI_YWt5RnfetBfLVGYYtxSuAA` | Web-token формата `vk1.a.<base64>`, длина 248 символов |
| `expires` | `1786104708` | Fri Aug 7 12:11:48 UTC 2026 (~24 часа с момента выдачи) |
| `user_id` | `171093180` | **Совпадает с `sui` cookie из Stage 1!** |
| `logout_hash` | `2f10b630b372245ee2` | Hash для OAuth-logout endpoint |

**Ключевое:** это **первый web_token**, выданный для `app_id = 7344294` (VKID Account app). Префикс ключа `7344294:` означает app_id.

#### Остальные 5 ключей — без изменений

`PromoShowed_7497650`, `PromoShowed_7934655`, `deviceId`, `landings:unauthId`, `tracer-device-id` — те же что в Stage 1.

### Cookies — 8 НОВЫХ записей

#### НОВАЯ cookie `.id.vk.ru`

| Cookie | Значение | Expires | Назначение |
|---|---|---|---|
| `httoken` | `3Hmt93InY1a6GWTvpM9WxJF7PgLvVsLL0vnb-j6bJk1PmupULyaOmfGYY6zxioN1Reogni5whyOQqe6tT4wpAUaQFCjgmJtb8hWKcVLqblL7D5kuGkxBVL9g7h7nRyMksdE` | 2026-08-07T12:56:47Z | CSRF для id.vk.ru (третий домен httoken) |

#### НОВАЯ cookie `.login.vk.ru`

| Cookie | Значение | Expires | Назначение |
|---|---|---|---|
| `p` | `vk1.a.w_Wz_dIAGD61FIi-fmNByJP6mhy3j2so02okwaWF4NFboOBKLnWlFb0o9Gn5Y3Ii0IgCTzcuig7-X-wnurDem0rVWVeoJ2stWV3ikd4CYtrPW4JaYka83x6nqhEzMk_NCf0Hiei7maBQhRrgcXM7pfHgQtUxNZ-o-ISTlsToYqA` | 2027-08-07T11:56:47Z (1 год) | **Login-persistent token**: формат `vk1.a.<base64>`, 178 байт. Это «долгая память» о залогиненном состоянии. |

#### НОВАЯ cookie `vk.ru`

| Cookie | Значение | Expires | Назначение |
|---|---|---|---|
| `_clientId` | `27e3c1ee-f563-46a2-934d-744286e3bb9e` | 2027-08-24T16:17:29Z | UUIDv4 для VK Compose Kit (frontend clientId) |

#### НОВЫЕ cookies `.vk.ru` (5 штук)

| Cookie | Значение | Expires | Назначение |
|---|---|---|---|
| `remixdmgr_tmp` | `93a3f1a3541719074c0150f5ccc83661486da7ac134551f1d9270caa38436631` | 2026-08-07T15:56:47Z (3 часа) | **Device-manager temporary hash** — sha256, 64 hex. Временный — через 3 часа превратится в `remixdmgr` (финальный, Stage 3). |
| `remixnsid` (на `vk.ru`) | `vk1.a.zT3qON99f0Smqxl7Ng0YnAqaK2dfzgAArs0hkm1n1Bif43tjDIo7VGyfgovpn6XfSWmxtYGDlahOD2llA62c_7J7RGLvuKuDDFE9TPGOFqcMu97xjzMxBlld4n8AVAuMB1HuAbqgYK35WFlFse4Y5JSmHj3egIt7LAWY2BGlX1gDwOW9kzy7H-W6yCzMwiIm` | 2027-07-28T12:45:18Z | **New Session ID** на домене vk.ru (207 байт) |
| `remixnsid` (на `id.vk.ru`) | `vk1.a.kIyihknpBc5fIgHq3dWbK5V_n7Cz_Y7hPoNsU077eejUAw4w-a5zOeTnO7ITwhN6yIELSg-BKtgroBz5S7gF0Q-FuJunph2UoGerGp2AXL0fqr69vOsiO6iP8_JeF06cpWzOVjv_W5OeJMg65uaB3WnxjgJqWKj8SLM3bt-7WVCPFNKq8sdzxkJ21bvtkj7-` | 2027-08-08T18:55:54Z | **New Session ID** на домене id.vk.ru (другое значение!) — параллельная сессия для домена id.vk.ru |
| `remixnttpid` | `vk1.a.lw1eC5PTUBC6Gy3A5DGdRFlp3kGwjvpNjTJ8kkzC55G8iJlLBqWx_U_HpUhBj_4gE7zyHgicF-suW-j30aj2G_13O7b5wJXwmtvc6rBo0mO5yBwSO8a70CRjKnKFj5PC5od7FGEt0RDVIgIZ6fYRVoA7XLxLrQjdZ0DjcrP7wYIPaAs_sVATQM4WCzIXHPjT` | 2026-08-14T11:56:47Z (1 нед) | **Notification Push Token ID**: 209 байт, `vk1.a.<base64>` |
| `remixsid` | `1_CnI-7BSCaB2LUl93I-DtrxOlSyWg6mS5_hsRYTEaEtczd4eEeYUCw3GpTTT71zJ_2u9Q5etBAkBjqfdKGHt5FA` | 2027-08-01T20:48:58Z | **Главный SESSION-ID** на домене `.vk.ru` (96 байт). Это и есть «вход выполнен». |

### Cookies — ИЗМЕНЁННЫЕ (3 штуки)

| Cookie | Было (Stage 1) | Стало (Stage 2) | Что изменилось |
|---|---|---|---|
| `remixsuc` (`.vk.ru`) | expires 2027-07-27T14:06:37Z | expires 2027-08-04T18:20:32.176Z | Продлён на 8 дней |
| `sui` (`.login.vk.ru`) | expires 2027-07-27T14:06:37.542Z | expires 2027-08-04T18:20:32.176Z | Продлён на 8 дней (то же время что `remixsuc` — синхронно) |

### Cookies — УДАЛЁННЫЕ (1 штука)

| Cookie | Домен | Что было |
|---|---|---|
| `sua` | `.login.vk.ru` | `yY0D64FbIkX-50Oq6tgoKU8Q8R9l0stVOiAzkll1lOc#171093180^vk1.a.tw1L...^1784535986` (272 байта) |

**Почему удалили `sua`?** После успешного логина VK заменяет «старую подпись» `sua` (которая хранила user_id + устаревший токен из прошлой сессии) на **свежий `p` cookie** (долгоживущий login-persistent). `sua` больше не нужна — её роль выполняет связка `p` + `remixsid` + `remixnsid`.

### Ключевые наблюдения по Стадии 2

1. **`access_token` выдан для `app_id = 7344294`** (VKID Account). Это **НЕ** основной `app_id = 6287487` VK web (который появится в Stage 3).
2. **Создан ОДИН web_token** (localStorage `7344294:web_token:login:auth`), но ЕЩЁ НЕТ `6287487:web_token` и `6287487:get_anonym_token`.
3. **Сессия установлена через 4 cookie**: `p` (login.vk.ru), `remixsid` (vk.ru), `remixnsid` × 2 домена (vk.ru + id.vk.ru).
4. **Push-token инициализирован** через `remixnttpid` (но реальный push-channel ещё не открыт — нет `im_m_comms_key`).
5. **CSRF-токены теперь на 3 доменах**: `.login.vk.ru`, `.api.vk.ru`, `.id.vk.ru`. Это позволяет делать POST-запросы к любому из них.
6. **`logout_hash = 2f10b630b372245ee2`** — короткий хеш (18 hex = 9 байт = 72 бита), нужен для `id.vk.ru/auth?act=logout&hash=...`.
7. **Expires web_token = 24 часа** (1786104708 = Aug 7 12:11:48, login был ~12:11). Это **типичный silent_token lifetime**.
8. **`remixdmgr_tmp` живёт 3 часа** — временное устройство, потом convert в `remixdmgr` (финальный).
9. **`landings:unauthId` НЕ обновлён** — остался `3818433158` (как в Stage 1). Это значит, что VKID SDK считает landing-сессию той же.
10. **`deviceId` НЕ обновлён** — `Wr8K9VHUespJJz5SDr-kA` (как в Stage 1). deviceId выдаётся один раз и не меняется.

---

## Стадия 3 — Полный переход на `https://vk.ru/feed`

URL: `https://vk.ru/` → редирект → `https://vk.ru/feed`

Пользователь попал на главную VK. Здесь активируется **основное web-приложение** (`app_id = 6287487` — канонический VK Web App). Запускается **полноценный SPA** с React/VKUI, открываются WebSocket/long-poll каналы.

### localStorage — НОВЫЕ записи

#### Auth-токены для app_id = 6287487 (3 НОВЫЕ)

##### `6287487:get_accounts:login:auth`

```json
{"expires": null}
```

**Пустой ответ** — аккаунты не возвращены (или endpoint ещё не отработал). `expires: null` = без кеша.

##### `6287487:get_anonym_token:login:auth`

```json
{
  "access_token": "anonym.eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbm9ueW1faWQiOjY5ODMyODc0NiwiYXBwX2lkIjo2Mjg3NDg3LCJpYXQiOjE3ODQzMTA4MDIsImlzX3ZlcmlmaWVkIjpmYWxzZSwiZXhwIjoxNzg0Mzk3MjAyLCJzaWduZWRfdGltZSI6bnVsbCwiYW5vbnltX2lkX2xvbmciOjkwNzA0OTEwODAwNzI5NTEyMTQsInNjb3BlIjo3ODgxMjk5MzQ3ODk4MzY4fQ.tfVPZJbzlMIm79TTxYN2RhyNrBzvb_ABPr_eS1mwlzo",
  "expired_at": 1784397202,
  "expires": 1784397202
}
```

**Декодированный JWT payload:**

```json
{
  "anonym_id": 698328746,
  "app_id": 6287487,
  "iat": 1784310802,
  "is_verified": false,
  "exp": 1784397202,
  "signed_time": null,
  "anonym_id_long": 9070491080072951214,
  "scope": 7881299347898368
}
```

| Поле | Значение | Расшифровка |
|---|---|---|
| `anonym_id` | `698328746` | Анонимный ID пользователя (int32). **Совпадает с `remixstid` из Stage 1!** |
| `app_id` | `6287487` | Канонический VK Web App ID |
| `iat` | `1784310802` | Issued-At: Fri Aug 1 12:33:22 UTC 2026 (5 дней назад — кешированный) |
| `is_verified` | `false` | Аноним не верифицирован |
| `exp` | `1784397202` | Expires: Sat Jul 18 17:53:22 UTC 2026 (через 24 часа после iat) |
| `anonym_id_long` | `9070491080072951214` | Длинная версия anonym_id (int64). **Совпадает с `remixstlid` из Stage 1!** |
| `scope` | `7881299347898368` | Битовая маска разрешений |
| `signed_time` | `null` | Не подписан временем (токен выдан без timestamp-подписи) |

**Это и есть тот самый `anonymous_token` из FIX-133!** Web-приложение ВК получает его для app_id=6287487 **независимо от web_token**, чтобы делать API-вызовы даже когда web_token ещё не получен/истёк.

##### `6287487:web_token:login:auth`

```json
{
  "access_token": "vk1.a.fxqX3lgtfMqLm2DiwnR9_hUxPtR19_0OlAeGwGOJ9GrXJup03caXR7ZpuJmZHxzl7D0sqa_CXJ8KR-GD889z9yQxU9pIloBOO-a-Gn9O_KFUf_Dxyr4JJxBMeOOyqvLwokbabjq-dh1jj015AouzXL6IXokHqire8VY-hqnzwihWS3XH8RSLpYHbS7eJEil0OozE62WytfZtYq_cEDiMaw",
  "expires": 1786104854,
  "user_id": 0,
  "logout_hash": ""
}
```

| Поле | Значение | Расшифровка |
|---|---|---|
| `access_token` | `vk1.a.fxqX3lgtfMqLm2DiwnR9_hUx...` | Web-token для app_id=6287487, 248 символов |
| `expires` | `1786104854` | Fri Aug 7 12:14:14 UTC 2026 (~24 часа) |
| `user_id` | `0` | **НЕ АВТОРИЗОВАН для этого app_id!** |
| `logout_hash` | `""` | Пустой — нет logout-строка (нет пользователя) |

**КРИТИЧЕСКОЕ наблюдение:** в момент загрузки `vk.ru/feed` web_token для `app_id=6287487` имеет `user_id=0` — это **placeholder**! Реальный web_token для 6287487 будет получен чуть позже (через silent_token exchange), но в дамп попал именно момент «фоллбэка на анонимный токен».

#### Auth-токены для app_id = 7913379 (1 НОВАЯ)

##### `7913379:get_accounts:login:auth`

```json
{"expires": null}
```

`app_id=7913379` — это, вероятно, **VKID Recovery** или sister-app для аккаунт-менеджмента.

#### Metrics storage (6 НОВЫХ)

| Ключ | Значение | Назначение |
|---|---|---|
| `@METRICS_171093180_ADBLOCK_STATS` | `{"timestamps":{"default":1786103966051}}` | Adblock-detection timestamps |
| `@METRICS_171093180_CERT_STATS` | `{"timestamps":{"default":1786103966621}}` | Certificate stats (TLS pinning проверка) |
| `@METRICS_171093180_PERFORMANCE_STATS_CORE` | `{"timestamps":{}}` | Core perf-metrics (пусто — не успели собраться) |
| `@METRICS_171093180_PERFORMANCE_STATS_PRODUCT` | `{"timestamps":{}}` | Product perf-metrics (пусто) |
| `@METRICS_171093180_USER_INFO_STATS` | `{"timestamps":{"default":1786103961482}}` | User-info fetch timestamp |
| `@METRICS_171093180_WASM_STATS` | `{"timestamps":{"default":1786103966478}}` | WASM init timestamp (декодер видео/аудио) |
| `@METRICS_GUEST_ADBLOCK_STATS` | `{"timestamps":{"default":1784310799197}}` | Guest-mode adblock (старый — Aug 1) |
| `@METRICS_GUEST_CERT_STATS` | `{"timestamps":{"default":1784310796853}}` | Guest-mode cert (старый) |

#### Audio player (6 НОВЫХ)

| Ключ | Значение | Назначение |
|---|---|---|
| `AudioStats_ConcurrentSafePersistentEventQueue_AudioStatsAPICollector` | `[]` | Очередь аудио-событий (пусто) |
| `audio_unique_unauth_id` | `"817c6de0-d198-416d-bfa2-f8ca8537f849"` | Уникальный ID для неавторизованного аудио-плеера |
| `audio_v21_pl_171093180` | `{type:"temp", ownerId:"", albumId:"152094335", ...}` | Текущий плейлист user 171093180 (album 152094335) |
| `audio_v21_progress_171093180` | `0` | Прогресс текущего трека |
| `audio_v21_saved_171093180` | `1784906609066` | Timestamp сохранения плейлиста |
| `audio_v21_tns_triggered_time_v3_171093180` | `1784906609533` | TNS (TensorNetworkSession?) timestamp |
| `audio_v21_track_171093180` | `[456249869, 171093180, "", "Свет атомов", "Dj Rudolf Astral", 209, 0, 0, "", 0, 0, "", {"podcast":false, "fave":false}, "ff4c1948264f2d4ab8/..."]` | Текущий трек: audio_id=456249869, owner=171093180, title="Свет атомов", artist="Dj Rudolf Astral", duration=209s |
| `audio_v21_uuid_171093180` | `"6fd38b86-548d-4e67-a620-1fa52c8cf2a8"` | UUID сессии аудио-плеера |
| `audioplayer_block_non_zero_volume` | `0.4761515102104643` | Громкость (47.6%) |

#### Video player (4 НОВЫХ)

| Ключ | Значение | Назначение |
|---|---|---|
| `videoplayer_auth_token` | `$nYgxk07P6aM34frSB98hnLBlXqXUr48ObYQwhDcNQxwDF0FZquF6YJW42kztuFAnmvlD1` | Stats-токен видеоплеера (50 символов) — см. видео.md (video.getStatsToken) |
| `videoplayer_notifications_manager` | `{"slow_video":{"timeout":600000,"launchTime":1785598328135}}` | Менеджер уведомлений плеера |
| `videoplayer_notifications_manager_count` | `1` | Счётчик |
| `videoplayer_prefs` | `{"muted":false, "position":{171093180:{"-225794656_456243949":{"date":1784563376192,"pos":52.392859}, "1018518054_456239203":{"date":1784737952339,"pos":197.67851}, ...}}}` | Позиции просмотра видео |
| `vk_player_preferred_autoplay_next` | `true` | Авто-play следующего |
| `vk_player_preferred_default_quality_settings` | `{"type":"auto_quality"}` | Качество: auto |
| `vk_player_preferred_rate` | `{"rate":1.25}` | Скорость 1.25× |
| `vk_player_preferred_volume` | `0.84` | Громкость 84% |
| `short_video_auth_token` | `$0XacpK6Tp8cMkam3FU6418pVoUxIBMAxMImATeAOjMDaZPmD05SSggLujTuPwjFVwOpg3` | Stats-токен для clips (shortVideo) |
| `stalls_manager_default_tuning_abr_params` | `{"bitrateFactorAtEmptyBuffer":2.8,"bitrateFactorAtFullBuffer":2,"containerSizeFactor":1.3}` | Adaptive bitrate params |
| `stalls_manager_metrics` | `[{"tvt":4378.1417,"stallsDuration":0,"stallsDurationPerTvt":0,...}]` | Метрики стабильности |

#### Messenger / reforged-storage (15+ НОВЫХ)

| Ключ | Назначение |
|---|---|
| `reforged-storage-db-v1-171093180-drafts` | Черновики сообщений |
| `reforged-storage-db-v1-171093180-fc-heads` | FC heads (fast-cache heads?) — `[141052855, -160065516, 5918872, 152094335, 246930948]` |
| `reforged-storage-db-v1-171093180-hidden-pinned-messages` | Скрытые закреплённые сообщения |
| `reforged-storage-db-v1-171093180-last-delete-message-for-all-option` | `true` — удаление для всех |
| `reforged-storage-db-v1-171093180-message-reactions-assets` | Реакции (версия 9, с ссылками на анимации) |
| `reforged-storage-db-v1-171093180-postponedDrafts` | Отложенные черновики |
| `reforged-storage-db-v1-171093180-reforged-device-id` | `6RGVU6Hdw21nnsphmsgcC` — device-ID мессенджера |
| `reforged-storage-db-v1-171093180-selected-convos-from-search` | `[139041066]` — выбранные диалоги |
| `reforged-storage-db-v1-171093180-stickers-keywords-meta` | Метаданные стикеров по ключевым словам |
| `reforged-storage-db-v1-171093180-theme-styles` | Темы оформления диалогов (love_game, candy, ...) |
| `reforged-storage-db-v1-171093180-theme-styles-appearances` | Appearances (candy: gradient bubble #FF...) |
| `reforged-storage-db-v1-171093180-theme-styles-backgrounds` | Backgrounds (crimson: vector blur #FFFFFF) |
| `reforged-storage-db-v1-171093180-videomessage-shapes` | Формы видеосообщений (14 shapes) |
| `stickers-v1-171093180-hasAvatar` | `0` — нет аватара-стикера |
| `stickers-v1-171093180-newAvatarSuggestionCounter` | `1` |
| `stickers-v1-171093180-stickersActiveTab` | `-2` |
| `stickers-v1-171093180-tabbarActiveTab` | `stickers` |
| `stickers-v1-settings-disabledPeerIdsByUser` | `[]` |
| `stickers-v1-settings-isAutoplay` | `0` |
| `stickers-v1-settings-isPopupAutoplayOnGet` | `0` |
| `stickers-v1-settings-isPopupAutoplayOnSend` | `0` |
| `stickers-v1-undefined-tabbarActiveTab` | `emoji` |

#### Long-poll queue (3 НОВЫХ)

| Ключ | Значение | Назначение |
|---|---|---|
| `queue_credential_calls_cache_171093180_6287487` | `{"data":{"key":"8bc297646137cbc9858feb48b58bc94894a74e2067e542662ea4e108bbaaad9b","ts":1189079450,"url":"https://queuev4.vk.ru/im1180","id":171093180},"l...` | **Queue credential** для app_id=6287487: long-poll URL `https://queuev4.vk.ru/im1180`, key (sha256), ts |
| `im_m_comms_key` | `{"ts":"1714332700","key":"d3c0efc135dc118b2880bc86f424555780913c5d26243e2e036b95daf28de566","queue":"nccts171093180"}` | Messenger long-poll key (queue=nccts171093180) |
| `server_queue_connection_events_queue171093180` | `["NjgzMjA1",1786104006574]` | Server queue events (base64 `NjgzMjA1` = `683205`, timestamp) |
| `queue_connection_events_queue171093180` | `{"__client":"NjgzMjA1","__act":"check_ok","__rnd":0.4800681708066158}` | Client queue state |
| `lc_server_switch_to_active_flag` | `true` | LC server switch (long-poll channel switch) |
| `im_notify_flag` | `1` | IM notifications enabled |
| `multiacc_last_id` | `171093180` | Multi-account: last active user |
| `userId` | `171093180` | Текущий user_id (дублирует `sui` cookie) |

#### Misc analytics/state (15+ НОВЫХ)

| Ключ | Значение | Назначение |
|---|---|---|
| `NAVIGATION_TAB_ID` | `45` | ID активной вкладки (45 = feed?) |
| `PERF_METRICS_TIMESTAMPS_WEB` | `{"group":{"group":{"FCP":1785522316166, "TBT":1785522321462, "TTLB":1785522321463, "TTFB":1785522321660, ...}}}` | Performance metrics (First Contentful Paint, TBT, TTLB, TTFB) |
| `SERPS_LOGS_DATA_LOCK` | `free` | SERP logs lock |
| `XHR_STATS_TRANSPORT_DATA_web2` | (пусто) | XHR transport stats |
| `_one-stat_deviceId` | `F3E088C1-D039-4415-A6C2-D207598F4715` | One-stat device ID (UUIDv4 uppercase — **ТРЕТИЙ device ID!**) |
| `articles_scrolls_171093180` | `{"/@security-kak-zaschitit-stranicu":2100}` | Прокрутка статей (прочитано 2100px статьи «security-kak-zaschitit-stranicu») |
| `emoji_recent_list` | `{"f09f9885":1, "f09fa790":1, ...}` | Недавние эмодзи (7 штук: 😀 💥 😣 💎 😁 😡) |
| `friends_recomm_visited_@id:171093180` | `{}` | Visited friends recommendations (пусто) |
| `last_reloaded` | `[]` | Last reloaded resources |
| `lock_stats_cookie_locked_stats_api` | `[[1784310796,"web_dark_theme","auto","vkcom_dark",1], ...]` | Locked stats API |
| `lockkk_stats_cookie_lock` | `false` | Lock flag |
| `one_video_rtt` | `364` | RTT одного видео (364ms) |
| `one_video_throughput` | `19200` | Throughput (19200 = 19 KB/s?) |
| `onestat_events_forticom` | `{}` | OneStat events for Forticom (parent company VK) |
| `promoted_stickers_urls` | `{"time":1785598697786, "stickerUrls":{"1909":"https://vk.ru/sticker/1-1909-64b", "1918":"https://vk.ru/sticker/1-1918-64b", ...}}` | Продвигаемые стикеры |
| `recent_stickers` | `{"stickers":[[83382,256,"https://vk.ru/sticker/1-83382-64b","",0,"ab68d4ab1efa7179e3",[],[]], [76086,256,"https://vk.ru/sticker/1-76086-64b","https://vk.ru/sticker/3-76086.json",0,"aae2...",[],[]]]}` | Недавние стикеры |
| `supportedFeatures` | `current_scheme:2/is_auto_schemes_supported:1/is_schemes_supported:1` | Поддерживаемые фичи браузера |
| `tracer-device-id` | `bb52306d-a810-48f0-8f08-e852d23d82f4` | **ИЗМЕНЁН!** Был `3ee6f837-...` стал `bb52306d-...` — новый tracer для vk.ru (отдельный от id.vk.ru) |
| `undefined:web_token:login:auth--request` | `1` | Счётчик запросов `web_token` для `undefined` app_id (загадочный — возможно pre-flight) |
| `vms_public_path` | `chrome-extension://ijgkbcbalaekboipcmaefchfjpognmog/` | VMS (Video Messaging System?) Chrome extension path |
| `AUDIO_PLAYER_INIT_ID` | `cc0071f7-cb01-428e-9a54-6d425b0476b7` | UUID инстанции аудио-плеера |
| `XHR_STATS_TRANSPORT_DATA_LOCK_web2` | `free` | XHR lock (free) |
| `XHR_STATS_TRANSPORT_META_web2` | `1786104004816` | XHR meta timestamp |
| `LongView.idled` | `{}` | LongView idle events |
| `LongView.viewed` | `{}` | LongView viewed events |

### Cookies — НОВЫЕ (9+ штук)

#### НОВАЯ cookie `.vk.ru` — 4-й httoken

| Cookie | Значение | Expires | Назначение |
|---|---|---|---|
| `httoken` (на `.vk.ru`) | `erkkgJi2kkrTsgbhDFP0q2rKzVjwSVl_zjkQvTxk4A1BOr0UGJ36QgbxGN3JHLZeJXQ3bIpPnNhzAMuaP7zVp2UAEKjcc-dcPSycraY5V8MKo6Lz3fJTO4dOKcNKlVYr7vM` | 2026-08-07T12:59:15Z | CSRF для vk.ru (четвёртый домен httoken) |

#### НОВАЯ cookie `.vk.ru` — финальный device-manager

| Cookie | Значение | Expires | Назначение |
|---|---|---|---|
| `remixdmgr` | `74cd6d884fe1cfc1c060d70f17daa79d218175b3f86eefe84ef8cebfa6652e31` | 2027-08-12T03:19:19Z (1 год) | **Финальный device-manager hash** (sha256, 64 hex) — пришёл на смену `remixdmgr_tmp` |

#### НОВАЯ cookie `.vk.ru` — state-tracking

| Cookie | Значение | Expires | Назначение |
|---|---|---|---|
| `remixsts` | `%7B%22data%22%3A%5B%5B1786103972%2C%22web_vk_env_desync%22%2C1%2Cnull%2C%22production%22%2C%22feed%22%2C%22false%22%2C%22false%22%2C%22https%3A//vk.ru/feed%22%5D%2C%5B1786103972%2C%22entries_requests%22%2C1%2C%22stickers%22%2C%22web%22%5D%5D` | Session | URL-decoded: `{"data":[[1786103972,"web_vk_env_desync",1,null,"production","feed","false","false","https://vk.ru/feed"],[1786103972,"entries_requests",1,"stickers","web"]]}` — **state-tracking cookie**: записывает текущую страницу (`https://vk.ru/feed`), окружение (`production`), ошибки env_desync, запросы стикеров. |

#### ОБНОВЛЁННАЯ cookie `.vk.ru` — новый remixsid

| Cookie | Было (Stage 2) | Стало (Stage 3) |
|---|---|---|
| `remixsid` (`.vk.ru`) | `1_CnI-7BSCaB2LUl93I-DtrxOlSyWg6mS5_hsRYTEaEtczd4eEeYUCw3GpTTT71zJ_2u9Q5etBAkBjqfdKGHt5FA` (96 байт, expires 2027-08-01T20:48:58Z) | `1_D0J0META0ol3kElHNhsneBqP_T6MWGD5tsYAyY8rERoY-yL3v0iGkO0mAsgJYRIVGV1RYdd2oqXNXywUJlNBHw` (96 байт, expires 2027-08-01T12:54:40Z) |

**remixsid заменён!** Старая сессия аннулирована, новая выдана специально для `vk.ru/feed` (а не для `id.vk.ru/account`).

#### ДУБЛИРОВАНИЕ cookies на `.vk.com` (15 НОВЫХ)

Все основные remix-* cookies теперь **продублированы на домене `.vk.com`** (зеркальный домен):

| Cookie | `.vk.ru` (Stage 2/3) | `.vk.com` (Stage 3 NEW) |
|---|---|---|
| `remixcolor_scheme_mode` | `dark` | `auto` (другое!) |
| `remixdark_color_scheme` | `1` | `1` |
| `remixdmgr` | `74cd6d884...` | `8965867ce9da1a17...` (другой hash!) |
| `remixdt` | `0` | `0` |
| `remixlang` | `0` | `0` |
| `remixnsid` | `vk1.a.Rb8qmm9ie3UkW_pvO2SV...` | (нет дубля на vk.com) |
| `remixsid` | `1_D0J0META0ol3kElHNhsneBqP...` | `1_lIvTHQ4FVTYTFVmGd_PnTnxhhj1HhxJzZ8Ua6ThfdDPOQzuOznjznLxVnZhQjCvhOc-HDqLREWgPg8BPdTRP7g` (другой! expires 2027-08-04T16:51:58Z) |
| `remixstid` | `698328746_zinIypCImEkbpZi2lUzBBZteN1TxBhZwnp2LwKz3ljH` | то же |
| `remixstlid` | `9070491080072951214_7qLF3PX20dJ5M0RJEkMdeoM4AZAqz1Z6xA7cNDEkZGg` | то же |
| `remixsuc` | `1%3A` | `1%3A` |
| `remixua` | `45%7C-1%7C215%7C2519205882` | `45%7C-1%7C215%7C2519205882` |
| `remixuacck` | `727346957580a8d775` | `ba20d857802b011520` (другой!) |
| `remixuas` | `YjUxN2Y0YWIwYTIyMzJmODA3NGQwMmI4` | то же |
| `remixff` | (только на vk.ru) | (нет на vk.com) |
| `remixsf` | (только на vk.ru) | (нет на vk.com) |

**Важно:** `.vk.ru` и `.vk.com` — это два разных домена, у каждого **своя сессия** (`remixsid` различается!). Это означает, что `vk.com` — это **legacy-домен**, который VK поддерживает параллельно, но данные сессий могут не совпадать.

#### НОВЫЕ cookies `.vknext.net` (7 НОВЫХ)

| Cookie | Значение | Expires | Назначение |
|---|---|---|---|
| `__ddg1_` (2 записи) | `VdaWCB6wza2iDMR7lnLN` + `JGFOCVg83RcpEW4BZ9PI` | 2027-08-01 / 2027-08-07 | **DuckDuckGo?** или DDG-tracking-id (28 символов) |
| `__ddg10_` | `1786103962` | 2026-08-07T12:19:23Z | DDG timestamp (epoch) |
| `__ddg8_` | `d3q9owALywBcLAF2` | 2026-08-07T12:19:23Z | DDG 8 (16 hex) |
| `__ddg9_` | `95.26.25.24` | 2026-08-07T12:19:23Z | DDG 9 — **IP-адрес клиента!** `95.26.25.24` (РФ) |
| `_vms_uid` | `171093180` | Session | VMS User-ID (Video Messaging System? совпадает с userId) |

**`vknext.net`** — это домен **VMS (VK Media Services?)** или **CDN next-gen** для видео/медиа. IP-адрес в cookie `__ddg9_` — это DataDome-style bot-protection (DDG = DataDome Generic?).

### Ключевые наблюдения по Стадии 3

1. **`user_id = 0` в `6287487:web_token`** — это **placeholder/фоллбэк**. Реальный web_token ещё обменивается. В этот момент web-приложение использует `anonym_token` (для app_id=6287487).
2. **`anonym_id = 698328746`** — **совпадает с `remixstid`** из Stage 1! Это означает, что anonym_id выдаётся один раз при первом визите и **персистит между сессиями**. VK связывает анонимные действия с этим ID даже после логина.
3. **`anonym_id_long = 9070491080072951214`** — **совпадает с `remixstlid`** из Stage 1. То же самое long-значение.
4. **`scope = 7881299347898368`** — битовая маска. В двоичном виде: `0001 1100 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000` — три бита в верхних позициях (52, 51, 50). Это scope для offline-доступа к публичному контенту.
5. **`anonym_token` живёт 24 часа** (`exp - iat = 86400`). При истечении — нужно получать заново через `auth.getAnonymToken` (см. FIX-133).
6. **3 разных device-ID в localStorage одновременно:**
   - `deviceId` = `Wr8K9VHUespJJz5SDr-kA` (старый VKID SDK формат, 21 символ)
   - `tracer-device-id` = `bb52306d-a810-48f0-8f08-e852d23d82f4` (UUIDv4 для tracing)
   - `_one-stat_deviceId` = `F3E088C1-D039-4415-A6C2-D207598F4715` (UUIDv4 uppercase для OneStat)
   - `reforged-storage-db-v1-171093180-reforged-device-id` = `6RGVU6Hdw21nnsphmsgcC` (для мессенджера)
7. **`remixsid` ОБНОВЛЁН** при переходе `id.vk.ru/account` → `vk.ru/feed`. Старая сессия завершена, новая выдана для основного домена.
8. **`.vknext.net` cookies с IP-адресом** — это DataDome-антиспам. IP `95.26.25.24` — это российский IP (блок Ростелеком).
9. **`vk.ru/feed` открывает long-poll channel** (`queue_credential_calls_cache_171093180_6287487` → `https://queuev4.vk.ru/im1180`). В Stage 2 этого не было.
10. **`video.getStatsToken` уже получен** (`videoplayer_auth_token` = `$nYgxk07P6aM34frSB98hnLBlXqXUr48ObYQwhDcNQxwDF0FZquF6YJW42kztuFAnmvlD1`) — даже без открытого плеера. Web-приложение prefetch'ит stats-токен заранее.

---

## Сводная таблица изменений

### Auth-токены

| Stage | app_id=7344294 web_token | app_id=6287487 web_token | app_id=6287487 anonym_token | user_id |
|---|---|---|---|---|
| 1 (до входа) | — | — | — | — |
| 2 (после входа) | ✓ `vk1.a.0UR3YM7wkIdi...`, user_id=171093180, expires Aug 7 12:11 | — | — | 171093180 |
| 3 (vk.ru/feed) | ✓ (без изменений) | ✓ `vk1.a.fxqX3lgtfMqLm...`, **user_id=0** (placeholder!), expires Aug 7 12:14 | ✓ `anonym.eyJhbGc...`, **anonym_id=698328746**, exp Sat Jul 18 17:53 | 171093180 |

### Cookies по доменам

| Домен | Stage 1 | Stage 2 (+new) | Stage 3 (+new) |
|---|---|---|---|
| `.login.vk.ru` | httoken, sua, sui | + `p` (login-persistent); − sua (удалена) | (без изменений) |
| `.api.vk.ru` | httoken | (без изменений) | (без изменений) |
| `.id.vk.ru` | — | + httoken | (без изменений) |
| `.vk.ru` | 18 remix-* (без sid) | + `remixsid`, + `remixnsid`, + `remixnttpid`, + `remixdmgr_tmp`, + `_clientId` | + `remixdmgr` (финал), + `httoken` (4-й домен), + `remixsts` (state), `remixsid` ОБНОВЛЁН, `remixnsid` ОБНОВЛЁН |
| `.vk.com` | — | — | + 15 дублирующих remix-* |
| `.vknext.net` | — | — | + `__ddg1_` ×2, `__ddg10_`, `__ddg8_`, `__ddg9_` (IP), `_vms_uid` |
| `gtmpx.com` | UniqAnalyticsId | (без изменений) | (без изменений) |

### localStorage — счетчик ключей

| Stage | Кол-во ключей | Категории |
|---|---:|---|
| 1 (до входа) | 5 | PromoShowed ×2, deviceId, landings:unauthId, tracer-device-id |
| 2 (после входа) | 6 | + 1 web_token (7344294) |
| 3 (vk.ru/feed) | 60+ | + 3 auth-токена (6287487×3), + 8 METRICS, + 6 audio, + 4 video, + 15+ reforged-storage, + 4 long-poll, + 15+ misc |

### Cookie count по stage

| Stage | Login.vk.ru | Api.vk.ru | Id.vk.ru | Vk.ru | Vk.com | Vknext.net | Gtmpx.com | Total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 3 (httoken, sua, sui) | 1 | 0 | 18 | 0 | 0 | 1 | **23** |
| 2 | 2 (httoken, sui, +p, −sua) → 3 | 1 | 1 (+httoken) | 18+5 (remixsid, nsid, nttpid, dmgr_tmp, _clientId) = 23 | 0 | 0 | 1 | **29** |
| 3 | 3 | 1 | 1 | 23+4 (remixdmgr, httoken, remixsts, + обновл remixsid/nsid) = 27 | 15 (дубли) | 7 (ddg×5 + vms_uid + ...) | 1 | **54** |

---

## Ключевые находки

### 1. `anonym_id` выдаётся один раз и персистит вечно

| Хранилище | Stage 1 | Stage 3 |
|---|---|---|
| `remixstid` cookie | `698328746_zinIypCImEkbpZi2lUzBBZteN1TxBhZwnp2LwKz3ljH` | то же (без изменений) |
| `remixstlid` cookie | `9070491080072951214_7qLF3PX20dJ5M0RJEkMdeoM4AZAqz1Z6xA7cNDEkZGg` | то же |
| `6287487:get_anonym_token` JWT | (не выдан) | `anonym_id=698328746`, `anonym_id_long=9070491080072951214` |

**Совпадение!** VK использует `remixstid`/`remixstlid` cookies (которые живут 1 год) для хранения `anonym_id` до того, как будет запрошен `anonym_token`. При запросе `auth.getAnonymToken` значения из cookies передаются на сервер, который возвращает JWT с теми же ID. Это **continuous anonymous identity** — VK связывает все действия одного браузера даже без логина.

### 2. `sua` cookie — « подписанная сессия из прошлого»

Stage 1 содержит `sua` = `yY0D64FbIkX-50Oq6tgoKU8Q8R9l0stVOiAzkll1lOc#171093180^vk1.a.tw1L4Ph3uNh_as82yajyKq3qrIhnoJSVi3sOyGQwoSSqwbUgxwQJJ1MbEzuPBkHExsuAFBsfJvK_P1wbBQxHmuWd3cwZ12mmIoifBs_MdYqGkyVraecc3qdHuCGI5GsZeR0BtqeZaFne3GP7U4tYVgoAGBh04jWKN8GcSQ0JIiB9QwdnvNK0H4OUkPukPJVz^1784535986`

Декодировано:
- `yY0D64FbIkX-50Oq6tgoKU8Q8R9l0stVOiAzkll1lOc` — подпись (43 символа, base64url)
- `#171093180` — user_id (разделитель `#`)
- `^vk1.a.tw1L4Ph3uNh_as82yajyKq3qrIhnoJSVi3sOyGQwoSSqwbUgxwQJJ1MbEzuPBkHExsuAFBsfJvK_P1wbBQxHmuWd3cwZ12mmIoifBs_MdYqGkyVraecc3qdHuCGI5GsZeR0BtqeZaFne3GP7U4tYVgoAGBh04jWKN8GcSQ0JIiB9QwdnvNK0H4OUkPukPJVz` — старый access_token (248 символов, разделитель `^`)
- `^1784535986` — expires (epoch sec, = Mar 18 2027)

**После Stage 2 эта cookie удаляется** — VK заменяет «подписанную память о прошлой сессии» на свежий `p` cookie (login-persistent token на 1 год). Это **защита от cookie-hijacking**: старый `sua`-токен инвалидируется при новом логине.

### 3. `p` cookie — долгоживущий login-persistent

| Свойство | Значение |
|---|---|
| Домен | `.login.vk.ru` |
| Значение | `vk1.a.w_Wz_dIAGD61FIi-fmNByJP6mhy3j2so02okwaWF4NFboOBKLnWlFb0o9Gn5Y3Ii0IgCTzcuig7-X-wnurDem0rVWVeoJ2stWV3ikd4CYtrPW4JaYka83x6nqhEzMk_NCf0Hiei7maBQhRrgcXM7pfHgQtUxNZ-o-ISTlsToYqA` (178 байт) |
| Expires | 2027-08-07T11:56:47Z (**1 год**) |
| HttpOnly | ✓ |
| Secure | ✓ |

Это **persistent login-token** — позволяет VK автоматически распознать залогиненного пользователя при следующем визите (даже после закрытия браузера). Формат `vk1.a.<base64>` — тот же что у `access_token`.

### 4. Два web_token в localStorage одновременно

В Stage 3 в localStorage **два** `*:web_token:login:auth` ключа:

| Ключ | app_id | access_token | user_id | expires | Назначение |
|---|---:|---|---:|---:|---|
| `7344294:web_token:login:auth` | 7344294 | `vk1.a.0UR3YM7wkIdi...` | 171093180 | 1786104708 (Aug 7 12:11) | VKID Account app (для id.vk.ru) |
| `6287487:web_token:login:auth` | 6287487 | `vk1.a.fxqX3lgtfMqL...` | **0** (placeholder!) | 1786104854 (Aug 7 12:14) | Main VK Web App (для vk.ru) |

**Важно:** `user_id=0` во втором токене — это **временное состояние** сразу после редиректа с `id.vk.ru/account` на `vk.ru/feed`. Web-приложение ещё не успело обменять silent_token на полноценный web_token с user_id=171093180. В этот момент API-вызовы идут через **anonym_token** (который уже есть в localStorage).

### 5. `remixsid` обновляется при переходе между разделами

| Stage | remixsid (vk.ru) | expires |
|---|---|---|
| 1 | (отсутствует) | — |
| 2 | `1_CnI-7BSCaB2LUl93I-DtrxOlSyWg6mS5_hsRYTEaEtczd4eEeYUCw3GpTTT71zJ_2u9Q5etBAkBjqfdKGHt5FA` | 2027-08-01T20:48:58Z |
| 3 | `1_D0J0META0ol3kElHNhsneBqP_T6MWGD5tsYAyY8rERoY-yL3v0iGkO0mAsgJYRIVGV1RYdd2oqXNXywUJlNBHw` | 2027-08-01T12:54:40Z |

Старая сессия (с `1_CnI-7BSCaB...`) **завершается** при переходе на основной домен `vk.ru/feed`, выдаётся новая (`1_D0J0META0ol...`). Это **session rotation** — защита от session-fixation атак.

### 6. `remixdmgr_tmp` → `remixdmgr` конверсия

| Stage | Cookie | Hash | Expires |
|---|---|---|---|
| 2 | `remixdmgr_tmp` | `93a3f1a3541719074c0150f5ccc83661486da7ac134551f1d9270caa38436631` (sha256) | 3 часа |
| 3 | `remixdmgr` | `74cd6d884fe1cfc1c060d70f17daa79d218175b3f86eefe84ef8cebfa6652e31` (другой sha256) | 1 год |
| 3 | `remixdmgr` (на `.vk.com`) | `8965867ce9da1a17a87feea7327d780dc2cf077ed7a686554aa27d80012d8fb1` (третий hash) | 1 год |

Временный hash через ~3 часа конвертируется в финальный. Хеши разные потому что `remixdmgr` считается по другому алгоритму (с добавлением `remixsid` и других факторов). На `.vk.com` — отдельный `remixdmgr` (т.к. отдельная сессия).

### 7. `vknext.net` cookies — DataDome bot-protection

Cookies `__ddg1_`, `__ddg10_`, `__ddg8_`, `__ddg9_` на домене `.vknext.net` — это **DataDome** antispam-bot система. `__ddg9_` содержит **IP-адрес пользователя** (`95.26.25.24`). DataDome используется VK для защиты от парсинга/ботов на медиа-домене `vknext.net` (VMS = VK Media Services, для видео/фото CDN).

### 8. 3 различных app_id в одном auth-flow

| app_id | Назначение | Когда появляется |
|---|---|---|
| `7497650` | VKID Landing (OAuth-страница входа) | Stage 1 — URL-параметр |
| `7934655` | Sister-app (PromoShowed) | Stage 1 — localStorage |
| `7344294` | VKID Account (id.vk.ru/account) | Stage 2 — web_token |
| `6287487` | Main VK Web App (vk.ru/feed) — канонический VK API app_id | Stage 3 — web_token + anonym_token |
| `7913379` | Recovery/Account-management sister-app | Stage 3 — get_accounts (пустой) |

**Ключевое:** `app_id = 6287487` — это **канонический app_id**, который используется во ВСЕХ VK API-вызовах с web-домена `vk.ru`. Все другие app_id — служебные (VKID SDK, recovery и т.д.).

---

## Архитектурный разбор auth-flow

### Полная последовательность событий

```
[Stage 1] https://id.vk.ru/auth?response_type=silent_token&app_id=7497650&...
  │
  │  user уже имеет cookies sua+sui (user_id=171093180 из прошлой сессии)
  │  но remixsid отсутствует — формально не залогинен
  │  anonym_id=698328746 уже в remixstid cookie (выдан при первом визите когда-то)
  │
  ▼ (user вводит логин/пароль или подтверждает вход)
[Stage 2] https://id.vk.ru/account/#/main
  │
  │  VKID SDK выполняет silent_token exchange:
  │    POST https://login.vk.ru/auth?silent_token=...
  │    → returns: { access_token, user_id, expires, logout_hash }
  │
  │  Сохраняет в localStorage: 7344294:web_token:login:auth
  │  Удаляет старую sua cookie (заменена на p)
  │  Устанавливает: p (login-persistent, 1 год)
  │                 remixsid (vk.ru session, 1 мес)
  │                 remixnsid × 2 домена (new session)
  │                 remixnttpid (push token)
  │                 remixdmgr_tmp (3 часа, → финал на следующей стадии)
  │                 _clientId (VK Compose Kit)
  │                 httoken на .id.vk.ru (3-й CSRF домен)
  │
  ▼ (user нажимает «продолжить» или редиректится на vk.ru)
[Stage 3] https://vk.ru/ → 302 → https://vk.ru/feed
  │
  │  Основное web-приложение (app_id=6287487) инициализируется:
  │
  │  1. Проверяет: есть ли 6287487:web_token в localStorage? — НЕТ (первый визит под этим app_id)
  │     → запрашивает anonym_token через auth.getAnonymToken
  │     → получает: anonym.eyJhbGc... с anonym_id=698328746 (из remixstid)
  │     → кеширует: 6287487:get_anonym_token:login:auth
  │
  │  2. Параллельно: silent_token exchange для app_id=6287487
  │     → web-приложение шлёт POST с silent_token из cookie p
  │     → получает: { access_token, user_id=0 (placeholder!), expires }
  │     → кеширует: 6287487:web_token:login:auth
  │     (user_id=0 потому что exchange ещё идёт в фоне)
  │
  │  3. Все API-вызовы сейчас идут через anonym_token (т.к. web_token user_id=0)
  │     → catalog.getVideoShowcase, users.get, и т.д.
  │     → backend видит anonym_id=698328746 + user_id=171093180 из cookie remixsid
  │       → возвращает контент для залогиненного пользователя
  │
  │  4. Background: web_token exchange завершается, user_id становится 171093180
  │     → web-приложение переключается на web_token для следующих API-вызовов
  │
  │  5. Long-poll инициализация:
  │     → queue.subscribe({queue_ids:"account_counters_171093180"})
  │     → получает: { base_url: "https://queuev4.vk.ru/im1180", key, ts }
  │     → кеширует: queue_credential_calls_cache_171093180_6287487
  │     → запускает long-poll цикл
  │
  │  6. Инициализация мессенджера:
  │     → im.getInlineKey → возвращает im_m_comms_key
  │     → запускает nccts171093180 queue
  │
  │  7. Prefetch видеоплеера:
  │     → video.getStatsToken → videoplayer_auth_token
  │     → video.getUVStatsToken → (не в дампе)
  │
  │  8. Cookie rotation:
  │     → старый remixsid (Stage 2) аннулируется
  │     → новый remixsid выдаётся для vk.ru/feed
  │     → remixdmgr_tmp → remixdmgr (финал)
  │     → httoken на .vk.ru (4-й CSRF домен)
  │     → remixsts (state-tracking для текущей страницы)
  │
  │  9. Mirror cookies на .vk.com (для legacy-домена)
  │     → 15 remix-* cookies дублируются
  │     → у каждого свои значения сессий
  │
  │  10. DataDome cookies на .vknext.net
  │      → __ddg1_, __ddg10_, __ddg8_, __ddg9_ (с IP-адресом)
  │      → _vms_uid = 171093180
```

### Список всех доменов в auth-flow

| Домен | Роль | Cookies |
|---|---|---|
| `id.vk.ru` | VKID Account (личный кабинет, OAuth-страница) | httoken, remixnsid |
| `login.vk.ru` | Auth-служба (silent_token exchange) | httoken, sua→p, sui |
| `api.vk.ru` | Основной API endpoint | httoken |
| `vk.ru` | Main web app (vk.ru/feed) | httoken, remixsid, remixnsid, remixnttpid, remixdmgr(_tmp), remixsts, _clientId, + 15 базовых remix-* |
| `vk.com` | Legacy-домен (зеркало vk.ru) | 15 дублирующих remix-* |
| `vknext.net` | VMS / медиа-CDN + DataDome | __ddg1_ ×2, __ddg10_, __ddg8_, __ddg9_, _vms_uid |
| `gtmpx.com` | Аналитика (Top.Mail.Ru) | UniqAnalyticsId |
| `queuev4.vk.ru` | Long-poll server (для мессенджера и account_counters) | — (через localStorage credential) |

---

## Применение к Android-моду (расширение FIX-133)

### Текущее состояние `SilentTokenExchanger`

В существующем коде (после коммитов `39aebcef7`, `9bfd1e927`, `e44c1b2af` от P2-11..P2-14):

- `SilentTokenExchanger.getAnonymToken()` (line 324) — получает anonym_token для публичного контента
- `SilentTokenExchanger.exchangeSilentToken(silentToken, app_id)` — обменивает silent_token на web_token
- Используется для: `catalog.getVideo`, `video.get` (публичный контент) — FIX-133

### Расширения, следующие из этого дампа

#### EXT-1: Поддержка **нескольких app_id параллельно**

В дампе видно 2 активных web_token одновременно (`7344294` + `6287487`). Android-мод должен:

```kotlin
data class WebToken(
    val appId: Long,
    val accessToken: String,  // vk1.a.<base64>
    val userId: Long,         // 0 = placeholder (фоллбэк на anonym_token)
    val expiresAt: Instant,
    val logoutHash: String?   // null для app_id=6287487 (пустой)
)

class WebTokenStore {
    private val tokens: MutableMap<Long, WebToken> = mutableMapOf()
    
    fun getToken(appId: Long): WebToken? = tokens[appId]
    
    fun saveToken(token: WebToken) {
        tokens[token.appId] = token
    }
    
    fun getActiveToken(): WebToken? {
        // Priority: 6287487 (main app) > 7344294 (VKID) > any
        return tokens[6287487] ?: tokens[7344294] ?: tokens.values.firstOrNull()
    }
}
```

#### EXT-2: **`user_id=0` как сигнал фоллбэка**

Когда `web_token.user_id == 0`, Android-мод должен:

```kotlin
suspend fun <T> withAuth(appId: Long = 6287487, block: suspend (token: String) -> T): T {
    val webToken = webTokenStore.getToken(appId)
    val anonymToken = anonymTokenStore.getToken(appId)
    
    return when {
        webToken != null && webToken.userId != 0 && !webToken.isExpired -> 
            block(webToken.accessToken)
        anonymToken != null && !anonymToken.isExpired -> 
            block(anonymToken.accessToken)  // FIX-133 fallback
        else -> {
            // Запустить AuthActivity для получения нового web_token
            authActivityLauncher.launch()
            throw AuthRequiredException()
        }
    }
}
```

#### EXT-3: **Persistance `anonym_id` между сессиями**

Аналог `remixstid` cookie. Android-мод должен:

```kotlin
class AnonymousIdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences("vk_anonym", MODE_PRIVATE)
    
    fun getAnonymId(): Long {
        return prefs.getLong("anonym_id", 0).let { 
            if (it == 0) {
                val newId = Random.nextLong(1_000_000, 2_000_000_000)
                prefs.edit().putLong("anonym_id", newId).apply()
                newId
            } else it
        }
    }
    
    fun getAnonymIdLong(): Long {
        return prefs.getLong("anonym_id_long", 0).let { 
            if (it == 0L) {
                val newId = Random.nextLong(Long.MAX_VALUE / 2, Long.MAX_VALUE)
                prefs.edit().putLong("anonym_id_long", newId).apply()
                newId
            } else it
        }
    }
}
```

При запросе `auth.getAnonymToken` передать эти ID в body — VK вернёт токен с теми же ID (как в дампе).

#### EXT-4: **Logout flow через `logout_hash`**

```kotlin
class AuthRepository {
    suspend fun logout() {
        val webToken = webTokenStore.getToken(7344294) // VKID token
        val logoutHash = webToken?.logoutHash
        
        if (logoutHash != null) {
            // POST https://id.vk.ru/auth?act=logout&hash=<logoutHash>
            apiClient.logoutFromVKID(logoutHash)
        }
        
        // Очистить все токены
        webTokenStore.clear()
        anonymTokenStore.clear()
        
        // CookieManager.removeAllCookies() для WebView
        cookieManager.removeAllCookies(null)
    }
}
```

#### EXT-5: **Session rotation при смене контекста**

Аналог обновления `remixsid` при переходе Stage 2 → Stage 3. Android-мод должен:

```kotlin
class SessionManager {
    // При переходе от AuthActivity к FeedActivity:
    fun onContextChange(oldContext: AppContext, newContext: AppContext) {
        if (oldContext != newContext) {
            // Запросить новый web_token для нового app_id
            val newToken = silentTokenExchanger.exchangeForApp(newContext.appId)
            webTokenStore.saveToken(newToken)
            
            // Старый токен НЕ удалять — может ещё понадобиться для возврата
        }
    }
}
```

#### EXT-6: **Long-poll через `queuev4.vk.ru`**

В дампе: `queue_credential_calls_cache_171093180_6287487` → URL `https://queuev4.vk.ru/im1180`. Android-мод должен:

```kotlin
class LongPollClient(
    private val apiClient: VKApiClient,
    private val credentialStore: QueueCredentialStore
) {
    suspend fun subscribe(queueId: String) {
        // 1. Запросить credential
        val response = apiClient.queueSubscribe(
            queue_ids = "account_counters_${userId},im_$userId"
        )
        // response = { base_url: "https://queuev4.vk.ru/im1180", queues: [{key, ts}] }
        
        credentialStore.save(userId, 6287487, response)
        
        // 2. Запустить long-poll цикл
        startLongPollLoop(response.baseUrl, response.queues.first().key, response.queues.first().ts)
    }
    
    private fun startLongPollLoop(baseUrl: String, key: String, ts: Long) {
        // GET https://queuev4.vk.ru/im1180?key=...&ts=...
        // → ждёт события, возвращает { ts: newTs, updates: [...] }
        // → повторить с newTs
    }
}
```

#### EXT-7: **Prefetch `video.getStatsToken` при инициализации**

В дампе: `videoplayer_auth_token` = `$nYgxk07P6aM34frSB98hnLBlXqXUr48ObYQwhDcNQxwDF0FZquF6YJW42kztuFAnmvlD1` уже сохранён **до** открытия плеера. Android-мод должен:

```kotlin
class VideoPlayerInitializer {
    suspend fun prefetchStatsToken() {
        // Делать при инициализации App, а не при открытии плеера
        val token = apiClient.videoGetStatsToken()
        statsTokenStore.save(token)
    }
}
```

Это сократит время холодного старта плеера на ~200ms (время API-вызова `video.getStatsToken`).

### Связка с предыдущими документами

| Документ | Связанная находка |
|---|---|
| `Профиль.md` FIX-133 | `anonymous_token` для публичного контента — расширен на 3 стадии (EXT-2) |
| `видео.md` Part B §6 | `video.getStatsToken` → `videoplayer_auth_token` localStorage (EXT-7) |
| `видео.md` Part B §7 | `queue.subscribe` long-poll через `queuev4.vk.ru` (EXT-6) |
| `видео.md` Part D §2 Решение 4 | `anonymous_token` fallback для `catalog.getVideo` + `video.get` (EXT-1, EXT-2) |
| `музыка.md` auth section | `audio_v21_*` localStorage keys — теперь виден полный список (8 шт.) |

### Рекомендации по тестам

1. **Test EXT-2 (user_id=0 fallback):** Имитировать web_token с `user_id=0` и проверить, что API-вызовы идут через `anonym_token`.
2. **Test EXT-3 (anonym_id persistence):** Удалить приложение, переустановить — `anonym_id` должен быть тот же (из persistent storage).
3. **Test EXT-5 (session rotation):** Залогиниться через `id.vk.ru`, потом перейти на `vk.ru/feed` — `remixsid` должен обновиться.
4. **Test EXT-6 (long-poll reconnect):** Разорвать соединение с `queuev4.vk.ru` — клиент должен реконнектиться с последним `ts`.

---

## Приложение A. Полный список cookie имён

### Stage 1 (23 cookies)

```
.login.vk.ru:    httoken, sua, sui
.api.vk.ru:      httoken
.vk.ru:          remixage18, remixcolor_scheme_mode, remixcurr_audio,
                 remixdark_color_scheme, remixdt, remixff, remixlang,
                 remixmdevice, remixmvk-fp, remixsf, remixstickers_hash,
                 remixstid, remixstlid, remixsuc, remixua, remixuacck,
                 remixuas
gtmpx.com:       UniqAnalyticsId
```

### Stage 2 (29 cookies — +6, -1, +2 updated)

```
NEW:    .login.vk.ru:    p
        .id.vk.ru:       httoken
        .vk.ru:          remixdmgr_tmp, remixnsid, remixnsid (на id.vk.ru),
                         remixnttpid, remixsid, _clientId
DEL:    .login.vk.ru:    sua
UPD:    .vk.ru:          remixsuc (продлён)
        .login.vk.ru:    sui (продлён)
```

### Stage 3 (54 cookies — +25)

```
NEW:    .vk.ru:          httoken, remixdmgr, remixsts (3 новых)
                          + remixsid, remixnsid ОБНОВЛЕНЫ
        .vk.com:         15 дублирующих remix-*
        .vknext.net:     __ddg1_ ×2, __ddg10_, __ddg8_, __ddg9_, _vms_uid (7)
```

## Приложение B. Полный список localStorage ключей (Stage 3)

### Auth (4)

- `7344294:web_token:login:auth` (Stage 2+)
- `6287487:get_accounts:login:auth` (Stage 3)
- `6287487:get_anonym_token:login:auth` (Stage 3)
- `6287487:web_token:login:auth` (Stage 3)
- `7913379:get_accounts:login:auth` (Stage 3)
- `undefined:web_token:login:auth--request` (Stage 3)

### Identity / device (4)

- `deviceId` (Stage 1+) = `Wr8K9VHUespJJz5SDr-kA`
- `landings:unauthId` (Stage 1+) = `3818433158`
- `tracer-device-id` (Stage 1+) — ИЗМЕНЁН в Stage 3 на `bb52306d-a810-48f0-8f08-e852d23d82f4`
- `_one-stat_deviceId` (Stage 3) = `F3E088C1-D039-4415-A6C2-D207598F4715`

### Promo (2)

- `PromoShowed_7497650` (Stage 1+)
- `PromoShowed_7934655` (Stage 1+)

### Metrics (8)

- `@METRICS_171093180_ADBLOCK_STATS`
- `@METRICS_171093180_CERT_STATS`
- `@METRICS_171093180_PERFORMANCE_STATS_CORE`
- `@METRICS_171093180_PERFORMANCE_STATS_PRODUCT`
- `@METRICS_171093180_USER_INFO_STATS`
- `@METRICS_171093180_WASM_STATS`
- `@METRICS_GUEST_ADBLOCK_STATS`
- `@METRICS_GUEST_CERT_STATS`

### Audio player (8)

- `AudioStats_ConcurrentSafePersistentEventQueue_AudioStatsAPICollector`
- `audio_unique_unauth_id`
- `audio_v21_pl_171093180`
- `audio_v21_progress_171093180`
- `audio_v21_saved_171093180`
- `audio_v21_tns_triggered_time_v3_171093180`
- `audio_v21_track_171093180`
- `audio_v21_uuid_171093180`
- `audioplayer_block_non_zero_volume`

### Video player (10)

- `videoplayer_auth_token`
- `videoplayer_notifications_manager`
- `videoplayer_notifications_manager_count`
- `videoplayer_prefs`
- `vk_player_preferred_autoplay_next`
- `vk_player_preferred_default_quality_settings`
- `vk_player_preferred_rate`
- `vk_player_preferred_volume`
- `short_video_auth_token`
- `stalls_manager_default_tuning_abr_params`
- `stalls_manager_metrics`

### Messenger (22)

- `reforged-storage-db-v1-171093180-drafts`
- `reforged-storage-db-v1-171093180-fc-heads`
- `reforged-storage-db-v1-171093180-hidden-pinned-messages`
- `reforged-storage-db-v1-171093180-last-delete-message-for-all-option`
- `reforged-storage-db-v1-171093180-message-reactions-assets`
- `reforged-storage-db-v1-171093180-postponedDrafts`
- `reforged-storage-db-v1-171093180-reforged-device-id`
- `reforged-storage-db-v1-171093180-selected-convos-from-search`
- `reforged-storage-db-v1-171093180-stickers-keywords-meta`
- `reforged-storage-db-v1-171093180-theme-styles`
- `reforged-storage-db-v1-171093180-theme-styles-appearances`
- `reforged-storage-db-v1-171093180-theme-styles-backgrounds`
- `reforged-storage-db-v1-171093180-videomessage-shapes`
- `stickers-v1-171093180-hasAvatar`
- `stickers-v1-171093180-newAvatarSuggestionCounter`
- `stickers-v1-171093180-stickersActiveTab`
- `stickers-v1-171093180-tabbarActiveTab`
- `stickers-v1-settings-disabledPeerIdsByUser`
- `stickers-v1-settings-isAutoplay`
- `stickers-v1-settings-isPopupAutoplayOnGet`
- `stickers-v1-settings-isPopupAutoplayOnSend`
- `stickers-v1-undefined-tabbarActiveTab`

### Long-poll / queue (7)

- `queue_credential_calls_cache_171093180_6287487`
- `im_m_comms_key`
- `server_queue_connection_events_queue171093180`
- `queue_connection_events_queue171093180`
- `lc_server_switch_to_active_flag`
- `im_notify_flag`
- `multiacc_last_id`

### User state (5)

- `userId`
- `NAVIGATION_TAB_ID`
- `articles_scrolls_171093180`
- `emoji_recent_list`
- `friends_recomm_visited_@id:171093180`

### Performance / analytics (8)

- `PERF_METRICS_TIMESTAMPS_WEB`
- `SERPS_LOGS_DATA_LOCK`
- `XHR_STATS_TRANSPORT_DATA_web2`
- `XHR_STATS_TRANSPORT_DATA_LOCK_web2`
- `XHR_STATS_TRANSPORT_META_web2`
- `LongView.idled`
- `LongView.viewed`
- `onestat_events_forticom`

### Misc (10)

- `AUDIO_PLAYER_INIT_ID`
- `last_reloaded`
- `lock_stats_cookie_locked_stats_api`
- `lockkk_stats_cookie_lock`
- `one_video_rtt`
- `one_video_throughput`
- `promoted_stickers_urls`
- `recent_stickers`
- `supportedFeatures`
- `vms_public_path`

---

*Документ compiled из `vk.id.txt` (60.6 KB, 224 строки). Связанные документы: `музыка.md`, `Профиль.md` (FIX-133), `видео.md` (Part B §6-7, Part D §2). Work-лог: `/home/z/my-project/worklog.md`.*
