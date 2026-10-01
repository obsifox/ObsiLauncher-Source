package studio.obsifox.launcher.core.java

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.net.DownloadItem
import studio.obsifox.launcher.core.net.Downloader
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.OsName
import studio.obsifox.launcher.core.util.Platform
import studio.obsifox.launcher.core.util.ProgressSink
import studio.obsifox.launcher.core.util.ProgressUpdate
import studio.obsifox.launcher.core.util.RemoteJson
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

data class JavaInstall(val executable: Path, val major: Int, val vendorLine: String)

/**
 * Finds or downloads the Java runtime a Minecraft version needs.
 * Mojang publishes per-component runtimes (java-runtime-gamma = Java 17, delta = 21 ...) that the official launcher uses.
 */
class JavaRuntimeManager(
    private val http: Http,
    private val paths: LauncherPaths,
    private val downloader: Downloader,
) {
    private val indexUrl = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"

    /** Returns a java executable for [component] (e.g. "java-runtime-delta"), downloading it when necessary. */
    suspend fun ensureMojangRuntime(component: String, progress: ProgressSink): Path? {
        val key = Platform.mojangRuntimeKey ?: return null
        val home = paths.runtime.resolve(component).resolve(key)
        val exe = executableIn(home)
        val marker = home.resolve(".obsi-complete")
        if (Files.isRegularFile(exe) && Files.isRegularFile(marker)) return exe

        progress(ProgressUpdate("java"))
        val all = RemoteJson.parseToJsonElement(http.getString(indexUrl)).jsonObject
        val entry = all[key]?.jsonObject?.get(component)?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val manifestUrl = entry["manifest"]!!.jsonObject["url"]!!.jsonPrimitive.content
        val manifest = RemoteJson.parseToJsonElement(http.getString(manifestUrl)).jsonObject
        val files = manifest["files"]!!.jsonObject

        val items = ArrayList<DownloadItem>()
        val links = ArrayList<Pair<Path, String>>()
        withContext(Dispatchers.IO) {
            for ((rel, v) in files) {
                val o = v.jsonObject
                val target = home.resolve(rel).normalize()
                if (!target.startsWith(home)) continue
                when (o["type"]?.jsonPrimitive?.content) {
                    "directory" -> Files.createDirectories(target)
                    "file" -> {
                        val raw = o["downloads"]!!.jsonObject["raw"]!!.jsonObject
                        items += DownloadItem(
                            listOf(raw["url"]!!.jsonPrimitive.content), target,
                            raw["sha1"]?.jsonPrimitive?.content, raw["size"]?.jsonPrimitive?.content?.toLongOrNull(),
                            executable = o["executable"]?.jsonPrimitive?.booleanOrNull == true,
                        )
                    }
                    "link" -> links += target to o["target"]!!.jsonPrimitive.content
                }
            }
        }
        downloader.run(items, "java", progress)
        withContext(Dispatchers.IO) {
            if (Platform.os != OsName.WINDOWS) {
                for ((link, target) in links) {
                    try {
                        Files.createDirectories(link.parent)
                        if (!Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) Files.createSymbolicLink(link, Paths.get(target))
                    } catch (_: Exception) {
                    }
                }
            }
            Files.writeString(marker, "ok")
        }
        return exe.takeIf { Files.isRegularFile(it) }
    }

    /** Looks at JAVA_HOME and PATH for a Java >= [minMajor]. */
    suspend fun findSystemJava(minMajor: Int): JavaInstall? = withContext(Dispatchers.IO) {
        val candidates = LinkedHashSet<Path>()
        System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { candidates.add(executableIn(Paths.get(it))) }
        System.getProperty("java.home")?.let { candidates.add(executableIn(Paths.get(it))) }
        (System.getenv("PATH") ?: "").split(File.pathSeparator).filter { it.isNotBlank() }.forEach {
            candidates.add(Paths.get(it).resolve(if (Platform.os == OsName.WINDOWS) "javaw.exe" else "java"))
        }
        candidates.filter { Files.isRegularFile(it) }
            .mapNotNull { probe(it) }
            .filter { it.major >= minMajor }
            .minByOrNull { it.major }
    }

    /** Runs `java -version` and parses the major version. */
    fun probe(exe: Path): JavaInstall? = try {
        val javaForProbe = if (exe.fileName.toString().equals("javaw.exe", true)) exe.resolveSibling("java.exe").takeIf { Files.exists(it) } ?: exe else exe
        val p = ProcessBuilder(javaForProbe.toString(), "-version").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(15, TimeUnit.SECONDS)
        parseMajor(out)?.let { JavaInstall(exe, it, out.lineSequence().firstOrNull().orEmpty()) }
    } catch (_: Exception) {
        null
    }

    companion object {
        private val versionRegex = Regex("version \"([0-9]+)(?:\\.([0-9]+))?")

        fun parseMajor(text: String): Int? {
            val m = versionRegex.find(text) ?: return null
            val a = m.groupValues[1].toInt()
            return if (a == 1) m.groupValues[2].toIntOrNull() else a
        }

        fun executableIn(home: Path): Path {
            val bin = when (Platform.os) {
                OsName.MACOS -> if (Files.isDirectory(home.resolve("jre.bundle"))) home.resolve("jre.bundle/Contents/Home/bin") else home.resolve("bin")
                else -> home.resolve("bin")
            }
            return when (Platform.os) {
                OsName.WINDOWS -> bin.resolve("javaw.exe").takeIf { Files.exists(it) } ?: bin.resolve("java.exe")
                else -> bin.resolve("java")
            }
        }
    }

    /**
     * Resolves the Java to use for a version: explicit override > Mojang runtime > system Java.
     * [javaMajor]/[component] come from the version JSON (absent for very old versions, which want Java 8).
     */
    suspend fun resolveFor(
        override: String?,
        component: String?,
        javaMajor: Int?,
        progress: ProgressSink,
    ): Path {
        if (!override.isNullOrBlank()) {
            val p = Paths.get(override)
            if (!Files.isRegularFile(p)) throw LauncherException("Configured Java not found: $override")
            return p
        }
        val comp = component ?: "jre-legacy"
        val mojang = try {
            ensureMojangRuntime(comp, progress)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (mojang != null) return mojang
        val need = javaMajor ?: 8
        findSystemJava(need)?.let { return it.executable }
        throw LauncherException("No suitable Java ($need+) found and Mojang has no runtime for this platform. Install a JDK and set it in Settings.")
    }
}
