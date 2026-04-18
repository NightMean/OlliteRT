package com.ollitert.llm.server.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.IMPORTS_DIR
import com.ollitert.llm.server.data.KEEP_ALIVE_RECHECK_MS
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import fi.iki.elonen.NanoHTTPD
import java.io.File

/**
 * Manages the LLM model keep-alive lifecycle: idle timeout, auto-unload, auto-reload,
 * model selection, and helper utilities (image decoding, system instruction building).
 *
 * Separated from LlmHttpService to isolate model lifecycle transitions from HTTP routing,
 * notification management, and inference execution concerns.
 *
 * Owns the keep-alive timer, model cache, and idle-unload state. The enclosing service
 * holds a reference and delegates model selection and keep-alive management here.
 */
class LlmHttpModelLifecycle(
  private val context: Context,
  private val allowlistLoader: LlmHttpAllowlistLoader,
  /** Reads imported models from DataStore. Provided by the service via Hilt EntryPoint. */
  private val readImportedModels: () -> List<ImportedModel> = { emptyList() },
) {

  companion object {
    private const val LOG_TAG = "LlmHttpModelLifecycle"
  }

  // ── State ──────────────────────────────────────────────────────────────────

  /** Currently loaded model. Null when idle-unloaded or before first load. */
  @Volatile var defaultModel: Model? = null

  /** Cache of Model objects built from the allowlist, keyed by name. */
  val modelCache: MutableMap<String, Model> = java.util.concurrent.ConcurrentHashMap()

  /** Name of the model that was unloaded due to idle timeout, for auto-reload. */
  @Volatile var keepAliveUnloadedModelName: String? = null
    private set

  fun setKeepAliveUnloadedModelName(name: String?) { keepAliveUnloadedModelName = name }

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
   */
  val keepAliveLock = Any()

  private val keepAliveRunnable = Runnable { onKeepAliveTimeout() }

  /** Called when the keep-alive idle timer fires. */
  private fun onKeepAliveTimeout() {
    // Capture the model and null the reference inside the lock (fast — no blocking I/O).
    // This prevents selectModel() from returning a model that we're about to destroy.
    // The actual native cleanup (Engine.close) runs OUTSIDE the lock to avoid blocking
    // request threads for seconds while multi-GB native memory is freed.
    data class UnloadInfo(val model: Model, val minutes: Int)
    val info: UnloadInfo = synchronized(keepAliveLock) {
      if (ServerMetrics.isInferring.value) {
        keepAliveHandler.postDelayed(keepAliveRunnable, KEEP_ALIVE_RECHECK_MS)
        Log.i(LOG_TAG, "Keep-alive: model is inferring, will recheck in ${KEEP_ALIVE_RECHECK_MS / 1000}s")
        return
      }
      val model = defaultModel ?: return
      val mins = LlmHttpPrefs.getKeepAliveMinutes(context)
      Log.i(LOG_TAG, "Keep-alive: unloading model ${model.name} after ${mins}m idle")
      keepAliveUnloadedModelName = model.name
      // Null defaultModel inside the lock so selectModel() sees it as unavailable immediately.
      // Keep model.instance non-null so cleanUp() can close the native Engine/Conversation.
      model.initializing = false
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
    if (!LlmHttpPrefs.isKeepAliveEnabled(context)) return
    val minutes = LlmHttpPrefs.getKeepAliveMinutes(context)
    if (minutes <= 0) return
    keepAliveHandler.postDelayed(keepAliveRunnable, minutes * 60_000L)
  }

  // ── Model reload from idle ─────────────────────────────────────────────────

  /**
   * Reload the model after it was unloaded due to keep_alive idle timeout.
   * Blocks the calling thread (NanoHTTPD request thread) until the model is ready.
   * Returns the loaded model, or null if reload fails.
   */
  fun reloadModelFromIdle(): Model? {
    synchronized(keepAliveLock) {
      // Double-check: another thread may have already reloaded
      if (defaultModel != null) return defaultModel
      val modelName = keepAliveUnloadedModelName ?: return null
      Log.i(LOG_TAG, "Keep-alive: reloading model $modelName (waking from idle)")
      RequestLogStore.addEvent(
        "Auto-reloading model: $modelName (keep_alive wake-up)",
        modelName = modelName,
        category = EventCategory.MODEL,
      )
      ServerMetrics.onModelReloadedFromIdle()

      // pickModelByName already restores persisted inference config via restoreInferenceConfig
      val model = pickModelByName(modelName) ?: run {
        Log.e(LOG_TAG, "Keep-alive: model '$modelName' not found during reload")
        return null
      }

      val loadStart = SystemClock.elapsedRealtime()
      val eagerVision = LlmHttpPrefs.isEagerVisionInit(context)
      val supportImage = model.llmSupportImage && eagerVision
      val supportAudio = model.llmSupportAudio
      var initErr = ""
      ServerLlmModelHelper.initialize(
        context = context,
        model = model,
        supportImage = supportImage,
        supportAudio = supportAudio,
        onDone = { initErr = it },
        systemInstruction = buildSystemInstruction(model.name),
      )
      if (initErr.isNotEmpty()) {
        Log.e(LOG_TAG, "Keep-alive: model reload failed: $initErr")
        RequestLogStore.addEvent(
          "Keep-alive reload failed: $initErr",
          level = LogLevel.ERROR,
          modelName = modelName,
          category = EventCategory.MODEL,
        )
        return null
      }
      model.initializedWithVision = supportImage
      defaultModel = model
      modelCache[model.name] = model
      keepAliveUnloadedModelName = null
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
    val allowlist = allowlistLoader.load()
    val allowlistMatch = allowlist.firstOrNull { it.name.equals(name, ignoreCase = true) }
    val model = if (allowlistMatch != null) {
      val built = LlmHttpModelFactory.buildAllowedModel(allowlistMatch, importsDir)
      built.preProcess()
      built
    } else {
      // 2. Fall back to imported models from DataStore
      val importedMatch = try {
        readImportedModels().firstOrNull { it.fileName.equals(name, ignoreCase = true) }
      } catch (e: Exception) {
        Log.w(LOG_TAG, "Failed to read imported models from DataStore", e)
        null
      }
      if (importedMatch != null) {
        Log.i(LOG_TAG, "Model '$name' found in imported models registry")
        LlmHttpModelFactory.buildImportedModel(importedMatch)
      } else {
        null
      }
    } ?: return null

    // Restore persisted inference config so settings survive app/service restarts
    LlmHttpModelFactory.restoreInferenceConfig(context, model)
    return model
  }

  // ── Model selection (per-request) ──────────────────────────────────────────

  /** Result of [selectModel]: either the active model or a descriptive error. */
  sealed class ModelSelection {
    data class Ok(val model: Model) : ModelSelection()
    data class Error(val status: NanoHTTPD.Response.Status, val message: String) : ModelSelection()
  }

  /**
   * Resolves the model to use for an inference request. Handles idle-reload when the model
   * was unloaded by keep_alive, validates the client's requested model name against the
   * active model, and returns a descriptive error if there's a mismatch.
   */
  fun selectModel(requestedModel: String?): ModelSelection {
    // Hold keepAliveLock to prevent the keep-alive timer from unloading the model between
    // our read of defaultModel and the caller's use of the returned Model object.
    synchronized(keepAliveLock) {
      // If model was unloaded due to keep_alive idle timeout, auto-reload it regardless of
      // what model the client requested. The keep_alive reload restores the same model that was
      // previously active, with its persisted config.
      if (defaultModel == null && ServerMetrics.isIdleUnloaded.value) {
        val reloaded = reloadModelFromIdle()
          ?: return ModelSelection.Error(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "Model is reloading after idle timeout, please retry")
        return ModelSelection.Ok(reloaded)
      }

      val active = defaultModel
        ?: return ModelSelection.Error(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "No model is currently loaded")

      val requested = requestedModel?.trim().orEmpty()
      if (requested.isEmpty() || requested.equals("local", ignoreCase = true) ||
        requested.equals("default", ignoreCase = true)
      ) {
        return ModelSelection.Ok(active)
      }
      // Check if the requested model matches the currently loaded model. We normalize both
      // names to handle variations (e.g. "gemma-4-e2b" vs "Gemma_4_E2B_it").
      val requestedKey = LlmHttpBridgeUtils.normalizeModelKey(requested)
      val activeKey = LlmHttpBridgeUtils.normalizeModelKey(active.name)
      if (requestedKey == activeKey) {
        return ModelSelection.Ok(active)
      }
      // The requested model doesn't match the active model. Return a descriptive error.
      return ModelSelection.Error(
        NanoHTTPD.Response.Status.BAD_REQUEST,
        "Model '${requested}' is not loaded. Currently loaded: '${active.name}'. " +
          "Please select '${active.name}' in your client or load the requested model on the device first."
      )
    }
  }

  // ── Utilities ──────────────────────────────────────────────────────────────

  /** Builds the LiteRT systemInstruction from the per-model system prompt stored in prefs. */
  fun buildSystemInstruction(modelName: String): Contents? {
    if (!LlmHttpPrefs.isCustomPromptsEnabled(context)) return null
    val prompt = LlmHttpPrefs.getSystemPrompt(context, modelName)
    if (prompt.isBlank()) return null
    return Contents.of(listOf(Content.Text(prompt)))
  }

  /**
   * Decodes base64 image data URIs from chat messages into Bitmaps for multimodal inference.
   * Expected format: `data:image/jpeg;base64,/9j/4AAQ...`
   */
  fun decodeImageDataUris(messages: List<ChatMessage>): List<Bitmap> {
    val uris = LlmHttpRequestAdapter.extractImageDataUris(messages)
    return uris.mapNotNull { uri ->
      try {
        val base64Data = if (uri.contains(",")) uri.substringAfter(",") else uri
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      } catch (e: Exception) {
        Log.w(LOG_TAG, "Failed to decode image data URI", e)
        RequestLogStore.addEvent(
          "Failed to decode image: ${e.message?.take(80) ?: "Unknown error"}",
          level = LogLevel.ERROR,
          modelName = defaultModel?.name,
          category = EventCategory.SERVER,
        )
        null
      }
    }
  }
}
