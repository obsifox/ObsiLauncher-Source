/*
 * This file is part of ObsiLauncher's UI overlay.
 * Licensed under GPL-3.0-or-later (see /NOTICE.md in the repository root).
 */

package com.movtery.zalithlauncher.obsi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shared, composable-observable theme state for the ObsiLauncher "Obsi Dynamic" mode.
 *
 * [accent] is seeded from the active version wallpaper by [ObsiWallpaper]; every
 * composable that reads it (the Material scheme in `Theme.kt`) recomposes as soon
 * as the user switches to a different game version and a different wallpaper is
 * applied. Until the first wallpaper lands (fresh install, offline) the value
 * stays at [FALLBACK_ACCENT], which maps to the Obsidian Fox palette.
 */
object ObsiTheme {
    private val TAG = "ObsiTheme"

    /** Fox-orange fallback, matches the desktop `Palette.kt` seed colour. */
    val FALLBACK_ACCENT: Color = Color(0xFFFF8A3C)

    /** Compose-observable accent colour derived from the active wallpaper. */
    val accent = mutableStateOf(FALLBACK_ACCENT)

    /** Identity of the wallpaper the accent was extracted from (pack + file name). */
    val source = mutableStateOf("")

    /**
     * Extracts a vivid accent colour from [file] and publishes it to [accent].
     * The algorithm mirrors the desktop implementation (`desktop/Palette.kt`):
     * a hue histogram over a small down-scaled sample, skipping near-grey and
     * near-black pixels, then maximising saturation/value inside the winning hue.
     */
    fun extractAccent(file: File?) {
        if (file == null || !file.exists()) return
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return

            // decode a small sample — plenty for a histogram, cheap on memory
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap: Bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return

            val histogram = IntArray(24)          // 15-degree hue buckets
            val vividness = FloatArray(24)        // saturation*value weight per bucket
            var counted = 0
            val stepX = max(1, bitmap.width / 48)
            val stepY = max(1, bitmap.height / 48)
            val hsv = FloatArray(3)
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val c = bitmap.getPixel(x, y)
                    val r = AndroidColor.red(c) / 255f
                    val g = AndroidColor.green(c) / 255f
                    val b = AndroidColor.blue(c) / 255f
                    val maxC = max(r, max(g, b))
                    val minC = min(r, min(g, b))
                    val v = maxC
                    val s = if (maxC <= 0f) 0f else (maxC - minC) / maxC
                    // ignore greys / near black / blown highlights — they carry no hue identity
                    if (v > 0.18f && v < 0.97f && s > 0.22f) {
                        AndroidColor.RGBToHSV((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv)
                        val bucket = ((hsv[0] / 15f).toInt()).coerceIn(0, 23)
                        histogram[bucket] += 1
                        vividness[bucket] += s * v
                        counted++
                    }
                    x += stepX
                }
                y += stepY
            }
            bitmap.recycle()

            if (counted < 12) return            // almost monochrome artwork — keep current accent
            var best = -1
            var bestScore = -1f
            for (i in histogram.indices) {
                // smooth neighbours a little so noisy gradients still cluster
                val score = (histogram[i] + 0.5f * histogram[(i + 23) % 24] + 0.5f * histogram[(i + 1) % 24]) *
                    (1f + vividness[i] / max(1, histogram[i]))
                if (score > bestScore) { bestScore = score; best = i }
            }
            if (best < 0) return

            val hsvOut = floatArrayOf(best * 15f + 7.5f, 0.72f, 0.92f)
            val rgb = AndroidColor.HSVToColor(hsvOut)
            val color = Color(rgb).copy(alpha = 1f)

            accent.value = color
            source.value = file.name
            Logger.info(TAG, "Obsi accent extracted from ${file.name}: #%06X".format(rgb))
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to extract accent from wallpaper", e)
        }
    }

    /** Perceptual luminance — mirrors the desktop contrast helper. */
    fun luminance(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    /** Hue distance in degrees, used to skip near-identical re-extractions. */
    fun hueDistance(a: Color, b: Color): Float {
        val ha = hueOf(a); val hb = hueOf(b)
        val d = abs(ha - hb) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun hueOf(c: Color): Float {
        val maxC = max(c.red, max(c.green, c.blue))
        val minC = min(c.red, min(c.green, c.blue))
        if (maxC - minC <= 0f) return 0f
        val d = maxC - minC
        val h = when (maxC) {
            c.red -> ((c.green - c.blue) / d) % 6f
            c.green -> (c.blue - c.red) / d + 2f
            else -> (c.red - c.green) / d + 4f
        }
        return (h * 60f + 360f) % 360f
    }
}
