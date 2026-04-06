package com.ollite.llm.server.service

import com.ollite.llm.server.data.AllowedModel
import com.ollite.llm.server.data.Model
import java.io.File

object LlmHttpModelFactory {
  fun buildDefaultModel(
    allowlist: List<AllowedModel>,
    importsDir: File,
    preferredImportFileName: String = "gemma-3n-E2B-it-int4.litertlm",
  ): Model? {
    val base = LlmHttpModelResolver.pickDefaultAllowedModel(allowlist)?.toModel() ?: return null
    val imported = File(importsDir, preferredImportFileName)
    return withImportOverride(base, imported)
  }

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
