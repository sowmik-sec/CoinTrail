package com.cointrail.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cointrail.R

/**
 * Bricolage Grotesque carries amounts and headings; body and label text stay on the system sans.
 * The bundled files are Latin subsets, so `৳` falls back to the system font — amounts that need
 * it to sit well are drawn with [com.cointrail.ui.components.AmountText].
 */
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque_medium, FontWeight.Medium),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
)

private val base = Typography()

private fun TextStyle.display(weight: FontWeight, tracking: Double) = copy(
    fontFamily = Bricolage,
    fontWeight = weight,
    letterSpacing = tracking.em,
    fontFeatureSettings = "tnum",
)

val CoinTrailTypography = Typography(
    displayLarge = base.displayLarge.display(FontWeight.Bold, -0.03),
    displayMedium = base.displayMedium.copy(fontSize = 52.sp, lineHeight = 56.sp).display(FontWeight.Bold, -0.03),
    displaySmall = base.displaySmall.display(FontWeight.Bold, -0.02),
    headlineLarge = base.headlineLarge.display(FontWeight.Medium, -0.01),
    headlineMedium = base.headlineMedium.display(FontWeight.Medium, -0.01),
    headlineSmall = base.headlineSmall.display(FontWeight.Medium, -0.01),
    titleLarge = base.titleLarge.display(FontWeight.Medium, 0.0),
    titleMedium = base.titleMedium,
    titleSmall = base.titleSmall,
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge,
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)
