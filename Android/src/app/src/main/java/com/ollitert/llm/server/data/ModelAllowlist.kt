/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

import android.os.Build
import android.util.Log
import com.ollitert.llm.server.common.isPixel10
import com.ollitert.llm.server.common.SemVer
import com.google.gson.annotations.SerializedName

private const val TAG = "OlliteRTModelAllowlist"

data class DefaultConfig(
  @SerializedName("topK") val topK: Int? = null,
  @SerializedName("topP") val topP: Float? = null,
  @SerializedName("temperature") val temperature: Float? = null,
  @SerializedName("accelerators") val accelerators: String? = null,
  @SerializedName("visionAccelerator") val visionAccelerator: String? = null,
  @SerializedName("maxContextLength") val maxContextLength: Int? = null,
  @SerializedName("maxTokens") val maxTokens: Int? = null,
)

/** A model file on HF for a specific SOC. */
data class SocModelFile(
  @SerializedName("modelFile") val modelFile: String?,
  @SerializedName("url") val url: String?,
  @SerializedName("commitHash") val commitHash: String?,
  @SerializedName("sizeInBytes") val sizeInBytes: Long?,
)

/** A model in the model allowlist. */
data class AllowedModel(
  val name: String,
  val modelId: String,
  val modelFile: String,
  val commitHash: String = "",
  val description: String,
  val sizeInBytes: Long,
  val defaultConfig: DefaultConfig,
  val llmSupportImage: Boolean? = null,
  val llmSupportAudio: Boolean? = null,
  val llmSupportThinking: Boolean? = null,
  val minDeviceMemoryInGb: Int? = null,
  val localModelFilePathOverride: String? = null,
  val url: String? = null,
  val socToModelFiles: Map<String, SocModelFile>? = null,
  val runtimeType: RuntimeType? = null,
  val badge: String? = null,
  val pinned: Boolean? = null,
  val minAppVersion: String? = null,
  val maxAppVersion: String? = null,
) {
  fun isCompatibleWith(appVersion: SemVer): Boolean {
    val min = minAppVersion?.let { SemVer.parse(it) }
    val max = maxAppVersion?.let { SemVer.parse(it) }
    val effectiveMax = if (min != null && max != null && max < min) null else max
    if (min != null && appVersion < min) return false
    if (effectiveMax != null && appVersion > effectiveMax) return false
    return true
  }

  fun toModel(appVersion: SemVer? = null): Model {
    // Construct HF download url.
    var version = commitHash
    var downloadedFileName = modelFile
    var downloadUrl =
      url ?: "https://huggingface.co/$modelId/resolve/$commitHash/$modelFile?download=true"
    var sizeInBytes = sizeInBytes

    // Handle per-soc model files.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (socToModelFiles?.isNotEmpty() == true) {
        socToModelFiles.get(SOC)?.let { info ->
          Log.d(TAG, "Found soc-specific model files for model $name: $info")
          version = info.commitHash ?: "-"
          downloadedFileName = info.modelFile ?: "-"
          downloadUrl =
            info.url
              ?: "https://huggingface.co/$modelId/resolve/${info.commitHash}/${info.modelFile}?download=true"
          sizeInBytes = info.sizeInBytes ?: -1
        }
      }
    }

    // Config.
    val defaultTopK: Int = defaultConfig.topK ?: DEFAULT_TOPK
    val defaultTopP: Float = defaultConfig.topP ?: DEFAULT_TOPP
    val defaultTemperature: Float = defaultConfig.temperature ?: DEFAULT_TEMPERATURE
    val llmMaxToken = defaultConfig.maxTokens ?: DEFAULT_MAX_TOKEN
    val llmMaxContextLength = defaultConfig.maxContextLength
    var accelerators: List<Accelerator> = DEFAULT_ACCELERATORS
    var visionAccelerator: Accelerator = DEFAULT_VISION_ACCELERATOR
    if (defaultConfig.accelerators != null) {
      val items = defaultConfig.accelerators.split(",")
      accelerators = mutableListOf()
      for (item in items) {
        if (item == "cpu") {
          accelerators.add(Accelerator.CPU)
        } else if (item == "gpu") {
          accelerators.add(Accelerator.GPU)
        } else if (item == "npu") {
          accelerators.add(Accelerator.NPU)
        }
      }
      // Remove GPU from pixel 10 devices.
      if (isPixel10()) {
        accelerators.remove(Accelerator.GPU)
      }
    }
    if (defaultConfig.visionAccelerator != null) {
      val accelerator = defaultConfig.visionAccelerator
      if (accelerator == "cpu") {
        visionAccelerator = Accelerator.CPU
      } else if (accelerator == "gpu") {
        visionAccelerator = Accelerator.GPU
      } else if (accelerator == "npu") {
        visionAccelerator = Accelerator.NPU
      }
    }
    val npuOnly = accelerators.size == 1 && accelerators[0] == Accelerator.NPU
    val configs =
      (
        if (npuOnly) {
          createLlmChatConfigsForNpuModel(
            defaultMaxToken = llmMaxToken,
            accelerators = accelerators,
          )
        } else {
          createLlmChatConfigs(
            defaultTopK = defaultTopK,
            defaultTopP = defaultTopP,
            defaultTemperature = defaultTemperature,
            defaultMaxToken = llmMaxToken,
            defaultMaxContextLength = llmMaxContextLength,
            accelerators = accelerators,
            supportThinking = llmSupportThinking == true,
          )
        })
        .toMutableList()

    val incompatibilityReason = appVersion?.let { ver ->
      val minVer = minAppVersion?.let { SemVer.parse(it) }
      val maxVer = maxAppVersion?.let { SemVer.parse(it) }
      val effectiveMax = if (minVer != null && maxVer != null && maxVer < minVer) null else maxVer
      when {
        minVer != null && ver < minVer -> "Requires app version $minAppVersion"
        effectiveMax != null && ver > effectiveMax -> "Not available after version $maxAppVersion"
        else -> null
      }
    }

    return Model(
      name = name,
      version = version,
      info = description,
      url = downloadUrl,
      sizeInBytes = sizeInBytes,
      minDeviceMemoryInGb = minDeviceMemoryInGb,
      configs = configs,
      downloadFileName = downloadedFileName,
      showBenchmarkButton = false,
      learnMoreUrl = "https://huggingface.co/${modelId}",
      capabilities = buildSet {
        if (llmSupportImage == true) add(ModelCapability.VISION)
        if (llmSupportAudio == true) add(ModelCapability.AUDIO)
        if (llmSupportThinking == true) add(ModelCapability.THINKING)
      },
      llmMaxToken = llmMaxToken,
      accelerators = accelerators,
      visionAccelerator = visionAccelerator,
      badge = badge?.let { ModelBadge.fromKey(it) },
      pinned = pinned == true,
      incompatibilityReason = incompatibilityReason,
      localModelFilePathOverride = localModelFilePathOverride ?: "",
      isLlm = true,
      runtimeType = runtimeType ?: RuntimeType.LITERT_LM,
    )
  }

  override fun toString(): String {
    return "$modelId/$modelFile"
  }
}

/** The model allowlist. */
data class ModelAllowlist(
  val schemaVersion: Int = 1,
  val models: List<AllowedModel>,
) {
  companion object {
    const val SUPPORTED_SCHEMA_VERSION = 1
  }

  fun filterCompatible(appVersion: SemVer): ModelAllowlist {
    if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
      return ModelAllowlist(schemaVersion = schemaVersion, models = emptyList())
    }
    val compatible = models.filter { it.isCompatibleWith(appVersion) }
    return ModelAllowlist(schemaVersion = schemaVersion, models = compatible)
  }
}
