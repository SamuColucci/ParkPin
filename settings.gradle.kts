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

        // ECCO LA RIGA CHE MANCAVA!
        // Senza questa, Android Studio non trova "osmbonuspack"
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ParkPin"
include(":app")