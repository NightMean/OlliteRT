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

import android.content.Context
import com.google.gson.annotations.SerializedName
import java.io.File

/** Capability that a model may support. */
enum class ModelCapability {
  VISION,
  AUDIO,
  THINKING,
}

data class ModelDataFile(
  val url: String,
  val downloadFileName: String,
  val sizeInBytes: Long,
)

const val IMPORTS_DIR = "__imports"
private val NORMALIZE_NAME_REGEX = Regex("[^a-zA-Z0-9]")

enum class RuntimeType {
  @SerializedName("unknown") UNKNOWN,
  @SerializedName("litert_lm") LITERT_LM,
}

/** A model available for serving. */
data class Model(
  /**
   * The name of the model.
   *
   * This field is used to uniquely identify this model among all available models.
   *
   * IMPORTANT: it shouldn't contain "/" character.
   */
  val name: String,

  /**
   * The display name of the model, for display purpose.
   *
   * If this field is not set, the `name` field above will be used as the default display name.
   */
  val displayName: String = "",

  /**
   * (optional)
   *
   * A description or information about the model (Markdown supported).
   *
   * Displayed in the expanded model info card.
   */
  val info: String = "",

  /**
   * (optional)
   *
   * A list of configurable parameters for the model.
   *
   * If set, a gear icon appears on the right side of the model main screen's app bar. When
   * selected, a dialog pops up, allowing users to update the model's configurations.
   *
   * See [Config] for more details
   */
  var configs: List<Config> = listOf(),

  /**
   * (optional)
   *
   * The url to jump to when clicking "learn more" in model's info card.
   */
  val learnMoreUrl: String = "",

  /**
   * (optional)
   *
   * The minimum device memory in GB to run the model.
   *
   * If set, a warning dialog will be shown when user trying to download the model or enter the
   * model screen.
   */
  val minDeviceMemoryInGb: Int? = null,

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Fill in the following fields if the model file needs to be downloaded from internet.
  //
  // If you want to manually manage model files without downloading them from internet, set the
  // `localFilePathOverride` field below.

  /**
   * The URL to download the model from.
   *
   * If the url is from HuggingFace, we will automatically prompt users to fetch access token if the
   * model is gated.
   */
  val url: String = "",

  /**
   * The size of the model file in bytes.
   *
   * This will be used to calculate download progress.
   */
  val sizeInBytes: Long = 0L,

  /**
   * The name of the downloaded model file.
   *
   * It will be used to define the file path on local device to store the downloaded model.
   * {context.getExternalFilesDir}/{normalizedName}/{version}/{downloadFileName}
   */
  val downloadFileName: String = "_",

  /**
   * (optional)
   *
   * The version of the model.
   *
   * It will be used to define the file path on local device to store the downloaded model.
   * {context.getExternalFilesDir}/{normalizedName}/{version}/{downloadFileName}
   */
  val version: String = "_",

  /**
   * (optional, experimental)
   *
   * A list of additional data files required by the model.
   */
  val extraDataFiles: List<ModelDataFile> = listOf(),

  /** Whether the model is LLM or not. */
  val isLlm: Boolean = false,

  // End of model download related fields.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /** The type of local runtime environment to use for running the model. */
  val runtimeType: RuntimeType = RuntimeType.UNKNOWN,

  /**
   * Set this to a relative path pointing to a dir (e.g., my_model/local_dir/) if you want to
   * manually manage model files instead of downloading them. This dir is relative to the app's
   * "External Files Directory", which is: /storage/emulated/0/Android/data/<app_id>/files/.
   *
   * The <app_id> depends on how the app was built:
   * - `com.ollitert.llm.server` for builds from the GitHub source.
   * - `com.ollitert.llm.server` for other builds (Play store, internal, etc).
   *
   * For example, if this field is set to "my_model/local_dir/", then the location you should push
   * files to is (assuming non-github builds):
   *
   * /storage/emulated/0/Android/data/com.ollitert.llm.server/files/my_model/local_dir/
   *
   * You can get the full path to a specific file within your code using `Model.getPath(Context,
   * fileNameToGet)`.
   *
   * Using this field is recommended when:
   * - Your model files are not publicly accessible on the internet (e.g. private models).
   * - Your "model" or experience requires multiple files. Manually pushing these files to the
   *   device and using Model.getPath() for each one is often simpler than downloading them,
   *   especially for demos.
   */
  val localFileRelativeDirPathOverride: String = "",

  /**
   * When set, the app will try to use this path to find the model file.
   *
   * For testing purpose only.
   */
  val localModelFilePathOverride: String = "",

  // The following fields are only used for built-in tasks. Can ignore if you are creating your own
  // custom tasks.
  //

  /** Indicates whether the model is a zip file. */
  val isZip: Boolean = false,

  /** The name of the directory to unzip the model to (if it's a zip file). */
  val unzipDir: String = "",

  /** Capabilities supported by this model (vision, audio, thinking). */
  val capabilities: Set<ModelCapability> = emptySet(),

  /** The max token for llm model. */
  val llmMaxToken: Int = 0,

  /** Compatible accelerators. */
  val accelerators: List<Accelerator> = listOf(),

  /** Accelerator for running vision encoder. */
  val visionAccelerator: Accelerator = Accelerator.GPU,

  /** Badge from the model allowlist (e.g. "Best overall", "New"). */
  val badge: ModelBadge? = null,

  /** Whether this model should be pinned to the top of the model list. */
  val pinned: Boolean = false,

  /** Non-null when the model is incompatible with this app version (e.g. "Requires app version 0.9.0"). */
  val incompatibilityReason: String? = null,

  /** Whether the model is imported or not. */
  val imported: Boolean = false,

  // The following fields are managed by the app. Don't need to set manually.
  //
  var normalizedName: String = "",
  @Volatile var instance: Any? = null,
  @Volatile var initializedWithVision: Boolean = false,
  @Volatile var configValues: Map<String, Any> = mapOf(),
  var prevConfigValues: Map<String, Any> = mapOf(),
  var totalBytes: Long = 0L,
  var accessToken: String? = null,
) {
  init {
    normalizedName = NORMALIZE_NAME_REGEX.replace(name, "_")
  }

  fun preProcess() {
    val configValues: MutableMap<String, Any> = mutableMapOf()
    for (config in this.configs) {
      // Normalize to the target type so e.g. Float 4000.0 becomes Int 4000 for INT configs.
      // This prevents phantom "changed" detection when comparing with values from the UI.
      configValues[config.key.label] = convertValueToTargetType(config.defaultValue, config.valueType)
    }
    this.configValues = configValues.toMap()
    this.totalBytes = this.sizeInBytes + this.extraDataFiles.sumOf { it.sizeInBytes }
  }

  fun getPath(context: Context, fileName: String = downloadFileName): String {
    val externalDir = context.getExternalFilesDir(null)?.absolutePath
      ?: throw IllegalStateException("External storage unavailable — cannot access model files")

    if (imported) {
      return listOf(externalDir, fileName)
        .joinToString(File.separator)
    }

    if (localModelFilePathOverride.isNotEmpty()) {
      return localModelFilePathOverride
    }

    if (localFileRelativeDirPathOverride.isNotEmpty()) {
      return listOf(externalDir, localFileRelativeDirPathOverride, fileName)
        .joinToString(File.separator)
    }

    val baseDir =
      listOf(externalDir, normalizedName, version)
        .joinToString(File.separator)
    return if (this.isZip && this.unzipDir.isNotEmpty()) {
      listOf(baseDir, this.unzipDir).joinToString(File.separator)
    } else {
      listOf(baseDir, fileName).joinToString(File.separator)
    }
  }

  fun getIntConfigValue(key: ConfigKey, defaultValue: Int = 0): Int {
    return getTypedConfigValue(key = key, valueType = ValueType.INT, defaultValue = defaultValue)
      as Int
  }

  fun getFloatConfigValue(key: ConfigKey, defaultValue: Float = 0.0f): Float {
    return getTypedConfigValue(key = key, valueType = ValueType.FLOAT, defaultValue = defaultValue)
      as Float
  }

  fun getStringConfigValue(key: ConfigKey, defaultValue: String = ""): String {
    return getTypedConfigValue(key = key, valueType = ValueType.STRING, defaultValue = defaultValue)
      as String
  }

  private fun getTypedConfigValue(key: ConfigKey, valueType: ValueType, defaultValue: Any): Any {
    return convertValueToTargetType(
      value = configValues.getOrDefault(key.label, defaultValue),
      valueType = valueType,
    )
  }
}

val Model.llmSupportImage: Boolean get() = ModelCapability.VISION in capabilities
val Model.llmSupportAudio: Boolean get() = ModelCapability.AUDIO in capabilities
val Model.llmSupportThinking: Boolean get() = ModelCapability.THINKING in capabilities

enum class ModelDownloadStatusType {
  NOT_DOWNLOADED,
  PARTIALLY_DOWNLOADED,
  IN_PROGRESS,
  UNZIPPING,
  SUCCEEDED,
  FAILED,
}

data class ModelDownloadStatus(
  val status: ModelDownloadStatusType,
  val totalBytes: Long = 0,
  val receivedBytes: Long = 0,
  val errorMessage: String = "",
  val bytesPerSecond: Long = 0,
)

////////////////////////////////////////////////////////////////////////////////////////////////////
// Configs.

val EMPTY_MODEL: Model =
  Model(name = "empty", downloadFileName = "empty.tflite", url = "", sizeInBytes = 0L)
