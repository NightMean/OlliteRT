package com.ollite.llm.server.ui.theme

import androidx.compose.runtime.mutableStateOf
import com.ollite.llm.server.proto.Theme

/**
 * Ollite is dark-only. This object is retained for compatibility with code
 * that reads [themeOverride], but it always resolves to dark.
 */
object ThemeSettings {
  val themeOverride = mutableStateOf(Theme.THEME_DARK)
}
