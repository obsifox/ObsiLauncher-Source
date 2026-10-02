package studio.obsifox.launcher.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Extracts a small, stable palette from the active wallpaper:
 *  - [accent]: a saturated colour sampled from the artwork (falls back to the brand orange)
 *  - [isDark]: overall luminance, used to fine-tune scrims
 *
 * The sample grid is intentionally coarse (about 40x40) so extraction stays fast even
 * for 4K wallpapers, and results are stable across nearly identical frames.
 */
data class PaletteSample(val accent: Color, val accentDeep: Color, val accentSoft: Color, val isDark: Boolean, val found: Boolean)

private const val GRID = 40
private const val HUE_BINS = 18

fun extractPalette(bitmap: ImageBitmap): PaletteSample {
    return try {
        val pm = bitmap.toPixelMap()
        val w = pm.width
        val h = pm.height
        val stepX = (w / GRID).coerceAtLeast(1)
        val stepY = (h / GRID).coerceAtLeast(1)

        val hueW = DoubleArray(HUE_BINS)
        val hueS = DoubleArray(HUE_BINS)
        val hueV = DoubleArray(HUE_BINS)
        var lumSum = 0.0
        var samples = 0
        var colored = 0

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = pm[x, y]
                val r = c.red
                val g = c.green
                val b = c.blue
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                val d = mx - mn
                val l = (mx + mn) / 2f
                val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f)).coerceAtLeast(0.0001f)
                lumSum += l.toDouble()
                samples++
                if (s > 0.28f && mx > 0.2f && mx < 0.98f) {
                    var hue = 0f
                    if (d != 0f) {
                        hue = when (mx) {
                            r -> ((g - b) / d) % 6f
                            g -> (b - r) / d + 2f
                            else -> (r - g) / d + 4f
                        } * 60f
                        if (hue < 0) hue += 360f
                    }
                    val bin = (hue / (360f / HUE_BINS)).toInt().coerceIn(0, HUE_BINS - 1)
                    val weight = (s * (1.0 - abs(l - 0.5f))).coerceAtLeast(0.0)
                    hueW[bin] += weight
                    hueS[bin] += s.toDouble()
                    hueV[bin] += mx.toDouble()
                    colored++
                }
                x += stepX
            }
            y += stepY
        }

        val isDark = lumSum / samples.coerceAtLeast(1) < 0.5
        val best = hueW.indices.maxByOrNull { hueW[it] } ?: -1
        if (best < 0 || hueW[best] <= 0.0) default(isDark) else {
            val hue = (best * (360f / HUE_BINS) + (360f / HUE_BINS) / 2f) % 360f
            val sat = (hueS[best] / hueW[best]).toFloat().coerceIn(0.45f, 0.85f)
            val brt = (hueV[best] / hueW[best]).toFloat().coerceIn(0.55f, 0.85f)
            PaletteSample(
                accent = Color.hsv(hue, sat, brt),
                accentDeep = Color.hsv(hue, (sat + 0.1f).coerceAtMost(1f), (brt * 0.68f).coerceIn(0.3f, 0.7f)),
                accentSoft = Color.hsv(hue, (sat * 0.75f).coerceIn(0.3f, 0.8f), (brt * 1.15f).coerceAtMost(0.95f)),
                isDark = isDark,
                found = true,
            )
        }
    } catch (_: Throwable) {
        default(true)
    }
}

private fun default(isDark: Boolean): PaletteSample {
    val hue = 24f // warm fox orange
    return PaletteSample(
        accent = Color.hsv(hue, 0.76f, 0.86f),
        accentDeep = Color.hsv(hue, 0.86f, 0.58f),
        accentSoft = Color.hsv(hue, 0.6f, 0.95f),
        isDark = isDark,
        found = false,
    )
}

fun Int.dp2px(density: Float): Int = (this * density).roundToInt()
