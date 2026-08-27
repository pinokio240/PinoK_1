# Coding Style — PinoK / VK_X_mod

Краткая шпаргалка по null-safety и связанным идиомам Kotlin в этом проекте.
Цель — единый стиль в новых правках и понятные комментарии, почему `?:`
оставлен там, где он действительно нужен.

---

## `!!` (non-null assertion) — **запрещено в новом коде**

`x!!` бросает `NullPointerException`, если `x == null`. Это всегда либо баг,
либо признак того, что компилятору «не доверяют» там, где он прав.

В проекте `!!` = **0 случаев** (фикс от `029f4e833`). Так и должно остаться.

### Как избежать

**Паттерн 1 — локальный захват var-делегата перед `when`/`if`:**

```kotlin
// ПЛОХО: smart-cast не работает для var-делегата mutableStateOf,
//        отсюда соблазн поставить !!
var error by remember { mutableStateOf<String?>(null) }
when {
    error != null -> Text(error!!, color = ...)  // ❌
}

// ХОРОШО: захват в val — smart-cast срабатывает
val err = error
when {
    err != null -> Text(err, color = ...)        // ✅
}
```

**Паттерн 2 — `requireNotNull` / `checkNotNull` для инвариантов:**

```kotlin
// Если по контракту null невозможен, но тип nullable (legacy API):
val token = requireNotNull(tokenStorage.load()) { "token должен быть сохранён перед вызовом" }
```

Бросает `IllegalArgumentException` с понятным сообщением вместо голого NPE.

**Паттерн 3 — early-return (`return` / `return@launch`):**

```kotlin
val uri = intent.data ?: return
val stream = ctx.contentResolver.openInputStream(uri) ?: return@launch
```

---

## `?:` (elvis) — **использовать, но осознанно**

Elvis правильный инструмент на границах с ненадёжными данными. В проекте
сейчас ~1268 случаев, и большинство из них корректны. Ниже — классификация.

### ✅ Когда `?:` — правильный выбор

**1. Парсинг JSON от VK API** (`api/VKApiClient.kt`, `data/model/Models.kt`,
`realtime/LongPollClient.kt`)

VK постоянно присылает partial-объекты: `first_name` может отсутствовать,
`counters` может быть `null`, `text` может быть `JsonNull`. Здесь `?:` —
часть контракта парсера.

```kotlin
firstName = obj.get("first_name")?.asString ?: "",       // ✅
likes     = post.likes?.count ?: 0,                        // ✅
photo600 ?: photo300 ?: photo200 ?: photo                 // ✅ цепочка best-effort
```

**2. DataStore defaults** (`data/local/SovaPrefs.kt`)

Значение может быть не записано при первом запуске — `?:` задаёт дефолт.

```kotlin
themeAccentIndex = p[Keys.THEME_ACCENT_INDEX] ?: 6,       // ✅
autoCacheAudio   = p[Keys.AUTO_CACHE_AUDIO]   ?: true,    // ✅
```

**3. Early-return / early-continue**

```kotlin
val json = call("users.get", args) ?: return null          // ✅
val uid  = o.get("id")?.asLong ?: continue                 // ✅
```

**4. Optional UI-состояние из кэша/снапшота**

```kotlin
val allPosts = FeedDataHolder.allPosts ?: emptyList()      // ✅
darkTheme    = snap?.themeDark ?: true                     // ✅ (на этапе snap==null рисуется сплэш)
```

**5. Optional Java-API результаты**

```kotlin
val file  = dir.listFiles() ?: emptyArray()               // ✅ File.listFiles() возвращает null по контракту
val host  = java.net.URI(url).host ?: return url           // ✅
```

### ❌ Когда `?:` — code smell (избегать в новом коде)

**1. `?: ""` / `?: 0` «ублажить компилятор», когда null невозможен по логике**

```kotlin
// ПЛОХО: если canSubmit уже проверил валидность — null тут невозможен.
//        Передаём пустую строку, которая потом валится в submitCredentials.
onSubmit(PhoneFormatter.toApiForm(phoneRaw) ?: "", password)   // ❌

// ЛУЧШЕ: smart-cast после явной проверки или requireNotNull.
val phone = PhoneFormatter.toApiForm(phoneRaw)
if (phone != null) {
    onSubmit(phone, password)
}
```

**2. `x ?: x!!` (elvis + assertion) — всегда баг**

Если слева и справа один и тот же `x` — это либо `x ?: x` (= `x`), либо
попытка обмануть компилятор. Встречается редко, но сразу удалять.

**3. Глубокие цепочки `a ?: b ?: c ?: DEFAULT` для бизнес-логики**

Если fallback зависит от условий (а не от приоритета источников) — лучше
явный `when` или sealed-result:

```kotlin
// СПОРНО: 3 уровня fallback скрывают логику
val title = post.title ?: post.copyHistory?.firstOrNull()?.text ?: "Без заголовка"  // ⚠️

// ЯСНЕЕ: когда важна причина
val title = when {
    !post.title.isNullOrBlank() -> post.title
    else -> "Без заголовка"
}
```

(Для best-photo-URL `photo600 ?: photo300 ?: photo200 ?: photo` — это
именно приоритет источников, так что цепочка там уместна.)

**4. `?:` рядом с `let` — часто лишний слой**

```kotlin
// ПЛОХО: лишний let
val name = user?.let { "${it.first} ${it.last}" } ?: "Гость"

// ТО ЖЕ: но проще
val name = if (user != null) "${user.first} ${user.last}" else "Гость"
```

(Для одной строки `let` ок; для сложной логики — `if`/`when` читается лучше.)

---

## Смежные правила

### Smart-cast

Доверяйте компилятору. После `if (x != null)` / `when (x)` ветки — `x`
уже non-null, **если** `x` — локальный `val`. Для `var`-делегатов
(`mutableStateOf`, delegated properties) smart-cast не работает —
захватывайте в локальный `val` перед проверкой.

### `as` casts

Неиспользуемые `as String` / `as Int` — это ClassCastException в рантайме.
Вместо:
- `as? String` (safe cast) + обработка `null`
- `toString()` / `toIntOrNull()` где уместно

В проекте явных `as` кастов почти нет (хорошо).

### `@Suppress("UNUSED...")`

Лучше понять, почему компилятор ругается, чем давить предупреждение.
`@Suppress` — только для случаев, где предупреждение заведомо ложное,
с комментарием почему.

---

## KDoc и вложенные комментарии — **критично**

Kotlin, в отличие от Java/JS, поддерживает **вложенные блочные комментарии**:
`/* … /* … */ … */` — это один комментарий (в Java второй `/*` был бы текстом).
Это значит, что любая пара символов «slash + star» внутри `/** … */` KDoc-блока
**открывает новый уровень вложенности**, а один `*/` в конце закрывает только
внутренний уровень — KDoc при этом НЕ закрывается, и весь последующий код до
следующего `*/` становится комментарием.

### Симптомы (если в KDoc затесался `/*`)

- `Unresolved reference` на все методы ниже KDoc
- `Cannot infer type parameter T`, `Argument type mismatch: Any vs String`
  (каскад — методы вернули error-type)
- `Syntax error: Missing '}'` (object/class не закрыт)
- `Syntax error: Unclosed comment` в конце файла

Один такой `/*` в KDoc даёт **15+ ошибок компиляции** в каскаде.
Реальный случай: `WebTokenAuth.kt` (фикс `854a9a9d8`, 2026-08-05) —
в KDoc метода `waitForSsoReturnRedirect` был glob-шаблон `m.vk.ru/*`.

### Что НЕ писать внутри KDoc

| Плохо | Почему | Хорошо |
|-------|--------|--------|
| `m.vk.ru/*` | `/*` открывает nested comment | `m.vk.ru/…` или `m.vk.ru/<path>` |
| `login.vk.com/*` | то же | `login.vk.com/…` |
| `/* comment */` в примере кода | nested comment | `// comment` (вынести на свою строку) |
| `*/` как текст | преждевременно закрывает KDoc | «star + slash» словами |

### Правило

Внутри `/** … */` KDoc-блока **никогда** не пишите литералы `/*` или `*/`
как текст. Для glob-шаблонов используйте `…` или `/<path>`, для inline-комментариев
в примерах кода — `//` (с переносом `}` на следующую строку если нужно).

Если нужно сослаться на сам синтаксис — описывайте словами: «slash + star»,
«star + slash», либо используйте code-escape с escape-последовательностями.

---

## Чек-лист для ревью нового кода

- [ ] Нет `!!`
- [ ] `?:` стоит только на границах с ненадёжными данными (JSON, DataStore,
      Java API, optional UI state) — не «ублажить компилятор»
- [ ] var-делегаты захватываются в `val` перед `when`/`if` (для smart-cast)
- [ ] Нет `x ?: x` / `x ?: x!!` паттернов
- [ ] Длинные `?:`-цепочки имеют комментарий-обоснование или заменены на `when`
- [ ] Явные `as` касты заменены на `as?` или `*OrNull()` где можно
- [ ] В KDoc нет литералов `/*` или `*/` (nested comments — см. раздел выше)
