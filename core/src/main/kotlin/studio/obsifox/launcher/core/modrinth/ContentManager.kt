package studio.obsifox.launcher.core.modrinth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.net.DownloadItem
import studio.obsifox.launcher.core.net.Downloader
import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.ProgressSink
import studio.obsifox.launcher.core.util.ProgressUpdate
import studio.obsifox.launcher.core.util.StateJson
import studio.obsifox.launcher.core.util.sha1Hex
import studio.obsifox.launcher.core.util.writeTextAtomic
import java.nio.file.Files
import java.nio.file.Path

/** An available update for an installed file. */
data class ContentUpdate(val file: ContentFile, val latest: MrVersion)

/**
 * Manages the mods / resource packs / shader packs of one instance and remembers where each file came from
 * (`<instance>/obsi-content.json`), so we can show real names, check for updates and resolve dependencies.
 */
class ContentManager(
    private val paths: LauncherPaths,
    private val api: ModrinthApi,
    private val downloader: Downloader,
) {
    private fun manifestPath(instanceId: String): Path = paths.instanceDir(instanceId).resolve("obsi-content.json")

    fun readManifest(instanceId: String): ContentManifest = try {
        val p = manifestPath(instanceId)
        if (Files.isRegularFile(p)) StateJson.decodeFromString(ContentManifest.serializer(), Files.readString(p)) else ContentManifest()
    } catch (_: Exception) {
        ContentManifest()
    }

    @Synchronized
    private fun writeManifest(instanceId: String, m: ContentManifest) =
        writeTextAtomic(manifestPath(instanceId), StateJson.encodeToString(ContentManifest.serializer(), m))

    /** The Modrinth `loaders` values whose projects can run in [instance]. */
    fun compatibleLoaders(instance: Instance): List<String> = when (instance.loader) {
        LoaderType.VANILLA -> emptyList()
        LoaderType.QUILT -> listOf("quilt", "fabric")
        else -> listOf(instance.loader.name.lowercase())
    }

    fun folderFor(instanceId: String, kind: ProjectKind): Path {
        val folder = kind.folder ?: throw LauncherException("${kind.apiType} cannot be installed into an instance")
        return paths.gameDir(instanceId).resolve(folder)
    }

    // ------------------------------------------------------------------------------------------------ listing

    suspend fun list(instance: Instance, kind: ProjectKind): List<ContentFile> = withContext(Dispatchers.IO) {
        val dir = folderFor(instance.id, kind)
        if (!Files.isDirectory(dir)) return@withContext emptyList()
        val manifest = readManifest(instance.id).entries.associateBy { it.fileName.removeSuffix(".disabled") }
        Files.list(dir).use { s ->
            s.filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .filter { n -> val b = n.removeSuffix(".disabled"); b.endsWith(".jar") || b.endsWith(".zip") || b.endsWith(".mrpack") }
                .toList()
        }.map { name ->
            val enabled = !name.endsWith(".disabled")
            ContentFile(kind, name, enabled, Files.size(dir.resolve(name)), manifest[name.removeSuffix(".disabled")])
        }.sortedBy { it.displayName.lowercase() }
    }

    suspend fun setEnabled(instance: Instance, file: ContentFile, enabled: Boolean) = withContext(Dispatchers.IO) {
        val dir = folderFor(instance.id, file.kind)
        val from = dir.resolve(file.fileName)
        val base = file.fileName.removeSuffix(".disabled")
        val to = dir.resolve(if (enabled) base else "$base.disabled")
        if (from != to && Files.exists(from)) Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    suspend fun remove(instance: Instance, file: ContentFile) = withContext(Dispatchers.IO) {
        val dir = folderFor(instance.id, file.kind)
        Files.deleteIfExists(dir.resolve(file.fileName))
        val base = file.fileName.removeSuffix(".disabled")
        writeManifest(instance.id, ContentManifest(readManifest(instance.id).entries.filterNot { it.fileName.removeSuffix(".disabled") == base }))
    }

    /** Looks unknown files up on Modrinth by hash so they get proper names and icons. Returns how many were identified. */
    suspend fun identifyUnknown(instance: Instance): Int {
        var identified = 0
        for (kind in listOf(ProjectKind.MOD, ProjectKind.RESOURCEPACK, ProjectKind.SHADER)) {
            val unknown = list(instance, kind).filter { it.entry == null }
            if (unknown.isEmpty()) continue
            val dir = folderFor(instance.id, kind)
            val hashes = withContext(Dispatchers.IO) { unknown.associateBy { sha1Hex(dir.resolve(it.fileName)) } }
            val found = api.versionsByHashes(hashes.keys)
            if (found.isEmpty()) continue
            val projects = api.projects(found.values.map { it.projectId }.toSet()).associateBy { it.id }
            val entries = readManifest(instance.id).entries.toMutableList()
            for ((hash, v) in found) {
                val f = hashes[hash] ?: continue
                val p = projects[v.projectId]
                entries += InstalledEntry(f.fileName.removeSuffix(".disabled"), kind.apiType, v.projectId, v.id, v.versionNumber, p?.title, p?.iconUrl, hash, explicit = true)
                identified++
            }
            writeManifest(instance.id, ContentManifest(entries))
        }
        return identified
    }

    // ------------------------------------------------------------------------------------------------ installing

    /** Chooses the best version of [projectId] for [instance] (release > beta > alpha), or null when none is compatible. */
    suspend fun pickVersion(instance: Instance, projectId: String, kind: ProjectKind): MrVersion? {
        val loaders = if (kind == ProjectKind.MOD) compatibleLoaders(instance) else emptyList()
        if (kind == ProjectKind.MOD && loaders.isEmpty()) return null
        val all = api.versions(projectId, loaders, listOf(instance.mcVersion))
        return all.firstOrNull { it.versionType == "release" } ?: all.firstOrNull { it.versionType == "beta" } ?: all.firstOrNull()
    }

    /**
     * Installs [version] (and its required dependencies) into [instance].
     * @return titles of everything that was installed, main project first.
     */
    suspend fun install(
        instance: Instance,
        project: Project,
        version: MrVersion,
        progress: ProgressSink,
        explicit: Boolean = true,
    ): List<String> {
        val kind = ProjectKind.fromApi(project.projectType)
        val installed = ArrayList<String>()
        val known = readManifest(instance.id).entries.mapNotNull { it.projectId }.toMutableSet()
        installOne(instance, kind, project, version, explicit, progress)
        installed += project.title
        known += project.id

        if (kind == ProjectKind.MOD) {
            // breadth-first over required dependencies
            val queue = ArrayDeque(version.dependencies.filter { it.dependencyType == "required" })
            var guard = 0
            while (queue.isNotEmpty() && guard++ < 64) {
                val dep = queue.removeFirst()
                val pid = dep.projectId ?: dep.versionId?.let { api.version(it).projectId } ?: continue
                if (!known.add(pid)) continue
                val depVersion = if (dep.versionId != null) api.version(dep.versionId) else pickVersion(instance, pid, ProjectKind.MOD) ?: continue
                val depProject = api.project(pid)
                installOne(instance, ProjectKind.MOD, depProject, depVersion, false, progress)
                installed += depProject.title
                queue += depVersion.dependencies.filter { it.dependencyType == "required" }
            }
        }
        return installed
    }

    private suspend fun installOne(instance: Instance, kind: ProjectKind, project: Project, version: MrVersion, explicit: Boolean, progress: ProgressSink) {
        val file = version.primaryFile ?: throw LauncherException("${project.title} ${version.versionNumber} has no downloadable file")
        val dir = folderFor(instance.id, kind)
        val safeName = file.filename.substringAfterLast('/').substringAfterLast('\\')
        val dest = dir.resolve(safeName)
        downloader.run(listOf(DownloadItem(listOf(file.url), dest, file.hashes["sha1"], file.size.takeIf { it > 0 })), "content:${project.title}", progress)
        val old = readManifest(instance.id).entries
        // replace any older entry of the same project (update) and delete its file
        old.filter { it.projectId == project.id && it.fileName.removeSuffix(".disabled") != safeName }.forEach {
            withContext(Dispatchers.IO) {
                Files.deleteIfExists(dir.resolve(it.fileName))
                Files.deleteIfExists(dir.resolve(it.fileName.removeSuffix(".disabled") + ".disabled"))
            }
        }
        val keep = old.filterNot { it.projectId == project.id || it.fileName.removeSuffix(".disabled") == safeName }
        writeManifest(
            instance.id,
            ContentManifest(keep + InstalledEntry(safeName, kind.apiType, project.id, version.id, version.versionNumber, project.title, project.iconUrl, file.hashes["sha1"], explicit)),
        )
    }

    // ------------------------------------------------------------------------------------------------ updates

    /** Checks all mods for newer compatible versions using Modrinth's bulk hash endpoint. */
    suspend fun checkUpdates(instance: Instance, kind: ProjectKind = ProjectKind.MOD): List<ContentUpdate> {
        val files = list(instance, kind).filter { it.enabled && it.entry?.versionId != null }
        if (files.isEmpty()) return emptyList()
        val dir = folderFor(instance.id, kind)
        val byHash = withContext(Dispatchers.IO) { files.associateBy { sha1Hex(dir.resolve(it.fileName)) } }
        val loaders = if (kind == ProjectKind.MOD) compatibleLoaders(instance) else emptyList()
        val latest = api.latestForHashes(byHash.keys, loaders, listOf(instance.mcVersion))
        val result = ArrayList<ContentUpdate>()
        for ((hash, v) in latest) {
            val f = byHash[hash] ?: continue
            if (v.id == f.entry?.versionId) continue
            if (v.versionType == "release") {
                result += ContentUpdate(f, v)
                continue
            }
            // The newest upload is a beta/alpha. Only offer it when the installed file is a pre-release too;
            // otherwise offer the newest *release* (if it is actually newer than what is installed).
            val current = f.entry?.versionId?.let { id -> runCatching { api.version(id) }.getOrNull() }
            if (current != null && current.versionType != "release") {
                result += ContentUpdate(f, v)
                continue
            }
            val release = api.versions(v.projectId, loaders, listOf(instance.mcVersion)).firstOrNull { it.versionType == "release" } ?: continue
            if (release.id != f.entry?.versionId && (current == null || release.datePublished > current.datePublished)) {
                result += ContentUpdate(f, release)
            }
        }
        return result
    }

    suspend fun applyUpdate(instance: Instance, update: ContentUpdate, progress: ProgressSink) {
        val project = api.project(update.latest.projectId)
        install(instance, project, update.latest, progress, explicit = update.file.entry?.explicit ?: true)
    }
}
