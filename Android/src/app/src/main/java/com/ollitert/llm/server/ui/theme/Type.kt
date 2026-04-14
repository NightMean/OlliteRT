package com.ollitert.llm.server.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R

/** Manrope — body, labels, general UI text. */
val ManropeFontFamily =
  FontFamily(
    Font(R.font.manrope_extralight, FontWeight.ExtraLight),
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
  )

/** Space Grotesk — headlines, titles, display, code-style text. */
val SpaceGroteskFontFamily =
  FontFamily(
    Font(R.font.space_grotesk_light, FontWeight.Light),
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
  )

private val baseline = Typography()

val AppTypography =
  Typography(
    // Display — Space Grotesk
    displayLarge = baseline.displayLarge.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold),
    displayMedium = baseline.displayMedium.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold),
    displaySmall = baseline.displaySmall.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold),
    // Headline — Space Grotesk
    headlineLarge = baseline.headlineLarge.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.SemiBold),
    // Title — Space Grotesk
    titleLarge = baseline.titleLarge.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = baseline.titleMedium.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = baseline.titleSmall.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.SemiBold),
    // Body — Manrope
    bodyLarge = baseline.bodyLarge.copy(fontFamily = ManropeFontFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = ManropeFontFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = ManropeFontFamily),
    // Label — Manrope
    labelLarge = baseline.labelLarge.copy(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = baseline.labelMedium.copy(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium),
    labelSmall = baseline.labelSmall.copy(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium),
  )

// Extended styles used by existing components

val labelSmallNarrow =
  baseline.labelSmall.copy(fontFamily = ManropeFontFamily, letterSpacing = 0.0.sp)

val bodyLargeNarrow = baseline.bodyLarge.copy(fontFamily = ManropeFontFamily, letterSpacing = 0.2.sp)

val headlineLargeMedium =
  baseline.headlineLarge.copy(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Medium)
