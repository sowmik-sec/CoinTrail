package com.cointrail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Jade40,
    onPrimary = Paper,
    primaryContainer = JadeContainerLight,
    onPrimaryContainer = OnJadeContainerLight,
    secondary = GreenGrey30,
    onSecondary = Paper,
    secondaryContainer = GreenGreyContainerLight,
    onSecondaryContainer = OnGreenGreyContainerLight,
    tertiary = Amber40,
    onTertiary = OnAmberLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = MistLight,
    onBackground = OnMistLight,
    surface = MistLight,
    onSurface = OnMistLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceTint = Jade40,
    surfaceBright = MistLight,
    surfaceDim = Color(0xFFD7DFDA),
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Color(0xFFECF1EE),
    surfaceContainer = Color(0xFFE6ECE9),
    surfaceContainerHigh = Color(0xFFE0E7E3),
    surfaceContainerHighest = Color(0xFFDAE2DE),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = BrickLight,
    onError = OnBrickLight,
    errorContainer = BrickContainerLight,
    onErrorContainer = OnBrickContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = Jade80,
    onPrimary = OnJadeDark,
    primaryContainer = JadeContainerDark,
    onPrimaryContainer = OnJadeContainerDark,
    secondary = GreenGrey80,
    onSecondary = OnJadeDark,
    secondaryContainer = GreenGreyContainerDark,
    onSecondaryContainer = OnGreenGreyContainerDark,
    tertiary = Amber80,
    onTertiary = OnAmberDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = MistDark,
    onBackground = OnMistDark,
    surface = MistDark,
    onSurface = OnMistDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceTint = Jade80,
    surfaceBright = Color(0xFF333D38),
    surfaceDim = MistDark,
    surfaceContainerLowest = Color(0xFF090E0C),
    surfaceContainerLow = Color(0xFF141B18),
    surfaceContainer = Color(0xFF18201C),
    surfaceContainerHigh = Color(0xFF212A26),
    surfaceContainerHighest = Color(0xFF2B3530),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = BrickDark,
    onError = OnBrickDark,
    errorContainer = BrickContainerDark,
    onErrorContainer = OnBrickContainerDark,
)

/**
 * Colours for the deep forest block behind the Home hero. The block stays dark in both modes,
 * so it carries its own content colours instead of borrowing the light/dark scheme's.
 */
@Immutable
data class ForestColors(
    val container: Color,
    val content: Color,
    val contentMuted: Color,
    val trailBar: Color,
    val trailToday: Color,
    val trailAhead: Color,
    val spendUp: Color,
    val spendDown: Color,
)

private fun forestColors(darkTheme: Boolean) = ForestColors(
    container = if (darkTheme) ForestInkDark else ForestInkLight,
    content = OnForest,
    contentMuted = OnForestMuted,
    trailBar = ForestTrailBar,
    trailToday = ForestTrailToday,
    trailAhead = ForestTrailAhead,
    spendUp = ForestSpendUp,
    spendDown = ForestSpendDown,
)

private val LocalForestColors = staticCompositionLocalOf { forestColors(darkTheme = false) }

/** CoinTrail-specific theme values that Material's scheme has no slot for. */
object CoinTrailTheme {
    val forest: ForestColors
        @Composable @ReadOnlyComposable
        get() = LocalForestColors.current
}

@Composable
fun CoinTrailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalForestColors provides forestColors(darkTheme)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = CoinTrailTypography,
            content = content,
        )
    }
}
