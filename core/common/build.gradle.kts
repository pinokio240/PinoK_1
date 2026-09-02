// Этап 1.2-а модульной архитектуры (#ARCH-CONTAINERS): общий core-слой.
// AppLog, BuildStamp и чистые утилиты (backoff, форматирование, User-Agent,
// сетевой обсервер, состояние network-switch). Пакеты НЕ переименовывались
// (re.pinok / re.pinok.util) — перенос = перемещение каталога.
// Правило: :core:* НЕ зависят от :app и :feature:*; androidx/compose сюда не тащим.
// Единственная внешняя зависимость — kotlinx-coroutines-android
// (ExponentialBackoff: delay/ensureActive; NetworkObserver: StateFlow).
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
}

android {
    namespace = "re.pinok.common"
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
    implementation(libs.kotlinx.coroutines.android)
}
