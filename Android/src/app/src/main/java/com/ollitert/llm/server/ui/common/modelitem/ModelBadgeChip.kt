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

package com.ollitert.llm.server.ui.common.modelitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.ModelBadge
import com.ollitert.llm.server.ui.theme.OlliteRTBadgeFastest
import com.ollitert.llm.server.ui.theme.OlliteRTBadgeNew
import com.ollitert.llm.server.ui.theme.OlliteRTWarningText

private data class BadgeStyle(val icon: ImageVector?, val tint: Color)

private val KNOWN_STYLES = mapOf(
  "best_overall" to BadgeStyle(Icons.Filled.Star, OlliteRTWarningText),
  "new" to BadgeStyle(Icons.Filled.NewReleases, OlliteRTBadgeNew),
  "fastest" to BadgeStyle(Icons.Filled.Speed, OlliteRTBadgeFastest),
)

@Composable
fun ModelBadgeChip(badge: ModelBadge, modifier: Modifier = Modifier) {
  val style = KNOWN_STYLES[badge.key]
  val label = when (badge) {
    is ModelBadge.BestOverall -> stringResource(R.string.badge_best_overall)
    is ModelBadge.New -> stringResource(R.string.badge_new)
    is ModelBadge.Fastest -> stringResource(R.string.badge_fastest)
    is ModelBadge.Other -> badge.displayLabel
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.padding(bottom = 6.dp),
  ) {
    if (style?.icon != null) {
      Icon(
        style.icon,
        tint = style.tint,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
    }
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.alpha(0.6f),
    )
  }
}
