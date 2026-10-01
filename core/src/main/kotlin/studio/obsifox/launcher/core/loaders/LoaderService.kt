package studio.obsifox.launcher.core.loaders

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.java.JavaRuntimeManager
import studio.obsifox.launcher.core.mojang.GameInstaller
import studio.obsifox.launcher.core.mojang.MojangApi
import studio.obsifox.launcher.core.net.DownloadItem
import studio.obsifox.launcher.core.net.Downloader
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.ProgressSink
import studio.obsifox.launcher.core.util.ProgressUpdate
import studio.obsifox.launcher.core.util.RemoteJson
import studio.obsifox.launcher.core.util.writeTextAtomic
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class LoaderVersion(val version: String, val stable: Boolean, val recommended: Boolean = false)

/** Result of an installation: the directory name under shared/versions and the loader version that was picked. */
data class InstalledLoader(val versionId: String, val loaderVersion: String?)

/**
 * Installs mod loaders into the shared `versions/` directory.
 *  - Fabric / Quilt: their meta servers serve a ready-made version JSON.
 *  - Forge / NeoForge: the official installer jar is run headless (`--installClient`).
 */
class LoaderService(
    private val http: Http,
    private val paths: LauncherPaths,
    private val mojang: MojangApi,
    private val java: JavaRuntimeManager,
    private val installer: GameInstaller,
    private val downloader: Downloader,
) {
    private val cache = ConcurrentHashMap<String, List<LoaderVersion>>()

    suspend fun versions(type: LoaderType, mc: String): List<LoaderVersion> {
        if (type == LoaderType.VANILLA) return emptyList()
        val key = "${type.name}:$mc"
        cache[key]?.let { return it }
        val list = when (type) {
            LoaderType.FABRIC -> metaVersions("https://meta.fabricmc.net/v2/versions/loader/${Http.enc(mc)}")
            LoaderType.QUILT -> metaVersions("https://meta.quiltmc.org/v3/versions/loader/${Http.enc(mc)}")
            LoaderType.FORGE -> forgeVersions(mc)
            LoaderType.NEOFORGE -> neoForgeVersions(mc)
            LoaderType.VANILLA -> emptyList()
        }
        cache[key] = list
        return list
    }

    private suspend fun metaVersions(url: String): List<LoaderVersion> {
        val arr = RemoteJson.parseToJsonElement(http.getString(url, mirrors = true)).jsonArray
        return arr.mapNotNull { e ->
            val l = e.jsonObject["loader"]?.jsonObject ?: return@mapNotNull null
            val v = l["version"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val stable = l["stable"]?.jsonPrimitive?.booleanOrNull ?: !Regex("(?i)(beta|alpha|rc|pre|snapshot)").containsMatchIn(v)
            LoaderVersion(v, stable)
        }
    }

    private suspend fun forgeVersions(mc: String): List<LoaderVersion> {
        val xml = http.getString("$FORGE_MAVEN/net/minecraftforge/forge/maven-metadata.xml")
        val rec = try {
            RemoteJson.parseToJsonElement(http.getString("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"))
                .jsonObject["promos"]?.jsonObject?.get("$mc-recommended")?.jsonPrimitive?.contentOrNull
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return versionTags(xml).filter { it.startsWith("$mc-") }.map { full ->
            // Old releases look like "1.7.10-10.13.4.1614-1.7.10"
            full.removePrefix("$mc-").removeSuffix("-$mc")
        }.distinct().reversed().map { v -> LoaderVersion(v, stable = true, recommended = v == rec) }
    }

    private suspend fun neoForgeVersions(mc: String): List<LoaderVersion> {
        val prefix = neoForgePrefix(mc) ?: return emptyList()
        val xml = http.getString("$NEOFORGE_MAVEN/net/neoforged/neoforge/maven-metadata.xml")
        val list = versionTags(xml).filter { it.startsWith(prefix) }.reversed()
        val firstStable = list.firstOrNull { !it.contains("-") }
        return list.map { LoaderVersion(it, stable = !it.contains("-"), recommended = it == firstStable) }
    }

    // --------------------------------------------------------------------------------------------- install

    /** Installs [type] for [mc] and returns the id of the version to launch (a directory name in shared/versions). */
    suspend fun install(type: LoaderType, mc: String, loaderVersion: String?, progress: ProgressSink): InstalledLoader {
        if (type == LoaderType.VANILLA) {
            installer.install(mc, progress)
            return InstalledLoader(mc, null)
        }
        val lv = loaderVersion ?: versions(type, mc).let { list -> (list.firstOrNull { it.recommended } ?: list.firstOrNull { it.stable } ?: list.firstOrNull())?.version }
            ?: throw LauncherException("${type.display} is not available for Minecraft $mc")
        val id = when (type) {
            LoaderType.FABRIC -> installMeta("https://meta.fabricmc.net/v2/versions/loader/${Http.enc(mc)}/${Http.enc(lv)}/profile/json", mc, progress)
            LoaderType.QUILT -> installMeta("https://meta.quiltmc.org/v3/versions/loader/${Http.enc(mc)}/${Http.enc(lv)}/profile/json", mc, progress)
            LoaderType.FORGE -> installWithInstaller(
                type, mc, lv,
                url = "$FORGE_MAVEN/net/minecraftforge/forge/${forgeFull(mc, lv)}/forge-${forgeFull(mc, lv)}-installer.jar",
                progress = progress,
            )
            LoaderType.NEOFORGE -> installWithInstaller(
                type, mc, lv,
                url = "$NEOFORGE_MAVEN/net/neoforged/neoforge/$lv/neoforge-$lv-installer.jar",
                progress = progress,
            )
            LoaderType.VANILLA -> mc
        }
        return InstalledLoader(id, lv)
    }

    private suspend fun installMeta(profileUrl: String, mc: String, progress: ProgressSink): String {
        progress(ProgressUpdate("Fetching loader profile"))
        val text = http.getString(profileUrl)
        val id = RemoteJson.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.contentOrNull
            ?: throw LauncherException("Loader profile has no id")
        writeTextAtomic(paths.versionJson(id), text)
        // make sure vanilla (inheritsFrom) + loader libraries + assets are all present
        installer.install(id, progress)
        return id
    }

    private suspend fun installWithInstaller(type: LoaderType, mc: String, lv: String, url: String, progress: ProgressSink): String {
        val expected = if (type == LoaderType.FORGE) "$mc-forge-$lv" else "neoforge-$lv"
        if (Files.isRegularFile(paths.versionJson(expected))) {
            installer.install(expected, progress)
            return expected
        }

        // 1. vanilla must exist (the installer patches the client jar)
        val vanilla = installer.install(mc, progress)

        // 2. installer jar
        val jar = paths.cache.resolve("installers").resolve(url.substringAfterLast('/'))
        downloader.run(listOf(DownloadItem(listOf(url), jar)), "loader-installer", progress)

        // 3. a stub launcher profile file is required by the installer
        val profiles = paths.shared.resolve("launcher_profiles.json")
        if (!Files.exists(profiles)) writeTextAtomic(profiles, """{"profiles":{},"settings":{},"version":3}""")

        // 4. run it headless with the Java version that Minecraft itself needs
        val javaExe = java.resolveFor(null, vanilla.javaComponent, vanilla.javaMajor, progress)
        val before = listVersionDirs()
        var result = runInstaller(javaExe, jar, "--installClient", progress, type)
        if (result.exit != 0 && result.output.contains("--install-client", ignoreCase = true).not() && result.output.contains("Unknown option", true)) {
            result = runInstaller(javaExe, jar, "--install-client", progress, type)
        }
        if (result.exit != 0) {
            throw LauncherException("${type.display} installer failed (exit ${result.exit}):\n" + result.output.lines().takeLast(25).joinToString("\n"))
        }

        // 5. which version did it create?
        val created = (listVersionDirs() - before).filter { it != mc && Files.isRegularFile(paths.versionJson(it)) }
        val id = when {
            Files.isRegularFile(paths.versionJson(expected)) -> expected
            created.size == 1 -> created.first()
            else -> created.firstOrNull { it.contains(type.name, ignoreCase = true) }
                ?: throw LauncherException("${type.display} installer finished but no new version was found")
        }
        installer.install(id, progress)
        return id
    }

    private data class RunResult(val exit: Int, val output: String)

    private suspend fun runInstaller(javaExe: Path, jar: Path, flag: String, progress: ProgressSink, type: LoaderType): RunResult =
        withContext(Dispatchers.IO) {
            val javaForInstaller = if (javaExe.fileName.toString().equals("javaw.exe", true)) javaExe.resolveSibling("java.exe").takeIf { Files.exists(it) } ?: javaExe else javaExe
            val pb = ProcessBuilder(javaForInstaller.toString(), "-Djava.awt.headless=true", "-jar", jar.toString(), flag, paths.shared.toString())
                .directory(paths.shared.toFile())
                .redirectErrorStream(true)
            val p = pb.start()
            val log = StringBuilder()
            val reader = Thread {
                p.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(log) { log.appendLine(line) }
                    val short = line.trim().takeIf { it.isNotEmpty() }?.take(90)
                    if (short != null) progress(ProgressUpdate("${type.display}: $short"))
                }
            }.apply { isDaemon = true; start() }
            try {
                while (!p.waitFor(500, TimeUnit.MILLISECONDS)) currentCoroutineContext().ensureActive()
                reader.join(2000)
            } catch (e: CancellationException) {
                p.destroyForcibly()
                throw e
            }
            RunResult(p.exitValue(), synchronized(log) { log.toString() })
        }

    private fun listVersionDirs(): Set<String> =
        if (!Files.isDirectory(paths.versions)) emptySet()
        else Files.list(paths.versions).use { s -> s.filter { Files.isDirectory(it) }.map { it.fileName.toString() }.toList().toSet() }

    companion object {
        const val FORGE_MAVEN = "https://maven.minecraftforge.net"
        const val NEOFORGE_MAVEN = "https://maven.neoforged.net/releases"

        private val versionTag = Regex("<version>([^<]+)</version>")
        fun versionTags(xml: String): List<String> = versionTag.findAll(xml).map { it.groupValues[1].trim() }.toList()

        /** Forge versions of Minecraft 1.7.10 and earlier carry the MC version twice in the artifact version. */
        fun forgeFull(mc: String, lv: String): String =
            if (mc == "1.7.10" || mc.startsWith("1.6") || mc.startsWith("1.5") || mc.startsWith("1.4") || mc.startsWith("1.3")) "$mc-$lv-$mc" else "$mc-$lv"

        /** NeoForge versions are "<mc minor>.<mc patch>.<build>", e.g. MC 1.21.1 -> "21.1.". */
        fun neoForgePrefix(mc: String): String? {
            val parts = mc.split('.')
            return when {
                parts.size >= 2 && parts[0] == "1" -> {
                    val minor = parts[1].toIntOrNull() ?: return null
                    if (minor < 20) return null
                    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    if (minor == 20 && patch < 2) return null
                    "$minor.$patch."
                }
                parts.isNotEmpty() && parts[0].toIntOrNull() != null && parts[0].toInt() >= 26 -> "${parts.take(2).joinToString(".")}."
                else -> null
            }
        }
    }
}
