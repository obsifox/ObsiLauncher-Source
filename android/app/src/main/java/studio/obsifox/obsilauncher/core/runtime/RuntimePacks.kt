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
) {
    fun isValid(): Boolean = launcherSo.isFile && libDirs.all { it.isDirectory }
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
                Pack(
                    name = json.optString("name", dir.name),
                    abi = json.optString("abi", "arm64-v8a"),
                    dir = dir,
                    launcherSo = File(dir, json.optString("launcher_so", "lib/launcher.so")),
                    jvmArgs = json.optJSONArray("jvm_args")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty(),
                    libDirs = json.optJSONArray("lib_dirs")?.let { arr -> (0 until arr.length()).map { File(dir, arr.getString(it)) } }
                        ?: listOf(File(dir, "lib")),
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
            if (pack == null || !pack.isValid()) {
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
