package ar.canastacerca.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Палитра веб-версии (public/style.css): celeste и sol — цвета флага Аргентины
private val Celeste = Color(0xFF0B5CAD)
private val Celeste2 = Color(0xFF74ACDF)
private val CelesteSoft = Color(0xFFE8F1FB)
private val Sol = Color(0xFFF6B40E)
private val Ink = Color(0xFF142235)
private val Muted = Color(0xFF5B6B80)
private val Line = Color(0xFFE2E8F0)
private val Bg = Color(0xFFF4F7FB)
private val Good = Color(0xFF11865B)
private val GoodSoft = Color(0xFFE3F5EC)
private val Warn = Color(0xFFB45309)
private val WarnSoft = Color(0xFFFFF4E0)
private val Bad = Color(0xFFC0392B)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Celeste,
    onPrimary = Color.White,
    primaryContainer = CelesteSoft,
    onPrimaryContainer = Celeste,
    secondary = Sol,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFFFF1CC),
    onSecondaryContainer = Ink,
    tertiary = Good,
    onTertiary = Color.White,
    tertiaryContainer = GoodSoft,
    onTertiaryContainer = Good,
    error = Bad,
    background = Bg,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FBFD),
    surfaceContainer = Bg,
    surfaceContainerHigh = Color(0xFFEDF1F6),
    outline = Color(0xFFC7D0DB),
    outlineVariant = Line,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Celeste2,
    onPrimary = Color(0xFF00315E),
    primaryContainer = Color(0xFF0B3F73),
    onPrimaryContainer = Color(0xFFD5E6F8),
    secondary = Sol,
    onSecondary = Ink,
    tertiary = Color(0xFF5FD3A0),
    onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF0E4B33),
    onTertiaryContainer = Color(0xFFBDF1D8),
    error = Color(0xFFFFB4A9),
    background = Color(0xFF0E1622),
    onBackground = Color(0xFFE3E9F1),
    surface = Color(0xFF141E2B),
    onSurface = Color(0xFFE3E9F1),
    surfaceVariant = Color(0xFF1E2A39),
    onSurfaceVariant = Color(0xFFA8B5C6),
    outline = Color(0xFF3B4A5C),
    outlineVariant = Color(0xFF263345),
)

/** Цвета, которых нет в Material: «выгодно» и «внимание». */
@Immutable
data class CanastaExtraColors(
    val good: Color,
    val goodContainer: Color,
    val warn: Color,
    val warnContainer: Color,
)

private val LightExtra = CanastaExtraColors(good = Good, goodContainer = GoodSoft, warn = Warn, warnContainer = WarnSoft)
private val DarkExtra = CanastaExtraColors(
    good = Color(0xFF5FD3A0),
    goodContainer = Color(0xFF0E4B33),
    warn = Color(0xFFFFB86B),
    warnContainer = Color(0xFF4A2E05),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtra }

private val CanastaTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

private val CanastaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun CanastaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = CanastaTypography,
            shapes = CanastaShapes,
            content = content,
        )
    }
}

object CanastaTheme {
    val extraColors: CanastaExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtraColors.current

    /** Крупная цена в карточках. */
    val priceStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
}
