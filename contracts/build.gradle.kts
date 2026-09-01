// Этап 1.1 модульной архитектуры (#ARCH-CONTAINERS): контрактный слой.
// Чистые интерфейсы БЕЗ androidx/compose-зависимостей — только android.jar.
// Все :feature:* контейнеры зависят от этого модуля, хост (:app) тоже.
// Правило: контейнеры НЕ зависят друг от друга — только от :contracts.
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
}

android {
    namespace = "re.pinok.contracts"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
