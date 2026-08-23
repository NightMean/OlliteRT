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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.UnfoldLessDouble
import androidx.compose.material.icons.rounded.UnfoldMoreDouble
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.common.SMALL_BUTTON_CONTENT_PADDING
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
          if (targetState) {
            scaleIn(initialScale = 0.8f) + fadeIn() togetherWith
              scaleOut(targetScale = 0.8f) + fadeOut()
          } else {
            slideInVertically { 40 } + fadeIn() togetherWith slideOutVertically { 40 } + fadeOut()
          }
        },
      ) { running ->
        if (running) {
          Box(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
              Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                CircularProgressIndicator(strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
                Text(
                  stringResource(R.string.running_benchmark_msg),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
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
            modifier = Modifier
              .fillMaxSize()
              .padding(top = innerPadding.calculateTopPadding())
              .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.TopCenter,
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                  itemsIndexed(items = filteredResults, key = { _, item -> item.id }) { index, result ->
                    var cardModifier = Modifier.clip(RoundedCornerShape(24.dp)).fillMaxWidth()
                    if (showLazyListPlacementAnimation) {
                      cardModifier = cardModifier.animateItem()
                    }
                    BenchmarkResultCard(
                      result = result,
                      baselineResult = uiState.baselineResult,
                      showBaselineToggle = filteredResults.size > 1,
                      onExpandedChange = { viewModel.setExpanded(id = result.id, expanded = it) },
                      onBasicInfoExpandedChange = { viewModel.setBasicInfoExpanded(id = result.id, expanded = it) },
                      onStatsExpandedChange = { viewModel.setStatsExpanded(id = result.id, expanded = it) },
                      onBaselineToggle = { viewModel.setBaseline(id = result.id) },
                      onAggregationChange = { viewModel.setAggregation(id = result.id, aggregation = it) },
                      onDelete = {
                        benchmarkResultIdToDelete = result.id
                        showConfirmDeleteDialog = true
                      },
                      modifier = cardModifier,
                    )
                    if (index != filteredResults.size - 1) {
                      Spacer(modifier = Modifier.height(12.dp).animateItem(placementSpec = null))
                    }
                  }
                  item { Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding())) }
                }
              }
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
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
    BenchmarkDeleteConfirmDialog(
      onConfirm = {
        showLazyListPlacementAnimation = true
        showConfirmDeleteDialog = false
        viewModel.deleteBenchmarkResult(id = benchmarkResultIdToDelete)
        scope.launch {
          delay(500)
          showLazyListPlacementAnimation = false
        }
      },
      onDismiss = { showConfirmDeleteDialog = false },
    )
  }

  if (showBenchmarkComparisonHelpBottomSheet) {
    BenchmarkComparisonHelpBottomSheet(
      sheetState = sheetState,
      onDismiss = {
        scope.launch {
          sheetState.hide()
          showBenchmarkComparisonHelpBottomSheet = false
        }
      },
    )
  }
}
