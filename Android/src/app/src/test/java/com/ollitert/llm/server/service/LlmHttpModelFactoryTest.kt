package com.ollitert.llm.server.service

import com.ollitert.llm.server.data.AllowedModel
import com.ollitert.llm.server.data.DefaultConfig
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

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
      taskTypes = listOf("llm_chat"),
    )
  }
}
