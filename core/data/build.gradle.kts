// #ARCH-DATA (2026-09-03, Task 20): data-слой — SovaPrefs (+ вложенный Snapshot).
// Первая выемка из запланированного выделения data-слоя (контейнеры.план.md,
// Часть 3): перенос экранов звонков в :feature:calls (6742de6c) требует тип
// SovaPrefs в CallsDependencies (:feature:calls не может видеть :app-типы —
// цикл зависимостей запрещён). Пакеты НЕ переименовывались (re.pinok.data.local)
// — :app видит класс как раньше (implementation(":core:data")).
// Правило: :core:* НЕ зависят от :app и :feature:*; compose сюда не тащим.
// BuildConfig: библиотечному модулю НЕ нужен — дефолт showLogFab (был
// BuildConfig.DEBUG) подаётся хостом через параметр конструктора debugDefault
// (SovaApp: SovaPrefs(this, BuildConfig.DEBUG)) — паттерн инжекта AppLog.
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
}

android {
    namespace = "re.pinok.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    // AppLog (re.pinok.util) — логирование чтения/записи prefs.
    implementation(project(":core:common"))
    // DataStore Preferences — хранилище Snapshot.
    implementation(libs.androidx.datastore.preferences)
    // Flow/first/map.
    implementation(libs.kotlinx.coroutines.android)
    // Task 22 (2026-09-03): UserProfile (re.pinok.data.model) использует
    // @SerializedName — фасады CallsApi (:feature:calls) ссылаются на его тип.
    implementation(libs.gson)
}
