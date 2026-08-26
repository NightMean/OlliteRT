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

import com.ollitert.llm.server.common.ServerMetrics
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_LONG_CHARS
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.model.isSpeculativeDecodingEnabled
import com.ollitert.llm.server.data.model.isThinkingEnabled
import com.ollitert.llm.server.data.model.llmSupportAudio
import com.ollitert.llm.server.data.model.llmSupportImage
import com.ollitert.llm.server.data.model.llmSupportThinking
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.inference.InferenceRunner
import com.ollitert.llm.server.service.inference.ModelLifecycle
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.ollitert.llm.server.data.prefs.isVerboseDebugEnabled
/**
 * Handles background model loading orchestration: storage verification, idle/cleanup latching,
 * engine initialization / warmup, metrics publishing, configuration logging, and failure handling.
 */
internal class ServerModelLoader(
  private val context: Context,
  private val modelLifecycle: ModelLifecycle,
  private val notificationManager: ServerNotificationManager,
  private val getInferenceRunner: () -> InferenceRunner?,
  private val loadGeneration: AtomicLong,
  private val pendingReloadAfterLoad: AtomicReference<ServerService.Companion.PendingReload?>,
  private val onModelPublished: (Model) -> Unit,
  private val onOomRecover: () -> Unit,
  private val emitDebugStackTrace: (Throwable, String, String?) -> Unit,
) {
  companion object {
    private const val TAG = "OlliteRT.ModelLoader"
  }

  /**
   * Loads a model on a background thread: storage check, cleanup latch wait,
   * warmup/initialize, metrics, notification update.
   *
   * Must NOT be called from the main thread — contains blocking I/O and native SDK calls.
   */
  fun loadModelOnThread(
    model: Model,
    thisGeneration: Long,
    notifState: LoadNotificationState,
  ) {
    try {
      ServerCleanupCoordinator.checkStorageBeforeLoad(context)
      ServerCleanupCoordinator.awaitPreviousCleanup()
      modelLifecycle.awaitIdleCleanup()

      val loadStart = SystemClock.elapsedRealtime()
      initializeOrWarmUp(model)

      // If another model load was initiated while we were warming up, discard this result
      if (loadGeneration.get() != thisGeneration) {
        Log.w(TAG, "Warmup for ${model.name} completed but a newer load was initiated — discarding")
        ServerLlmModelHelper.safeCleanup(model)
        return
      }
      val published = synchronized(modelLifecycle.keepAliveLock) {
        if (loadGeneration.get() != thisGeneration) {
          false
        } else {
          onModelPublished(model)
          true
        }
      }
      if (!published) {
        ServerLlmModelHelper.safeCleanup(model)
        return
      }
      ServerMetrics.recordModelLoadTime(SystemClock.elapsedRealtime() - loadStart)
      ServerMetrics.setActiveAccelerator(
        model.configValues[com.ollitert.llm.server.data.prefs.ConfigKeys.ACCELERATOR.id]?.toString()
      )
      ServerMetrics.setThinkingEnabled(model.isThinkingEnabled)
      ServerMetrics.setSpeculativeDecodingEnabled(model.isSpeculativeDecodingEnabled)
      ServerMetrics.onServerRunning(notifState.advertisedHost, notifState.isLoopbackOnly)
      modelLifecycle.resetKeepAliveTimer()
      RequestLogStore.addEvent(
        "Model ready: ${model.name} (${SystemClock.elapsedRealtime() - loadStart}ms)",
        modelName = model.name,
        category = EventCategory.MODEL,
      )
      logVerboseModelConfig(model)
      if (handleQueuedReload(model)) return
      logActiveSystemPrompt(model)
      notificationManager.updateToRunning(model, notifState)
    } catch (t: Throwable) {
      handleModelLoadFailure(t, model, thisGeneration, notifState)
    }
  }

  /** Initializes the model engine, with or without warmup depending on user settings. */
  private fun initializeOrWarmUp(model: Model) {
    if (ServerPrefs.isWarmupEnabled(context)) {
      getInferenceRunner()?.warmUpModel(model)
    } else {
      val initErr = modelLifecycle.initializeEngine(model)
      if (initErr.isNotEmpty()) {
        throw RuntimeException(context.getString(R.string.error_model_init_failed, initErr))
      }
      RequestLogStore.addEvent(
        "Warmup skipped — Model loaded without test inference (disabled in Settings)",
        modelName = model.name,
        category = EventCategory.MODEL,
      )
    }
  }

  /** Logs model config dump when verbose debug is enabled. */
  private fun logVerboseModelConfig(model: Model) {
    if (!ServerPrefs.isVerboseDebugEnabled(context)) return
    val sizeMb = String.format(java.util.Locale.US, "%.1f", model.totalBytes / (1024.0 * 1024.0))
    val debugText = buildString {
      appendLine("Name: ${model.name}")
      appendLine("Path: ${model.getPath(context)}")
      appendLine("Size: ${sizeMb}MB (${model.totalBytes} bytes)")
      appendLine("Capabilities: vision=${model.llmSupportImage}, audio=${model.llmSupportAudio}, thinking=${model.llmSupportThinking}, speculative_decoding=${model.isSpeculativeDecodingEnabled}")
      if (model.configValues.isNotEmpty()) {
        appendLine("Config:")
        model.configValues.forEach { (k, v) -> appendLine("  $k: $v") }
      }
    }.trimEnd()
    RequestLogStore.addEvent(
      "Loaded model configuration",
      level = LogLevel.DEBUG,
      modelName = model.name,
      category = EventCategory.MODEL,
      body = debugText,
    )
  }

  /**
   * Checks for a queued reload (user changed reinit settings while model was loading).
   * @return true if a reload was triggered and the caller should return immediately.
   */
  private fun handleQueuedReload(model: Model): Boolean {
    val queued = pendingReloadAfterLoad.getAndSet(null) ?: return false
    if (queued.modelName == model.name) {
      Log.i(TAG, "Executing queued reload for ${queued.modelName}")
      RequestLogStore.addEvent(
        "Applying queued settings change — reloading model",
        modelName = queued.modelName,
        category = EventCategory.SETTINGS,
      )
      ServerService.reload(context, queued.port, queued.modelName, queued.configValues)
      return true
    }
    Log.w(TAG, "Discarding stale queued reload for ${queued.modelName} — loaded model is ${model.name}")
    return false
  }

  /** Logs the active system prompt if custom prompts are enabled. */
  private fun logActiveSystemPrompt(model: Model) {
    val sysPrompt = if (ServerPrefs.isCustomPromptsEnabled(context))
      ServerPrefs.getSystemPrompt(context, model.prefsKey) else ""
    if (sysPrompt.isNotBlank()) {
      RequestLogStore.addEvent(
        "System prompt active: \"${sysPrompt.take(LOG_ERROR_PREVIEW_LONG_CHARS)}\"${if (sysPrompt.length > LOG_ERROR_PREVIEW_LONG_CHARS) "…" else ""}",
        modelName = model.name,
        category = EventCategory.PROMPT,
        body = buildJsonObject {
          put("type", "prompt_active")
          put("prompt_type", "system_prompt")
          put("text", sysPrompt)
        }.toString(),
      )
    }
  }

  /** Handles model load failure: OOM cleanup, error reporting, notification update. */
  private fun handleModelLoadFailure(
    t: Throwable,
    model: Model,
    thisGeneration: Long,
    notifState: LoadNotificationState,
  ) {
    if (loadGeneration.get() != thisGeneration) {
      Log.w(TAG, "Warmup for ${model.name} failed but a newer load was initiated — ignoring")
      ServerLlmModelHelper.safeCleanup(model)
      return
    }
    if (t is OutOfMemoryError) {
      onOomRecover()
      try {
        ServerLlmModelHelper.cleanUp(model) {}
      } catch (e: Exception) {
        Log.w(TAG, "cleanUp() failed during OOM recovery", e)
      }
      System.gc()
    }
    Log.e(TAG, "Failed to load model ${model.name}", t)
    emitDebugStackTrace(t, "model_load", model.name)
    pendingReloadAfterLoad.set(null)
    val msg = t.message?.take(LOG_ERROR_PREVIEW_LONG_CHARS) ?: context.getString(R.string.error_model_init_unknown)
    val category = if (t is OutOfMemoryError) ErrorCategory.SYSTEM else ErrorCategory.MODEL_LOAD
    ServerMetrics.onServerError(msg)
    ServerMetrics.incrementErrorCount(category)
    RequestLogStore.addEvent(
      "Model load failed: $msg",
      level = LogLevel.ERROR,
      modelName = model.name,
      category = EventCategory.MODEL,
    )
    notificationManager.updateToLoadFailed(t, model, notifState)
  }
}
