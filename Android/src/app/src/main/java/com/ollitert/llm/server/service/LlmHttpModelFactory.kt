package com.ollitert.llm.server.service

import com.ollitert.llm.server.data.AllowedModel
import com.ollitert.llm.server.data.Model
import java.io.File

object LlmHttpModelFactory {
  fun buildAllowedModel(allowedModel: AllowedModel, importsDir: File): Model {
    val base = allowedModel.toModel()
    val imported = File(importsDir, "${allowedModel.name}.litertlm")
    return withImportOverride(base, imported)
  }

  private fun withImportOverride(base: Model, importedFile: File): Model {
    return if (importedFile.exists()) {
      base.copy(localModelFilePathOverride = importedFile.absolutePath)
    } else {
      base
    }
  }
}
