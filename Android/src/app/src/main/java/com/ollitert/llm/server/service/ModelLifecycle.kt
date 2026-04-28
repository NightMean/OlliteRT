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

package com.ollitert.llm.server.service

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.IMPORTS_DIR
import com.ollitert.llm.server.data.KEEP_ALIVE_RECHECK_MS
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.llmSupportAudio
import com.ollitert.llm.server.data.llmSupportImage
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
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
  val allowlistLoader: AllowlistLoader,
  /** Reads imported models from DataStore. Provided by the service via Hilt EntryPoint. */
  private val readImportedModels: () -> List<ImportedModel> = { emptyList() },
) {

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

  // True while a keep-alive reload is in progress. Concurrent requests check this to
  // return 503 immediately instead of queuing on keepAliveLock for 10-60+ seconds.
  @Volatile var isReloading: Boolean = false
    private set

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
      // Capture the model and null the reference inside the lock (fast — no blocking I/O).
      // This prevents selectModel() from returning a model that we're about to destroy.
      // The actual native cleanup (Engine.close) runs OUTSIDE the lock to avoid blocking
      // request threads for seconds while multi-GB native memory is freed.
      data class UnloadInfo(val model: Model, val minutes: Int)
      val info: UnloadInfo = synchronized(keepAliveLock) {
        if (ServerMetrics.isInferring.value) {
          keepAliveHandler.postDelayed(keepAliveRunnable, KEEP_ALIVE_RECHECK_MS)
          Log.i(TAG, "Keep-alive: model is inferring, will recheck in ${KEEP_ALIVE_RECHECK_MS / 1000}s")
          return@launch
        }
        val model = defaultModel ?: return@launch
        val mins = ServerPrefs.getKeepAliveMinutes(context)
        Log.i(TAG, "Keep-alive: unloading model ${model.name} after ${mins}m idle")
        keepAliveUnloadedRef.set(model.name to model.prefsKey)
        // Null defaultModel inside the lock so selectModel() sees it as unavailable immediately.
        // Keep model.instance non-null so cleanUp() can close the native Engine/Conversation.
        defaultModel = null
        ServerMetrics.onModelIdleUnloaded()
        UnloadInfo(model, mins)
      }
      // Native cleanup runs outside the lock — Engine.close() can take seconds for large models.
      // selectModel() will see defaultModel==null and isIdleUnloaded==true, triggering a reload.
      ServerLlmModelHelper.safeCleanup(info.model)
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

  /**
   * Reset the keep-alive idle timer. Called after each inference completes.
   * If keep_alive is enabled, schedules a model unload after the configured idle duration.
   */
  fun resetKeepAliveTimer() {
    cancelKeepAliveTimer()
    if (!ServerPrefs.isKeepAliveEnabled(context)) return
    val minutes = ServerPrefs.getKeepAliveMinutes(context)
    if (minutes <= 0) return
    keepAliveHandler.postDelayed(keepAliveRunnable, minutes * 60_000L)
  }

  // ── Model reload from idle ─────────────────────────────────────────────────

  /**
   * Reload the model after it was unloaded due to keep_alive idle timeout.
   * Blocks the calling thread (request handler thread) until the model is ready.
   * Returns the loaded model, or null if reload fails.
   *
   * Intentionally holds [keepAliveLock] for the full 10-60s model init. Releasing
   * the lock during init would allow concurrent double-reloads (crashing the
   * non-thread-safe native SDK) and keep-alive timer interference. The [isReloading]
   * volatile is a best-effort optimization that gives most concurrent requests an
   * immediate 503 — threads that slip past the TOCTOU window block on the lock and
   * get a successful response once the model is ready, which is better than a 503.
   */
  fun reloadModelFromIdle(): Model? {
    synchronized(keepAliveLock) {
      // Double-check: another thread may have already reloaded
      if (defaultModel != null) return defaultModel
      val modelName = keepAliveUnloadedModelName ?: return null
      isReloading = true
      try {
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
      } finally {
        isReloading = false
      }
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
    val allowlist = allowlistLoader.load()
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

    // Restore persisted inference config so settings survive app/service restarts
    ModelFactory.restoreInferenceConfig(context, model)
    return model
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
    // If another thread is already reloading the model, return 503 immediately instead
    // of blocking the request thread for 10-60+ seconds on keepAliveLock.
    if (isReloading) {
      return ModelSelection.Error(503, "Model is reloading after idle timeout, please retry in a few seconds", retryAfterSeconds = 30)
    }
    // Hold keepAliveLock to prevent the keep-alive timer from unloading the model between
    // our read of defaultModel and the caller's use of the returned Model object.
    synchronized(keepAliveLock) {
      // If model was unloaded due to keep_alive idle timeout, auto-reload it.
      // After reload, fall through to the name-matching check below — don't return Ok
      // blindly, since the client may have requested a different model than what was
      // idle-unloaded.
      if (defaultModel == null && ServerMetrics.isIdleUnloaded.value) {
        reloadModelFromIdle()
          ?: return ModelSelection.Error(503, "Model is reloading after idle timeout, please retry")
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
   * inference. The LiteRT SDK's Content.ImageBytes accepts raw bytes and detects the format
   * (JPEG, PNG, WebP) from magic bytes in the native layer — no Bitmap intermediate needed.
   * Expected format: `data:image/jpeg;base64,/9j/4AAQ...`
   */
  fun decodeImageDataUris(messages: List<ChatMessage>): List<ByteArray> {
    val uris = PromptBuilder.extractImageDataUris(messages)
    return uris.mapNotNull { uri ->
      try {
        val base64Data = if (uri.contains(",")) uri.substringAfter(",") else uri
        Base64.decode(base64Data, Base64.DEFAULT)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to decode image data URI", e)
        RequestLogStore.addEvent(
          "Failed to decode image: ${e.message?.take(80) ?: context.getString(R.string.error_unknown)}",
          level = LogLevel.ERROR,
          modelName = defaultModel?.name,
          category = EventCategory.SERVER,
        )
        null
      }
    }
  }

  /**
   * Decodes base64 audio data strings from `input_audio` content parts into raw byte arrays,
   * then ensures each clip is mono PCM (required by the LiteRT audio API).
   * Silently drops any clip that fails to decode or preprocess — same error-resilience
   * pattern used by [decodeImageDataUris].
   */
  fun decodeAudioData(dataStrings: List<String>): List<ByteArray> {
    return dataStrings.mapNotNull { base64Data ->
      try {
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        val format = AudioPreprocessor.detectFormat(bytes)
        AudioPreprocessor.ensureMono(bytes, format)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to decode audio data", e)
        RequestLogStore.addEvent(
          "Failed to decode audio: ${e.message?.take(80) ?: context.getString(R.string.error_unknown)}",
          level = LogLevel.ERROR,
          modelName = defaultModel?.name,
          category = EventCategory.SERVER,
        )
        null
      }
    }
  }
}
