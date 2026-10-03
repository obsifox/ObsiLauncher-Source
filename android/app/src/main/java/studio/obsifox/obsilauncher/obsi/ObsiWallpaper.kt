package studio.obsifox.obsilauncher.obsi

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.core.BackgroundMode
import studio.obsifox.obsilauncher.core.ObsiSettings
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.net.Http
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.ln

/**
 * ObsiLauncher background engine (written from scratch).
 *
 * Two background types, exactly as briefed:
 *  - WALLPAPER: version-driven official artwork — Minecraft >= 1.12.2 uses the
 *    *Wilderness Bound* pack, older versions use the *Minecraft PC bundle*
 *    (both fetched from minecraft.net). The accent colour extracted from the
 *    active artwork drives the "Obsi Dynamic" theme through [accent].
 *  - VIDEO: the trailer is BUNDLED in the APK (res/raw/video_wallpaper.mp4,
 *    H.264 — plays everywhere). It runs once, then the version wallpaper
 *    rests on screen for 45 seconds, then the video plays again — an endless
 *    cycle. Only the latest Minecraft release (26.3 or newer at minimum)
 *    gets the video; every other version falls back to wallpaper artwork.
 *
 * A user-picked custom background always wins over both.
 */
class ObsiWallpaper(private val context: Context, val settings: ObsiSettings) {

    data class Active(
        val isVideo: Boolean,
        val imagePath: String?,
        val resId: Int? = null,
        /** v1.12.0 — the version this state belongs to; the video cycle keys
         *  off it so switching versions lands on the photo immediately. */
        val versionId: String? = null,
    )

    val active = MutableStateFlow(Active(isVideo = false, imagePath = null))
    val accent = MutableStateFlow(0xFFFF8A3D.toInt())

    private val latestRelease = MutableStateFlow("")

    /** keeps the newest sync alive — an older, slower sync must never win. */
    private var syncJob: Job? = null

    /** artwork resolved per version this session — instant re-switching. */
    private val resolved = HashMap<String, Active>()

    /**
     * Call whenever the selected version changes (and once at startup).
     *
     * v1.11.0 — the switch is now INSTANT: the bundled per-era artwork is
     * published immediately, before any network call, and the heavyweight
     * minecraft.net pack (manifest fetch + zip download) only *upgrades*
     * the background afterwards. Switching 1.12.2 -> 1.16.5 no longer
     * waits on Mojang or on a wallpaper download.
     */
    fun sync(versionId: String?) {
        syncJob?.cancel()
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            apply(context, settings, versionId)
        }
    }

    private suspend fun apply(context: Context, settings: ObsiSettings, versionId: String?) =
        withContext(Dispatchers.IO) {
            // ---- 1) INSTANT pass: everything that needs zero network -------
            val custom = settings.customWallpaperValue
            if (custom.isNotBlank() && File(custom).isFile) {
                publish(Active(false, custom), extractAccent(File(custom)))
                return@withContext
            }

            val cachedLatest = latestRelease.value.ifBlank { FALLBACK_LATEST }
            val videoNow = settings.backgroundModeValue == BackgroundMode.VIDEO &&
                versionId != null && versionId == cachedLatest
            val cacheKey = "${versionId ?: "none"}|${if (videoNow) "v" else "w"}"
            resolved[cacheKey]?.let {
                publish(it, it.imagePath?.let { p -> extractAccent(File(p)) } ?: accent.value)
                return@withContext
            }
            // bundled era art covers every version offline — publish it NOW
            publish(
                Active(
                    isVideo = videoNow,
                    imagePath = null,
                    resId = bundledArtFor(versionId),
                    versionId = versionId,
                ),
                accent.value,
            )

            // ---- 2) UPGRADE pass: network-dependent, best effort -----------
            val latest = fetchLatestRelease().ifBlank { FALLBACK_LATEST }
            val video = settings.backgroundModeValue == BackgroundMode.VIDEO &&
                versionId != null && versionId == latest

            if (video) {
                val artwork = ensureArtwork(packForVersion(versionId, latest), versionId)
                val acc = artwork?.let(::extractAccent) ?: accent.value
                val state = Active(
                    isVideo = true,
                    imagePath = artwork?.absolutePath,
                    resId = if (artwork == null) bundledArtFor(versionId) else null,
                    versionId = versionId,
                )
                resolved[cacheKey] = state
                publish(state, acc)
                return@withContext
            }

            // the remote latest release differs from the cached one and flips
            // the video gate off — re-publish the plain wallpaper instantly
            if (!video && videoNow) {
                publish(Active(false, null, bundledArtFor(versionId), versionId), accent.value)
            }

            val packDir = packForVersion(versionId, latest)
            val artwork = ensureArtwork(packDir, versionId)
            if (artwork == null) return@withContext // keep the bundled art
            val state = Active(false, artwork.absolutePath, null, versionId)
            resolved[cacheKey] = state
            publish(state, extractAccent(artwork))
        }

    private fun publish(a: Active, newAccent: Int) {
        active.value = a
        accent.value = newAccent
    }

    /** Latest release according to Mojang's manifest (cached for the session). */
    private fun fetchLatestRelease(): String {
        latestRelease.value.takeIf { it.isNotEmpty() }?.let { return it }
        val v = runCatching {
            JSONObject(Http.getOrNull(LATEST_URL) ?: "{}")
                .optJSONObject("latest")?.optString("release").orEmpty()
        }.getOrDefault("")
        latestRelease.value = v
        return v
    }

    /** Latest known release without touching the network again. */
    fun cachedLatestRelease(): String = latestRelease.value

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

        /** Offline fallback for "the latest release" — the video gate never dies. */
        const val FALLBACK_LATEST = "26.3"
    }
}

/** Bundled offline artwork for a version era — background + version cards. */
fun bundledArtFor(mcVersion: String?): Int = artForVersion(mcVersion)

/**
 * One official minecraft.net wallpaper per era (bundled in the APK).
 * Snapshots and unknown ids fall back to the current era's art.
 */
fun artForVersion(mcVersion: String?): Int {
    val v = mcVersion?.trim()?.removePrefix("v") ?: return R.drawable.wp_wilderness_bound
    val base = v.substringBefore('-').substringBefore('+')
    fun part(i: Int): Int = base.split('.').getOrNull(i)?.filter(Char::isDigit)?.toIntOrNull() ?: -1
    val major = part(0); val minor = part(1); val patch = part(2)
    return when {
        major == 26 -> when {
            minor >= 3 -> R.drawable.wp_wilderness_bound
            minor == 2 -> R.drawable.wp_chaos_cubed
            minor == 1 -> R.drawable.wp_tiny_takeover
            else -> R.drawable.wp_wilderness_bound
        }
        major == 1 -> when (minor) {
            21 -> when {
                patch >= 9 -> R.drawable.wp_copper
                patch >= 6 -> R.drawable.wp_skies
                patch == 5 -> R.drawable.wp_spring
                patch in 3..4 -> R.drawable.wp_garden
                patch in 2..3 -> R.drawable.wp_bundles
                else -> R.drawable.wp_tricky
            }
            20 -> R.drawable.wp_trails
            19 -> R.drawable.wp_wild
            18 -> R.drawable.wp_caves2
            17 -> R.drawable.wp_caves1
            16 -> R.drawable.wp_nether
            15 -> R.drawable.wp_buzzy
            14 -> R.drawable.wp_village
            13 -> R.drawable.wp_aquatic
            12 -> R.drawable.wp_worldcolor
            else -> R.drawable.wp_java
        }
        else -> R.drawable.wp_java
    }
}

/** The official release name for a parent version group ("1.21" -> Tricky Trials). */
fun releaseNameFor(parent: String): String = when {
    parent.startsWith("26.") -> when (parent) {
        "26.3" -> "Wilderness Bound"
        "26.2" -> "Chaos Cubed"
        "26.1" -> "Tiny Takeover"
        else -> ""
    }
    parent.startsWith("1.21") -> "Tricky Trials"
    parent.startsWith("1.20") -> "Trails & Tales"
    parent.startsWith("1.19") -> "The Wild Update"
    parent.startsWith("1.18") -> "Caves & Cliffs: Part II"
    parent.startsWith("1.17") -> "Caves & Cliffs: Part I"
    parent.startsWith("1.16") -> "The Nether Update"
    parent.startsWith("1.15") -> "Buzzy Bees"
    parent.startsWith("1.14") -> "Village & Pillage"
    parent.startsWith("1.13") -> "Update Aquatic"
    parent.startsWith("1.12") -> "World of Color"
    parent.startsWith("1.11") -> "Exploration Update"
    parent.startsWith("1.10") -> "Frostburn Update"
    parent.startsWith("1.9") -> "Combat Update"
    parent.startsWith("1.8") -> "Bountiful Update"
    else -> ""
}

/**
 * The full-screen backdrop: the bundled trailer cycling with the version
 * wallpaper when [ObsiWallpaper.Active.isVideo] is set, otherwise the static
 * wallpaper with scrims.
 */
@Composable
fun ObsiWallpaperLayer(wallpaper: ObsiWallpaper, blurPx: Int, modifier: Modifier = Modifier) {
    val active by wallpaper.active.collectAsState()
    val muted by wallpaper.settings.videoMuted.collectAsState()

    Box(modifier.fillMaxSize().background(Color(0xFF0B0908))) {
        val path = active.imagePath
        when {
            active.isVideo -> VideoCycleLayer(
                restImagePath = path,
                restResId = active.resId,
                muted = muted,
                versionKey = active.versionId,
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
        // bundled artwork branch (resId on the delegated value needs a local)
        val resId = active.resId
        if (!active.isVideo && active.imagePath == null && resId != null) {
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurPx > 0) Modifier.blur(blurPx.dp()) else Modifier),
            )
        }
        // v1.9.0: the artwork stays CLEAR — only a light veil darkens it a
        // touch, gently deeper at the bottom where the dashboard sits
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x26000000), Color(0x1F000000), Color(0x8C000000))
                    )
                )
        )
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

/** Phases of the v1.10.0 background cycle. */
private enum class VideoPhase { PLAYING, RESTING }

/** the wallpaper eases in over the last second of the video (briefed: the
 *  final 1 to 0.5 s, slowly), so the cut to the photo is seamless. */
private const val VIDEO_FADE_MS = 1_200L

/**
 * The background cycle, exactly as briefed:
 * the version wallpaper holds the screen -> the bundled video plays ONCE,
 * fading into the wallpaper across its last second -> 10 s rest -> the video
 * starts AGAIN FROM ZERO. Endless loop, zero downloads.
 *
 * v1.12.0 — switching versions lands DIRECTLY on that version's photo and
 * restarts the schedule from there ([versionKey]); every replay begins at
 * 0:00, never mid-way.
 * v1.13.0 — the rest window between two trailer passes is 10 s (was 45 s).
 */
@Composable
private fun VideoCycleLayer(
    restImagePath: String?,
    restResId: Int?,
    muted: Boolean,
    versionKey: String?,
    modifier: Modifier = Modifier,
) {
    // always ENTER on the photo: the video takes over on the same schedule
    var phase by remember { mutableStateOf(VideoPhase.RESTING) }
    var cycle by remember { mutableStateOf(0) }

    // version switch -> the new version's photo, immediately, with a fresh
    // rest timer; the next video pass then starts from zero (new cycle key)
    LaunchedEffect(versionKey) {
        phase = VideoPhase.RESTING
        cycle += 1
    }

    when (phase) {
        VideoPhase.PLAYING -> key(cycle) {
            var fade by remember { mutableStateOf(0f) }
            BundledVideoSurface(
                videoRes = R.raw.video_wallpaper,
                muted = muted,
                onEnded = { phase = VideoPhase.RESTING },
                onRemaining = { ms ->
                    fade = when {
                        ms <= 0L -> 1f
                        ms >= VIDEO_FADE_MS -> 0f
                        else -> 1f - ms.toFloat() / VIDEO_FADE_MS
                    }
                },
                modifier = modifier,
            )
            // the slow crossfade: the version wallpaper rises over the
            // video's final second, then the RESTING phase continues it
            Box(modifier.graphicsLayer { alpha = fade }) {
                RestArtwork(restImagePath, restResId, Modifier.fillMaxSize())
            }
        }
        VideoPhase.RESTING -> {
            RestArtwork(restImagePath, restResId, modifier)
            LaunchedEffect(cycle) {
                delay(10_000L) // v1.13.0 — the trailer replays after 10 s
                cycle += 1
                phase = VideoPhase.PLAYING
            }
        }
    }
}

/** The static version artwork shown while the video rests. */
@Composable
private fun RestArtwork(imagePath: String?, resId: Int?, modifier: Modifier = Modifier) {
    when {
        imagePath != null -> {
            val bitmap = remember(imagePath) {
                runCatching { BitmapFactory.decodeFile(imagePath)?.asImageBitmap() }.getOrNull()
            }
            if (bitmap != null) {
                Image(bitmap, null, contentScale = ContentScale.Crop, modifier = modifier)
            } else if (resId != null) {
                Image(painterResource(resId), null, contentScale = ContentScale.Crop, modifier = modifier)
            }
        }
        resId != null -> {
            Image(painterResource(resId), null, contentScale = ContentScale.Crop, modifier = modifier)
        }
    }
}

/**
 * One pass of the bundled trailer on a SurfaceView. Not self-looping —
 * completion hands over to the wallpaper rest (10 s since v1.13.0). Sound
 * follows the user's mute choice and playback pauses with the app lifecycle.
 * v1.12.0 — [onRemaining] reports the ms left, powering the slow
 * wallpaper crossfade over the final second; every pass starts at 0:00.
 */
@Composable
private fun BundledVideoSurface(
    videoRes: Int,
    muted: Boolean,
    onEnded: () -> Unit,
    onRemaining: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    // the mute toggle applies to the running player immediately
    LaunchedEffect(muted, player) {
        player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
    }
    // position polling for the end-of-video crossfade
    LaunchedEffect(player) {
        val mp = player ?: return@LaunchedEffect
        while (true) {
            val probe = runCatching {
                if (mp.duration <= 0) Long.MAX_VALUE else (mp.duration - mp.currentPosition).toLong()
            }.getOrNull() ?: break // released — stop polling
            onRemaining(probe.coerceAtLeast(0L))
            delay(100)
        }
    }
    // no sound (or decoding) while the launcher itself is hidden
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> runCatching { player?.takeIf { it.isPlaying }?.pause() }
                Lifecycle.Event.ON_RESUME -> runCatching { player?.start() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    DisposableEffect(videoRes) {
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
                            mp.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                    .build(),
                            )
                            val afd = ctx.resources.openRawResourceFd(videoRes)
                            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                            mp.setSurface(holder.surface)
                            mp.isLooping = false // one pass -> 45 s wallpaper rest -> again
                            mp.setOnCompletionListener { onEnded() }
                            val v = if (muted) 0f else 1f
                            mp.setVolume(v, v)
                            mp.setOnPreparedListener { it.start() }
                            mp.prepareAsync()
                            player = mp
                        }.onFailure {
                            // decoder hiccup: rest on the wallpaper, retry next cycle
                            onEnded()
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
