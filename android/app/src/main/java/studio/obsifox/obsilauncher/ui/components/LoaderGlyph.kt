package studio.obsifox.obsilauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import studio.obsifox.obsilauncher.core.loaders.LoaderType

/**
 * Small drawn glyphs for every loader — no font dependencies, always crisp.
 * Used by the loader picker tiles on the Version screen.
 */
@Composable
fun LoaderGlyph(type: LoaderType, modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        when (type) {
            LoaderType.VANILLA -> {
                // grass block
                drawRect(Color(0xFF7A4E2A), size = Size(w, h))
                drawRect(Color(0xFF6FAE3E), size = Size(w, h * 0.3f))
                drawRect(Color(0x33000000), size = Size(w, h), style = Stroke(stroke))
            }
            LoaderType.FABRIC -> {
                // feather: curved spine + barbs
                drawLine(color, Offset(w * 0.15f, h * 0.85f), Offset(w * 0.8f, h * 0.15f), stroke)
                drawLine(color, Offset(w * 0.45f, h * 0.5f), Offset(w * 0.7f, h * 0.55f), stroke)
                drawLine(color, Offset(w * 0.3f, h * 0.65f), Offset(w * 0.55f, h * 0.72f), stroke)
            }
            LoaderType.FORGE -> {
                // anvil
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.12f, h * 0.28f),
                    size = Size(w * 0.76f, h * 0.22f),
                    cornerRadius = CornerRadius(h * 0.06f),
                    style = Stroke(stroke),
                )
                drawRect(color, topLeft = Offset(w * 0.4f, h * 0.5f), size = Size(w * 0.2f, h * 0.24f), style = Stroke(stroke))
                drawRect(color, topLeft = Offset(w * 0.26f, h * 0.74f), size = Size(w * 0.48f, h * 0.12f), style = Stroke(stroke))
            }
            LoaderType.NEOFORGE -> {
                // anvil with a spark
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.12f, h * 0.3f),
                    size = Size(w * 0.76f, h * 0.2f),
                    cornerRadius = CornerRadius(h * 0.06f),
                    style = Stroke(stroke),
                )
                drawRect(color, topLeft = Offset(w * 0.4f, h * 0.5f), size = Size(w * 0.2f, h * 0.22f), style = Stroke(stroke))
                drawRect(color, topLeft = Offset(w * 0.26f, h * 0.72f), size = Size(w * 0.48f, h * 0.12f), style = Stroke(stroke))
                drawCircle(color, radius = stroke * 1.4f, center = Offset(w * 0.85f, h * 0.16f))
            }
            LoaderType.QUILT -> {
                // 4 quilt patches
                val gap = 1.2.dp.toPx()
                val cell = (w - gap) / 2f
                drawRect(color, Offset(0f, 0f), Size(cell, cell), style = Stroke(stroke))
                drawRect(color, Offset(cell + gap, 0f), Size(cell, cell), style = Stroke(stroke))
                drawRect(color, Offset(0f, cell + gap), Size(cell, cell), style = Stroke(stroke))
                drawRect(color, Offset(cell + gap, cell + gap), Size(cell, cell), style = Stroke(stroke))
            }
            LoaderType.OPTIFINE -> {
                // glasses (the OptiFine mark)
                drawCircle(color, radius = h * 0.22f, center = Offset(w * 0.3f, h * 0.55f), style = Stroke(stroke))
                drawCircle(color, radius = h * 0.22f, center = Offset(w * 0.7f, h * 0.55f), style = Stroke(stroke))
                drawLine(color, Offset(w * 0.3f + h * 0.22f, h * 0.5f), Offset(w * 0.7f - h * 0.22f, h * 0.5f), stroke)
            }
            else -> {
                // generic cube
                drawRect(color, Offset(w * 0.15f, h * 0.15f), Size(w * 0.7f, h * 0.7f), style = Stroke(stroke))
            }
        }
    }
}
