package studio.obsifox.obsilauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.core.loaders.LoaderType

/**
 * The icon shown next to a version everywhere (home chip, cards):
 * the vanilla grass block for vanilla builds, the loader's own mark
 * (official logos bundled from FabricMC / MinecraftForge / NeoForged /
 * Team-OptiFine) for loader builds.
 */
@Composable
fun LoaderIcon(type: LoaderType, modifier: Modifier = Modifier) {
    when (type) {
        LoaderType.VANILLA -> GrassBlockIcon(modifier)
        LoaderType.FABRIC -> Image(
            painterResource(R.drawable.loader_fabric),
            contentDescription = null,
            modifier = modifier,
        )
        LoaderType.FORGE -> Image(
            painterResource(R.drawable.loader_forge),
            contentDescription = null,
            modifier = modifier,
        )
        LoaderType.NEOFORGE -> Image(
            painterResource(R.drawable.loader_neoforge),
            contentDescription = null,
            modifier = modifier,
        )
        LoaderType.OPTIFINE -> Image(
            painterResource(R.drawable.loader_optifine),
            contentDescription = null,
            modifier = modifier,
        )
        else -> LoaderGlyph(type, modifier, color = Color(0xFFF4EFEA))
    }
}

/** The classic 8-bit grass block — the mark of a vanilla install. */
@Composable
fun GrassBlockIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF7A4E2A), size = Size(w, h))
        // pixel specks in the dirt
        val p = w / 8f
        drawRect(Color(0xFF5E3A1E), topLeft = Offset(p, h * 0.55f), size = Size(p, p))
        drawRect(Color(0xFF93603A), topLeft = Offset(p * 4, h * 0.7f), size = Size(p, p))
        drawRect(Color(0xFF5E3A1E), topLeft = Offset(p * 6, h * 0.5f), size = Size(p, p))
        drawRect(Color(0xFF5E3A1E), topLeft = Offset(p * 2, h * 0.82f), size = Size(p, p))
        // grass cap
        drawRect(Color(0xFF6FAE3E), size = Size(w, h * 0.3f))
        drawRect(Color(0xFF8CCB54), topLeft = Offset(0f, 0f), size = Size(w, h * 0.12f))
        // rim
        drawRect(
            Color(0x33000000),
            size = Size(w, h),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
