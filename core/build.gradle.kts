plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // network integration tests are opt-in: ./gradlew :core:test -Pnet=true
    systemProperty("obsi.net", (findProperty("net") ?: "false").toString())
    maxHeapSize = "384m"
}

// prints the runtime classpath so the headless CLI can run without Gradle (handy on small machines):
//   java -cp "$(./gradlew -q :core:printClasspath)" studio.obsifox.launcher.core.cli.CliMainKt versions
tasks.register("printClasspath") {
    dependsOn(tasks.jar)
    doLast { println((listOf(tasks.jar.get().archiveFile.get().asFile) + sourceSets.main.get().runtimeClasspath.files.filter { it.name.endsWith(".jar") }).joinToString(File.pathSeparator)) }
}
