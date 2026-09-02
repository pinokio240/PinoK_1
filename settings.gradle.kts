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
