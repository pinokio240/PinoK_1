# План применения находок vk.id.md — бесшовная авторизация при смене сети

> **Цель:** обеспечить, чтобы при переключении Wi-Fi ↔ LTE ↔ другая Wi-Fi (или при кратковременной потере сети) пользователя НЕ выкидывало на AuthActivity. Лента, видео, профиль продолжают работать. Только auth-required действия (лайк, комментарий, сообщение) требуют молчаливого re-auth — и тот происходит в фоне, без UI.
> **Источник находок:** `vk.id.md` (3 стадии auth-flow: до входа / после входа / vk.ru/feed).
> **Текущее состояние кода:** `SilentTokenExchanger.kt` (501стр), `ExchangeAuthRepository.kt` (2903стр), `ExchangeTokenStorage.kt` (889стр), `CookieRefreshWorker.kt` (138стр), `NetworkObserver.kt`, `RemixsidCapturer.kt`. Уже есть `silentAuth()`, `silentRefreshViaRemixsid()`, `hasSilentReloginMeans()` — частично закрывают задачу, но есть пробелы.

---

## Содержание

1. [Проблема и текущее состояние](#1-проблема-и-текущее-состояние)
2. [Карта находок vk.id.md → применяемые решения](#2-карта-находок-vkidmd--применяемые-решения)
3. [Архитектура: 4-уровневый fallback chain](#3-архитектура-4-уровневый-fallback-chain)
4. [План внедрения (P0–P3)](#4-план-внедрения-p0p3)
5. [Сценарии: что происходит при смене сети](#5-сценарии-что-происходит-при-смене-сети)
6. [Метрики успеха](#6-метрики-успеха)
7. [Риски и митигация](#7-риски-и-митигация)

---

## 1. Проблема и текущее состояние

### Симптом (по жалобам пользователя)

При смене сети (Wi-Fi → мобильный интернет, переключение между вышками, лифт/метро с потерей сигнала) приложение:
1. Выкидывает на AuthActivity (форма ввода логина/пароля)
2. Теряет доступ к ленте даже в read-only режиме
3. Прерывает текущее воспроизведение видео/аудио
4. Разрывает WebSocket / long-poll соединения без авто-восстановления

### Причина (по анализу vk.id.md)

VK использует несколько привязок сессии:
| Токен | Привязка | Что инвалидируется при смене IP |
|---|---|---|
| `remixsid` (session, `.vk.ru`) | **Привязан к IP** (подозрение) — VK может потребовать re-login при резкой смене гео/IP | ✗ Теряется сессия на vk.ru |
| `remixnsid` (new session) | Тоже может быть IP-bound | ✗ Теряется |
| `httoken` (CSRF, 3 домена) | 1 неделя, не IP-bound | ✓ Сохраняется |
| `p` cookie (login-persistent, `.login.vk.ru`) | **1 ГОД, НЕ привязан к IP** — это persistent-login token | ✓ Сохраняется |
| `sua` cookie (signed user auth, `.login.vk.ru`) | 1 год, содержит подпись прошлой сессии | ✓ Сохраняется |
| `remixstid`/`remixstlid` (anonym_id) | 1 год, НЕ привязан к IP | ✓ Сохраняется |
| `web_token` (app_id=6287487) | 24 часа, не IP-bound, но `user_id=0` placeholder сразу после редиректа | ⚠ Требует re-exchange |
| `anonym_token` (app_id=6287487) | 24 часа, не IP-bound | ✓ Сохраняется |

**Корневая причина выкидывания:** текущий код, видимо, при потере `remixsid` считает сессию недействительной и запускает AuthActivity, **не используя `p` cookie для silent re-auth**.

### Что уже есть в коде

| Компонент | Файл | Что делает | Пробел |
|---|---|---|---|
| `silentAuth()` | `ExchangeAuthRepository.kt:1654` | Молчаливый re-auth через `remixsid` | Не использует `p` cookie — fallback ограничен |
| `silentRefreshViaRemixsid()` | `ExchangeAuthRepository.kt:2312` | Refresh через remixsid cookie | Если remixsid инвалидирован IP-сменой → fallback нет |
| `hasSilentReloginMeans()` | `ExchangeAuthRepository.kt:1915` | Проверяет, есть ли чем сделать silent re-login | Возможно, не учитывает `p` cookie как валидный source |
| `NetworkObserver.kt` | `util/` | Слушает `ConnectivityManager.NetworkCallback` | Не вызывает silent refresh при reconnect |
| `CookieRefreshWorker.kt` | `exchange/` (138стр) | WorkManager periodic worker | Запускается по расписанию, НЕ на событие сети |
| `p` cookie handling | `RemixsidCapturer.kt:335`, `VkAuthWebViewScreenV2.kt:91` | Сохраняет `p` при auth-flow | **Не использует `p` для silent re-auth** при истечении `web_token` |
| `ExchangeTokenStorage` | 889 строк | Хранит tokens в SharedPreferences | Не хранит `p` cookie между запусками в encrypted storage |

---

## 2. Карта находок vk.id.md → применяемые решения

| # | Находка в vk.id.md | Применение в Android-моде | Приоритет |
|---|---|---|---|
| **F-1** | `p` cookie живёт 1 год на `.login.vk.ru`, НЕ привязан к IP, формат `vk1.a.<base64>` | Сохранять `p` в EncryptedSharedPreferences → использовать для silent re-exchange `silent_token → web_token` без UI | **P0** |
| **F-2** | `sua` cookie содержит `signature#user_id^access_token^expires` — подписанную сессию из прошлого | Сохранять `sua` как backup identity; если `p` истёк, попробовать silent auth через `sua` | **P1** |
| **F-3** | `anonym_id=698328746` совпадает в `remixstid` cookie (Stage 1) и в JWT `anonym_token` (Stage 3) — персистит вечно | Гарантировать, что `anonym_id` не пересоздаётся при каждом запуске; сохранять в persistent storage | **P0** |
| **F-4** | `web_token` для app_id=6287487 имеет `user_id=0` (placeholder) сразу после редиректа — VK сам использует `anonym_token` как fallback | Реализовать `AuthGate`: API-вызовы для публичного контента (`catalog.getVideo`, `video.get`, `users.get`) идут через `anonym_token` когда `web_token.user_id==0` или истёк | **P0** |
| **F-5** | `remixsid` ротируется при переходе Stage 2→3 (session rotation) — защита от session-fixation | При silent re-auth НЕ пытаться переиспользовать старый `remixsid` — запрашивать новый через `p` cookie | **P1** |
| **F-6** | Long-poll через `queuev4.vk.ru/im1180` с credential в localStorage (`queue_credential_calls_cache_171093180_6287487`) | При смене сети long-poll клиент должен реконнектиться с тем же `key`+`ts`, не запрашивать новый credential | **P2** |
| **F-7** | `videoplayer_auth_token` prefetch'ится при инициализации, ДО открытия плеера | Prefetch `video.getStatsToken` в `Application.onCreate()` — экономия ~200ms на cold start плеера | **P3** |
| **F-8** | 3 различных app_id параллельно (7497650 landing, 7344294 VKID account, 6287487 main, 7913379 recovery) | `WebTokenStore` должен хранить токены для всех 4 app_id, не только 6287487 | **P1** |
| **F-9** | `remixdmgr_tmp` (3 часа) → `remixdmgr` (1 год) конверсия — device-manager hash | Сохранять `remixdmgr` между запусками; при потере — VK выдаст новый через 3 часа | **P2** |
| **F-10** | `logout_hash` в `7344294:web_token:login:auth` (18 hex) — для logout endpoint | Использовать для корректного logout без показа UI (если пользователь явно вышел в web-версии) | **P3** |
| **F-11** | `remixsts` cookie — state-tracking текущей страницы (`{"data":[[ts,"web_vk_env_desync",1,null,"production","feed",...]]}`) | Не нужен для auth, но полезен для отладки env_desync ошибок | **P3** |
| **F-12** | DataDome cookies на `.vknext.net` (`__ddg9_` = IP пользователя) — antispam для медиа-CDN | При смене сети DataDome может потребовать пере-check. Реализовать retry с backoff для медиа-запросов | **P2** |

---

## 3. Архитектура: 4-уровневый fallback chain

```
API-вызов (например, catalog.getVideo)
   │
   ▼
┌─────────────────────────────────────────────────────────────────┐
│ TokenManager.getTokenFor(api)                                   │
│                                                                 │
│  Уровень 1: web_token (app_id=6287487)                          │
│  ─────────────────────────────────────────────────              │
│  • Проверить expiresAt > now + 5min                             │
│  • Проверить user_id != 0 (не placeholder)                      │
│  • Если валиден → вернуть web_token                             │
│                                                                 │
│  Уровень 2: silent re-auth через p cookie                       │
│  ─────────────────────────────────────────────────              │
│  • Если web_token истёк/инвалидирован                           │
│  • Загрузить p cookie из EncryptedSharedPreferences             │
│  • Если p есть и не истёк (1 год) →                             │
│    silentExchange(silent_token_from_p) → новый web_token        │
│  • Сохранить новый web_token в storage                          │
│  • Вернуть новый web_token                                      │
│                                                                 │
│  Уровень 3: anonym_token (фоллбэк для публичного контента)      │
│  ─────────────────────────────────────────────────              │
│  • Если p истёк или silent exchange упал                        │
│  • Загрузить anonym_token из storage                            │
│  • Если валиден (24ч) и API = публичный → вернуть anonym_token  │
│  • Если API = auth-required (likes/comments) → бросить          │
│    AuthRequiredException (показать AuthActivity только тут)     │
│                                                                 │
│  Уровень 4: AuthActivity (последний рубеж)                      │
│  ─────────────────────────────────────────────────              │
│  • Только если API = auth-required И нет валидного web_token    │
│  • AuthGate перехватывает действие, показывает AuthActivity     │
│  • После успешного auth → повторить исходное действие           │
└─────────────────────────────────────────────────────────────────┘
```

### Классификация API-вызовов

| Категория | Примеры | Минимальный уровень |
|---|---|---|
| **Public read** | `catalog.getVideo`, `catalog.getBlockItems`, `video.get` (публичные), `users.get` (публичные), `wall.get` (публичные), `photos.get` (публичные) | Уровень 3 (`anonym_token`) |
| **Auth read** | `video.get` (свои приватные), `messages.getDialogs`, `friends.get` (свои) | Уровень 1-2 (`web_token`) |
| **Auth write** | `likes.add`, `wall.addComment`, `messages.send`, `video.add` (watch later) | Уровень 1-2 (`web_token`) |
| **Account management** | `account.setInfo`, `notifications.saveSettings` | Уровень 1-2 (`web_token`) |

---

## 4. План внедрения (P0–P3)

### P0 — Critical (~30 ч, 5 задач)

#### P0-1: `PersistentLoginStore` — сохранение `p` cookie в encrypted storage

**Файл:** `app/src/main/java/re/pinok/auth/exchange/PersistentLoginStore.kt` (новый)

```kotlin
class PersistentLoginStore(context: Context) {
    // EncryptedSharedPreferences через Jetpack Security
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        context, "vk_persistent_login", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun savePCookie(p: String) {
        // p = "vk1.a.<base64>", 178 байт
        prefs.edit().putString(KEY_P_COOKIE, p).apply()
    }
    
    fun getPCookie(): String? = prefs.getString(KEY_P_COOKIE, null)
    
    fun saveSuaCookie(sua: String) {
        // sua = "signature#user_id^token^expires"
        prefs.edit().putString(KEY_SUA_COOKIE, sua).apply()
    }
    
    fun getSuaCookie(): String? = prefs.getString(KEY_SUA_COOKIE, null)
    
    fun clear() {
        prefs.edit().clear().apply()
    }
    
    companion object {
        private const val KEY_P_COOKIE = "p_cookie"
        private const val KEY_SUA_COOKIE = "sua_cookie"
    }
}
```

**Интеграция:** в `RemixsidCapturer.kt:335` (где уже обрабатывается `p` cookie) — при поимке `p` дополнительно сохранить в `PersistentLoginStore`.

#### P0-2: `SilentReauthViaPersistentLogin` — обмен `p` → новый `web_token`

**Файл:** `app/src/main/java/re/pinok/auth/exchange/SilentReauthViaPersistentLogin.kt` (новый)

```kotlin
class SilentReauthViaPersistentLogin(
    private val persistentLoginStore: PersistentLoginStore,
    private val exchangeAuthApi: ExchangeAuthApi,
    private val tokenStorage: ExchangeTokenStorage
) {
    /**
     * Пытается молча получить новый web_token через p cookie.
     * Возвращает web_token или null если p отсутствует/истёк.
     * НЕ показывает UI.
     */
    suspend fun tryReauth(): String? = withContext(Dispatchers.IO) {
        val pCookie = persistentLoginStore.getPCookie() ?: return@withContext null
        
        try {
            // Используем p cookie для silent re-exchange
            // POST https://login.vk.ru/auth?silent_token=<derived_from_p>
            // Cookie: p=<pCookie>
            val response = exchangeAuthApi.silentRefreshWithPersistentCookie(pCookie)
            
            if (response.isSuccessful) {
                val newToken = parseWebToken(response.body())
                tokenStorage.saveAuthResult(newToken, tokenStorage.deviceId())
                AppLog.i(TAG, "Silent re-auth via p cookie: SUCCESS, user_id=${newToken.userId}")
                newToken.accessToken
            } else {
                AppLog.w(TAG, "Silent re-auth via p cookie: HTTP ${response.code()}")
                null
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Silent re-auth via p cookie failed", e)
            null
        }
    }
    
    companion object { private const val TAG = "SilentReauthP" }
}
```

**Интеграция в `ExchangeAuthRepository.ensureFreshToken()`:** после неудачного `silentRefreshViaRemixsid()` → вызвать `SilentReauthViaPersistentLogin.tryReauth()` перед запуском AuthActivity.

#### P0-3: `AuthGate` — классификация API и перехват auth-required действий

**Файл:** `app/src/main/java/re/pinok/auth/AuthGate.kt` (новый)

```kotlin
enum class ApiAuthLevel {
    PUBLIC_READ,    // catalog.getVideo, users.get, wall.get, photos.get
    AUTH_READ,      // video.get (private), messages.getDialogs
    AUTH_WRITE,     // likes.add, wall.addComment, messages.send, video.add
    ACCOUNT_MGMT    // account.setInfo, notifications.saveSettings
}

object ApiAuthRegistry {
    private val publicRead = setOf(
        "catalog.getVideo", "catalog.getVideoShowcase", "catalog.getBlockItems",
        "catalog.getSection", "catalog.getAlbumById",
        "video.get",  // публичные видео
        "users.get", "users.getSubscriptions", "users.getFollowers",
        "wall.get", "wall.getById",  // публичные посты
        "photos.get", "photos.getAll",  // публичные фото
        "groups.get", "groups.getById",
        "video.getCategories", "video.search"
    )
    
    private val authWrite = setOf(
        "likes.add", "likes.delete", "likes.isLiked",
        "wall.addComment", "wall.post", "wall.edit",
        "messages.send", "messages.editChat",
        "video.add", "video.delete", "video.edit",
        "friends.add", "friends.delete",
        "groups.join", "groups.leave",
        "account.setInfo"
    )
    
    fun levelFor(apiMethod: String): ApiAuthLevel = when {
        apiMethod in publicRead -> ApiAuthLevel.PUBLIC_READ
        apiMethod in authWrite -> ApiAuthLevel.AUTH_WRITE
        else -> ApiAuthLevel.AUTH_READ  // safe default
    }
}

class AuthGate(
    private val tokenManager: TokenManager,
    private val authActivityLauncher: AuthActivityLauncher
) {
    /**
     * Возвращает suspend лямбду, которая делает API-вызов с правильным токеном.
     * Если требуется auth и нет валидного токена — бросает AuthRequiredException.
     */
    suspend fun <T> withAuth(apiMethod: String, block: suspend (token: String) -> T): T {
        val level = ApiAuthRegistry.levelFor(apiMethod)
        
        return when (level) {
            ApiAuthLevel.PUBLIC_READ -> {
                // Допускается anonym_token
                val token = tokenManager.getBestToken()  // web_token OR anonym_token
                block(token)
            }
            ApiAuthLevel.AUTH_READ, ApiAuthLevel.AUTH_WRITE, ApiAuthLevel.ACCOUNT_MGMT -> {
                val webToken = tokenManager.getValidWebToken()
                    ?: throw AuthRequiredException(apiMethod)
                block(webToken)
            }
        }
    }
}

class AuthRequiredException(val apiMethod: String) : Exception("Auth required for $apiMethod")
```

**Интеграция:** в UI-компонентах, которые делают auth-write действия, обернуть в `try { authGate.withAuth("likes.add") { ... } } catch (e: AuthRequiredException) { showAuthActivity() }`.

#### P0-4: `TokenManager` — централизованное управление 4 уровнями

**Файл:** `app/src/main/java/re/pinok/auth/TokenManager.kt` (новый, частично использует существующий `ExchangeTokenStorage`)

```kotlin
class TokenManager(
    private val storage: ExchangeTokenStorage,
    private val silentReauth: SilentReauthViaPersistentLogin,
    private val anonymTokenStore: AnonymTokenStore
) {
    private val refreshMutex = Mutex()
    
    /**
     * Лучший доступный токен для публичного API.
     * Priority: web_token (if valid) > anonym_token > fresh web_token via silent re-auth.
     */
    suspend fun getBestToken(): String {
        // 1. Пробуем web_token
        storage.accessToken()?.let { token ->
            if (isWebTokenValid(token)) return token
        }
        
        // 2. Пробуем anonym_token
        anonymTokenStore.get()?.let { anonym ->
            if (!anonym.isExpired) return anonym.accessToken
        }
        
        // 3. Пробуем silent re-auth через p cookie (в фоне, без UI)
        silentReauth.tryReauth()?.let { return it }
        
        // 4. Если ничего нет — возвращаем anonym (даже если истёк, VK может простить)
        return anonymTokenStore.get()?.accessToken ?: throw NoTokenAvailableException()
    }
    
    /**
     * Гарантированно валидный web_token для auth-required API.
     * Если не получается получить — возвращает null (тогда caller показывает AuthActivity).
     */
    suspend fun getValidWebToken(): String? {
        storage.accessToken()?.let { token ->
            if (isWebTokenValid(token)) return token
        }
        
        // Пробуем silent re-auth через p cookie
        return silentReauth.tryReauth()
    }
    
    private fun isWebTokenValid(token: String): Boolean {
        val expiresAt = storage.expiresAt()
        val userId = storage.userId()
        val now = System.currentTimeMillis()
        // +5 минут предиктивно
        return expiresAt > now + 300_000 && userId != 0L && token.isNotBlank()
    }
}
```

#### P0-5: Интеграция `NetworkObserver` → silent refresh на reconnect

**Файл:** `app/src/main/java/re/pinok/util/NetworkObserver.kt` (модификация существующего)

```kotlin
// В NetworkObserver добавить:
class NetworkObserver(
    private val context: Context,
    private val tokenManager: TokenManager  // NEW
) {
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            AppLog.i(TAG, "Network available — triggering silent refresh")
            // Запускаем silent refresh в фоне, НЕ блокируя UI
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    tokenManager.getValidWebToken()  // попытка silent re-auth
                    AppLog.i(TAG, "Silent refresh after network change: OK")
                } catch (e: Exception) {
                    AppLog.w(TAG, "Silent refresh after network change failed: ${e.message}")
                    // НЕ показываем AuthActivity — пользователь ещё ничего не делал
                }
            }
        }
        
        override fun onLost(network: Network) {
            AppLog.i(TAG, "Network lost — will refresh on reconnect")
        }
    }
    
    fun register() {
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().build(),
            networkCallback
        )
    }
    
    companion object { private const val TAG = "NetworkObserver" }
}
```

**Важно:** `onAvailable` вызывается при КАЖДОМ подключении (включая переключение WiFi→LTE). Silent refresh запускается в фоне, UI не блокируется. Если refresh упал — ничего не показываем, пользователь ещё не делал auth-required действие.

---

### P1 — High (~35 ч, 5 задач)

#### P1-1: `SuaBackupAuth` — fallback на `sua` cookie если `p` истёк

Если `p` cookie истёк (через 1 год) или VK его инвалидировал — попробовать silent auth через `sua` cookie. `sua` содержит `signature#user_id^access_token^expires` — это «подписанная память о прошлой сессии».

```kotlin
class SuaBackupAuth(
    private val persistentLoginStore: PersistentLoginStore,
    private val exchangeAuthApi: ExchangeAuthApi
) {
    suspend fun trySuaAuth(): String? {
        val sua = persistentLoginStore.getSuaCookie() ?: return null
        val parts = parseSua(sua) ?: return null
        // parts = (signature, userId, accessToken, expires)
        
        if (parts.expires < System.currentTimeMillis()) {
            AppLog.w(TAG, "sua expired")
            return null
        }
        
        return try {
            val response = exchangeAuthApi.silentRefreshWithSua(sua)
            if (response.isSuccessful) {
                AppLog.i(TAG, "sua backup auth: SUCCESS, user_id=${parts.userId}")
                parseWebToken(response.body()).accessToken
            } else null
        } catch (e: Exception) { null }
    }
}
```

**Интеграция:** в `TokenManager.getValidWebToken()` — после неудачного `silentReauth.tryReauth()` вызвать `suaBackupAuth.trySuaAuth()`.

#### P1-2: `MultiAppIdWebTokenStore` — поддержка 4 app_id параллельно

Из vk.id.md: одновременно 2 web_token (app_id=7344294 для VKID account, 6287487 для main VK). Реализовать хранение для всех 4 app_id:

```kotlin
data class WebToken(
    val appId: Long,
    val accessToken: String,
    val userId: Long,
    val expiresAt: Long,
    val logoutHash: String?
)

class MultiAppIdWebTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("vk_web_tokens", MODE_PRIVATE)
    
    fun save(token: WebToken) {
        prefs.edit()
            .putString("web_token_${token.appId}", token.accessToken)
            .putLong("user_id_${token.appId}", token.userId)
            .putLong("expires_at_${token.appId}", token.expiresAt)
            .putString("logout_hash_${token.appId}", token.logoutHash ?: "")
            .apply()
    }
    
    fun get(appId: Long): WebToken? {
        val token = prefs.getString("web_token_$appId", null) ?: return null
        return WebToken(
            appId = appId,
            accessToken = token,
            userId = prefs.getLong("user_id_$appId", 0),
            expiresAt = prefs.getLong("expires_at_$appId", 0),
            logoutHash = prefs.getString("logout_hash_$appId", null)?.takeIf { it.isNotEmpty() }
        )
    }
    
    fun getActiveToken(): WebToken? {
        // Priority: 6287487 (main) > 7344294 (VKID) > any
        return get(6287487) ?: get(7344294) ?: allAppIds().mapNotNull { get(it) }.firstOrNull()
    }
    
    private fun allAppIds() = listOf(6287487L, 7344294L, 7913379L, 7497650L)
}
```

#### P1-3: `AnonymTokenStore` — персистентное хранение `anonym_id`

Из vk.id.md F-3: `anonym_id` должен персистить между запусками, не пересоздаваться.

```kotlin
class AnonymTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("vk_anonym", MODE_PRIVATE)
    
    fun getAnonymId(): Long {
        var id = prefs.getLong("anonym_id", 0)
        if (id == 0L) {
            id = Random.nextLong(1_000_000, 2_000_000_000)
            prefs.edit().putLong("anonym_id", id).apply()
        }
        return id
    }
    
    fun getAnonymIdLong(): Long {
        var id = prefs.getLong("anonym_id_long", 0)
        if (id == 0L) {
            id = Random.nextLong(Long.MAX_VALUE / 2, Long.MAX_VALUE)
            prefs.edit().putLong("anonym_id_long", id).apply()
        }
        return id
    }
    
    fun saveAnonymToken(token: String, expiresAt: Long) {
        prefs.edit()
            .putString("anonym_token", token)
            .putLong("anonym_expires", expiresAt)
            .apply()
    }
    
    fun get(): AnonymToken? {
        val token = prefs.getString("anonym_token", null) ?: return null
        val expires = prefs.getLong("anonym_expires", 0)
        return AnonymToken(token, expires)
    }
}
```

#### P1-4: `CookiePersistenceStore` — сохранение всех `remix*` cookies между запусками

Из vk.id.md F-9: `remixdmgr`, `remixstid`, `remixstlid`, `remixsid`, `remixnsid`, `remixnttpid` — всё нужно сохранять.

```kotlin
class CookiePersistenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("vk_cookies", MODE_PRIVATE)
    
    fun saveCookies(url: String, cookies: Map<String, String>) {
        val json = JSONObject(cookies).toString()
        prefs.edit().putString("cookies_${url.hashCode()}", json).apply()
    }
    
    fun loadCookies(url: String): Map<String, String> {
        val json = prefs.getString("cookies_${url.hashCode()}", null) ?: return emptyMap()
        return JSONObject(json).toMap().mapValues { it.value.toString() }
    }
    
    fun saveRemixstid(stid: String) = prefs.edit().putString("remixstid", stid).apply()
    fun getRemixstid(): String? = prefs.getString("remixstid", null)
    
    // Аналогично для remixstlid, remixsid, remixdmgr, и т.д.
}
```

**Интеграция:** при каждом успешном auth-flow сохранять ВСЕ cookies в `CookiePersistenceStore`. При запуске — восстанавливать в `CookieManager`.

#### P1-5: Предиктивный refresh — за 5 минут до истечения `web_token`

В `Application.onCreate()` запустить periodic worker:

```kotlin
class PredictiveTokenRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val storage = ExchangeTokenStorage(applicationContext)
        val expiresAt = storage.expiresAt()
        val now = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000L
        
        if (expiresAt - now < fiveMinutes) {
            // Истекает в течение 5 минут — refresh сейчас
            val tokenManager = (applicationContext as App).tokenManager
            tokenManager.getValidWebToken()
        }
        
        return Result.success()
    }
    
    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PredictiveTokenRefreshWorker>(
                15, TimeUnit.MINUTES  // каждые 15 минут проверка
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "predictive_token_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
```

---

### P2 — Medium (~25 ч, 4 задачи)

#### P2-1: `QueueReconnectHandler` — автоматический реконнект long-poll

Из vk.id.md F-6: при смене сети long-poll должен реконнектиться с тем же `key`+`ts`.

```kotlin
class QueueReconnectHandler(
    private val credentialStore: QueueCredentialStore,
    private val networkObserver: NetworkObserver
) {
    private var currentConnection: LongPollConnection? = null
    
    init {
        networkObserver.registerListener { online ->
            if (online) {
                CoroutineScope(Dispatchers.IO).launch {
                    reconnectWithExistingCredential()
                }
            }
        }
    }
    
    private suspend fun reconnectWithExistingCredential() {
        val credential = credentialStore.getCurrent() ?: return
        // НЕ запрашивать новый credential — использовать существующий key+ts
        currentConnection?.close()
        currentConnection = LongPollConnection(credential).also { it.connect() }
        AppLog.i(TAG, "Long-poll reconnected with existing credential")
    }
}
```

#### P2-2: `DataDomeRetryInterceptor` — retry для медиа-запросов

Из vk.id.md F-12: при смене сети DataDome на `.vknext.net` может потребовать re-check.

```kotlin
class DataDomeRetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        
        // DataDome возвращает 403 с captcha или redirect
        if (response.code == 403 && request.url.host.contains("vknext.net")) {
            Thread.sleep(2000)  // backoff
            return chain.proceed(request)  // retry
        }
        
        return response
    }
}
```

#### P2-3: `CookieRefreshWorker` — расширить для `p` cookie

В существующем `CookieRefreshWorker.kt` (138стр) — добавить проверку `p` cookie и silent refresh.

#### P2-4: Логирование и метрики auth-событий

В каждый уровень fallback добавить логирование для диагностики:
- `silent_reauth_via_p_success` / `silent_reauth_via_p_failed`
- `sua_backup_auth_success` / `sua_backup_auth_failed`
- `anonym_token_fallback_used` (для каких API)
- `auth_activity_shown` (с причиной — какой API выбросил AuthRequiredException)

---

### P3 — Polish (~15 ч, 3 задачи)

#### P3-1: Prefetch `video.getStatsToken` при инициализации

Из vk.id.md F-7: `videoplayer_auth_token` prefetch'ится при инициализации, до открытия плеера. В `Application.onCreate()`:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Prefetch stats token в фоне
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = apiClient.videoGetStatsToken()
                statsTokenStore.save(token)
                AppLog.i(TAG, "video.getStatsToken prefetched")
            } catch (e: Exception) {
                AppLog.w(TAG, "video.getStatsToken prefetch failed: ${e.message}")
            }
        }
    }
}
```

#### P3-2: `LogoutHandler` через `logout_hash`

Из vk.id.md F-10: использовать `logout_hash` из `7344294:web_token` для корректного logout без UI.

#### P3-3: `MultiAccount` — переключение между аккаунтами

Сохранять `p` cookie для каждого аккаунта, переключаться без re-login.

---

## 5. Сценарии: что происходит при смене сети

### Сценарий A: Wi-Fi → LTE (типичный)

```
T=0    Пользователь смотрит видео по Wi-Fi
       web_token=valid (user_id=171093180, expires через 20 часов)
       
T=1s   Wi-Fi отключается, LTE подключается
       NetworkObserver.onAvailable() срабатывает
       → запускает tokenManager.getValidWebToken() в фоне
       → web_token ещё валиден → ничего не делает
       → UI НЕ блокируется, видео продолжает играть
       
T=2s   Long-poll reconnect с тем же key+ts
       → новые события приходят, мессенджер работает
       
Результат: пользователь НИЧЕГО не замечает
```

### Сценарий B: Wi-Fi → LTE + VK инвалидировал `remixsid`

```
T=0    Пользователь смотрит видео
T=1s   Смена сети, VK на сервере видит смену IP
T=2s   Следующий API-запрос (catalog.getVideo) → 401 Unauthorized
       → TokenManager.getBestToken() вызывается
       → web_token в storage помечается как инвалидный
       → silentReauth.tryReauth() через p cookie
       → SUCCESS: новый web_token получен
       → API-запрос повторяется с новым токеном
       → UI НЕ блокируется
       
Результат: пользователь видит 1-секундный спиннер, видео продолжает играть
```

### Сценарий C: `p` cookie тоже истёк (через 1 год)

```
T=0    Пользователь открывает приложение через год
T=1s   TokenManager.getBestToken() для catalog.getVideo (public read)
       → web_token истёк
       → silentReauth.tryReauth() через p → p истёк
       → suaBackupAuth.trySuaAuth() → sua истёк
       → Возвращаем anonym_token (24 часа, может ещё валиден)
       → catalog.getVideo работает (public content)
       → UI: лента показывает, видео играет
       
T=10s  Пользователь нажимает «Лайк»
       → authGate.withAuth("likes.add") 
       → AUTH_WRITE level
       → tokenManager.getValidWebToken() → null (всё истекло)
       → AuthRequiredException
       → UI показывает AuthActivity ТОЛЬКО ТУТ
       
Результат: пользователь весь день читает ленту без логина.
           Только когда хочет лайкнуть — приложение просит войти.
```

### Сценарий D: Кратковременная потеря сети (лифт/метро)

```
T=0    Пользователь в лифте, сеть пропадает
T=1s   NetworkObserver.onLost() — никаких действий
T=10s  Сеть возвращается
       NetworkObserver.onAvailable()
       → silent refresh в фоне
       → long-poll реконнектится
       → WebView cookie синхронизируется
       
Результат: пользователь НИЧЕГО не замечает
```

### Сценарий E: Текущее поведение (ДО внедрения плана)

```
T=0    Пользователь смотрит видео
T=1s   Смена сети
T=2s   API-запрос → 401 (remixsid инвалидирован)
       → текущий код: ensureFreshToken() → silentRefreshViaRemixsid() fails
       → нет fallback на p cookie
       → AuthActivity запускается
       → пользователь видит форму ввода логина/пароля
       
Результат: пользователь раздражён, нужно заново логиниться
```

---

## 6. Метрики успеха

| Метрика | До | После P0 | После P0+P1 | Цель |
|---|---|---|---|---|
| % случаев, когда смена сети → AuthActivity | ~80% | <30% | <10% | <5% |
| Среднее время от смены сети до следующего успешного API-запроса | N/A (interrupt) | 1-2 сек | 1-2 сек | <2 сек |
| % пользователей, у которых `p` cookie позволяет silent re-auth | 0% | >90% | >95% | >95% |
| Количество показов AuthActivity в день на активного пользователя | ~3-5 | <1 | <0.3 | <0.2 |
| Crash-рейт при смене сети (если есть) | baseline | -50% | -80% | -95% |

### Метрики для логирования

```kotlin
// В каждый уровень fallback:
Analytics.log("auth_fallback_used", mapOf(
    "level" to "web_token",  // or "silent_reauth_p", "sua_backup", "anonym_fallback", "auth_activity"
    "api_method" to apiMethod,
    "reason" to "expired",  // or "invalidated_by_ip", "user_action", "manual_logout"
    "success" to true,
    "duration_ms" to duration
))
```

---

## 7. Риски и митигация

| Риск | Severity | Вероятность | Митигация |
|---|---|---|---|
| VK изменит endpoint для silent re-auth через `p` cookie | High | Low | Иметь fallback на `sua` cookie, потом на `anonym_token`. Мониторить изменения VK API |
| `p` cookie может быть привязан к device fingerprint | Medium | Medium | Сохранять `deviceId` (21 символ) между запусками, не пересоздавать |
| EncryptedSharedPreferences падает на некоторых устройствах | Medium | Low | Fallback на обычный SharedPreferences с обфускацией |
| `NetworkObserver.onAvailable` срабатывает слишком часто (flapping) | Medium | High | Debounce 3 секунды — если за 3 сек было >1 события, выполнять только последний |
| Silent re-auth в фоне может пересекаться с user-initiated auth | Low | Medium | `refreshMutex` в `TokenManager` гарантирует, что только один refresh за раз |
| DataDome может заблокировать приложение при слишком частых re-auth | Medium | Medium | Rate limit: не чаще 1 silent re-auth в 30 секунд |
| `anonym_token` fallback может показать пользователю чужой контент (если anonym_id связан с другим юзером) | Low | Very Low | `anonym_id` генерируется локально и хранится вечно — конфликт маловероятен |
| Пользователь явно вышел в web-версии — `p` cookie инвалидируется | Medium | Medium | При неудачном silent re-auth через `p` → попробовать `sua` → если тоже неудачно → показать AuthActivity |
| Long-poll credential может быть одноразовым | Low | Low | Запросить новый credential через `queue.subscribe` если реконнект с старым упал |
| Multi-account: разные `p` cookies для разных юзеров | Medium | Medium | Хранить `p` cookie per-userId, переключаться при multi-account switch |

---

## 8. Итоговая сводка плана

| Приоритет | Задач | Часов | Результат |
|---|---:|---:|---|
| **P0 Critical** | 5 | 30 | Бесшовная авторизация при типичной смене сети (Wi-Fi↔LTE). Пользователь не видит AuthActivity при read-операциях |
| **P1 High** | 5 | 35 | Fallback на `sua` + multi-app_id + cookie persistence + предиктивный refresh |
| **P2 Medium** | 4 | 25 | Long-poll реконнект + DataDome retry + логирование |
| **P3 Polish** | 3 | 15 | Prefetch stats token + logout handler + multi-account |
| **Итого** | **17** | **105 ч** | Полноценная бесшовная авторизация |

### Что меняется для пользователя

| Ситуация | Сейчас | После плана |
|---|---|---|
| Переключение Wi-Fi→LTE | Выкидывает на форму входа | Ничего не замечает |
| Лифт/метро (потеря сети на 30 сек) | Выкидывает на форму входа | Ничего не замечает |
| Поездка в другой город (смена IP) | Выкидывает на форму входа | Ничего не замечает |
| Через 24 часа после последнего входа | Выкидывает на форму входа | Ничего не замечает (silent refresh через `p` cookie) |
| Через 1 год после последнего входа | N/A | Лента работает (anonym_token). AuthActivity только при попытке лайкнуть |
| Явный logout в web-версии | Не ловим | AuthActivity при следующем действии (как сейчас) |

### Связь с предыдущими документами

| Документ | Связь |
|---|---|
| `vk.id.md` | Прямой источник всех находок F-1..F-12 |
| `Профиль.md` FIX-133 | Расширение: anonym_token fallback теперь часть 4-уровневой цепи |
| `видео.md` Part B §6 | `video.getStatsToken` prefetch (P3-1) |
| `видео.md` Part B §7 | `queue.subscribe` long-poll реконнект (P2-1) |
| `видео.md` Part D §2 | Расширение Решения 4 (anonymous_token) до полного auth-chain |
| `музыка.md` | Audio player продолжает играть при смене сети (косвенный benefit) |

---

*Документ составлен как план применения находок `vk.id.md` (1072 строки). Реализация — модификация существующих файлов `ExchangeAuthRepository.kt`, `NetworkObserver.kt`, `CookieRefreshWorker.kt` + 7 новых файлов. Work-лог: `/home/z/my-project/worklog.md`.*
