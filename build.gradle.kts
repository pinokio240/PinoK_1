// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.kotlin.compose) apply false
    // #ARCH-CONTAINERS (Этап 1.1): :contracts — контрактный слой модульной архитектуры.
    alias(libs.plugins.android.library) apply false
}
