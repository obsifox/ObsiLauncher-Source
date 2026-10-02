package studio.obsifox.obsilauncher.obsi

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Extracts a representative accent colour from artwork (hue histogram,
 * same idea as the desktop palette engine).
 */
object AccentExtractor {

    fun extract(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
        val hueBuckets = IntArray(18)
        val satSum = FloatArray(18)
        var any = 0
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val c = small.getPixel(x, y)
                val hsv = FloatArray(3)
                Color.colorToHSV(c, hsv)
                val sat = hsv[1]
                val value = hsv[2]
                if (sat < 0.18f || value < 0.18f || value > 0.97f) continue
                val bucket = ((hsv[0] / 20f).roundToInt().coerceIn(0, 17))
                hueBuckets[bucket]++
                satSum[bucket] += sat
                any++
            }
        }
        small.recycle()
        if (any == 0) return 0xFFFF8A3D.toInt() // obsidian fox orange fallback

        var best = 0
        for (i in 1 until hueBuckets.size) {
            if (hueBuckets[i] > hueBuckets[best]) best = i
        }
        val hue = best * 20f + 10f
        val sat = max(0.55f, satSum[best] / max(1, hueBuckets[best]))
        val value = 0.95f
        return Color.HSVToColor(floatArrayOf(hue, sat.coerceAtMost(0.85f), value))
    }
}
