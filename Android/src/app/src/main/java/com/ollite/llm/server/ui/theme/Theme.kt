package com.ollite.llm.server.ui.theme

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

private val olliteColorScheme =
  darkColorScheme(
    primary = OllitePrimary,
    onPrimary = OlliteOnPrimary,
    primaryContainer = OllitePrimaryContainer,
    onPrimaryContainer = OlliteOnPrimaryContainer,
    secondary = OlliteSecondary,
    onSecondary = OlliteOnSecondary,
    secondaryContainer = OlliteSecondaryContainer,
    onSecondaryContainer = OlliteOnSecondaryContainer,
    tertiary = OlliteTertiary,
    onTertiary = OlliteOnTertiary,
    tertiaryContainer = OlliteTertiaryContainer,
    onTertiaryContainer = OlliteOnTertiaryContainer,
    error = OlliteError,
    onError = OlliteOnError,
    errorContainer = OlliteErrorContainer,
    onErrorContainer = OlliteOnErrorContainer,
    background = OlliteBackground,
    onBackground = OlliteOnBackground,
    surface = OlliteSurface,
    onSurface = OlliteOnSurface,
    surfaceVariant = OlliteSurfaceVariant,
    onSurfaceVariant = OlliteOnSurfaceVariant,
    outline = OlliteOutline,
    outlineVariant = OlliteOutlineVariant,
    scrim = OlliteScrim,
    inverseSurface = OlliteInverseSurface,
    inverseOnSurface = OlliteInverseOnSurface,
    inversePrimary = OlliteInversePrimary,
    surfaceDim = OlliteSurfaceDim,
    surfaceBright = OlliteSurfaceBright,
    surfaceContainerLowest = OlliteSurfaceContainerLowest,
    surfaceContainerLow = OlliteSurfaceContainerLow,
    surfaceContainer = OlliteSurfaceContainer,
    surfaceContainerHigh = OlliteSurfaceContainerHigh,
    surfaceContainerHighest = OlliteSurfaceContainerHighest,
  )

/**
 * Custom colors that extend Material3 for Ollite-specific needs.
 * Fields retained for compatibility with existing components (benchmark, model items, etc.).
 */
@Immutable
data class CustomColors(
  val appTitleGradientColors: List<Color> = listOf(OllitePrimary, OlliteDeepBlue),
  val taskCardBgColor: Color = OlliteSurfaceContainerLow,
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
  val linkColor: Color = OlliteLinkColor,
  val successColor: Color = OlliteSuccessColor,
  val modelInfoIconColor: Color = Color(0xFFCCCCCC),
  val warningContainerColor: Color = OlliteWarningContainer,
  val warningTextColor: Color = OlliteWarningText,
  val errorContainerColor: Color = Color(0xFF523A3B),
  val errorTextColor: Color = Color(0xFFEE675C),
)

val LocalCustomColors = staticCompositionLocalOf { CustomColors() }

val MaterialTheme.customColors: CustomColors
  @Composable @ReadOnlyComposable get() = LocalCustomColors.current

/** Action gradient brush used for primary buttons. */
val OlliteActionGradient = Brush.linearGradient(listOf(OllitePrimary, OlliteDeepBlue))

@Composable
fun OlliteTheme(content: @Composable () -> Unit) {
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
      colorScheme = olliteColorScheme,
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

// Keep old name as alias during migration so commented-out previews don't break imports
@Composable
fun GalleryTheme(content: @Composable () -> Unit) = OlliteTheme(content)
