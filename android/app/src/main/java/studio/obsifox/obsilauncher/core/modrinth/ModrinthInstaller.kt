package studio.obsifox.obsilauncher.core.modrinth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.game.InstallState
import studio.obsifox.obsilauncher.core.game.McVersion
import studio.obsifox.obsilauncher.core.game.VersionInstaller
import studio.obsifox.obsilauncher.core.loaders.LoaderService
import studio.obsifox.obsilauncher.core.loaders.LoaderType
import studio.obsifox.obsilauncher.core.net.Http
import java.io.File
import java.util.zip.ZipFile

/**
 * Downloads Modrinth files into an instance and keeps the manifest in sync.
 * Also understands `.mrpack` modpack archives (modrinth.index.json).
 */
class ModrinthInstaller(private val context: Context) {

    val api = ModrinthApi()
    val content = ContentManager(Paths.versionsRoot(context))

    var currentInstance: String = ""
        private set

    /** Bind the manager to an instance (version id) before listing / installing. */
    fun forInstance(versionId: String): ModrinthInstaller {
        currentInstance = versionId
        content.loadDependencyCache(versionId)
        return this
    }

    fun newResolver(): DepResolver = DepResolver(api, content.installedProjectIds(currentInstance))

    /** Simple direct install without dependency dialog (used by "quick install" and updates). */
    suspend fun installVersion(
        project: MrProject,
        version: MrVersion,
        explicit: Boolean,
        progress: (InstallState) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val kind = ProjectKind.fromApi(project.projectType)
        val dir = content.folderFor(currentInstance, kind)
        dir.mkdirs()
        val dest = File(dir, version.fileName)
        progress(InstallState.Running("content:${project.title}", 0, 1, 0f))
        Http.downloadToFile(version.fileUrl, dest, version.fileSha1) { done, total ->
            progress(InstallState.Running("content:${project.title}", 0, 1, if (total > 0) done.toFloat() / total else null))
        }
        content.recordInstalled(currentInstance, kind, project.id, project.title, project.iconUrl, version, explicit)
        progress(InstallState.Done(currentInstance))
        dest.name
    }

    /** Install a whole .mrpack modpack into a NEW instance directory named [name]. */
    suspend fun installModpack(
        mrpackFile: File,
        name: String,
        settings: ObsiSettings,
        installer: VersionInstaller,
        loaders: LoaderService,
        progress: (InstallState) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        progress(InstallState.Running("modpack_index", 0, 1, null))
        val index = ZipFile(mrpackFile).use { zip ->
            val entry = zip.getEntry("modrinth.index.json")
                ?: throw IllegalStateException("not a Modrinth modpack (modrinth.index.json missing)")
            zip.getInputStream(entry).bufferedReader().readText()
        }
        val root = JSONObject(index)
        val gameId = root.optString("gameId").takeIf { it == "minecraft" }
            ?: throw IllegalStateException("only Minecraft modpacks are supported")
        val deps = root.optJSONObject("dependencies") ?: JSONObject()
        val mcVersion = deps.optString("minecraft").takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("modpack does not declare a Minecraft version")

        // 1) vanilla base
        installer.install(McVersion(mcVersion, "release", manifestUrlFor(mcVersion), ""), settings, progress)

        // 2) loader
        val loaderName = deps.optString("fabric-loader").takeIf { it.isNotEmpty() }
        val forgeName = deps.optString("forge").takeIf { it.isNotEmpty() }
        val neoName = deps.optString("neoforge").takeIf { it.isNotEmpty() }
        val quiltName = deps.optString("quilt-loader").takeIf { it.isNotEmpty() }
        val versionId = when {
            loaderName != null -> loaders.install(LoaderType.FABRIC, mcVersion, loaderName, installer, settings, progress).versionId
            forgeName != null -> loaders.install(LoaderType.FORGE, mcVersion, forgeName, installer, settings, progress).versionId
            neoName != null -> loaders.install(LoaderType.NEOFORGE, mcVersion, neoName, installer, settings, progress).versionId
            quiltName != null -> loaders.install(LoaderType.QUILT, mcVersion, quiltName, installer, settings, progress).versionId
            else -> mcVersion
        }

        // 3) files
        val files = root.optJSONArray("files") ?: JSONArray()
        val contentDir = content.versionDir(versionId)
        val manifestEntries = ArrayList<InstalledEntry>()
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val path = f.optString("path")
            if (path.contains("..")) continue // safety
            val env = f.optJSONObject("env")
            if (env != null && env.optString("client").let { it.isNotEmpty() && it != "required" && it != "optional" }) continue
            val kind = ProjectKind.fromIndexPath(path)
            val dest = File(contentDir, path)
            dest.parentFile?.mkdirs()
            val downloads = f.optJSONArray("downloads")?.optString(0)
                ?: throw IllegalStateException("modpack file $path has no download")
            val hashes = f.optJSONObject("hashes")
            progress(InstallState.Running("modpack_files", i, files.length(), i.toFloat() / files.length()))
            Http.downloadToFile(downloads, dest, hashes?.optString("sha1")?.takeIf { it.length == 40 })
            // look the file up on Modrinth for a friendly manifest entry
            val sha1 = hashes?.optString("sha1")
            val versionInfo = if (sha1?.length == 40) api.versionsByHashes(listOf(sha1))[sha1] else null
            val project = versionInfo?.let { runCatching { api.project(it.projectId) }.getOrNull() }
            manifestEntries += InstalledEntry(
                fileName = path.substringAfterLast('/'),
                kind = kind.apiType,
                projectId = versionInfo?.projectId ?: "",
                versionId = versionInfo?.id ?: "",
                versionNumber = versionInfo?.versionNumber ?: "",
                title = project?.title ?: path.substringAfterLast('/'),
                iconUrl = project?.iconUrl,
                sha1 = sha1?.takeIf { it.length == 40 },
                explicit = false,
            )
        }
        // merge into the manifest
        val existing = content.readManifest(versionId).entries
        content.writeManifest(versionId, ContentManifest(existing + manifestEntries.filter { new -> existing.none { it.fileName == new.fileName } }))
        progress(InstallState.Done(versionId))
        versionId
    }

    private fun manifestUrlFor(id: String): String {
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
}
