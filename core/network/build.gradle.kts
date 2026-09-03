// Этап 1.2-б модульной архитектуры (#ARCH-CONTAINERS): сетевой слой.
// Перенесено (git mv, пакеты НЕ переименовывались — перенос = перемещение каталога):
//   - re.pinok.realtime.CallSignalingClient — WS-сигналинг звонков VK
//     (протокол calls SDK, ping/pong, transmit-data; bounce/перерегистрация).
//   - re.pinok.media.ConversationParamsDecoder — декодер conversation params звонка
//     (payload LP 115: base64+LZ4 → JSON; разбор ответа vchat.getConversationParams).
//     Компаньон CallSignalingClient: замыкание чистое (gson + java.util.Base64).
// Остальной сетевой слой (VKApiClient, LongPollClient/LongPollKeepAliveService,
// Queuev4Client, VkNotificationsNotifier и notifier/poller-хвост realtime/) ОСТАЛСЯ
// в :app — замыкание на data-слой (SovaPrefs/TokenStorage/data.model), SovaApp,
// R-ресурсы и UI-классы; см. контейнеры.план.md (Этап 1.2) — вводная для 1.3.
// Правило: :core:* НЕ зависят от :app и :feature:*; androidx сюда не тащим.
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
}

android {
    namespace = "re.pinok.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // AppLog (re.pinok.util) — общий логгер из 1.2-а.
    implementation(project(":core:common"))
    // #ARCH-DATA (Task 20): ExchangeAuthRepository (re.pinok.auth.exchange) читает SovaPrefs.
    implementation(project(":core:data"))
    // CallSignalingClient: okhttp3 (OkHttpClient/Request/WebSocket/WebSocketListener);
    // okio.ByteString — транзитивная зависимость okhttp (на compile-classpath модуля).
    implementation(libs.okhttp)
    // ConversationParamsDecoder + CallSignalingClient: com.google.gson (JsonParser/JsonObject).
    implementation(libs.gson)
    // CallSignalingClient: CoroutineScope/Job/Dispatchers/SharedFlow/delay.
    implementation(libs.kotlinx.coroutines.android)
}
