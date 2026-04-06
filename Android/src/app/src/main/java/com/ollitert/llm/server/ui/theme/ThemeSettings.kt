package com.ollitert.llm.server.ui.theme

import androidx.compose.runtime.mutableStateOf
import com.ollitert.llm.server.proto.Theme

/**
 * OlliteRT is dark-only. This object is retained for compatibility with code
 * that reads [themeOverride], but it always resolves to dark.
 */
object ThemeSettings {
  val themeOverride = mutableStateOf(Theme.THEME_DARK)
}
