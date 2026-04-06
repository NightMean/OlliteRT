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
  fun externalFileDirTakesPrecedenceOverAssets() {
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
