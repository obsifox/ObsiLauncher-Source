package studio.obsifox.launcher.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import studio.obsifox.launcher.core.wallpaper.Palette
import kotlin.math.max
import kotlin.math.min
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection

enum class ThemeId(val key: String) {
    VANILLA("vanilla"), WHITE("white"), BLACK("black");

    companion object {
        fun of(key: String?): ThemeId = entries.firstOrNull { it.key == key } ?: VANILLA
    }
}

/**
 * ObsiLauncher palette. Every value is Compose state, so each composable that reads one follows theme and wallpaper changes
 * without being rewired. [apply] is the only writer: it takes the chosen theme (Vanilla / White / Black) and, optionally,
 * the [Palette] of the current wallpaper, which tints the surfaces and picks the accent ("the design takes the artwork's colour").
 */
object Obsi {
    var theme by mutableStateOf(ThemeId.VANILLA); private set
    val isLight: Boolean get() = theme == ThemeId.WHITE

    // opaque tones for cards that sit on the glass panel
    var bg0 by mutableStateOf(Color(0xFF170F0F)); private set
    var bg1 by mutableStateOf(Color(0xFF241615)); private set
    var bg2 by mutableStateOf(Color(0xFF2E1E1C)); private set
    var bg3 by mutableStateOf(Color(0xFF3A2824)); private set
    var line by mutableStateOf(Color(0x38FFEFE1)); private set
    var soft by mutableStateOf(Color(0x1FFFEFE1)); private set
    var text by mutableStateOf(Color(0xFFFFF8F1)); private set
    var textDim by mutableStateOf(Color(0xFFC0B3A8)); private set

    // translucent panels drawn over the wallpaper
    var glass by mutableStateOf(Color(0x781F1211)); private set
    var glassStrong by mutableStateOf(Color(0xD11C1111)); private set

    /** Main accent. Always readable on [bg1] (text, icons and fills use it). Kept under the old name `orange`. */
    var orange by mutableStateOf(Color(0xFFDC6B32)); private set
    var orangeDeep by mutableStateOf(Color(0xFFC85322)); private set
    var accentLight by mutableStateOf(Color(0xFFF39A60)); private set

    /** Text / icon colour that reads on top of [orange]. */
    var onAccent by mutableStateOf(Color(0xFF1B0F00)); private set

    val purple = Color(0xFF8B5CF6)
    val green = Color(0xFF4ADE80)
    val red = Color(0xFFF87171)
    val yellow = Color(0xFFFACC15)

    fun apply(id: ThemeId, palette: Palette?) {
        val adapt = palette != null && palette.vivid
        val hue = if (adapt) palette!!.hue else when (id) { ThemeId.VANILLA -> 6f; ThemeId.BLACK -> 20f; ThemeId.WHITE -> 28f }
        val sat = if (adapt) palette!!.saturation else 0.7f
        val bgSat: Float
        when (id) {
            ThemeId.VANILLA -> {
                bgSat = if (adapt) (sat * 0.55f).coerceIn(0.28f, 0.50f) else 0.42f
                bg0 = Color.hsv(hue, bgSat, 0.085f); bg1 = Color.hsv(hue, bgSat, 0.13f); bg2 = Color.hsv(hue, bgSat, 0.17f); bg3 = Color.hsv(hue, bgSat, 0.23f)
                glass = Color.hsv(hue, bgSat * 0.9f, 0.115f).copy(alpha = 0.47f); glassStrong = Color.hsv(hue, bgSat * 0.9f, 0.105f).copy(alpha = 0.82f)
                line = Color(0x38FFEFE1); soft = Color(0x1FFFEFE1); text = Color(0xFFFFF8F1); textDim = Color(0xFFC0B3A8)
            }
            ThemeId.BLACK -> {
                bgSat = if (adapt) (sat * 0.22f).coerceIn(0.08f, 0.18f) else 0.10f
                bg0 = Color.hsv(hue, bgSat, 0.03f); bg1 = Color.hsv(hue, bgSat, 0.06f); bg2 = Color.hsv(hue, bgSat, 0.09f); bg3 = Color.hsv(hue, bgSat, 0.13f)
                glass = Color.hsv(hue, bgSat, 0.03f).copy(alpha = 0.69f); glassStrong = Color.hsv(hue, bgSat, 0.03f).copy(alpha = 0.92f)
                line = Color(0x29FFF5EA); soft = Color(0x1AFFF5EA); text = Color(0xFFF6F2EE); textDim = Color(0xFFB7AEA6)
            }
            ThemeId.WHITE -> {
                bgSat = if (adapt) (sat * 0.14f).coerceIn(0.04f, 0.12f) else 0.06f
                bg0 = Color.hsv(hue, bgSat, 0.95f); bg1 = Color.hsv(hue, bgSat * 0.6f, 0.985f); bg2 = Color.hsv(hue, bgSat, 0.935f); bg3 = Color.hsv(hue, bgSat * 1.3f, 0.88f)
                glass = Color.hsv(hue, bgSat, 0.975f).copy(alpha = 0.76f); glassStrong = Color.hsv(hue, bgSat, 0.985f).copy(alpha = 0.93f)
                line = Color(0x33422A1F); soft = Color(0x1F422A1F); text = Color(0xFF261A15); textDim = Color(0xFF6B5D55)
            }
        }
        theme = id

        if (adapt) {
            val v = palette!!.value.coerceIn(0.80f, 0.96f)
            val s = (sat * 1.05f).coerceIn(0.52f, 0.90f)
            orange = fitAccent(hue, s, v, bg1, light = id == ThemeId.WHITE, minContrast = 4.5)
            orangeDeep = Color.hsv(hue, min(1f, s + 0.08f), (v * 0.78f).coerceAtLeast(0.30f)).let { if (id == ThemeId.WHITE) fitAccent(hue, min(1f, s + 0.1f), v * 0.7f, bg1, true, 6.0) else it }
            accentLight = Color.hsv(hue, s * 0.62f, min(1f, v + 0.08f)).let { if (id == ThemeId.WHITE) fitAccent(hue, s * 0.8f, v, bg1, true, 3.0) else it }
        } else when (id) {
            ThemeId.VANILLA -> { orange = Color(0xFFDC6B32); orangeDeep = Color(0xFFC85322); accentLight = Color(0xFFF39A60) }
            ThemeId.WHITE -> { orange = Color(0xFFC65D2A); orangeDeep = Color(0xFFAC471D); accentLight = Color(0xFFE88B55) }
            ThemeId.BLACK -> { orange = Color(0xFFC96A36); orangeDeep = Color(0xFF9F4B24); accentLight = Color(0xFFE18A55) }
        }
        val dark = Color(0xFF1B0F00)
        onAccent = if (contrast(orange, dark) >= contrast(orange, Color.White)) dark else Color.White
    }

    /** Nudges brightness / saturation until [minContrast] against [bg] is reached (brighter on dark surfaces, darker on light ones). */
    private fun fitAccent(h: Float, s0: Float, v0: Float, bg: Color, light: Boolean, minContrast: Double): Color {
        var s = s0
        var v = v0
        repeat(40) {
            val c = Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
            if (contrast(c, bg) >= minContrast) return c
            if (light) { v = max(0.18f, v - 0.03f); s = min(0.95f, s + 0.01f) } else { v = min(1f, v + 0.02f); if (v >= 0.98f) s = max(0.22f, s - 0.03f) }
        }
        return Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    }

    /** WCAG contrast ratio between two opaque colours. */
    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble() + 0.05
        val lb = b.luminance().toDouble() + 0.05
        return max(la, lb) / min(la, lb)
    }

    init { apply(ThemeId.VANILLA, null) }
}

private val centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun TextStyle.fit(f: FontFamily): TextStyle = copy(fontFamily = f, lineHeightStyle = centered)

private fun Typography.withFamily(f: FontFamily): Typography = copy(
    displayLarge = displayLarge.fit(f), displayMedium = displayMedium.fit(f), displaySmall = displaySmall.fit(f),
    headlineLarge = headlineLarge.fit(f), headlineMedium = headlineMedium.fit(f), headlineSmall = headlineSmall.fit(f),
    titleLarge = titleLarge.fit(f), titleMedium = titleMedium.fit(f), titleSmall = titleSmall.fit(f),
    bodyLarge = bodyLarge.fit(f), bodyMedium = bodyMedium.fit(f), bodySmall = bodySmall.fit(f),
    labelLarge = labelLarge.fit(f), labelMedium = labelMedium.fit(f), labelSmall = labelSmall.fit(f),
)

@Composable
fun ObsiTheme(lang: Lang, content: @Composable () -> Unit) {
    // Vazirmatn covers Persian/Arabic and Latin, so one family gives the same look on every OS.
    val family = remember {
        FontFamily(
            Font(resource = "fonts/Vazirmatn-Regular.ttf", weight = FontWeight.Normal),
            Font(resource = "fonts/Vazirmatn-Medium.ttf", weight = FontWeight.Medium),
            Font(resource = "fonts/Vazirmatn-Bold.ttf", weight = FontWeight.Bold),
        )
    }
    val scheme = if (Obsi.isLight) lightColorScheme(
        primary = Obsi.orange, onPrimary = Obsi.onAccent,
        primaryContainer = Obsi.accentLight.copy(alpha = 0.35f), onPrimaryContainer = Obsi.text,
        secondary = Obsi.purple, onSecondary = Color.White,
        secondaryContainer = Obsi.bg3, onSecondaryContainer = Obsi.text,
        tertiary = Color(0xFF1F9D55),
        background = Obsi.bg0, onBackground = Obsi.text,
        surface = Obsi.bg1, onSurface = Obsi.text,
        surfaceVariant = Obsi.bg2, onSurfaceVariant = Obsi.textDim,
        surfaceContainer = Obsi.bg1, surfaceContainerHigh = Obsi.bg2, surfaceContainerHighest = Obsi.bg3,
        surfaceContainerLow = Obsi.bg1, surfaceContainerLowest = Obsi.bg0,
        outline = Obsi.line, outlineVariant = Obsi.line,
        error = Color(0xFFC62828), onError = Color.White,
    ) else darkColorScheme(
        primary = Obsi.orange, onPrimary = Obsi.onAccent,
        primaryContainer = Obsi.orangeDeep.copy(alpha = 0.55f), onPrimaryContainer = Obsi.text,
        secondary = Obsi.purple, onSecondary = Color.White,
        secondaryContainer = Color(0xFF3B2A6E), onSecondaryContainer = Color(0xFFE6DEFF),
        tertiary = Obsi.green,
        background = Obsi.bg0, onBackground = Obsi.text,
        surface = Obsi.bg1, onSurface = Obsi.text,
        surfaceVariant = Obsi.bg2, onSurfaceVariant = Obsi.textDim,
        surfaceContainer = Obsi.bg1, surfaceContainerHigh = Obsi.bg2, surfaceContainerHighest = Obsi.bg3,
        surfaceContainerLow = Obsi.bg1, surfaceContainerLowest = Obsi.bg0,
        outline = Obsi.line, outlineVariant = Obsi.line,
        error = Obsi.red, onError = Color(0xFF2B0000),
    )
    val base = Typography()
    val typography = remember(family) {
        base.withFamily(family).copy(
            titleLarge = base.titleLarge.fit(family).copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            titleMedium = base.titleMedium.fit(family).copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
            headlineSmall = base.headlineSmall.fit(family).copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
        )
    }
    CompositionLocalProvider(LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
