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
 *
 * [installLoaded] completes libraries/assets for a version whose JSON was
 * already written by a loader (Fabric / Quilt profile, Forge installer, OptiFine).
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

    suspend fun install(version: McVersion, settings: ObsiSettings, progress: (InstallState) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            if (state.value is InstallState.Running) return@withContext
            cancelled = false
            try {
                val dir = Paths.versionDir(context, version.id)
                dir.mkdirs()
                emit("manifest", 0, 1, null, progress)
                val text = Http.get(version.url) ?: throw IllegalStateException("no metadata for ${version.id}")
                versionJson(context, version.id).writeText(text)
                installAndVerify(version.id, JSONObject(text), settings, progress)
                state.value = InstallState.Done(version.id)
            } catch (e: Exception) {
                state.value = InstallState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

    /** Libraries + natives + assets for a version whose json already exists (loader profiles). */
    suspend fun installLoaded(id: String, settings: ObsiSettings, progress: (InstallState) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            cancelled = false
            try {
                val jsonFile = versionJson(context, id)
                if (!jsonFile.isFile) throw IllegalStateException("version json missing for $id")
                installAndVerify(id, JSONObject(jsonFile.readText()), settings, progress)
                state.value = InstallState.Done(id)
            } catch (e: Exception) {
                state.value = InstallState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

    /**
     * "Fix & repair": verify the client jar, every library and every asset
     * against their published sha1/size and delete anything missing or corrupt,
     * then re-run the install so the gaps are re-downloaded.
     */
    suspend fun repair(id: String, settings: ObsiSettings, progress: (InstallState) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            cancelled = false
            try {
                val jsonFile = versionJson(context, id)
                if (!jsonFile.isFile) throw IllegalStateException("version json missing for $id")
                val root = JSONObject(jsonFile.readText())

                emit("verify", 0, 1, null, progress)
                // client jar (and the inherited vanilla jar for loader profiles)
                val chain = mutableListOf(id)
                root.optString("inheritsFrom").takeIf { it.isNotEmpty() && it != id }?.let { chain.add(it) }
                for (vid in chain) {
                    val vRoot = if (vid == id) root else runCatching { JSONObject(versionJson(context, vid).readText()) }.getOrNull() ?: continue
                    vRoot.optJSONObject("downloads")?.optJSONObject("client")?.let { client ->
                        val jar = clientJar(context, vid)
                        val sha1 = client.optString("sha1").takeIf { it.length == 40 }
                        if (jar.isFile && sha1 != null && Http.sha1Of(jar) != sha1) {
                            emit("repair_client", 0, 1, null, progress)
                            jar.delete()
                        } else if (!jar.isFile) {
                            emit("repair_client", 0, 1, null, progress)
                        }
                    }
                }
                // libraries
                val libRoot = Paths.librariesRoot(context)
                for (lib in collectLibraries(root, id, progress)) {
                    if (lib.url.startsWith("file://")) continue
                    val dest = File(libRoot, lib.path)
                    if (!dest.isFile) continue
                    if (lib.sha1 != null && Http.sha1Of(dest) != lib.sha1) {
                        emit("repair_libraries", 0, 1, null, progress)
                        dest.delete()
                    }
                }
                // assets
                val indexObj = root.optJSONObject("assetIndex")
                if (indexObj != null) {
                    val indexId = indexObj.optString("id").ifEmpty { id }
                    val indexFile = File(Paths.assetsRoot(context), "indexes/$indexId.json")
                    if (indexFile.isFile) {
                        val objects = runCatching { JSONObject(indexFile.readText()).optJSONObject("objects") }.getOrNull()
                        if (objects != null) {
                            val objectsRoot = File(Paths.assetsRoot(context), "objects")
                            for (key in objects.keys()) {
                                val obj = objects.getJSONObject(key)
                                val hash = obj.getString("hash")
                                val dest = File(objectsRoot, "${hash.substring(0, 2)}/$hash")
                                if (dest.isFile && dest.length() != obj.optLong("size", dest.length())) dest.delete()
                            }
                        }
                    }
                }
                installAndVerify(id, root, settings, progress)
                state.value = InstallState.Done(id)
            } catch (e: Exception) {
                state.value = InstallState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

    /** One file that failed the final inspection. */
    private data class BadFile(val url: String, val dest: File, val sha1: String?)

    /**
     * v1.11.0 — install, then INSPECT: every client jar, library and asset
     * is checked against its published sha1/size. Whatever is missing or
     * corrupt is deleted and re-downloaded — starting exactly from the first
     * bad file — and the whole thing repeats until EVERY file is complete
     * (max 3 passes, then a hard failure instead of a silent broken install).
     *
     * Pressing DOWNLOAD again on a half-installed version therefore resumes:
     * good files are kept, bad files are fetched again.
     */
    private suspend fun installAndVerify(
        id: String,
        root: JSONObject,
        settings: ObsiSettings,
        progress: (InstallState) -> Unit,
    ) {
        var pass = 0
        while (true) {
            installRest(id, root, settings, progress)
            pass += 1
            emit("verify", 0, 1, null, progress)
            val bad = inspectAll(id, root, progress)
            if (bad.isEmpty()) return
            if (pass >= 3) {
                throw IllegalStateException("$id: ${bad.size} file(s) still failing verification")
            }
            bad.forEach { runCatching { it.dest.delete() } }
        }
    }

    /**
     * The final inspection the user asked for: is EVERY file really there
     * and intact? Returns exactly the files that are not.
     */
    private fun inspectAll(id: String, root: JSONObject, progress: (InstallState) -> Unit): List<BadFile> {
        val bad = ArrayList<BadFile>()

        // client jar — this version and the inherited vanilla one
        val chain = mutableListOf(id)
        root.optString("inheritsFrom").takeIf { it.isNotEmpty() && it != id }?.let { chain.add(it) }
        for (vid in chain) {
            val vRoot = if (vid == id) root
            else runCatching { JSONObject(versionJson(context, vid).readText()) }.getOrNull() ?: continue
            vRoot.optJSONObject("downloads")?.optJSONObject("client")?.let { client ->
                val jar = clientJar(context, vid)
                val sha1 = client.optString("sha1").takeIf { it.length == 40 }
                val broken = !jar.isFile || (sha1 != null && Http.sha1Of(jar) != sha1)
                if (broken) bad += BadFile(client.optString("url"), jar, sha1)
            }
        }

        // libraries — sha1 when published, size when known
        val libRoot = Paths.librariesRoot(context)
        val wanted = collectLibraries(root, id, progress)
        wanted.forEachIndexed { index, lib ->
            if (lib.url.startsWith("file://")) return@forEachIndexed
            val dest = File(libRoot, lib.path)
            val broken = when {
                !dest.isFile || dest.length() == 0L -> true
                lib.sha1 != null && Http.sha1Of(dest) != lib.sha1 -> true
                lib.size > 0 && dest.length() != lib.size -> true
                else -> false
            }
            if (broken) {
                val url = when {
                    lib.url.isNotEmpty() -> lib.url
                    lib.coord.isNotEmpty() -> "${lib.repo.ifEmpty { defaultRepoFor(lib.coord) }}/${lib.path}"
                    else -> null
                }
                if (url != null) bad += BadFile(url, dest, lib.sha1)
            }
            if (index % 64 == 0) emit("verify", index, wanted.size, index.toFloat() / wanted.size, progress)
        }

        // assets — the index carries an exact size for every object
        val indexObj = root.optJSONObject("assetIndex")
        if (indexObj != null) {
            val indexId = indexObj.optString("id").ifEmpty { id }
            val indexFile = File(Paths.assetsRoot(context), "indexes/$indexId.json")
            if (indexFile.isFile) {
                val objects = runCatching { JSONObject(indexFile.readText()).optJSONObject("objects") }.getOrNull()
                if (objects != null) {
                    val objectsRoot = File(Paths.assetsRoot(context), "objects")
                    val keys = objects.keys().asSequence().toList()
                    keys.forEachIndexed { index, key ->
                        val obj = objects.getJSONObject(key)
                        val hash = obj.getString("hash")
                        val size = obj.optLong("size", 0L)
                        val dest = File(objectsRoot, "${hash.substring(0, 2)}/$hash")
                        if (!dest.isFile || (size > 0 && dest.length() != size)) {
                            bad += BadFile("$RESOURCES/${hash.substring(0, 2)}/$hash", dest, hash)
                        }
                        if (index % 256 == 0) emit("verify", index, keys.size, index.toFloat() / keys.size, progress)
                    }
                }
            }
        }
        return bad
    }

    private suspend fun installRest(
        id: String,
        root: JSONObject,
        settings: ObsiSettings,
        progress: (InstallState) -> Unit,
    ) {
        val dir = Paths.versionDir(context, id)
        val inherits = root.optString("inheritsFrom").takeIf { it.isNotEmpty() }
        if (inherits != null && inherits != id) {
            // loader profile: the vanilla parent (jar + assets) must be complete first
            if (!clientJar(context, inherits).isFile) {
                val parent = File(Paths.versionsRoot(context), inherits)
                parent.mkdirs()
                emit("manifest", 0, 1, null, progress)
                val parentJsonUrl = resolveParentUrl(inherits)
                val text = Http.get(parentJsonUrl) ?: throw IllegalStateException("no metadata for $inherits")
                versionJson(context, inherits).writeText(text)
                installRest(inherits, JSONObject(text), settings, progress)
            }
        }

        // 1) client jar (loader versions carry their own or reuse the parent's) ----
        val client = root.optJSONObject("downloads")?.optJSONObject("client")
        if (client != null && !clientJar(context, id).isFile) {
            emit("client", 0, 1, 0f, progress)
            Http.downloadToFile(
                url = client.getString("url"),
                dest = clientJar(context, id),
                sha1 = client.optString("sha1").takeIf { it.length == 40 },
            ) { done, total -> emit("client", 0, 1, if (total > 0) done.toFloat() / total else null, progress) }
            checkCancelled()
        }

        // 2) libraries -----------------------------------------------------------
        val wanted = collectLibraries(root, id, progress)
        val libRoot = Paths.librariesRoot(context)
        val jarLibs = ArrayList<File>(wanted.size)
        val nativesJars = ArrayList<File>()
        wanted.forEachIndexed { index, lib ->
            checkCancelled()
            val dest = File(libRoot, lib.path)
            // v1.11.0: "already there" is not enough — an existing library is
            // only skipped when its checksum matches, so resuming a broken
            // download repairs the corrupt files instead of trusting them
            val complete = dest.isFile && dest.length() > 0L &&
                (lib.sha1 == null || Http.sha1Of(dest) == lib.sha1)
            if (!complete) {
                emit("libraries", index, wanted.size, index.toFloat() / wanted.size, progress)
                if (lib.url.startsWith("file://")) {
                    val src = File(lib.url.removePrefix("file://"))
                    if (src.isFile) {
                        dest.parentFile?.mkdirs()
                        src.copyTo(dest, overwrite = true)
                    }
                } else if (lib.url.isNotEmpty()) {
                    Http.downloadToFile(lib.url, dest, lib.sha1) { done, total ->
                        emit("libraries", index, wanted.size, libFraction(index, wanted.size, done, total), progress)
                    }
                } else if (lib.coord.isNotEmpty()) {
                    val repo = lib.repo.ifEmpty { defaultRepoFor(lib.coord) }
                    Http.downloadToFile("$repo/${lib.path}", dest, lib.sha1) { done, total ->
                        emit("libraries", index, wanted.size, libFraction(index, wanted.size, done, total), progress)
                    }
                }
            }
            if (lib.isNatives) nativesJars += dest else jarLibs += dest
        }

        // 3) extract natives ------------------------------------------------------
        emit("natives", 0, 1, null, progress)
        val nativesDir = nativesDir(context, id)
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

        // 4) assets ----------------------------------------------------------------
        downloadAssets(id, root, progress)

        // 5) mark selected if nothing chosen yet -------------------------------------
        if (settings.selectedVersionValue.isBlank()) settings.selectedVersionValue = id
    }

    private fun libFraction(index: Int, total: Int, done: Long, fileTotal: Long): Float {
        val base = index.toFloat() / total
        val part = if (fileTotal > 0) (done.toFloat() / fileTotal) / total else 0f
        return base + part
    }

    /** Vanilla parent URL for inheritsFrom resolution, straight from the cached manifest. */
    private fun resolveParentUrl(id: String): String {
        val cached = File(context.cacheDir, "version_manifest_v2.json")
        if (cached.isFile) {
            runCatching {
                val arr = JSONObject(cached.readText()).getJSONArray("versions")
                for (i in 0 until arr.length()) {
                    val v = arr.getJSONObject(i)
                    if (v.getString("id") == id) return v.getString("url")
                }
            }
        }
        return "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }

    private suspend fun downloadAssets(id: String, root: JSONObject, progress: (InstallState) -> Unit) {
        val indexObj = root.optJSONObject("assetIndex") ?: return
        val indexId = indexObj.optString("id").ifEmpty { id }
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
                emit("assets", index, keys.size, index.toFloat() / keys.size, progress)
                Http.downloadToFile("$RESOURCES/${hash.substring(0, 2)}/$hash", dest, hash)
            }
        }
    }

    private data class LibEntry(
        val coord: String,
        val url: String,
        val path: String,
        val sha1: String?,
        val size: Long,
        val repo: String = "",
        val isNatives: Boolean = false,
    )

    /**
     * Collects the library list for a version, merging the inherited (vanilla)
     * libraries for loader profiles and understanding both the modern
     * `downloads` layout and the pre-1.13 `name`+`url` layout.
     */
    private fun collectLibraries(root: JSONObject, id: String, progress: (InstallState) -> Unit): List<LibEntry> {
        val out = ArrayList<LibEntry>()
        val seen = HashSet<String>()

        fun addFrom(json: JSONObject) {
            val libs = json.optJSONArray("libraries") ?: return
            for (i in 0 until libs.length()) {
                val entry = libs.getJSONObject(i)
                if (!rulesAllow(entry.optJSONArray("rules"))) continue
                val coord = entry.optString("name")
                if (coord.isEmpty() || !seen.add(coord)) continue
                val downloads = entry.optJSONObject("downloads")
                if (downloads != null) {
                    downloads.optJSONObject("artifact")?.let { artifact ->
                        out += LibEntry(
                            coord = coord,
                            url = artifact.optString("url"),
                            path = artifact.optString("path").ifEmpty { mavenPath(coord) },
                            sha1 = artifact.optString("sha1").takeIf { it.length == 40 },
                            size = artifact.optLong("size", 0L),
                        )
                    }
                    downloads.optJSONObject("natives-linux")?.let { nativesLinux ->
                        out += LibEntry(
                            coord = coord,
                            url = nativesLinux.optString("url"),
                            path = nativesLinux.optString("path").ifEmpty { mavenPath(coord, "natives-linux") },
                            sha1 = nativesLinux.optString("sha1").takeIf { it.length == 40 },
                            size = nativesLinux.optLong("size", 0L),
                            isNatives = true,
                        )
                    }
                } else {
                    // pre-1.13 layout: name + optional repo url
                    out += LibEntry(
                        coord = coord,
                        url = "",
                        path = mavenPath(coord),
                        sha1 = null,
                        size = 0L,
                        repo = entry.optString("url").removeSuffix("/").takeIf { it.isNotEmpty() } ?: "",
                    )
                    if (entry.has("natives-linux") || entry.optString("natives").isNotEmpty()) {
                        out += LibEntry(
                            coord = coord,
                            url = "",
                            path = mavenPath(coord, "natives-linux"),
                            sha1 = null,
                            size = 0L,
                            repo = entry.optString("url").removeSuffix("/").takeIf { it.isNotEmpty() } ?: "",
                            isNatives = true,
                        )
                    }
                }
            }
        }

        // inherited (vanilla) libraries first so loader libraries win on duplicates
        val inherits = root.optString("inheritsFrom").takeIf { it.isNotEmpty() }
        if (inherits != null && inherits != id) {
            val parentJson = versionJson(context, inherits)
            if (parentJson.isFile) {
                runCatching { addFrom(JSONObject(parentJson.readText())) }
            }
        }
        addFrom(root)
        return out
    }

    private fun emit(step: String, done: Int, total: Int, fraction: Float?, progress: (InstallState) -> Unit) {
        val s = InstallState.Running(step, done, total, fraction)
        state.value = s
        progress(s)
    }

    private fun checkCancelled() {
        if (cancelled) throw InterruptedException("cancelled")
    }

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
