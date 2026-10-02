package studio.obsifox.obsilauncher.core.game

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.net.Http
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

sealed class InstallState {
    data object Idle : InstallState()
    data class Running(val step: String, val done: Int, val total: Int, val fraction: Float?) : InstallState()
    data class Done(val id: String) : InstallState()
    data class Failed(val message: String) : InstallState()
}

/**
 * Installs a Minecraft version into the games directory:
 * version json + client jar + libraries (+ native extraction) + assets.
 * Every download is sha1-verified when Mojang publishes a checksum.
 */
class VersionInstaller(private val context: Context) {

    val state = MutableStateFlow<InstallState>(InstallState.Idle)
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun isInstalled(id: String): Boolean = versionJson(context, id).isFile && clientJar(context, id).isFile

    fun installed(): List<String> =
        Paths.versionsRoot(context).listFiles { f -> f.isDirectory }?.map { it.name }?.sorted().orEmpty()

    suspend fun install(version: McVersion, settings: ObsiSettings) = withContext(Dispatchers.IO) {
        if (state.value is InstallState.Running) return@withContext
        cancelled = false
        try {
            run(version, settings)
            state.value = InstallState.Done(version.id)
        } catch (e: Exception) {
            state.value = InstallState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun run(version: McVersion, settings: ObsiSettings) {
        val dir = Paths.versionDir(context, version.id)
        dir.mkdirs()

        // 1) version json -------------------------------------------------------
        step("manifest", 0, 1, null)
        val jsonFile = versionJson(context, version.id)
        val text = Http.get(version.url) ?: throw IllegalStateException("no metadata for ${version.id}")
        jsonFile.writeText(text)
        val root = JSONObject(text)
        val downloads = root.optJSONObject("downloads")

        // 2) client jar ---------------------------------------------------------
        val client = downloads?.optJSONObject("client")
        if (client != null) {
            step("client", 0, 1, 0f)
            Http.downloadToFile(
                url = client.getString("url"),
                dest = clientJar(context, version.id),
                sha1 = client.optString("sha1").takeIf { it.length == 40 },
            ) { done, total -> step("client", 0, 1, if (total > 0) done.toFloat() / total else null) }
            checkCancelled()
        }

        // 3) libraries ----------------------------------------------------------
        val libs = root.optJSONArray("libraries") ?: JSONArray()
        val wanted = ArrayList<LibEntry>(libs.length())
        for (i in 0 until libs.length()) {
            val entry = libs.getJSONObject(i)
            if (!rulesAllow(entry.optJSONArray("rules"))) continue
            val downloadsObj = entry.optJSONObject("downloads") ?: continue
            val artifact = downloadsObj.optJSONObject("artifact") ?: continue
            wanted += LibEntry(
                coord = entry.getString("name"),
                url = artifact.optString("url"),
                path = artifact.optString("path").ifEmpty { mavenPath(entry.getString("name")) },
                sha1 = artifact.optString("sha1").takeIf { it.length == 40 },
                size = artifact.optLong("size", 0L),
            )
            val nativesLinux = downloadsObj.optJSONObject("natives-linux")
            if (nativesLinux != null) {
                wanted += LibEntry(
                    coord = entry.getString("name"),
                    url = nativesLinux.optString("url"),
                    path = nativesLinux.optString("path").ifEmpty {
                        mavenPath(entry.getString("name"), classifier = "natives-linux")
                    },
                    sha1 = nativesLinux.optString("sha1").takeIf { it.length == 40 },
                    size = nativesLinux.optLong("size", 0L),
                    isNatives = true,
                )
            }
        }
        val libRoot = Paths.librariesRoot(context)
        val jarLibs = ArrayList<File>(wanted.size)
        val nativesJars = ArrayList<File>()
        wanted.forEachIndexed { index, lib ->
            checkCancelled()
            val dest = File(libRoot, lib.path)
            if (!dest.isFile || dest.length() == 0L) {
                step("libraries", index, wanted.size, index.toFloat() / wanted.size)
                if (lib.url.isNotEmpty()) {
                    Http.downloadToFile(lib.url, dest, lib.sha1) { done, total ->
                        val base = index.toFloat() / wanted.size
                        val part = if (total > 0) (done.toFloat() / total) / wanted.size else 0f
                        step("libraries", index, wanted.size, base + part)
                    }
                } else if (lib.coord.isNotEmpty()) {
                    val repo = defaultRepoFor(lib.coord)
                    Http.downloadToFile("$repo/${lib.path}", dest, lib.sha1) { done, total ->
                        val base = index.toFloat() / wanted.size
                        val part = if (total > 0) (done.toFloat() / total) / wanted.size else 0f
                        step("libraries", index, wanted.size, base + part)
                    }
                }
            }
            if (lib.isNatives) nativesJars += dest else jarLibs += dest
        }

        // 4) extract natives ----------------------------------------------------
        step("natives", 0, 1, null)
        val nativesDir = File(dir, "natives")
        nativesDir.mkdirs()
        nativesJars.forEach { jar ->
            runCatching {
                ZipFile(jar).use { zip ->
                    for (entry in zip.entries()) {
                        if (entry.isDirectory) continue
                        val name = entry.name.substringAfterLast('/')
                        if (!name.endsWith(".so")) continue
                        if (name.startsWith("lib")) {
                            zip.getInputStream(entry).use { input ->
                                File(nativesDir, name).outputStream().use { input.copyTo(it) }
                            }
                        }
                    }
                }
            }
        }

        // 5) assets -------------------------------------------------------------
        val indexObj = root.optJSONObject("assetIndex")
        if (indexObj != null) {
            val indexId = indexObj.optString("id").ifEmpty { version.id }
            val indexFile = File(Paths.assetsRoot(context), "indexes/$indexId.json")
            val indexText = if (indexFile.isFile && indexFile.length() > 0) {
                indexFile.readText()
            } else {
                val t = Http.get(indexObj.getString("url"))
                    ?: throw IllegalStateException("no asset index for $indexId")
                indexFile.parentFile?.mkdirs()
                indexFile.writeText(t)
                t
            }
            val objects = JSONObject(indexText).optJSONObject("objects") ?: JSONObject()
            val keys = objects.keys().asSequence().toList()
            val objectsRoot = File(Paths.assetsRoot(context), "objects")
            keys.forEachIndexed { index, key ->
                checkCancelled()
                val obj = objects.getJSONObject(key)
                val hash = obj.getString("hash")
                val dest = File(objectsRoot, "${hash.substring(0, 2)}/$hash")
                if (!dest.isFile || dest.length() != obj.optLong("size", dest.length())) {
                    if (obj.optLong("size", 0L) == 0L && dest.isFile) return@forEachIndexed
                    step("assets", index, keys.size, index.toFloat() / keys.size)
                    Http.downloadToFile("$RESOURCES/${hash.substring(0, 2)}/$hash", dest, hash)
                }
            }
        }

        // 6) mark selected if nothing chosen yet ----------------------------------
        if (settings.selectedVersionValue.isBlank()) settings.selectedVersionValue = version.id
    }

    private fun step(step: String, done: Int, total: Int, fraction: Float?) {
        state.value = InstallState.Running(step, done, total, fraction)
    }

    private fun checkCancelled() {
        if (cancelled) throw InterruptedException("cancelled")
    }

    data class LibEntry(
        val coord: String,
        val url: String,
        val path: String,
        val sha1: String?,
        val size: Long,
        val isNatives: Boolean = false,
    )

    companion object {
        private const val RESOURCES = "https://resources.download.minecraft.net"
        private const val MOJANG_LIBS = "https://libraries.minecraft.net"

        fun versionJson(context: Context, id: String): File =
            File(Paths.versionDir(context, id), "$id.json")

        fun clientJar(context: Context, id: String): File =
            File(Paths.versionDir(context, id), "$id.jar")

        fun nativesDir(context: Context, id: String): File =
            File(Paths.versionDir(context, id), "natives")

        /** org.lwjgl:lwjgl:3.3.3 -> org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar */
        fun mavenPath(coord: String, classifier: String? = null): String {
            val parts = coord.split(":")
            require(parts.size >= 3) { "bad maven coordinate: $coord" }
            val (group, artifact, version) = parts
            val extra = parts.getOrNull(3)
            val file = buildString {
                append(artifact)
                append('-').append(version)
                if (extra != null && extra.isNotEmpty()) append('-').append(extra)
                if (classifier != null) append('-').append(classifier)
                append(".jar")
            }
            return "${group.replace('.', '/')}/$artifact/$version/$file"
        }

        /** Libraries Mojang does not host land on the Bukkit-style maven repos. */
        fun defaultRepoFor(coord: String): String = when {
            coord.startsWith("com.mojang") || coord.startsWith("net.minecraft") -> MOJANG_LIBS
            else -> MOJANG_LIBS
        }

        /**
         * Standard Minecraft rules evaluation. On Android we present ourselves as
         * linux; rules that require osx/windows exclude the library.
         */
        fun rulesAllow(rules: JSONArray?): Boolean {
            if (rules == null || rules.length() == 0) return true
            var allowed = true
            for (i in 0 until rules.length()) {
                val rule = rules.getJSONObject(i)
                val action = rule.optString("action")
                val os = rule.optJSONObject("os")?.optString("name")
                val matches = when (os) {
                    null, "", "linux" -> true
                    else -> false
                }
                val features = rule.optJSONObject("features")
                val featureMatch = features == null || features.length() == 0
                if (matches && featureMatch) {
                    when (action) {
                        "allow" -> allowed = true
                        "disallow" -> allowed = false
                    }
                }
            }
            return allowed
        }
    }
}

fun String.toVersionLocale(): String = lowercase(Locale.ROOT)
