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

import com.ollitert.llm.server.data.AllowedModel
import com.ollitert.llm.server.data.DefaultConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LlmHttpModelFactoryTest {
  @Test
  fun buildAllowedModelPrefersNamedImportOverrideWhenPresent() {
    val importsDir = createTempDirectory(prefix = "imports-dir").toFile()
    try {
      File(importsDir, "Gemma3-1B-IT.litertlm").writeText("stub")

      val model =
        LlmHttpModelFactory.buildAllowedModel(
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
}
