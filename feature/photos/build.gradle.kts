// Этап 1.5-а модульной архитектуры (#ARCH-CONTAINERS): контейнер фото.
// Публикует NavEntry «Фото» (route "photos" — destination хоста уже существует)
// и AttachmentRenderer (rendererKey "photos_inline"): инлайн-рендер фото-вложений
// чата (сетка 1/2 колонки + стикер-фото Fix #227/#228) переехал из ChatDetailScreen
// в PhotosInlineRenderer.
// Этап 3.7-1 (2026-09-03): PhotosScreen переехал сюда из :app (git mv, пакет
// re.pinok.ui.screens.photos сохранён) вместе с PhotoViewer (фото-домен, пакет
// re.pinok.ui.components сохранён — :app продолжает видеть его через classpath);
// ErrorView — в :core:ui (универсальный, решение пользователя); DI-контракт —
// PhotosDependencies/PhotosApi/LocalPhotosDeps (канон §3.2).
// Правила: :feature:* НЕ зависят от :app и друг от друга — только :contracts + :core:*
// (проверяемые инварианты И1–И7 канона §3.5).
plugins {
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
    // Compose-компилятор: модуль содержит компосабл-рендерер PhotosInlineRenderer.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "re.pinok.feature.photos"
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
        // re.pinok.BuildConfig (прецедент 1.2-а/1.2-в/1.3: инжект из хоста).
        buildConfig = false
    }
}

dependencies {
    // Контракты — единственный «публичный» слой контейнера (api: хост видит
    // capability-интерфейсы, найденные в реестре, без явной зависимости).
    api(project(":contracts"))

    // AppLog (re.pinok.util).
    implementation(project(":core:common"))

    // ImageSaver (re.pinok.media) — сохранение фото в галерею (PhotoViewer).
    implementation(project(":core:media"))

    // #ARCH-CONTAINERS 3.7-1: Album/PhotoItem и весь пакет re.pinok.data.model
    // перенесены в :core:data (пакет сохранён); PhotosApi ссылается на них
    // в сигнатурах.
    implementation(project(":core:data"))

    // ErrorView (re.pinok.ui.components) — универсальный компонент в :core:ui
    // (решение пользователя, Этап 3.7-1); PhotoViewer — фото-домен, здесь.
    implementation(project(":core:ui"))

    // Compose-набор (BOM): рендер фото-сетки — foundation/ui (+material3 про запас).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    // Иконки PhotoViewer/ErrorView/PhotosScreen: Download, ErrorOutline, WifiOff,
    // PhotoLibrary, Favorite/FavoriteBorder (extended — не все в core-наборе).
    implementation(libs.androidx.compose.material.icons.extended)
    // Coroutines: launch/snapshotFlow/distinctUntilChanged (PhotosScreen/PhotoViewer).
    implementation(libs.kotlinx.coroutines.android)

    // Coil 3 (coil-compose): AsyncImage для фото. ImageLoader — ГЛОБАЛЬНЫЙ,
    // его строит хост (SovaApp.newImageLoader: OkHttp с cookie-jar + GIF/
    // animated-WebP декодеры) — SingletonImageLoader подхватывается автоматически,
    // поэтому модулю нужны только compose-API (coil-network-okhttp/coil-gif не нужны).
    implementation(libs.coil.compose)
}
