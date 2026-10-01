package studio.obsifox.launcher.core.modrinth

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.util.RemoteJson

enum class SortIndex(val api: String) { RELEVANCE("relevance"), DOWNLOADS("downloads"), FOLLOWS("follows"), NEWEST("newest"), UPDATED("updated") }

/** Client for the public Modrinth API v2 (https://docs.modrinth.com). */
class ModrinthApi(private val http: Http, private val base: String = "https://api.modrinth.com/v2") {

    suspend fun search(
        query: String,
        kind: ProjectKind = ProjectKind.MOD,
        gameVersion: String? = null,
        loaders: List<String> = emptyList(),
        categories: List<String> = emptyList(),
        sort: SortIndex = SortIndex.RELEVANCE,
        limit: Int = 20,
        offset: Int = 0,
    ): SearchResponse {
        val facets = buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive("project_type:${kind.apiType}")) })
            if (gameVersion != null) add(buildJsonArray { add(JsonPrimitive("versions:$gameVersion")) })
            if (loaders.isNotEmpty() && (kind == ProjectKind.MOD || kind == ProjectKind.MODPACK)) {
                add(buildJsonArray { loaders.forEach { add(JsonPrimitive("categories:$it")) } })
            }
            categories.forEach { c -> add(buildJsonArray { add(JsonPrimitive("categories:$c")) }) }
        }
        val url = "$base/search?query=${Http.enc(query)}&facets=${Http.enc(facets.toString())}" +
            "&index=${sort.api}&limit=$limit&offset=$offset"
        return RemoteJson.decodeFromString(SearchResponse.serializer(), http.getString(url))
    }

    suspend fun project(idOrSlug: String): Project =
        RemoteJson.decodeFromString(Project.serializer(), http.getString("$base/project/${Http.enc(idOrSlug)}"))

    suspend fun projects(ids: Collection<String>): List<Project> {
        if (ids.isEmpty()) return emptyList()
        val arr = buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }.toString()
        return RemoteJson.decodeFromString(ListSerializer(Project.serializer()), http.getString("$base/projects?ids=${Http.enc(arr)}"))
    }

    suspend fun versions(projectIdOrSlug: String, loaders: List<String> = emptyList(), gameVersions: List<String> = emptyList()): List<MrVersion> {
        val q = buildList {
            if (loaders.isNotEmpty()) add("loaders=" + Http.enc(buildJsonArray { loaders.forEach { add(JsonPrimitive(it)) } }.toString()))
            if (gameVersions.isNotEmpty()) add("game_versions=" + Http.enc(buildJsonArray { gameVersions.forEach { add(JsonPrimitive(it)) } }.toString()))
        }.joinToString("&")
        val url = "$base/project/${Http.enc(projectIdOrSlug)}/version" + if (q.isNotEmpty()) "?$q" else ""
        return RemoteJson.decodeFromString(ListSerializer(MrVersion.serializer()), http.getString(url))
    }

    suspend fun version(versionId: String): MrVersion =
        RemoteJson.decodeFromString(MrVersion.serializer(), http.getString("$base/version/${Http.enc(versionId)}"))

    suspend fun versionsByIds(ids: Collection<String>): List<MrVersion> {
        if (ids.isEmpty()) return emptyList()
        val arr = buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }.toString()
        return RemoteJson.decodeFromString(ListSerializer(MrVersion.serializer()), http.getString("$base/versions?ids=${Http.enc(arr)}"))
    }

    /** Identify files by SHA-1: hash -> the version it belongs to. */
    suspend fun versionsByHashes(sha1: Collection<String>): Map<String, MrVersion> {
        if (sha1.isEmpty()) return emptyMap()
        val body = buildJsonObject {
            putJsonArray("hashes") { sha1.forEach { add(JsonPrimitive(it)) } }
            put("algorithm", "sha1")
        }.toString()
        return RemoteJson.decodeFromString(MapSerializer(String.serializer(), MrVersion.serializer()), http.postJson("$base/version_files", body))
    }

    /** For each installed file hash: the newest compatible version (which may be the same one). */
    suspend fun latestForHashes(sha1: Collection<String>, loaders: List<String>, gameVersions: List<String>): Map<String, MrVersion> {
        if (sha1.isEmpty()) return emptyMap()
        val body = buildJsonObject {
            putJsonArray("hashes") { sha1.forEach { add(JsonPrimitive(it)) } }
            put("algorithm", "sha1")
            putJsonArray("loaders") { loaders.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("game_versions") { gameVersions.forEach { add(JsonPrimitive(it)) } }
        }.toString()
        return RemoteJson.decodeFromString(MapSerializer(String.serializer(), MrVersion.serializer()), http.postJson("$base/version_files/update", body))
    }
}
