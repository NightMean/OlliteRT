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

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpAllowlistLoaderTest {

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
      val loader = LlmHttpAllowlistLoader(externalFilesDir = dir, packageName = "test.pkg")
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
      val loader = LlmHttpAllowlistLoader(externalFilesDir = dir, packageName = "test.pkg")
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
      val loader = LlmHttpAllowlistLoader(
        externalFilesDir = dir,
        packageName = "test.pkg",
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
      val loader = LlmHttpAllowlistLoader(
        externalFilesDir = dir,
        packageName = "test.pkg",
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
      val loader = LlmHttpAllowlistLoader(
        externalFilesDir = dir,
        packageName = "test.pkg",
        assetReader = { newerBundled },
      )
      val result = loader.load()
      assertEquals(1, result.size)
      assertEquals("NewModel", result.first().name)
      assertEquals("asset", loader.lastSource)
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
      val loader = LlmHttpAllowlistLoader(
        externalFilesDir = dir,
        packageName = "test.pkg",
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

      val loader = LlmHttpAllowlistLoader(externalFilesDir = dir, packageName = "test.pkg")
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
      val loader = LlmHttpAllowlistLoader(externalFilesDir = dir, packageName = "test.pkg")
      val result = loader.load()
      assertTrue("malformed JSON should yield empty list", result.isEmpty())
      assertEquals("error", loader.lastSource)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun handlesNullExternalFilesDirGracefully() {
    val loader = LlmHttpAllowlistLoader(
      externalFilesDir = null,
      packageName = "test.pkg",
      assetReader = { minimalAllowlistJson },
    )
    val result = loader.load()
    // Falls through to asset reader
    assertEquals(1, result.size)
  }
}
