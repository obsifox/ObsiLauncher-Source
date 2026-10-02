package studio.obsifox.launcher.core

import kotlinx.coroutines.runBlocking
import studio.obsifox.launcher.core.mojang.ManifestEntry
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.update.RemoteBuild
import studio.obsifox.launcher.core.update.UpdateService
import studio.obsifox.launcher.core.update.UpdateStatus
import studio.obsifox.launcher.core.wallpaper.McVersion
import studio.obsifox.launcher.core.wallpaper.PaletteExtractor
import studio.obsifox.launcher.core.wallpaper.WallpaperCatalog
import studio.obsifox.launcher.core.wallpaper.WallpaperStore
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WallpaperTests {
    // ---------------------------------------------------------------------------------------------- catalog

    private fun pack(id: String) = WallpaperCatalog.forVersion(id).id

    @Test
    fun releasesBelow1122UseTheGenericBundle() {
        assertEquals("pc_bundle", pack("1.12.1"))
        assertEquals("pc_bundle", pack("1.12"))
        assertEquals("pc_bundle", pack("1.8.9"))
        assertEquals("pc_bundle", pack("1.7.10"))
        assertEquals("pc_bundle", pack("b1.7.3"))
        assertEquals("pc_bundle", pack("a1.2.6"))
        assertEquals("pc_bundle", pack("rd-132211"))
    }

    @Test
    fun releasesFrom1122UseTheArtOfTheirUpdate() {
        val expected = mapOf(
            "1.12.2" to "world_of_color", "1.13" to "update_aquatic", "1.13.2" to "update_aquatic", "1.14.4" to "village_pillage",
            "1.15.2" to "buzzy_bees", "1.16.5" to "nether_update", "1.17.1" to "caves_cliffs_1", "1.18.2" to "caves_cliffs_2",
            "1.19.4" to "wild_update", "1.20" to "trails_and_tales", "1.20.4" to "trails_and_tales", // Bats and Pots has no art of its own
            "1.20.6" to "trails_and_tales", "1.21" to "tricky_trials", "1.21.1" to "tricky_trials", "1.21.2" to "bundles_of_bravery",
            "1.21.3" to "bundles_of_bravery", "1.21.4" to "garden_awakens", "1.21.5" to "spring_to_life", "1.21.6" to "chase_the_skies",
            "1.21.8" to "chase_the_skies", "1.21.9" to "copper_age", "1.21.10" to "copper_age", "1.21.11" to "mounts_of_mayhem",
            "26.1" to "tiny_takeover", "26.2" to "chaos_cubed", "26.3" to "wilderness_bound", "26.9" to "wilderness_bound",
        )
        for ((v, id) in expected) assertEquals(id, pack(v), "wallpaper for $v")
    }

    @Test
    fun snapshotsGetTheArtOfTheReleaseTheyLeadTo() {
        val manifest = listOf(
            ManifestEntry("26.4-snapshot-2", "snapshot", releaseTime = "2026-10-01T10:00:00+00:00"),
            ManifestEntry("26.3", "release", releaseTime = "2026-09-15T10:00:00+00:00"),
            ManifestEntry("25w10a", "snapshot", releaseTime = "2025-03-05T10:00:00+00:00"),
            ManifestEntry("1.21.5", "release", releaseTime = "2025-03-25T10:00:00+00:00"),
            ManifestEntry("1.21.4", "release", releaseTime = "2024-12-03T10:00:00+00:00"),
            ManifestEntry("17w43a", "snapshot", releaseTime = "2017-10-25T10:00:00+00:00"),
            ManifestEntry("1.13", "release", releaseTime = "2018-07-18T10:00:00+00:00"),
            ManifestEntry("1.12.2", "release", releaseTime = "2017-09-18T10:00:00+00:00"),
            ManifestEntry("1.9-pre1", "snapshot", releaseTime = "2016-02-01T10:00:00+00:00"),
            ManifestEntry("1.9", "release", releaseTime = "2016-02-29T10:00:00+00:00"),
        )
        assertEquals("spring_to_life", WallpaperCatalog.forVersion("25w10a", manifest).id)
        assertEquals("update_aquatic", WallpaperCatalog.forVersion("17w43a", manifest).id)
        assertEquals("pc_bundle", WallpaperCatalog.forVersion("1.9-pre1", manifest).id)
        // newer than every release we know: the next update has no art yet -> newest art
        assertEquals("wilderness_bound", WallpaperCatalog.forVersion("26.4-snapshot-2", manifest).id)
        // offline (no manifest): pre-releases still resolve from their name
        assertEquals("spring_to_life", WallpaperCatalog.forVersion("1.21.5-pre1").id)
        assertEquals("spring_to_life", WallpaperCatalog.forVersion("1.21.5-rc2").id)
        assertEquals("wilderness_bound", WallpaperCatalog.forVersion("totally-unknown").id)
    }

    @Test
    fun catalogIsConsistent() {
        val firsts = WallpaperCatalog.packs.map { it.first!! }
        assertEquals(firsts.sorted(), firsts, "packs must be ascending")
        assertEquals(firsts.size, firsts.toSet().size)
        assertEquals("wilderness_bound", WallpaperCatalog.latest.id)
        assertTrue(WallpaperCatalog.packs.all { it.url.startsWith("https://www.minecraft.net/") && it.url.endsWith(".zip") })
        assertTrue(McVersion.parse("1.21.4")!! > McVersion.parse("1.21")!!)
        assertTrue(McVersion.parse("26.3")!! > McVersion.parse("1.21.11")!!)
        assertEquals(null, McVersion.parse("25w10a"))
    }

    // ---------------------------------------------------------------------------------------------- variant selection

    private fun meta(vararg dims: Pair<Int, Int>) = studio.obsifox.launcher.core.wallpaper.PackMeta(
        "t", "T", dims.map { studio.obsifox.launcher.core.wallpaper.Variant("${it.first}x${it.second}.jpg", it.first, it.second) },
        PaletteExtractor.fromImage(solid(8, 8, Color.ORANGE)),
    )

    @Test
    fun variantMatchesTheDisplayShape() {
        val m = meta(2560 to 1440, 1920 to 1080, 2058 to 1440, 2048 to 2048, 1080 to 1920)
        fun pick(w: Int, h: Int) = WallpaperStore.best(m, w, h).file
        assertEquals("1920x1080.jpg", pick(1920, 1080)) // exact fit beats a bigger image
        assertEquals("2560x1440.jpg", pick(2560, 1440))
        assertEquals("2560x1440.jpg", pick(3840, 2160)) // 4K screen: nothing is big enough, take the largest of the right shape
        assertEquals("1080x1920.jpg", pick(1080, 2340)) // phone-like portrait window
        assertEquals("2048x2048.jpg", pick(1000, 1000)) // square window
        assertEquals("2058x1440.jpg", pick(1440, 1000)) // 1.44 : 1
    }

    // ---------------------------------------------------------------------------------------------- palette

    private fun solid(w: Int, h: Int, c: Color): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics(); g.color = c; g.fillRect(0, 0, w, h); g.dispose()
        return img
    }

    private fun hueDistance(a: Float, b: Float): Float { val d = Math.abs(a - b) % 360f; return if (d > 180f) 360f - d else d }

    @Test
    fun paletteFollowsTheArtwork() {
        val orange = PaletteExtractor.fromImage(solid(200, 120, Color(230, 110, 30)))
        assertTrue(orange.vivid)
        assertTrue(hueDistance(orange.hue, 24f) < 8f, "orange hue was ${orange.hue}")

        val half = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        val g = half.createGraphics(); g.color = Color(10, 10, 12); g.fillRect(0, 0, 200, 100); g.color = Color(40, 110, 220); g.fillRect(0, 0, 90, 100); g.dispose()
        val blue = PaletteExtractor.fromImage(half)
        assertTrue(blue.vivid)
        assertTrue(hueDistance(blue.hue, 214f) < 10f, "blue hue was ${blue.hue}")
        assertTrue(blue.luminance < 0.4f)
        assertTrue(PaletteExtractor.distance(blue.accent, orange.accent) > 120, "different artwork must give different accents")

        val grey = PaletteExtractor.fromImage(solid(100, 100, Color(120, 120, 124)))
        assertFalse(grey.vivid, "grey art has no colour to borrow")
    }

    // ---------------------------------------------------------------------------------------------- unpacking

    private fun png(w: Int, h: Int, c: Color): ByteArray {
        val bo = ByteArrayOutputStream(); ImageIO.write(solid(w, h, c), "png", bo); return bo.toByteArray()
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return bo.toByteArray()
    }

    private fun store(tmp: Path) = WallpaperStore(Http("test"), LauncherPaths(tmp))

    @Test
    fun unpacksAFlatPackAndSkipsJunk() {
        val tmp = Files.createTempDirectory("obsi-wp")
        val file = tmp.resolve("pack.zip")
        Files.write(file, zip(
            "art_2560x1440.png" to png(2560, 1440, Color(220, 90, 30)),
            "art_1920x1080.png" to png(1920, 1080, Color(220, 90, 30)),
            "art_1080x1920.png" to png(1080, 1920, Color(220, 90, 30)),
            "art_414x414.png" to png(414, 414, Color(220, 90, 30)),       // thumbnail: must be dropped
            "__MACOSX/._art_2560x1440.png" to ByteArray(10),                // mac resource fork junk
            "readme.txt" to "hi".toByteArray(),
        ))
        val s = store(tmp)
        val meta = s.installFromZip(WallpaperCatalog.latest, file)
        assertEquals(listOf("2560x1440.jpg", "1920x1080.jpg", "1080x1920.jpg"), meta.variants.map { it.file })
        assertTrue(meta.variants.all { Files.size(s.file(meta, it)) > 0 })
        assertTrue(meta.palette.vivid)
        assertTrue(s.isInstalled(WallpaperCatalog.latest))
        assertEquals(setOf("wilderness_bound"), s.installed.value)
        // a second store over the same directory sees it (survives a restart)
        assertEquals(meta.variants, store(tmp).meta("wilderness_bound")!!.variants)
        assertFalse(Files.exists(s.root.resolve("wilderness_bound.tmp")))
    }

    @Test
    fun unpacksAZipInsideAZipAndPicksTheMainFolder() {
        val tmp = Files.createTempDirectory("obsi-wp")
        // "The Wild Update" style: ZIPs inside the ZIP, the biggest one is the main art
        val big = zip("wild_2560x1440.png" to png(2560, 1440, Color(60, 160, 70)), "wild_1920x1080.png" to png(1920, 1080, Color(60, 160, 70)))
        val small = zip("warden_1920x1080.png" to png(1920, 1080, Color(20, 60, 90)))
        val nested = tmp.resolve("nested.zip")
        Files.write(nested, zip("main.zip" to big, "extra.zip" to small, "__MACOSX/._main.zip" to ByteArray(4)))
        val s = store(tmp)
        val m1 = s.installFromZip(WallpaperCatalog.byId("wild_update")!!, nested)
        assertEquals(2, m1.variants.size)
        assertTrue(m1.palette.hue in 90f..140f, "green art, hue ${m1.palette.hue}")

        // "Tiny Takeover" style: sticker sets next to the main art in sub-folders -> the folder with the most image data wins
        val folders = tmp.resolve("folders.zip")
        Files.write(folders, zip(
            "drop/stickers/axo_1920x1080.png" to png(1920, 1080, Color(250, 250, 250)),
            "drop/main/art_2560x1440.png" to png(2560, 1440, Color(30, 90, 220)),
            "drop/main/art_1920x1080.png" to png(1920, 1080, Color(30, 90, 220)),
        ))
        val m2 = s.installFromZip(WallpaperCatalog.byId("tiny_takeover")!!, folders)
        assertTrue(hueDistance(m2.palette.hue, 220f) < 12f, "blue main art, hue ${m2.palette.hue}")
        assertEquals(setOf("wild_update", "tiny_takeover"), s.installed.value)
    }

    @Test
    fun customImageIsScaledAndKeptSeparately() {
        val tmp = Files.createTempDirectory("obsi-wp")
        val src = tmp.resolve("mine.png")
        Files.write(src, png(5000, 2000, Color(200, 40, 160)))
        val s = store(tmp)
        val meta = s.importCustom(src)
        assertEquals(WallpaperStore.CUSTOM_ID, meta.id)
        val v = meta.variants.single()
        assertEquals(2560, maxOf(v.width, v.height))
        assertEquals(1024, minOf(v.width, v.height))
        assertTrue("custom" in s.installed.value)
        s.removeCustom()
        assertFalse("custom" in s.installed.value)
        assertFalse(Files.exists(s.root.resolve("custom")))
        // not an image
        Files.write(tmp.resolve("x.png"), "nope".toByteArray())
        assertTrue(runCatching { s.importCustom(tmp.resolve("x.png")) }.isFailure)
    }

    // ---------------------------------------------------------------------------------------------- update check

    @Test
    fun updateComparison() {
        val remote = RemoteBuild("0.2.0", "abcdef1234567", null)
        assertTrue(UpdateService.compare("abcdef1", remote) is UpdateStatus.UpToDate)
        assertTrue(UpdateService.compare("ABCDEF1", remote) is UpdateStatus.UpToDate)
        assertTrue(UpdateService.compare("dev", remote) is UpdateStatus.UpToDate)   // development build
        assertTrue(UpdateService.compare("1111111", remote) is UpdateStatus.Available)
        assertTrue(UpdateService.compare("1111111", RemoteBuild("x", "", null)) is UpdateStatus.Failed)
    }

    @Test
    fun networkFailuresAreToldApartFromServerFailures() {
        assertTrue(UpdateService.isNetworkDown(java.net.UnknownHostException("github.com")))
        assertTrue(UpdateService.isNetworkDown(RuntimeException("wrapped", java.net.ConnectException("refused"))))
        assertFalse(UpdateService.isNetworkDown(IllegalStateException("bad json")))
        // unreachable address -> Offline, quickly
        val svc = UpdateService(Http("test"), url = "http://127.0.0.1:9/latest.json", timeoutMs = 3000)
        val r = runBlocking { svc.check("abc1234") }
        assertTrue(r is UpdateStatus.Offline, "got $r")
    }
}
