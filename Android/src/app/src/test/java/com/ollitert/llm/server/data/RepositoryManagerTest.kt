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

package com.ollitert.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryManagerTest {

  @Test
  fun deduplicatesByModelIdFirstRepoWins() {
    val officialModels = listOf(makeAllowedModel("ModelA", modelId = "community/modelA"))
    val thirdPartyModels = listOf(makeAllowedModel("ModelA-Custom", modelId = "community/modelA"))
    val deduped = RepositoryManager.deduplicateAllowedModels(
      repoModels = listOf(officialModels, thirdPartyModels),
      repoNames = listOf("Official", "Community"),
    )
    assertEquals(1, deduped.size)
    assertEquals("Official", deduped[0].second)
  }

  @Test
  fun differentModelIdsArePreserved() {
    val officialModels = listOf(makeAllowedModel("Gemma3", modelId = "community/gemma3"))
    val thirdPartyModels = listOf(makeAllowedModel("CustomLLM", modelId = "user/custom"))
    val deduped = RepositoryManager.deduplicateAllowedModels(
      repoModels = listOf(officialModels, thirdPartyModels),
      repoNames = listOf("Official", "Community"),
    )
    assertEquals(2, deduped.size)
  }

  @Test
  fun displayNameCollisionAppendsRepoNameToAllDuplicates() {
    val models = listOf(
      Model(name = "Gemma3", sourceRepository = "Official"),
      Model(name = "Gemma3", sourceRepository = "MyRepo"),
    )
    val result = RepositoryManager.disambiguateDisplayNames(models)
    assertEquals(2, result.size)
    assertEquals("Gemma3", result[0].name)
    assertEquals("Gemma3", result[1].name)
    assertEquals("Gemma3 (Official)", result[0].displayName)
    assertEquals("Gemma3 (MyRepo)", result[1].displayName)
  }

  @Test
  fun noCollisionWhenNamesAreDifferent() {
    val models = listOf(
      Model(name = "Gemma3-1B", sourceRepository = "Official"),
      Model(name = "Gemma3-4B", sourceRepository = "Community"),
    )
    val result = RepositoryManager.disambiguateDisplayNames(models)
    assertEquals("Gemma3-1B", result[0].name)
    assertEquals("Gemma3-4B", result[1].name)
    assertEquals("", result[0].displayName)
    assertEquals("", result[1].displayName)
  }

  @Test
  fun disambiguateWithEmptySourceRepositoryUsesUnknownFallback() {
    val models = listOf(
      Model(name = "Gemma3", sourceRepository = "Official"),
      Model(name = "Gemma3", sourceRepository = ""),
    )
    val result = RepositoryManager.disambiguateDisplayNames(models)
    assertEquals("Gemma3 (Official)", result[0].displayName)
    assertEquals("Gemma3 (Unknown)", result[1].displayName)
  }

  @Test
  fun deduplicatesPropagatesRepoIds() {
    val officialModels = listOf(makeAllowedModel("Gemma3", modelId = "community/gemma3"))
    val thirdPartyModels = listOf(makeAllowedModel("CustomLLM", modelId = "user/custom"))
    val deduped = RepositoryManager.deduplicateAllowedModels(
      repoModels = listOf(officialModels, thirdPartyModels),
      repoNames = listOf("Official", "Community"),
      repoIds = listOf("official", "uuid-123"),
    )
    assertEquals(2, deduped.size)
    assertEquals("official", deduped[0].third)
    assertEquals("uuid-123", deduped[1].third)
  }

  @Test
  fun emptyRepoListReturnsEmptyMerge() {
    val deduped = RepositoryManager.deduplicateAllowedModels(
      repoModels = emptyList(),
      repoNames = emptyList(),
    )
    assertTrue(deduped.isEmpty())
  }

  @Test
  fun validateDownloadFileNameRejectsPathSeparators() {
    assertTrue(RepositoryManager.isValidDownloadFileName("model.litertlm"))
    assertTrue(RepositoryManager.isValidDownloadFileName("model-v2.litertlm"))
    assertFalse(RepositoryManager.isValidDownloadFileName(""))
    assertFalse(RepositoryManager.isValidDownloadFileName("."))
    assertFalse(RepositoryManager.isValidDownloadFileName(".."))
    assertFalse(RepositoryManager.isValidDownloadFileName("../evil.bin"))
    assertFalse(RepositoryManager.isValidDownloadFileName("path/to/file.bin"))
    assertFalse(RepositoryManager.isValidDownloadFileName("path\\to\\file.bin"))
  }

  @Test
  fun truncateMetadataCapsLongStrings() {
    val longName = "A".repeat(200)
    val longDesc = "B".repeat(1000)
    val longIcon = "C".repeat(5000)
    assertEquals(MAX_REPO_NAME_LENGTH, RepositoryManager.truncateMetadata(longName, MAX_REPO_NAME_LENGTH).length)
    assertEquals(MAX_REPO_DESCRIPTION_LENGTH, RepositoryManager.truncateMetadata(longDesc, MAX_REPO_DESCRIPTION_LENGTH).length)
    assertEquals(MAX_REPO_ICON_URL_LENGTH, RepositoryManager.truncateMetadata(longIcon, MAX_REPO_ICON_URL_LENGTH).length)
  }

  @Test
  fun truncateMetadataPreservesShortStrings() {
    assertEquals("short", RepositoryManager.truncateMetadata("short", MAX_REPO_NAME_LENGTH))
  }

  @Test
  fun disambiguateThreeWayCollisionLabelsAll() {
    val models = listOf(
      Model(name = "LLM", sourceRepository = "RepoA"),
      Model(name = "LLM", sourceRepository = "RepoB"),
      Model(name = "LLM", sourceRepository = "RepoC"),
    )
    val result = RepositoryManager.disambiguateDisplayNames(models)
    assertEquals("LLM (RepoA)", result[0].displayName)
    assertEquals("LLM (RepoB)", result[1].displayName)
    assertEquals("LLM (RepoC)", result[2].displayName)
  }

  @Test
  fun disambiguateSingleModelNoChange() {
    val models = listOf(Model(name = "Solo", sourceRepository = "Official"))
    val result = RepositoryManager.disambiguateDisplayNames(models)
    assertEquals("", result[0].displayName)
  }

  @Test
  fun deduplicateAcrossThreeReposFirstWins() {
    val repo1 = listOf(makeAllowedModel("M1", modelId = "shared/model"))
    val repo2 = listOf(makeAllowedModel("M2", modelId = "shared/model"), makeAllowedModel("M3", modelId = "unique/a"))
    val repo3 = listOf(makeAllowedModel("M4", modelId = "unique/b"))
    val deduped = RepositoryManager.deduplicateAllowedModels(
      repoModels = listOf(repo1, repo2, repo3),
      repoNames = listOf("R1", "R2", "R3"),
    )
    assertEquals(3, deduped.size)
    assertEquals("R1", deduped[0].second)
    assertEquals("R2", deduped[1].second)
    assertEquals("R3", deduped[2].second)
  }

  @Test
  fun repoCacheFilenameMatchesRepositoryProperty() {
    val repo = Repository(
      id = "test-id", url = "https://example.com", enabled = true,
      isBuiltIn = false, contentVersion = 1, lastRefreshMs = 0, lastError = "",
    )
    assertEquals("model_allowlist_test-id.json", repo.cacheFilename)
    assertEquals(repo.cacheFilename, repoCacheFilename("test-id"))
  }

  private fun makeAllowedModel(name: String, modelId: String = "test/$name"): AllowedModel =
    AllowedModel(
      name = name,
      modelId = modelId,
      modelFile = "$name.litertlm",
      description = "test",
      sizeInBytes = 100,
      defaultConfig = DefaultConfig(),
    )
}
