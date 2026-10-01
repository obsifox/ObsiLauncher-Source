package studio.obsifox.launcher.core.cli

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import studio.obsifox.launcher.core.LauncherCore
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.modrinth.ProjectKind
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Headless developer CLI on top of the same core the GUI uses. Handy for CI smoke tests and for diagnosing
 * problems on machines without a display:
 *
 *   obsi-cli versions
 *   obsi-cli loaders <loader> <mc>
 *   obsi-cli search <query>
 *   obsi-cli create <mc> [vanilla|fabric|quilt|forge|neoforge] [loaderVersion]
 *   obsi-cli launch <instanceId> [playerName]       (prints the game output; add --dry to only print the command)
 *
 * Data directory: $OBSI_HOME (default: the same place as the GUI).
 */
fun main(args: Array<String>) {
    runBlocking { cli(args) }
}

private suspend fun cli(args: Array<String>) {
    if (args.isEmpty()) {
        println("usage: obsi-cli versions | loaders <loader> <mc> | search <q> | create <mc> [loader] [ver] | launch <id> [name] [--dry]")
        exitProcess(2)
    }
    val core = LauncherCore(LauncherPaths(studio.obsifox.launcher.core.util.Platform.defaultDataDir()), "cli", System.getenv("MS_CLIENT_ID"))
    var lastStage = ""
    val progress: (studio.obsifox.launcher.core.util.ProgressUpdate) -> Unit = { u ->
        val line = if (u.fraction >= 0) "${u.stage} ${(u.fraction * 100).toInt()}%" else u.stage
        if (line != lastStage && (u.fraction < 0 || (u.fraction * 100).toInt() % 10 == 0 || u.fraction >= 1f)) {
            lastStage = line
            System.err.println("  $line")
        }
    }
    when (args[0]) {
        "versions" -> {
            val m = core.mojang.manifest()
            println("latest release ${m.latest.release}, snapshot ${m.latest.snapshot}, ${m.versions.size} versions")
        }
        "loaders" -> core.loaders.versions(LoaderType.valueOf(args[1].uppercase()), args[2]).take(10).forEach { println(it) }
        "search" -> core.modrinth.search(args.drop(1).joinToString(" "), ProjectKind.MOD).hits.forEach { println("${it.title} (${it.slug}) - ${it.downloads} downloads") }
        "create" -> {
            val loader = args.getOrNull(2)?.let { LoaderType.valueOf(it.uppercase()) } ?: LoaderType.VANILLA
            val inst = core.createInstance("CLI ${args[1]} ${loader.name.lowercase()}", args[1], loader, args.getOrNull(3), progress)
            core.instances.save(inst.copy(maxMemoryMb = 640, minMemoryMb = 128))
            println("created ${inst.id} -> ${inst.launchVersionId}")
        }
        "mods" -> {
            val inst = core.instances.get(args[1]) ?: error("no such instance")
            core.content.list(inst, ProjectKind.MOD).forEach { println("${if (it.enabled) "[x]" else "[ ]"} ${it.displayName} ${it.entry?.versionNumber ?: ""} (${it.fileName})") }
        }
        "install" -> {
            val inst = core.instances.get(args[1]) ?: error("no such instance")
            val project = core.modrinth.project(args[2])
            val version = core.content.pickVersion(inst, project.id, ProjectKind.fromApi(project.projectType)) ?: error("no compatible version")
            println("installing ${project.title} ${version.versionNumber}")
            println("installed: " + core.content.install(inst, project, version, progress))
        }
        "updates" -> {
            val inst = core.instances.get(args[1]) ?: error("no such instance")
            println("identified ${core.content.identifyUnknown(inst)} unknown files")
            val ups = core.content.checkUpdates(inst)
            println("${ups.size} update(s)")
            ups.forEach { println("  ${it.file.displayName}: ${it.file.entry?.versionNumber} -> ${it.latest.versionNumber}") }
        }
        "modpack" -> {
            val project = core.modrinth.project(args[1])
            val versions = core.modrinth.versions(project.id)
            val v = versions.firstOrNull { it.versionType == "release" } ?: versions.first()
            println("installing modpack ${project.title} ${v.versionNumber} (${v.gameVersions.firstOrNull()})")
            val inst = core.modpacks.install(project, v, progress)
            println("created ${inst.id}: ${inst.mcVersion} ${inst.loader} -> ${inst.launchVersionId}")
        }
        "ms-probe" -> {
            try {
                val info = core.auth.microsoft.startDeviceCode()
                println("device code OK: ${info.userCode} at ${info.verificationUri} (valid ${info.expiresInSec}s)")
            } catch (e: Exception) { println("device code failed: ${e.message}") }
        }
        "launch" -> {
            val id = args[1]
            val name = args.getOrNull(2)?.takeIf { !it.startsWith("--") } ?: "Tester"
            val account = core.auth.addOffline(name)
            val prepared = core.prepare(id, account, progress)
            if ("--dry" in args) {
                println(prepared.command.redacted(emptyList()))
            } else {
                val session = core.start(prepared)
                var printed = 0
                val code = withTimeoutOrNull(150_000) {
                    while (session.running.value) {
                        val lines = session.lines.value
                        while (printed < lines.size) println(lines[printed++])
                        kotlinx.coroutines.delay(300)
                    }
                    session.exitCode.await()
                }
                val lines = session.lines.value
                while (printed < lines.size) println(lines[printed++])
                if (code == null) { session.stopAndWait(); println("TIMEOUT: game still running after 150 s"); exitProcess(3) }
                println("EXIT $code")
            }
        }
        else -> { println("unknown command ${args[0]}"); exitProcess(2) }
    }
}
