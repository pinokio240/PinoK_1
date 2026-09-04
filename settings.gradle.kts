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
// SirenTranscoder. WebRtcEngine (с аудиофокусом внутри) переехал в
// :feature:calls на Этапе 1.3.
// Плеер/download-менеджеры/сервисы остались в :app (data.model/UI/Service —
// см. контейнеры.план.md, Этап 1.2).
include(":core:media")
// #ARCH-DATA (2026-09-03, Task 20): data-слой — SovaPrefs (+Snapshot). Первая
// выемка из запланированного выделения data-слоя (контейнеры.план.md, Часть 3):
// предпосылка — перенос экранов звонков в :feature:calls (6742de6c), экранам
// нужен тип SovaPrefs в CallsDependencies. Пакеты НЕ переименовываются
// (re.pinok.data.local) — :app видит класс как раньше.
include(":core:data")
// #ARCH-CONTAINERS Этап 3.7-1 (2026-09-03, решение пользователя): дом
// УНИВЕРСАЛЬНЫХ compose-компонентов (без доменной специфики) — виден и ядру,
// и всем контейнерам. Первый жилец — ErrorView (пакет re.pinok.ui.components
// сохранён; дальше — по потребности контейнеров, §3.3 решатель).
include(":core:ui")
// #ARCH-CONTAINERS (Этап 1.3): контейнер-пионер звонков — WebRtcEngine,
// VideoTextureRenderer (re.pinok.media), CallModels (re.pinok.data.model),
// CallsContainer (capability-реестр звонков). UI-экраны звонков пока в :app
// (блокер SovaApp — см. контейнеры.план.md, Этап 1.3/1.4).
include(":feature:calls")
// #ARCH-CONTAINERS (Этап 1.5-а): контейнер фото — NavEntry «Фото» (route "photos")
// + AttachmentRenderer ("photos_inline": инлайн-рендер фото-вложений чата,
// перенесён из ChatDetailScreen). Этап 3.7-1: сюда же переехал экран раздела
// PhotosScreen (git mv, пакет re.pinok.ui.screens.photos сохранён) и PhotoViewer
// (фото-домен); ErrorView — в :core:ui (универсальный).
include(":feature:photos")
// #ARCH-CONTAINERS (Этап 1.5-б): контейнер аудио — NavEntry «Эквалайзер»
// (route "equalizer" — бывший ядерный пункт drawer) + SettingsSection
// («Эквалайзер», route "settings_audio" — контент остаётся в :app) +
// AttachmentRenderer ("audio_inline": инлайн-рендер аудио-вложений чата,
// перенесён из ChatDetailScreen). Плеер/эквалайзер-экраны и движки
// (PlayerConnection/PlayerService/AudioEffectsEngine/EqualizerHelper) пока
// в :app — блокер data-слой (SovaApp/data.model) — см. контейнеры.план.md,
// Этап 1.5-б.
include(":feature:audio")
