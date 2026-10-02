package studio.obsifox.obsilauncher.core.modrinth

import org.json.JSONArray
import org.json.JSONObject
import studio.obsifox.obsilauncher.core.net.Http

/** Modrinth project kinds the launcher can install. */
enum class ProjectKind(val apiType: String, val folder: String?) {
    MOD("mod", "mods"),
    MODPACK("modpack", null),
    RESOURCEPACK("resourcepack", "resourcepacks"),
    SHADER("shader", "shaderpacks");

    companion object {
        fun fromApi(type: String): ProjectKind =
            entries.firstOrNull { it.apiType == type } ?: MOD
        fun fromIndexPath(path: String): ProjectKind = when {
            path.startsWith("mods/") -> MOD
            path.startsWith("resourcepacks/") -> RESOURCEPACK
            path.startsWith("shaderpacks/") -> SHADER
            else -> MOD
        }
    }
}

data class SearchHit(
    val projectId: String,
    val slug: String,
    val title: String,
    val description: String,
    val categories: List<String>,
    val displayCategories: List<String>,
    val downloads: Long,
    val iconUrl: String,
    val author: String,
    val dateModified: String,
)

data class MrDependency(val projectId: String?, val versionId: String?, val dependencyType: String)

data class MrVersion(
    val id: String,
    val projectId: String,
    val versionNumber: String,
    val versionType: String,
    val loaders: List<String>,
    val gameVersions: List<String>,
    val datePublished: String,
    val dependencies: List<MrDependency>,
    val fileName: String,
    val fileUrl: String,
    val fileSha1: String?,
    val fileSize: Long,
    val changelog: String,
) {
    val primary: Boolean get() = fileUrl.isNotEmpty()
}

data class MrProject(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val body: String,
    val projectType: String,
    val categories: List<String>,
    val loaders: List<String>,
    val gameVersions: List<String>,
    val downloads: Long,
    val iconUrl: String,
    val sourceUrl: String,
)

data class SearchResponse(val hits: List<SearchHit>, val total: Int)

/** Client for the public Modrinth API v2 (https://docs.modrinth.com). No key needed. */
class ModrinthApi(private val base: String = "https://api.modrinth.com/v2") {

    fun search(
        query: String,
        kind: ProjectKind = ProjectKind.MOD,
        gameVersion: String? = null,
        loaders: List<String> = emptyList(),
        sort: String = "relevance",
        limit: Int = 20,
        offset: Int = 0,
    ): SearchResponse {
        val facets = JSONArray()
            .put(JSONArray().put("project_type:${kind.apiType}"))
        if (gameVersion != null && gameVersion.isNotEmpty()) {
            facets.put(JSONArray().put("versions:$gameVersion"))
        }
        if (loaders.isNotEmpty() && (kind == ProjectKind.MOD || kind == ProjectKind.MODPACK)) {
            facets.put(JSONArray().apply { loaders.forEach { put("categories:$it") } })
        }
        val url = "$base/search?query=${Http.enc(query)}&facets=${Http.enc(facets.toString())}" +
            "&index=$sort&limit=$limit&offset=$offset"
        val root = JSONObject(Http.get(url) ?: return SearchResponse(emptyList(), 0))
        val hits = root.optJSONArray("hits") ?: JSONArray()
        val list = ArrayList<SearchHit>(hits.length())
        for (i in 0 until hits.length()) {
            val h = hits.getJSONObject(i)
            list += SearchHit(
                projectId = h.optString("project_id"),
                slug = h.optString("slug"),
                title = h.optString("title"),
                description = h.optString("description"),
                categories = h.strList("categories"),
                displayCategories = h.strList("display_categories"),
                downloads = h.optLong("downloads"),
                iconUrl = h.optString("icon_url"),
                author = h.optString("author"),
                dateModified = h.optString("date_modified"),
            )
        }
        return SearchResponse(list, root.optInt("total_hits", list.size))
    }

    fun project(idOrSlug: String): MrProject {
        val p = JSONObject(Http.get("$base/project/${Http.enc(idOrSlug)}") ?: throw IllegalStateException("project not found"))
        return MrProject(
            id = p.optString("id"),
            slug = p.optString("slug"),
            title = p.optString("title"),
            description = p.optString("description"),
            body = p.optString("body"),
            projectType = p.optString("project_type"),
            categories = p.strList("categories"),
            loaders = p.strList("loaders"),
            gameVersions = p.strList("game_versions"),
            downloads = p.optLong("downloads"),
            iconUrl = p.optString("icon_url"),
            sourceUrl = p.optString("source_url"),
        )
    }

    fun projects(ids: Collection<String>): List<MrProject> {
        if (ids.isEmpty()) return emptyList()
        val arr = JSONArray(); ids.forEach { arr.put(it) }
        val text = Http.get("$base/projects?ids=${Http.enc(arr.toString())}") ?: return emptyList()
        val root = JSONArray(text)
        val out = ArrayList<MrProject>(root.length())
        for (i in 0 until root.length()) {
            val p = root.getJSONObject(i)
            out += MrProject(
                id = p.optString("id"),
                slug = p.optString("slug"),
                title = p.optString("title"),
                description = p.optString("description"),
                body = p.optString("body"),
                projectType = p.optString("project_type"),
                categories = p.strList("categories"),
                loaders = p.strList("loaders"),
                gameVersions = p.strList("game_versions"),
                downloads = p.optLong("downloads"),
                iconUrl = p.optString("icon_url"),
                sourceUrl = p.optString("source_url"),
            )
        }
        return out
    }

    fun versions(projectIdOrSlug: String, loaders: List<String> = emptyList(), gameVersions: List<String> = emptyList()): List<MrVersion> {
        val q = buildList {
            if (loaders.isNotEmpty()) add("loaders=" + Http.enc(JSONArray().apply { loaders.forEach { put(it) } }.toString()))
            if (gameVersions.isNotEmpty()) add("game_versions=" + Http.enc(JSONArray().apply { gameVersions.forEach { put(it) } }.toString()))
        }.joinToString("&")
        val url = "$base/project/${Http.enc(projectIdOrSlug)}/version" + if (q.isNotEmpty()) "?$q" else ""
        val text = Http.get(url) ?: return emptyList()
        return parseVersions(JSONArray(text))
    }

    fun version(versionId: String): MrVersion =
        parseVersions(JSONArray().put(JSONObject(Http.get("$base/version/${Http.enc(versionId)}") ?: throw IllegalStateException("version not found")))).first()

    fun versionsByIds(ids: Collection<String>): List<MrVersion> {
        if (ids.isEmpty()) return emptyList()
        val arr = JSONArray(); ids.forEach { arr.put(it) }
        val text = Http.get("$base/versions?ids=${Http.enc(arr.toString())}") ?: return emptyList()
        return parseVersions(JSONArray(text))
    }

    /** Identify files by SHA-1: hash -> the version it belongs to. */
    fun versionsByHashes(sha1: Collection<String>): Map<String, MrVersion> {
        if (sha1.isEmpty()) return emptyMap()
        val body = JSONObject().put("hashes", JSONArray().apply { sha1.forEach { put(it) } }).put("algorithm", "sha1").toString()
        val text = Http.postJson("$base/version_files", body) ?: return emptyMap()
        val root = JSONObject(text)
        val out = HashMap<String, MrVersion>()
        for (key in root.keys()) {
            out[key] = versionFromJson(root.getJSONObject(key))
        }
        return out
    }

    private fun parseVersions(arr: JSONArray): List<MrVersion> {
        val out = ArrayList<MrVersion>(arr.length())
        for (i in 0 until arr.length()) out += versionFromJson(arr.getJSONObject(i))
        return out
    }

    private fun versionFromJson(v: JSONObject): MrVersion {
        val files = v.optJSONArray("files") ?: JSONArray()
        var fileName = ""; var fileUrl = ""; var fileSha1: String? = null; var fileSize = 0L
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            if (f.optBoolean("primary") || i == 0) {
                fileName = f.optString("filename")
                fileUrl = f.optString("url")
                fileSha1 = f.optJSONObject("hashes")?.optString("sha1")?.takeIf { it.length == 40 }
                fileSize = f.optLong("size")
                if (f.optBoolean("primary")) break
            }
        }
        val deps = v.optJSONArray("dependencies") ?: JSONArray()
        val depList = ArrayList<MrDependency>(deps.length())
        for (i in 0 until deps.length()) {
            val d = deps.getJSONObject(i)
            depList += MrDependency(
                projectId = d.optString("project_id").takeIf { it.isNotEmpty() },
                versionId = d.optString("version_id").takeIf { it.isNotEmpty() },
                dependencyType = d.optString("dependency_type", "optional"),
            )
        }
        return MrVersion(
            id = v.optString("id"),
            projectId = v.optString("project_id"),
            versionNumber = v.optString("version_number"),
            versionType = v.optString("version_type"),
            loaders = v.strList("loaders"),
            gameVersions = v.strList("game_versions"),
            datePublished = v.optString("date_published"),
            dependencies = depList,
            fileName = fileName,
            fileUrl = fileUrl,
            fileSha1 = fileSha1,
            fileSize = fileSize,
            changelog = v.optString("changelog"),
        )
    }

    private fun JSONObject.strList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { runCatching { arr.getString(it) }.getOrNull() }
    }
}
