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

package com.ollitert.llm.server.ui.modelmanager

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.common.TooltipIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepositoryExploreTopBar(
  title: String,
  linkLabel: String,
  linkEnabled: Boolean,
  backEnabled: Boolean,
  onOpenLink: () -> Unit,
  onBack: () -> Unit,
) {
  val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
  TopAppBar(
    title = { Text(title) },
    actions = {
      TooltipIconButton(
        icon = Icons.Outlined.Link,
        tooltip = linkLabel,
        onClick = onOpenLink,
        enabled = linkEnabled,
        tint = iconColor.copy(alpha = if (linkEnabled) 1f else 0.38f),
        modifier = Modifier.padding(end = 12.dp),
      )
    },
    navigationIcon = {
      TooltipIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        tooltip = stringResource(R.string.hf_explore_back),
        onClick = onBack,
        enabled = backEnabled,
        tint = iconColor.copy(alpha = if (backEnabled) 1f else 0.38f),
        modifier = Modifier.padding(horizontal = 8.dp),
      )
    },
  )
}
