package studio.obsifox.launcher.core.modrinth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What kind of Modrinth project (and which folder it lands in). */
enum class ProjectKind(val apiType: String, val folder: String?) {
    MOD("mod", "mods"),
    MODPACK("modpack", null),
    RESOURCEPACK("resourcepack", "resourcepacks"),
    SHADER("shader", "shaderpacks"),
    DATAPACK("datapack", "datapacks");

    companion object {
        fun fromApi(s: String?): ProjectKind = entries.firstOrNull { it.apiType == s } ?: MOD
    }
}

@Serializable
data class SearchResponse(
    val hits: List<ProjectHit> = emptyList(),
    val offset: Int = 0,
    val limit: Int = 0,
    @SerialName("total_hits") val totalHits: Int = 0,
)

@Serializable
data class ProjectHit(
    @SerialName("project_id") val projectId: String,
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val author: String = "",
    @SerialName("project_type") val projectType: String = "mod",
    val downloads: Long = 0,
    val follows: Int = 0,
    @SerialName("icon_url") val iconUrl: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("display_categories") val displayCategories: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
    @SerialName("client_side") val clientSide: String? = null,
    @SerialName("server_side") val serverSide: String? = null,
    @SerialName("date_modified") val dateModified: String? = null,
)

@Serializable
data class License(val id: String? = null, val name: String? = null)

@Serializable
data class Project(
    val id: String,
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val body: String = "",
    @SerialName("project_type") val projectType: String = "mod",
    val downloads: Long = 0,
    val followers: Int = 0,
    @SerialName("icon_url") val iconUrl: String? = null,
    val categories: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    @SerialName("client_side") val clientSide: String? = null,
    @SerialName("server_side") val serverSide: String? = null,
    val license: License? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("issues_url") val issuesUrl: String? = null,
    @SerialName("wiki_url") val wikiUrl: String? = null,
    @SerialName("discord_url") val discordUrl: String? = null,
    val updated: String? = null,
)

@Serializable
data class MrFile(
    val hashes: Map<String, String> = emptyMap(),
    val url: String,
    val filename: String,
    val primary: Boolean = false,
    val size: Long = 0,
)

@Serializable
data class MrDependency(
    @SerialName("version_id") val versionId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("dependency_type") val dependencyType: String = "required",
)

@Serializable
data class MrVersion(
    val id: String,
    @SerialName("project_id") val projectId: String,
    val name: String = "",
    @SerialName("version_number") val versionNumber: String = "",
    val changelog: String? = null,
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    @SerialName("version_type") val versionType: String = "release",
    @SerialName("date_published") val datePublished: String = "",
    val downloads: Long = 0,
    val files: List<MrFile> = emptyList(),
    val dependencies: List<MrDependency> = emptyList(),
) {
    val primaryFile: MrFile? get() = files.firstOrNull { it.primary } ?: files.firstOrNull()
}

/** One entry of an instance's `obsi-content.json`. */
@Serializable
data class InstalledEntry(
    val fileName: String,
    val kind: String = "mod",
    val projectId: String? = null,
    val versionId: String? = null,
    val versionNumber: String? = null,
    val title: String? = null,
    val iconUrl: String? = null,
    val sha1: String? = null,
    /** false when it was only pulled in as a dependency */
    val explicit: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class ContentManifest(val entries: List<InstalledEntry> = emptyList())

/** A file found on disk, joined with what we know about it. */
data class ContentFile(
    val kind: ProjectKind,
    val fileName: String,
    val enabled: Boolean,
    val sizeBytes: Long,
    val entry: InstalledEntry?,
) {
    val displayName: String get() = entry?.title ?: fileName.removeSuffix(".disabled")
}

// ---- .mrpack
@Serializable
data class PackFileEnv(val client: String = "required", val server: String = "required")

@Serializable
data class PackFile(
    val path: String,
    val hashes: Map<String, String> = emptyMap(),
    val env: PackFileEnv? = null,
    val downloads: List<String> = emptyList(),
    val fileSize: Long = 0,
)

@Serializable
data class PackIndex(
    val formatVersion: Int = 1,
    val game: String = "minecraft",
    val versionId: String = "",
    val name: String = "",
    val summary: String? = null,
    val files: List<PackFile> = emptyList(),
    val dependencies: Map<String, String> = emptyMap(),
)
