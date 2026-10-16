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

rootProject.name = "zikra"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:i18n")
include(":core:design-system")
include(":core:database")
include(":core:network")
include(":core:prayer")
include(":core:places")
include(":core:quran")
include(":core:adhkar")
include(":core:memorization")
include(":core:packs")
include(":core:hadith")
include(":core:settings")
include(":platform:alarm")
include(":tooling:detekt-rules")
include(":tooling:content-pipeline")
// Further modules are added by their own work packages (BUILD_PLAN §4/§5):
// ...
