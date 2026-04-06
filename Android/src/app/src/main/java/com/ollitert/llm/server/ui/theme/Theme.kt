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
import androidx.compose.ui.graphics.Brush
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
  val appTitleGradientColors: List<Color> = listOf(OlliteRTPrimary, OlliteRTDeepBlue),
  val taskCardBgColor: Color = OlliteRTSurfaceContainerLow,
  val taskBgColors: List<Color> = listOf(
    Color(0xFF181210),
    Color(0xFF131711),
    Color(0xFF191924),
    Color(0xFF1A1813),
  ),
  val taskBgGradientColors: List<List<Color>> = listOf(
    listOf(Color(0xFFE25F57), Color(0xFFDB372D)),
    listOf(Color(0xFF41A15F), Color(0xFF128937)),
    listOf(Color(0xFF669DF6), Color(0xFF3174F1)),
    listOf(Color(0xFFFDD45D), Color(0xFFCAA12A)),
  ),
  val taskIconColors: List<Color> = listOf(
    Color(0xFFE25F57),
    Color(0xFF41A15F),
    Color(0xFF669DF6),
    Color(0xFFCAA12A),
  ),
  val taskIconShapeBgColor: Color = Color(0xFF202124),
  val linkColor: Color = OlliteRTLinkColor,
  val successColor: Color = OlliteRTSuccessColor,
  val modelInfoIconColor: Color = Color(0xFFCCCCCC),
  val warningContainerColor: Color = OlliteRTWarningContainer,
  val warningTextColor: Color = OlliteRTWarningText,
  val errorContainerColor: Color = Color(0xFF523A3B),
  val errorTextColor: Color = Color(0xFFEE675C),
)

val LocalCustomColors = staticCompositionLocalOf { CustomColors() }

val MaterialTheme.customColors: CustomColors
  @Composable @ReadOnlyComposable get() = LocalCustomColors.current

/** Action gradient brush used for primary buttons. */
val OlliteRTActionGradient = Brush.linearGradient(listOf(OlliteRTPrimary, OlliteRTDeepBlue))

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

