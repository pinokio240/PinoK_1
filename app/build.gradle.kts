plugins {
    alias(libs.plugins.android.application)
    // kotlin-android НЕ нужен — AGP 9.0+ имеет встроенную поддержку Kotlin
    // https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "re.pinok"
    compileSdk = 36

    defaultConfig {
        applicationId = "re.pinok"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // VK credentials — official VK Android client (client_id=2274003).
        // Required for auth via id.vk.com/auth_by_exchange_token.
        // From decompiled VK 8.178: AuthByExchangeToken.kt sends all requests
        // to this single endpoint with different grant_type values.
        buildConfigField("String", "VK_CLIENT_ID", "\"2274003\"")
        buildConfigField("String", "VK_CLIENT_SECRET", "\"hHbZxrka2uZ6jB1inYsH\"")
        buildConfigField("String", "VK_API_VERSION", "\"5.269\"")
        buildConfigField("String", "VK_API_HOST", "\"https://api.vk.com\"")
        // VK OAuth endpoint (oauth.vk.com) — Implicit Grant flow, password auth, 2FA.
        // НЕ id.vk.com — id.vk.com это VK ID endpoint (только exchange_token refresh).
        buildConfigField("String", "VK_OAUTH_HOST", "\"https://oauth.vk.com\"")
        // VK ID host — PRIMARY auth endpoint (id.vk.com/auth_by_exchange_token).
        buildConfigField("String", "VK_ID_HOST", "\"https://id.vk.com\"")
        // VK Web client_id — единый canonical ID для web-token flow (audit Medium #1).
        // Значение 6287487 (vk.com desktop web) подтверждено дампом ВК.txt:
        //   6287487:get_anonym_token → anonym.eyJ...
        //   6287487:web_token        → vk1.a.38fKxG41... (рабочий access_token)
        // Используется в OAuthWebViewActivity.kt (Implicit Grant flow).
        // WebTokenAuth.kt использует hardcoded WEB_APP_IDS list (7879029 + 6287487)
        // с retry — см. #49.
        buildConfigField("String", "VK_WEB_CLIENT_ID", "\"6287487\"")
        // #49: VK Web mobile client_id — m.vk.com web app (как в VK_X_3).
        // Primary в WebTokenAuth retry. БЕЗ client_secret (в отличие от desktop).
        buildConfigField("String", "VK_WEB_MOBILE_CLIENT_ID", "\"7879029\"")
        // User-Agent генерируется динамически в VkUserAgent.get() — формат
        // идентичен VK.app (SOVA RE: defpackage/C7754aaaaa.java:83).
        // Статический хардкод здесь убран: некорректное содержимое
        // (Android-Studio, smartphone вместо manufacturer/model/WxH)
        // приводило к error 15 на messages.* (VK отбрасывает по UA).
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }

    androidResources {
        // Support for Cyrillic in resources
        generateLocaleConfig = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // #SESSION-COOKIES-BG-REFRESH: ProcessLifecycleOwner — cookie sync на app foreground.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.documentfile)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Fragment (BiometricPrompt requires FragmentActivity)
    implementation(libs.androidx.fragment.ktx)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security / EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)

    // Biometric
    implementation(libs.androidx.biometric)

    // WebKit (OAuth WebView fallback)
    implementation(libs.androidx.webkit)

    // Browser (Chrome Custom Tabs — OAuth via external browser to bypass "direct auth" block)
    implementation(libs.androidx.browser)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coil 3
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Fix #229: анимированные стикеры (GIF + animated WebP декодеры).
    implementation(libs.coil.gif)

    // Gson
    implementation(libs.gson)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.database)
    // Fix #68: HLS для VK audio (index.m3u8?siren=1) и video (video.m3u8).
    implementation(libs.androidx.media3.exoplayer.hls)

    // §42.12 P0 #2: ffmpeg-kit-audio — Siren→AAC транскодер.
    // Audio-only build (~15-20 MB). Расшифровывает VK Siren (G.722.1) офлайн.
    // Без этой зависимости siren-треки кэшируются как .ts (codec=siren) и
    // стримятся онлайн — Wi-Fi бейдж в UI. С ней — транскодируются в .m4a (aac),
    // играют офлайн.
    implementation(libs.ffmpeg.kit.audio)

    // §37.12 Phase 5: CameraX для записи клипов (ClipCreateScreen).
    // Preview + VideoCapture (Recorder/FileOutputOptions). minSdk 24 совместим.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    // #SESSION-COOKIES-BG-REFRESH: WorkManager — periodic background cookie sync
    // (CookieRefreshWorker, каждые 6ч). Ловит ротэйты remixsid/p/remixnsid пока
    // app в фоне, чтобы silentRefreshViaRemixsid не слал стейловые cookies.
    implementation(libs.androidx.work.runtime.ktx)

    // #CALLS: WebRTC (org.webrtc.* API) от Stream — голосовые/видеозвонки.
    implementation(libs.webrtc)
}
