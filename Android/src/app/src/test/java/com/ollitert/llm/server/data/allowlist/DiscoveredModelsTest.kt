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

package com.ollitert.llm.server.data.allowlist

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiscoveredModelsTest {
  @get:Rule val temporary = TemporaryFolder()
  private fun model(path: String = "nested/model file.litertlm") = DiscoveredModel(
    repository = "author/model", path = path, revision = "a".repeat(40), size = 123,
    vision = true, accelerators = listOf("cpu", "gpu"),
  )

  @Test fun legacyHuggingFaceCatalogKeepsIdentity() {
    val directory = temporary.newFolder()
    val id = "c2fc03b7-086f-4419-a966-0cc28c89cf02"
    val expected = model("model.litertlm").copy(localId = id, vision = false, accelerators = listOf("cpu"))
    File(directory, "huggingface_models.json").writeText(
      """[{"repository":"author/model","path":"model.litertlm","revision":"${"a".repeat(40)}","size":123,"localId":"$id"}]"""
    )
    val restored = DiscoveredModels(directory).read().single()
    assertEquals(expected, restored)
    assertEquals(expected.toAllowedModel().name, restored.toAllowedModel().name)
  }

  @Test fun modelScopeAndHuggingFaceSelectionsAreIsolatedAndReachServerCatalog() {
    val directory = temporary.newFolder()
    val huggingFace = model()
    val modelScope = huggingFace.copy(provider = DiscoveredProvider.MODELSCOPE)
    val hfStore = DiscoveredModels(directory)
    val msStore = DiscoveredModels(directory, DiscoveredProvider.MODELSCOPE)
    assertTrue(hfStore.add(huggingFace))
    assertTrue(msStore.add(modelScope))
    assertThrows(IllegalArgumentException::class.java) { hfStore.add(modelScope) }
    assertEquals(listOf(huggingFace), DiscoveredModels(directory).read())
    assertEquals(listOf(modelScope), DiscoveredModels(directory, DiscoveredProvider.MODELSCOPE).read())
    val allowed = modelScope.toAllowedModel()
    assertNotEquals(huggingFace.toAllowedModel().name, allowed.name)
    assertTrue(allowed.url!!.startsWith("$MODELSCOPE_BASE_URL/models/"))
    assertTrue(allowed.modelFile.startsWith("ms-"))
    assertEquals("$MODELSCOPE_BASE_URL/models/author/model", allowed.toModel(soc = "test-soc").learnMoreUrl)
    assertEquals(setOf(huggingFace.toAllowedModel().name, allowed.name), ModelCatalogMerger(directory).load().map { it.name }.toSet())
    assertTrue(msStore.remove(allowed.name))
    assertEquals(listOf(huggingFace), hfStore.read())
  }

  @Test fun corruptModelScopeCatalogDoesNotHideHuggingFaceModels() {
    val directory = temporary.newFolder()
    val huggingFace = model()
    DiscoveredModels(directory).add(huggingFace)
    File(directory, "modelscope_models.json").writeText("broken")
    val failures = mutableListOf<String>()
    val result = ModelCatalogMerger(directory, onError = { source, _ -> failures.add(source) }).load()
    assertEquals(listOf(huggingFace.toAllowedModel().name), result.map { it.name })
    assertEquals(listOf("modelscope-models"), failures)
  }

  @Test fun selectedFilesSurviveRestartAndHaveDistinctIdentity() {
    val directory = temporary.newFolder()
    val store = DiscoveredModels(directory)
    val first = model()
    val second = model("other/model file.litertlm")
    assertTrue(store.add(first))
    assertTrue(store.add(second))
    assertFalse(store.add(first.copy(revision = "b".repeat(40))))

    assertEquals(listOf(first, second), DiscoveredModels(directory).read())
    val firstAllowed = first.toAllowedModel()
    val secondAllowed = second.toAllowedModel()
    assertNotEquals(firstAllowed.name, secondAllowed.name)
    assertNotEquals(firstAllowed.modelFile, secondAllowed.modelFile)
    assertTrue(firstAllowed.url!!.contains("/nested/model%20file.litertlm"))
    assertEquals("cpu,gpu", firstAllowed.defaultConfig.accelerators)
    assertEquals(true, firstAllowed.llmSupportImage)
  }

  @Test fun selectedSettingsSurviveStorageAndReachCatalog() {
    val directory = temporary.newFolder()
    val entry = model().copy(displayName = "My model", maxTokens = 2048, topK = 42, topP = 0.8f,
      temperature = 0.6f, speculativeDecoding = true, accelerators = listOf("npu"))
    DiscoveredModels(directory).add(entry)
    val restored = DiscoveredModels(directory).read().single()
    val allowed = restored.toAllowedModel()
    assertEquals(entry, restored)
    assertEquals("My model", allowed.displayName)
    assertEquals(2048, allowed.defaultConfig.maxTokens)
    assertEquals(2048, allowed.defaultConfig.maxContextLength)
    assertEquals(42, allowed.defaultConfig.topK)
    assertEquals(0.8f, allowed.defaultConfig.topP!!, 0f)
    assertEquals(0.6f, allowed.defaultConfig.temperature!!, 0f)
    assertEquals(true, allowed.llmSupportSpeculativeDecoding)
    assertEquals("npu", allowed.defaultConfig.accelerators)
    val built = allowed.toModel(soc = "test-soc")
    assertEquals("My model", built.displayName)
    assertEquals(2048, built.llmMaxToken)
    assertTrue(built.capabilities.contains(com.ollitert.llm.server.data.model.ModelCapability.SPECULATIVE_DECODING))
    assertFalse(built.configs.any { it.key == com.ollitert.llm.server.data.prefs.ConfigKeys.TOPK })
  }

  @Test fun catalogMergerKeepsLocalFilesEvenWhenOfficialRepositoryHasSameId() {
    val directory = temporary.newFolder()
    val first = model()
    val second = model("second.litertlm")
    val store = DiscoveredModels(directory)
    store.add(first)
    store.add(second)
    val bundled = """{"models":[{"name":"Official","modelId":"author/model","modelFile":"official.litertlm","description":"","sizeInBytes":1,"defaultConfig":{}}]}"""
    val merger = ModelCatalogMerger(directory, assetReader = { bundled })
    assertEquals(setOf("Official", first.toAllowedModel().name, second.toAllowedModel().name), merger.load().map { it.name }.toSet())

    assertTrue(store.remove(first.toAllowedModel().name))
    assertFalse(store.remove(first.toAllowedModel().name))
    assertEquals(setOf("Official", second.toAllowedModel().name), merger.load().map { it.name }.toSet())
    val readded = model()
    assertTrue(store.add(readded))
    assertNotEquals(first.toAllowedModel().modelFile, readded.toAllowedModel().modelFile)
  }

  @Test fun invalidOrCorruptRecordsAreNeverOverwritten() {
    val directory = temporary.newFolder()
    val store = DiscoveredModels(directory)
    assertThrows(IllegalArgumentException::class.java) { store.add(model("../escape.litertlm")) }
    assertThrows(IllegalArgumentException::class.java) { store.add(model().copy(accelerators = emptyList())) }
    val catalog = File(directory, "huggingface_models.json")
    catalog.writeText("invalid")
    assertThrows(Exception::class.java) { store.add(model()) }
    assertEquals("invalid", catalog.readText())
  }

  @Test fun mergerRetainsLastValidLocalCatalogOnReadFailure() {
    val directory = temporary.newFolder()
    val entry = model()
    DiscoveredModels(directory).add(entry)
    val errors = mutableListOf<String>()
    val merger = ModelCatalogMerger(directory, onError = { source, _ -> errors.add(source) })
    assertEquals(listOf(entry.toAllowedModel().name), merger.load().map { it.name })
    File(directory, "huggingface_models.json").writeText("broken")
    assertEquals(listOf(entry.toAllowedModel().name), merger.load().map { it.name })
    assertEquals(listOf("huggingface-models"), errors)
  }
}
