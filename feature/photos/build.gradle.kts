// Этап 1.5-а модульной архитектуры (#ARCH-CONTAINERS): контейнер фото.
// Публикует NavEntry «Фото» (route "photos" — destination хоста уже существует)
// и AttachmentRenderer (rendererKey "photos_inline"): инлайн-рендер фото-вложений
// чата (сетка 1/2 колонки + стикер-фото Fix #227/#228) переехал из ChatDetailScreen
// в PhotosInlineRenderer. Экран раздела PhotosScreen пока в :app (блокер SovaApp/
// apiClient — data-слой, см. контейнеры.план.md). Правила: :feature:* НЕ зависят
// от :app и друг от друга — только :contracts + :core:*.
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

    // ImageSaver (re.pinok.media) — сохранение фото в галерею. Сегодня им
    // пользуется хостовый PhotoViewer; задел на 1.5-б («скачать» из рендера).
    implementation(project(":core:media"))

    // Compose-набор (BOM): рендер фото-сетки — foundation/ui (+material3 про запас).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    // Coil 3 (coil-compose): AsyncImage для фото. ImageLoader — ГЛОБАЛЬНЫЙ,
    // его строит хост (SovaApp.newImageLoader: OkHttp с cookie-jar + GIF/
    // animated-WebP декодеры) — SingletonImageLoader подхватывается автоматически,
    // поэтому модулю нужны только compose-API (coil-network-okhttp/coil-gif не нужны).
    implementation(libs.coil.compose)
}
