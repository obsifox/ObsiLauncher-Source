package studio.obsifox.launcher.core.mojang

import kotlinx.serialization.json.JsonElement

/**
 * A version JSON with its `inheritsFrom` chain merged (Fabric/Forge/... on top of vanilla).
 */
class ResolvedVersion(
    /** Id of the version that is launched (the leaf of the chain). */
    val id: String,
    /** Id of the vanilla version at the root of the chain. */
    val rootId: String,
    val type: String,
    val mainClass: String,
    val assetIndex: AssetIndexRef,
    val clientDownload: DownloadInfo?,
    val javaComponent: String?,
    val javaMajor: Int?,
    /** Child-first, de-duplicated. Rules are NOT applied yet. */
    val libraries: List<Library>,
    val jvmArgs: List<JsonElement>,
    val gameArgs: List<JsonElement>,
    /** Pre-1.13 style `minecraftArguments`, if any. */
    val legacyGameArgs: String?,
    val logging: LogClient?,
    /** Root first, leaf last. */
    val chain: List<VersionJson>,
) {
    val inherited: Boolean get() = id != rootId
}

object VersionResolver {
    /** @param chain root first, leaf last */
    fun resolve(chain: List<VersionJson>): ResolvedVersion {
        require(chain.isNotEmpty())
        val leaf = chain.last()
        val root = chain.first()
        val leafFirst = chain.asReversed()

        val mainClass = leafFirst.firstNotNullOfOrNull { it.mainClass }
            ?: error("Version ${leaf.id} has no mainClass")
        val assetIndex = leafFirst.firstNotNullOfOrNull { it.assetIndex }
            ?: AssetIndexRef(id = leafFirst.firstNotNullOfOrNull { it.assets } ?: "legacy")
        val java = leafFirst.firstNotNullOfOrNull { it.javaVersion }

        val seen = HashSet<String>()
        val libs = ArrayList<Library>()
        for (v in leafFirst) for (l in v.libraries) if (seen.add(Maven.key(l.name))) libs += l
        // keep the original order within the chain: parent libs first is what the official launcher does for the classpath,
        // but child-first de-duplication already made the child's entries win. Re-sort so parents come first again.
        val order = chain.flatMap { v -> v.libraries.map { Maven.key(it.name) } }.distinct()
        val byKey = libs.associateBy { Maven.key(it.name) }
        val ordered = order.mapNotNull { byKey[it] }

        return ResolvedVersion(
            id = leaf.id,
            rootId = root.id,
            type = leaf.type ?: root.type ?: "release",
            mainClass = mainClass,
            assetIndex = assetIndex,
            clientDownload = leafFirst.firstNotNullOfOrNull { it.downloads?.get("client") },
            javaComponent = java?.component,
            javaMajor = java?.majorVersion,
            libraries = ordered,
            jvmArgs = chain.flatMap { it.arguments?.jvm.orEmpty() },
            gameArgs = chain.flatMap { it.arguments?.game.orEmpty() },
            legacyGameArgs = leafFirst.firstNotNullOfOrNull { it.minecraftArguments },
            logging = leafFirst.firstNotNullOfOrNull { v -> v.logging?.client?.takeIf { it.file?.url != null } },
            chain = chain,
        )
    }
}
