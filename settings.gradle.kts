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
    }
}

rootProject.name = "canasta-cerca-android"

// Пока приложение — оболочка над сайтом (WebView), один модуль.
// Нативные экраны (Compose, Metro, Navigation 3, 6 фич) — в ветке `native`.
include(":app")
