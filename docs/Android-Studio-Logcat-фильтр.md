# Android Studio — фильтр Logcat для PinoK

> Logcat → раскрой поле Filter (или значок ►): вставь одну из строк ниже.
> Фильтры — это синтаксис Logcat Android Studio (regex-части поддерживаются).

## 1. Ошибки и предупреждения приложения (базовый)

```
package:mine level:W
```

## 2. Только ошибки (краши, API-ошибки)

```
package:mine level:E
```

## 3. Все сигналы PinoK (логи приложения + ошибки, без системного шума)

```
package:mine -tag:Choreographer -tag:ViewRootImpl -tag:OpenGLRenderer level:V
```

## 4. Логи админ-панели сообщества (по тегам приложения)

```
package:mine (TAG:CommunityAdminBlock | TAG:AdminStatsScreen | TAG:AdminMenuScreen | TAG:AdminStrikesScreen | TAG:AdminCommentsScreen | TAG:VKApiClient | TAG:AdminChats)
```

## 5. API-вызовы админки (по маркеру #ADMIN)

```
package:mine #ADMIN
```

## 6. Только предупреждения (для поиска deprecation/несоответствий)

```
package:mine level:W -level:E
```

## 7. Kotlin-ошибки времени компиляции в Logcat (не отображаются — нужен Build)

Для ошибок компиляции используй не Logcat, а панель **Build** (View → Tool Windows → Build):
`Warning`/`error:` строки в task `:app:compileDebugKotlin`.

---

## Как сохранить фильтр

1. Открой Logcat (View → Tool Windows → Logcat).
2. В поле Filter введи нужную строку выше.
3. Нажми значок **сейфа/диска** (Save As New Logcat Filter) → назови, например `PinoK-errors`.
4. Переключение — из выпадающего списка Filter.

## Полезные сочетания

- Если приложение крашится: `package:mine level:E` + фильтр `AndroidRuntime`.
- Если API-метод возвращает null: ищи `VKApiClient` + `lastApiError` — логи идут в AppLog с категорией.
- Логи AppLog видны с тегом согласно категории; общий префикс — `PinoK` / `Sova` / `VKApiClient`.