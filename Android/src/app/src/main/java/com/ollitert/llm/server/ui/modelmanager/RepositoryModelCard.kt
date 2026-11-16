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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.DateTimeException
import java.util.Locale
import java.util.TimeZone
import java.util.Date

internal data class RepositoryModelSummary(
  val id: String,
  val downloads: Long,
  val likes: Long,
  val lastModified: String?,
  val variantCount: Int? = null,
)

internal data class RepositoryFileVariant(val path: String, val sizeBytes: Long?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepositoryModelCard(model: RepositoryModelSummary, loading: Boolean = false, showStats: Boolean = true, onClick: () -> Unit) {
  val owner = model.id.substringBefore('/')
  val name = model.id.substringAfter('/')
  Card(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(owner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (showStats) RepositoryModelStats(model)
      if (loading) {
        val description = stringResource(R.string.repository_loading_files)
        LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = description })
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepositoryModelStats(model: RepositoryModelSummary) {
  val date = remember(model.lastModified) { formatRepositoryUpdatedDate(model.lastModified) }
  val downloads = NumberFormat.getIntegerInstance().format(model.downloads)
  val likes = NumberFormat.getIntegerInstance().format(model.likes)
  val variantCount = model.variantCount
  FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    RepositoryStat(Icons.Rounded.CloudDownload, downloads, stringResource(R.string.hf_explore_download_count, downloads))
    RepositoryStat(Icons.Rounded.Favorite, likes, stringResource(R.string.hf_explore_like_count, likes))
    if (date != null) {
      val updated = stringResource(R.string.hf_explore_updated_date, date)
      RepositoryStat(Icons.Rounded.Schedule, updated, updated)
    }
    if (variantCount != null) {
      val variants = pluralStringResource(R.plurals.hf_explore_variants, variantCount, variantCount)
      RepositoryStat(Icons.Outlined.Layers, variants, variants)
    }
  }
}

@Composable
private fun RepositoryStat(icon: ImageVector, text: String, description: String) {
  Row(
    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

internal fun formatRepositoryUpdatedDate(value: String?, locale: Locale = Locale.getDefault(), zone: TimeZone = TimeZone.getDefault()): String? {
  if (value == null) return null
  return try {
    DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = zone }.format(Date.from(Instant.parse(value)))
  } catch (_: DateTimeException) {
    null
  } catch (_: IllegalArgumentException) {
    null
  }
}
