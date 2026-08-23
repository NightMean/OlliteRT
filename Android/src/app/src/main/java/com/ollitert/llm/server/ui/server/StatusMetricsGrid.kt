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

package com.ollitert.llm.server.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusMetricsGrid(
  uptimeSeconds: Long,
  requestCount: Long,
  tokensIn: Long,
  tokensGenerated: Long,
  lastDecodeSpeed: Double,
  peakDecodeSpeed: Double,
  lastTtfbMs: Long,
  avgTtfbMs: Long,
  errorCount: Long,
  showRequestTypes: Boolean,
  textRequests: Long,
  imageRequests: Long,
  audioRequests: Long,
  showAdvancedMetrics: Boolean,
  lastPrefillSpeed: Double,
  avgThroughput: String,
  lastItlMs: Double,
  lastLatencyMs: Long,
  avgLatencyMs: Long,
  peakLatencyMs: Long,
  modifier: Modifier = Modifier,
) {
  var showMetricsInfoDialog by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Section header
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.status_section_metrics),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.weight(1f))
      TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(stringResource(R.string.status_metrics_info_tooltip)) } },
        state = rememberTooltipState(),
      ) {
        IconButton(onClick = { showMetricsInfoDialog = true }, modifier = Modifier.size(32.dp)) {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.status_metrics_info_tooltip),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }

    if (showMetricsInfoDialog) {
      AlertDialog(
        onDismissRequest = { showMetricsInfoDialog = false },
        title = { Text(stringResource(R.string.status_metrics_info_title)) },
        text = { Text(stringResource(R.string.status_metrics_info_body)) },
        confirmButton = {
          TextButton(onClick = { showMetricsInfoDialog = false }) {
            Text(stringResource(R.string.ok))
          }
        },
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      MetricCard(
        label = stringResource(R.string.status_metric_uptime),
        value = formatUptime(uptimeSeconds),
        modifier = Modifier.weight(1f),
      )
      MetricCard(
        label = stringResource(R.string.status_metric_requests),
        value = requestCount.toString(),
        modifier = Modifier.weight(1f),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      MetricCard(
        label = stringResource(R.string.status_metric_tokens_in),
        value = tokensIn.toString(),
        modifier = Modifier.weight(1f),
      )
      MetricCard(
        label = stringResource(R.string.status_metric_tokens_out),
        value = tokensGenerated.toString(),
        modifier = Modifier.weight(1f),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val noData = stringResource(R.string.status_value_no_data)
      MetricCard(
        label = stringResource(R.string.status_metric_decode_speed),
        value = remember(lastDecodeSpeed, noData) { if (lastDecodeSpeed > 0) String.format(Locale.US, "%.1f t/s", lastDecodeSpeed) else noData },
        modifier = Modifier.weight(1f),
      )
      MetricCard(
        label = stringResource(R.string.status_metric_peak_decode),
        value = remember(peakDecodeSpeed, noData) { if (peakDecodeSpeed > 0) String.format(Locale.US, "%.1f t/s", peakDecodeSpeed) else noData },
        modifier = Modifier.weight(1f),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      MetricCard(
        label = stringResource(R.string.status_metric_last_ttfb),
        value = if (lastTtfbMs > 0) stringResource(R.string.status_value_ms, lastTtfbMs) else stringResource(R.string.status_value_no_data),
        modifier = Modifier.weight(1f),
      )
      MetricCard(
        label = stringResource(R.string.status_metric_avg_ttfb),
        value = if (avgTtfbMs > 0) stringResource(R.string.status_value_ms, avgTtfbMs) else stringResource(R.string.status_value_no_data),
        modifier = Modifier.weight(1f),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val noDataText = stringResource(R.string.status_value_no_data)
      val successRate = remember(requestCount, errorCount, noDataText) {
        if (requestCount > 0) String.format(Locale.US, "%.0f%%", ((requestCount - errorCount).toDouble() / requestCount) * 100) else noDataText
      }
      MetricCard(
        label = stringResource(R.string.status_metric_success_rate),
        value = if (requestCount > 0) stringResource(R.string.status_value_success_rate, successRate, errorCount) else stringResource(R.string.status_value_no_data),
        modifier = Modifier.weight(1f),
      )
    }

    // Request modality breakdown
    if (showRequestTypes) {
      Text(
        text = stringResource(R.string.status_section_request_types),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        MetricCard(
          label = stringResource(R.string.status_metric_text),
          value = textRequests.toString(),
          modifier = Modifier.weight(1f),
        )
        MetricCard(
          label = stringResource(R.string.status_metric_vision),
          value = imageRequests.toString(),
          modifier = Modifier.weight(1f),
        )
        MetricCard(
          label = stringResource(R.string.status_metric_audio),
          value = audioRequests.toString(),
          modifier = Modifier.weight(1f),
        )
      }
    }

    // Advanced metrics
    if (showAdvancedMetrics) {
      Text(
        text = stringResource(R.string.status_section_advanced),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
      )

      val advNoData = stringResource(R.string.status_value_no_data)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        MetricCard(
          label = stringResource(R.string.status_metric_prefill_speed),
          value = remember(lastPrefillSpeed, advNoData) { if (lastPrefillSpeed > 0) String.format(Locale.US, "%.1f t/s", lastPrefillSpeed) else advNoData },
          modifier = Modifier.weight(1f),
        )
        MetricCard(
          label = stringResource(R.string.status_metric_avg_throughput),
          value = stringResource(R.string.status_value_throughput, avgThroughput),
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        MetricCard(
          label = stringResource(R.string.status_metric_inter_token_latency),
          value = remember(lastItlMs, advNoData) { if (lastItlMs > 0) String.format(Locale.US, "%.1fms", lastItlMs) else advNoData },
          modifier = Modifier.weight(1f),
        )
        MetricCard(
          label = stringResource(R.string.status_metric_last_latency),
          value = if (lastLatencyMs > 0) stringResource(R.string.status_value_ms, lastLatencyMs) else stringResource(R.string.status_value_no_data),
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        MetricCard(
          label = stringResource(R.string.status_metric_avg_latency),
          value = if (avgLatencyMs > 0) stringResource(R.string.status_value_ms, avgLatencyMs) else stringResource(R.string.status_value_no_data),
          modifier = Modifier.weight(1f),
        )
        MetricCard(
          label = stringResource(R.string.status_metric_peak_latency),
          value = if (peakLatencyMs > 0) stringResource(R.string.status_value_ms, peakLatencyMs) else stringResource(R.string.status_value_no_data),
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
internal fun MetricCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(16.dp),
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

internal fun formatUptime(totalSeconds: Long): String {
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}
