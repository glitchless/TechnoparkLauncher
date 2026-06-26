pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        google()
    }
}

rootProject.name = "TechnoparkLauncher"

include(":core")
include(":desktop")
