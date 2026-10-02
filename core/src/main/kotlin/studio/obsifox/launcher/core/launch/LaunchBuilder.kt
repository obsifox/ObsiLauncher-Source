package studio.obsifox.launcher.core.launch

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.auth.Account
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.instance.Settings
import studio.obsifox.launcher.core.mojang.Rule
import studio.obsifox.launcher.core.mojang.ResolvedVersion
import studio.obsifox.launcher.core.mojang.Rules
import studio.obsifox.launcher.core.util.Platform
import studio.obsifox.launcher.core.util.RemoteJson
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class LaunchCommand(val executable: Path, val args: List<String>, val workDir: Path) {
    val commandLine: List<String> get() = listOf(executable.toString()) + args

    /** Printable command line with secrets masked. */
    fun redacted(secrets: Collection<String>): String =
        commandLine.joinToString(" ") { a ->
            var s = a
            for (sec in secrets) if (sec.length > 8) s = s.replace(sec, "***")
            if (s.any { it.isWhitespace() }) "\"$s\"" else s
        }
}

object LaunchBuilder {
    const val LAUNCHER_NAME = "ObsiLauncher"

    private val gcDefaults = listOf(
        "-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20", "-XX:G1ReservePercent=20",
        "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M",
    )

    fun effectiveMaxMemoryMb(instance: Instance, settings: Settings): Int {
        instance.maxMemoryMb?.let { return it }
        if (settings.defaultMaxMemoryMb > 0) return settings.defaultMaxMemoryMb
        val total = Platform.totalMemoryMb()
        return if (total <= 0) 2048 else (total / 2).coerceIn(1024, 4096)
    }

    fun effectiveMinMemoryMb(instance: Instance, settings: Settings, max: Int): Int =
        (instance.minMemoryMb ?: settings.defaultMinMemoryMb).coerceIn(128, max)

    /** @param classpathLibs libraries valid for this OS, in order (client jar is appended here). */
    fun build(
        rv: ResolvedVersion,
        paths: LauncherPaths,
        classpathLibs: List<Path>,
        instance: Instance,
        account: Account,
        settings: Settings,
        javaExe: Path,
        launcherVersion: String,
        gameDir: Path = paths.gameDir(instance.id),
    ): LaunchCommand {
        val clientJar = paths.versionJar(rv.id)
        // NB: java.nio.file.Path is an Iterable<Path>, so `list + path` would append its name elements - always wrap in listOf().
        val classpath = (classpathLibs + listOf(clientJar)).joinToString(File.pathSeparator) { it.toString() }
        val natives = paths.nativesDir(rv.id)
        val quickPlay = instance.autoJoinServer?.takeIf { it.isNotBlank() }
        val hasResolution = instance.width != null && instance.height != null

        val features = mapOf(
            "is_demo_user" to false,
            "has_custom_resolution" to hasResolution,
            "has_quick_plays_support" to (quickPlay != null),
            "is_quick_play_singleplayer" to false,
            "is_quick_play_multiplayer" to (quickPlay != null),
            "is_quick_play_realms" to false,
        )

        val assetsDir = paths.assets
        val gameAssets = when {
            rv.assetIndex.id == "pre-1.6" -> gameDir.resolve("resources")
            rv.assetIndex.id == "legacy" -> assetsDir.resolve("virtual").resolve("legacy")
            else -> assetsDir
        }
        val token = "0" // local accounts only: vanilla offline servers accept the zero token
        val vars = HashMap<String, String>().apply {
            put("auth_player_name", account.username)
            put("auth_uuid", account.uuid)
            put("auth_access_token", token)
            put("auth_session", "token:$token:${account.uuid}")
            put("auth_xuid", "0")
            put("clientid", "0")
            put("user_type", "legacy")
            put("user_properties", "{}")
            put("version_name", rv.id)
            put("version_type", rv.type)
            put("game_directory", gameDir.toString())
            put("assets_root", assetsDir.toString())
            put("assets_index_name", rv.assetIndex.id)
            put("game_assets", gameAssets.toString())
            put("natives_directory", natives.toString())
            put("launcher_name", LAUNCHER_NAME)
            put("launcher_version", launcherVersion)
            put("classpath", classpath)
            put("classpath_separator", File.pathSeparator)
            put("library_directory", paths.libraries.toString())
            put("primary_jar_name", clientJar.fileName.toString())
            put("resolution_width", (instance.width ?: 854).toString())
            put("resolution_height", (instance.height ?: 480).toString())
            if (quickPlay != null) put("quickPlayMultiplayer", quickPlay)
        }
        fun sub(s: String): String = placeholder.replace(s) { m -> vars[m.groupValues[1]] ?: m.value }

        // ---------------------------------------------------------------- JVM arguments
        val maxMb = effectiveMaxMemoryMb(instance, settings)
        val minMb = effectiveMinMemoryMb(instance, settings, maxMb)
        val userJvm = splitArgs(settings.defaultJvmArgs) + splitArgs(instance.jvmArgs)

        val jvm = ArrayList<String>()
        jvm += "-Xms${minMb}M"
        jvm += "-Xmx${maxMb}M"
        if (userJvm.none { it.startsWith("-XX:+Use") && it.endsWith("GC") }) jvm += gcDefaults
        jvm += userJvm

        val versionJvm = expand(rv.jvmArgs, features).map(::sub).toMutableList()
        if (versionJvm.none { it.startsWith("-Djava.library.path") }) versionJvm += "-Djava.library.path=$natives"
        if (versionJvm.none { it.startsWith("-Dminecraft.launcher.brand") }) versionJvm += "-Dminecraft.launcher.brand=$LAUNCHER_NAME"
        if (versionJvm.none { it.startsWith("-Dminecraft.launcher.version") }) versionJvm += "-Dminecraft.launcher.version=$launcherVersion"
        if (versionJvm.none { it == "-cp" || it == "-classpath" }) {
            versionJvm += "-cp"
            versionJvm += classpath
        }
        jvm += versionJvm

        rv.logging?.let { l ->
            val f = l.file?.id?.let { paths.assets.resolve("log_configs").resolve(it) }
            if (f != null && Files.isRegularFile(f) && l.argument != null && jvm.none { it.startsWith("-Dlog4j.configurationFile") }) {
                jvm += l.argument.replace("\${path}", f.toString())
            }
        }

        // ---------------------------------------------------------------- game arguments
        val game = ArrayList<String>()
        rv.legacyGameArgs?.let { game += splitArgs(it).map(::sub) }
        game += expand(rv.gameArgs, features).map(::sub)
        if (quickPlay != null && game.none { it == "--quickPlayMultiplayer" }) {
            val host = quickPlay.substringBeforeLast(':', quickPlay)
            val port = if (':' in quickPlay) quickPlay.substringAfterLast(':') else "25565"
            game += listOf("--server", host, "--port", port)
        }
        if (hasResolution && game.none { it == "--width" }) game += listOf("--width", instance.width.toString(), "--height", instance.height.toString())
        if (instance.fullscreen && "--fullscreen" !in game) game += "--fullscreen"
        game += splitArgs(instance.gameArgs).map(::sub)

        val args = jvm + rv.mainClass + game
        return LaunchCommand(javaExe, args, gameDir)
    }

    private val placeholder = Regex("\\$\\{([^}]+)}")

    private fun expand(args: List<JsonElement>, features: Map<String, Boolean>): List<String> {
        val out = ArrayList<String>()
        for (a in args) {
            when (a) {
                is JsonNull -> {}
                is JsonPrimitive -> out += a.content
                is JsonObject -> {
                    val rules = a["rules"]?.let { RemoteJson.decodeFromJsonElement(ListSerializer(Rule.serializer()), it) }
                    if (!Rules.allowed(rules, features)) continue
                    when (val v = a["value"]) {
                        is JsonNull, null -> {}
                        is JsonPrimitive -> out += v.content
                        is JsonArray -> v.forEach { if (it is JsonPrimitive && it !is JsonNull) out += it.content }
                        else -> {}
                    }
                }
                else -> {}
            }
        }
        return out
    }

    /** Splits a command-line style string, honouring double quotes. */
    fun splitArgs(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuote = false
        var has = false
        for (c in s) {
            when {
                c == '"' -> { inQuote = !inQuote; has = true }
                c.isWhitespace() && !inQuote -> if (has || cur.isNotEmpty()) { out += cur.toString(); cur.clear(); has = false }
                else -> cur.append(c)
            }
        }
        if (has || cur.isNotEmpty()) out += cur.toString()
        return out
    }
}
