package studio.obsifox.obsilauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.obsifox.obsilauncher.core.ThemeMode

/** Colours used across the glass-dark design. */
data class ObsiColors(
    val accent: Color,
    val accentDim: Color,
    val glass: Color,
    val glassBorder: Color,
    val text: Color,
    val textDim: Color,
    val scrim: Color,
    val danger: Color,
)

val LocalObsi = staticCompositionLocalOf {
    ObsiColors(
        accent = Color(0xFFFF8A3D),
        accentDim = Color(0xFF8A4A22),
        glass = Color(0xCC1A1512),
        glassBorder = Color(0x33FFFFFF),
        text = Color(0xFFF4EFEA),
        textDim = Color(0xFFB9AFA6),
        scrim = Color(0x66000000),
        danger = Color(0xFFE5604C),
    )
}

private fun palette(mode: ThemeMode, dynamicAccent: Color): ObsiColors = when (mode) {
    ThemeMode.DYNAMIC -> {
        val a = dynamicAccent
        ObsiColors(
            accent = a,
            accentDim = a.copy(alpha = 0.35f),
            glass = Color(0xCC15120F),
            glassBorder = Color(0x2EFFFFFF),
            text = Color(0xFFF4EFEA),
            textDim = Color(0xFFB9AFA6),
            scrim = Color(0x59000000),
            danger = Color(0xFFE5604C),
        )
    }
    ThemeMode.OBSIDIAN -> palette(ThemeMode.DYNAMIC, Color(0xFFFF8A3D)).copy(glass = Color(0xCC17120D))
    ThemeMode.VANILLA -> palette(ThemeMode.DYNAMIC, Color(0xFF8CC45B)).copy(glass = Color(0xCC101409))
    ThemeMode.WHITE -> ObsiColors(
        accent = Color(0xFF8A97A8),
        accentDim = Color(0x5A8A97A8),
        glass = Color(0xE6E8EAEE),
        glassBorder = Color(0x33000000),
        text = Color(0xFF191C20),
        textDim = Color(0xFF5C646D),
        scrim = Color(0x22FFFFFF),
        danger = Color(0xFFC74A38),
    )
}

private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val ObsiTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ObsiTheme(
    mode: ThemeMode,
    dynamicAccent: Color,
    content: @Composable () -> Unit,
) {
    val obsi = palette(mode, dynamicAccent)
    val dark = mode != ThemeMode.WHITE
    val scheme = darkColorScheme(
        primary = obsi.accent,
        onPrimary = Color(0xFF17110B),
        secondary = obsi.accentDim,
        background = Color(0xFF0B0908),
        surface = obsi.glass,
        surfaceVariant = obsi.glassBorder,
        onBackground = obsi.text,
        onSurface = obsi.text,
        onSurfaceVariant = obsi.textDim,
        error = obsi.danger,
    )
    CompositionLocalProvider(LocalObsi provides obsi) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ObsiTypography,
            shapes = GlassShapes,
            content = content,
        )
    }
    // keep the parameter referenced for API clarity on light system bars
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
}
