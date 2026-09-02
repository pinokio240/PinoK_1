// Этап 1.5-б модульной архитектуры (#ARCH-CONTAINERS): контейнер аудио.
// Публикует NavEntry «Эквалайзер» (route "equalizer" — бывший ядерный пункт
// drawer, открывает полноэкранный EqualizerScreen), SettingsSection «Эквалайзер»
// (route "settings_audio" — бывшая ядерная вкладка настроек) и AttachmentRenderer
// (rendererKey "audio_inline"): инлайн-рендер аудио-вложений чата переехал из
// ChatDetailScreen в AudioInlineRenderer. Экраны плеера/эквалайзера
// (AudioPlayerScreen/EqualizerScreen/MusicScreen) и движки (PlayerConnection/
// AudioEffectsEngine/EqualizerHelper) ОСТАЮТСЯ в :app — блокер SovaApp/
// data.model (EqualizerPreset/Track/PlayerState) + PlayerService — data-слой,
// см. контейнеры.план.md, Этап 1.5-б. Правила: :feature:* НЕ зависят от :app
// и друг от друга — только :contracts + :core:*.
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
    // Compose-компилятор: модуль содержит компосабл-рендерер AudioInlineRenderer.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "re.pinok.feature.audio"
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
        // BuildConfig библиотечному модулю НЕ нужен: контейнер не читает
        // re.pinok.BuildConfig (прецедент 1.2-а/1.2-в/1.3/1.5-а: инжект из хоста).
        buildConfig = false
    }
}

dependencies {
    // Контракты — единственный «публичный» слой контейнера (api: хост видит
    // capability-интерфейсы, найденные в реестре, без явной зависимости).
    api(project(":contracts"))

    // AppLog (re.pinok.util).
    implementation(project(":core:common"))

    // Compose-набор (BOM): рендер строки аудио-вложения — foundation/ui/material3.
    // (:core:media НЕ подключён — по факту 1.5-б рендер воспроизведение не
    // выполняет: тап делегируется хосту через onPlay → PlayerConnection (:app);
    // VoiceRecorder (:core:media) остаётся в чате хоста — записи в модуле нет.)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    // Иконки: AudioInlineRenderer использует Icons.Filled.PlayArrow — тот же
    // артефакт, что у хоста (не полагаемся на транзитивность material3).
    implementation(libs.androidx.compose.material.icons.extended)
}
