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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ModelFactoryTest {
  @Test
  fun buildAllowedModelPrefersNamedImportOverrideWhenPresent() {
    val importsDir = createTempDirectory(prefix = "imports-dir").toFile()
    try {
      File(importsDir, "Gemma3-1B-IT.litertlm").writeText("stub")

      val model =
        ModelFactory.buildAllowedModel(
          allowedModel = allowedModel(name = "Gemma3-1B-IT"),
          importsDir = importsDir,
        )

      assertEquals(
        File(importsDir, "Gemma3-1B-IT.litertlm").absolutePath,
        model.localModelFilePathOverride,
      )
    } finally {
      importsDir.deleteRecursively()
    }
  }

  private fun allowedModel(name: String): AllowedModel {
    return AllowedModel(
      name = name,
      modelId = "google/$name",
      modelFile = "$name.litertlm",
      description = "test model",
      sizeInBytes = 1L,
      defaultConfig = DefaultConfig(),
    )
  }

  @Test
  fun partialSocEntryFallsBackToBaseFileMetadata() {
    // A SoC entry that only pins the commitHash must not produce sentinel
    // values ("-", negative sizes, null-interpolated URLs) for the rest.
    val model =
      allowedModel(name = "Gemma3-1B-IT").copy(
        commitHash = "basehash",
        socToModelFiles = mapOf(
          "test-soc" to SocModelFile(modelFile = null, url = null, commitHash = "sochash", sizeInBytes = null),
        ),
      ).toModel(soc = "test-soc")

    assertEquals("sochash", model.version)
    assertEquals("Gemma3-1B-IT.litertlm", model.downloadFileName)
    assertEquals(
      "https://huggingface.co/google/Gemma3-1B-IT/resolve/sochash/Gemma3-1B-IT.litertlm?download=true",
      model.url,
    )
    assertEquals(1L, model.sizeInBytes)
  }

  @Test
  fun fullSocEntryOverridesAllFileFields() {
    val model =
      allowedModel(name = "Gemma3-1B-IT").copy(
        commitHash = "basehash",
        socToModelFiles = mapOf(
          "test-soc" to SocModelFile(
            modelFile = "soc.litertlm",
            url = null,
            commitHash = "sochash",
            sizeInBytes = 99L,
          ),
        ),
      ).toModel(soc = "test-soc")

    assertEquals("sochash", model.version)
    assertEquals("soc.litertlm", model.downloadFileName)
    assertEquals(
      "https://huggingface.co/google/Gemma3-1B-IT/resolve/sochash/soc.litertlm?download=true",
      model.url,
    )
    assertEquals(99L, model.sizeInBytes)
  }
}
