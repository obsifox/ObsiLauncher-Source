package studio.obsifox.launcher.core.instance

import kotlinx.serialization.Serializable
import studio.obsifox.launcher.core.net.NetConfig

@Serializable
enum class LoaderType(val display: String) {
    VANILLA("Vanilla"), FABRIC("Fabric"), QUILT("Quilt"), FORGE("Forge"), NEOFORGE("NeoForge");

    /** Modrinth `loaders` facet value. */
    val modrinthId: String? get() = when (this) { VANILLA -> null; else -> name.lowercase() }
}

@Serializable
data class ModpackInfo(
    val source: String = "modrinth",
    val projectId: String? = null,
    val versionId: String? = null,
    val versionNumber: String? = null,
    val name: String? = null,
)

@Serializable
data class Instance(
    val id: String,
    val name: String,
    val mcVersion: String,
    val loader: LoaderType = LoaderType.VANILLA,
    val loaderVersion: String? = null,
    /** Version id under shared/versions/ that is actually launched (set once the loader is installed). */
    val launchVersionId: String? = null,
    val javaPath: String? = null,
    val minMemoryMb: Int? = null,
    val maxMemoryMb: Int? = null,
    val jvmArgs: String = "",
    val gameArgs: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val fullscreen: Boolean = false,
    val notes: String = "",
    val iconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0,
    val playTimeSeconds: Long = 0,
    val modpack: ModpackInfo? = null,
    /** Join this server directly on launch (host[:port]). */
    val autoJoinServer: String? = null,
) {
    val subtitle: String
        get() = if (loader == LoaderType.VANILLA) mcVersion else "${loader.display} · $mcVersion"
}

@Serializable
data class Settings(
    /** "auto" | "en" | "fa" */
    val language: String = "auto",
    val defaultMinMemoryMb: Int = 512,
    /** 0 = pick automatically from installed RAM */
    val defaultMaxMemoryMb: Int = 0,
    val defaultJvmArgs: String = "",
    val javaPath: String? = null,
    val net: NetConfig = NetConfig(),
    val downloadThreads: Int = 16,
    /** Azure application (client) id used for Microsoft sign-in. Empty = use the one baked into the build. */
    val msClientId: String? = null,
    val hideLauncherWhileRunning: Boolean = false,
    val openConsoleOnLaunch: Boolean = true,
    val selectedAccountId: String? = null,
    val selectedInstanceId: String? = null,
)
