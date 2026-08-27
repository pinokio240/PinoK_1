# VKID Android SDK — Анализ авторизации
# Дата: 2026-06-25
# Источник: https://github.com/VKCOM/vkid-android-sdk (ветка master, shallow clone)

## Обзор

VKID SDK — официальный VK SDK для авторизации через VK ID (OAuth 2.1 с PKCE).
Поддерживает: браузерную авторизацию, silent auth через установленное приложение VK,
обновление токенов через refresh_token.

## Архитектура авторизации

### Эндпоинты (id.vk.ru, НЕ id.vk.com!)

| Назначение | Метод | URL |
|---|---|---|
| Авторизация (открытие UI) | GET | `https://id.vk.ru/authorize` |
| Обмен code → token | POST | `https://id.vk.ru/oauth2/auth` |
| Обновление токена | POST | `https://id.vk.ru/oauth2/auth` |
| Получение user_info | POST | `https://id.vk.ru/oauth2/user_info` |
| Логаут | POST | `https://id.vk.ru/oauth2/logout` |
| Silent auth providers | POST | `https://api.vk.ru/method/auth.getSilentAuthProviders` |

### Flow 1: Полная браузерная авторизация (OAuth 2.1 + PKCE)

```
1. Генерация PKCE:
   code_verifier = base64url(random_bytes(128))
   code_challenge = base64url(sha256(code_verifier))
   state = random_alphanumeric(32)

2. Открытие браузера/Chrome Custom Tabs:
   GET https://id.vk.ru/authorize?
     client_id={appId}
     &response_type=code
     &redirect_uri={scheme}://{host}/blank.html
     &code_challenge_method=s256
     &code_challenge={code_challenge}
     &state={state}
     &prompt=login
     &sdk_type=vkid
     &v={sdkVersion}
     &scope={scopes}

3. Пользователь вводит логин/пароль в браузере VK

4. VK редиректит на:
   {scheme}://{host}/blank.html?code={auth_code}&state={state}&device_id={device_id}

5. Приложение получает auth_code из redirect URI

6. Обмен code → access_token + refresh_token:
   POST https://id.vk.ru/oauth2/auth
     grant_type=authorization_code
     &code={auth_code}
     &code_verifier={code_verifier}  // из шага 1
     &client_id={appId}
     &device_id={deviceId}
     &redirect_uri={redirectUri}
     &state={state}

7. Получение user_info:
   POST https://id.vk.ru/oauth2/user_info?client_id={clientId}
     access_token={accessToken}
     &device_id={deviceId}
```

### Flow 2: Silent Auth (через приложение VK)

```
1. Запрос провайдеров:
   POST api.vk.ru/method/auth.getSilentAuthProviders
     v=5.131&client_id={clientId}&client_secret={clientSecret}
   → список {package_name, weight, service_name, cert_fingerprints[]}

2. Проверка установленных приложений (по package_name + SHA сертификата)

3. Запуск intent:
   {package}://vkcexternalauth-codeflow?
     app_id={clientId}
     &response_type=code
     &redirect_uri=...
     &code_challenge=...
     &state=...

4. Приложение VK обрабатывает авторизацию и возвращает code через redirect URI

5. Обмен code → token (как в Flow 1, шаг 6)
```

### Flow 3: Обновление токена

```
POST https://id.vk.ru/oauth2/auth
  grant_type=refresh_token
  &refresh_token={refreshToken}
  &client_id={appId}
  &device_id={deviceId}
  &state={newState}
```

### Flow 4: V1 → V2 обмен токена

```
POST https://id.vk.ru/oauth2/auth
  grant_type=access_token
  &access_token={v1Token}
  &client_id={appId}
  &device_id={deviceId}
  &state={state}
  &code_challenge={codeChallenge}
  &code_challenge_method=s256
  &response_type=code
```

## Ключевые отличия от нашего текущего подхода

| Параметр | Наш (Direct Auth) | VKID SDK (OAuth 2.1) |
|---|---|---|
| Endpoint авторизации | oauth.vk.com/access_token | id.vk.ru/authorize |
| Пароль в приложении | Вводим в своём UI | Вводит в браузере VK |
| PKCE | Нет | Да (S256) |
| grant_type (первый шаг) | password | — (response_type=code) |
| grant_type (обмен) | — | authorization_code |
| client_id | 2274003 (VK Android) | Свой (из кабинета VK ID) |
| client_secret | Да (hHbZxrka2uZ6jB1inYsH) | Да (из кабинета VK ID) |
| refresh_token | Нет (exchange_token) | Да |
| Flood control риск | ВЫСОКИЙ (прямая передача пароля) | НИЗКИЙ (пароль в браузере VK) |
| Требует WebView | Нет | Да (Chrome Custom Tabs) |
| Скоупы | "all" | Конкретные (status, email, etc.) |

## Вывод

Наш текущий подход (Direct Auth через oauth.vk.com/access_token) технически работает,
но вызывает flood control (ошибка 9) потому что VK детектит прямую передачу пароля
из неофициального приложения.

**Правильный подход**: VKID SDK через OAuth 2.1 + PKCE:
1. Получить client_id и client_secret в кабинете VK ID (id.vk.ru/business/go)
2. Использовать PKCE flow: открыть id.vk.ru/authorize в Chrome Custom Tabs
3. Получить auth_code через redirect URI
4. Обменять code на access_token + refresh_token через id.vk.ru/oauth2/auth

Это полностью устраняет проблему flood control, т.к. пароль вводится на серверах VK,
а не передаётся из нашего приложения.

## Важное примечание

VK ID SDK использует домен `id.vk.ru` (НЕ `id.vk.com`!).
Это разные домены: `id.vk.ru` — для OAuth 2.1 VK ID,
`id.vk.com` — для внутреннего exchange_token VK Android клиента.