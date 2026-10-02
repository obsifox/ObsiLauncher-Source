package studio.obsifox.launcher.core.wallpaper

import studio.obsifox.launcher.core.mojang.ManifestEntry
import java.time.Instant
import java.time.OffsetDateTime

/** A dotted Minecraft release number such as `1.21.4` or `26.3`, compared numerically. */
data class McVersion(val parts: List<Int>) : Comparable<McVersion> {
    override fun compareTo(other: McVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val a = parts.getOrElse(i) { 0 }
            val b = other.parts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    override fun toString() = parts.joinToString(".")

    companion object {
        private val RELEASE = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?$""")
        private val LEADING = Regex("""^(\d+\.\d+(?:\.\d+)?)-(?:pre|rc|snapshot)[-\d]*$""")

        /** `1.21.4`, `1.21`, `26.1` ... ; null for snapshots, betas and anything else. */
        fun parse(id: String): McVersion? =
            RELEASE.matchEntire(id.trim())?.groupValues?.drop(1)?.filter { it.isNotEmpty() }?.map { it.toInt() }?.let(::McVersion)

        /** Also understands `1.21.5-pre1`, `1.21.5-rc2` and `26.4-snapshot-2` (the release they lead up to). */
        fun parseLoose(id: String): McVersion? = parse(id) ?: LEADING.matchEntire(id.trim())?.let { parse(it.groupValues[1]) }
    }
}

/** One official wallpaper download from minecraft.net. [first] = first release the art belongs to (null = generic bundle). */
data class WallpaperPack(val id: String, val title: String, val file: String, val first: McVersion?) {
    val url: String get() = WallpaperCatalog.BASE + file
}

/**
 * Which official wallpaper belongs to which Minecraft version.
 *
 *  * releases **older than 1.12.2** (and old alpha/beta/classic builds) use the generic Java Edition bundle;
 *  * **1.12.2 and newer** use the artwork of their named update ("Update Aquatic", "Tricky Trials", "Chaos Cubed" ...);
 *    updates without a wallpaper of their own (e.g. 1.20.3 "Bats and Pots") inherit the previous one;
 *  * snapshots / pre-releases use the art of the release they lead up to; versions newer than anything we know get the latest art.
 *
 * Nothing is bundled with ObsiLauncher: the ZIPs are downloaded from minecraft.net on demand (see [WallpaperStore]).
 */
object WallpaperCatalog {
    const val BASE = "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/software/"

    /** The official page that lists these downloads. */
    const val PAGE = "https://www.minecraft.net/en-us/collectibles"

    private fun v(s: String) = McVersion.parse(s)!!

    val genericBundle = WallpaperPack("pc_bundle", "Minecraft", "wallpapers_minecraft_pc_bundle.zip", null)

    val PER_VERSION_FROM: McVersion = v("1.12.2")

    /** Ascending by [WallpaperPack.first]. */
    val packs: List<WallpaperPack> = listOf(
        WallpaperPack("world_of_color", "World of Color", "wallpapers_minecraft_world_color.zip", v("1.12.2")),
        WallpaperPack("update_aquatic", "Update Aquatic", "wallpapers_minecraft_update_aquatic.zip", v("1.13")),
        WallpaperPack("village_pillage", "Village & Pillage", "wallpapers_minecraft_village_pillage.zip", v("1.14")),
        WallpaperPack("buzzy_bees", "Buzzy Bees", "wallpapers_minecraft_buzzybees.zip", v("1.15")),
        WallpaperPack("nether_update", "Nether Update", "wallpapers_minecraft_nether_update.zip", v("1.16")),
        WallpaperPack("caves_cliffs_1", "Caves & Cliffs: Part I", "wallpapers_minecraft_caves_cliffs%28part1%29.zip", v("1.17")),
        WallpaperPack("caves_cliffs_2", "Caves & Cliffs: Part II", "wallpapers_minecraft_caves_cliffs%28part2%29.zip", v("1.18")),
        WallpaperPack("wild_update", "The Wild Update", "wallpaper_minecraft_wild_update.zip", v("1.19")),
        WallpaperPack("trails_and_tales", "Trails & Tales", "Minecraft_Trails_and_Tales_.Net.zip", v("1.20")),
        WallpaperPack("tricky_trials", "Tricky Trials", "wallpapers_tricky_trials_update2.zip", v("1.21")),
        WallpaperPack("bundles_of_bravery", "Bundles of Bravery", "wallpapers_bundles_of_bravery.zip", v("1.21.2")),
        WallpaperPack("garden_awakens", "The Garden Awakens", "wallpapers_the_garden_awakens_update.zip", v("1.21.4")),
        WallpaperPack("spring_to_life", "Spring to Life", "wallpapers_spring_to_life_update.zip", v("1.21.5")),
        WallpaperPack("chase_the_skies", "Chase the Skies", "wallpapers_chase_the_skies_update.zip", v("1.21.6")),
        WallpaperPack("copper_age", "The Copper Age", "wallpapers_the_copper_age_drop-1.zip", v("1.21.9")),
        WallpaperPack("mounts_of_mayhem", "Mounts of Mayhem", "wallpapers_mounts_of_mayhem_drop.zip", v("1.21.11")),
        WallpaperPack("tiny_takeover", "Tiny Takeover", "wallpapers_tiny_takeover_drop.zip", v("26.1")),
        WallpaperPack("chaos_cubed", "Chaos Cubed", "wallpapers_chaos_cubed_drop.zip", v("26.2")),
        WallpaperPack("wilderness_bound", "Wilderness Bound", "wallpapers_wilderness_bound_drop.zip", v("26.3")),
    )

    /** Art of the newest update we know about. */
    val latest: WallpaperPack get() = packs.last()

    fun byId(id: String): WallpaperPack? = if (id == genericBundle.id) genericBundle else packs.firstOrNull { it.id == id }

    fun forRelease(version: McVersion): WallpaperPack =
        if (version < PER_VERSION_FROM) genericBundle else packs.last { it.first!! <= version }

    /**
     * @param id a Minecraft version id (`1.21.4`, `25w10a`, `1.21.5-pre1`, `b1.7.3` ...)
     * @param entries the Mojang version manifest (used to place snapshots between two releases); may be empty when offline
     */
    fun forVersion(id: String, entries: List<ManifestEntry> = emptyList()): WallpaperPack {
        McVersion.parse(id)?.let { return forRelease(it) }
        if (isPreHistoric(id)) return genericBundle

        val entry = entries.firstOrNull { it.id == id }
        val at = entry?.releaseTime?.let(::parseTime)
        if (at != null) {
            val next = entries.asSequence().filter { it.type == "release" }
                .mapNotNull { e -> parseTime(e.releaseTime)?.let { e to it } }
                .filter { it.second >= at }
                .minByOrNull { it.second }
            val leadsTo = next?.first?.id?.let { McVersion.parse(it) }
            return if (leadsTo != null) forRelease(leadsTo) else latest // newer than every release: the next update has no art yet
        }
        McVersion.parseLoose(id)?.let { return forRelease(it) }
        return latest
    }

    private fun isPreHistoric(id: String): Boolean =
        id.startsWith("rd-") || id.startsWith("inf-") || (id.length > 1 && id[0] in "abc" && id[1].isDigit())

    private fun parseTime(s: String?): Instant? = s?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
}
