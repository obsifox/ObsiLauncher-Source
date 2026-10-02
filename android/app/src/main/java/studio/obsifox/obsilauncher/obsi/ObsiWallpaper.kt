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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.net.Http
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.ln

/**
 * Version-driven wallpaper engine (written from scratch for ObsiLauncher 1.3.0).
 *
 * Rules:
 *  - Minecraft >= 1.12.2 -> official *Wilderness Bound* pack (minecraft.net)
 *  - Minecraft <  1.12.2 -> official *Minecraft PC bundle* pack (minecraft.net)
 *  - the latest release  -> the Wilderness trailer as a live background when
 *    `<files>/obsi/trailer.mp4` is present
 *  - a user-picked custom background always wins
 *
 * The accent colour extracted from the active artwork drives the "Obsi Dynamic"
 * theme through [accent].
 */
class ObsiWallpaper(private val context: Context, private val settings: ObsiSettings) {

    data class Active(val isVideo: Boolean, val imagePath: String?)

    val active = MutableStateFlow(Active(isVideo = false, imagePath = null))
    val accent = MutableStateFlow(0xFFFF8A3D.toInt())

    /** Call whenever the selected version changes (and once at startup). */
    fun sync(versionId: String?) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            apply(context, settings, versionId)
        }
    }

    private suspend fun apply(context: Context, settings: ObsiSettings, versionId: String?) = withContext(Dispatchers.IO) {
        val custom = settings.customWallpaperValue
        if (custom.isNotBlank() && File(custom).isFile) {
            publish(Active(false, custom), extractAccent(File(custom)))
            return@withContext
        }

        // trailer for the latest release
        val trailer = Paths.trailerFile(context)
        val manifestLatest = runCatching {
            JSONObject(Http.get(LATEST_URL) ?: "{}").optJSONObject("latest")?.optString("release").orEmpty()
        }.getOrNull()
        if (manifestLatest != null && manifestLatest.isNotEmpty() &&
            versionId == manifestLatest && trailer.isFile && trailer.length() > 0
        ) {
            publish(Active(true, null), accent.value)
            return@withContext
        }

        val packDir = packForVersion(versionId, manifestLatest)
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
        if (versionId != null && latestRelease != null && versionId == latestRelease) WILDERNESS
        else if (isAtLeast(versionId)) WILDERNESS else LEGACY

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
        return pickVariant(files, portrait = context.resources.configuration.screenWidthDp <
            context.resources.configuration.screenHeightDp)
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
 * The full-screen wallpaper backdrop: image with blur + scrims, or the trailer
 * video when [ObsiWallpaper.Active.isVideo] is set.
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
