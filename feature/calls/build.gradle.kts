// Этап 1.3 модульной архитектуры (#ARCH-CONTAINERS): контейнер-пионер звонков.
// Первый :feature-контейнер: WebRtcEngine, VideoTextureRenderer (пакет re.pinok.media),
// CallModels (пакет re.pinok.data.model) и CallsContainer (реестр capability).
// Пакеты НЕ переименовывались — перенос = перемещение каталога; :app видит
// перенесённые классы как раньше (зависимость implementation(":feature:calls")).
// Правила: :feature:* НЕ зависят от :app и друг от друга — только :contracts + :core:*.
// UI-экраны звонков (ui/screens/calls/*, 12 файлов) пока в :app — блокер SovaApp
// (см. контейнеры.план.md, Этап 1.3): переезд экранов = Этап 1.4 (хост на реестре).
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
    // Compose-компилятор подключён с первого дня (модуль целится в приём
    // CallScreen/секций на Этапе 1.4); compose-зависимости добавятся по факту
    // переноса экранов — текущему коду модуля compose не нужен.
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
        // compose-кода в модуле пока нет (контейнер — без UI); флаг включён
        // заранее, чтобы Этап 1.4 не трогал gradle-файл.
        compose = true
        // BuildConfig библиотечному модулю НЕ нужен: перенесённый код не читает
        // re.pinok.BuildConfig (прецедент 1.2-а/1.2-в: AppLog.setAppBuildInfo,
        // AppLog.debugBuild — инжект из хоста).
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
}
