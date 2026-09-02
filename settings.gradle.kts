pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://repo1.maven.org/maven2/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "SOVA_2_0"

// #ARCH-CONTAINERS (Этап 1.1): контрактный слой модульной архитектуры.
include(":app")
include(":contracts")
// #ARCH-CONTAINERS (Этап 1.2-а): общий core-слой (AppLog, BuildStamp, утилиты).
include(":core:common")
// #ARCH-CONTAINERS (Этап 1.2-б): сетевой слой — CallSignalingClient (WS-сигналинг
// звонков) + ConversationParamsDecoder (декодер payload'а звонков, компаньон).
// VKApiClient/LongPoll*/Queuev4Client/VkNotificationsNotifier остались в :app
// (замыкание на data-слой/UI — см. контейнеры.план.md, Этап 1.2).
include(":core:network")
// #ARCH-CONTAINERS (Этап 1.2-в): медиа-хелперы — AudioRouteLogger,
// PlaybackPositionStore (де-факто «медиа-кэш» позиций), DocumentFileStorage,
// ImageSaver, VideoPipController, VoiceRecorder, GeniusLyricsFetcher,
// SirenTranscoder. Аудиофокус остаётся в WebRtcEngine (:app) — зона Этапа 1.3.
// Плеер/download-менеджеры/сервисы остались в :app (data.model/UI/Service —
// см. контейнеры.план.md, Этап 1.2).
include(":core:media")
