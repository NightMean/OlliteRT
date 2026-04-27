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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.UnfoldLessDouble
import androidx.compose.material.icons.rounded.UnfoldMoreDouble
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.copyToClipboard
import com.ollitert.llm.server.proto.LlmBenchmarkResult
import com.ollitert.llm.server.proto.ValueSeries
import com.ollitert.llm.server.ui.common.Accordions
import com.ollitert.llm.server.ui.common.MarkdownText
import com.ollitert.llm.server.ui.common.SHEET_MAX_WIDTH
import com.ollitert.llm.server.ui.common.SMALL_BUTTON_CONTENT_PADDING
import com.ollitert.llm.server.ui.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkResultsViewer(
  initialModelName: String,
  viewModel: BenchmarkViewModel,
  onClose: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showConfirmDeleteDialog by remember { mutableStateOf(false) }
  var showLazyListPlacementAnimation by remember { mutableStateOf(false) }
  var showBenchmarkComparisonHelpBottomSheet by remember { mutableStateOf(false) }
  var benchmarkResultIdToDelete by remember { mutableStateOf("") }
  val filterableModelNames = remember { mutableStateListOf<String>() }
  var selectedModelName by remember { mutableStateOf(initialModelName) }
  val filteredResults = remember { mutableStateListOf<BenchmarkResultInfo>() }
  val strAll = stringResource(R.string.all)
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  // Update filterable model names.
  LaunchedEffect(uiState.results) {
    filterableModelNames.clear()
    filterableModelNames.add(strAll)
    filterableModelNames.addAll(
      uiState.results.mapNotNull { it.benchmarkResult.llmResult?.basicInfo?.modelName }.distinct()
    )
  }

  // Update filteredResults when selected model is changed.
  LaunchedEffect(selectedModelName, uiState.results) {
    filteredResults.clear()
    filteredResults.addAll(
      uiState.results.filter {
        selectedModelName == strAll ||
          it.benchmarkResult.llmResult?.basicInfo?.modelName == selectedModelName
      }
    )
  }

  // Reset baseline when model selection is changed.
  LaunchedEffect(selectedModelName) { viewModel.clearBaseline() }

  // Show "benchmark comparison help" bottom sheet when there are multiple results available.
  LaunchedEffect(filteredResults.size) {
    if (
      filteredResults.size > 1 && !viewModel.dataStoreRepository.getHasSeenBenchmarkComparisonHelp()
    ) {
      delay(500)
      showBenchmarkComparisonHelpBottomSheet = true
      viewModel.dataStoreRepository.setHasSeenBenchmarkComparisonHelp(true)
    }
  }

  // Close it when back button is clicked.
  BackHandler {
    if (!uiState.running) {
      onClose()
    }
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        // Title label.
        title = {
          if (!uiState.running) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                stringResource(R.string.benchmark_results),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              BenchmarkModelPicker(
                selectedModelName = selectedModelName,
                modelNames = filterableModelNames,
                titleResId = R.string.select_model,
                onSelected = {
                  showLazyListPlacementAnimation = true
                  selectedModelName = it
                  scope.launch {
                    delay(500)
                    showLazyListPlacementAnimation = false
                  }
                },
              )
            }
          }
        },
        navigationIcon = {
          if (filteredResults.size > 1) {
            IconButton(onClick = { showBenchmarkComparisonHelpBottomSheet = true }) {
              Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(R.string.cd_help),
              )
            }
          } else {
            Spacer(modifier = Modifier.size(48.dp))
          }
        },
        // The close button.
        actions = {
          if (!uiState.running) {
            IconButton(onClick = onClose) {
              Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
            }
          }
        },
      )
    },
    modifier = Modifier.fillMaxSize(),
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize()) {
      AnimatedContent(
        targetState = uiState.running,
        transitionSpec = {
          // Running.
          if (targetState) {
            scaleIn(initialScale = 0.8f) + fadeIn() togetherWith
              scaleOut(targetScale = 0.8f) + fadeOut()
          }
          // Results.
          else {
            slideInVertically { 40 } + fadeIn() togetherWith slideOutVertically { 40 } + fadeOut()
          }
        },
      ) { running ->
        // Running in progress.
        if (running) {
          Box(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier =
                Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                // Progress spinner.
                CircularProgressIndicator(strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
                // Info text.
                Text(
                  stringResource(R.string.running_benchmark_msg),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                // Progress text.
                Text(
                  "${uiState.completedRunCount} / ${uiState.totalRunCount}",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.labelLarge,
                )
              }
            }
          }
        } else {
          Box(
            modifier =
              Modifier.fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.TopCenter,
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              // Results.
              //
              // Empty state.
              if (filteredResults.isEmpty()) {
                Column(
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.fillMaxSize(),
                ) {
                  Text(
                    stringResource(R.string.benchmark_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = TextAlign.Center,
                  )
                }
              } else {
                // List.
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                  item { Spacer(modifier = Modifier.height(16.dp)) }
                  if (filteredResults.size > 1) {
                    item {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp),
                      ) {
                        OutlinedButton(
                          onClick = { viewModel.expandAll() },
                          contentPadding = SMALL_BUTTON_CONTENT_PADDING,
                        ) {
                          Icon(
                            Icons.Rounded.UnfoldMoreDouble,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp).size(16.dp),
                          )
                          Text(stringResource(R.string.expand_all))
                        }
                        OutlinedButton(
                          onClick = { viewModel.collapseAll() },
                          contentPadding = SMALL_BUTTON_CONTENT_PADDING,
                        ) {
                          Icon(
                            Icons.Rounded.UnfoldLessDouble,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp).size(16.dp),
                          )
                          Text(stringResource(R.string.collapse_all))
                        }
                      }
                    }
                  }
                  itemsIndexed(items = filteredResults, key = { index, item -> item.id }) {
                    index,
                    result ->
                    // Result card.
                    var cardModifier = Modifier.clip(RoundedCornerShape(24.dp)).fillMaxWidth()
                    if (showLazyListPlacementAnimation) {
                      cardModifier = cardModifier.animateItem()
                    }
                    result.benchmarkResult.llmResult?.let { llmResult ->
                      val modelName = llmResult.basicInfo.modelName
                      Accordions(
                        title = "$modelName · ${llmResult.basicInfo.accelerator}",
                        subtitle =
                          SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(Date(llmResult.basicInfo.startMs)),
                        boldTitle = true,
                        expanded = result.expanded,
                        onExpandedChange = { viewModel.setExpanded(id = result.id, expanded = it) },
                        modifier = cardModifier,
                        titleRowAction = {
                          // A chip to toggle on/off baseline, used to set the comparison base.
                          // Only visible when there are >=2 results.
                          if (filteredResults.size > 1) {
                            FilterChip(
                              onClick = { viewModel.setBaseline(id = result.id) },
                              label = {
                                Text(
                                  stringResource(R.string.baseline),
                                  style = MaterialTheme.typography.labelSmall,
                                )
                              },
                              selected = result.id == uiState.baselineResult?.id,
                              leadingIcon =
                                if (result.id == uiState.baselineResult?.id) {
                                  {
                                    Icon(
                                      Icons.Rounded.Check,
                                      contentDescription = null,
                                      modifier = Modifier.size(16.dp).offset(x = 2.dp),
                                    )
                                  }
                                } else {
                                  null
                                },
                              modifier = Modifier.height(24.dp),
                            )
                          }
                        },
                      ) {
                        Column(
                          verticalArrangement = Arrangement.spacedBy(8.dp),
                          modifier = Modifier.padding(bottom = 2.dp),
                        ) {
                          // Basic info.
                          Accordions(
                            title = stringResource(R.string.basic_info),
                            bgColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            expanded = result.basicInfoExpanded,
                            onExpandedChange = {
                              viewModel.setBasicInfoExpanded(id = result.id, expanded = it)
                            },
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                          ) {
                            Column(
                              verticalArrangement = Arrangement.spacedBy(8.dp),
                              modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 4.dp),
                            ) {
                              StatRow(label = stringResource(R.string.benchmark_stat_model), value = llmResult.basicInfo.modelName)
                              StatRow(
                                label = stringResource(R.string.benchmark_stat_accelerator),
                                value = llmResult.basicInfo.accelerator,
                              )
                              StatRow(
                                label = stringResource(R.string.benchmark_stat_prefill_tokens),
                                value = "${llmResult.basicInfo.prefillTokens}",
                              )
                              StatRow(
                                label = stringResource(R.string.benchmark_stat_decode_tokens),
                                value = "${llmResult.basicInfo.decodeTokens}",
                              )
                              StatRow(
                                label = stringResource(R.string.benchmark_stat_number_of_runs),
                                value = "${llmResult.basicInfo.numberOfRuns}",
                              )
                              StatRow(label = stringResource(R.string.benchmark_stat_app_version), value = llmResult.basicInfo.appVersion)
                            }
                          }

                          // Stats
                          val resources = LocalResources.current
                          Accordions(
                            title =
                              "${stringResource(R.string.results)} (${resources.getQuantityString(
                                R.plurals.runs ,
                                llmResult.basicInfo.numberOfRuns,
                                llmResult.basicInfo.numberOfRuns,
                              )})",
                            bgColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            expanded = result.statsExpanded,
                            onExpandedChange = {
                              viewModel.setStatsExpanded(id = result.id, expanded = it)
                            },
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                            titleRowAction = {
                              if (
                                (result.benchmarkResult.llmResult?.basicInfo?.numberOfRuns ?: 0) > 1
                              ) {
                                var showAggregationDropdown by remember { mutableStateOf(false) }
                                // Aggregation method.
                                Box {
                                  Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                      Modifier.clip(RoundedCornerShape(8.dp))
                                        .clickable { showAggregationDropdown = true }
                                        .background(
                                          MaterialTheme.colorScheme.surfaceContainerLowest
                                        )
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
                                          viewModel.setAggregation(
                                            id = result.id,
                                            aggregation = aggregation,
                                          )
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
                              val baselineStats =
                                uiState.baselineResult?.benchmarkResult?.llmResult?.stats
                              val unitTokensSec = stringResource(R.string.benchmark_unit_tokens_per_sec)
                              val unitSec = stringResource(R.string.benchmark_unit_sec)
                              val unitMs = stringResource(R.string.benchmark_unit_ms)
                              ValueSeriesRow(
                                label = stringResource(R.string.benchmark_stat_prefill_speed),
                                valueSeries = llmResult.stats.prefillSpeed,
                                aggregation = result.aggregation,
                                unit = unitTokensSec,
                                baselineValueSeries =
                                  if (result.id != uiState.baselineResult?.id) {
                                    baselineStats?.prefillSpeed
                                  } else {
                                    null
                                  },
                                baselineAggregation =
                                  if (result.id != uiState.baselineResult?.id) {
                                    uiState.baselineResult?.aggregation
                                  } else {
                                    null
                                  },
                              )
                              ValueSeriesRow(
                                label = stringResource(R.string.benchmark_stat_decode_speed),
                                valueSeries = llmResult.stats.decodeSpeed,
                                aggregation = result.aggregation,
                                unit = unitTokensSec,
                                baselineValueSeries =
                                  if (result.id != uiState.baselineResult?.id) {
                                    baselineStats?.decodeSpeed
                                  } else {
                                    null
                                  },
                                baselineAggregation =
                                  if (result.id != uiState.baselineResult?.id) {
                                    uiState.baselineResult?.aggregation
                                  } else {
                                    null
                                  },
                              )
                              ValueSeriesRow(
                                label = stringResource(R.string.benchmark_stat_time_to_first_token),
                                valueSeries = llmResult.stats.timeToFirstToken,
                                aggregation = result.aggregation,
                                unit = unitSec,
                                baselineValueSeries =
                                  if (result.id != uiState.baselineResult?.id) {
                                    baselineStats?.timeToFirstToken
                                  } else {
                                    null
                                  },
                                baselineAggregation =
                                  if (result.id != uiState.baselineResult?.id) {
                                    uiState.baselineResult?.aggregation
                                  } else {
                                    null
                                  },
                                lessIsBetter = true,
                              )
                              StatRow(
                                label = stringResource(R.string.benchmark_stat_first_init_time),
                                value =
                                  String.format(
                                    Locale.US,
                                    "%.2f",
                                    llmResult.stats.firstInitTimeMs,
                                  ),
                                unit = unitMs,
                                rawValue = llmResult.stats.firstInitTimeMs,
                                baselineValue =
                                  if (result.id != uiState.baselineResult?.id) {
                                    baselineStats?.firstInitTimeMs
                                  } else {
                                    null
                                  },
                                lessIsBetter = true,
                              )
                              if (llmResult.stats.nonFirstInitTimeMs.valueCount > 1) {
                                ValueSeriesRow(
                                  label = stringResource(R.string.benchmark_stat_steady_init_time),
                                  valueSeries = llmResult.stats.nonFirstInitTimeMs,
                                  aggregation = result.aggregation,
                                  unit = unitMs,
                                  baselineValueSeries =
                                    if (result.id != uiState.baselineResult?.id) {
                                      baselineStats?.nonFirstInitTimeMs
                                    } else {
                                      null
                                    },
                                  baselineAggregation =
                                    if (result.id != uiState.baselineResult?.id) {
                                      uiState.baselineResult?.aggregation
                                    } else {
                                      null
                                    },
                                  lessIsBetter = true,
                                )
                              }
                            }
                          }

                          // Buttons.
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                          ) {
                            // Delete.
                            OutlinedButton(
                              onClick = {
                                benchmarkResultIdToDelete = result.id
                                showConfirmDeleteDialog = true
                              },
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

                            // Copy
                            val copyContext = LocalContext.current
                            Button(
                              onClick = {
                                scope.launch {
                                  val csv =
                                    getBenchmarkResultCsv(
                                      llmResult = llmResult,
                                      aggregation = result.aggregation,
                                    )
                                  copyToClipboard(copyContext, "OlliteRT Benchmark Results", csv, formatSuffix = "CSV")
                                }
                              },
                              colors =
                                ButtonDefaults.buttonColors(
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
                    if (index != filteredResults.size - 1) {
                      Spacer(modifier = Modifier.height(12.dp).animateItem(placementSpec = null))
                    }
                  }
                  item { Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding())) }
                }
              }
            }

            // Gradient overlay at the bottom.
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(innerPadding.calculateBottomPadding())
                  .background(
                    Brush.verticalGradient(
                      colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainer)
                    )
                  )
                  .align(Alignment.BottomCenter)
            )
          }
        }
      }
    }
  }

  if (showConfirmDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmDeleteDialog = false },
      title = { Text(stringResource(R.string.delete_benchmark_result_dialog_title)) },
      text = { Text(stringResource(R.string.delete_benchmark_result_dialog_content)) },
      confirmButton = {
        Button(
          onClick = {
            showLazyListPlacementAnimation = true
            showConfirmDeleteDialog = false
            viewModel.deleteBenchmarkResult(id = benchmarkResultIdToDelete)

            scope.launch {
              delay(500)
              showLazyListPlacementAnimation = false
            }
          },
          contentPadding = SMALL_BUTTON_CONTENT_PADDING,
        ) {
          Text(stringResource(R.string.delete))
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showConfirmDeleteDialog = false },
          contentPadding = SMALL_BUTTON_CONTENT_PADDING,
        ) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  if (showBenchmarkComparisonHelpBottomSheet) {
    ModalBottomSheet(
      onDismissRequest = { showBenchmarkComparisonHelpBottomSheet = false },
      sheetState = sheetState,
      sheetMaxWidth = SHEET_MAX_WIDTH,
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
          Text(
            stringResource(R.string.benchmark_comparison_help_title),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        MarkdownText(
          text = stringResource(R.string.benchmark_comparison_help_content),
          smallFontSize = true,
        )
        OutlinedButton(
          onClick = {
            scope.launch {
              sheetState.hide()
              showBenchmarkComparisonHelpBottomSheet = false
            }
          },
          contentPadding = SMALL_BUTTON_CONTENT_PADDING,
          modifier = Modifier.align(alignment = Alignment.End),
        ) {
          Text(stringResource(R.string.dismiss))
        }
      }
    }
  }
}

@Composable
private fun StatRow(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  unit: String = "",
  rawValue: Double? = null,
  baselineValue: Double? = null,
  lessIsBetter: Boolean = false,
) {
  Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    // label.
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(0.6f),
      maxLines = 1,
      overflow = TextOverflow.MiddleEllipsis,
    )
    // Value
    Column(
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.Start,
      modifier = Modifier.weight(0.4f),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          value,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.MiddleEllipsis,
        )
        AnimatedContent(
          baselineValue,
          contentAlignment = Alignment.CenterStart,
          transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { curBaselineValue ->
          if (curBaselineValue != null && rawValue != null) {
            val doubleValue = rawValue
            val pct = (doubleValue - curBaselineValue) / curBaselineValue * 100
            val strPct = String.format(Locale.US, "%.1f", abs(pct))
            val sign = if (pct >= 0.0) "+" else "-"
            val betterSign = if (lessIsBetter) "-" else "+"
            val color =
              if (sign == betterSign) {
                MaterialTheme.customColors.successColor
              } else {
                MaterialTheme.customColors.errorTextColor
              }
            Text("$sign$strPct%", style = MaterialTheme.typography.labelMedium, color = color)
          }
        }
      }
      if (unit.isNotEmpty()) {
        Text(
          unit,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      }
    }
  }
}

@Composable
private fun ValueSeriesRow(
  label: String,
  valueSeries: ValueSeries,
  aggregation: Aggregation,
  modifier: Modifier = Modifier,
  unit: String = "",
  baselineValueSeries: ValueSeries? = null,
  baselineAggregation: Aggregation? = null,
  lessIsBetter: Boolean = false,
) {
  val value = getAggregationValue(valueSeries = valueSeries, aggregation = aggregation)
  var baselineValue: Double? = null
  if (baselineValueSeries != null && baselineAggregation != null) {
    baselineValue =
      getAggregationValue(valueSeries = baselineValueSeries, aggregation = baselineAggregation)
  }
  var showValueSeriesBottomSheet by remember { mutableStateOf(false) }

  Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    // label.
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(0.6f),
      maxLines = 1,
      overflow = TextOverflow.MiddleEllipsis,
    )
    // Value
    Column(
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.Start,
      modifier = Modifier.weight(0.4f),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val linkColor = MaterialTheme.customColors.linkColor
        val isMultipleRuns = valueSeries.valueCount > 1
        val textColor = if (isMultipleRuns) linkColor else MaterialTheme.colorScheme.onSurface
        val textModifier =
          if (isMultipleRuns) {
            Modifier.drawBehind {
                val strokeWidth = 2f
                val y = size.height - strokeWidth

                // Define the dash pattern: 8px line, 8px gap
                val dashPath = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                drawLine(
                  color = linkColor,
                  start = Offset(0f, y),
                  end = Offset(size.width, y),
                  strokeWidth = strokeWidth,
                  pathEffect = dashPath,
                )
              }
              .clickable { showValueSeriesBottomSheet = true }
          } else {
            Modifier
          }
        AnimatedContent(value) { curValue ->
          Text(
            String.format(Locale.US, "%.2f", curValue),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = textModifier,
          )
        }
        AnimatedContent(
          baselineValue,
          contentAlignment = Alignment.CenterStart,
          transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { curBaselineValue ->
          if (curBaselineValue != null && abs(curBaselineValue) > 1e-6) {
            val pct = (value - curBaselineValue) / curBaselineValue * 100
            val strPct = String.format(Locale.US, "%.1f", abs(pct))
            val sign = if (pct >= 0.0) "+" else "-"
            val betterSign = if (lessIsBetter) "-" else "+"
            val color =
              if (sign == betterSign) {
                MaterialTheme.customColors.successColor
              } else {
                MaterialTheme.customColors.errorTextColor
              }
            Text("$sign$strPct%", style = MaterialTheme.typography.labelMedium, color = color)
          }
        }
      }
      if (unit.isNotEmpty()) {
        Text(
          unit,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      }
    }
  }

  if (showValueSeriesBottomSheet) {
    BenchmarkValueSeriesViewer(
      title = "$label ($unit)",
      valueSeries = valueSeries,
      onDismiss = { showValueSeriesBottomSheet = false },
    )
  }
}

private fun getBenchmarkResultCsv(llmResult: LlmBenchmarkResult, aggregation: Aggregation): String {
  val basicInfo = llmResult.basicInfo
  val stats = llmResult.stats

  val header =
    listOf(
        "start time (ms)",
        "end time (ms)",
        "model name",
        "accelerator",
        "prefill tokens count",
        "decode tokens count",
        "runs count",
        "app version",
        "prefill speed (tokens/sec)",
        "decode speed (tokens/sec)",
        "time to first token (sec)",
        "first init time (ms)",
        "steady init time (ms)",
      )
      .joinToString(",")

  val data =
    listOf(
        basicInfo.startMs,
        basicInfo.endMs,
        basicInfo.modelName,
        basicInfo.accelerator,
        basicInfo.prefillTokens,
        basicInfo.decodeTokens,
        basicInfo.numberOfRuns,
        basicInfo.appVersion,
        getAggregationValue(stats.prefillSpeed, aggregation),
        getAggregationValue(stats.decodeSpeed, aggregation),
        getAggregationValue(stats.timeToFirstToken, aggregation),
        stats.firstInitTimeMs,
        getAggregationValue(stats.nonFirstInitTimeMs, aggregation),
      )
      .joinToString(",")

  return "$header\n$data"
}

private fun getAggregationValue(valueSeries: ValueSeries, aggregation: Aggregation): Double {
  return when (aggregation) {
    Aggregation.AVG -> valueSeries.avg
    Aggregation.MEDIAN -> valueSeries.medium
    // Aggregation.P25 -> valueSeries.pct25
    // Aggregation.P75 -> valueSeries.pct75
    Aggregation.MIN -> valueSeries.min
    Aggregation.MAX -> valueSeries.max
  }
}
