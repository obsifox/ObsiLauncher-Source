package studio.obsifox.launcher.core.mojang

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class VersionManifest(val latest: Latest = Latest(), val versions: List<ManifestEntry> = emptyList())

@Serializable
data class Latest(val release: String = "", val snapshot: String = "")

@Serializable
data class ManifestEntry(
    val id: String,
    val type: String = "release",
    val url: String = "",
    val sha1: String? = null,
    val releaseTime: String? = null,
    val time: String? = null,
)

/** Used for client/server downloads and library artifacts alike. */
@Serializable
data class DownloadInfo(
    val url: String? = null,
    val sha1: String? = null,
    val size: Long? = null,
    val path: String? = null,
)

@Serializable
data class AssetIndexRef(
    val id: String,
    val sha1: String? = null,
    val size: Long? = null,
    val totalSize: Long? = null,
    val url: String? = null,
)

@Serializable
data class JavaVersionInfo(val component: String? = null, val majorVersion: Int? = null)

@Serializable
data class OsRule(val name: String? = null, val version: String? = null, val arch: String? = null)

@Serializable
data class Rule(val action: String = "allow", val os: OsRule? = null, val features: Map<String, Boolean>? = null)

@Serializable
data class ExtractRule(val exclude: List<String> = emptyList())

@Serializable
data class LibDownloads(val artifact: DownloadInfo? = null, val classifiers: Map<String, DownloadInfo>? = null)

@Serializable
data class Library(
    val name: String,
    val downloads: LibDownloads? = null,
    val url: String? = null,
    val rules: List<Rule>? = null,
    val natives: Map<String, String>? = null,
    val extract: ExtractRule? = null,
    val sha1: String? = null,
    val size: Long? = null,
)

@Serializable
data class LogFile(val id: String? = null, val sha1: String? = null, val size: Long? = null, val url: String? = null)

@Serializable
data class LogClient(val argument: String? = null, val file: LogFile? = null, val type: String? = null)

@Serializable
data class LoggingCfg(val client: LogClient? = null)

@Serializable
data class ArgumentsBlock(val game: List<JsonElement> = emptyList(), val jvm: List<JsonElement> = emptyList())

@Serializable
data class VersionJson(
    val id: String,
    val inheritsFrom: String? = null,
    val jar: String? = null,
    val type: String? = null,
    val mainClass: String? = null,
    val minecraftArguments: String? = null,
    val arguments: ArgumentsBlock? = null,
    val assetIndex: AssetIndexRef? = null,
    val assets: String? = null,
    val downloads: Map<String, DownloadInfo>? = null,
    val javaVersion: JavaVersionInfo? = null,
    val libraries: List<Library> = emptyList(),
    val logging: LoggingCfg? = null,
    val releaseTime: String? = null,
    val time: String? = null,
)

@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject> = emptyMap(),
    val virtual: Boolean = false,
    @SerialName("map_to_resources") val mapToResources: Boolean = false,
)

@Serializable
data class AssetObject(val hash: String, val size: Long = 0)
