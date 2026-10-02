package studio.obsifox.launcher.desktop

import studio.obsifox.launcher.core.LauncherCore
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Version-driven wallpaper engine.
 *
 * Rule set (product decision):
 *  - Minecraft >= 1.12.2 (including every newer release/snapshot) uses the official
 *    *Wilderness Bound* wallpaper pack.
 *  - Minecraft < 1.12.2 uses the official *Minecraft PC bundle* wallpaper pack.
 *  - The latest release additionally supports the *Wilderness Bound trailer* as a
 *    live video background (muted by default).
 *
 * Packs are downloaded from minecraft.net on first use, unpacked under
 * `<data>/shared/wallpapers/<pack>/` and cached forever. When the download fails
 * (offline) an optimized JPEG pair (wide + portrait) bundled in the app resources
 * is extracted instead, so the launcher always has its artwork.
 */
class WallpaperService(private val core: LauncherCore) {

    enum class Pack(val dirName: String, val zipUrl: String, val label: String) {
        WILDERNESS(
            "wilderness",
            "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/software/wallpapers_wilderness_bound_drop.zip",
            "Wilderness Bound",
        ),
        LEGACY(
            "legacy",
            "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/software/wallpapers_minecraft_pc_bundle.zip",
            "Minecraft PC Bundle",
        ),
    }

    data class Variant(val file: Path, val width: Int, val height: Int) {
        val ratio: Float get() = width.toFloat() / height
    }

    data class Resolved(val pack: Pack, val variants: List<Variant>, val fromNetwork: Boolean)

    private val root: Path get() = core.paths.shared.resolve("wallpapers")

    // ------------------------------------------------------------------ version mapping

    /** Latest release id from the Mojang manifest (cached manifest also works offline). */
    suspend fun latestReleaseId(): String? = try {
        core.mojang.manifest().latest.release.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /** True when [mcVersion] is at least [ref] (semver-ish `1.x.y` comparison). */
    fun isAtLeast(mcVersion: String, ref: String = "1.12.2"): Boolean {
        val a = numbers(mcVersion)
        val b = numbers(ref)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return true
    }

    /** The pack that matches [mcVersion] (newer than or equal to 1.12.2 -> Wilderness Bound). */
    fun packForVersion(mcVersion: String): Pack =
        if (isAtLeast(mcVersion)) Pack.WILDERNESS else Pack.LEGACY

    private fun numbers(v: String): List<Int> =
        v.substringBefore('-').split('.').map { part -> part.filter(Char::isDigit).take(3).ifEmpty { "0" }.toIntOrNull() ?: 0 }

    // ------------------------------------------------------------------ pack storage

    fun packDir(pack: Pack): Path = root.resolve(pack.dirName)

    fun videoDir(): Path = root.resolve("video")

    /**
     * Local trailer file. Looked up in this order:
     *  1. user-provided `<data>/shared/wallpapers/video/trailer.mp4`
     *  2. trailer bundled in the app resources (extracted on first use)
     */
    fun videoFile(): Path? {
        val user = videoDir().resolve("trailer.mp4")
        if (user.exists() && Files.size(user) > 0) return user
        val out = videoDir().resolve("trailer.mp4")
        return try {
            WallpaperService::class.java.getResourceAsStream("/wallpapers/video/trailer.mp4")?.use { input ->
                Files.createDirectories(videoDir())
                if (!out.exists()) Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING)
            }
            out.takeIf { it.exists() && Files.size(it) > 0 }
        } catch (_: Exception) {
            null
        }
    }

    /** Ensures the pack exists on disk and returns its variants. Never throws. */
    suspend fun ensurePack(pack: Pack): Resolved {
        val dir = packDir(pack)
        val existing = variants(dir)
        if (existing.isNotEmpty()) return Resolved(pack, existing, fromNetwork = true)
        Files.createDirectories(dir)
        // 1) try the official zip
        try {
            val bytes = core.http.getBytes(pack.zipUrl, 64 * 1024 * 1024)
            val tmp = Files.createTempFile("obsi-wallpaper-", ".zip")
            try {
                Files.write(tmp, bytes)
                unpack(tmp, dir)
            } finally {
                Files.deleteIfExists(tmp)
            }
            val after = variants(dir)
            if (after.isNotEmpty()) return Resolved(pack, after, fromNetwork = true)
        } catch (_: Exception) {
            // fall through to bundled artwork
        }
        // 2) bundled fallback (wide + portrait)
        extractBundled(pack, dir)
        return Resolved(pack, variants(dir), fromNetwork = false)
    }

    private fun variants(dir: Path): List<Variant> {
        if (!dir.exists()) return emptyList()
        return try {
            Files.list(dir).use { stream ->
                stream
                    .filter { p -> p.extension.lowercase() in setOf("png", "jpg", "jpeg") && !p.name.startsWith(".") }
                    .map { p -> p.toFile() }
                    .toList()
                    .mapNotNull { f -> dimsOf(f)?.let { (w, h) -> Variant(f.toPath(), w, h) } }
            }.sortedByDescending { it.width * it.height }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun unpack(zip: Path, into: Path) {
        ZipFile(zip.toFile()).use { zf ->
            for (entry in zf.entries()) {
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                if (name.startsWith("__MACOSX") || name.startsWith(".")) continue
                if (!name.lowercase().endsWith(".png") && !name.lowercase().endsWith(".jpg")) continue
                zf.getInputStream(entry).use { input ->
                    val target = into.resolve(name)
                    Files.createDirectories(into)
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun extractBundled(pack: Pack, into: Path) {
        for ((res, w, h) in listOf(Triple("wide.jpg", 1920, 1080), Triple("portrait.jpg", 720, 1280))) {
            try {
                val stream = WallpaperService::class.java.getResourceAsStream("/wallpapers/${pack.dirName}/$res") ?: continue
                stream.use { input ->
                    Files.createDirectories(into)
                    // dims are encoded in the file name so variant picking works without decoding
                    val target = into.resolve("${pack.dirName}-bundled-${w}x$h.jpg")
                    if (!target.exists()) Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------ variant picking

    /** Chooses the best variant for a target area (spec 5.2: close ratio, small enough, no upscaling). */
    fun bestVariant(variants: List<Variant>, targetW: Int, targetH: Int): Variant? {
        if (variants.isEmpty()) return null
        val tr = if (targetH > 0) targetW.toFloat() / targetH else 16f / 9f
        fun score(v: Variant): Double {
            val ratioPenalty = kotlin.math.abs(kotlin.math.ln(v.ratio / tr))
            val tooSmall = if (v.width < targetW || v.height < targetH) 2.5 else 0.0
            val oversize = kotlin.math.ln((v.width * v.height).toDouble() / (targetW * targetH).coerceAtLeast(1)).coerceAtLeast(0.0) * 0.35
            return ratioPenalty + tooSmall + oversize
        }
        return variants.minByOrNull { score(it) }
    }

    // ------------------------------------------------------------------ png dims

    /** Dimensions from the file name (`..._2560x1440.png`) or the PNG/JPEG header. */
    private fun dimsOf(f: File): Pair<Int, Int>? {
        Regex("(\\d{2,5})x(\\d{2,5})").find(f.name)?.let {
            val (w, h) = it.destructured
            val wi = w.toIntOrNull() ?: 0
            val hi = h.toIntOrNull() ?: 0
            if (wi > 0 && hi > 0) return wi to hi
        }
        return headerDims(f)
    }

    private fun headerDims(f: File): Pair<Int, Int>? = try {
        java.io.RandomAccessFile(f, "r").use { raf ->
            val head = ByteArray(33)
            val read = raf.read(head)
            if (read >= 33 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte()) {
                // PNG: IHDR width/height at fixed offsets (big endian)
                val w = ((head[16].toInt() and 0xFF) shl 24) or ((head[17].toInt() and 0xFF) shl 16) or ((head[18].toInt() and 0xFF) shl 8) or (head[19].toInt() and 0xFF)
                val h = ((head[20].toInt() and 0xFF) shl 24) or ((head[21].toInt() and 0xFF) shl 16) or ((head[22].toInt() and 0xFF) shl 8) or (head[23].toInt() and 0xFF)
                w to h
            } else {
                // JPEG: scan the SOF marker for the real dimensions
                val data = f.readBytes()
                var i = 2
                while (i + 9 < data.size) {
                    if (data[i] != 0xFF.toByte()) { i++; continue }
                    val marker = data[i + 1].toInt() and 0xFF
                    if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                        val h = ((data[i + 5].toInt() and 0xFF) shl 8) or (data[i + 6].toInt() and 0xFF)
                        val w = ((data[i + 7].toInt() and 0xFF) shl 8) or (data[i + 8].toInt() and 0xFF)
                        return w to h
                    }
                    val len = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                    i += 2 + len.coerceAtLeast(2)
                }
                null
            }
        }
    } catch (_: Exception) {
        null
    }
}
