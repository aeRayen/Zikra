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
// Further modules are added by their own work packages (BUILD_PLAN §4/§5):
// core:common (1.3), core:i18n (1.4), core:design-system (1.5), core:database (1.7), ...
