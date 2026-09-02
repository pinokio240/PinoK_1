// Этап 1.2-в модульной архитектуры (#ARCH-CONTAINERS): медиа-хелперы.
// AudioRouteLogger, PlaybackPositionStore (де-факто «медиа-кэш» позиций),
// DocumentFileStorage (SAF/SD), ImageSaver, VideoPipController, VoiceRecorder,
// GeniusLyricsFetcher, SirenTranscoder. Пакеты НЕ переименовывались
// (re.pinok.media) — перенос = перемещение каталога.
// Правило: :core:* НЕ зависят от :app и :feature:*; compose/data-слой сюда не тащим.
// Аудиофокус в модуле НЕТ: он живёт внутри WebRtcEngine.kt (остался в :app,
// зона Этапа 1.3 :feature:calls) и у плеера ExoPlayer (handleAudioFocus=true
// в PlayerService) — выделение было бы правкой логики, не переносом.
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
}

android {
    namespace = "re.pinok.media"
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
    implementation(project(":core:common"))
    // ImageSaver (Dispatchers/withContext).
    implementation(libs.kotlinx.coroutines.android)
    // ImageSaver, GeniusLyricsFetcher.
    implementation(libs.okhttp)
    // PlaybackPositionStore (JSON-файл позиций).
    implementation(libs.gson)
    // DocumentFileStorage (SAF tree URI на SD-карте).
    implementation(libs.androidx.documentfile)
    // VideoPipController (@RequiresApi). Алиас добавлен в каталог на Этапе 1.2-в.
    implementation(libs.androidx.annotation)
    // SirenTranscoder (ffmpeg-kit community fork, см. комментарий в каталоге).
    implementation(libs.ffmpeg.kit.audio)
}
