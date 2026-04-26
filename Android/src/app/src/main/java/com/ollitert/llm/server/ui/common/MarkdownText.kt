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

package com.ollitert.llm.server.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.ollitert.llm.server.ui.theme.customColors

/** Composable function to display Markdown-formatted text. */
@Composable
fun MarkdownText(
  text: String,
  modifier: Modifier = Modifier,
  smallFontSize: Boolean = false,
  textColor: Color = MaterialTheme.colorScheme.onSurface,
  linkColor: Color = MaterialTheme.customColors.linkColor,
) {
  val textStyle =
    if (smallFontSize) MaterialTheme.typography.bodyMedium
    else MaterialTheme.typography.bodyLarge

  Markdown(
    content = text,
    modifier = modifier,
    colors = markdownColor(text = textColor),
    typography = markdownTypography(
      paragraph = textStyle,
      text = textStyle,
      textLink = TextLinkStyles(style = SpanStyle(color = linkColor)),
    ),
  )
}
