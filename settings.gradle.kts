pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "ObsiLauncher"

// Android lives in ./android (pinned ZalithLauncher2 + overlay, built by its own Gradle, see android/README.md).
include(":core", ":desktop")
