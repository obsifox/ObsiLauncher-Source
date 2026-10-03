package studio.obsifox.obsilauncher.core.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.net.Http
import java.io.File
import java.io.FileInputStream

/**
 * Launcher components — the same model as ZalithLauncher/PojavLauncher:
 *
 *   other_login     -> authlib-injector
 *   caciocavallo    -> portable AWT backend (Java 8)
 *   caciocavallo17  -> portable AWT backend (Java 17+)
 *   lwjgl3          -> our Android LWJGL 3 fork classes (GLFW replacement)
 *   components      -> Launcher Components (Mio lib patcher, log4j hardening…)
 *   jre-8/17/21/25  -> the "Internal-8/17/21/25" Java runtimes
 *
 * jre-21 + the small jars ship INSIDE the APK (zero-touch first launch);
 * the other JREs download from the catalog when a game needs them, and every
 * entry is visible and repairable in Settings → Components.
 */
object ObsiComponents {

    data class Component(
        val id: String,
        val displayName: String,
        val bundled: Boolean,
        val isRuntime: Boolean,
        val javaVersion: Int = 0,
    )

    data class Status(
        val component: Component,
        val installed: Boolean,
        val busy: Boolean = false,
        val progress: Float? = null,
    )

    const val LWJGL3 = "lwjgl3"
    const val COMPONENTS = "components"
    const val CACIO = "caciocavallo"
    const val CACIO17 = "caciocavallo17"
    const val OTHER_LOGIN = "other_login"

    /** component -> asset dir name inside the APK */
    private val BUNDLED = listOf(
        Component(LWJGL3, "LWJGL 3.3.6 · Android fork", bundled = true, isRuntime = false),
        Component(COMPONENTS, "Launcher Components", bundled = true, isRuntime = false),
        Component(CACIO, "caciocavallo", bundled = true, isRuntime = false),
        Component(CACIO17, "caciocavallo 17", bundled = true, isRuntime = false),
        Component(OTHER_LOGIN, "authlib-injector", bundled = true, isRuntime = false),
        Component("jre-21", "Internal-21", bundled = true, isRuntime = true, javaVersion = 21),
    )

    /** downloadable-only runtimes (the Internal-* family) */
    val DOWNLOADABLE_RUNTIMES = listOf(
        Component("jre-8", "Internal-8", bundled = false, isRuntime = true, javaVersion = 8),
        Component("jre-17", "Internal-17", bundled = false, isRuntime = true, javaVersion = 17),
        Component("jre-25", "Internal-25", bundled = false, isRuntime = true, javaVersion = 25),
    )

    /** JNA ships as libjnidispatch.so in the APK + the jna jar from game libraries */
    val JNA = Component("jna", "JNA", bundled = true, isRuntime = false)

    val ALL: List<Component> = BUNDLED + listOf(JNA) + DOWNLOADABLE_RUNTIMES

    /** raw.githubusercontent source of the Zalith component store (GPL). */
    private const val ZALITH_RAW =
        "https://raw.githubusercontent.com/ZalithLauncher/ZalithLauncher/main/ZalithLauncher/src/main/assets/components"

    val installing = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow<Float?>(null)
    val message = MutableStateFlow<String?>(null)

    fun installedComponents(context: Context): List<String> =
        Paths.componentsRoot(context).listFiles { f -> f.isDirectory }?.map { it.name }.orEmpty()

    fun isComponentInstalled(context: Context, id: String): Boolean =
        File(Paths.componentDir(context, id), ".done").isFile

    fun isRuntimeInstalled(context: Context, id: String): Boolean {
        val dir = File(Paths.runtimeRoot(context), "Internal-" + id.removePrefix("jre-"))
        return File(dir, "lib/libjli.so").isFile && File(dir, "lib/server/libjvm.so").isFile
    }

    /**
     * Extract the bundled Internal-21 JRE (universal + bin-arm64 tar.xz shipped
     * in the APK) into the runtime home. Zero-touch: called on shell start.
     */
    fun ensureBundledRuntime(context: Context) {
        val outDir = runtimeHome(context, "Internal-21")
        if (File(outDir, "lib/libjli.so").isFile && File(outDir, "lib/server/libjvm.so").isFile) return
        runCatching {
            outDir.deleteRecursively()
            outDir.mkdirs()
            for (part in listOf("universal.tar.xz", "bin-arm64.tar.xz")) {
                context.assets.open("components/jre-21/$part").use { input ->
                    TarArchiveInputStream(XZInputStream(input)).use { tar ->
                        untarInto(tar, outDir)
                    }
                }
            }
        }
    }

    /** The installed runtime dir name ("Internal-21" …) best matching [javaVersion]. */
    fun installedRuntimeName(context: Context, javaVersion: Int): String? {
        val candidates = listOf(javaVersion, 21, 17, 25, 8).distinct()
        return candidates.firstOrNull { v ->
            isRuntimeInstalled(context, "jre-$v")
        }?.let { "Internal-$it" }
    }

    fun isRuntimeInstalledByName(context: Context, name: String): Boolean =
        File(runtimeHome(context, name), "lib/server/libjvm.so").isFile

    fun runtimeHome(context: Context, name: String): File = File(Paths.runtimeRoot(context), name)

    /**
     * Unpack every bundled component from the APK. Called on every shell
     * start — the .done marker carries the asset "version" so upgrades of
     * the app re-unpack newer component files.
     */
    fun ensureBundled(context: Context) {
        for (c in BUNDLED) {
            runCatching { unpackBundled(context, c.id) }
        }
    }

    private fun assetVersion(context: Context, id: String): String =
        runCatching {
            context.assets.open("components/$id/version").bufferedReader().use { it.readText().trim() }
        }.getOrDefault("1")

    fun unpackBundled(context: Context, id: String) {
        val dir = Paths.componentDir(context, id)
        val marker = File(dir, ".done")
        val want = assetVersion(context, id)
        if (marker.isFile && marker.readText().trim() == want) return
        if (id.startsWith("jre-")) return // runtimes install into runtime/, not components/
        dir.deleteRecursively()
        dir.mkdirs()
        val entries = context.assets.list("components/$id").orEmpty()
        for (e in entries) {
            if (e == "version") continue
            val out = File(dir, e)
            context.assets.open("components/$id/$e").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }
        marker.writeText(want)
    }

    /** Download a non-bundled runtime (Internal-8/17/25) and unpack it. */
    suspend fun downloadRuntime(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val c = DOWNLOADABLE_RUNTIMES.firstOrNull { it.id == id } ?: return@withContext false
        if (isRuntimeInstalled(context, id)) return@withContext true
        installing.value = c.displayName
        message.value = null
        progress.value = null
        try {
            val outDir = runtimeHome(context, "Internal-" + id.removePrefix("jre-"))
            outDir.mkdirs()
            val parts = listOf("universal.tar.xz", "bin-arm64.tar.xz")
            for (part in parts) {
                val url = "$ZALITH_RAW/$id/$part"
                val staging = File(context.cacheDir, "dl-$id-$part")
                Http.downloadToFile(url, staging) { done, total ->
                    progress.value = if (total > 0) done.toFloat() / total else null
                }
                progress.value = null
                TarArchiveInputStream(XZInputStream(FileInputStream(staging))).use { tar ->
                    untarInto(tar, outDir)
                }
                staging.delete()
            }
            if (!isRuntimeInstalled(context, id)) {
                throw IllegalStateException("runtime did not unpack completely")
            }
            message.value = null
            true
        } catch (e: Exception) {
            message.value = e.message
            false
        } finally {
            installing.value = null
            progress.value = null
        }
    }

    fun remove(context: Context, id: String) {
        if (id.startsWith("jre-")) {
            runtimeHome(context, "Internal-" + id.removePrefix("jre-")).deleteRecursively()
        } else {
            Paths.componentDir(context, id).deleteRecursively()
        }
    }

    private fun untarInto(tar: TarArchiveInputStream, into: File) {
        while (true) {
            val entry = tar.nextTarEntry ?: break
            val target = File(into, entry.name).canonicalFile
            if (!target.path.startsWith(into.canonicalPath)) continue
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.outputStream().use { tar.copyTo(it) }
                target.setExecutable(true, true)
            }
        }
    }

    /** Status snapshot for the Settings → Components screen. */
    fun statuses(context: Context): List<Status> = ALL.map { c ->
        Status(
            c,
            installed = when (c.id) {
                // JNA lives in the APK's native libs, not in components/
                JNA.id -> File(context.applicationInfo.nativeLibraryDir, "libjnidispatch.so").isFile
                else -> if (c.isRuntime) isRuntimeInstalled(context, c.id) else isComponentInstalled(context, c.id)
            },
            busy = installing.value == c.displayName,
            progress = progress.value.takeIf { installing.value == c.displayName },
        )
    }
}
