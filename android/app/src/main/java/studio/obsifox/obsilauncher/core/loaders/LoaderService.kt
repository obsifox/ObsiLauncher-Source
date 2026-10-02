package studio.obsifox.obsilauncher.core.loaders

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.game.InstallState
import studio.obsifox.obsilauncher.core.game.McVersion
import studio.obsifox.obsilauncher.core.game.PackJava
import studio.obsifox.obsilauncher.core.game.VersionInstaller
import studio.obsifox.obsilauncher.core.net.Http
import studio.obsifox.obsilauncher.core.runtime.RuntimePacks
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class LoaderType(val id: String, val display: String) {
    VANILLA("vanilla", "Vanilla"),
    FABRIC("fabric", "Fabric"),
    FORGE("forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge"),
    QUILT("quilt", "Quilt"),
    OPTIFINE("optifine", "OptiFine");

    companion object {
        fun byId(id: String): LoaderType = entries.firstOrNull { it.id == id } ?: VANILLA
    }
}

data class LoaderVersion(val version: String, val stable: Boolean, val recommended: Boolean = false)

data class InstalledLoader(val versionId: String, val loader: LoaderType, val loaderVersion: String?)

/**
 * Installs mod loaders on top of a vanilla Minecraft version.
 *  - Fabric / Quilt: their meta servers serve a ready-made version JSON.
 *  - Forge / NeoForge: the official installer jar runs headless on the runtime pack's JVM.
 *  - OptiFine: the installer jar is patched onto the client jar with `optifine.Patcher`.
 */
class LoaderService(private val context: Context) {

    val state = kotlinx.coroutines.flow.MutableStateFlow<InstallState>(InstallState.Idle)

    private fun progress(s: InstallState) { state.value = s }
    private fun progressStep(step: String) { progress(InstallState.Running(step, 0, 1, null)) }

    private val cache = ConcurrentHashMap<String, List<LoaderVersion>>()

    suspend fun versions(type: LoaderType, mc: String): List<LoaderVersion> = withContext(Dispatchers.IO) {
        if (type == LoaderType.VANILLA) return@withContext emptyList()
        val key = "${type.id}:$mc"
        cache[key]?.let { return@withContext it }
        val list = when (type) {
            LoaderType.FABRIC -> metaVersions("https://meta.fabricmc.net/v2/versions/loader/${Http.enc(mc)}")
            LoaderType.QUILT -> metaVersions("https://meta.quiltmc.org/v3/versions/loader/${Http.enc(mc)}")
            LoaderType.FORGE -> forgeVersions(mc)
            LoaderType.NEOFORGE -> neoForgeVersions(mc)
            LoaderType.OPTIFINE -> optiFineVersions(mc)
            LoaderType.VANILLA -> emptyList()
        }.sortedWith { a, b -> compareVersions(b.version, a.version) }
        cache[key] = list
        list
    }

    private fun metaVersions(url: String): List<LoaderVersion> {
        val text = Http.get(url) ?: return emptyList()
        val arr = org.json.JSONArray(text)
        val out = ArrayList<LoaderVersion>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val l = e.optJSONObject("loader") ?: continue
            val v = l.optString("version").takeIf { it.isNotEmpty() } ?: continue
            val stable = l.optBoolean("stable", !Regex("(?i)(beta|alpha|rc|pre|snapshot)").containsMatchIn(v))
            out += LoaderVersion(v, stable)
        }
        return out
    }

    private fun forgeVersions(mc: String): List<LoaderVersion> {
        val xml = Http.get("$FORGE_MAVEN/net/minecraftforge/forge/maven-metadata.xml") ?: return emptyList()
        val rec = runCatching {
            Http.get("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
                ?.let { JSONObject(it).optJSONObject("promos")?.optString("$mc-recommended") }
        }.getOrNull()
        return versionTags(xml).filter { it.startsWith("$mc-") }
            .map { it.removePrefix("$mc-").removeSuffix("-$mc") }
            .distinct()
            .map { v -> LoaderVersion(v, stable = true, recommended = v == rec) }
    }

    private fun neoForgeVersions(mc: String): List<LoaderVersion> {
        val prefix = neoForgePrefix(mc) ?: return emptyList()
        val xml = Http.get("$NEOFORGE_MAVEN/net/neoforged/neoforge/maven-metadata.xml") ?: return emptyList()
        val list = versionTags(xml).filter { it.startsWith(prefix) }
        val firstStable = list.firstOrNull { !it.contains("-") }
        return list.map { LoaderVersion(it, stable = !it.contains("-"), recommended = it == firstStable) }
    }

    /** Best-effort scrape of the official OptiFine downloads page. */
    private fun optiFineVersions(mc: String): List<LoaderVersion> {
        val html = Http.get("https://optifine.net/downloads") ?: return emptyList()
        val regex = Regex("""OptiFine_${Regex.escape(mc)}[._A-Za-z0-9]*\.jar""")
        return regex.findAll(html).map { it.value }
            .map { it.removePrefix("OptiFine_").removeSuffix(".jar") }
            .distinct()
            .map { LoaderVersion(it, stable = !it.contains("pre", true)) }
            .toList()
    }

    /** The adloadx page embeds a signed token needed for the direct file download. */
    fun optiFineDownloadUrl(mc: String, loaderVersion: String): String? {
        val name = "OptiFine_${mc}_${loaderVersion}.jar"
        val page = Http.get("https://optifine.net/adloadx?f=$name") ?: return null
        val token = Regex("""x\s*=\s*["']?([A-Za-z0-9]+)["']?""").find(page)?.groupValues?.get(1)
        return if (token != null) "https://optifine.net/downloadx?f=$name&x=$token" else null
    }

    // --------------------------------------------------------------------------- install

    suspend fun install(
        type: LoaderType,
        mc: String,
        loaderVersion: String?,
        installer: VersionInstaller,
        settings: ObsiSettings,
        progress: (InstallState) -> Unit = { },
    ): InstalledLoader = withContext(Dispatchers.IO) {
        if (type == LoaderType.VANILLA) {
            installer.install(McVersion(mc, "release", manifestUrlFor(mc), ""), settings)
            return@withContext InstalledLoader(mc, LoaderType.VANILLA, null)
        }
        val lv = loaderVersion ?: versions(type, mc).let { list ->
            (list.firstOrNull { it.recommended } ?: list.firstOrNull { it.stable } ?: list.firstOrNull())?.version
        } ?: throw IllegalStateException("${type.display} is not available for Minecraft $mc (or optifine.net is unreachable)")

        val id = when (type) {
            LoaderType.FABRIC -> installMeta("https://meta.fabricmc.net/v2/versions/loader/${Http.enc(mc)}/${Http.enc(lv)}/profile/json", mc, LoaderType.FABRIC, lv, installer, settings, progress)
            LoaderType.QUILT -> installMeta("https://meta.quiltmc.org/v3/versions/loader/${Http.enc(mc)}/${Http.enc(lv)}/profile/json", mc, LoaderType.QUILT, lv, installer, settings, progress)
            LoaderType.FORGE -> installWithInstaller(type, mc, lv, "$FORGE_MAVEN/net/minecraftforge/forge/${forgeFull(mc, lv)}/forge-${forgeFull(mc, lv)}-installer.jar", installer, settings, progress)
            LoaderType.NEOFORGE -> installWithInstaller(type, mc, lv, "$NEOFORGE_MAVEN/net/neoforged/neoforge/$lv/neoforge-$lv-installer.jar", installer, settings, progress)
            LoaderType.OPTIFINE -> installOptiFine(mc, lv, installer, settings, progress)
            LoaderType.VANILLA -> mc
        }
        InstalledLoader(id, type, lv)
    }

    private fun manifestUrlFor(id: String): String {
        // exact entry from the manifest keeps the installer's own download paths
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
        return "https://piston-meta.mojang.com/v1/packages/unknown/$id.json"
    }

    private suspend fun installMeta(
        profileUrl: String,
        mc: String,
        type: LoaderType,
        lv: String,
        installer: VersionInstaller,
        settings: ObsiSettings,
        progress: (InstallState) -> Unit,
    ): String {
        progressStep("loader_profile")
        val text = Http.get(profileUrl) ?: throw IllegalStateException("loader meta server unreachable")
        val id = JSONObject(text).optString("id").takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("loader profile has no id")
        Paths.versionDir(context, id).mkdirs()
        VersionInstaller.versionJson(context, id).writeText(text)
        installer.installLoaded(id, settings, ::progress)
        return id
    }

    private suspend fun installWithInstaller(
        type: LoaderType,
        mc: String,
        lv: String,
        url: String,
        installer: VersionInstaller,
        settings: ObsiSettings,
        progress: (InstallState) -> Unit,
    ): String {
        val expected = if (type == LoaderType.FORGE) "$mc-forge-$lv" else "neoforge-$lv"
        if (VersionInstaller.versionJson(context, expected).isFile && installedMarker(expected).isFile) {
            installer.installLoaded(expected, settings, ::progress)
            return expected
        }
        // 1) vanilla must exist — the installer patches the client jar in place
        installer.install(McVersion(mc, "release", manifestUrlFor(mc), ""), settings, ::progress)

        // 2) installer jar
        progressStep("loader_installer")
        val jar = File(File(context.cacheDir, "installers"), url.substringAfterLast('/'))
        if (!jar.isFile || jar.length() == 0L) {
            Http.downloadToFile(url, jar)
        }

        // 3) stub launcher profile required by the installer
        val shared = Paths.gamesRoot(context)
        val profiles = File(shared, "launcher_profiles.json")
        if (!profiles.isFile) profiles.writeText("""{"profiles":{},"settings":{},"version":3}""")

        // 4) run it headless on the runtime pack's JVM
        val pack = selectedPack(settings)
            ?: throw IllegalStateException("Runtime pack missing — install one from Settings → Runtime first")
        val before = versionDirs()
        var result = PackJava.run(context, pack, listOf("-Djava.awt.headless=true", "-jar", jar.absolutePath, "--installClient", shared.absolutePath), shared) {
            progressStep("loader_output")
        }
        if (result.exitCode != 0 && result.output.contains("Unknown option", ignoreCase = true)) {
            result = PackJava.run(context, pack, listOf("-Djava.awt.headless=true", "-jar", jar.absolutePath, "--install-client", shared.absolutePath), shared)
        }
        if (result.exitCode != 0) {
            throw IllegalStateException("${type.display} installer failed (exit ${result.exitCode}):\n" +
                result.output.lines().takeLast(20).joinToString("\n"))
        }

        // 5) which version did it create?
        val created = (versionDirs() - before).filter { it != mc && VersionInstaller.versionJson(context, it).isFile }
        val id = when {
            VersionInstaller.versionJson(context, expected).isFile -> expected
            created.size == 1 -> created.first()
            else -> created.firstOrNull { it.contains(type.id, ignoreCase = true) }
                ?: throw IllegalStateException("${type.display} installer finished but no new version was found")
        }
        installedMarker(id).writeText("installer finished OK\n")
        installer.installLoaded(id, settings, ::progress)
        return id
    }

    /**
     * OptiFine has no usable headless installer; the community-standard route is
     * `optifine.Patcher` (inside the installer jar) which produces the patched
     * client jar. We then write the version JSON ourselves.
     */
    private suspend fun installOptiFine(
        mc: String,
        lv: String,
        installer: VersionInstaller,
        settings: ObsiSettings,
        progress: (InstallState) -> Unit,
    ): String {
        val id = "$mc-OptiFine_$lv"
        Paths.versionDir(context, id).mkdirs()
        progressStep("loader_installer")

        // 1) vanilla
        installer.install(McVersion(mc, "release", manifestUrlFor(mc), ""), settings, ::progress)

        // 2) OptiFine installer jar
        val dlUrl = optiFineDownloadUrl(mc, lv)
            ?: throw IllegalStateException("cannot resolve the OptiFine download (optifine.net unreachable?)")
        val installersDir = File(context.cacheDir, "installers")
        val jar = File(installersDir, "OptiFine_${mc}_$lv.jar")
        if (!jar.isFile || jar.length() == 0L) Http.downloadToFile(dlUrl, jar)

        // 3) run the patcher:  java -cp OptiFine.jar optifine.Patcher <client> <installer> <output>
        val pack = selectedPack(settings)
            ?: throw IllegalStateException("Runtime pack missing — install one from Settings → Runtime first")
        val patched = File(Paths.versionDir(context, id), "$id.jar")
        val workDir = Paths.gamesRoot(context)
        val result = PackJava.run(
            context, pack,
            listOf("-cp", jar.absolutePath, "optifine.Patcher", VersionInstaller.clientJar(context, mc).absolutePath, jar.absolutePath, patched.absolutePath),
            workDir,
        )
        if (result.exitCode != 0 || !patched.isFile || patched.length() == 0L) {
            throw IllegalStateException("OptiFine patcher failed (exit ${result.exitCode}):\n" +
                result.output.lines().takeLast(20).joinToString("\n"))
        }

        // 4) version json — inherits everything from vanilla; the installer jar stays
        //    on the classpath (it carries the Start entry point and launchwrapper).
        val json = JSONObject().apply {
            put("id", id)
            put("inheritsFrom", mc)
            put("type", "release")
            put("releaseTime", System.currentTimeMillis().toString())
            put("time", System.currentTimeMillis().toString())
            put("mainClass", "Start")
            put("arguments", JSONObject().put("game", org.json.JSONArray()))
            put(
                "libraries",
                org.json.JSONArray().put(
                    JSONObject().put("name", "optifine:OptiFine:$lv")
                        .put("downloads", JSONObject().put(
                            "artifact",
                            JSONObject().put("path", "optifine/OptiFine/$lv/OptiFine-$lv.jar")
                                .put("url", "file://${jar.absolutePath}")
                                .put("sha1", Http.sha1Of(jar))
                                .put("size", jar.length()),
                        )),
                ),
            )
        }
        VersionInstaller.versionJson(context, id).writeText(json.toString())
        // mirror the vanilla jar as the base classpath entry (the patched jar replaces it)
        VersionInstaller.clientJar(context, id).let { base ->
            if (!base.isFile) runCatching { java.nio.file.Files.copy(patched.toPath(), base.toPath()) }
        }
        installedMarker(id).writeText("optifine patched OK\n")
        return id
    }

    private fun selectedPack(settings: ObsiSettings): studio.obsifox.obsilauncher.core.runtime.Pack? {
        val packs = RuntimePacks(context)
        return packs.packByName(settings.runtimePackValue) ?: packs.packs.value.firstOrNull()
    }

    private fun installedMarker(id: String): File = File(Paths.versionDir(context, id), ".obsi-installed")

    private fun versionDirs(): Set<String> =
        Paths.versionsRoot(context).listFiles { f -> f.isDirectory }?.map { it.name }?.toSet().orEmpty()

    companion object {
        const val FORGE_MAVEN = "https://maven.minecraftforge.net"
        const val NEOFORGE_MAVEN = "https://maven.neoforged.net/releases"

        private val versionTag = Regex("<version>([^<]+)</version>")
        fun versionTags(xml: String): List<String> = versionTag.findAll(xml).map { it.groupValues[1].trim() }.toList()

        fun forgeFull(mc: String, lv: String): String =
            if (mc == "1.7.10" || mc.startsWith("1.6") || mc.startsWith("1.5") || mc.startsWith("1.4") || mc.startsWith("1.3")) "$mc-$lv-$mc" else "$mc-$lv"

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
                parts.isNotEmpty() && parts[0].toIntOrNull()?.let { it >= 26 } == true -> "${parts.take(2).joinToString(".")}."
                else -> null
            }
        }

        fun compareVersions(a: String, b: String): Int {
            fun split(v: String): Pair<List<String>, List<String>> {
                val clean = v.substringBefore('+')
                val main = clean.substringBefore('-').split('.')
                val pre = if ('-' in clean) clean.substringAfter('-').split('.', '-') else emptyList()
                return main to pre
            }
            fun cmpPart(x: String, y: String): Int {
                val xn = x.toLongOrNull(); val yn = y.toLongOrNull()
                return when {
                    xn != null && yn != null -> xn.compareTo(yn)
                    xn != null -> -1
                    yn != null -> 1
                    else -> x.compareTo(y, ignoreCase = true)
                }
            }
            fun cmpList(x: List<String>, y: List<String>): Int {
                for (i in 0 until maxOf(x.size, y.size)) {
                    val c = cmpPart(x.getOrElse(i) { "0" }, y.getOrElse(i) { "0" })
                    if (c != 0) return c
                }
                return 0
            }
            val (am, ap) = split(a); val (bm, bp) = split(b)
            val main = cmpList(am, bm)
            if (main != 0) return main
            if (ap.isEmpty() && bp.isEmpty()) return 0
            if (ap.isEmpty()) return 1
            if (bp.isEmpty()) return -1
            for (i in 0 until minOf(ap.size, bp.size)) {
                val c = cmpPart(ap[i], bp[i])
                if (c != 0) return c
            }
            return ap.size.compareTo(bp.size)
        }
    }
}
