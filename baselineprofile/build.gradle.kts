// O1 #PERF-BASELINE (Task 77, plugin-free revision): модуль-генератор Baseline Profile.
// ВАЖНО: androidx.baselineprofile Gradle-плагин НЕ применяем — версия 1.4.1 несовместима
// с AGP 9.1.1 ("Module :app is not a supported android module"). BaselineProfileRule
// (из benchmark-macro-junit4) пишет baseline-prof.txt в build-каталог этого модуля;
// затем вручную копируем в app/src/main/baseline-prof.txt.
// Тип com.android.test — тестовый модуль, инструментирует :app как target.
// Требует реального устройства/эмулятора Android 13+ (API 33+) — у нас Cyber 15.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "re.pinok.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
}
