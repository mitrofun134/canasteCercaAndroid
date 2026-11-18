pluginManagement {
    includeBuild("build-logic")
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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "canasta-cerca-android"

include(":app")

// Core
include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:navigation")
include(":core:network:api")
include(":core:network:impl")
include(":core:storage:api")
include(":core:storage:impl")

// Features: api — ключи навигации и контракты, impl — экраны, ViewModel, данные, DI
include(":feature:offers:api")
include(":feature:offers:impl")
include(":feature:catalog:api")
include(":feature:catalog:impl")
include(":feature:recipes:api")
include(":feature:recipes:impl")
include(":feature:basket:api")
include(":feature:basket:impl")
include(":feature:location:api")
include(":feature:location:impl")
include(":feature:payments:api")
include(":feature:payments:impl")
