/*
 * Copyright 2025 Google LLC
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

package com.ollitert.llm.server.runtime

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File
import com.ollitert.llm.server.common.cleanUpMediapipeTaskErrorMessage
import com.ollitert.llm.server.data.Accelerator
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.DEFAULT_MAX_TOKEN
import com.ollitert.llm.server.data.MIN_STORAGE_FOR_MODEL_INIT_BYTES
import com.ollitert.llm.server.data.bytesToMb
import com.ollitert.llm.server.data.DEFAULT_TEMPERATURE
import com.ollitert.llm.server.data.DEFAULT_TOPK
import com.ollitert.llm.server.data.DEFAULT_TOPP
import com.ollitert.llm.server.data.DEFAULT_VISION_ACCELERATOR
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.service.LlmHttpRequestAdapter
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope

private const val TAG = "ServerLlmModelHelper"

data class LlmModelInstance(val engine: Engine, var conversation: Conversation)

object ServerLlmModelHelper : LlmModelHelper {
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = java.util.concurrent.ConcurrentHashMap()

  @OptIn(ExperimentalApi::class)
  override fun initialize(
    context: Context,
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    coroutineScope: CoroutineScope?,
  ) {
    val maxTokens =
      model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature =
      model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val accelerator =
      model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    val visionAccelerator =
      model.getStringConfigValue(
        key = ConfigKeys.VISION_ACCELERATOR,
        defaultValue = DEFAULT_VISION_ACCELERATOR.label,
      )
    val visionBackend =
      when (visionAccelerator) {
        Accelerator.CPU.label -> Backend.CPU()
        Accelerator.GPU.label -> Backend.GPU()
        Accelerator.NPU.label ->
          Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.GPU()
      }
    val preferredBackend =
      when (accelerator) {
        Accelerator.CPU.label -> Backend.CPU()
        Accelerator.GPU.label -> Backend.GPU()
        Accelerator.NPU.label ->
          Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.CPU()
      }
    Log.d(TAG, "Preferred backend: $preferredBackend")

    val modelPath = model.getPath(context = context)

    // Pre-load validation: verify the model file exists and has a reasonable size
    // before passing it to the native Engine constructor, which can SIGABRT on
    // corrupt/truncated files — unrecoverable from Java.
    val modelFile = File(modelPath)
    if (!modelFile.exists()) {
      onDone("Model file not found: ${modelFile.name}")
      return
    }
    // Minimum size check — a valid .litertlm file is always > 1KB.
    // Truncated files (e.g. from interrupted downloads) trigger native abort().
    if (modelFile.length() < 1024) {
      onDone("Model file appears corrupted or truncated (${modelFile.length()} bytes): ${modelFile.name}")
      return
    }

    // Pre-flight storage check: LiteRT needs scratch space for memory-mapping, temp files,
    // and GPU buffer allocation during Engine initialization. If the device is critically
    // low on storage (e.g. after a failed import filled the disk), Engine() will fail with
    // a cryptic native "Failed to create engine: INTERNAL" error. Check early and provide
    // a clear, actionable error message instead.
    try {
      val stat = StatFs(Environment.getDataDirectory().path)
      val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
      if (availableBytes < MIN_STORAGE_FOR_MODEL_INIT_BYTES) {
        val availableMb = availableBytes.bytesToMb()
        val requiredMb = MIN_STORAGE_FOR_MODEL_INIT_BYTES.bytesToMb()
        onDone("Insufficient storage to load model (${availableMb}MB free, need at least ${requiredMb}MB). " +
          "Free up space by deleting unused models or files, then try again.")
        return
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to check storage before engine creation: ${e.message}")
      // Don't block model loading if StatFs fails — let the Engine attempt proceed
    }

    val engineConfig =
      EngineConfig(
        modelPath = modelPath,
        backend = preferredBackend,
        visionBackend = if (supportImage) visionBackend else null,
        audioBackend = if (supportAudio) Backend.CPU() else null,
        maxNumTokens = maxTokens,
        cacheDir =
          if (modelPath.startsWith("/data/local/tmp"))
            context.getExternalFilesDir(null)?.absolutePath
          else null,
      )

    try {
      val engine = Engine(engineConfig)
      engine.initialize()

      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      val conversation =
        engine.createConversation(
          ConversationConfig(
            samplerConfig =
              if (preferredBackend is Backend.NPU) {
                null
              } else {
                SamplerConfig(
                  topK = topK,
                  topP = topP.toDouble(),
                  temperature = temperature.toDouble(),
                )
              },
            systemInstruction = systemInstruction,
            tools = tools,
          )
        )
      ExperimentalFlags.enableConversationConstrainedDecoding = false
      model.instance = LlmModelInstance(engine = engine, conversation = conversation)
    } catch (e: Exception) {
      onDone(cleanUpMediapipeTaskErrorMessage(e.message ?: "Unknown error"))
      return
    }
    onDone("")
  }

  @OptIn(ExperimentalApi::class)
  override fun resetConversation(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
  ) {
    try {
      Log.d(TAG, "Resetting conversation for model '${model.name}'")

      val instance = model.instance as? LlmModelInstance ?: return

      // Close old conversation in an inner try-catch — if it fails (e.g. already destroyed
      // by another thread), we still proceed to create a new one. The old native memory
      // will be reclaimed when the Engine is eventually closed or GC finalizes the wrapper.
      try {
        instance.conversation.close()
      } catch (e: Exception) {
        Log.w(TAG, "Old conversation close failed (proceeding with new): ${e.message}")
      }

      val engine = instance.engine
      val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
      val temperature =
        model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)

      val accelerator =
        model.getStringConfigValue(
          key = ConfigKeys.ACCELERATOR,
          defaultValue = Accelerator.GPU.label,
        )
      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      val newConversation =
        engine.createConversation(
          ConversationConfig(
            samplerConfig =
              if (accelerator == Accelerator.NPU.label) {
                null
              } else {
                SamplerConfig(
                  topK = topK,
                  topP = topP.toDouble(),
                  temperature = temperature.toDouble(),
                )
              },
            systemInstruction = systemInstruction,
            tools = tools,
          )
        )
      ExperimentalFlags.enableConversationConstrainedDecoding = false
      instance.conversation = newConversation

      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to reset conversation completely", e)
      RequestLogStore.addEvent(
        "Failed to reset conversation: ${e.message?.take(80) ?: "Unknown error"}",
        level = LogLevel.ERROR,
        modelName = model.name,
      )
      // If new Conversation creation failed, the model is in a broken state —
      // null the instance so the next request triggers a full re-initialization
      // rather than using a model with a dead/missing Conversation.
      model.instance = null
      System.gc()
    }
  }

  /** Safe cleanup: close native resources with try-catch, null instance, hint GC. */
  fun safeCleanup(model: Model) {
    try {
      cleanUp(model) {}
    } catch (e: Exception) {
      Log.w(TAG, "Error during model cleanup: ${e.message}")
    }
    model.instance = null
    System.gc()
  }

  override fun cleanUp(model: Model, onDone: () -> Unit) {
    // Safe cast: model.instance is @Volatile and can be set to null by another thread
    // between the null check and the cast. Use as? to avoid NullPointerException.
    val instance = model.instance as? LlmModelInstance ?: return

    try {
      instance.conversation.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the conversation: ${e.message}")
    }

    try {
      instance.engine.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the engine: ${e.message}")
    }

    val onCleanUp = cleanUpListeners.remove(model.name)
    if (onCleanUp != null) {
      onCleanUp()
    }
    model.instance = null

    onDone()
    Log.d(TAG, "Clean up done.")
  }

  override fun stopResponse(model: Model) {
    val instance = model.instance as? LlmModelInstance ?: return
    // The Conversation may already be closed if the server is stopping while inference is
    // in progress (e.g. user taps Stop Server mid-generation). The SDK throws
    // IllegalStateException("Conversation is not alive") from cancelProcess() in that case.
    try {
      instance.conversation.cancelProcess()
    } catch (_: IllegalStateException) {
      Log.d(TAG, "stopResponse: conversation already closed, skipping cancel")
    }
  }

  override fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit,
    images: List<Bitmap>,
    audioClips: List<ByteArray>,
    coroutineScope: CoroutineScope?,
    extraContext: Map<String, String>?,
  ) {
    val instance = model.instance as? LlmModelInstance
    if (instance == null) {
      onError("LlmModelInstance is not initialized.")
      return
    }

    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    val conversation = instance.conversation

    val contents = mutableListOf<Content>()
    if (images.isNotEmpty() && input.contains(LlmHttpRequestAdapter.IMAGE_PLACEHOLDER)) {
      // Multi-image interleaving: the prompt contains placeholder tokens at the exact
      // positions where images appeared in the conversation. Split on placeholders and
      // interleave Content.Text / Content.ImageBytes so each image is associated with
      // its correct conversation turn.
      val segments = input.split(LlmHttpRequestAdapter.IMAGE_PLACEHOLDER)
      var imageIndex = 0
      for ((i, segment) in segments.withIndex()) {
        if (segment.trim().isNotEmpty()) {
          contents.add(Content.Text(segment.trim()))
        }
        // After each segment except the last, insert the corresponding image
        if (i < segments.size - 1 && imageIndex < images.size) {
          contents.add(Content.ImageBytes(images[imageIndex].toPngByteArray()))
          imageIndex++
        }
      }
      // Append any remaining images that had no placeholder (shouldn't happen, but safe)
      while (imageIndex < images.size) {
        contents.add(Content.ImageBytes(images[imageIndex].toPngByteArray()))
        imageIndex++
      }
    } else {
      // Single-image or non-chat path: images before text (matches reference app behavior)
      for (image in images) {
        contents.add(Content.ImageBytes(image.toPngByteArray()))
      }
      if (input.trim().isNotEmpty()) {
        contents.add(Content.Text(input))
      }
    }
    for (audioClip in audioClips) {
      contents.add(Content.AudioBytes(audioClip))
    }

    conversation.sendMessageAsync(
      Contents.of(contents),
      object : MessageCallback {
        override fun onMessage(message: Message) {
          resultListener(message.toString(), false, message.channels["thought"])
        }

        override fun onDone() {
          resultListener("", true, null)
        }

        override fun onError(throwable: Throwable) {
          if (throwable is CancellationException) {
            Log.i(TAG, "The inference is cancelled.")
            resultListener("", true, null)
          } else {
            Log.e(TAG, "onError", throwable)
            onError("Error: ${throwable.message}")
          }
        }
      },
      extraContext ?: emptyMap(),
    )
  }

  private fun Bitmap.toPngByteArray(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
  }
}
