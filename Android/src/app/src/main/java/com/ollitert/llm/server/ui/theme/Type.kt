/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
