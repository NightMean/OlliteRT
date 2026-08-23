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

import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*

import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.OlliteRTApplication
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.data.ClientIpAccessPolicy
import com.ollitert.llm.server.data.DATASTORE_READ_TIMEOUT_MS
import com.ollitert.llm.server.data.EventCategory
import com.ollitert.llm.server.data.LOG_ERROR_PREVIEW_LONG_CHARS
import com.ollitert.llm.server.data.LogLevel
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_FILENAME
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelCatalogMerger
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.isSpeculativeDecodingEnabled
import com.ollitert.llm.server.data.isThinkingEnabled
import com.ollitert.llm.server.data.llmSupportAudio
import com.ollitert.llm.server.data.llmSupportImage
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service exposing an OpenAI-compatible HTTP API for local LLM inference.
 * See [RouteResolver] for the full endpoint table.
 */
class ServerService : Service() {

  private var server: KtorServer? = null
  private var inferenceRunner: InferenceRunner? = null
  private var inferenceExecutor: java.util.concurrent.ExecutorService? = null
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val requestCounter = AtomicLong(0)
  /** Incremented each time a new model load is initiated; stale warmup threads check this to bail out. */
  private val loadGeneration = AtomicLong(0)
  /** Shared lock for serializing inference and config writes — passed to InferenceRunner and Server.
   *  Must always be acquired AFTER keepAliveLock (in ModelLifecycle), never before it. */
  private val inferenceLock = Any()
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var loadJob: Job? = null

  // Notification manager — encapsulates notification creation, updates, and intent building.
  private val notificationManager by lazy { ServerNotificationManager(this) }

  // Model lifecycle: keep-alive, model selection, image decoding — see ModelLifecycle.kt
  private lateinit var modelLifecycle: ModelLifecycle

  // Convenience accessors for model state (delegates to modelLifecycle)
  private inline var defaultModel: Model?
    get() = modelLifecycle.defaultModel
    set(value) { modelLifecycle.defaultModel = value }
  private inline val modelCache get() = modelLifecycle.modelCache
  private inline var keepAliveUnloadedModelName: String?
    get() = modelLifecycle.keepAliveUnloadedModelName
    set(value) { modelLifecycle.setKeepAliveUnloadedModel(value, null) }

  // Wake lock and WiFi lock helper for continuous 24/7 background serving.
  private lateinit var wakeLockHelper: ServerWakeLockHelper

  private lateinit var modelCatalogMerger: ModelCatalogMerger

  // Model loader helper — encapsulates background model loading, warmup, and failure recovery.
  private lateinit var modelLoader: ServerModelLoader

  override fun onCreate() {
    super.onCreate()
    activeInstance = this
    try {
      // Access DataStoreRepository via Hilt EntryPoint so imported models can be resolved
      // when starting the server. The DataStore singleton is managed by Hilt; creating a
      // second instance would corrupt the protobuf file.
      val dataStoreRepo = try {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
          applicationContext, OlliteRTApplication.DataStoreEntryPoint::class.java
        )
        entryPoint.dataStoreRepository()
      } catch (e: Exception) {
        Log.w(TAG, "Failed to access DataStoreRepository — imported models won't be loadable", e)
        null
      }
      modelCatalogMerger = ModelCatalogMerger(
        externalFilesDir = getExternalFilesDir(null),
        appVersionName = BuildConfig.VERSION_NAME,
        assetReader = {
          try { assets.open(MODEL_ALLOWLIST_FILENAME).use { it.reader().readText() } } catch (e: Exception) { Log.w(TAG, "Failed to read bundled $MODEL_ALLOWLIST_FILENAME", e); null }
        },
        enabledCacheFilenames = {
          try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { withTimeout(DATASTORE_READ_TIMEOUT_MS) { dataStoreRepo?.readRepositories() } }
              ?.filter { it.enabled }
              ?.map { it.cacheFilename }
              ?.toSet()
          } catch (e: Exception) { Log.w(TAG, "Failed to read enabled repository filenames from DataStore", e); null }
        },
        onError = { source, ex -> Log.w(TAG, "Allowlist parse error ($source)", ex) },
      )
      modelLifecycle = ModelLifecycle(
        context = this,
        modelCatalogMerger = modelCatalogMerger,
        readImportedModels = {
          try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { withTimeout(DATASTORE_READ_TIMEOUT_MS) { dataStoreRepo?.readImportedModels() } } ?: emptyList()
          } catch (e: Exception) { Log.w(TAG, "Failed to read imported models from DataStore", e); emptyList() }
        },
      )
      modelLoader = ServerModelLoader(
        context = this,
        modelLifecycle = modelLifecycle,
        notificationManager = notificationManager,
        getInferenceRunner = { inferenceRunner },
        loadGeneration = loadGeneration,
        pendingReloadAfterLoad = pendingReloadAfterLoad,
        onModelPublished = { model ->
          defaultModel = model
          modelCache[model.name] = model
        },
        onOomRecover = {
          synchronized(modelLifecycle.keepAliveLock) { defaultModel = null }
          modelCache.clear()
        },
        emitDebugStackTrace = { t, src, name -> emitDebugStackTrace(t, src, name) },
      )
      wakeLockHelper = ServerWakeLockHelper(this)
      NotificationHelper.createChannel(this)
      notificationManager.checkCorruptedDataStores()
    } catch (e: Exception) {
      Log.e(TAG, "Service initialization failed — stopping immediately", e)
      stopSelf()
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Guard: if onCreate() failed partway through, the service is in a zombie state.
    // Stop immediately to prevent UninitializedPropertyAccessException crashes.
    if (!::modelLifecycle.isInitialized) {
      Log.e(TAG, "Service not initialized — stopping")
      stopSelf()
      return START_NOT_STICKY
    }

    // Handle stop action from notification
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }

    // Handle keep-alive timer reset — lightweight action, no foreground notification needed.
    // Sent by SettingsScreen when the user changes keep_alive settings while the server is running.
    if (intent?.action == ACTION_RESET_KEEP_ALIVE) {
      resetKeepAliveTimer()
      return START_STICKY
    }

    // System auto-restart after crash: intent is null or has no model name and no action.
    // Don't call startForeground() — on Android 12+ it throws
    // ForegroundServiceStartNotAllowedException when the app is in the background.
    // Just stop immediately to avoid a crash loop.
    if (intent == null || (intent.action == null && intent.getStringExtra(EXTRA_MODEL_NAME) == null)) {
      Log.i(TAG, "No intent or model specified — stopping to avoid crash loop")
      stopSelf()
      return START_NOT_STICKY
    }

    notificationManager.startForegroundPlaceholder(this)
    acquireWakeLocks()

    // Handle reload action: clean up current model first, then proceed with normal start.
    // Unlike a full stop, reload emits "Model restart requested" + "Unloading model" instead
    // of "Server stopped", because the server will immediately start again.
    if (intent.action == ACTION_RELOAD) {
      handleReloadCleanup()
    }

    val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
    val requestedModelName = intent.getStringExtra(EXTRA_MODEL_NAME)

    // If no explicit model was requested, this is likely a system restart after a crash.
    // Don't auto-load the last model to avoid crash loops (e.g. from OOM).
    // Auto-start on boot is handled separately by BootReceiver which passes EXTRA_MODEL_NAME.
    if (requestedModelName == null) {
      Log.i(TAG, "No model specified in intent — not auto-loading to avoid potential crash loop")
      stopSelf()
      return START_NOT_STICKY
    }

    val startSource = intent.getStringExtra(EXTRA_START_SOURCE)

    // ── Ktor server setup & network resolution ─────────────────────────────
    val (networkConfig, configError) = ServerNetworkMonitor.resolveConfig(this)
    if (networkConfig == null || configError != null) {
      reportNetworkConfigFailure(configError ?: getString(R.string.error_invalid_client_ip_policy), requestedModelName)
      return START_NOT_STICKY
    }

    val notifState = notificationManager.buildNotificationIntents(networkConfig.advertisedHost, networkConfig.isLoopbackOnly, port)

    NotificationHelper.update(
      context = this,
      title = getString(R.string.notif_loading_model_title, requestedModelName),
      text = getString(R.string.notif_loading_model_body),
      contentIntent = notifState.contentIntent,
      showProgress = true,
    )

    if (!startHttpServer(port, networkConfig.bindHost, networkConfig.accessPolicy, requestedModelName)) return START_NOT_STICKY

    // ── Model resolution + initialization (off main thread) ─────────────────
    // pickModelByName triggers runBlocking DataStore reads inside
    // AllowlistLoader.enabledCacheFilenames and ModelLifecycle.readImportedModels.
    // Running on Dispatchers.IO avoids ANR risk on the main thread.
    val thisGeneration = loadGeneration.incrementAndGet()
    loadJob?.cancel()
    loadJob = serviceScope.launch {
      val model = pickModelByName(requestedModelName)
      if (model == null) {
        val sourcePrefix = when (startSource) {
          SOURCE_BOOT -> getString(R.string.error_autostart_boot_prefix)
          SOURCE_LAUNCH -> getString(R.string.error_autostart_launch_prefix)
          else -> ""
        }
        val msg = sourcePrefix + getString(R.string.error_model_not_found, requestedModelName)
        Log.e(TAG, "Model '$requestedModelName' not found — cannot start server (source=$startSource)")
        ServerMetrics.onServerError(msg)
        ServerMetrics.incrementErrorCount(ErrorCategory.MODEL_LOAD)
        RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = requestedModelName, category = EventCategory.MODEL)
        pendingConfigOverrides.set(null)
        stopSelf()
        return@launch
      }
      // Apply pending config overrides from the reload caller (e.g. InferenceSettingsSheet).
      // configValues is written from 3 paths: here (initial load overrides),
      // updateConfigValues() (runtime settings change), and reload() which triggers this path again.
      // All paths are serialized via @Synchronized companion methods or the load coroutine.
      // getAndSet(null) is atomic — prevents a concurrent reload's write from being lost.
      pendingConfigOverrides.getAndSet(null)?.let { overrides ->
        model.configValues = overrides.toMap()
        Log.i(TAG, "Applied ${overrides.size} config overrides from reload caller")
      }
      // Verify model files actually exist on disk.
      val modelPath = model.getPath(context = this@ServerService)
      if (!File(modelPath).exists()) {
        val sourcePrefix = when (startSource) {
          SOURCE_BOOT -> getString(R.string.error_autostart_boot_prefix)
          SOURCE_LAUNCH -> getString(R.string.error_autostart_launch_prefix)
          else -> ""
        }
        val msg = sourcePrefix + getString(R.string.error_model_file_missing)
        Log.e(TAG, "Model files not found at $modelPath for ${model.name} — cannot start server (source=$startSource)")
        ServerMetrics.onServerError(msg)
        ServerMetrics.incrementErrorCount(ErrorCategory.MODEL_LOAD)
        RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = model.name, category = EventCategory.MODEL)
        stopSelf()
        return@launch
      }

      ServerMetrics.onServerStarting(port, model.name)
      ServerMetrics.setActiveModelSize(model.totalBytes)
      RequestLogStore.addEvent("Loading model: ${model.name}", modelName = model.name, category = EventCategory.MODEL)

      loadModelOnThread(model, thisGeneration, notifState)
    }

    return START_STICKY
  }

  /** Acquires CPU + WiFi wake locks for 24/7 server operation. */
  private fun acquireWakeLocks() {
    if (::wakeLockHelper.isInitialized) {
      wakeLockHelper.acquire()
    }
  }

  private fun reportNetworkConfigFailure(message: String, requestedModelName: String) {
    Log.e(TAG, message)
    ServerMetrics.onServerError(message)
    ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
    RequestLogStore.addEvent(
      message,
      level = LogLevel.ERROR,
      modelName = requestedModelName,
      category = EventCategory.SERVER,
    )
    stopSelf()
  }

  /**
   * Creates the Ktor HTTP server and inference pipeline. Returns false if the server
   * failed to bind (caller should return START_NOT_STICKY).
   */
  private fun startHttpServer(
    port: Int,
    bindHost: String,
    clientIpAccessPolicy: ClientIpAccessPolicy,
    requestedModelName: String,
  ): Boolean {
    val probeError = ServerNetworkMonitor.probePortBind(this, bindHost, port)
    if (probeError != null) {
      ServerMetrics.onServerError(probeError)
      ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
      RequestLogStore.addEvent(probeError, level = LogLevel.ERROR, modelName = requestedModelName, category = EventCategory.SERVER)
      stopSelf()
      return false
    }

    server?.stop(gracePeriodMillis = 0, timeoutMillis = 0)
    inferenceExecutor?.shutdownNow()

    val pipeline = ServerServicePipelineFactory.createPipeline(
      context = this,
      port = port,
      bindHost = bindHost,
      clientIpAccessPolicy = clientIpAccessPolicy,
      modelLifecycle = modelLifecycle,
      json = json,
      inferenceLock = inferenceLock,
      nextRequestId = { nextRequestId() },
      logEvent = { msg -> logEvent(msg) },
      emitDebugStackTrace = { t, src, name -> emitDebugStackTrace(t, src, name) },
      buildSystemInstruction = { name -> buildSystemInstruction(name) },
    )
    inferenceExecutor = pipeline.executor
    inferenceRunner = pipeline.runner
    server = pipeline.server

    return try {
      Log.i(
        TAG,
        "Starting HTTP server on $bindHost:$port with client IP policy " +
          "${clientIpAccessPolicy.mode.preferenceValue} (${clientIpAccessPolicy.ruleCount} rules)",
      )
      server?.start()
      true
    } catch (e: Exception) {
      val reason = if (e is java.net.BindException || e.message?.contains("Address already in use") == true)
        getString(R.string.error_port_in_use, port) else (e.message?.take(LOG_ERROR_PREVIEW_LONG_CHARS) ?: getString(R.string.error_unknown))
      val msg = getString(R.string.error_server_failed_to_start, reason)
      Log.e(TAG, msg, e)
      ServerMetrics.onServerError(msg)
      ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = requestedModelName, category = EventCategory.SERVER)
      stopSelf()
      false
    }
  }

  /**
   * Cleans up the current model and server state before a reload.
   * Called from [onStartCommand] when [ACTION_RELOAD] is received.
   */
  private fun handleReloadCleanup() {
    ServerReloadCoordinator.executeReloadCleanup(
      modelLifecycle = modelLifecycle,
      loadGeneration = loadGeneration,
      server = server,
      loadJob = loadJob,
      inferenceExecutor = inferenceExecutor,
    )
    loadJob = null
    inferenceExecutor = null
  }

  /**
   * Loads a model on a background thread: storage check, cleanup latch wait,
   * warmup/initialize, metrics, notification update.
   *
   * Delegated to [ServerModelLoader].
   */
  private fun loadModelOnThread(
    model: Model,
    thisGeneration: Long,
    notifState: LoadNotificationState,
  ) {
    modelLoader.loadModelOnThread(model, thisGeneration, notifState)
  }

  @Suppress("DEPRECATION") // onTrimMemory deprecated in API 34, but onTrimMemory is still called by the framework
  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    // TRIM_MEMORY_RUNNING_CRITICAL = 15: the system is critically low on memory and the process
    // is running. This fires just before the OOM killer would kill the process. Log it so users
    // can see "System memory pressure" in the Logs tab before a crash, rather than the app dying
    // silently. The GC hint doesn't free the model's native memory (which is the bulk of our
    // footprint) but helps release JVM wrapper objects sooner.
    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
      RequestLogStore.addEvent(
        "System memory pressure (critical)",
        modelName = defaultModel?.name,
        category = EventCategory.SERVER,
        level = LogLevel.WARNING,
      )
      System.gc()
      // Shed 50% of in-memory log entries to free JVM heap before OOM killer strikes.
      RequestLogStore.trimToPercentage(50)
    }
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    stopSelf()
  }

  override fun onDestroy() {
    activeInstance = null
    cancelKeepAliveTimer()
    keepAliveUnloadedModelName = null
    // Invalidate any in-flight warmup thread so it won't transition to RUNNING after we stop
    loadGeneration.incrementAndGet()
    val previousLoadJob = loadJob
    previousLoadJob?.cancel()
    loadJob = null
    // Signal request-scoped controls first so native done callbacks cannot win success.
    RequestLogStore.cancelAllPending()
    server?.stop(gracePeriodMillis = 0, timeoutMillis = 0)
    // Cancel any in-flight inference so the native JNI call returns quickly.
    // Without this, shutdownNow() only calls Thread.interrupt() which has no
    // effect on blocking native code — the 5s await can expire with the thread
    // still inside LiteRT SDK.
    defaultModel?.let { ServerLlmModelHelper.stopResponse(it) }
    val executor = inferenceExecutor
    executor?.shutdownNow()
    inferenceExecutor = null
    val modelName = defaultModel?.name

    // Collect models that need native cleanup (Engine + Conversation close).
    // These operations can take seconds for multi-GB models and must NOT run on the main
    // thread — doing so causes an ANR ("Input dispatching timed out") when the user taps
    // Stop Server, because onDestroy runs on the main thread.
    val modelsToCleanUp = linkedSetOf<Model>()
    synchronized(modelLifecycle.keepAliveLock) {
      defaultModel?.let(modelsToCleanUp::add)
      defaultModel = null
      modelsToCleanUp.addAll(modelCache.values.filter { it.instance != null })
      modelCache.clear()
    }

    if (previousLoadJob != null || executor != null || modelsToCleanUp.isNotEmpty() ||
      modelLifecycle.hasActiveIdleCleanup()
    ) {
      ServerCleanupCoordinator.enqueueCleanup("OlliteRT-ModelCleanup") {
        modelLifecycle.awaitIdleCleanup()
        previousLoadJob?.let { job -> kotlinx.coroutines.runBlocking { job.join() } }
        if (executor?.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS) == false) {
          Log.w(TAG, "Inference executor did not terminate during destroy cleanup")
        }
        for (model in modelsToCleanUp) {
          ServerLlmModelHelper.safeCleanup(model)
        }
        System.gc()
      }
    }

    notificationManager.clear()
    pendingReloadAfterLoad.set(null)
    ServerMetrics.onServerStopped()
    if (modelName != null) {
      RequestLogStore.addEvent("Server stopped", modelName = modelName, category = EventCategory.SERVER)
    }
    if (ServerPrefs.isClearLogsOnStop(this)) {
      RequestLogStore.clear()
    }
    // Release wake lock if still held (e.g. service killed mid-inference)
    if (::wakeLockHelper.isInitialized) {
      wakeLockHelper.release()
    }
    try {
      val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        applicationContext, OlliteRTApplication.PersistenceEntryPoint::class.java
      )
      entryPoint.requestLogPersistence().shutdown()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to shut down RequestLogPersistence", e)
    }
    modelLifecycle.destroy()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  // Model lifecycle methods — delegated to ModelLifecycle.kt
  private fun pickModelByName(name: String) = modelLifecycle.pickModelByName(name)
  private fun cancelKeepAliveTimer() = modelLifecycle.cancelKeepAliveTimer()
  private fun resetKeepAliveTimer() = modelLifecycle.resetKeepAliveTimer()
  private fun buildSystemInstruction(modelPrefsKey: String) = modelLifecycle.buildSystemInstruction(modelPrefsKey)

  private fun nextRequestId(): String {
    ServerMetrics.incrementRequestCount()
    if (ServerPrefs.isNotifShowRequestCount(this)) {
      notificationManager.refreshRunning()
    }
    return "r${requestCounter.incrementAndGet()}"
  }

  private fun logEvent(message: String) {
    Log.i(TAG, "LLM_HTTP $message")
  }

  /**
   * Emits a DEBUG-level log entry with the full stack trace of a caught [Throwable].
   * Only logs when verbose debug mode is enabled. Called from model load, inference
   * gateway catch blocks, and the serve() catch-all to preserve stack traces that
   * would otherwise be reduced to just [Throwable.message].
   *
   * @param t The caught throwable
   * @param source Identifier for which catch block produced this (e.g. "model_load", "execute", "ktor_serve_catch_all")
   * @param modelName Optional model name for log entry context
   */
  private fun emitDebugStackTrace(t: Throwable, source: String, modelName: String? = null) {
    if (!ServerPrefs.isVerboseDebugEnabled(this)) return
    val traceText = "Source: $source\n${t.stackTraceToString()}"
    RequestLogStore.addEvent(
      "Exception in $source — stack trace",
      level = LogLevel.DEBUG,
      modelName = modelName,
      category = EventCategory.SERVER,
      body = traceText,
    )
  }

  companion object {
    private const val TAG = "OlliteRT.Service"
    const val EXTRA_PORT = "extra_port"
    const val EXTRA_MODEL_NAME = "extra_model_name"
    /** Optional: identifies what triggered the start (e.g. "boot", "launch") for better error messages. */
    const val EXTRA_START_SOURCE = "extra_start_source"
    const val SOURCE_BOOT = "boot"
    const val SOURCE_LAUNCH = "launch"
    const val DEFAULT_PORT = com.ollitert.llm.server.data.DEFAULT_PORT
    const val ACTION_STOP = "com.ollitert.llm.server.STOP_SERVER"
    const val ACTION_RELOAD = "com.ollitert.llm.server.RELOAD_SERVER"
    const val ACTION_RESET_KEEP_ALIVE = "com.ollitert.llm.server.RESET_KEEP_ALIVE"

    fun start(context: Context, port: Int = DEFAULT_PORT, modelName: String? = null, source: String? = null): Boolean {
      val intent = Intent(context, ServerService::class.java).apply {
        putExtra(EXTRA_PORT, port)
        if (modelName != null) putExtra(EXTRA_MODEL_NAME, modelName)
        if (source != null) putExtra(EXTRA_START_SOURCE, source)
      }
      return try {
        context.startForegroundService(intent)
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start service", e)
        false
      }
    }

    fun stop(context: Context) {
      try {
        context.stopService(Intent(context, ServerService::class.java))
      } catch (e: Exception) {
        Log.w(TAG, "Failed to stop service", e)
      }
    }

    /**
     * Pending config values to apply after the next reload creates a fresh model.
     * Set by [reload] before sending the intent, consumed in [onStartCommand].
     * Uses AtomicReference to prevent race conditions when two rapid reloads overwrite each other.
     */
    private val pendingConfigOverrides = java.util.concurrent.atomic.AtomicReference<Map<String, Any>?>(null)

    /**
     * Queued reload request to execute after the current model finishes loading.
     * Set by [queueReloadAfterLoad] when the user changes reinit-requiring settings
     * while a model is still loading. Consumed in the warmup thread after [onServerRunning].
     */
    internal data class PendingReload(val port: Int, val modelName: String, val configValues: Map<String, Any>?)
    /** Atomic to prevent lost updates when the UI thread writes a new reload while the warmup thread reads and clears. */
    private val pendingReloadAfterLoad = java.util.concurrent.atomic.AtomicReference<PendingReload?>(null)

    /**
     * Queue a reload to execute automatically after the current model finishes loading.
     * If the model is not currently loading, this is a no-op — use [reload] instead.
     */
    fun queueReloadAfterLoad(port: Int, modelName: String, configValues: Map<String, Any>?) {
      pendingReloadAfterLoad.set(PendingReload(port, modelName, configValues))
    }

    fun reload(context: Context, port: Int = DEFAULT_PORT, modelName: String? = null, configValues: Map<String, Any>? = null): Boolean {
      pendingConfigOverrides.set(configValues)
      val intent = Intent(context, ServerService::class.java).apply {
        action = ACTION_RELOAD
        putExtra(EXTRA_PORT, port)
        if (modelName != null) putExtra(EXTRA_MODEL_NAME, modelName)
      }
      return try {
        context.startForegroundService(intent)
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to reload service", e)
        false
      }
    }

    /**
     * Tell the running service to re-read keep_alive prefs and reschedule (or cancel) the
     * idle-unload timer. Called from SettingsScreen after saving keep_alive changes.
     * Uses [Context.startService] (not startForegroundService) because the service is already
     * in the foreground — this just delivers the intent without triggering a new foreground start.
     */
    fun resetKeepAliveTimer(context: Context) {
      try {
        context.startService(
          Intent(context, ServerService::class.java).apply { action = ACTION_RESET_KEEP_ALIVE }
        )
      } catch (e: Exception) {
        Log.w(TAG, "Failed to reset keep-alive timer — service may not be running", e)
      }
    }

    /** Applies validated client admission rules without restarting the listener or model. */
    fun updateClientIpAccessPolicy(policy: ClientIpAccessPolicy) {
      activeInstance?.server?.updateClientIpAccessPolicy(policy)
    }

    /**
     * Update config values on the running service's model without reloading.
     * Used for non-reinitialization config changes (temperature, topK, topP, etc.).
     */
    @Volatile
    private var activeInstance: ServerService? = null

    fun updateConfigValues(configValues: Map<String, Any>) {
      // TOCTOU: activeInstance may become null between this read and the synchronized block
      // if onDestroy runs concurrently. Consequence is benign — defaultModel will be null
      // inside the lock, so the ?.let is a no-op. Not worth adding a second lock layer for.
      val instance = activeInstance ?: return
      synchronized(instance.inferenceLock) {
        instance.defaultModel?.let { model ->
          model.configValues = configValues.toMap()
          // Update thinking/MTP state in metrics so the Status screen reflects the change
          ServerMetrics.setThinkingEnabled(model.isThinkingEnabled)
          ServerMetrics.setSpeculativeDecodingEnabled(model.isSpeculativeDecodingEnabled)
        }
      }
    }
  }
}
