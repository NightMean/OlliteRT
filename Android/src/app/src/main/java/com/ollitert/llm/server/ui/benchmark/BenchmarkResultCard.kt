/*
 * Copyright 2026 Google LLC
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

package com.ollitert.llm.server.ui.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.copyToClipboard
import com.ollitert.llm.server.ui.common.Accordions
import com.ollitert.llm.server.ui.common.SMALL_BUTTON_CONTENT_PADDING
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun BenchmarkResultCard(
  result: BenchmarkResultInfo,
  baselineResult: BenchmarkResultInfo?,
  showBaselineToggle: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  onBasicInfoExpandedChange: (Boolean) -> Unit,
  onStatsExpandedChange: (Boolean) -> Unit,
  onBaselineToggle: () -> Unit,
  onAggregationChange: (Aggregation) -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val copyContext = LocalContext.current
  val llmResult = result.benchmarkResult.llmResult ?: return

  val modelName = llmResult.basicInfo.modelName
  val titleSuffix = if (llmResult.basicInfo.speculativeDecoding) " · MTP" else ""

  Accordions(
    title = "$modelName · ${llmResult.basicInfo.accelerator}$titleSuffix",
    subtitle = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(llmResult.basicInfo.startMs)),
    boldTitle = true,
    expanded = result.expanded,
    onExpandedChange = onExpandedChange,
    modifier = modifier,
    titleRowAction = {
      if (showBaselineToggle) {
        val isBaseline = result.id == baselineResult?.id
        FilterChip(
          onClick = onBaselineToggle,
          label = {
            Text(
              stringResource(R.string.baseline),
              style = MaterialTheme.typography.labelSmall,
            )
          },
          selected = isBaseline,
          leadingIcon = if (isBaseline) {
            {
              Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp).offset(x = 2.dp),
              )
            }
          } else null,
          modifier = Modifier.height(24.dp),
        )
      }
    },
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(bottom = 2.dp),
    ) {
      // Basic info section
      Accordions(
        title = stringResource(R.string.basic_info),
        bgColor = MaterialTheme.colorScheme.surfaceContainerLow,
        expanded = result.basicInfoExpanded,
        onExpandedChange = onBasicInfoExpandedChange,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 4.dp),
        ) {
          StatRow(label = stringResource(R.string.benchmark_stat_model), value = llmResult.basicInfo.modelName)
          StatRow(label = stringResource(R.string.benchmark_stat_accelerator), value = llmResult.basicInfo.accelerator)
          StatRow(label = stringResource(R.string.benchmark_stat_prefill_tokens), value = "${llmResult.basicInfo.prefillTokens}")
          StatRow(label = stringResource(R.string.benchmark_stat_decode_tokens), value = "${llmResult.basicInfo.decodeTokens}")
          StatRow(label = stringResource(R.string.benchmark_stat_number_of_runs), value = "${llmResult.basicInfo.numberOfRuns}")
          if (llmResult.basicInfo.speculativeDecoding) {
            StatRow(
              label = stringResource(R.string.benchmark_stat_speculative_decoding),
              value = stringResource(R.string.enabled),
            )
          }
          StatRow(label = stringResource(R.string.benchmark_stat_app_version), value = llmResult.basicInfo.appVersion)
        }
      }

      // Stats section
      val resources = LocalResources.current
      Accordions(
        title = "${stringResource(R.string.results)} (${resources.getQuantityString(
          R.plurals.runs,
          llmResult.basicInfo.numberOfRuns,
          llmResult.basicInfo.numberOfRuns,
        )})",
        bgColor = MaterialTheme.colorScheme.surfaceContainerLow,
        expanded = result.statsExpanded,
        onExpandedChange = onStatsExpandedChange,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        titleRowAction = {
          if (llmResult.basicInfo.numberOfRuns > 1) {
            var showAggregationDropdown by remember { mutableStateOf(false) }
            Box {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { showAggregationDropdown = true }
                  .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                  .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                  )
                  .padding(start = 8.dp, end = 0.dp)
                  .height(24.dp),
              ) {
                Text(
                  result.aggregation.label,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                  Icons.Rounded.ArrowDropDown,
                  modifier = Modifier.size(20.dp),
                  contentDescription = null,
                )
              }
              DropdownMenu(
                expanded = showAggregationDropdown,
                onDismissRequest = { showAggregationDropdown = false },
              ) {
                for (aggregation in Aggregation.entries) {
                  DropdownMenuItem(
                    text = { Text(aggregation.label) },
                    onClick = {
                      showAggregationDropdown = false
                      onAggregationChange(aggregation)
                    },
                  )
                }
              }
            }
          }
        },
        hideTitleRowActionOnCollapse = true,
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        ) {
          val baselineStats = baselineResult?.benchmarkResult?.llmResult?.stats
          val isDifferentFromBaseline = result.id != baselineResult?.id
          val unitTokensSec = stringResource(R.string.benchmark_unit_tokens_per_sec)
          val unitSec = stringResource(R.string.benchmark_unit_sec)
          val unitMs = stringResource(R.string.benchmark_unit_ms)

          ValueSeriesRow(
            label = stringResource(R.string.benchmark_stat_prefill_speed),
            valueSeries = llmResult.stats.prefillSpeed,
            aggregation = result.aggregation,
            unit = unitTokensSec,
            baselineValueSeries = if (isDifferentFromBaseline) baselineStats?.prefillSpeed else null,
            baselineAggregation = if (isDifferentFromBaseline) baselineResult?.aggregation else null,
          )
          ValueSeriesRow(
            label = stringResource(R.string.benchmark_stat_decode_speed),
            valueSeries = llmResult.stats.decodeSpeed,
            aggregation = result.aggregation,
            unit = unitTokensSec,
            baselineValueSeries = if (isDifferentFromBaseline) baselineStats?.decodeSpeed else null,
            baselineAggregation = if (isDifferentFromBaseline) baselineResult?.aggregation else null,
          )
          ValueSeriesRow(
            label = stringResource(R.string.benchmark_stat_time_to_first_token),
            valueSeries = llmResult.stats.timeToFirstToken,
            aggregation = result.aggregation,
            unit = unitSec,
            baselineValueSeries = if (isDifferentFromBaseline) baselineStats?.timeToFirstToken else null,
            baselineAggregation = if (isDifferentFromBaseline) baselineResult?.aggregation else null,
            lessIsBetter = true,
          )
          StatRow(
            label = stringResource(R.string.benchmark_stat_first_init_time),
            value = String.format(Locale.US, "%.2f", llmResult.stats.firstInitTimeMs),
            unit = unitMs,
            rawValue = llmResult.stats.firstInitTimeMs,
            baselineValue = if (isDifferentFromBaseline) baselineStats?.firstInitTimeMs else null,
            lessIsBetter = true,
          )
          if (llmResult.stats.nonFirstInitTimeMs.valueCount > 1) {
            ValueSeriesRow(
              label = stringResource(R.string.benchmark_stat_steady_init_time),
              valueSeries = llmResult.stats.nonFirstInitTimeMs,
              aggregation = result.aggregation,
              unit = unitMs,
              baselineValueSeries = if (isDifferentFromBaseline) baselineStats?.nonFirstInitTimeMs else null,
              baselineAggregation = if (isDifferentFromBaseline) baselineResult?.aggregation else null,
              lessIsBetter = true,
            )
          }
        }
      }

      // Actions row (Delete, Copy CSV)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth(),
      ) {
        OutlinedButton(
          onClick = onDelete,
          contentPadding = SMALL_BUTTON_CONTENT_PADDING,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Text(stringResource(R.string.delete))
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
          onClick = {
            scope.launch {
              val csv = getBenchmarkResultCsv(
                llmResult = llmResult,
                aggregation = result.aggregation,
              )
              copyToClipboard(copyContext, "OlliteRT Benchmark Results", csv, formatSuffix = "CSV")
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
          ),
          contentPadding = SMALL_BUTTON_CONTENT_PADDING,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              Icons.Rounded.ContentCopy,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
              stringResource(R.string.copy),
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }
    }
  }
}
