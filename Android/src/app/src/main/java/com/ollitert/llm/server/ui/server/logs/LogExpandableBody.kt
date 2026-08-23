/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.ui.server.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.theme.OlliteRTSubtleGrey
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LOG_BODY_FONT_SIZE = 12.sp
internal val LOG_DETAIL_FONT_SIZE = 11.sp
internal val LOG_BODY_LINE_HEIGHT = 16.sp

internal const val COLLAPSED_MAX_LINES = 8
internal const val COLLAPSED_MAX_CHARS = 600

/**
 * Bodies above this size get highlighted asynchronously (off main thread) to avoid jank.
 * At 1KB, regex-based JSON highlighting can take 10-30ms on mid-range devices — enough
 * to drop frames during scrolling. Larger bodies (2-4KB) can take 50-100ms.
 */
internal const val ASYNC_HIGHLIGHT_THRESHOLD = 1_000

@Composable
internal fun ExpandableBodySection(
  label: String,
  labelColor: Color,
  body: String,
  expanded: Boolean,
  showToggle: Boolean,
  onToggle: () -> Unit,
  /** Optional annotated label — takes precedence over plain [label] when provided. */
  annotatedLabel: AnnotatedString? = null,
  /** Active search query — overlays yellow highlight on matches within JSON-highlighted text. */
  searchQuery: String = "",
  wrapText: Boolean = true,
) {
  if (annotatedLabel != null) {
    Text(
      text = annotatedLabel,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
    )
  } else if (label.isNotBlank()) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = labelColor,
      fontWeight = FontWeight.SemiBold,
    )
  }
  Spacer(modifier = Modifier.height(4.dp))

  val collapsedHighlighted = remember(body, searchQuery) {
    val preview = if (body.length > COLLAPSED_MAX_CHARS) body.substring(0, COLLAPSED_MAX_CHARS) else body
    val jsonStyled = highlightJson(preview)
    if (searchQuery.isNotEmpty()) overlaySearchHighlights(jsonStyled, searchQuery) else jsonStyled
  }

  if (expanded) {
    val fullHighlighted by produceState(
      initialValue = if (body.length <= ASYNC_HIGHLIGHT_THRESHOLD) {
        val jsonStyled = highlightJson(body)
        if (searchQuery.isNotEmpty()) overlaySearchHighlights(jsonStyled, searchQuery) else jsonStyled
      } else {
        null
      },
      key1 = body,
      key2 = searchQuery,
    ) {
      if (body.length > ASYNC_HIGHLIGHT_THRESHOLD) {
        value = withContext(Dispatchers.Default) {
          val jsonStyled = highlightJson(body)
          if (searchQuery.isNotEmpty()) overlaySearchHighlights(jsonStyled, searchQuery) else jsonStyled
        }
      }
    }
    SelectionContainer {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerLowest)
          .clearAndSetSemantics { contentDescription = body },
      ) {
        Box(
          modifier = Modifier
            .padding(12.dp)
            .then(if (!wrapText) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        ) {
          val highlighted = fullHighlighted
          if (highlighted != null) {
            Text(
              text = highlighted,
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = LOG_DETAIL_FONT_SIZE,
                lineHeight = LOG_BODY_LINE_HEIGHT,
              ),
            )
          } else {
            Text(
              text = body,
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = LOG_DETAIL_FONT_SIZE,
                lineHeight = LOG_BODY_LINE_HEIGHT,
              ),
              color = OlliteRTSubtleGrey,
            )
          }
        }
        if (showToggle) {
          Icon(
            imageVector = Icons.Outlined.ExpandLess,
            contentDescription = stringResource(R.string.logs_body_collapse_cd),
            tint = labelColor.copy(alpha = 0.7f),
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp)
              .size(22.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f))
              .clickable(onClick = onToggle),
          )
        }
      }
    }
  } else {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .clearAndSetSemantics { contentDescription = body }
        .then(if (showToggle) Modifier.clickable(onClick = onToggle) else Modifier),
    ) {
      Box(
        modifier = Modifier
          .padding(12.dp)
          .then(if (!wrapText) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
      ) {
        Text(
          text = collapsedHighlighted,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontSize = LOG_DETAIL_FONT_SIZE,
            lineHeight = LOG_BODY_LINE_HEIGHT,
          ),
          maxLines = COLLAPSED_MAX_LINES,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (showToggle) {
        Icon(
          imageVector = Icons.Outlined.ExpandMore,
          contentDescription = stringResource(R.string.logs_body_expand_cd),
          tint = labelColor.copy(alpha = 0.7f),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)),
        )
      }
    }
  }
}
