// Define where Gradle can download build plugins
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
// Select the Java toolchain used to run Gradle
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// Define where app libraries can be downloaded
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Name the project and include the Android application module
rootProject.name = "Workout Tracker"
include(":app")
 
