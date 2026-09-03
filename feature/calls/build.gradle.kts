// Этап 1.3 модульной архитектуры (#ARCH-CONTAINERS): контейнер-пионер звонков.
// Первый :feature-контейнер: WebRtcEngine, VideoTextureRenderer (пакет re.pinok.media),
// CallModels (пакет re.pinok.data.model), CallsContainer (реестр capability)
// + UI-экраны звонков (ui/screens/calls/*, 12 файлов перенесены из :app на Этапе 1.3).
// Пакеты НЕ переименовывались — перенос = перемещение каталога; :app видит
// перенесённые классы как раньше (зависимость implementation(":feature:calls")).
// Правила: :feature:* НЕ зависят от :app и друг от друга — только :contracts + :core:*.
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
    // Compose-компилятор: модуль содержит CallScreen и все экраны звонков.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "re.pinok.feature.calls"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }
}

dependencies {
    // Контракты — единственный «публичный» слой контейнера (api: хост видит
    // capability-интерфейсы, найденные в реестре, без явной зависимости).
    api(project(":contracts"))

    // AppLog/BuildStamp (re.pinok.util / re.pinok).
    implementation(project(":core:common"))

    // CallSignalingClient (WS-сигналинг звонков) — уже в core-слое с Этапа 1.2-б.
    implementation(project(":core:network"))

    // ConversationParamsDecoder (декодер payload LP 115, компаньон WebRtcEngine).
    implementation(project(":core:media"))

    // #CALLS: WebRTC (org.webrtc.*) — WebRtcEngine/VideoTextureRenderer.
    implementation(libs.webrtc)

    // Compose-набор (BOM): CallScreen и все экраны звонков.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Coil (аватарки в CallScreen/CallsHistoryScreen)
    implementation(libs.coil.compose)

    // Navigation (для compose-маршрутов экранов)
    implementation(libs.androidx.navigation.compose)

    // #ARCH-DATA (Task 20): CallsDependencies.prefs — тип SovaPrefs из :core:data.
    implementation(project(":core:data"))

    // CallSignalingClient(deps.httpClient) — конструктор сигналинга принимает
    // okhttp3.OkHttpClient (тип нужен на classpath: лог сборки 2026-09-03).
    implementation(libs.okhttp)
    // CallScreen парсит vchat-ответы (JsonObject/JsonArray) и CallsApi-сигнатуры
    // возвращают gson-типы.
    implementation(libs.gson)
    // SharedFlow/StateFlow в CallsQueue/фасадах (явно, не транзитивно).
    implementation(libs.kotlinx.coroutines.android)
    // PermissionManager (перенесён в :feature:calls, пакет re.pinok.util) —
    // ContextCompat/Manifest проверки разрешений звонка.
    implementation(libs.androidx.core.ktx)
}
