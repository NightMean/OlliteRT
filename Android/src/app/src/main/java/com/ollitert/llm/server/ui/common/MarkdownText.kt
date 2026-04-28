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

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.rememberMarkdownState
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.customColors
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.getTextInNode

private val markdownSyntaxRegex = Regex("""[*_`~\[\]#>]|!\[""")
private val WHITESPACE_REGEX = "\\s+".toRegex()

/** Composable function to display Markdown-formatted text. */
@Composable
fun MarkdownText(
  text: String,
  modifier: Modifier = Modifier,
  smallFontSize: Boolean = false,
  textColor: Color = MaterialTheme.colorScheme.onSurface,
  linkColor: Color = MaterialTheme.customColors.linkColor,
  searchQuery: String = "",
) {
  val textStyle =
    if (smallFontSize) MaterialTheme.typography.bodyMedium
    else MaterialTheme.typography.bodyLarge

  val searchWords = remember(searchQuery) {
    if (searchQuery.isBlank()) emptyList()
    else searchQuery.trim().lowercase().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
  }

  val annotator = remember(searchWords) {
    if (searchWords.isNotEmpty()) {
      markdownAnnotator { content, child ->
        if (child.type == MarkdownTokenTypes.TEXT) {
          val nodeText = child.getTextInNode(content).toString()
          val nodeTextLower = nodeText.lowercase()
          val ranges = mutableListOf<IntRange>()
          for (word in searchWords) {
            var start = 0
            while (true) {
              val idx = nodeTextLower.indexOf(word, start)
              if (idx < 0) break
              ranges.add(idx until idx + word.length)
              start = idx + 1
            }
          }
          if (ranges.isEmpty()) {
            append(nodeText)
          } else {
            val merged = ranges.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
              if (acc.isEmpty() || acc.last().last < r.first - 1) acc.add(r)
              else acc[acc.lastIndex] = acc.last().first..maxOf(acc.last().last, r.last)
              acc
            }
            val highlightStyle = SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.Bold)
            var pos = 0
            for (range in merged) {
              if (pos < range.first) append(nodeText.substring(pos, range.first))
              pushStyle(highlightStyle)
              append(nodeText.substring(range.first, range.last + 1))
              pop()
              pos = range.last + 1
            }
            if (pos < nodeText.length) append(nodeText.substring(pos))
          }
          true
        } else false
      }
    } else markdownAnnotator()
  }

  // Pre-parse markdown once — keyed only on `text` so annotator changes don't re-parse.
  val markdownState = rememberMarkdownState(content = text)
  val state by markdownState.state.collectAsState()

  // TalkBack reads styled spans as color metadata — provide plain text instead.
  val plainText = text.replace(markdownSyntaxRegex, "").trim()
  Box(modifier = Modifier.clearAndSetSemantics { contentDescription = plainText }) {
    if (state is State.Success) {
      Markdown(
        state = state,
        modifier = modifier,
        colors = markdownColor(text = textColor),
        typography = markdownTypography(
          paragraph = textStyle,
          text = textStyle,
          textLink = TextLinkStyles(style = SpanStyle(color = linkColor)),
        ),
        annotator = annotator,
      )
    }
  }
}
