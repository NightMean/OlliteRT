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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R

@Composable
internal fun RepositoryLoadingIndicator(loading: Boolean) {
  // Keep the list in place while a search begins or ends, so a tap cannot move to another card.
  Box(Modifier.fillMaxWidth().height(4.dp)) {
    if (loading) {
      val description = stringResource(R.string.repository_loading_models)
      LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = description })
    }
  }
}

@Composable
internal fun rememberRepositoryErrorSnackbar(message: String?, onRetry: () -> Unit, onDismiss: () -> Unit): SnackbarHostState {
  val host = remember { SnackbarHostState() }
  val currentRetry by rememberUpdatedState(onRetry)
  val currentDismiss by rememberUpdatedState(onDismiss)
  val retry = stringResource(R.string.retry)
  LaunchedEffect(message, retry) {
    if (message != null) {
      when (host.showSnackbar(message, retry, withDismissAction = true, duration = SnackbarDuration.Indefinite)) {
        SnackbarResult.ActionPerformed -> currentRetry()
        SnackbarResult.Dismissed -> currentDismiss()
      }
    }
  }
  return host
}
