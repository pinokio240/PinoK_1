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
    // kotlin.compose НЕ подключён (раньше был «на вырост» — ошибка): плагин
    // Compose-компилятора требует androidx.compose.runtime на classpath ДАЖЕ
    // в модуле без @Composable — с плагином и без compose-зависимостей
    // :feature:calls:compileDebugKotlin падал «The Compose Compiler requires
    // the Compose Runtime to be on the class path» (лог сборки 2026-09-02,
    // Task 18). В модуле 0 compose-кода (grep androidx.compose|@Composable = 0).
    // Вернуть плагин + compose = true + compose-bom/ui/material3 ОДНИМ коммитом
    // при реальном переносе CallScreen — прецедент :feature:photos/:feature:audio.
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
        // compose = true НЕ включён: флаг без плагина/зависимостей мёртвый,
        // вернуть ВМЕСТЕ с kotlin.compose при переносе CallScreen (см. plugins).
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
