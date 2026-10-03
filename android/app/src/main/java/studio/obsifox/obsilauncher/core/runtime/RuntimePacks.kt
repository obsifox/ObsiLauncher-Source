package studio.obsifox.obsilauncher.core.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.net.Http
import java.io.File
import java.io.FileInputStream

/** An installed runtime pack: a JVM for Android + native glue, described by pack.json. */
data class Pack(
    val name: String,
    val abi: String,
    val dir: File,
    /** shared object passed to /system/bin/linker64 as the executable (PoJav-style JVM launcher) */
    val launcherSo: File,
    val jvmArgs: List<String>,
    val libDirs: List<File>,
    /** v1.12.0 — deep check done at rescan: the JVM shared object is really there. */
    val complete: Boolean = false,
) {
    fun isValid(): Boolean = launcherSo.isFile && libDirs.all { it.isDirectory }

    /**
     * v1.12.0 — a pack that LOOKS installed but lost its libjvm.so (interrupted
     * download, cleaner app, partial unpack) makes the game die the moment PLAY
     * is pressed. The launch path must treat such a pack as absent and download
     * it again — so validity for launching is [complete], not just [isValid].
     */
    fun isComplete(): Boolean = complete && isValid()

    companion object {
        /** the one file the JVM cannot boot without — searched under the lib dirs */
        const val JVM_SO = "libjvm.so"

        /** bounded walk so a huge pack can never stall the caller. */
        fun findJvmSo(dirs: List<File>): Boolean {
            for (dir in dirs) {
                if (!dir.isDirectory) continue
                var seen = 0
                var found = false
                dir.walkTopDown().forEach { f ->
                    if (seen++ > 8000) return@forEach
                    if (!found && f.isFile && f.name == JVM_SO && f.length() > 0) found = true
                }
                if (found) return true
            }
            return false
        }
    }
}

class RuntimePacks(private val context: Context) {

    val packs = MutableStateFlow<List<Pack>>(emptyList())
    val installing = MutableStateFlow(false)
    val progress = MutableStateFlow<Float?>(null)
    val message = MutableStateFlow<String?>(null)

    init {
        rescan()
    }

    fun rescan() {
        val root = Paths.runtimeRoot(context)
        root.mkdirs()
        val list = root.listFiles { f -> f.isDirectory }?.mapNotNull { dir ->
            runCatching {
                val meta = File(dir, "pack.json")
                if (!meta.isFile) return@runCatching null
                val json = JSONObject(meta.readText())
                val launcherSo = File(dir, json.optString("launcher_so", "lib/launcher.so"))
                val libDirs = json.optJSONArray("lib_dirs")?.let { arr -> (0 until arr.length()).map { File(dir, arr.getString(it)) } }
                    ?: listOf(File(dir, "lib"))
                Pack(
                    name = json.optString("name", dir.name),
                    abi = json.optString("abi", "arm64-v8a"),
                    dir = dir,
                    launcherSo = launcherSo,
                    jvmArgs = json.optJSONArray("jvm_args")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty(),
                    libDirs = libDirs,
                    complete = launcherSo.isFile && launcherSo.length() > 0 &&
                        libDirs.all { it.isDirectory } && Pack.findJvmSo(libDirs),
                )
            }.getOrNull()
        }.orEmpty()
        packs.value = list
    }

    fun packByName(name: String): Pack? = packs.value.firstOrNull { it.name == name }

    /** Download a .tar.xz pack, unpack it and validate pack.json. */
    suspend fun install(url: String, suggestedName: String) = withContext(Dispatchers.IO) {
        if (installing.value) return@withContext
        installing.value = true
        message.value = null
        progress.value = null
        try {
            val staging = File(Paths.runtimeRoot(context), ".staging-$suggestedName")
            staging.deleteRecursively()
            staging.mkdirs()
            val archive = File(staging, "pack.tar.xz")
            Http.downloadToFile(url, archive) { done, total ->
                progress.value = if (total > 0) done.toFloat() / total else null
            }
            progress.value = null
            message.value = null

            val outDir = File(Paths.runtimeRoot(context), suggestedName)
            outDir.deleteRecursively()
            outDir.mkdirs()
            TarArchiveInputStream(XZInputStream(FileInputStream(archive))).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val target = File(outDir, entry.name).canonicalFile
                    // zip-slip protection
                    if (!target.path.startsWith(outDir.canonicalPath)) continue
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { tar.copyTo(it) }
                        target.setExecutable(true, true)
                    }
                }
            }
            archive.delete()

            // if the archive wrapped everything in one directory, unwrap it
            val metaLocation = sequenceOf(
                File(outDir, "pack.json"),
                File(File(outDir, outDir.list()?.firstOrNull() ?: ""), "pack.json"),
            ).firstOrNull { it.isFile }
            val meta = metaLocation ?: throw IllegalStateException("pack.json not found in archive")
            if (meta.parentFile != outDir) {
                val inner = meta.parentFile!!
                val moved = File(outDir.parentFile, ".unwrap")
                inner.renameTo(moved)
                outDir.deleteRecursively()
                moved.renameTo(outDir)
            }

            rescan()
            val pack = packByName(suggestedName) ?: packByName(
                runCatching { JSONObject(File(outDir, "pack.json").readText()).optString("name", suggestedName) }.getOrDefault(suggestedName)
            )
            if (pack == null || !pack.isComplete()) {
                throw IllegalStateException("pack is missing its launcher .so or lib dirs")
            }
            message.value = null
        } catch (e: Exception) {
            message.value = e.message
            throw e
        } finally {
            installing.value = false
        }
    }

    /**
     * v1.9.0 — auto-provisioning used by the update gate: downloads ANY
     * open-source Android JRE build (tar.xz / tar.gz), unpacks it and
     * synthesizes the pack.json for it (finds libjvm.so, the lib dirs and
     * wires the MobileGlues renderer flag) so the user never has to
     * hand-build or hand-download a runtime pack.
     */
    suspend fun installAuto(url: String, suggestedName: String) = withContext(Dispatchers.IO) {
        // a raw JRE unpacks with no pack.json — remember that before install()
        val staging = File(Paths.runtimeRoot(context), ".staging-$suggestedName")
        staging.deleteRecursively()
        staging.mkdirs()
        val lower = url.substringBefore('?').lowercase()
        val archive = File(staging, if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) "pack.tar.gz" else "pack.tar.xz")
        Http.downloadToFile(url, archive) { done, total ->
            progress.value = if (total > 0) done.toFloat() / total else null
        }
        progress.value = null

        val work = File(staging, "extract")
        work.mkdirs()
        if (archive.name.endsWith(".tar.gz")) {
            java.util.zip.GZIPInputStream(FileInputStream(archive)).use { gz ->
                TarArchiveInputStream(gz).use { tar -> untarInto(tar, work) }
            }
        } else {
            TarArchiveInputStream(XZInputStream(FileInputStream(archive))).use { tar ->
                untarInto(tar, work)
            }
        }
        archive.delete()

        // unwrap single top directory
        val entries = work.listFiles()?.toList().orEmpty()
        val root = if (entries.size == 1 && entries[0].isDirectory) entries[0] else work

        // locate the JVM shared object — the launcher entry point
        val jvmSo = sequenceOf(
            File(root, "lib/server/libjvm.so"),
            File(root, "lib/minimal/libjvm.so"),
            File(root, "lib/client/libjvm.so"),
        ).firstOrNull { it.isFile }
            ?: root.walkTopDown().filter { it.isFile && it.name == "libjvm.so" }.firstOrNull()
            ?: throw IllegalStateException("no libjvm.so in the downloaded runtime")

        // every directory that carries native libraries feeds LD_LIBRARY_PATH
        val libDirs = root.walkTopDown()
            .filter { it.isFile && it.extension == "so" }
            .map { it.parentFile }
            .distinct()
            .take(8)
            .toList()
            .ifEmpty { listOf(File(root, "lib")) }

        val packJson = JSONObject().apply {
            put("name", suggestedName)
            put("abi", "arm64-v8a")
            put("launcher_so", jvmSo.relativeTo(root).path)
            put(
                "jvm_args",
                org.json.JSONArray()
                    .put("-Dorg.lwjgl.opengl.libname=libMobileGlues.so"),
            )
            put("lib_dirs", org.json.JSONArray().apply { libDirs.forEach { put(it.relativeTo(root).path) } })
        }

        // move the runtime into place + write the generated manifest
        val outDir = File(Paths.runtimeRoot(context), suggestedName)
        outDir.deleteRecursively()
        root.renameTo(outDir)
        File(outDir, "pack.json").writeText(packJson.toString())
        staging.deleteRecursively()
        rescan()
        val pack = packByName(suggestedName)
        if (pack == null || !pack.isComplete()) {
            throw IllegalStateException("runtime pack failed validation")
        }
    }

    private fun untarInto(tar: TarArchiveInputStream, into: File) {
        while (true) {
            val entry = tar.nextTarEntry ?: break
            val target = File(into, entry.name).canonicalFile
            // zip-slip protection
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

    fun remove(name: String) {
        packByName(name)?.dir?.deleteRecursively()
        rescan()
    }

    companion object {
        /** Derive a filesystem-friendly pack name from a URL. */
        fun nameFor(url: String): String {
            val base = url.substringBefore('?').substringAfterLast('/').ifEmpty { "pack" }
            return base.removeSuffix(".tar.xz").removeSuffix(".tar").replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(48).ifEmpty { "pack" }
        }
    }
}
