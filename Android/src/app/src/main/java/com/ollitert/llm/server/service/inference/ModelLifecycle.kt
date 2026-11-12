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
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.ModelType
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

internal const val MODEL_RECOVERY_IN_PROGRESS_MESSAGE =
  "Model is recovering from an out-of-memory failure. Retry shortly."

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

  /**
   * Pinned secondary — a second resident model loaded alongside [defaultModel]
   * any model type; routable by name via the request's model field). Unlike [defaultModel] it is exempt from keep-alive
   * idle unload and from reload teardown, so e.g. an embedding model stays warm
   * while the chat model is swapped. Null when no secondary is loaded.
   */
  @Volatile var secondaryModel: Model? = null

  /**
   * Generation counter guarding concurrent secondary loads: a load publishes only
   * if its generation is still current, mirroring ServerModelLoader's load-generation
   * pattern. Bumped on every [loadSecondaryModel] start and on [destroy].
   */
  private val secondaryLoadGeneration = java.util.concurrent.atomic.AtomicLong(0)

  /** Name of a secondary currently initializing (null = no load in flight). */
  private val secondaryLoadingName = java.util.concurrent.atomic.AtomicReference<String?>(null)

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
  private data class EmergencyUnloadState(
    val model: Model,
    val cleanupReady: CountDownLatch,
    val cleanupFinished: CountDownLatch,
  )
  private var emergencyUnloadState: EmergencyUnloadState? = null

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
      // Invalidate any in-flight secondary load so it cleans up after itself
      // instead of publishing a model onto a dead lifecycle.
      secondaryLoadGeneration.incrementAndGet()
      secondaryLoadingName.set(null)
      ServerMetrics.updateSecondaryModelName(null)
      // Service shutdown has already stopped request admission and interrupted
      // inference, so a deferred OOM cleanup must not remain blocked on old leases.
      emergencyUnloadState?.cleanupReady?.countDown()
    }
    lifecycleScope.cancel()
  }

  /**
   * Admit one model-using HTTP request. The lease must cover response writing,
   * including the complete SSE writer lifetime, not only handler construction.
   */
  internal fun tryAcquireRequestAdmission(): RequestAdmission? {
    synchronized(keepAliveLock) {
      check(!isDestroyed) { "Model lifecycle is destroyed" }
      if (emergencyUnloadState != null) return null
      cancelKeepAliveTimer()
      activeRequestAdmissions++
    }
    return RequestAdmission(::releaseRequestAdmission)
  }

  private fun releaseRequestAdmission() {
    synchronized(keepAliveLock) {
      check(activeRequestAdmissions > 0) { "No active model request admission to release" }
      activeRequestAdmissions--
      if (activeRequestAdmissions == 0) {
        val emergency = emergencyUnloadState
        if (emergency != null) {
          if (!ServerMetrics.isInferring.value) emergency.cleanupReady.countDown()
        } else if (!isDestroyed) {
          resetKeepAliveTimer()
        }
      }
    }
  }

  internal fun activeRequestAdmissionCount(): Int =
    synchronized(keepAliveLock) { activeRequestAdmissions }

  /** Waits off the main thread until an idle-unloaded Engine has finished closing. */
  internal fun awaitIdleCleanup() {
    val cleanup = idleCleanupFinished ?: return
    awaitLatchUninterruptibly(cleanup)
  }

  internal fun hasActiveIdleCleanup(): Boolean = (idleCleanupFinished?.count ?: 0) > 0

  /**
   * Emergency unload after an OutOfMemoryError on the serving path.
   *
   * Restores the invariant that all [defaultModel] writes hold [keepAliveLock] —
   * this must never be done by mutating the field directly from an HTTP coroutine.
   * The state transition mirrors the idle unload: null the reference, record the
   * unloaded marker so the next admitted request auto-reloads via
   * [reloadModelFromIdle], and publish the cleanup latch so a concurrent reloader
   * waits for `Engine.close()` instead of racing it.
   *
   * The model is unpublished immediately so no new work can select it. Existing
   * request admissions keep their lease and finish without concurrent teardown;
   * one process-wide cleanup starts after the last lease releases. New admissions
   * receive a retryable rejection until cleanup completes.
   */
  fun emergencyUnloadOnOom() {
    var secondary: Model? = null
    val emergency = synchronized(keepAliveLock) {
      if (isDestroyed || emergencyUnloadState != null) return
      val model = defaultModel ?: return
      // Free the secondary's RAM too: under OOM pressure the smaller resident
      // engine is the cheap one to drop, and it restores itself on next load.
      secondary = secondaryModel
      Log.w(TAG, "OOM: emergency-unloading model ${model.name}" +
        (if (secondary != null) " and secondary ${secondary.name}" else ""))
      keepAliveUnloadedRef.set(model.name to model.prefsKey)
      defaultModel = null
      modelCache.remove(model.name, model)
      if (secondary != null) {
        secondaryModel = null
        modelCache.remove(secondary.name, secondary)
        ServerPrefs.setSecondaryModelName(context, null)
        secondaryLoadGeneration.incrementAndGet()
        // Service keeps running after OOM — a stale loading flag would wedge
        // all future secondary loads with "already loading".
        secondaryLoadingName.set(null)
        ServerMetrics.updateSecondaryModelName(null)
      }
      ServerMetrics.onModelIdleUnloaded()
      val cleanupReady = CountDownLatch(1)
      val cleanupFinished = CountDownLatch(1)
      idleCleanupFinished = cleanupFinished
      EmergencyUnloadState(model, cleanupReady, cleanupFinished).also { state ->
        emergencyUnloadState = state
        if (activeRequestAdmissions == 0 && !ServerMetrics.isInferring.value) {
          state.cleanupReady.countDown()
        }
      }
    }

    // Queue cleanup immediately on the process-wide native cleanup chain. It waits
    // for the final pre-OOM admission, and survives service-scope cancellation.
    ServerCleanupCoordinator.enqueueCleanup("OlliteRT-OomCleanup") {
      awaitLatchUninterruptibly(emergency.cleanupReady)
      try {
        ServerLlmModelHelper.safeCleanup(emergency.model)
        secondary?.let { ServerLlmModelHelper.safeCleanup(it) }
      } finally {
        synchronized(keepAliveLock) {
          if (emergencyUnloadState === emergency) emergencyUnloadState = null
        }
        emergency.cleanupFinished.countDown()
      }
    }
  }

  private fun awaitLatchUninterruptibly(latch: CountDownLatch) {
    var wasInterrupted = false
    while (latch.count > 0) {
      try {
        latch.await()
      } catch (_: InterruptedException) {
        wasInterrupted = true
      }
    }
    if (wasInterrupted) Thread.currentThread().interrupt()
  }

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
   * Initialize the LiteRT engine for [model] using persisted settings: vision
   * support follows the eager-vision pref, audio follows model capability, and
   * the system instruction is built from the model's stored prompt.
   *
   * Returns the engine error message ("" on success). On success also sets
   * [Model.initializedWithVision] to match the requested capability.
   *
   * Shared by the initial load path (ServerModelLoader) and keep-alive
   * reload-from-idle so capability resolution cannot drift between them.
   * Deliberately distinct from [InferenceModelPreparer.reinitIfNeeded], which
   * serves request-scoped vision/audio upgrades with config overrides.
   */
  fun initializeEngine(model: Model): String {
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
    if (initErr.isEmpty()) {
      model.initializedWithVision = supportImage
    }
    return initErr
  }

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
      ServerMetrics.onKeepAliveReloadStarted()
      try {
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
        val initErr = initializeEngine(model)
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
        // The Logs timer must stop on success, failure, early return, or exception.
        ServerMetrics.onKeepAliveReloadFinished()
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
    val allowlist = modelCatalogMerger.load()
    val allowlistMatch = allowlist.firstOrNull { it.name.equals(name, ignoreCase = true) }
    val model = if (allowlistMatch != null) {
      val built = ModelFactory.buildAllowedModel(allowlistMatch, importsDir)
      built.preProcess()
      built
    } else {
      // 2. Fall back to imported models from DataStore
      val importedMatch = try {
        readImportedModels().firstOrNull {
          it.fileName.equals(name, ignoreCase = true) ||
            ModelFactory.importedModelId(it.fileName).equals(name, ignoreCase = true)
        }
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

  /**
   * Finds any downloaded model other than [excludeName] whose files resolve on disk.
   *
   * Self-healing fallback for auto-start paths: when the configured default model
   * has been deleted or renamed, boot/launch auto-start would otherwise fail and
   * leave the server down until a human intervenes — the exact failure mode of a
   * phone left in a drawer for weeks. Returns null when nothing usable exists.
   */
  fun pickFallbackModel(excludeName: String?): Model? {
    val externalDir = context.getExternalFilesDir(null) ?: return null
    val importsDir = File(externalDir, IMPORTS_DIR)

    fun resolvesToDisk(model: Model): Boolean = try {
      File(model.getPath(context)).exists()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to resolve path for fallback candidate '${model.name}'", e)
      false
    }

    // Same precedence as pickModelByName: allowlist entries first, then imported models.
    for (entry in modelCatalogMerger.load()) {
      if (entry.name.equals(excludeName, ignoreCase = true)) continue
      val candidate = try {
        ModelFactory.buildAllowedModel(entry, importsDir).also { it.preProcess() }
      } catch (e: Exception) {
        Log.w(TAG, "Skipping allowlist entry '${entry.name}' in fallback scan", e)
        continue
      }
      resolveOnDiskVersion(candidate, externalDir)
      if (resolvesToDisk(candidate)) {
        ModelFactory.restoreInferenceConfig(context, candidate)
        return candidate
      }
    }
    val importedModels = try {
      readImportedModels()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to read imported models during fallback scan", e)
      emptyList()
    }
    for (info in importedModels) {
      if (
        info.fileName.equals(excludeName, ignoreCase = true) ||
        ModelFactory.importedModelId(info.fileName).equals(excludeName, ignoreCase = true)
      ) continue
      val candidate = try {
        ModelFactory.buildImportedModel(info)
      } catch (e: Exception) {
        Log.w(TAG, "Skipping imported model '${info.fileName}' in fallback scan", e)
        continue
      }
      if (resolvesToDisk(candidate)) {
        ModelFactory.restoreInferenceConfig(context, candidate)
        return candidate
      }
    }
    return null
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
      val legacyStorageKey = if (active.imported) {
        BridgeUtils.normalizeModelKey(active.storageFileName)
      } else {
        null
      }
      if (requestedKey == activeKey || requestedKey == legacyStorageKey) {
        return ModelSelection.Ok(active)
      }
      // The requested model doesn't match the active model — check the resident
      // second model. A pinned second model is reachable by name from any
      // inference route (OpenAI-style model= routing).
      val secondary = secondaryModel
      if (secondary != null) {
        val secondaryKey = BridgeUtils.normalizeModelKey(secondary.name)
        val secondaryLegacy = if (secondary.imported) {
          BridgeUtils.normalizeModelKey(secondary.storageFileName)
        } else {
          null
        }
        if (requestedKey == secondaryKey || requestedKey == secondaryLegacy) {
          return ModelSelection.Ok(secondary)
        }
      }
      // The requested model matches neither resident model.
      return ModelSelection.Error(
        400,
        "Model '${requested}' is not loaded. Currently loaded: '${active.name}'" +
          (secondary?.let { " and '${it.name}'" } ?: "") +
          ". Please select a loaded model in your client or load the requested model on the device first."
      )
    }
  }

  // ── Second model (second resident model; any model type, routable by name) ─

  /**
   * Resolves the embedding model for an /v1/embeddings request. Looks at BOTH
   * resident models (secondary first, then default) so a chat model can be the
   * active default while a pinned embedding model serves embeddings.
   *
   * [requestedModel] is the client's `model` field: blank/"local"/"default"
   * resolves to whichever loaded model has an embedding engine; any other value
   * must name-match a loaded embedding model (same normalization rules as
   * [selectModel]).
   */
  fun selectEmbeddingModel(requestedModel: String?): ModelSelection {
    synchronized(keepAliveLock) {
      val candidates = listOfNotNull(secondaryModel, defaultModel)
      if (candidates.isEmpty()) {
        return ModelSelection.Error(
          503,
          "No model is loaded. Load an embedding model first (POST /v1/server/models/load)."
        )
      }
      val requested = requestedModel?.trim().orEmpty()
      if (requested.isEmpty() || requested.equals("local", ignoreCase = true) ||
        requested.equals("default", ignoreCase = true)
      ) {
        val any = candidates.firstOrNull { (it.instance as? EmbeddingEngine) != null }
          ?: return ModelSelection.Error(
            400,
            "No embedding model is loaded. Loaded: " +
              candidates.joinToString { it.name } +
              ". Load one via POST /v1/server/models/load."
          )
        return ModelSelection.Ok(any)
      }
      val requestedKey = BridgeUtils.normalizeModelKey(requested)
      for (candidate in candidates) {
        if ((candidate.instance as? EmbeddingEngine) == null) continue
        val key = BridgeUtils.normalizeModelKey(candidate.name)
        val legacyStorageKey = if (candidate.imported) {
          BridgeUtils.normalizeModelKey(candidate.storageFileName)
        } else null
        if (requestedKey == key || requestedKey == legacyStorageKey) {
          return ModelSelection.Ok(candidate)
        }
      }
      return ModelSelection.Error(
        400,
        "Model '$requested' is not a loaded embedding model. Loaded: " +
          candidates.joinToString { it.name }
      )
    }
  }

  /** Probes the model file's LiteRT model type; false on any probe failure. */
  @OptIn(ExperimentalApi::class)
  private fun isEmbeddingModelFile(model: Model): Boolean {
    return try {
      val info = ModelInfo.from(model.getPath(context))
      val isEmbedding = info.modelType == ModelType.EMBEDDING
      try { info.close() } catch (_: Exception) { }
      isEmbedding
    } catch (e: Exception) {
      Log.w(TAG, "ModelInfo probe failed for secondary check on '${model.name}': ${e.message}")
      false
    }
  }

  /**
   * Loads [name] as the pinned secondary alongside the active chat model.
   * Embedding-only in v1. Blocking: native engine init runs 10-60s and must be
   * called from a background thread (route handler thread is fine — it is a CIO
   * worker, not the main looper).
   *
   * Native init runs OUTSIDE [keepAliveLock] (mirroring ServerModelLoader's
   * initial-load path) so a secondary load never blocks chat request selection;
   * state publication happens under the lock and is guarded by the load
   * generation so a superseded load cleans up after itself.
   *
   * Returns an error message, or "" on success / already loaded.
   */
  fun loadSecondaryModel(name: String): String {
    val requestedKey = BridgeUtils.normalizeModelKey(name)
    val model: Model
    val generation: Long
    synchronized(keepAliveLock) {
      if (isDestroyed) return "Server is shutting down"
      val existing = secondaryModel
      if (existing != null && existing.instance != null) {
        val existingKey = BridgeUtils.normalizeModelKey(existing.name)
        return if (existingKey == requestedKey) ""
        else "Second model '${existing.name}' is already loaded — unload it first (POST /v1/server/models/unload)"
      }
      val inFlight = secondaryLoadingName.get()
      if (inFlight != null) {
        return "Second model '$inFlight' is already loading — retry shortly"
      }
      val active = defaultModel
      if (active != null && BridgeUtils.normalizeModelKey(active.name) == requestedKey) {
        return "Model '$name' is the active chat model — the secondary must be a different model"
      }
      model = pickModelByName(name)
        ?: return "Model '$name' not found (not in the allowlist or imports registry)"
      secondaryLoadingName.set(model.name)
      generation = secondaryLoadGeneration.incrementAndGet()
    }
    try {
      val initErr = initializeEngine(model)
      if (initErr.isNotEmpty()) {
        ServerLlmModelHelper.safeCleanup(model)
        return "Second model load failed: $initErr"
      }
      synchronized(keepAliveLock) {
        if (isDestroyed) {
          ServerLlmModelHelper.safeCleanup(model)
          return "Server is shutting down"
        }
        if (secondaryLoadGeneration.get() != generation) {
          // A newer load request (or destroy) superseded this one.
          ServerLlmModelHelper.safeCleanup(model)
          return "Second model load superseded by a newer request — retry"
        }
        secondaryModel = model
        modelCache[model.name] = model
        ServerPrefs.setSecondaryModelName(context, model.name)
        ServerMetrics.updateSecondaryModelName(model.name)
      }
    } catch (e: Exception) {
      ServerLlmModelHelper.safeCleanup(model)
      return "Second model load failed: ${e.message}"
    } finally {
      // Clear the loading flag on EVERY path (success, init failure, destroy,
      // supersede). compareAndSet: only clear it if we still own it — a newer
      // load may have already replaced it with its own name under the lock.
      secondaryLoadingName.compareAndSet(model.name, null)
    }
    RequestLogStore.addEvent(
      "Second model loaded: ${model.name} (resident alongside active model)",
      modelName = model.name,
      category = EventCategory.MODEL,
    )
    return ""
  }

  /**
   * Unloads the pinned secondary. When [name] is given, unloads only if it
   * matches the current secondary. Returns an error message, or "" on success.
   */
  fun unloadSecondaryModel(name: String?): String {
    val model = synchronized(keepAliveLock) {
      val current = secondaryModel ?: return "No secondary is loaded"
      // Never close a native engine under in-flight requests: an embedding call
      // mid-compute would hit a freed engine. Embedding calls are short (~0.5s),
      // so a retryable rejection is the right call.
      if (activeRequestAdmissions > 0 || ServerMetrics.isInferring.value) {
        return "A request is in flight on the secondary — retry shortly"
      }
      if (name != null && name.isNotBlank()) {
        val key = BridgeUtils.normalizeModelKey(name)
        val currentKey = BridgeUtils.normalizeModelKey(current.name)
        val legacyStorageKey = if (current.imported) {
          BridgeUtils.normalizeModelKey(current.storageFileName)
        } else null
        if (key != currentKey && key != legacyStorageKey) {
          return "Second model is '${current.name}', not '$name'"
        }
      }
      secondaryModel = null
      modelCache.remove(current.name, current)
      current
    }
    ServerPrefs.setSecondaryModelName(context, null)
    ServerMetrics.updateSecondaryModelName(null)
    // Native close outside the lock — state transition is already published.
    ServerLlmModelHelper.safeCleanup(model)
    RequestLogStore.addEvent(
      "Second model unloaded: ${model.name}",
      modelName = model.name,
      category = EventCategory.MODEL,
    )
    return ""
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
