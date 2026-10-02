/*
 * This file is part of ObsiLauncher's UI overlay.
 * Licensed under GPL-3.0-or-later (see /NOTICE.md in the repository root).
 */

package com.movtery.zalithlauncher.obsi

import android.content.Context
import android.graphics.BitmapFactory
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.viewmodel.BackgroundViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * ObsiLauncher version-driven wallpaper engine (mobile edition).
 *
 * Mirrors the desktop `WallpaperService` rule set:
 *  - Minecraft >= 1.12.2  -> official *Wilderness Bound* wallpaper pack
 *  - Minecraft <  1.12.2  -> official *Minecraft PC bundle* wallpaper pack
 *  - the latest release   -> the Wilderness Bound trailer as a live video
 *                            background when the user (or the desktop tool
 *                            `tools/fetch-trailer.py`) has provided it as
 *                            `<files>/obsi/trailer.mp4`; otherwise the
 *                            wilderness wallpaper is used.
 *
 * Both wallpaper packs are downloaded once from minecraft.net, unpacked under
 * `<files>/obsi/wallpapers/<pack>/` and cached forever. The chosen artwork is
 * copied over the launcher background file that ZalithLauncher2's built-in
 * [BackgroundViewModel] renders, so the full glass/blur pipeline (haze) keeps
 * working unchanged. The accent colour of the applied wallpaper feeds
 * [ObsiTheme], driving the "Obsi Dynamic" colour scheme.
 */
object ObsiWallpaper {
    private val TAG = "ObsiWallpaper"

    private const val WILDERNESS_URL =
        "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/software/wallpapers_wilderness_bound_drop.zip"
    private const val LEGACY_URL =
        "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/software/wallpapers_minecraft_pc_bundle.zip"
    private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val REFERENCE_VERSION = "1.12.2"   // packs split at this release
    private const val MAX_ZIP_BYTES = 64L * 1024 * 1024

    private enum class Pack(val dirName: String, val zipUrl: String) {
        WILDERNESS("wilderness", WILDERNESS_URL),
        LEGACY("legacy", LEGACY_URL),
    }

    private fun root(context: Context): File = File(context.filesDir, "obsi")
    private fun packDir(context: Context, pack: Pack): File = File(root(context), "wallpapers/${pack.dirName}")
    private fun trailerFile(context: Context): File = File(root(context), "trailer.mp4")
    private fun markerFile(context: Context): File = File(root(context), "wallpaper_state.json")
    private fun manifestCache(context: Context): File = File(root(context), "version_manifest.json")

    // ------------------------------------------------------------------ entry point

    /**
     * Called from `MainActivity` whenever the selected game version changes.
     * Resolves the matching artwork (and the latest-release trailer when
     * present), applies it to the launcher background, refreshes the
     * background ViewModel and re-extracts the dynamic accent colour.
     * Never throws — on any failure the previous background simply stays.
     */
    suspend fun autoSync(context: Context, version: Version?, backgroundViewModel: BackgroundViewModel?) {
        withContext(Dispatchers.IO) {
            runCatching { syncInternal(context, version, backgroundViewModel) }
                .onFailure { Logger.warning(TAG, "Wallpaper sync failed; keeping previous background", it) }
        }
    }

    private fun syncInternal(context: Context, version: Version?, backgroundViewModel: BackgroundViewModel?) {
        root(context).mkdirs()

        val mcVersion = version?.getVersionInfo()?.minecraftVersion
            ?: version?.getVersionName()
            ?: ""

        val latestRelease = latestReleaseId(context)
        val isLatest = mcVersion.isNotBlank() && latestRelease != null && mcVersion == latestRelease

        val marker = readMarker(context)
        val background = PathManager.FILE_LAUNCHER_BACKGROUND

        // Decide what should be shown for this version.
        val wanted: File? = when {
            isLatest && trailerFile(context).isFile && trailerFile(context).length() > 0 -> trailerFile(context)
            mcVersion.isBlank() -> null                                 // nothing selected yet
            else -> {
                val pack = packForVersion(mcVersion)
                val files = ensurePack(context, pack)
                pickVariant(files, portrait = true)
            }
        }

        if (wanted == null) {
            if (mcVersion.isBlank()) Logger.info(TAG, "No game version selected; wallpaper unchanged")
            return
        }

        val sameVersion = marker?.optString("version") == mcVersion
        if (sameVersion && background.isFile && marker!!.optLong("appliedAt", 0L) <= background.lastModified()) {
            // this version's wallpaper is already in place and the user has not
            // swapped in their own artwork afterwards — nothing to do
            refreshAccent(context, background)
            return
        }

        FileUtils.copyFile(wanted, background)
        writeMarker(context, mcVersion, background.lastModified())
        Logger.info(TAG, "Applied ${if (isVideo(wanted)) "video" else "wallpaper"} '${wanted.name}' for Minecraft $mcVersion")

        refreshAccent(context, if (isVideo(wanted)) null else wanted)
        backgroundViewModel?.reload()
    }

    /**
     * Keeps the dynamic accent in step with the visible background. Videos do
     * not yield a still image, so their accent is derived from the wilderness
     * artwork until a proper frame grab is worth the effort.
     */
    private fun refreshAccent(context: Context, image: File?) {
        when {
            image != null -> ObsiTheme.extractAccent(image)
            ObsiTheme.source.value.isNotBlank() -> Unit   // keep the previous accent
            else -> ObsiTheme.extractAccent(pickVariant(artworks(packDir(context, Pack.WILDERNESS)), portrait = true))
        }
    }

    // ------------------------------------------------------------------ version mapping

    /** Same semver-ish comparison as the desktop: `b1.7.3` -> [1,7,3] < [1,12,2]. */
    fun isAtLeast(mcVersion: String, ref: String = REFERENCE_VERSION): Boolean {
        val a = numbers(mcVersion)
        val b = numbers(ref)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return true
    }

    private fun packForVersion(mcVersion: String): Pack =
        if (isAtLeast(mcVersion)) Pack.WILDERNESS else Pack.LEGACY

    private fun numbers(v: String): List<Int> =
        v.substringBefore('-').split('.').map { part -> part.filter(Char::isDigit).take(3).ifEmpty { "0" }.toIntOrNull() ?: 0 }

    /**
     * Latest release id from the Mojang manifest, cached on disk for 6 hours.
     * Returns null when offline with no cache — the trailer is then simply not used.
     */
    private fun latestReleaseId(context: Context): String? {
        val cache = manifestCache(context)
        val cached = runCatching {
            if (cache.isFile && System.currentTimeMillis() - cache.lastModified() < 6L * 3600_000) {
                Regex("\"release\"\\s*:\\s*\"([^\"]+)\"").find(cache.readText())?.groupValues?.get(1)
            } else null
        }.getOrNull()
        if (cached != null) return cached

        return runCatching {
            val text = httpGet(MANIFEST_URL) ?: return null
            cache.writeText(text)
            Regex("\"release\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        }.getOrNull()
    }

    // ------------------------------------------------------------------ pack management

    /** Ensures the pack is unpacked on disk; returns the artwork files it contains. */
    private fun ensurePack(context: Context, pack: Pack): List<File> {
        val dir = packDir(context, pack)
        val existing = artworks(dir)
        if (existing.isNotEmpty()) return existing

        runCatching {
            val zip = httpGetToFile(pack.zipUrl, File(context.cacheDir, "obsi-${pack.dirName}.zip")) ?: return@runCatching
            ZipFile(zip).use { zf ->
                dir.mkdirs()
                for (entry in zf.entries()) {
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    if (name.startsWith(".") || name.startsWith("__MACOSX")) continue
                    val lower = name.lowercase()
                    if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) continue
                    zf.getInputStream(entry).use { input ->
                        val target = File(dir, name)
                        FileUtils.copyInputStreamToFile(input, target)
                    }
                }
            }
            zip.delete()
        }.onFailure { Logger.warning(TAG, "Could not download wallpaper pack ${pack.dirName}", it) }

        return artworks(dir)
    }

    private fun artworks(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && !f.name.startsWith(".") && f.extension.lowercase() in setOf("png", "jpg", "jpeg") }
            ?.sortedByDescending { it.length() }
            .orEmpty()

    /**
     * Chooses the best-suited artwork for the device: on phones the closest
     * portrait ratio wins, on tablets/landscape the closest landscape one.
     */
    fun pickVariant(files: List<File>, portrait: Boolean): File? {
        if (files.isEmpty()) return null
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val targetRatio = if (portrait) metrics.heightPixels.toFloat() / metrics.widthPixels
        else metrics.widthPixels.toFloat() / metrics.heightPixels

        var best: File? = null
        var bestScore = Double.MAX_VALUE
        for (f in files) {
            val (w, h) = dimensions(f) ?: continue
            if (w <= 0 || h <= 0) continue
            val ratio = w.toFloat() / h
            val score = kotlin.math.abs(kotlin.math.ln(ratio / targetRatio)) +
                (if (w < 720 && h < 720) 1.5 else 0.0)
            if (score < bestScore) { bestScore = score; best = f }
        }
        return best ?: files.first()
    }

    private fun dimensions(f: File): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
    }

    private fun isVideo(f: File): Boolean = f.isFile && f.length() > 0 && f.extension.lowercase() !in setOf("png", "jpg", "jpeg")

    // ------------------------------------------------------------------ marker + networking

    private fun readMarker(context: Context): org.json.JSONObject? = runCatching {
        val f = markerFile(context)
        if (f.isFile) org.json.JSONObject(f.readText()) else null
    }.getOrNull()

    private fun writeMarker(context: Context, version: String, appliedAt: Long) {
        runCatching {
            markerFile(context).writeText(org.json.JSONObject().put("version", version).put("appliedAt", appliedAt).toString())
        }
    }

    private fun httpGet(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun httpGetToFile(url: String, target: File): File? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) return null
            target.outputStream().use { out -> conn.inputStream.use { input ->
                var copied = 0L
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    copied += n
                    if (copied > MAX_ZIP_BYTES) throw IllegalStateException("Wallpaper pack too large")
                    out.write(buf, 0, n)
                }
            } }
            target
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
