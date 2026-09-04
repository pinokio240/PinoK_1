// #ARCH-CONTAINERS Этап 3.7-1 (2026-09-03, решение пользователя): дом
// УНИВЕРСАЛЬНЫХ compose-компонентов без доменной специфики — виден и ядру
// (:app), и всем контейнерам (:feature:*), поэтому НЕ домен ни одного
// контейнера. Первый жилец — ErrorView (re.pinok.ui.components, git mv, пакет
// сохранён; использовали 7 ядерных экранов + PhotosScreen; прецедент владения
// «у контейнера» из Task 20 (PermissionManager) признан запахом: следующему
// контейнеру (:feature:audio) он понадобился бы, а фичи друг от друга НЕ
// зависят — дублировать нельзя). Правило канона (§3.3): универсальный
// compose-компонент (≥1 контейнер + ядро) → :core:ui; доменный (только
// домен контейнера) → в сам контейнер (PhotoViewer — фото-домен →
// :feature:photos). Правила: :core:* НЕ зависят от :app/:feature:*;
// kotlin.compose ставится ТОЛЬКО с полным compose-набором (урок Task 18).
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
    // Compose-компилятор: модуль содержит компосаблы.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "re.pinok.core.ui"
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
        // BuildConfig библиотечному модулю НЕ нужен (прецедент 1.2-а/1.2-в).
        buildConfig = false
    }
}

dependencies {
    // AppLog (re.pinok.util) — если компоненту нужен лог.
    implementation(project(":core:common"))

    // Compose-набор (BOM) — ПОЛНЫЙ (плагин kotlin.compose требует runtime на
    // classpath даже без @Composable — IrGenerationExtensionException, Task 18).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Coroutines — про запас для компонентов с эффектами (явно, не транзитивно).
    implementation(libs.kotlinx.coroutines.android)
}
