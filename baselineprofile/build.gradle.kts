// O1 #PERF-BASELINE (Task 77): модуль-генератор Baseline Profile.
// Тип com.android.test — тестовый модуль, подключается к :app как цель профилирования.
// Требует реального устройства/эмулятора с Android 13+ (API 33+) — у нас Cyber 15.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
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

baselineProfile {
    // Профиль складывается в src/ (в модуль baselineprofile), затем вручную/плагином переносится в :app.
    saveInSrc = true
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
}