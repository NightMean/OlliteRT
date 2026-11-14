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

import android.content.Context
import com.ollitert.llm.server.data.allowlist.DiscoveredModels
import com.ollitert.llm.server.data.allowlist.HuggingFaceCatalog
import com.ollitert.llm.server.data.allowlist.HuggingFaceFile
import com.ollitert.llm.server.data.allowlist.HuggingFaceModel
import com.ollitert.llm.server.data.repository.PreferencesRepository
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class HuggingFaceExploreViewModelTest {
  @get:Rule val temporary = TemporaryFolder()
  private val dispatcher = StandardTestDispatcher()
  @Before fun setup() { Dispatchers.setMain(dispatcher) }
  @After fun teardown() { Dispatchers.resetMain() }

  @Test fun savesBeforeReportingImportAndIgnoresSearchDuringSave() = runTest(dispatcher) {
    val dir = temporary.newFolder()
    val context = mockk<Context> { every { getExternalFilesDir(null) } returns dir }
    val preferences = mockk<PreferencesRepository> { every { getHfToken() } returns "" }
    val file = HuggingFaceFile("model.litertlm", size = 123)
    val model = HuggingFaceModel("author/model", sha = "a".repeat(40), siblings = listOf(file))
    val catalog = mockk<HuggingFaceCatalog> {
      every { search(any(), any(), any()) } returns listOf(model)
      every { details(model.id, "", null) } returns model
    }
    val vm = HuggingFaceExploreViewModel(context, preferences, catalog, dispatcher)
    advanceUntilIdle()
    vm.open(model)
    assertEquals(model.id, vm.state.value.openingModelId)
    assertNull(vm.state.value.details)
    assertFalse(vm.state.value.loading)
    advanceUntilIdle()
    assertNull(vm.state.value.openingModelId)
    val config = LlmConfig.newBuilder().addCompatibleAccelerators("CPU").setDefaultMaxTokens(1024).build()
    vm.add(file, ImportedModel.newBuilder().setLlmConfig(config).build())
    vm.search("ignored")
    vm.closeDetails()
    assertTrue(vm.state.value.saving)
    assertNull(vm.state.value.addedModelName)
    advanceUntilIdle()
    assertEquals(1, DiscoveredModels(dir).read().size)
    assertNotNull(vm.state.value.addedModelName)
    assertEquals("", vm.state.value.query)
    assertTrue(vm.state.value.saving)
  }
}
