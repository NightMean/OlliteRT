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
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StartupModelSelectionTest {
  @Test
  fun autoStartUsesDownloadedCatalogFallbackWhenImportRecoveryFails() = runTest {
    val missing = Model(name = "Configured model")
    val downloaded = Model(name = "Downloaded model")
    val recoveryFailure = ImportedModelRecoveryException(IOException("Unresolved journal"))

    val selection = selectStartupModel(
      requestedName = missing.name,
      autoStart = true,
      defaultName = { missing.name },
      readRecoveredImports = { throw recoveryFailure },
      pickByName = { _, _ -> missing },
      pickFallback = { excluded, imports ->
        assertEquals(missing.name, excluded)
        assertTrue(imports.isEmpty())
        downloaded
      },
      modelExists = { it == downloaded },
    )

    assertEquals(downloaded, selection.model)
    assertTrue(selection.fallback)
    assertSame(recoveryFailure, selection.recoveryFailure)
  }

  @Test
  fun availableCatalogDefaultDoesNotReadImports() = runTest {
    val catalog = Model(name = "Installed model")
    val selection = selectStartupModel(
      requestedName = "Old default",
      autoStart = true,
      defaultName = { catalog.name },
      readRecoveredImports = { error("Catalog default does not depend on imports") },
      pickByName = { name, _ -> catalog.takeIf { it.name == name } },
      pickFallback = { _, _ -> error("Catalog default is available") },
      modelExists = { true },
    )

    assertEquals(catalog, selection.model)
    assertFalse(selection.fallback)
  }

  @Test
  fun catalogDefaultThatFinishesDownloadingDuringRecoveryStillWins() = runTest {
    val primary = Model(name = "Configured model")
    var downloaded = false
    val recoveryFailure = ImportedModelRecoveryException(IOException("Unresolved journal"))
    val selection = selectStartupModel(
      requestedName = primary.name,
      autoStart = true,
      defaultName = { null },
      readRecoveredImports = {
        downloaded = true
        throw recoveryFailure
      },
      pickByName = { name, _ -> primary.takeIf { it.name == name } },
      pickFallback = { _, _ -> error("The configured model is available now") },
      modelExists = { downloaded },
    )

    assertEquals(primary, selection.model)
    assertFalse(selection.fallback)
    assertSame(recoveryFailure, selection.recoveryFailure)
  }

  @Test
  fun autoStartPrefersRecoveredRenamedDefault() = runTest {
    var defaultName = "Old import"
    val renamed = Model(name = "Renamed import")
    val record = ImportedModel.newBuilder().setFileName("renamed.litertlm").build()
    val selection = selectStartupModel(
      requestedName = defaultName,
      autoStart = true,
      defaultName = { defaultName },
      readRecoveredImports = {
        defaultName = renamed.name
        listOf(record)
      },
      pickByName = { name, imports -> renamed.takeIf { name == it.name && record in imports } },
      pickFallback = { _, _ -> error("Recovered default is available") },
      modelExists = { true },
    )

    assertEquals(renamed, selection.model)
    assertFalse(selection.fallback)
  }

  @Test
  fun manualStartAndUnavailableFallbackPreserveRecoveryFailure() = runTest {
    val recoveryFailure = ImportedModelRecoveryException(IOException("Unresolved journal"))
    for (autoStart in listOf(false, true)) {
      try {
        selectStartupModel(
          requestedName = "Imported model",
          autoStart = autoStart,
          defaultName = { null },
          readRecoveredImports = { throw recoveryFailure },
          pickByName = { _, _ -> null },
          pickFallback = { _, _ ->
            assertTrue("Manual starts must keep the requested model", autoStart)
            null
          },
          modelExists = { false },
        )
        fail("Expected the recovery failure")
      } catch (e: ImportedModelRecoveryException) {
        assertSame(recoveryFailure, e)
      }
    }
  }

  @Test
  fun cancellationDoesNotSelectFallback() = runTest {
    val cancellation = CancellationException("Startup was cancelled")
    try {
      selectStartupModel(
        requestedName = "Imported model",
        autoStart = true,
        defaultName = { null },
        readRecoveredImports = { throw cancellation },
        pickByName = { _, _ -> null },
        pickFallback = { _, _ -> error("Cancelled startup must not select a fallback") },
        modelExists = { false },
      )
      fail("Expected startup cancellation")
    } catch (e: CancellationException) {
      assertSame(cancellation, e)
    }
  }
}
