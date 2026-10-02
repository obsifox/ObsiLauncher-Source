package studio.obsifox.launcher.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path

private val bitmapCache = object : LinkedHashMap<Path, ImageBitmap>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, ImageBitmap>?) = size > 4
}

/** Decodes a wallpaper file once and keeps the last few, so switching between profiles of the same update is instant. */
internal fun loadBitmap(path: Path): ImageBitmap? {
    synchronized(bitmapCache) { bitmapCache[path] }?.let { return it }
    return runCatching { Image.makeFromEncoded(Files.readAllBytes(path)).toComposeImageBitmap() }.getOrNull()
        ?.also { synchronized(bitmapCache) { bitmapCache[path] = it } }
}

// the picture itself is slightly toned down, exactly like the design: saturate(.91) brightness(.83)
private val dim = ColorMatrix().apply {
    setToSaturation(0.91f)
    timesAssign(ColorMatrix().apply { setToScale(0.83f, 0.83f, 0.83f, 1f) })
}

/**
 * The full-window artwork of the selected profile, fitted with "cover" (cropped, never stretched), cross-fading when it changes,
 * plus the soft shades that keep the navigation and the Play button readable on any picture.
 */
@Composable
fun WallpaperLayer(app: AppController, modifier: Modifier = Modifier) {
    val backdrop by app.backdrop.collectAsState()
    val loaded by produceState<Pair<Path?, ImageBitmap?>>(null to null, backdrop.file) {
        val f = backdrop.file
        value = f to (f?.let { withContext(Dispatchers.IO) { loadBitmap(it) } })
    }
    Box(modifier.fillMaxSize().background(Obsi.bg0).onSizeChanged { app.setViewport(it.width, it.height) }) {
        Crossfade(loaded, animationSpec = tween(450), label = "wallpaper") { (_, bmp) ->
            if (bmp != null) {
                Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.Center, colorFilter = ColorFilter.colorMatrix(dim))
            } else {
                // nothing downloaded yet (first start, offline): a warm gradient in the theme colours instead of a blank window
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Obsi.orange.copy(alpha = 0.22f), Obsi.bg0), radius = 1100f)))
            }
        }
        Shades(Obsi.isLight)
    }
}

@Composable
private fun Shades(light: Boolean) {
    val ink = Color(0xFF110A0A)
    val vertical = if (light) listOf(0f to .35f, .28f to .04f, .60f to .06f, 1f to .70f)
    else listOf(0f to .52f, .23f to .13f, .48f to .02f, .65f to .16f, 1f to .86f)
    val horizontal = if (light) listOf(0f to .22f, .45f to .02f, .70f to .04f, 1f to .20f)
    else listOf(0f to .44f, .42f to .08f, .70f to .08f, 1f to .28f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(*vertical.map { it.first to ink.copy(alpha = it.second) }.toTypedArray())))
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(*horizontal.map { it.first to ink.copy(alpha = it.second) }.toTypedArray())))
}

/** Translucent rounded surface that floats over the wallpaper. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    radius: Dp = 17.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape).background(if (strong) Obsi.glassStrong else Obsi.glass).border(1.dp, Obsi.line, shape), content = content)
}
