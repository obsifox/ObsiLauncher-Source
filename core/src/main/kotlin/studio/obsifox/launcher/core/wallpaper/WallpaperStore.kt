package studio.obsifox.launcher.core.wallpaper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import studio.obsifox.launcher.core.LauncherPaths
import studio.obsifox.launcher.core.mojang.ManifestEntry
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.util.LauncherException
import studio.obsifox.launcher.core.util.NoProgress
import studio.obsifox.launcher.core.util.ProgressSink
import studio.obsifox.launcher.core.util.ProgressUpdate
import studio.obsifox.launcher.core.util.StateJson
import studio.obsifox.launcher.core.util.writeTextAtomic
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

@Serializable
data class Variant(val file: String, val width: Int, val height: Int)

@Serializable
data class PackMeta(
    val id: String,
    val title: String,
    val variants: List<Variant>,
    val palette: Palette,
    val source: String = "",
    val installedAt: Long = 0,
)

/**
 * Downloads, unpacks and caches the official wallpaper ZIPs under `shared/wallpapers/<packId>/`.
 *
 * A pack is stored as a handful of JPEG variants (wide, 16:9, classic, square, portrait ...) plus `meta.json` with the
 * colour [Palette]; the variant that fits the window best is chosen at display time ([best]). The art is never shipped
 * with the launcher - it is fetched from minecraft.net, the same place the official "Wallpapers" page links to.
 */
class WallpaperStore(
    private val http: Http,
    paths: LauncherPaths,
    /** Mojang version manifest entries (used to place snapshots); may throw when offline. */
    private val manifest: suspend () -> List<ManifestEntry> = { emptyList() },
) {
    val root: Path = paths.shared.resolve("wallpapers")
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val metas = ConcurrentHashMap<String, PackMeta>()

    private val _installed = MutableStateFlow(scan())

    /** Ids of the packs that are on disk. */
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    private val _changes = MutableStateFlow(0L)

    /** Ticks whenever a pack was installed or the custom image changed - UIs re-resolve their background on it. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun scan(): Set<String> {
        if (!Files.isDirectory(root)) return emptySet()
        return Files.list(root).use { s ->
            s.filter { Files.isDirectory(it) }.toList().mapNotNull { d ->
                val name = d.fileName.toString()
                when {
                    name.endsWith(".tmp") -> { runCatching { d.toFile().deleteRecursively() }; null } // crashed installs
                    Files.isRegularFile(d.resolve("meta.json")) -> name
                    else -> null
                }
            }.toSet()
        }
    }

    fun packDir(id: String): Path = root.resolve(id)
    fun isInstalled(pack: WallpaperPack): Boolean = pack.id in _installed.value

    fun meta(id: String): PackMeta? {
        metas[id]?.let { return it }
        val f = packDir(id).resolve("meta.json")
        if (!Files.isRegularFile(f)) return null
        return runCatching { StateJson.decodeFromString<PackMeta>(Files.readString(f)) }.getOrNull()?.also { metas[id] = it }
    }

    fun file(meta: PackMeta, variant: Variant): Path = packDir(meta.id).resolve(variant.file)

    /** The pack for [mcVersion]; falls back to the version string alone when the manifest cannot be loaded. */
    suspend fun packFor(mcVersion: String): WallpaperPack {
        val entries = runCatching { manifest() }.getOrDefault(emptyList())
        return WallpaperCatalog.forVersion(mcVersion, entries)
    }

    suspend fun ensureForVersion(mcVersion: String, progress: ProgressSink = NoProgress): PackMeta = ensure(packFor(mcVersion), progress)

    /** Downloads [pack] unless it is already on disk. Concurrent calls for the same pack share one download. */
    suspend fun ensure(pack: WallpaperPack, progress: ProgressSink = NoProgress): PackMeta {
        meta(pack.id)?.let { return it }
        return locks.computeIfAbsent(pack.id) { Mutex() }.withLock {
            meta(pack.id)?.let { return it }
            Files.createDirectories(root)
            val zip = root.resolve("${pack.id}.zip")
            try {
                var done = 0L
                http.download(listOf(pack.url), zip) { n ->
                    done += n
                    progress(ProgressUpdate("wallpaper:${pack.title}", doneBytes = done))
                }
                withContext(Dispatchers.IO) { installFromZip(pack, zip) }
            } finally {
                runCatching { Files.deleteIfExists(zip) }
                runCatching { Files.deleteIfExists(zip.resolveSibling(zip.fileName.toString() + ".part")) }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ unpacking

    private class Img(val group: String, val size: Long, val read: () -> ByteArray)

    /** Unpacks a downloaded pack. Blocking; also used directly by tests with synthetic ZIPs. */
    fun installFromZip(pack: WallpaperPack, zip: Path): PackMeta {
        val tmp = root.resolve("${pack.id}.tmp")
        tmp.toFile().deleteRecursively()
        Files.createDirectories(tmp)
        var nestedFile: Path? = null
        try {
            ZipFile(zip.toFile()).use { top ->
                var (images, nested) = collect(top)
                var inner: ZipFile? = null
                try {
                    if (images.isEmpty() && nested.isNotEmpty()) {
                        // e.g. "The Wild Update" ships ZIPs inside the ZIP: take the biggest one as the main artwork
                        val big = nested.maxBy { it.size }
                        nestedFile = Files.createTempFile("obsi-wallpaper", ".zip").also { f -> top.getInputStream(big).use { Files.copy(it, f, StandardCopyOption.REPLACE_EXISTING) } }
                        inner = ZipFile(nestedFile!!.toFile())
                        images = collect(inner).first
                    }
                    if (images.isEmpty()) throw LauncherException("No images found in ${pack.title}")
                    // "Tiny Takeover" mixes the main art with sticker sets in sub-folders: the folder with the most image data is the main one
                    val group = images.groupBy { it.group }.maxBy { (_, v) -> v.sumOf { it.size } }.value
                    return finish(pack, group, tmp)
                } finally {
                    inner?.close()
                }
            }
        } catch (e: Throwable) {
            tmp.toFile().deleteRecursively()
            throw e
        } finally {
            nestedFile?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun collect(zf: ZipFile): Pair<List<Img>, List<java.util.zip.ZipEntry>> {
        val images = ArrayList<Img>()
        val nested = ArrayList<java.util.zip.ZipEntry>()
        for (e in zf.entries()) {
            if (e.isDirectory) continue
            val name = e.name
            val leaf = name.substringAfterLast('/')
            if (name.startsWith("__MACOSX/") || leaf.startsWith("._")) continue
            when (leaf.substringAfterLast('.', "").lowercase()) {
                "zip" -> nested += e
                "png", "jpg", "jpeg" -> images += Img(name.substringBeforeLast('/', ""), e.size) { zf.getInputStream(e).use { it.readBytes() } }
            }
        }
        return images to nested
    }

    private fun finish(pack: WallpaperPack, group: List<Img>, tmp: Path): PackMeta {
        class Decoded(val w: Int, val h: Int, val bytes: Long)
        val written = LinkedHashMap<String, Decoded>() // "WxH" -> info
        var biggest: BufferedImage? = null
        var fallback: Pair<BufferedImage, Img>? = null
        for (img in group.sortedByDescending { it.size }) {
            val bi = runCatching { ImageIO.read(ByteArrayInputStream(img.read())) }.getOrNull() ?: continue
            val pixels = bi.width.toLong() * bi.height
            if (fallback == null || pixels > fallback.first.width.toLong() * fallback.first.height) fallback = bi to img
            // thumbnails / sticker crops (414x414, 800x450, 560x1440 ...) are useless as a full-window background
            if (pixels < 1_000_000L) continue
            val key = "${bi.width}x${bi.height}"
            if (key in written) continue
            val out = tmp.resolve("$key.jpg")
            writeJpeg(bi, out, 0.9f)
            written[key] = Decoded(bi.width, bi.height, Files.size(out))
            if (biggest == null || pixels > biggest.width.toLong() * biggest.height) biggest = bi
        }
        if (written.isEmpty()) { // an unusually small pack: keep the largest picture we have
            val (bi, _) = fallback ?: throw LauncherException("No readable image in ${pack.title}")
            writeJpeg(bi, tmp.resolve("${bi.width}x${bi.height}.jpg"), 0.9f)
            written["${bi.width}x${bi.height}"] = Decoded(bi.width, bi.height, 0)
            biggest = bi
        }
        val palette = PaletteExtractor.fromImage(biggest!!)
        val meta = PackMeta(
            id = pack.id, title = pack.title,
            variants = written.entries.map { (k, d) -> Variant("$k.jpg", d.w, d.h) }.sortedWith(compareByDescending<Variant> { it.width.toLong() * it.height }.thenByDescending { it.width }),
            palette = palette, source = pack.file, installedAt = System.currentTimeMillis(),
        )
        writeTextAtomic(tmp.resolve("meta.json"), StateJson.encodeToString(PackMeta.serializer(), meta))
        publish(pack.id, tmp, meta)
        return meta
    }

    private fun publish(id: String, tmp: Path, meta: PackMeta) {
        val dest = packDir(id)
        dest.toFile().deleteRecursively()
        try {
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, dest)
        }
        metas[id] = meta
        _installed.update { it + id }
        _changes.update { it + 1 }
    }

    // ------------------------------------------------------------------------------------------------ custom image

    /** Copies [src] into the data directory as a ≤2560 px JPEG and computes its palette (spec: "scale to max 2560, high quality JPEG"). */
    fun importCustom(src: Path): PackMeta {
        val img = runCatching { ImageIO.read(src.toFile()) }.getOrNull() ?: throw LauncherException("This file is not a readable image")
        val scaled = scaleTo(img, 2560)
        val tmp = root.resolve("custom.tmp")
        tmp.toFile().deleteRecursively()
        Files.createDirectories(tmp)
        writeJpeg(scaled, tmp.resolve("custom.jpg"), 0.92f)
        val meta = PackMeta(
            id = CUSTOM_ID, title = "Custom image", variants = listOf(Variant("custom.jpg", scaled.width, scaled.height)),
            palette = PaletteExtractor.fromImage(scaled), source = src.fileName.toString(), installedAt = System.currentTimeMillis(),
        )
        writeTextAtomic(tmp.resolve("meta.json"), StateJson.encodeToString(PackMeta.serializer(), meta))
        publish(CUSTOM_ID, tmp, meta)
        return meta
    }

    fun removeCustom() {
        packDir(CUSTOM_ID).toFile().deleteRecursively()
        metas.remove(CUSTOM_ID)
        _installed.update { it - CUSTOM_ID }
        _changes.update { it + 1 }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private fun scaleTo(img: BufferedImage, maxEdge: Int): BufferedImage {
        var cur = toRgb(img)
        if (max(cur.width, cur.height) <= maxEdge) return cur
        while (max(cur.width, cur.height) > maxEdge * 2) cur = resize(cur, cur.width / 2, cur.height / 2) // halve first: much better quality than one big step
        val f = maxEdge.toDouble() / max(cur.width, cur.height)
        return resize(cur, max(1, (cur.width * f).toInt()), max(1, (cur.height * f).toInt()))
    }

    private fun resize(src: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    private fun toRgb(img: BufferedImage): BufferedImage {
        if (img.type == BufferedImage.TYPE_INT_RGB) return img
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = java.awt.Color.BLACK
        g.fillRect(0, 0, img.width, img.height)
        g.drawImage(img, 0, 0, null)
        g.dispose()
        return out
    }

    private fun writeJpeg(img: BufferedImage, out: Path, quality: Float) {
        val rgb = toRgb(img)
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality
        }
        Files.newOutputStream(out).use { os ->
            ImageIO.createImageOutputStream(os).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(rgb, null, null), param)
            }
        }
        writer.dispose()
    }

    companion object {
        const val CUSTOM_ID = "custom"

        /**
         * Picks the variant that fits a window of [targetW] x [targetH] physical pixels best: a close aspect ratio matters most,
         * then not being too small, then (mildly) not being needlessly large. Same scoring as the UX specification (§5.2).
         */
        fun best(meta: PackMeta, targetW: Int, targetH: Int): Variant {
            val tw = max(320, targetW).toDouble()
            val th = max(320, targetH).toDouble()
            val targetRatio = tw / th
            return meta.variants.minBy { v ->
                val ratioError = abs(ln(targetRatio / (v.width.toDouble() / v.height)))
                val shortfall = maxOf(0.0, tw / v.width - 1, th / v.height - 1)
                val oversize = maxOf(0.0, v.width / tw - 1, v.height / th - 1)
                ratioError * 4 + shortfall * 0.35 + oversize * 0.012
            }
        }
    }
}
