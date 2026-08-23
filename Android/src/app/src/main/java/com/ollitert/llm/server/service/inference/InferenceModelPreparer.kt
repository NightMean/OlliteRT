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

package com.ollitert.llm.server.service.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.runtime.ServerLlmModelHelper

internal data class ConversationPreparation(
  val incrementalUserText: String?,
  val cacheGeneration: Long,
  val systemInstruction: Contents? = null,
)

/**
 * Handles model re-initialization and conversation preparation for vision, audio,
 * and multimodal inference requests.
 */
internal object InferenceModelPreparer {
  private const val TAG = "OlliteRT.Inference"

  /**
   * Re-initialize the model if needed (null instance or missing vision support).
   * Must be called inside synchronized(inferenceLock). Returns an error message on failure, or null on success.
   */
  fun reinitIfNeeded(
    context: Context,
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    buildSystemInstruction: (modelName: String) -> Contents?,
  ): String? {
    val needsReinit = model.instance == null ||
      (supportImage && !model.initializedWithVision)
    if (!needsReinit) return null

    if (model.instance != null) {
      Log.i(TAG, "Re-initializing model for vision/audio support")
      ServerLlmModelHelper.safeCleanup(model)
    }
    val initConfig = ServerPrefs.getInferenceConfig(context, model.prefsKey)
    var err = ""
    ServerLlmModelHelper.initialize(
      context = context,
      model = model,
      supportImage = supportImage,
      supportAudio = supportAudio,
      onDone = { err = it },
      systemInstruction = buildSystemInstruction(model.prefsKey),
      configOverrides = initConfig,
    )
    if (err.isNotEmpty()) {
      model.instance = null
      return err
    }
    model.initializedWithVision = supportImage
    return null
  }
}
