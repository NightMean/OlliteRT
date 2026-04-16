package com.ollitert.llm.server.service

import android.content.Context
import com.ollitert.llm.server.data.Accelerator
import com.ollitert.llm.server.data.AllowedModel
import com.ollitert.llm.server.data.IMPORTS_DIR
import com.ollitert.llm.server.data.MIN_MAX_TOKENS
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RuntimeType
import com.ollitert.llm.server.data.convertValueToTargetType
import com.ollitert.llm.server.data.createLlmChatConfigs
import com.ollitert.llm.server.proto.ImportedModel
import java.io.File

/**
 * Single source of truth for building [Model] instances from different data sources.
 * Both the service layer (LlmHttpModelLifecycle) and the UI layer (ModelManagerViewModel)
 * should use these methods to ensure imported and allowlist models are constructed identically.
 */
object LlmHttpModelFactory {
  fun buildAllowedModel(allowedModel: AllowedModel, importsDir: File): Model {
    val base = allowedModel.toModel()
    val imported = File(importsDir, "${allowedModel.name}.litertlm")
    return withImportOverride(base, imported)
  }

  /**
   * Builds a [Model] from an [ImportedModel] proto entry (stored in DataStore).
   * Called by both the service (pickModelByName) and the UI (ModelManagerViewModel)
   * so that imported models are constructed identically in both layers.
   */
  fun buildImportedModel(info: ImportedModel): Model {
    val accelerators = info.llmConfig.compatibleAcceleratorsList.mapNotNull { label ->
      when (label.trim()) {
        Accelerator.GPU.label -> Accelerator.GPU
        Accelerator.CPU.label -> Accelerator.CPU
        Accelerator.NPU.label -> Accelerator.NPU
        else -> null
      }
    }.toMutableList()

    // Use the user-configured max tokens as both the default and the context window upper bound,
    // so the inference settings sheet shows a slider the user can adjust up to their chosen limit.
    // Only pass defaultMaxContextLength when the value exceeds the slider minimum used in
    // createLlmChatConfigs — otherwise the slider range would be inverted (min > max).
    val maxTokens = info.llmConfig.defaultMaxTokens
    val contextLength = if (maxTokens > MIN_MAX_TOKENS) maxTokens else null
    val configs = createLlmChatConfigs(
      defaultMaxToken = maxTokens,
      defaultMaxContextLength = contextLength,
      defaultTopK = info.llmConfig.defaultTopk,
      defaultTopP = info.llmConfig.defaultTopp,
      defaultTemperature = info.llmConfig.defaultTemperature,
      accelerators = accelerators,
      supportThinking = info.llmConfig.supportThinking,
    ).toMutableList()

    val model = Model(
      name = info.fileName,
      url = "",
      configs = configs,
      sizeInBytes = info.fileSize,
      downloadFileName = "$IMPORTS_DIR/${info.fileName}",
      showBenchmarkButton = false,
      imported = true,
      llmSupportImage = info.llmConfig.supportImage,
      llmSupportAudio = info.llmConfig.supportAudio,
      llmSupportThinking = info.llmConfig.supportThinking,
      llmMaxToken = info.llmConfig.defaultMaxTokens,
      accelerators = accelerators,
      // All imported models are assumed to be LLM for now.
      isLlm = true,
      runtimeType = RuntimeType.LITERT_LM,
      // Estimate minimum device RAM from file size since imported models have no allowlist
      // metadata. LiteRT needs substantial headroom for the Android OS, app, GPU buffers,
      // and scratch space on top of the raw model weights. Thresholds derived from the
      // allowlist: 0.58GB→6GB, 1.6GB→6GB, 2.6GB→8GB, 3.7GB→12GB, 4.9GB→12GB.
      minDeviceMemoryInGb = estimateMinMemoryGb(info.fileSize),
    )
    model.preProcess()
    return model
  }

  /**
   * Restores persisted inference config (temperature, topK, etc.) from SharedPreferences
   * onto a model. Called after building a model to apply user-customized settings that
   * survive app/service restarts.
   */
  fun restoreInferenceConfig(context: Context, model: Model) {
    val savedConfig = LlmHttpPrefs.getInferenceConfig(context, model.name) ?: return
    val restored = model.configValues.toMutableMap()
    for ((key, savedValue) in savedConfig) {
      if (key in restored) {
        val config = model.configs.find { it.key.label == key }
        if (config != null) {
          restored[key] = convertValueToTargetType(savedValue, config.valueType)
        } else {
          restored[key] = savedValue
        }
      }
    }
    model.configValues = restored
  }

  /**
   * Estimates the minimum device RAM (in GB) needed to run an imported model based on its
   * file size. Imported models lack allowlist metadata, so this heuristic provides a
   * reasonable memory warning threshold. Derived from the allowlist's known file-size-to-RAM
   * ratios: small models (< 1 GB) still need ~6 GB for Android + GPU overhead; larger models
   * scale up because LiteRT allocates GPU buffers and scratch space proportional to model size.
   */
  private fun estimateMinMemoryGb(fileSizeBytes: Long): Int {
    val fileSizeGb = fileSizeBytes / (1024.0 * 1024 * 1024)
    return when {
      fileSizeGb < 1.0 -> 6
      fileSizeGb < 3.0 -> 8
      fileSizeGb < 5.0 -> 12
      else -> 16
    }
  }

  private fun withImportOverride(base: Model, importedFile: File): Model {
    return if (importedFile.exists()) {
      base.copy(localModelFilePathOverride = importedFile.absolutePath)
    } else {
      base
    }
  }
}
