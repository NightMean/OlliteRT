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

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ollitertColorScheme =
  darkColorScheme(
    primary = OlliteRTPrimary,
    onPrimary = OlliteRTOnPrimary,
    primaryContainer = OlliteRTPrimaryContainer,
    onPrimaryContainer = OlliteRTOnPrimaryContainer,
    secondary = OlliteRTSecondary,
    onSecondary = OlliteRTOnSecondary,
    secondaryContainer = OlliteRTSecondaryContainer,
    onSecondaryContainer = OlliteRTOnSecondaryContainer,
    tertiary = OlliteRTTertiary,
    onTertiary = OlliteRTOnTertiary,
    tertiaryContainer = OlliteRTTertiaryContainer,
    onTertiaryContainer = OlliteRTOnTertiaryContainer,
    error = OlliteRTError,
    onError = OlliteRTOnError,
    errorContainer = OlliteRTErrorContainer,
    onErrorContainer = OlliteRTOnErrorContainer,
    background = OlliteRTBackground,
    onBackground = OlliteRTOnBackground,
    surface = OlliteRTSurface,
    onSurface = OlliteRTOnSurface,
    surfaceVariant = OlliteRTSurfaceVariant,
    onSurfaceVariant = OlliteRTOnSurfaceVariant,
    outline = OlliteRTOutline,
    outlineVariant = OlliteRTOutlineVariant,
    scrim = OlliteRTScrim,
    inverseSurface = OlliteRTInverseSurface,
    inverseOnSurface = OlliteRTInverseOnSurface,
    inversePrimary = OlliteRTInversePrimary,
    surfaceDim = OlliteRTSurfaceDim,
    surfaceBright = OlliteRTSurfaceBright,
    surfaceContainerLowest = OlliteRTSurfaceContainerLowest,
    surfaceContainerLow = OlliteRTSurfaceContainerLow,
    surfaceContainer = OlliteRTSurfaceContainer,
    surfaceContainerHigh = OlliteRTSurfaceContainerHigh,
    surfaceContainerHighest = OlliteRTSurfaceContainerHighest,
  )

/**
 * Custom colors that extend Material3 for OlliteRT-specific needs.
 * Fields retained for compatibility with existing components (benchmark, model items, etc.).
 */
@Immutable
data class CustomColors(
  val modelCardBgColor: Color = OlliteRTSurfaceContainerLow,
  val linkColor: Color = OlliteRTLinkColor,
  val successColor: Color = OlliteRTSuccessColor,
  val modelInfoIconColor: Color = OlliteRTModelInfoIcon,
  val warningTextColor: Color = OlliteRTWarningText,
  val errorTextColor: Color = OlliteRTErrorText,
)

val LocalCustomColors = staticCompositionLocalOf { CustomColors() }

val MaterialTheme.customColors: CustomColors
  @Composable @ReadOnlyComposable get() = LocalCustomColors.current

@Composable
fun OlliteRTTheme(content: @Composable () -> Unit) {
  val view = LocalView.current

  // Always dark — set light status bar icons
  val currentWindow = (view.context as? Activity)?.window
  if (currentWindow != null) {
    SideEffect {
      WindowCompat.setDecorFitsSystemWindows(currentWindow, false)
      val controller = WindowCompat.getInsetsController(currentWindow, view)
      controller.isAppearanceLightStatusBars = false
    }
  }

  CompositionLocalProvider(LocalCustomColors provides CustomColors()) {
    MaterialTheme(
      colorScheme = ollitertColorScheme,
      typography = AppTypography,
      content = content,
    )
  }

  // Keep navigation bar transparent
  LaunchedEffect(Unit) {
    val window = (view.context as Activity).window
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
  }
}

