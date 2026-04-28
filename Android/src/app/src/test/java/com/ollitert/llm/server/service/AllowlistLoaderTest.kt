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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class AllowlistLoaderTest {

  private val minimalAllowlistJson = """
    {
      "models": [
        {
          "name": "TestModel",
          "modelId": "test/TestModel",
          "modelFile": "test.litertlm",
          "description": "A test model",
          "sizeInBytes": 1000,
          "defaultConfig": {}
        }
      ]
    }
  """.trimIndent()

  @Test
  fun returnsEmptyListWhenNoFilesAndNoAssets() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      val loader = AllowlistLoader(externalFilesDir = dir)
      val result = loader.load()
      assertTrue("should return empty list when no allowlist file", result.isEmpty())
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun loadsModelsFromExternalFilesDir() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      File(dir, "model_allowlist.json").writeText(minimalAllowlistJson)
      val loader = AllowlistLoader(externalFilesDir = dir)
      val result = loader.load()
      assertEquals(1, result.size)
      assertEquals("TestModel", result.first().name)
      assertTrue("source should indicate external path", loader.lastSource.startsWith("external:"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun fallsBackToAssetReader() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      val loader = AllowlistLoader(
        externalFilesDir = dir,
        assetReader = { minimalAllowlistJson },
      )
      val result = loader.load()
      assertEquals(1, result.size)
      assertEquals("asset", loader.lastSource)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun externalFileDirTakesPrecedenceOverAssetsAtSameContentVersion() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      File(dir, "model_allowlist.json").writeText(minimalAllowlistJson)
      val loader = AllowlistLoader(
        externalFilesDir = dir,
        assetReader = { """{"models":[]}""" },
      )
      val result = loader.load()
      assertEquals(1, result.size)
      assertTrue(loader.lastSource.startsWith("external:"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun bundledAssetWinsOverStaleDiskCacheByContentVersion() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      val staleDiskCache = """{"contentVersion": 1, "models": [
        {"name": "OldModel", "modelId": "test/old", "modelFile": "old.litertlm",
         "description": "stale", "sizeInBytes": 100, "defaultConfig": {}}
      ]}"""
      File(dir, "model_allowlist.json").writeText(staleDiskCache)

      val newerBundled = """{"contentVersion": 2, "models": [
        {"name": "NewModel", "modelId": "test/new", "modelFile": "new.litertlm",
         "description": "fresh", "sizeInBytes": 200, "defaultConfig": {}}
      ]}"""
      val loader = AllowlistLoader(
        externalFilesDir = dir,
        assetReader = { newerBundled },
      )
      val result = loader.load()
      assertEquals(1, result.size)
      assertEquals("NewModel", result.first().name)
      // Multi-file loader always reports "external:" when disk files exist
      assertTrue(loader.lastSource.startsWith("external:"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun diskCacheWinsOverBundledWhenContentVersionIsHigher() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      val freshDiskCache = """{"contentVersion": 3, "models": [
        {"name": "CachedModel", "modelId": "test/cached", "modelFile": "cached.litertlm",
         "description": "fresh cache", "sizeInBytes": 100, "defaultConfig": {}}
      ]}"""
      File(dir, "model_allowlist.json").writeText(freshDiskCache)

      val olderBundled = """{"contentVersion": 1, "models": [
        {"name": "BundledModel", "modelId": "test/bundled", "modelFile": "bundled.litertlm",
         "description": "older", "sizeInBytes": 200, "defaultConfig": {}}
      ]}"""
      val loader = AllowlistLoader(
        externalFilesDir = dir,
        assetReader = { olderBundled },
      )
      val result = loader.load()
      assertEquals(1, result.size)
      assertEquals("CachedModel", result.first().name)
      assertTrue(loader.lastSource.startsWith("external:"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun cachesPreviousAllowlistWhenFileMissing() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      val allowlistFile = File(dir, "model_allowlist.json")
      allowlistFile.writeText(minimalAllowlistJson)

      val loader = AllowlistLoader(externalFilesDir = dir)
      val first = loader.load()
      assertEquals(1, first.size)

      // Remove file and reload — should return cached result
      allowlistFile.delete()
      val second = loader.load()
      assertEquals("should return cached models", 1, second.size)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun returnsEmptyListWhenAllowlistJsonIsMalformed() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      File(dir, "model_allowlist.json").writeText("not valid json {{{")
      val loader = AllowlistLoader(externalFilesDir = dir)
      val result = loader.load()
      assertTrue("malformed JSON should yield empty list", result.isEmpty())
      // Per-file error handler skips the file; lastSource is "external:" not "error"
      assertTrue(loader.lastSource.startsWith("external:"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun filtersModelsByAppVersionWhenProvided() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      val json = """
        {
          "schemaVersion": 1,
          "models": [
            {
              "name": "OldModel", "modelId": "test/old", "modelFile": "old.litertlm",
              "description": "works on 0.7+", "sizeInBytes": 100, "defaultConfig": {},
              "minAppVersion": "0.7.0"
            },
            {
              "name": "FutureModel", "modelId": "test/future", "modelFile": "future.litertlm",
              "description": "needs 2.0+", "sizeInBytes": 100, "defaultConfig": {},
              "minAppVersion": "2.0.0"
            }
          ]
        }
      """.trimIndent()
      File(dir, "model_allowlist.json").writeText(json)

      val loader = AllowlistLoader(
        externalFilesDir = dir,
        appVersionName = "0.8.0",
      )
      val result = loader.load()
      assertEquals("should filter out models requiring newer app version", 1, result.size)
      assertEquals("OldModel", result.first().name)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun skipsEmptyFileAndFallsBackToAsset() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      File(dir, "model_allowlist.json").writeText("")
      val loader = AllowlistLoader(
        externalFilesDir = dir,
        assetReader = { minimalAllowlistJson },
      )
      val result = loader.load()
      assertEquals(1, result.size)
      assertEquals("asset", loader.lastSource)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun lastContentVersionSetAfterLoad() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      val json = """{"contentVersion": 42, "models": [
        {"name": "TestModel", "modelId": "test/TestModel", "modelFile": "test.litertlm",
         "description": "A test model", "sizeInBytes": 1000, "defaultConfig": {}}
      ]}"""
      File(dir, "model_allowlist.json").writeText(json)
      val loader = AllowlistLoader(externalFilesDir = dir)
      assertEquals(0, loader.lastContentVersion)
      loader.load()
      assertEquals(42, loader.lastContentVersion)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun lastContentVersionZeroWhenNoModelsLoaded() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      val loader = AllowlistLoader(externalFilesDir = dir)
      loader.load()
      assertEquals(0, loader.lastContentVersion)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun lastContentVersionUsesHigherOfDiskAndAsset() {
    val dir = createTempDirectory("allowlist-test").toFile()
    try {
      val diskJson = """{"contentVersion": 5, "models": [
        {"name": "DiskModel", "modelId": "test/disk", "modelFile": "disk.litertlm",
         "description": "disk", "sizeInBytes": 100, "defaultConfig": {}}
      ]}"""
      File(dir, "model_allowlist.json").writeText(diskJson)

      val assetJson = """{"contentVersion": 10, "models": [
        {"name": "AssetModel", "modelId": "test/asset", "modelFile": "asset.litertlm",
         "description": "asset", "sizeInBytes": 200, "defaultConfig": {}}
      ]}"""
      val loader = AllowlistLoader(
        externalFilesDir = dir,
        assetReader = { assetJson },
      )
      loader.load()
      assertEquals(10, loader.lastContentVersion)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun handlesNullExternalFilesDirGracefully() {
    val loader = AllowlistLoader(
      externalFilesDir = null,
      assetReader = { minimalAllowlistJson },
    )
    val result = loader.load()
    // With externalFilesDir=null, should still fall back to bundled asset
    assertTrue(result.isNotEmpty())
  }

  @Test
  fun loadsAndMergesMultipleRepoFiles() {
    val dir = createTempDirectory("allowlist-multi").toFile()
    try {
      val official = """{"schemaVersion":1,"contentVersion":1,"sourceName":"Official","models":[{"name":"ModelA","modelId":"off/a","modelFile":"a.litertlm","description":"a","sizeInBytes":100,"defaultConfig":{}}]}"""
      val thirdParty = """{"schemaVersion":1,"contentVersion":1,"sourceName":"Community","models":[{"name":"ModelB","modelId":"com/b","modelFile":"b.litertlm","description":"b","sizeInBytes":200,"defaultConfig":{}}]}"""
      File(dir, "model_allowlist_official.json").writeText(official)
      File(dir, "model_allowlist_abc-123.json").writeText(thirdParty)

      val loader = AllowlistLoader(externalFilesDir = dir, appVersionName = "1.0.0")
      val models = loader.load()

      assertEquals(2, models.size)
      assertEquals("ModelA", models[0].name)
      assertEquals("ModelB", models[1].name)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun officialFileProcessedFirstRegardlessOfAlphabeticalOrder() {
    val dir = createTempDirectory("allowlist-order").toFile()
    try {
      // UUID starting with '0' sorts before 'official' alphabetically
      val thirdParty = """{"schemaVersion":1,"contentVersion":1,"sourceName":"Early","models":[{"name":"SharedModel","modelId":"shared/model","modelFile":"s.litertlm","description":"s","sizeInBytes":100,"defaultConfig":{}}]}"""
      val official = """{"schemaVersion":1,"contentVersion":1,"sourceName":"Official","models":[{"name":"SharedModel","modelId":"shared/model","modelFile":"s.litertlm","description":"s","sizeInBytes":100,"defaultConfig":{}}]}"""
      File(dir, "model_allowlist_0abc.json").writeText(thirdParty)
      File(dir, "model_allowlist_official.json").writeText(official)

      val loader = AllowlistLoader(externalFilesDir = dir, appVersionName = "1.0.0")
      val models = loader.load()

      // Official wins dedup — only 1 model, not 2
      assertEquals(1, models.size)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun migratesLegacyFilenameToOfficialOnFirstLoad() {
    val dir = createTempDirectory("allowlist-legacy").toFile()
    try {
      val legacy = """{"schemaVersion":1,"contentVersion":1,"models":[{"name":"Legacy","modelId":"l/m","modelFile":"l.litertlm","description":"l","sizeInBytes":100,"defaultConfig":{}}]}"""
      File(dir, "model_allowlist.json").writeText(legacy)

      val loader = AllowlistLoader(externalFilesDir = dir, appVersionName = "1.0.0")
      val models = loader.load()

      // Migration renames model_allowlist.json → model_allowlist_official.json
      assertEquals(1, models.size)
      assertEquals("Legacy", models[0].name)
      assertFalse(File(dir, "model_allowlist.json").exists())
      assertTrue(File(dir, "model_allowlist_official.json").exists())
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun onErrorCallbackFirsForMalformedFiles() {
    val dir = createTempDirectory("allowlist-error").toFile()
    try {
      File(dir, "model_allowlist_official.json").writeText("{ not valid json !!!")
      val errors = mutableListOf<Pair<String, Exception>>()
      val loader = AllowlistLoader(
        externalFilesDir = dir,
        onError = { source, ex -> errors.add(source to ex) },
      )
      val models = loader.load()
      assertTrue(models.isEmpty())
      assertEquals(1, errors.size)
      assertEquals("model_allowlist_official.json", errors[0].first)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun onErrorCallbackFirsForMalformedAsset() {
    val errors = mutableListOf<Pair<String, Exception>>()
    val loader = AllowlistLoader(
      externalFilesDir = null,
      assetReader = { "{ broken json" },
      onError = { source, ex -> errors.add(source to ex) },
    )
    val models = loader.load()
    assertTrue(models.isEmpty())
    assertEquals(1, errors.size)
    assertEquals("asset", errors[0].first)
  }
}
