package studio.obsifox.obsilauncher.obsi

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import studio.obsifox.obsilauncher.core.BackgroundMode
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.net.Http
import studio.obsifox.obsilauncher.core.net.YouTube
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.ln

/** Download progress of the live-video background. */
sealed class VideoBg {
    data object Idle : VideoBg()
    data class Downloading(val done: Long, val total: Long) : VideoBg()
    data class Failed(val message: String) : VideoBg()
    data object Ready : VideoBg()
}

/**
 * ObsiLauncher background engine (written from scratch).
 *
 * Two background types, exactly as briefed:
 *  - WALLPAPER: version-driven official artwork — Minecraft >= 1.12.2 uses the
 *    *Wilderness Bound* pack, older versions use the *Minecraft PC bundle*
 *    (both fetched from minecraft.net). The accent colour extracted from the
 *    active artwork drives the "Obsi Dynamic" theme through [accent].
 *  - VIDEO: the pinned official trailer (YouTube 1HCrV7mFWr8, fetched with a
 *    pytube-style resolver) plays as a muted, looping live background —
 *    only when the selected version IS the latest Minecraft release; every
 *    other version automatically falls back to wallpaper artwork.
 *
 * A user-picked custom background always wins over both.
 */
class ObsiWallpaper(private val context: Context, private val settings: ObsiSettings) {

    data class Active(val isVideo: Boolean, val imagePath: String?)

    val active = MutableStateFlow(Active(isVideo = false, imagePath = null))
    val accent = MutableStateFlow(0xFFFF8A3D.toInt())
    val videoBg = MutableStateFlow<VideoBg>(VideoBg.Idle)

    private val latestRelease = MutableStateFlow("")
    private val videoDownloading = AtomicBoolean(false)

    /** Call whenever the selected version changes (and once at startup). */
    fun sync(versionId: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            apply(context, settings, versionId)
        }
    }

    private suspend fun apply(context: Context, settings: ObsiSettings, versionId: String?) =
        withContext(Dispatchers.IO) {
            val latest = fetchLatestRelease()

            val custom = settings.customWallpaperValue
            if (custom.isNotBlank() && File(custom).isFile) {
                publish(Active(false, custom), extractAccent(File(custom)))
                return@withContext
            }

            // Video background: only for the latest release, only when the user
            // picked the video mode in the wizard / settings.
            if (settings.backgroundModeValue == BackgroundMode.VIDEO &&
                versionId != null && latest.isNotEmpty() && versionId == latest
            ) {
                val trailer = Paths.trailerFile(context)
                if (trailer.isFile && trailer.length() > 0) {
                    videoBg.value = VideoBg.Ready
                    publish(Active(true, null), accent.value)
                    return@withContext
                }
                ensureVideo() // download in the background; wallpaper stays meanwhile
            }

            val packDir = packForVersion(versionId, latest)
            val artwork = ensureArtwork(packDir, versionId)
            if (artwork == null) {
                publish(Active(false, null), accent.value)
                return@withContext
            }
            publish(Active(false, artwork.absolutePath), extractAccent(artwork))
        }

    private fun publish(a: Active, newAccent: Int) {
        active.value = a
        accent.value = newAccent
    }

    /** Latest release according to Mojang's manifest (cached for the session). */
    private fun fetchLatestRelease(): String {
        latestRelease.value.takeIf { it.isNotEmpty() }?.let { return it }
        val v = runCatching {
            JSONObject(Http.get(LATEST_URL) ?: "{}")
                .optJSONObject("latest")?.optString("release").orEmpty()
        }.getOrDefault("")
        latestRelease.value = v
        return v
    }

    /** Latest known release without touching the network again. */
    fun cachedLatestRelease(): String = latestRelease.value

    /**
     * Fetches the trailer with the pytube-style resolver and stores it as
     * `obsi/trailer.mp4`. A single download runs at a time; on success the
     * background re-syncs so the video starts immediately.
     */
    fun ensureVideo() {
        if (!videoDownloading.compareAndSet(false, true)) return
        videoBg.value = VideoBg.Idle
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dest = Paths.trailerFile(context)
                dest.parentFile?.mkdirs()
                val result = YouTube.download(YouTube.TRAILER_VIDEO_ID, dest) { done, total ->
                    videoBg.value = VideoBg.Downloading(done, total)
                }
                if (dest.length() <= 0) throw java.io.IOException("empty download")
                videoBg.value = VideoBg.Ready
                videoDownloading.set(false)
                sync(settings.selectedVersionValue.ifBlank { null })
            } catch (e: Exception) {
                videoDownloading.set(false)
                Paths.trailerFile(context).delete()
                videoBg.value = VideoBg.Failed(e.message ?: "download failed")
            }
        }
    }

    private fun extractAccent(image: File): Int = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(image.absolutePath, opts)
        val sample = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(opts.outWidth, opts.outHeight)
        }
        val bmp = BitmapFactory.decodeFile(image.absolutePath, sample) ?: return@runCatching 0xFFFF8A3D.toInt()
        val value = AccentExtractor.extract(bmp)
        bmp.recycle()
        value
    }.getOrDefault(0xFFFF8A3D.toInt())

    private fun computeInSampleSize(w: Int, h: Int): Int {
        var size = 1
        var sw = w
        var sh = h
        while (sw * sh > 4 * 48 * 48) {
            sw /= 2
            sh /= 2
            size *= 2
        }
        return size
    }

    // ---- packs -----------------------------------------------------------------

    private fun packForVersion(versionId: String?, latestRelease: String?): String =
        if (isAtLeast(versionId)) WILDERNESS else LEGACY

    private fun isAtLeast(mcVersion: String?, ref: String = "1.12.2"): Boolean {
        if (mcVersion == null) return true
        val a = numbers(mcVersion)
        val b = numbers(ref)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return true
    }

    private fun numbers(v: String): List<Int> =
        v.substringBefore('-').split('.').map { part -> part.filter(Char::isDigit).take(3).ifEmpty { "0" }.toIntOrNull() ?: 0 }

    /** Downloads (once) and unpacks the right minecraft.net wallpaper pack, then picks the best artwork. */
    private fun ensureArtwork(packDirName: String, versionId: String?): File? {
        val root = Paths.wallpaperRoot(context)
        val packDir = File(root, packDirName)
        val marker = File(packDir, ".ok")
        if (!marker.isFile) {
            val url = if (packDirName == WILDERNESS) WILDERNESS_URL else LEGACY_URL
            val zipFile = File(root, "$packDirName.zip")
            root.mkdirs()
            runCatching {
                Http.downloadToFile(url, zipFile)
                ZipFile(zipFile).use { zip ->
                    for (entry in zip.entries()) {
                        if (entry.isDirectory) continue
                        val name = entry.name.substringAfterLast('/')
                        if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")) continue
                        zip.getInputStream(entry).use { input ->
                            File(packDir, name).outputStream().use { input.copyTo(it) }
                        }
                    }
                }
                marker.writeText("ok")
                zipFile.delete()
            }.onFailure {
                return pickExisting(root) // offline: use whatever we already have
            }
        }
        val files = (packDir.listFiles { f -> f.isFile && isImage(f) }?.toList() ?: emptyList())
            .ifEmpty { packDir.walkTopDown().filter { it.isFile && isImage(it) }.toList() }
        if (files.isEmpty()) return pickExisting(root)
        // the launcher is landscape-only: always prefer landscape artwork
        return pickVariant(files, portrait = false)
    }

    private fun pickExisting(root: File): File? =
        root.walkTopDown().filter { it.isFile && isImage(it) }.firstOrNull()

    private fun isImage(f: File): Boolean =
        f.extension.lowercase() in setOf("png", "jpg", "jpeg")

    /** Closest aspect ratio to the screen, with a resolution floor. */
    private fun pickVariant(files: List<File>, portrait: Boolean): File {
        val targetRatio = if (portrait) 9f / 19.5f else 16f / 9f
        var best: File? = null
        var bestScore = Double.MAX_VALUE
        for (f in files) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            if (w <= 0 || h <= 0) continue
            val ratio = w.toFloat() / h
            val score = abs(ln(ratio / targetRatio)) + (if (w < 720 && h < 720) 1.5 else 0.0)
            if (score < bestScore) {
                bestScore = score
                best = f
            }
        }
        return best ?: files.first()
    }

    private companion object {
        const val WILDERNESS = "wilderness"
        const val LEGACY = "legacy"
        const val WILDERNESS_URL =
            "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/software/wallpapers_wilderness_bound_drop.zip"
        const val LEGACY_URL =
            "https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/software/wallpapers_minecraft_pc_bundle.zip"
        const val LATEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }
}

/**
 * The full-screen backdrop: the looping trailer when [ObsiWallpaper.Active.isVideo]
 * is set, otherwise the version wallpaper with blur + scrims.
 */
@Composable
fun ObsiWallpaperLayer(wallpaper: ObsiWallpaper, blurPx: Int, modifier: Modifier = Modifier) {
    val active by wallpaper.active.collectAsState()
    val context = LocalContext.current

    Box(modifier.fillMaxSize().background(Color(0xFF0B0908))) {
        val path = active.imagePath
        when {
            active.isVideo -> TrailerVideoLayer(
                video = Paths.trailerFile(context),
                modifier = Modifier.fillMaxSize(),
            )

            path != null -> {
                val bitmap = remember(path) {
                    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (blurPx > 0) Modifier.blur(blurPx.dp()) else Modifier),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x80000000), Color(0x14000000), Color(0xA6000000))
                    )
                )
        )
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

@Composable
private fun TrailerVideoLayer(video: File, modifier: Modifier = Modifier) {
    if (!video.isFile) return
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(video.path) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        runCatching {
                            val mp = MediaPlayer()
                            mp.setDataSource(video.absolutePath)
                            mp.setSurface(holder.surface)
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            mp.prepare()
                            mp.start()
                            player = mp
                        }
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        runCatching { player?.release() }
                        player = null
                    }
                })
            }
        },
        modifier = modifier,
    )
}
