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

        // Libraries: evaluate OS rules first, then let a child version override the parent's library with the same
        // coordinates (e.g. a loader shipping a newer guava). Entries of the SAME version file are never merged:
        // Mojang lists LWJGL 3.2.x several times per module (a mac-only 3.2.1 jar, the 3.2.2 jar, and one entry
        // carrying the natives), and they all have to survive.
        val overridden = HashSet<String>()
        val levels = ArrayList<List<Library>>()
        for (v in leafFirst) {
            val level = ArrayList<Library>()
            val keys = HashSet<String>()
            for (l in v.libraries) {
                if (!Rules.allowed(l.rules)) continue
                val k = libraryKey(l)
                if (k in overridden) continue
                level += l
                keys += k
            }
            overridden += keys
            levels += level
        }
        val ordered = levels.asReversed().flatten() // parents first, like the official launcher

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

    /** Identity of a library for the child-overrides-parent rule: group:artifact[:classifier], natives entries kept apart. */
    fun libraryKey(l: Library): String = Maven.key(l.name) + if (l.natives != null) "#natives" else ""
}
