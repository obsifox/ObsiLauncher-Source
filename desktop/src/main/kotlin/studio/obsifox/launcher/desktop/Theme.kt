package studio.obsifox.launcher.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * ObsiLauncher v2 palette — glass surfaces over a full-bleed wallpaper.
 *
 * Every surface colour carries its own alpha; components draw them on top of the
 * wallpaper so the artwork softly shines through ("frosted glass" look).
 */
data class ObsiPalette(
    // solid backdrop (window base, under the wallpaper)
    val bg0: Color,
    // glass surface fills (semi-transparent)
    val glass: Color,
    val glassHigh: Color,
    val glassLow: Color,
    val glassInput: Color,
    // hairlines
    val line: Color,
    val lineSoft: Color,
    // accents
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val secondary: Color,
    // text
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    // semantic
    val good: Color,
    val warn: Color,
    val bad: Color,
    // scrim over the wallpaper for text readability
    val scrim: Color,
    // metadata
    val dark: Boolean,
) {
    companion object {
        /** Warm charcoal glass with the restrained fox-orange accent (default "Vanilla" theme). */
        fun vanilla(a: PaletteSample? = null): ObsiPalette {
            val accent = a?.accent ?: Color(0xFFDC6B32)
            val deep = a?.accentDeep ?: Color(0xFFC85322)
            val soft = a?.accentSoft ?: Color(0xFFF39A60)
            return ObsiPalette(
                bg0 = Color(0xFF120D08),
                glass = Color(0xCC1C140C),
                glassHigh = Color(0xE6251A10),
                glassLow = Color(0x99171009),
                glassInput = Color(0xB31F150B),
                line = Color(0x59DC8A55),
                lineSoft = Color(0x2EDC8A55),
                accent = accent, accentDeep = deep, accentSoft = soft, onAccent = Color(0xFF1B0F00),
                secondary = a?.found?.let { if (it) Color(0xFF7C5CD6) else Color(0xFF8B5CF6) } ?: Color(0xFF8B5CF6),
                text = Color(0xFFF4EEE6),
                textDim = Color(0xFFC3B6A6),
                textFaint = Color(0xFF8F8375),
                good = Color(0xFF63D488), warn = Color(0xFFF2C94C), bad = Color(0xFFF4726E),
                scrim = Color(0x660A0603),
                dark = true,
            )
        }

        /** Light ivory glass ("White" theme). */
        fun white(a: PaletteSample? = null): ObsiPalette {
            val accent = a?.accentDeep ?: Color(0xFFC65D2A)
            return ObsiPalette(
                bg0 = Color(0xFFEFE9DF),
                glass = Color(0xC9F7F2E9),
                glassHigh = Color(0xE6FFFBF3),
                glassLow = Color(0x99F1EBE0),
                glassInput = Color(0xB3F4EEE4),
                line = Color(0x45A08663),
                lineSoft = Color(0x26A08663),
                accent = accent, accentDeep = a?.accentDeep ?: Color(0xFFAC471D), accentSoft = a?.accentSoft ?: Color(0xFFE88B55), onAccent = Color(0xFFFFF6EC),
                secondary = Color(0xFF7C5CD6),
                text = Color(0xFF2B2117),
                textDim = Color(0xFF6E5F4E),
                textFaint = Color(0xFF9C8D7B),
                good = Color(0xFF2E8B57), warn = Color(0xFFB8860B), bad = Color(0xFFC94F4B),
                scrim = Color(0x33EFE6D8),
                dark = false,
            )
        }

        /** Near-black low-glare glass ("Black" theme). */
        fun black(a: PaletteSample? = null): ObsiPalette {
            val accent = a?.accent ?: Color(0xFFC96A36)
            return ObsiPalette(
                bg0 = Color(0xFF0A0A0C),
                glass = Color(0xD3141418),
                glassHigh = Color(0xEC1C1C22),
                glassLow = Color(0x99101014),
                glassInput = Color(0xB318181E),
                line = Color(0x40FFFFFF),
                lineSoft = Color(0x1FFFFFFF),
                accent = accent, accentDeep = a?.accentDeep ?: Color(0xFF9F4B24), accentSoft = a?.accentSoft ?: Color(0xFFE18A55), onAccent = Color(0xFF1B0F00),
                secondary = Color(0xFF8B5CF6),
                text = Color(0xFFEDEDEF),
                textDim = Color(0xFFA6A6AE),
                textFaint = Color(0xFF74747C),
                good = Color(0xFF4ADE80), warn = Color(0xFFFACC15), bad = Color(0xFFF87171),
                scrim = Color(0x73000000),
                dark = true,
            )
        }
    }
}

fun themePalette(mode: String, sample: PaletteSample?): ObsiPalette = when (mode) {
    "white" -> ObsiPalette.white(sample)
    "black" -> ObsiPalette.black(sample)
    else -> ObsiPalette.vanilla(sample)
}

val LocalObsi = staticCompositionLocalOf { ObsiPalette.vanilla() }

/** The active glass palette — use this instead of hardcoded colours. */
@Composable
fun obsi(): ObsiPalette = LocalObsi.current

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
fun ObsiTheme(lang: Lang, palette: ObsiPalette, content: @Composable () -> Unit) {
    // Vazirmatn covers Persian/Arabic and Latin, so one family gives the same look on every OS.
    val family = remember {
        FontFamily(
            Font(resource = "fonts/Vazirmatn-Regular.ttf", weight = FontWeight.Normal),
            Font(resource = "fonts/Vazirmatn-Medium.ttf", weight = FontWeight.Medium),
            Font(resource = "fonts/Vazirmatn-Bold.ttf", weight = FontWeight.Bold),
        )
    }
    val base = Typography()
    val typography = remember(family) {
        base.withFamily(family).copy(
            titleLarge = base.titleLarge.fit(family).copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            titleMedium = base.titleMedium.fit(family).copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
            headlineSmall = base.headlineSmall.fit(family).copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
        )
    }
    val scheme = if (palette.dark) darkColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent,
        primaryContainer = palette.accentDeep, onPrimaryContainer = palette.accentSoft,
        secondary = palette.secondary, onSecondary = Color.White,
        secondaryContainer = palette.glassHigh, onSecondaryContainer = palette.text,
        tertiary = palette.good,
        background = palette.bg0, onBackground = palette.text,
        surface = palette.glass, onSurface = palette.text,
        surfaceVariant = palette.glassInput, onSurfaceVariant = palette.textDim,
        surfaceContainer = palette.glass, surfaceContainerHigh = palette.glassHigh, surfaceContainerHighest = palette.glassHigh,
        surfaceContainerLow = palette.glassLow, surfaceContainerLowest = palette.glassLow,
        outline = palette.line, outlineVariant = palette.lineSoft,
        error = palette.bad, onError = Color(0xFF2B0000),
    ) else lightColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent,
        primaryContainer = palette.accentSoft, onPrimaryContainer = palette.accentDeep,
        secondary = palette.secondary, onSecondary = Color.White,
        secondaryContainer = palette.glassHigh, onSecondaryContainer = palette.text,
        tertiary = palette.good,
        background = palette.bg0, onBackground = palette.text,
        surface = palette.glass, onSurface = palette.text,
        surfaceVariant = palette.glassInput, onSurfaceVariant = palette.textDim,
        surfaceContainer = palette.glass, surfaceContainerHigh = palette.glassHigh, surfaceContainerHighest = palette.glassHigh,
        surfaceContainerLow = palette.glassLow, surfaceContainerLowest = palette.glassLow,
        outline = palette.line, outlineVariant = palette.lineSoft,
        error = palette.bad, onError = Color.White,
    )
    CompositionLocalProvider(LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        CompositionLocalProvider(LocalObsi provides palette) {
            MaterialTheme(colorScheme = scheme, typography = typography, content = content)
        }
    }
}
