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

package com.ollitert.llm.server.ui.modelmanager

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.proto.ImportedModel

class ModelManagerDialogState internal constructor() {
  var showImportModelSheet by mutableStateOf(false)
  var showImportUrlDialog by mutableStateOf(false)
  internal var importUrlLoading by mutableStateOf(false)
  internal var importUrlError by mutableStateOf<String?>(null)
  var showUnsupportedFileTypeDialog by mutableStateOf(false)
  var showUnsupportedWebModelDialog by mutableStateOf(false)
  internal val selectedLocalModelFileUri = mutableStateOf<Uri?>(null)
  internal val selectedImportedModelInfo = mutableStateOf<ImportedModel?>(null)
  var showImportDialog by mutableStateOf(false)
  var showImportingDialog by mutableStateOf(false)
  var showSwitchModelDialog by mutableStateOf(false)
  var pendingSwitchModel by mutableStateOf<Model?>(null)
}

@Composable
fun rememberModelManagerDialogState(): ModelManagerDialogState {
  return remember { ModelManagerDialogState() }
}
