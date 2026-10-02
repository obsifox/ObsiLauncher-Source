package studio.obsifox.launcher.core.wallpaper

import kotlinx.serialization.Serializable
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The colour character of a wallpaper. The launcher UI uses it to tint its surfaces and to pick an accent colour
 * ("the design takes the colour of the artwork"). Colours are ARGB ints so the data stays UI-toolkit independent.
 */
@Serializable
data class Palette(
    /** Dominant hue in degrees, 0..360. */
    val hue: Float,
    /** Typical saturation / brightness of the dominant colour, 0..1. */
    val saturation: Float,
    val value: Float,
    /** False for grey / near-black artwork: there is no colour to borrow, callers keep their default accent. */
    val vivid: Boolean,
    /** Mean brightness 0..1 (dark scenes need less dimming). */
    val luminance: Float,
    val accent: Int,
    val accentStrong: Int,
    val accentLight: Int,
)

object PaletteExtractor {
    private const val BINS = 36
    private const val DEFAULT_HUE = 24f // the launcher's own warm orange

    fun fromImage(img: BufferedImage): Palette {
        val w = img.width
        val h = img.height
        val step = max(1, max(w, h) / 320) // ~320 samples on the long edge is plenty for colour statistics
        val hsb = FloatArray(3)
        val hist = DoubleArray(BINS)
        var samples = 0
        var lumSum = 0.0
        val vivid = ArrayList<FloatArray>() // h, s, v, weight

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val rgb = img.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                lumSum += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
                samples++
                Color.RGBtoHSB(r, g, b, hsb)
                val s = hsb[1]
                val v = hsb[2]
                if (s >= 0.18f && v >= 0.18f) {
                    val weight = s.toDouble().pow(1.3) * v.toDouble().pow(0.7)
                    hist[(hsb[0] * BINS).toInt().coerceIn(0, BINS - 1)] += weight
                    vivid += floatArrayOf(hsb[0] * 360f, s, v, weight.toFloat())
                }
                x += step
            }
            y += step
        }
        val luminance = if (samples == 0) 0.4f else (lumSum / samples).toFloat()
        val totalWeight = hist.sum()
        // not enough colourful pixels (below ~1.5 % of the picture): keep the default look
        if (samples == 0 || vivid.size < samples * 0.015 || totalWeight <= 0.0) return build(DEFAULT_HUE, 0.72f, 0.90f, false, luminance)

        // smooth the histogram with its neighbours so one noisy bin does not win, then take the strongest bin
        var peak = 0
        var best = -1.0
        for (i in 0 until BINS) {
            val sm = hist[(i + BINS - 1) % BINS] * 0.25 + hist[i] * 0.5 + hist[(i + 1) % BINS] * 0.25
            if (sm > best) { best = sm; peak = i }
        }
        val center = (peak + 0.5f) * (360f / BINS)

        // circular mean of the hues around the peak (+/-40 degrees), plus their typical saturation / brightness
        var sx = 0.0; var sy = 0.0; var ws = 0.0; var sSum = 0.0; var vSum = 0.0
        for (p in vivid) {
            var d = Math.abs(p[0] - center)
            if (d > 180f) d = 360f - d
            if (d > 40f) continue
            val wgt = p[3].toDouble()
            val rad = p[0] * PI / 180.0
            sx += cos(rad) * wgt; sy += sin(rad) * wgt
            ws += wgt; sSum += p[1] * wgt; vSum += p[2] * wgt
        }
        if (ws <= 0.0) return build(DEFAULT_HUE, 0.72f, 0.90f, false, luminance)
        var hue = (atan2(sy / ws, sx / ws) * 180.0 / PI).toFloat()
        if (hue < 0f) hue += 360f
        return build(hue, (sSum / ws).toFloat(), (vSum / ws).toFloat(), true, luminance)
    }

    private fun build(hue: Float, sat: Float, value: Float, vivid: Boolean, luminance: Float): Palette {
        // an accent has to be clearly coloured and bright enough to read on dark glass
        val s = (sat * 1.05f).coerceIn(0.52f, 0.90f)
        val v = max(value, 0.80f).coerceAtMost(0.96f)
        return Palette(
            hue = hue, saturation = sat, value = value, vivid = vivid, luminance = luminance,
            accent = hsvToArgb(hue, s, v),
            accentStrong = hsvToArgb(hue, min(1f, s + 0.08f), v * 0.80f),
            accentLight = hsvToArgb(hue, s * 0.62f, min(1f, v + 0.08f)),
        )
    }

    fun hsvToArgb(hueDeg: Float, s: Float, v: Float): Int =
        Color.HSBtoRGB(((hueDeg % 360f) + 360f) % 360f / 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)) or (0xFF shl 24)

    /** Euclidean distance helper for tests / callers that compare accents. */
    fun distance(a: Int, b: Int): Double {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}
