pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    // lets Gradle auto-provision a matching JDK (21) on machines that only ship a JRE
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
