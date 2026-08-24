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

package com.ollitert.llm.server.service.inference

import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.IMPORTS_DIR
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_SHORT_CHARS
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.allowlist.ModelCatalogMerger
import com.ollitert.llm.server.data.allowlist.ModelFactory
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.model.llmSupportAudio
import com.ollitert.llm.server.data.model.llmSupportImage
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the LLM model keep-alive lifecycle: idle timeout, auto-unload, auto-reload,
 * model selection, and helper utilities (image decoding, system instruction building).
 *
 * Separated from ServerService to isolate model lifecycle transitions from HTTP routing,
 * notification management, and inference execution concerns.
 *
 * Owns the keep-alive timer, model cache, and idle-unload state. The enclosing service
 * holds a reference and delegates model selection and keep-alive management here.
 */
class ModelLifecycle(
  private val context: Context,
  val modelCatalogMerger: ModelCatalogMerger,
  /** Reads imported models from DataStore. Provided by the service via Hilt EntryPoint. */
  private val readImportedModels: () -> List<ImportedModel> = { emptyList() },
) {

  private val payloadDecoder = MultimodalPayloadDecoder(context) { defaultModel?.name }

  companion object {
    private const val TAG = "OlliteRT.Lifecycle"
  }

  // ── State ──────────────────────────────────────────────────────────────────

  /** Currently loaded model. Null when idle-unloaded or before first load. */
  @Volatile var defaultModel: Model? = null

  /** Cache of Model objects built from the allowlist, keyed by name. */
  val modelCache: MutableMap<String, Model> = java.util.concurrent.ConcurrentHashMap()

  /**
   * Atomically paired name + prefs key of the model that was unloaded due to idle timeout.
   * Using AtomicReference ensures resolveModelContext() never sees an inconsistent pair
   * (e.g. new name with old prefsKey) without requiring a lock on the read path.
   */
  private val keepAliveUnloadedRef = AtomicReference<Pair<String?, String?>>(null to null)

  /** Name of the model that was unloaded due to idle timeout, for auto-reload. */
  val keepAliveUnloadedModelName: String?
    get() = keepAliveUnloadedRef.get().first

  /** Stable prefs key for the idle-unloaded model (used by REST config endpoints). */
  val keepAliveUnloadedModelPrefsKey: String?
    get() = keepAliveUnloadedRef.get().second

  fun setKeepAliveUnloadedModel(name: String?, prefsKey: String?) {
    keepAliveUnloadedRef.set(name to prefsKey)
  }

  // ── Keep-alive timer ───────────────────────────────────────────────────────
  // Uses a Handler on the main looper to schedule a delayed unload. The timer is reset
  // after each inference request completes. When it fires, native model memory (Engine +
  // Conversation) is released while the HTTP server stays running. The next request
  // triggers a synchronous model reload (blocking the HTTP thread until ready).

  private val keepAliveHandler = android.os.Handler(android.os.Looper.getMainLooper())
  /**
   * Lock protecting the idle-unload, reload-from-idle, and model selection transitions against
   * concurrent access. ALL reads and writes to [defaultModel] from service lifecycle paths
   * (onStartCommand, ACTION_RELOAD, onDestroy) and the inference hot path (selectModel) must
   * hold this lock. Without it, a keep-alive unload can race with an in-flight request, causing
   * the request thread to use a Model whose native Engine is being destroyed concurrently.
   *
   * Lock ordering (to prevent deadlock):
   * 1. keepAliveLock — outermost, for model lifecycle transitions (load, unload, idle reload, select)
   * 2. inferenceLock (in ServerService) — innermost, for serializing inference and config writes
   * Never acquire keepAliveLock while holding inferenceLock.
   */
  val keepAliveLock = Any()

  private var activeRequestAdmissions = 0
  private var isDestroyed = false
  @Volatile private var idleCleanupFinished: CountDownLatch? = null

  /** Keeps a selected model alive until the complete HTTP response has been written. */
  internal class RequestAdmission internal constructor(
    private val release: () -> Unit,
  ) : AutoCloseable {
    private val isReleased = AtomicBoolean(false)

    override fun close() {
      if (isReleased.compareAndSet(false, true)) release()
    }
  }

  /** Lifecycle-aware scope for background work (idle unload, cleanup). Cancelled on destroy. */
  private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val keepAliveRunnable = Runnable { onKeepAliveTimeout() }

  /**
   * Called when the keep-alive idle timer fires on the main thread.
   * Posts the actual work (lock acquisition + native cleanup) to IO dispatcher to avoid
   * blocking the main thread if the lock is held by a reload (10-60s → ANR risk).
   */
  private fun onKeepAliveTimeout() {
    lifecycleScope.launch {
      // Publish the lifecycle transition under the short state lock, then close native
      // resources outside it. Reload waits on the cleanup latch from a Ktor worker instead
      // of making Stop/Reload on the main thread wait for Engine.close().
      data class UnloadInfo(val model: Model, val minutes: Int, val cleanupFinished: CountDownLatch)
      val info: UnloadInfo = synchronized(keepAliveLock) {
        if (isDestroyed) return@launch
        if (activeRequestAdmissions > 0 || ServerMetrics.isInferring.value) {
          val recheckMs = ServerPrefs.getTimeoutKeepAliveRecheckSeconds(context) * 1000
          keepAliveHandler.postDelayed(keepAliveRunnable, recheckMs)
          Log.i(TAG, "Keep-alive: model has active requests, will recheck in ${recheckMs / 1000}s")
          return@launch
        }
        val model = defaultModel ?: return@launch
        val mins = ServerPrefs.getKeepAliveMinutes(context)
        Log.i(TAG, "Keep-alive: unloading model ${model.name} after ${mins}m idle")
        keepAliveUnloadedRef.set(model.name to model.prefsKey)
        // Null defaultModel inside the lock so selectModel() sees it as unavailable immediately.
        // Keep model.instance non-null so cleanUp() can close the native Engine/Conversation.
        defaultModel = null
        modelCache.remove(model.name, model)
        ServerMetrics.onModelIdleUnloaded()
        val cleanupFinished = CountDownLatch(1)
        idleCleanupFinished = cleanupFinished
        UnloadInfo(model, mins, cleanupFinished)
      }
      try {
        ServerLlmModelHelper.safeCleanup(info.model)
      } finally {
        info.cleanupFinished.countDown()
      }
      // A later admitted request sees the idle-unloaded state and performs one reload.
      RequestLogStore.addEvent(
        "Model unloaded: ${info.model.name} (after ${info.minutes}m idle, keep_alive)",
        modelName = keepAliveUnloadedModelName,
        category = EventCategory.MODEL,
      )
    }
  }

  /** Cancel any pending keep-alive unload timer. */
  fun cancelKeepAliveTimer() {
    keepAliveHandler.removeCallbacks(keepAliveRunnable)
  }

  /** Cancel the lifecycle scope to prevent coroutine leaks when the service is destroyed. */
  fun destroy() {
    synchronized(keepAliveLock) {
      isDestroyed = true
      cancelKeepAliveTimer()
    }
    lifecycleScope.cancel()
  }

  /**
   * Admit one model-using HTTP request. The lease must cover response writing,
   * including the complete SSE writer lifetime, not only handler construction.
   */
  internal fun acquireRequestAdmission(): RequestAdmission {
    synchronized(keepAliveLock) {
      check(!isDestroyed) { "Model lifecycle is destroyed" }
      cancelKeepAliveTimer()
      activeRequestAdmissions++
    }
    return RequestAdmission(::releaseRequestAdmission)
  }

  private fun releaseRequestAdmission() {
    synchronized(keepAliveLock) {
      check(activeRequestAdmissions > 0) { "No active model request admission to release" }
      activeRequestAdmissions--
      if (activeRequestAdmissions == 0 && !isDestroyed) resetKeepAliveTimer()
    }
  }

  internal fun activeRequestAdmissionCount(): Int =
    synchronized(keepAliveLock) { activeRequestAdmissions }

  /** Waits off the main thread until an idle-unloaded Engine has finished closing. */
  internal fun awaitIdleCleanup() {
    val cleanup = idleCleanupFinished ?: return
    var wasInterrupted = false
    while (cleanup.count > 0) {
      try {
        cleanup.await()
      } catch (_: InterruptedException) {
        wasInterrupted = true
      }
    }
    if (wasInterrupted) Thread.currentThread().interrupt()
  }

  internal fun hasActiveIdleCleanup(): Boolean = (idleCleanupFinished?.count ?: 0) > 0

  /**
   * Reset the keep-alive idle timer. Called after each inference completes.
   * If keep_alive is enabled, schedules a model unload after the configured idle duration.
   */
  fun resetKeepAliveTimer() {
    synchronized(keepAliveLock) {
      cancelKeepAliveTimer()
      if (isDestroyed || activeRequestAdmissions > 0) return
      if (!ServerPrefs.isKeepAliveEnabled(context)) return
      val minutes = ServerPrefs.getKeepAliveMinutes(context)
      if (minutes <= 0) return
      keepAliveHandler.postDelayed(keepAliveRunnable, minutes * 60_000L)
    }
  }

  // ── Model reload from idle ─────────────────────────────────────────────────

  /**
   * Reload the model after it was unloaded due to keep_alive idle timeout.
   * Blocks the calling thread (request handler thread) until the model is ready.
   * Returns the loaded model, or null if reload fails.
   *
   * Intentionally holds [keepAliveLock] for the full 10-60s model init. Releasing
   * the lock during init would allow concurrent double-reloads (crashing the
   * non-thread-safe native SDK) and keep-alive timer interference. Concurrent
   * requests block on the lock and get a successful response once the model is ready.
   */
  fun reloadModelFromIdle(): Model? {
    synchronized(keepAliveLock) {
      // Double-check: another thread may have already reloaded
      if (defaultModel != null) return defaultModel
      val modelName = keepAliveUnloadedModelName ?: return null
      awaitIdleCleanup()
      Log.i(TAG, "Keep-alive: reloading model $modelName (waking from idle)")
      RequestLogStore.addEvent(
        "Auto-reloading model: $modelName (keep_alive wake-up)",
        modelName = modelName,
        category = EventCategory.MODEL,
      )
      ServerMetrics.onModelReloadedFromIdle()

      // pickModelByName already restores persisted inference config via restoreInferenceConfig
      val model = pickModelByName(modelName) ?: run {
        Log.e(TAG, "Keep-alive: model '$modelName' not found during reload")
        // Clear unloaded state to prevent infinite retry on every subsequent request
        // (e.g. model file was deleted while idle-unloaded)
        keepAliveUnloadedRef.set(null to null)
        ServerMetrics.onModelReloadedFromIdle()
        return null
      }

      val loadStart = SystemClock.elapsedRealtime()
      val eagerVision = ServerPrefs.isEagerVisionInit(context)
      val supportImage = model.llmSupportImage && eagerVision
      val supportAudio = model.llmSupportAudio
      var initErr = ""
      ServerLlmModelHelper.initialize(
        context = context,
        model = model,
        supportImage = supportImage,
        supportAudio = supportAudio,
        onDone = { initErr = it },
        systemInstruction = buildSystemInstruction(model.prefsKey),
      )
      if (initErr.isNotEmpty()) {
        Log.e(TAG, "Keep-alive: model reload failed: $initErr")
        RequestLogStore.addEvent(
          "Keep-alive reload failed: $initErr",
          level = LogLevel.ERROR,
          modelName = modelName,
          category = EventCategory.MODEL,
        )
        // Clear unloaded state to prevent infinite retry on every subsequent request
        keepAliveUnloadedRef.set(null to null)
        ServerMetrics.onModelReloadedFromIdle()
        return null
      }
      model.initializedWithVision = supportImage
      defaultModel = model
      modelCache[model.name] = model
      keepAliveUnloadedRef.set(null to null)
      val loadMs = SystemClock.elapsedRealtime() - loadStart
      ServerMetrics.recordModelLoadTime(loadMs)
      RequestLogStore.addEvent(
        "Model reloaded: ${model.name} (${loadMs}ms, keep_alive wake-up)",
        modelName = model.name,
        category = EventCategory.MODEL,
      )
      // Reset keep-alive timer for the next idle period
      resetKeepAliveTimer()
      return model
    }
  }

  // ── Model lookup ───────────────────────────────────────────────────────────

  /**
   * Looks up a model by name from the allowlist or imported models registry, builds it,
   * and restores its persisted inference config. Does NOT initialize the LiteRT Engine —
   * the caller must call [ServerLlmModelHelper.initialize] separately.
   *
   * Resolution order:
   * 1. Allowlist models (from model_allowlist.json)
   * 2. Imported models (from DataStore, stored via the Import dialog)
   */
  fun pickModelByName(name: String): Model? {
    val externalDir = context.getExternalFilesDir(null) ?: return null
    val importsDir = File(externalDir, IMPORTS_DIR)

    // 1. Try allowlist models first
    val allowlist = modelCatalogMerger.load()
    val allowlistMatch = allowlist.firstOrNull { it.name.equals(name, ignoreCase = true) }
    val model = if (allowlistMatch != null) {
      val built = ModelFactory.buildAllowedModel(allowlistMatch, importsDir)
      built.preProcess()
      built
    } else {
      // 2. Fall back to imported models from DataStore
      val importedMatch = try {
        readImportedModels().firstOrNull { it.fileName.equals(name, ignoreCase = true) }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to read imported models from DataStore", e)
        null
      }
      if (importedMatch != null) {
        Log.i(TAG, "Model '$name' found in imported models registry")
        ModelFactory.buildImportedModel(importedMatch)
      } else {
        null
      }
    } ?: return null

    // Resolve the actual on-disk version: the allowlist may point to a newer commitHash
    // but the user still has an older file from updatableModelFiles.
    resolveOnDiskVersion(model, externalDir)

    // Restore persisted inference config so settings survive app/service restarts
    ModelFactory.restoreInferenceConfig(context, model)
    return model
  }

  private fun resolveOnDiskVersion(model: Model, externalDir: File) {
    if (model.localModelFilePathOverride.isNotEmpty()) return
    val currentPath = File(externalDir, "${model.normalizedName}/${model.version}/${model.downloadFileName}")
    if (currentPath.exists()) {
      Log.i(TAG, "Model '${model.name}' found at current version ${model.version}/" +
        "${model.downloadFileName}")
      cleanupOldVersions(model, externalDir)
      return
    }

    for (updatable in model.updatableModelFiles) {
      if (updatable.commitHash.isEmpty()) continue
      val oldPath = File(externalDir, "${model.normalizedName}/${updatable.commitHash}/${updatable.fileName}")
      if (oldPath.exists()) {
        Log.i(TAG, "Resolved '${model.name}' to older version ${updatable.commitHash} via updatableModelFiles")
        model.version = updatable.commitHash
        model.downloadFileName = updatable.fileName
        model.totalBytes = oldPath.length()
        model.updatable = true
        model.applyUpdateHints(context.getString(R.string.config_hint_requires_model_update))
        return
      }
    }

    // Last resort: scan version directories for the expected model file.
    val modelDir = File(externalDir, model.normalizedName)
    if (!modelDir.isDirectory) return
    val versionDirs = modelDir.listFiles { f -> f.isDirectory } ?: return
    for (dir in versionDirs) {
      val candidate = File(dir, model.downloadFileName)
      if (candidate.isFile && candidate.length() > 0) {
        Log.w(TAG, "Resolved '${model.name}' via filesystem scan — found in ${dir.name}/" +
          " (not in updatableModelFiles; allowlist may be incomplete)")
        model.version = dir.name
        model.totalBytes = candidate.length()
        return
      }
    }
  }

  private fun cleanupOldVersions(model: Model, externalDir: File) {
    for (updatable in model.updatableModelFiles) {
      if (updatable.commitHash.isEmpty()) continue
      val oldDir = File(externalDir, "${model.normalizedName}/${updatable.commitHash}")
      if (oldDir.isDirectory) {
        val sizeBytes = oldDir.listFiles()?.sumOf { it.length() } ?: 0
        if (oldDir.deleteRecursively()) {
          Log.i(TAG, "Deleted old model version for '${model.name}': ${updatable.commitHash} " +
            "(freed ${sizeBytes / 1_048_576}MB)")
        } else {
          Log.w(TAG, "Failed to delete old model version directory: ${oldDir.absolutePath}")
        }
      }
    }
  }

  // ── Model selection (per-request) ──────────────────────────────────────────

  /** Result of [selectModel]: either the active model or a descriptive error. */
  sealed class ModelSelection {
    data class Ok(val model: Model) : ModelSelection()
    data class Error(val statusCode: Int, val message: String, val retryAfterSeconds: Int? = null) : ModelSelection()
  }

  /**
   * Resolves the model to use for an inference request. Handles idle-reload when the model
   * was unloaded by keep_alive, validates the client's requested model name against the
   * active model, and returns a descriptive error if there's a mismatch.
   */
  fun selectModel(requestedModel: String?): ModelSelection {
    // The HTTP layer holds RequestAdmission across this call and the complete response.
    // This lock makes selection atomic with unload/reload lifecycle transitions.
    synchronized(keepAliveLock) {
      // If model was unloaded due to keep_alive idle timeout, auto-reload it.
      // After reload, fall through to the name-matching check below — don't return Ok
      // blindly, since the client may have requested a different model than what was
      // idle-unloaded.
      if (defaultModel == null && ServerMetrics.isIdleUnloaded.value) {
        reloadModelFromIdle()
          ?: return ModelSelection.Error(503, "Failed to reload model after idle timeout — check logs for details")
      }

      val active = defaultModel
        ?: return ModelSelection.Error(503, "No model is currently loaded")

      val requested = requestedModel?.trim().orEmpty()
      if (requested.isEmpty() || requested.equals("local", ignoreCase = true) ||
        requested.equals("default", ignoreCase = true)
      ) {
        return ModelSelection.Ok(active)
      }
      // Check if the requested model matches the currently loaded model. We normalize both
      // names to handle variations (e.g. "gemma-4-e2b" vs "Gemma_4_E2B_it").
      val requestedKey = BridgeUtils.normalizeModelKey(requested)
      val activeKey = BridgeUtils.normalizeModelKey(active.name)
      if (requestedKey == activeKey) {
        return ModelSelection.Ok(active)
      }
      // The requested model doesn't match the active model. Return a descriptive error.
      return ModelSelection.Error(
        400,
        "Model '${requested}' is not loaded. Currently loaded: '${active.name}'. " +
          "Please select '${active.name}' in your client or load the requested model on the device first."
      )
    }
  }

  // ── Utilities ──────────────────────────────────────────────────────────────

  /** Builds the LiteRT systemInstruction from the per-model system prompt stored in prefs. */
  fun buildSystemInstruction(modelPrefsKey: String): Contents? {
    if (!ServerPrefs.isCustomPromptsEnabled(context)) return null
    val prompt = ServerPrefs.getSystemPrompt(context, modelPrefsKey)
    if (prompt.isBlank()) return null
    return Contents.of(listOf(Content.Text(prompt)))
  }

  /**
   * Decodes base64 image data URIs from chat messages into raw byte arrays for multimodal
   * inference. Delegates to [MultimodalPayloadDecoder] — kept as a delegate so existing
   * handler wiring keeps compiling.
   */
  fun decodeImageDataUris(messages: List<ChatMessage>): List<ByteArray> =
    payloadDecoder.decodeImageDataUris(messages)

  /**
   * Decodes base64 audio data strings from `input_audio` content parts into raw byte arrays.
   * Delegates to [MultimodalPayloadDecoder].
   */
  fun decodeAudioData(dataStrings: List<String>): List<ByteArray> =
    payloadDecoder.decodeAudioData(dataStrings)
}
