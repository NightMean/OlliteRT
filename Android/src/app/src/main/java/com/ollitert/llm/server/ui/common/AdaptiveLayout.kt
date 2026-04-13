package com.ollitert.llm.server.ui.common

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providing the current window width size class.
 * Set at the root (OlliteRTApp) via CompositionLocalProvider.
 * Screens and navigation read this to switch between compact/medium/expanded layouts:
 * - Compact (< 600dp): phones portrait & landscape — bottom nav, full-width content
 * - Medium (600–839dp): small tablets, foldables — navigation rail, 600dp max content
 * - Expanded (≥ 840dp): large tablets — navigation rail, 840dp max content
 */
val LocalWindowWidthSizeClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }
