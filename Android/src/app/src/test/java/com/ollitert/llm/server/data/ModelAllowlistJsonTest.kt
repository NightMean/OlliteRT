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

import com.ollitert.llm.server.common.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAllowlistJsonTest {

  @Test
  fun decodesAllowlistWithKnownFields() {
    val json =
      """
      {
        "models": [
          {
            "name": "Gemma3-1B-IT",
            "modelId": "litert-community/Gemma3-1B-IT",
            "modelFile": "Gemma3-1B-IT.litertlm",
            "description": "test",
            "sizeInBytes": 123,
            "version": "main",
            "defaultConfig": {
              "topK": 40,
              "topP": 0.95,
              "temperature": 0.7,
              "accelerators": "gpu,cpu",
              "maxContextLength": 8192,
              "maxTokens": 2048
            },
            "llmSupportThinking": true
          }
        ]
      }
      """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)

    assertEquals(1, allowlist.models.size)
    assertEquals("Gemma3-1B-IT", allowlist.models.first().name)
    assertEquals(40, allowlist.models.first().defaultConfig.topK)
    assertTrue(allowlist.models.first().llmSupportThinking == true)
  }

  @Test
  fun ignoresUnknownKeys() {
    val json =
      """
      {
        "models": [
          {
            "name": "Gemma3-1B-IT",
            "modelId": "litert-community/Gemma3-1B-IT",
            "modelFile": "Gemma3-1B-IT.litertlm",
            "description": "test",
            "sizeInBytes": 123,
            "defaultConfig": {},
            "extraField": "ignored"
          }
        ],
        "topLevelExtra": true
      }
      """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)

    assertEquals(1, allowlist.models.size)
    assertEquals("Gemma3-1B-IT", allowlist.models.first().name)
  }

  @Test(expected = kotlinx.serialization.SerializationException::class)
  fun rejectsMalformedJson() {
    ModelAllowlistJson.decode("""{"models":[{"name":"broken"}""")
  }

  @Test
  fun decodesEmptyModelsList() {
    val json = """{"models":[]}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertTrue(allowlist.models.isEmpty())
  }

  @Test
  fun decodesModelWithMissingOptionalFields() {
    val json =
      """
      {
        "models": [
          {
            "name": "Minimal",
            "modelId": "test/minimal",
            "modelFile": "minimal.litertlm",
            "description": "test",
            "sizeInBytes": 100,
            "defaultConfig": {}
          }
        ]
      }
      """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)

    assertEquals(1, allowlist.models.size)
    val model = allowlist.models.first()
    assertEquals("Minimal", model.name)
    assertEquals(null, model.llmSupportThinking)
    assertEquals(null, model.llmSupportImage)
    assertEquals(null, model.llmSupportAudio)
    assertEquals(null, model.minDeviceMemoryInGb)
    assertEquals(null, model.defaultConfig.topK)
    assertEquals(null, model.defaultConfig.topP)
    assertEquals(null, model.defaultConfig.temperature)
    assertEquals(null, model.badge)
  }

  @Test(expected = kotlinx.serialization.SerializationException::class)
  fun rejectsModelWithMissingRequiredName() {
    val json = """
      {
        "models": [
          {
            "modelId": "test/no-name",
            "modelFile": "noname.litertlm",
            "description": "test",
            "sizeInBytes": 100,
            "defaultConfig": {}
          }
        ]
      }
    """.trimIndent()

    ModelAllowlistJson.decode(json)
  }

  @Test
  fun modelCapabilityExtensionsReflectCapabilitiesSet() {
    val allCaps = Model(name = "test", capabilities = setOf(ModelCapability.VISION, ModelCapability.AUDIO, ModelCapability.THINKING))
    assertTrue(allCaps.llmSupportImage)
    assertTrue(allCaps.llmSupportAudio)
    assertTrue(allCaps.llmSupportThinking)

    val visionOnly = Model(name = "test", capabilities = setOf(ModelCapability.VISION))
    assertTrue(visionOnly.llmSupportImage)
    assertFalse(visionOnly.llmSupportAudio)
    assertFalse(visionOnly.llmSupportThinking)

    val none = Model(name = "test")
    assertFalse(none.llmSupportImage)
    assertFalse(none.llmSupportAudio)
    assertFalse(none.llmSupportThinking)
  }

  @Test
  fun decodesBadgeField() {
    val json = """
      {
        "models": [
          {
            "name": "Gemma-4-E2B-it",
            "modelId": "litert-community/gemma-4-E2B-it-litert-lm",
            "modelFile": "gemma-4-E2B-it.litertlm",
            "description": "test",
            "sizeInBytes": 123,
            "defaultConfig": {},
            "badge": "best_overall",
            "llmSupportImage": true,
            "llmSupportAudio": true
          }
        ]
      }
    """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals("best_overall", allowlist.models.first().badge)
  }

  @Test
  fun badgeDefaultsToNull() {
    val json = """
      {
        "models": [
          {
            "name": "Minimal",
            "modelId": "test/minimal",
            "modelFile": "minimal.litertlm",
            "description": "test",
            "sizeInBytes": 100,
            "defaultConfig": {}
          }
        ]
      }
    """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals(null, allowlist.models.first().badge)
  }

  // --- schemaVersion / minAppVersion / maxAppVersion ---

  @Test
  fun decodesSchemaVersionWhenPresent() {
    val json = """
      {
        "schemaVersion": 1,
        "models": [
          {
            "name": "Test",
            "modelId": "test/test",
            "modelFile": "test.litertlm",
            "description": "test",
            "sizeInBytes": 100,
            "defaultConfig": {},
            "minAppVersion": "0.8.0",
            "maxAppVersion": "1.0.0"
          }
        ]
      }
    """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)

    assertEquals(1, allowlist.schemaVersion)
    assertEquals("0.8.0", allowlist.models.first().minAppVersion)
    assertEquals("1.0.0", allowlist.models.first().maxAppVersion)
  }

  @Test
  fun decodesSchemaVersionMissingDefaultsToOne() {
    val json = """{"models": []}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals(1, allowlist.schemaVersion)
  }

  @Test
  fun decodesMinMaxAppVersionMissingDefaultToNull() {
    val json = """
      {
        "schemaVersion": 1,
        "models": [
          {
            "name": "Old",
            "modelId": "test/old",
            "modelFile": "old.litertlm",
            "description": "test",
            "sizeInBytes": 100,
            "defaultConfig": {}
          }
        ]
      }
    """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals(null, allowlist.models.first().minAppVersion)
    assertEquals(null, allowlist.models.first().maxAppVersion)
  }

  // --- filterCompatible ---

  @Test
  fun filterCompatibleFiltersByMinAppVersion() {
    val models = listOf(
      makeFilterModel("Old", minAppVersion = "0.7.0"),
      makeFilterModel("Current", minAppVersion = "0.8.0"),
      makeFilterModel("Future", minAppVersion = "1.0.0"),
    )
    val allowlist = ModelAllowlist(schemaVersion = 1, models = models)

    val filtered = allowlist.filterCompatible(SemVer.parse("0.8.0")!!)

    assertEquals(2, filtered.models.size)
    assertEquals("Old", filtered.models[0].name)
    assertEquals("Current", filtered.models[1].name)
  }

  @Test
  fun filterCompatibleFiltersByMaxAppVersion() {
    val models = listOf(
      makeFilterModel("Deprecated", minAppVersion = "0.7.0", maxAppVersion = "0.7.9"),
      makeFilterModel("Current", minAppVersion = "0.7.0", maxAppVersion = "1.0.0"),
      makeFilterModel("NoMax", minAppVersion = "0.7.0"),
    )
    val allowlist = ModelAllowlist(schemaVersion = 1, models = models)

    val filtered = allowlist.filterCompatible(SemVer.parse("0.8.0")!!)

    assertEquals(2, filtered.models.size)
    assertEquals("Current", filtered.models[0].name)
    assertEquals("NoMax", filtered.models[1].name)
  }

  @Test
  fun filterCompatibleNoMinAppVersionCompatibleWithAll() {
    val models = listOf(makeFilterModel("Legacy"))
    val allowlist = ModelAllowlist(schemaVersion = 1, models = models)

    val filtered = allowlist.filterCompatible(SemVer.parse("99.0.0")!!)

    assertEquals(1, filtered.models.size)
  }

  @Test
  fun filterCompatibleInvalidRangeTreatedAsNoUpperBound() {
    val models = listOf(
      makeFilterModel("Typo", minAppVersion = "0.8.0", maxAppVersion = "0.7.0"),
    )
    val allowlist = ModelAllowlist(schemaVersion = 1, models = models)

    val filtered = allowlist.filterCompatible(SemVer.parse("0.9.0")!!)

    assertEquals(1, filtered.models.size)
  }

  @Test
  fun filterCompatibleIncludesModelAtExactMaxAppVersion() {
    val models = listOf(
      makeFilterModel("AtMax", minAppVersion = "0.7.0", maxAppVersion = "0.8.0"),
    )
    val allowlist = ModelAllowlist(schemaVersion = 1, models = models)

    val filtered = allowlist.filterCompatible(SemVer.parse("0.8.0")!!)

    assertEquals(1, filtered.models.size)
  }

  @Test
  fun filterCompatiblePreReleaseBuildMatchesOwnMinVersion() {
    val models = listOf(
      makeFilterModel("Model", minAppVersion = "0.9.0"),
    )
    val allowlist = ModelAllowlist(schemaVersion = 1, models = models)

    // Dev/beta builds of 0.9.0 should be compatible with minAppVersion=0.9.0
    val devBuild = allowlist.filterCompatible(SemVer.parse("0.9.0-dev.1")!!)
    assertEquals(1, devBuild.models.size)

    val betaBuild = allowlist.filterCompatible(SemVer.parse("0.9.0-beta.2")!!)
    assertEquals(1, betaBuild.models.size)

    // But a dev build of 0.8.0 should still be excluded
    val oldDev = allowlist.filterCompatible(SemVer.parse("0.8.0-dev.1")!!)
    assertEquals(0, oldDev.models.size)
  }

  @Test
  fun filterCompatibleUnsupportedSchemaReturnsEmpty() {
    val models = listOf(makeFilterModel("Model"))
    val allowlist = ModelAllowlist(schemaVersion = 99, models = models)

    val filtered = allowlist.filterCompatible(SemVer.parse("0.8.0")!!)

    assertEquals(0, filtered.models.size)
  }

  private fun makeFilterModel(
    name: String,
    minAppVersion: String? = null,
    maxAppVersion: String? = null,
  ) = AllowedModel(
    name = name,
    modelId = "test/$name",
    modelFile = "$name.litertlm",
    description = "test",
    sizeInBytes = 100,
    defaultConfig = DefaultConfig(),
    minAppVersion = minAppVersion,
    maxAppVersion = maxAppVersion,
  )

  @Test
  fun decodesPinnedField() {
    val json = """
      {
        "schemaVersion": 1,
        "models": [
          {
            "name": "Pinned",
            "modelId": "test/pinned",
            "modelFile": "pinned.litertlm",
            "description": "test",
            "sizeInBytes": 100,
            "defaultConfig": {},
            "pinned": true
          }
        ]
      }
    """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)
    assertTrue(allowlist.models.first().pinned == true)
  }

  @Test
  fun pinnedDefaultsToNullWhenOmitted() {
    val json = """
      {
        "schemaVersion": 1,
        "models": [
          {
            "name": "NotPinned",
            "modelId": "test/notpinned",
            "modelFile": "notpinned.litertlm",
            "description": "test",
            "sizeInBytes": 100,
            "defaultConfig": {}
          }
        ]
      }
    """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)
    assertTrue(allowlist.models.first().pinned == null)
  }

  @Test
  fun decodesContentVersionWhenPresent() {
    val json = """{"schemaVersion": 1, "contentVersion": 5, "models": []}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals(5, allowlist.contentVersion)
  }

  @Test
  fun decodesContentVersionMissingDefaultsToZero() {
    val json = """{"schemaVersion": 1, "models": []}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals(0, allowlist.contentVersion)
  }

  // --- sourceName ---

  @Test
  fun decodesSourceNameWhenPresent() {
    val json = """{"schemaVersion": 1, "sourceName": "Official", "models": []}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals("Official", allowlist.sourceName)
  }

  @Test
  fun decodesSourceNameMissingDefaultsToEmpty() {
    val json = """{"schemaVersion": 1, "models": []}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals("", allowlist.sourceName)
  }

  // --- sourceDescription ---

  @Test
  fun decodesSourceDescriptionWhenPresent() {
    val json = """{"schemaVersion": 1, "sourceDescription": "My models", "models": []}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals("My models", allowlist.sourceDescription)
  }

  @Test
  fun decodesSourceDescriptionMissingDefaultsToEmpty() {
    val json = """{"schemaVersion": 1, "models": []}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals("", allowlist.sourceDescription)
  }

  // --- sourceIconUrl ---

  @Test
  fun decodesSourceIconUrlWhenPresent() {
    val json = """{"schemaVersion": 1, "sourceIconUrl": "https://example.com/icon.png", "models": []}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals("https://example.com/icon.png", allowlist.sourceIconUrl)
  }

  @Test
  fun decodesSourceIconUrlMissingDefaultsToEmpty() {
    val json = """{"schemaVersion": 1, "models": []}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals("", allowlist.sourceIconUrl)
  }

  @Test
  fun filterCompatiblePreservesAllSourceFields() {
    val allowlist = ModelAllowlist(
      schemaVersion = 1,
      contentVersion = 3,
      sourceName = "Community",
      sourceDescription = "Community models",
      sourceIconUrl = "https://example.com/icon.png",
      models = listOf(makeFilterModel("A", minAppVersion = "1.0.0")),
    )
    val filtered = allowlist.filterCompatible(SemVer(1, 0, 0))
    assertEquals("Community", filtered.sourceName)
    assertEquals("Community models", filtered.sourceDescription)
    assertEquals("https://example.com/icon.png", filtered.sourceIconUrl)
  }

  @Test
  fun decodesJsonWithNoModelsKeyAsEmptyList() {
    val json = """{"schemaVersion": 1}"""
    val allowlist = ModelAllowlistJson.decode(json)
    assertEquals(1, allowlist.schemaVersion)
    assertTrue(allowlist.models.isEmpty())
  }

  @Test
  fun filterCompatiblePreservesContentVersion() {
    val allowlist = ModelAllowlist(
      schemaVersion = 1,
      contentVersion = 3,
      models = listOf(makeFilterModel("A", minAppVersion = "1.0.0")),
    )
    val filtered = allowlist.filterCompatible(SemVer(1, 0, 0))
    assertEquals(3, filtered.contentVersion)
  }
}
