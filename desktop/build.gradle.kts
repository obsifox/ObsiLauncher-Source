import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

val appVersion: String = rootProject.file("VERSION").readText().trim()

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
}

// build.properties: version + CI commit, embedded for the update check
val generateBuildInfo by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildinfo")
    val commit = providers.environmentVariable("GITHUB_SHA").orElse("dev")
    inputs.property("version", appVersion)
    inputs.property("commit", commit)
    outputs.dir(outDir)
    doLast {
        val f = outDir.get().file("obsi-build.properties").asFile
        f.parentFile.mkdirs()
        f.writeText("version=$appVersion\ncommit=${commit.get().take(7)}\n")
    }
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated/buildinfo")) }
tasks.processResources { dependsOn(generateBuildInfo) }

compose.desktop {
    application {
        mainClass = "studio.obsifox.launcher.desktop.MainKt"
        jvmArgs += listOf("-Xmx512m", "-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ObsiLauncher"
            packageVersion = appVersion
            description = "ObsiLauncher - a multi-profile Minecraft: Java Edition launcher with Modrinth"
            vendor = "ObsiFox Studio"
            copyright = "© ObsiFox Studio"
            // modules the packaged runtime needs (see `./gradlew :desktop:suggestRuntimeModules`)
            modules("java.instrument", "java.management", "java.net.http", "jdk.unsupported", "java.naming", "java.sql", "jdk.crypto.ec", "jdk.accessibility")

            windows {
                iconFile.set(project.file("packaging/icon.ico"))
                menuGroup = "ObsiFox"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "6d0f2a4e-8c4b-4f6e-9a57-3d2f7b1c9e10"
            }
            linux {
                iconFile.set(project.file("packaging/icon.png"))
                packageName = "obsilauncher"
                debMaintainer = "dev@obsifox.invalid"
                menuGroup = "Game"
                appCategory = "Game"
                shortcut = true
            }
        }
    }
}

// classpath for the headless screenshot tool:  java -cp "$(./gradlew -q :desktop:printTestClasspath)" studio.obsifox.launcher.desktop.ScreenshotsKt <outDir> en
tasks.register("printTestClasspath") {
    dependsOn(tasks.named("testClasses"))
    doLast { println(sourceSets["test"].runtimeClasspath.files.joinToString(File.pathSeparator)) }
}
