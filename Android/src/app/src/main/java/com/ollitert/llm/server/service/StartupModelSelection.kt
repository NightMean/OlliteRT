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

package com.ollitert.llm.server.service

import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.repository.ImportedModelRecoveryException
import com.ollitert.llm.server.proto.ImportedModel

internal data class StartupModelSelection(
  val requestedName: String,
  val model: Model?,
  val fallback: Boolean = false,
  val recoveryFailure: ImportedModelRecoveryException? = null,
)

internal suspend fun selectStartupModel(
  requestedName: String,
  autoStart: Boolean,
  defaultName: () -> String?,
  readRecoveredImports: suspend () -> List<ImportedModel>,
  pickByName: (String, List<ImportedModel>) -> Model?,
  pickFallback: (String, List<ImportedModel>) -> Model?,
  modelExists: (Model) -> Boolean,
): StartupModelSelection {
  val initialName = if (autoStart) defaultName() ?: requestedName else requestedName
  val catalogModel = pickByName(initialName, emptyList())
  if (catalogModel != null && (!autoStart || modelExists(catalogModel))) {
    return StartupModelSelection(initialName, catalogModel)
  }

  val imports = try {
    readRecoveredImports()
  } catch (e: ImportedModelRecoveryException) {
    // A failed import journal cannot affect files owned by the catalog.
    if (!autoStart) throw e
    if (catalogModel != null && modelExists(catalogModel)) {
      return StartupModelSelection(initialName, catalogModel, recoveryFailure = e)
    }
    val fallback = pickFallback(initialName, emptyList())
    if (fallback == null) throw e
    return StartupModelSelection(initialName, fallback, fallback = true, recoveryFailure = e)
  }
  // Recovery can complete a rename and update the saved default.
  val recoveredName = if (autoStart) defaultName() ?: initialName else initialName
  val model = catalogModel?.takeIf { recoveredName == initialName } ?: pickByName(recoveredName, imports)
  if (autoStart && (model == null || !modelExists(model))) {
    val fallback = pickFallback(recoveredName, imports)
    if (fallback != null) return StartupModelSelection(recoveredName, fallback, fallback = true)
  }
  return StartupModelSelection(recoveredName, model)
}
