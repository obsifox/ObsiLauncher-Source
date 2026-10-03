package studio.obsifox.obsilauncher.core.modrinth

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One mod/resource/shader file present in an instance's content folder. */
data class ContentFile(
    val kind: ProjectKind,
    val fileName: String,
    val enabled: Boolean,
    val size: Long,
    val entry: InstalledEntry?,
)

/** Where an installed file came from (persisted in obsi-content.json). */
data class InstalledEntry(
    val fileName: String,
    val kind: String,          // ProjectKind.apiType
    val projectId: String,
    val versionId: String,
    val versionNumber: String,
    val title: String?,
    val iconUrl: String?,
    val sha1: String?,
    val explicit: Boolean,     // chosen by the user (true) vs pulled in as dependency (false)
)

data class ContentManifest(val entries: List<InstalledEntry> = emptyList()) {
    fun toJson(): JSONObject = JSONObject().put(
        "entries",
        JSONArray().apply {
            entries.forEach {
                put(
                    JSONObject()
                        .put("file_name", it.fileName)
                        .put("kind", it.kind)
                        .put("project_id", it.projectId)
                        .put("version_id", it.versionId)
                        .put("version_number", it.versionNumber)
                        .put("title", it.title ?: "")
                        .put("icon_url", it.iconUrl ?: "")
                        .put("sha1", it.sha1 ?: "")
                        .put("explicit", it.explicit),
                )
            }
        },
    )

    companion object {
        fun fromJson(root: JSONObject): ContentManifest {
            val arr = root.optJSONArray("entries") ?: return ContentManifest()
            val list = ArrayList<InstalledEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                list += InstalledEntry(
                    fileName = e.optString("file_name"),
                    kind = e.optString("kind", "mod"),
                    projectId = e.optString("project_id"),
                    versionId = e.optString("version_id"),
                    versionNumber = e.optString("version_number"),
                    title = e.optString("title").takeIf { it.isNotEmpty() },
                    iconUrl = e.optString("icon_url").takeIf { it.isNotEmpty() },
                    sha1 = e.optString("sha1").takeIf { it.isNotEmpty() },
                    explicit = e.optBoolean("explicit", true),
                )
            }
            return ContentManifest(list)
        }
    }
}

/** A dependency candidate shown in the install dialog. */
data class DepCandidate(
    val project: MrProject,
    val version: MrVersion,
    val required: Boolean,     // required deps cannot be un-checked
    val alreadyInstalled: Boolean,
    val parentTitle: String?,  // who pulled this one in (null = the mod being installed)
)

/**
 * Plans an install of [version] of [project] into an instance: resolves Modrinth
 * dependencies recursively and returns the flat list the user confirms.
 * Required dependencies are locked (`required = true`); optional ones are
 * pre-checked at the caller's discretion.
 */
class DepResolver(private val api: ModrinthApi, private val installedProjectIds: Set<String>) {

    suspend fun resolve(project: MrProject, version: MrVersion, includeOptionalsByDefault: Boolean = true): List<DepCandidate> {
        val out = LinkedHashMap<String, DepCandidate>()
        val known = HashSet<String>()
        known += project.id

        // breadth-first dependency walk; required deps always followed,
        // optional deps only when the user's default says so (top level only)
        data class Task(val dep: MrDependency, val parentTitle: String?, val followOptional: Boolean)
        val queue = ArrayDeque<Task>()
        version.dependencies.forEach { queue.add(Task(it, null, followOptional = true)) }

        var guard = 0
        while (queue.isNotEmpty() && guard++ < 128) {
            val task = queue.removeFirst()
            val dep = task.dep
            if (dep.dependencyType == "incompatible" || dep.dependencyType == "embedded") continue
            val pid = dep.projectId ?: dep.versionId?.let { runCatching { api.version(it).projectId }.getOrNull() } ?: continue
            if (!known.add(pid)) continue
            val depVersion = if (dep.versionId != null) {
                runCatching { api.version(dep.versionId) }.getOrNull() ?: continue
            } else {
                pickVersion(pid) ?: continue
            }
            val depProject = runCatching { api.project(pid) }.getOrNull() ?: continue
            val required = dep.dependencyType == "required"
            val alreadyIn = installedProjectIds.contains(pid)
            out[pid] = DepCandidate(
                project = depProject,
                version = depVersion,
                required = required,
                alreadyInstalled = alreadyIn,
                parentTitle = task.parentTitle,
            )
            // follow deeper deps: required always, optionals only from required parents
            depVersion.dependencies.forEach { child ->
                val followOptional = task.followOptional && required
                if (child.dependencyType == "required" || (child.dependencyType == "optional" && followOptional)) {
                    queue.add(Task(child, depProject.title, followOptional))
                }
            }
        }
        return out.values.toList()
    }

    private suspend fun pickVersion(projectId: String): MrVersion? {
        val all = runCatching { api.versions(projectId) }.getOrNull() ?: return null
        return all.firstOrNull { it.versionType == "release" } ?: all.firstOrNull { it.versionType == "beta" } ?: all.firstOrNull()
    }
}

/**
 * Manages the mods / resource packs / shader packs of one instance (a version
 * directory) and remembers where each file came from (`obsi-content.json`),
 * so we can show real names, resolve dependencies and lock prerequisite files.
 */
class ContentManager(private val versionsRoot: File) {

    fun manifestFile(versionId: String): File = File(versionDir(versionId), "obsi-content.json")

    fun versionDir(versionId: String): File = File(versionsRoot, versionId)

    fun gameDir(versionId: String): File {
        // v1.13.0 — mirror the launch path's rule (LaunchPipeline/GameManager):
        // when the instance keeps its game files in a "game" subfolder, the
        // mods the GAME sees live in game/mods — the old code always listed
        // versions/<id>/mods, so installed mods never showed up
        return File(versionDir(versionId), "game").takeIf { it.isDirectory } ?: versionDir(versionId)
    }

    fun readManifest(versionId: String): ContentManifest = runCatching {
        val f = manifestFile(versionId)
        if (f.isFile) ContentManifest.fromJson(JSONObject(f.readText())) else ContentManifest()
    }.getOrDefault(ContentManifest())

    @Synchronized
    fun writeManifest(versionId: String, m: ContentManifest) {
        manifestFile(versionId).writeText(m.toJson().toString(2))
    }

    fun folderFor(versionId: String, kind: ProjectKind): File {
        val folder = kind.folder ?: throw IllegalArgumentException("modpacks are installed as whole instances")
        return File(gameDir(versionId), folder)
    }

    /** The Modrinth `loaders` values whose projects can run in [versionId]. */
    fun compatibleLoaders(versionId: String): List<String> {
        val json = File(versionDir(versionId), "$versionId.json")
        val id = versionId.lowercase()
        val loader = when {
            id.contains("fabric") -> "fabric"
            id.contains("quilt") -> "quilt"
            id.contains("forge") -> "forge"
            id.contains("neoforge") -> "neoforge"
            id.contains("optifine") -> "vanilla"
            json.isFile && runCatching {
                JSONObject(json.readText()).optJSONArray("libraries")?.let { libs ->
                    (0 until libs.length()).any { libs.getJSONObject(it).optString("name").contains("fabric-loader") }
                } == true
            }.getOrDefault(false) -> "fabric"
            else -> "vanilla"
        }
        return when (loader) {
            "vanilla" -> emptyList()
            "quilt" -> listOf("quilt", "fabric")
            else -> listOf(loader)
        }
    }

    fun mcVersionOf(versionId: String): String {
        // loader version ids are "<mc>-fabric-<v>" / "<mc>-forge-<v>" / "<mc>-OptiFine_<v>" / neoforge-<mcminor>.x
        val direct = Regex("""^(\d+(?:\.\d+)*[a-z]?)""").find(versionId)?.groupValues?.get(1)
        if (versionId.matches(Regex("""\d+(\.\d+)*([a-z]|-rc\d+|-pre\d+)?"""))) return versionId
        val dash = versionId.substringBefore("-fabric").substringBefore("-forge").substringBefore("-OptiFine")
        if (dash != versionId && dash.isNotBlank()) return dash
        if (versionId.startsWith("neoforge-")) {
            val rest = versionId.removePrefix("neoforge-")
            val parts = rest.split('.')
            if (parts.size >= 2) return "1.${parts[0]}.${parts[1].substringBefore('.')}"
        }
        return direct ?: versionId
    }

    // ---------------------------------------------------------------------------- listing

    fun list(versionId: String, kind: ProjectKind): List<ContentFile> {
        val dir = folderFor(versionId, kind)
        if (!dir.isDirectory) return emptyList()
        val manifest = readManifest(versionId).entries.associateBy { it.fileName.removeSuffix(".disabled") }
        return dir.listFiles { f -> f.isFile }?.map { it.name }
            ?.filter { n -> val b = n.removeSuffix(".disabled"); b.endsWith(".jar") || b.endsWith(".zip") || b.endsWith(".mrpack") }
            ?.map { name ->
                val enabled = !name.endsWith(".disabled")
                ContentFile(kind, name, enabled, File(dir, name).length(), manifest[name.removeSuffix(".disabled")])
            }
            ?.sortedBy { (it.entry?.title ?: it.fileName).lowercase() }
            .orEmpty()
    }

    @Synchronized
    fun setEnabled(versionId: String, file: ContentFile, enabled: Boolean) {
        val dir = folderFor(versionId, file.kind)
        val from = File(dir, file.fileName)
        val base = file.fileName.removeSuffix(".disabled")
        val to = File(dir, if (enabled) base else "$base.disabled")
        if (from.absolutePath != to.absolutePath && from.exists()) from.renameTo(to)
        // keep the manifest key aligned with the new name
        val m = readManifest(versionId)
        val changed = m.entries.map { if (it.fileName.removeSuffix(".disabled") == base) it.copy(fileName = to.name.removeSuffix(".disabled")) else it }
        if (changed != m.entries) writeManifest(versionId, ContentManifest(changed))
    }

    /** Mods that depend on [file]'s project and are currently installed. */
    fun requiredBy(versionId: String, file: ContentFile): List<String> {
        val pid = file.entry?.projectId ?: return emptyList()
        val dependents = ArrayList<String>()
        val manifest = readManifest(versionId)
        for (entry in manifest.entries) {
            if (entry.projectId == pid) continue
            val deps = cachedDependencies[entry.versionId]
            if (deps != null && deps.any { it.projectId == pid && (it.dependencyType == "required" || it.dependencyType == "optional") }) {
                dependents += entry.title ?: entry.fileName
            }
        }
        return dependents
    }

    /** Remembers dependency lists per installed version id so [requiredBy] works offline. */
    val cachedDependencies = HashMap<String, List<MrDependency>>()

    fun rememberDependencies(versionId: String, deps: List<MrDependency>) {
        cachedDependencies[versionId] = deps
        persistDependencyCache(versionId)
    }

    fun loadDependencyCache(versionId: String) {
        val f = File(versionDir(versionId), "obsi-depcache.json")
        if (!f.isFile) return
        runCatching {
            val root = JSONObject(f.readText())
            for (key in root.keys()) {
                val arr = root.getJSONArray(key)
                val list = ArrayList<MrDependency>(arr.length())
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    list += MrDependency(
                        projectId = d.optString("project_id").takeIf { it.isNotEmpty() },
                        versionId = d.optString("version_id").takeIf { it.isNotEmpty() },
                        dependencyType = d.optString("dependency_type", "optional"),
                    )
                }
                cachedDependencies[key] = list
            }
        }
    }

    private fun persistDependencyCache(versionId: String) {
        val root = JSONObject()
        cachedDependencies.forEach { (vid, deps) ->
            root.put(vid, JSONArray().apply {
                deps.forEach {
                    put(JSONObject().put("project_id", it.projectId ?: "").put("version_id", it.versionId ?: "").put("dependency_type", it.dependencyType))
                }
            })
        }
        File(versionDir(versionId), "obsi-depcache.json").writeText(root.toString())
    }

    @Synchronized
    fun remove(versionId: String, file: ContentFile) {
        val dir = folderFor(versionId, file.kind)
        File(dir, file.fileName).delete()
        val base = file.fileName.removeSuffix(".disabled")
        writeManifest(versionId, ContentManifest(readManifest(versionId).entries.filterNot { it.fileName.removeSuffix(".disabled") == base }))
    }

    /** All installed project ids of an instance (any kind) — used by the dep resolver. */
    fun installedProjectIds(versionId: String): Set<String> =
        readManifest(versionId).entries.map { it.projectId }.toSet()

    /** Replace the manifest entry + file for a project (used by updates). */
    @Synchronized
    fun recordInstalled(versionId: String, kind: ProjectKind, projectId: String, projectTitle: String?, iconUrl: String?, version: MrVersion, explicit: Boolean) {
        val dir = folderFor(versionId, kind)
        val safeName = version.fileName.substringAfterLast('/').substringAfterLast('\\')
        val old = readManifest(versionId).entries
        old.filter { it.projectId == projectId && it.fileName.removeSuffix(".disabled") != safeName }.forEach {
            File(dir, it.fileName).delete()
            File(dir, it.fileName.removeSuffix(".disabled") + ".disabled").delete()
        }
        val keep = old.filterNot { it.projectId == projectId || it.fileName.removeSuffix(".disabled") == safeName }
        writeManifest(
            versionId,
            ContentManifest(
                keep + InstalledEntry(
                    fileName = safeName,
                    kind = kind.apiType,
                    projectId = projectId,
                    versionId = version.id,
                    versionNumber = version.versionNumber,
                    title = projectTitle,
                    iconUrl = iconUrl,
                    sha1 = version.fileSha1,
                    explicit = explicit,
                ),
            ),
        )
        rememberDependencies(versionId, version.dependencies)
    }
}
