package studio.obsifox.launcher.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha as graphicsAlpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path

/**
 * Full-bleed wallpaper that sits behind the whole UI and drives the dynamic accent colour.
 *
 * Selection rules:
 *  - custom image (settings.wallpaperCustom) wins when set and readable
 *  - otherwise the official pack that matches the selected profile's Minecraft version
 *    (>= 1.12.2 -> Wilderness Bound, older -> PC bundle)
 *  - when the mode is "video" and the profile runs the latest release, the trailer plays
 *    on top of the still image (image stays as poster/fallback)
 */
class WallpaperUiState(
    val bitmap: ImageBitmap? = null,
    val sample: PaletteSample? = null,
    val videoFile: Path? = null,
    val packLabel: String = "",
)

@Composable
fun rememberWallpaperState(app: AppController): WallpaperUiState {
    val settings by app.core.settings.flow.collectAsState()
    val instances by app.core.instances.flow.collectAsState()
    val selected = instances.firstOrNull { it.id == settings.selectedInstanceId } ?: instances.firstOrNull()
    val mcVersion = selected?.mcVersion.orEmpty()

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val containerPx = windowInfo.containerSize

    var state by remember { mutableStateOf(WallpaperUiState()) }
    var latestId by remember { mutableStateOf<String?>(null) }

    // latest release id (for the video rule) — refreshed when the version changes
    LaunchedEffect(mcVersion) {
        latestId = runCatching { app.wallpapers.latestReleaseId() }.getOrNull()
    }

    val wantVideo = settings.wallpaperMode == "video" && latestId != null && mcVersion == latestId

    LaunchedEffect(settings.wallpaperMode, settings.wallpaperCustom, mcVersion, containerPx.width, containerPx.height, latestId) {
        if (mcVersion.isBlank()) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val custom: Path? = settings.wallpaperCustom?.takeIf { it.isNotBlank() }?.let { p ->
                    java.nio.file.Path.of(p).takeIf { Files.isRegularFile(it) }
                }
                if (custom != null) {
                    custom to WallpaperService.Pack.WILDERNESS
                } else {
                    val pack = app.wallpapers.packForVersion(mcVersion)
                    val resolved = app.wallpapers.ensurePack(pack)
                    val variant = app.wallpapers.bestVariant(
                        resolved.variants,
                        containerPx.width.coerceAtLeast(1280),
                        containerPx.height.coerceAtLeast(720),
                    )?.file ?: return@runCatching null
                    variant to pack
                }
            }.getOrNull()
        } ?: return@LaunchedEffect

        val (file, pack) = result
        val bmp = withContext(Dispatchers.IO) {
            runCatching { Image.makeFromEncoded(Files.readAllBytes(file)).toComposeImageBitmap() }.getOrNull()
        } ?: return@LaunchedEffect
        val video = if (wantVideo) app.wallpapers.videoFile() else null
        state = WallpaperUiState(
            bitmap = bmp,
            sample = extractPalette(bmp),
            videoFile = video,
            packLabel = pack.label,
        )
    }

    return state
}

@Composable
fun WallpaperSurface(state: WallpaperUiState, palette: ObsiPalette, content: @Composable () -> Unit) {
    val app = LocalApp.current
    val settings by app.core.settings.flow.collectAsState()
    var shown by remember { mutableStateOf(state.bitmap) }
    LaunchedEffect(state.bitmap) { if (state.bitmap != null) shown = state.bitmap }

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.bg0)) {
        val bmp = shown
        val alpha by animateFloatAsState(if (bmp != null) 1f else 0f, tween(650), label = "wallpaperFade")
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize().graphicsAlpha(alpha), contentScale = ContentScale.Crop)
        }
        // readability scrims (top bar + bottom actions)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to palette.scrim,
                    0.45f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to palette.scrim,
                )
            )
        )
        val video = state.videoFile
        if (video != null) {
            VideoBackground(video, Modifier.fillMaxWidth().fillMaxHeight(), muted = !settings.wallpaperSound)
        }
        content()
    }
}

val LocalWallpaperPackLabel = androidx.compose.runtime.staticCompositionLocalOf { "" }

@Composable
fun ObsiAppThemeHost(app: AppController, content: @Composable () -> Unit) {
    val strings by app.strings.collectAsState()
    val settings by app.core.settings.flow.collectAsState()
    val state = rememberWallpaperState(app)
    val palette = themePalette(settings.themeMode, if (settings.dynamicTint) state.sample else null)
    ObsiTheme(strings.lang, palette) {
        androidx.compose.runtime.CompositionLocalProvider(LocalWallpaperPackLabel provides state.packLabel) {
            WallpaperSurface(state, palette) { content() }
        }
    }
}
