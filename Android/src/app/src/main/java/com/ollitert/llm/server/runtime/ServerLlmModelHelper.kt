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

package com.ollitert.llm.server.runtime

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
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
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.cleanUpLiteRtErrorMessage
import com.ollitert.llm.server.data.Accelerator
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.DEFAULT_MAX_TOKEN
import com.ollitert.llm.server.data.DEFAULT_TEMPERATURE
import com.ollitert.llm.server.data.DEFAULT_TOPK
import com.ollitert.llm.server.data.DEFAULT_TOPP
import com.ollitert.llm.server.data.DEFAULT_VISION_ACCELERATOR
import com.ollitert.llm.server.data.MIN_STORAGE_FOR_MODEL_INIT_BYTES
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.bytesToMb
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.PromptBuilder
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.concurrent.CancellationException

typealias ResultListener =
  (partialResult: String, done: Boolean, partialThinkingResult: String?) -> Unit

typealias CleanUpListener = () -> Unit

private const val TAG = "OlliteRT.ModelHelper"

data class LlmModelInstance(val engine: Engine, var conversation: Conversation)

object GpuAvailability {
  // The SDK's sampler uses dlopen("libOpenCL.so") without a full path. On some
  // devices (Pixel 5), the library exists only in /vendor/lib64/ which is not
  // accessible from the app's linker namespace even though File.exists() returns
  // true (because /system/vendor/ is a symlink to /vendor/).
  val isOpenClAccessible: Boolean by lazy {
    Log.i(TAG, "OpenCL probe: device=${Build.DEVICE} model=${Build.MODEL} " +
      "SOC=${Build.SOC_MODEL} SDK=${Build.VERSION.SDK_INT}")

    val probeResults = StringBuilder()

    // Step 1: Check if System.loadLibrary can find it (app linker namespace).
    // This is the most reliable signal — if the app's own classloader can't
    // load it, the SDK's native dlopen definitely can't either.
    val javaLoadSuccess = try {
      System.loadLibrary("OpenCL")
      probeResults.append("System.loadLibrary=OK; ")
      true
    } catch (e: UnsatisfiedLinkError) {
      probeResults.append("System.loadLibrary=FAIL(${e.message}); ")
      false
    }

    // Step 2: Check where the library physically exists (diagnostic only).
    // /system/vendor/ is a symlink to /vendor/ on most devices — both are
    // blocked by linker namespace restrictions for app processes.
    val searchPaths = listOf(
      "/system/lib64/libOpenCL.so",
      "/system/lib/libOpenCL.so",
      "/system/vendor/lib64/libOpenCL.so",
      "/system/vendor/lib/libOpenCL.so",
      "/vendor/lib64/libOpenCL.so",
      "/vendor/lib/libOpenCL.so",
    )
    val foundPaths = searchPaths.filter { File(it).exists() }
    probeResults.append("paths_found=$foundPaths")

    // System.loadLibrary is authoritative: if it fails, OpenCL is not usable
    // regardless of which paths show the file. The file may physically exist
    // but the linker namespace prevents loading it.
    val accessible = javaLoadSuccess

    Log.i(TAG, "OpenCL probe result: accessible=$accessible — $probeResults")
    RequestLogStore.addEvent(
      "OpenCL probe: accessible=$accessible, " +
        "javaLoad=${if (javaLoadSuccess) "OK" else "FAIL"}, " +
        "found=${foundPaths.map { it.removePrefix("/system").removePrefix("/") }}",
      level = if (accessible) LogLevel.DEBUG else LogLevel.WARNING,
      category = EventCategory.MODEL,
    )

    accessible
  }
}

object ServerLlmModelHelper {
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = java.util.concurrent.ConcurrentHashMap()

  @OptIn(ExperimentalApi::class)
  fun initialize(
    context: Context,
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents? = null,
    tools: List<ToolProvider> = listOf(),
    initialMessages: List<Message> = listOf(),
    enableConversationConstrainedDecoding: Boolean = false,
    coroutineScope: CoroutineScope? = null,
    configOverrides: Map<String, Any>? = null,
  ) {
    val maxTokens = configOverrides?.let {
      (it[ConfigKeys.MAX_TOKENS.id] as? Number)?.toInt() ?: DEFAULT_MAX_TOKEN
    } ?: model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = configOverrides?.let {
      (it[ConfigKeys.TOPK.id] as? Number)?.toInt() ?: DEFAULT_TOPK
    } ?: model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = configOverrides?.let {
      (it[ConfigKeys.TOPP.id] as? Number)?.toFloat() ?: DEFAULT_TOPP
    } ?: model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature = configOverrides?.let {
      (it[ConfigKeys.TEMPERATURE.id] as? Number)?.toFloat() ?: DEFAULT_TEMPERATURE
    } ?: model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val accelerator = configOverrides?.let {
      (it[ConfigKeys.ACCELERATOR.id] as? String) ?: Accelerator.GPU.label
    } ?: model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    val visionAccelerator = configOverrides?.let {
      (it[ConfigKeys.VISION_ACCELERATOR.id] as? String) ?: DEFAULT_VISION_ACCELERATOR.label
    } ?: model.getStringConfigValue(
        key = ConfigKeys.VISION_ACCELERATOR,
        defaultValue = DEFAULT_VISION_ACCELERATOR.label,
      )
    val gpuAccessible = GpuAvailability.isOpenClAccessible
    val canFallbackToCpu = model.accelerators.contains(Accelerator.CPU)

    Log.i(TAG, "Backend selection: requested=$accelerator, openCL=$gpuAccessible, " +
      "cpuFallbackAvailable=$canFallbackToCpu, accelerators=${model.accelerators}")
    RequestLogStore.addEvent(
      "Backend: requested=$accelerator, OpenCL=${if (gpuAccessible) "OK" else "unavailable"}, " +
        "accelerators=${model.accelerators.map { it.label }}",
      level = LogLevel.DEBUG,
      modelName = model.name,
      category = EventCategory.MODEL,
    )

    val effectiveAccelerator = if (accelerator == Accelerator.GPU.label && !gpuAccessible && canFallbackToCpu) {
      Log.w(TAG, "GPU requested but OpenCL not accessible — falling back to CPU")
      RequestLogStore.addEvent(
        "GPU unavailable (OpenCL not accessible), using CPU",
        level = LogLevel.WARNING,
        modelName = model.name,
        category = EventCategory.MODEL,
      )
      Accelerator.CPU.label
    } else {
      accelerator
    }
    val effectiveVisionAccelerator = if (visionAccelerator == Accelerator.GPU.label && !gpuAccessible) {
      Accelerator.CPU.label
    } else {
      visionAccelerator
    }

    val visionBackend =
      when (effectiveVisionAccelerator) {
        Accelerator.CPU.label -> Backend.CPU()
        Accelerator.GPU.label -> Backend.GPU()
        Accelerator.NPU.label,
        Accelerator.TPU.label ->
          Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.GPU()
      }
    val preferredBackend =
      when (effectiveAccelerator) {
        Accelerator.CPU.label -> Backend.CPU()
        Accelerator.GPU.label -> Backend.GPU()
        Accelerator.NPU.label,
        Accelerator.TPU.label ->
          Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.CPU()
      }
    Log.i(TAG, "Preferred backend: ${preferredBackend::class.simpleName} " +
      "(requested: $accelerator, effective: $effectiveAccelerator)")
    RequestLogStore.addEvent(
      "Using backend: ${preferredBackend::class.simpleName} (effective=$effectiveAccelerator)",
      level = LogLevel.DEBUG,
      modelName = model.name,
      category = EventCategory.MODEL,
    )

    val modelPath = model.getPath(context = context)

    // Pre-load validation: verify the model file exists and has a reasonable size
    // before passing it to the native Engine constructor, which can SIGABRT on
    // corrupt/truncated files — unrecoverable from Java.
    val modelFile = File(modelPath)
    if (!modelFile.exists()) {
      onDone(context.getString(R.string.error_model_file_not_found, modelFile.name))
      return
    }
    // Minimum size check — a valid .litertlm file is always > 1KB.
    // Truncated files (e.g. from interrupted downloads) trigger native abort().
    if (modelFile.length() < 1024) {
      onDone(context.getString(R.string.error_model_file_corrupted, modelFile.length(), modelFile.name))
      return
    }
    if (model.totalBytes > 0 && modelFile.length() != model.totalBytes) {
      Log.e(TAG, "Model file size mismatch: on-disk=${modelFile.length()}, expected=${model.totalBytes}")
      onDone(context.getString(
        R.string.error_model_file_size_mismatch,
        modelFile.length().bytesToMb().toString() + "MB",
        model.totalBytes.bytesToMb().toString() + "MB",
      ))
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
        onDone(context.getString(R.string.error_storage_insufficient, availableMb.toString(), requiredMb.toString()))
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
        // /data/local/tmp is tmpfs (RAM-backed) which doesn't support mmap; redirect to
        // persistent storage so LiteRT can memory-map working files.
        cacheDir =
          if (modelPath.startsWith("/data/local/tmp"))
            context.getExternalFilesDir(null)?.absolutePath
          else null,
      )

    var engine: Engine? = null
    try {
      engine = Engine(engineConfig)
      engine.initialize()

      // THREAD SAFETY: This global flag has a set/read/reset race if initialize() and
      // resetConversation() overlap on different threads. Currently benign — all server-layer
      // callers pass false (the default), so the race has no observable effect.
      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      try {
        // The SDK's sampler internally uses an OpenCL topK kernel. On devices where
        // OpenCL is inaccessible (e.g. Pixel 5), passing SamplerConfig triggers
        // "Can not find OpenCL library" at inference time. Pass null to let the SDK
        // use its built-in non-OpenCL sampler path.
        val useSampler = preferredBackend !is Backend.NPU && gpuAccessible
        if (!useSampler && preferredBackend !is Backend.NPU) {
          Log.i(TAG, "Skipping SamplerConfig (OpenCL unavailable) — SDK will use internal sampler")
          RequestLogStore.addEvent(
            "SamplerConfig disabled (OpenCL unavailable for topK sampler)",
            level = LogLevel.INFO,
            modelName = model.name,
            category = EventCategory.MODEL,
          )
        }
        val conversation =
          engine.createConversation(
            ConversationConfig(
              samplerConfig =
                if (useSampler) {
                  SamplerConfig(
                    topK = topK,
                    topP = topP.toDouble(),
                    temperature = temperature.toDouble(),
                  )
                } else {
                  null
                },
              systemInstruction = systemInstruction,
              tools = tools,
              initialMessages = initialMessages,
              automaticToolCalling = false,
            ),
          )
        model.instance = LlmModelInstance(engine = engine, conversation = conversation)
      } finally {
        ExperimentalFlags.enableConversationConstrainedDecoding = false
      }
      Log.i(TAG, "Engine initialized successfully on ${preferredBackend::class.simpleName} for '${model.name}'")
      RequestLogStore.addEvent(
        "Engine initialized on ${preferredBackend::class.simpleName}",
        level = LogLevel.INFO,
        modelName = model.name,
        category = EventCategory.MODEL,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Engine init failed for '${model.name}' with ${preferredBackend::class.simpleName}: " +
        "[${e::class.simpleName}] ${e.message}", e)
      RequestLogStore.addEvent(
        "${preferredBackend::class.simpleName} init failed: [${e::class.simpleName}] ${e.message?.take(120)}",
        level = LogLevel.ERROR,
        modelName = model.name,
        category = EventCategory.MODEL,
      )
      try { engine?.close() } catch (closeEx: Exception) {
        Log.w(TAG, "Engine.close() failed during error cleanup (may already be closed by another thread)", closeEx)
      }
      System.gc()

      // Safety-net: if GPU init failed and the model supports CPU, retry with CPU.
      if (preferredBackend is Backend.GPU && canFallbackToCpu) {
        Log.w(TAG, "GPU initialization failed, retrying with CPU backend")
        RequestLogStore.addEvent(
          "GPU init failed, retrying with CPU: ${e.message?.take(80)}",
          level = LogLevel.WARNING,
          modelName = model.name,
          category = EventCategory.MODEL,
        )
        val cpuConfig = EngineConfig(
          modelPath = modelPath,
          backend = Backend.CPU(),
          visionBackend = if (supportImage) Backend.CPU() else null,
          audioBackend = if (supportAudio) Backend.CPU() else null,
          maxNumTokens = maxTokens,
          cacheDir =
            if (modelPath.startsWith("/data/local/tmp"))
              context.getExternalFilesDir(null)?.absolutePath
            else null,
        )
        var fallbackEngine: Engine? = null
        try {
          fallbackEngine = Engine(cpuConfig)
          fallbackEngine.initialize()
          ExperimentalFlags.enableConversationConstrainedDecoding =
            enableConversationConstrainedDecoding
          try {
            val conversation =
              fallbackEngine.createConversation(
                ConversationConfig(
                  samplerConfig = if (gpuAccessible) SamplerConfig(
                    topK = topK,
                    topP = topP.toDouble(),
                    temperature = temperature.toDouble(),
                  ) else null,
                  systemInstruction = systemInstruction,
                  tools = tools,
                  initialMessages = initialMessages,
                  automaticToolCalling = false,
                ),
              )
            model.instance = LlmModelInstance(engine = fallbackEngine, conversation = conversation)
          } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = false
          }
          Log.i(TAG, "CPU fallback successful for '${model.name}'")
          RequestLogStore.addEvent(
            "Model loaded on CPU (GPU unavailable on this device)",
            level = LogLevel.INFO,
            modelName = model.name,
            category = EventCategory.MODEL,
          )
          onDone("")
          return
        } catch (fallbackEx: Exception) {
          Log.e(TAG, "CPU fallback also failed for '${model.name}': " +
            "[${fallbackEx::class.simpleName}] ${fallbackEx.message}", fallbackEx)
          RequestLogStore.addEvent(
            "CPU fallback failed: [${fallbackEx::class.simpleName}] ${fallbackEx.message?.take(120)}",
            level = LogLevel.ERROR,
            modelName = model.name,
            category = EventCategory.MODEL,
          )
          try { fallbackEngine?.close() } catch (closeEx: Exception) {
            Log.w(TAG, "Engine.close() failed during CPU fallback cleanup", closeEx)
          }
          System.gc()
        }
      }

      onDone(cleanUpLiteRtErrorMessage(e.message ?: context.getString(R.string.error_unknown)))
      return
    }
    onDone("")
  }

  @OptIn(ExperimentalApi::class)
  fun resetConversation(
    model: Model,
    supportImage: Boolean = false,
    supportAudio: Boolean = false,
    systemInstruction: Contents? = null,
    tools: List<ToolProvider> = listOf(),
    initialMessages: List<Message> = listOf(),
    enableConversationConstrainedDecoding: Boolean = false,
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
      try {
        val isNpuBackend = accelerator == Accelerator.NPU.label || accelerator == Accelerator.TPU.label
        val useSampler = !isNpuBackend && GpuAvailability.isOpenClAccessible
        val newConversation =
          engine.createConversation(
            ConversationConfig(
              samplerConfig =
                if (useSampler) {
                  SamplerConfig(
                    topK = topK,
                    topP = topP.toDouble(),
                    temperature = temperature.toDouble(),
                  )
                } else {
                  null
                },
              systemInstruction = systemInstruction,
              tools = tools,
              initialMessages = initialMessages,
              automaticToolCalling = false,
            )
          )
        instance.conversation = newConversation
      } finally {
        ExperimentalFlags.enableConversationConstrainedDecoding = false
      }

      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to reset conversation completely", e)
      RequestLogStore.addEvent(
        "Failed to reset conversation: ${e.message?.take(80) ?: "Unknown error"}",
        level = LogLevel.ERROR,
        modelName = model.name,
      )
      // If new Conversation creation failed, the model is in a broken state —
      // close the Engine (which holds hundreds of MB of native memory) and null
      // the instance so the next request triggers a full re-initialization.
      try { (model.instance as? LlmModelInstance)?.engine?.close() } catch (e: Exception) {
        Log.w(TAG, "Engine.close() failed during conversation reset (may already be closed by another thread)", e)
      }
      cleanUpListeners.remove(model.name)?.invoke()
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

  fun cleanUp(model: Model, onDone: () -> Unit) {
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

    cleanUpListeners.remove(model.name)?.invoke()
    model.instance = null

    onDone()
    Log.d(TAG, "Clean up done.")
  }

  fun stopResponse(model: Model) {
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

  fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit = {},
    images: List<ByteArray> = listOf(),
    audioClips: List<ByteArray> = listOf(),
    coroutineScope: CoroutineScope? = null,
    extraContext: Map<String, String>? = null,
    onNativeToolCalls: ((List<com.google.ai.edge.litertlm.ToolCall>) -> Unit)? = null,
  ) {
    val instance = model.instance as? LlmModelInstance
    if (instance == null) {
      onError("LlmModelInstance is not initialized.")
      return
    }

    cleanUpListeners.putIfAbsent(model.name, cleanUpListener)

    val conversation = instance.conversation

    val contents = mutableListOf<Content>()
    if (images.isNotEmpty() && input.contains(PromptBuilder.IMAGE_PLACEHOLDER)) {
      // Multi-image interleaving: the prompt contains placeholder tokens at the exact
      // positions where images appeared in the conversation. Split on placeholders and
      // interleave Content.Text / Content.ImageBytes so each image is associated with
      // its correct conversation turn.
      val segments = input.split(PromptBuilder.IMAGE_PLACEHOLDER)
      var imageIndex = 0
      for ((i, segment) in segments.withIndex()) {
        if (segment.trim().isNotEmpty()) {
          contents.add(Content.Text(segment.trim()))
        }
        // After each segment except the last, insert the corresponding image
        if (i < segments.size - 1 && imageIndex < images.size) {
          contents.add(Content.ImageBytes(images[imageIndex]))
          imageIndex++
        }
      }
      // Append any remaining images that had no placeholder (shouldn't happen, but safe)
      while (imageIndex < images.size) {
        contents.add(Content.ImageBytes(images[imageIndex]))
        imageIndex++
      }
    } else {
      // Single-image or non-chat path: images before text (LiteRT expects image content first)
      for (image in images) {
        contents.add(Content.ImageBytes(image))
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
          if (onNativeToolCalls != null && message.toolCalls.isNotEmpty()) {
            onNativeToolCalls.invoke(message.toolCalls)
          }
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
            Log.e(TAG, "Inference onError for '${model.name}': " +
              "[${throwable::class.simpleName}] ${throwable.message}", throwable)
            onError("Error: ${throwable.message}")
          }
        }
      },
      extraContext ?: emptyMap(),
    )
  }

}
