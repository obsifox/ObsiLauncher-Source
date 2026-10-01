package studio.obsifox.launcher.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * On-disk layout (everything below [root]):
 *
 *   shared/            acts as a regular ".minecraft": versions/, libraries/, assets/ are shared by all instances
 *   runtime/           Java runtimes downloaded from Mojang
 *   instances/<id>/    instance.json + game/ (the per-instance game directory: mods, saves, options.txt ...)
 *   cache/ logs/ accounts.json settings.json
 */
class LauncherPaths(val root: Path) {
    val shared: Path = root.resolve("shared")
    val versions: Path = shared.resolve("versions")
    val libraries: Path = shared.resolve("libraries")
    val assets: Path = shared.resolve("assets")
    val runtime: Path = root.resolve("runtime")
    val instances: Path = root.resolve("instances")
    val cache: Path = root.resolve("cache")
    val logs: Path = root.resolve("logs")
    val accountsFile: Path = root.resolve("accounts.json")
    val settingsFile: Path = root.resolve("settings.json")

    fun versionDir(id: String): Path = versions.resolve(id)
    fun versionJson(id: String): Path = versionDir(id).resolve("$id.json")
    fun versionJar(id: String): Path = versionDir(id).resolve("$id.jar")
    fun nativesDir(id: String): Path = versionDir(id).resolve("natives")

    fun instanceDir(id: String): Path = instances.resolve(id)
    fun instanceFile(id: String): Path = instanceDir(id).resolve("instance.json")
    fun gameDir(id: String): Path = instanceDir(id).resolve("game")

    fun ensure() {
        listOf(shared, versions, libraries, assets, runtime, instances, cache, logs).forEach { Files.createDirectories(it) }
    }
}
