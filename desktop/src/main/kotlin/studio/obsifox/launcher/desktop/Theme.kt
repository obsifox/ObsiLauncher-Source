package studio.obsifox.launcher.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

/** ObsiLauncher palette: obsidian surfaces, fox-orange primary, amethyst secondary. */
object Obsi {
    val bg0 = Color(0xFF0F0B1A)
    val bg1 = Color(0xFF171127)
    val bg2 = Color(0xFF211936)
    val bg3 = Color(0xFF2B2147)
    val line = Color(0xFF3A2F5A)
    val orange = Color(0xFFFF8A2B)
    val orangeDeep = Color(0xFFE56A00)
    val purple = Color(0xFF8B5CF6)
    val text = Color(0xFFEDE8F7)
    val textDim = Color(0xFFA79FBF)
    val green = Color(0xFF4ADE80)
    val red = Color(0xFFF87171)
    val yellow = Color(0xFFFACC15)
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
    val scheme = darkColorScheme(
        primary = Obsi.orange, onPrimary = Color(0xFF1B0F00),
        primaryContainer = Color(0xFF5A2D00), onPrimaryContainer = Color(0xFFFFDCC2),
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
