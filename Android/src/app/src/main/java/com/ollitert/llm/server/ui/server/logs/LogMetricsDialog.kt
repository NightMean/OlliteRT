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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.ui.server.WarningColor
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import java.util.Locale

/**
 * Dialog showing per-request performance metrics for a single log entry.
 * Displays TTFB, decode speed, prefill speed, inter-token latency, token counts,
 * context utilization, and total latency.
 */
@Composable
internal fun RequestMetricsDialog(entry: RequestLogEntry, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.logs_metrics_title),
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (entry.ttfbMs > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_ttfb), stringResource(R.string.logs_metrics_value_ms, entry.ttfbMs))
        }
        if (entry.decodeSpeed > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_decode_speed), String.format(Locale.US, "%.1f t/s", entry.decodeSpeed))
        }
        if (entry.prefillSpeed > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_prefill_speed), String.format(Locale.US, "%.1f t/s", entry.prefillSpeed))
        }
        if (entry.itlMs > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_itl), String.format(Locale.US, "%.1fms", entry.itlMs))
        }
        if (entry.latencyMs > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_total_latency), stringResource(R.string.logs_metrics_value_ms, entry.latencyMs))
        }
        if (entry.inputTokenEstimate > 0) {
          val prefix = if (entry.isExactTokenCount) "" else "~"
          MetricsRow(stringResource(R.string.logs_metrics_input_tokens), "$prefix${entry.inputTokenEstimate}")
        }
        if (entry.tokens > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_output_tokens), "~${entry.tokens}")
        }
        if (entry.inputTokenEstimate > 0 && entry.maxContextTokens > 0) {
          val utilRatio = entry.inputTokenEstimate.toDouble() / entry.maxContextTokens.toDouble()
          MetricsRow(
            label = stringResource(R.string.logs_metrics_context_util),
            value = String.format(Locale.US, "%.1f%%", utilRatio * 100.0),
            valueColor = contextUtilizationColor(utilRatio),
            detail = stringResource(R.string.logs_metrics_ctx_detail, entry.inputTokenEstimate, entry.maxContextTokens),
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.logs_metrics_close))
      }
    },
  )
}

@Composable
internal fun MetricsRow(
  label: String,
  value: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
  detail: String? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = valueColor,
        fontWeight = FontWeight.SemiBold,
        fontFamily = SpaceGroteskFontFamily,
      )
      if (detail != null) {
        Text(
          text = detail,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
internal fun contextUtilizationColor(ratio: Double): Color = when {
  ratio > 0.8 -> MaterialTheme.colorScheme.error
  ratio > 0.5 -> WarningColor
  else -> MaterialTheme.colorScheme.onSurfaceVariant
}
