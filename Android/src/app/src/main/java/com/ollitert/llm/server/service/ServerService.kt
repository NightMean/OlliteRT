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

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.MainActivity
import com.ollitert.llm.server.OlliteRTApplication
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.common.getWifiIpAddress
import com.ollitert.llm.server.data.CLEANUP_AWAIT_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_FILENAME
import com.ollitert.llm.server.data.MIN_STORAGE_FOR_MODEL_INIT_BYTES
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.bytesToMb
import com.ollitert.llm.server.data.llmSupportAudio
import com.ollitert.llm.server.data.llmSupportImage
import com.ollitert.llm.server.data.isThinkingEnabled
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.ServerService.Companion.queueReloadAfterLoad
import com.ollitert.llm.server.service.ServerService.Companion.reload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

  // Notification state — saved after warmup so we can refresh the notification with live request count.
  // @Volatile: written from background load thread, read from main thread for notification refresh.
  @Volatile private var notifContentIntent: PendingIntent? = null
  @Volatile private var notifStopIntent: PendingIntent? = null
  @Volatile private var notifCopyIntent: PendingIntent? = null
  @Volatile private var notifEndpointUrl: String? = null
  @Volatile private var notifModelName: String? = null

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

  /**
   * Partial wake lock held for the entire server lifetime to keep the CPU awake while serving.
   * Without this, Doze mode suspends CPU on a locked/idle device — making the HTTP server
   * unreachable between requests. Essential for "closet server" use cases where the device
   * sits idle with the screen off for days/weeks. Intentionally acquired without a timeout —
   * the server is designed to run 24/7, and the lock is released in onDestroy().
   */
  private var wakeLock: android.os.PowerManager.WakeLock? = null
  /**
   * WiFi lock held for the entire server lifetime to keep the WiFi radio active when the
   * screen is off. Many OEMs (Samsung, Xiaomi, Huawei) put WiFi into low-power mode when
   * the screen turns off — even with a partial wake lock held — making the HTTP server
   * unreachable on the LAN. WIFI_MODE_FULL_HIGH_PERF keeps the radio at full power.
   * Like the wake lock, held without a timeout for 24/7 operation; released in onDestroy().
   */
  private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

  private lateinit var allowlistLoader: AllowlistLoader

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
      allowlistLoader = AllowlistLoader(
        externalFilesDir = getExternalFilesDir(null),
        appVersionName = BuildConfig.VERSION_NAME,
        assetReader = {
          try { assets.open(MODEL_ALLOWLIST_FILENAME).reader().readText() } catch (e: Exception) { Log.w(TAG, "Failed to read bundled $MODEL_ALLOWLIST_FILENAME", e); null }
        },
        enabledCacheFilenames = {
          try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { dataStoreRepo?.readRepositories() }
              ?.filter { it.enabled }
              ?.map { it.cacheFilename }
              ?.toSet()
          } catch (e: Exception) { null }
        },
        onError = { source, ex -> Log.w(TAG, "Allowlist parse error ($source)", ex) },
      )
      modelLifecycle = ModelLifecycle(
        context = this,
        allowlistLoader = allowlistLoader,
        readImportedModels = { kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { dataStoreRepo?.readImportedModels() ?: emptyList() } },
      )
      // Create a partial wake lock to keep the CPU awake while the server is running.
      // Acquired in onStartCommand once the server starts, released in onDestroy.
      val pm = getSystemService(POWER_SERVICE) as? android.os.PowerManager
      wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "OlliteRT::Server")?.apply {
        setReferenceCounted(false)
      }
      val wm = getSystemService(WIFI_SERVICE) as? android.net.wifi.WifiManager
      @Suppress("DEPRECATION") // WIFI_MODE_FULL_HIGH_PERF deprecated in API 34, no equivalent for keeping WiFi at full power
      wifiLock = wm?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "OlliteRT::Server")?.apply {
        setReferenceCounted(false)
      }
      NotificationHelper.createChannel(this)
      checkCorruptedDataStores()
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

    // Android requires startForeground() within ~10s of startForegroundService().
    // Call it immediately with a minimal notification to avoid
    // ForegroundServiceDidNotStartInTimeException on early-return paths
    // (e.g. model not found, files missing). The notification is replaced later
    // with the full loading/running notification once setup completes.
    val placeholderIntent = Intent(this, MainActivity::class.java)
    placeholderIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val placeholderContentIntent = PendingIntent.getActivity(
      this, 0, placeholderIntent, PendingIntent.FLAG_IMMUTABLE,
    )
    val placeholderNotification = NotificationHelper.build(
      context = this,
      title = getString(R.string.notif_starting_title),
      text = getString(R.string.notif_starting_body),
      contentIntent = placeholderContentIntent,
      showProgress = true,
    )
    // Pass the foreground service type explicitly so Android 14+ (which requires it)
    // shows the notification immediately instead of deferring it for up to 10 seconds.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NotificationHelper.NOTIFICATION_ID,
        placeholderNotification,
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
      )
    } else {
      startForeground(NotificationHelper.NOTIFICATION_ID, placeholderNotification)
    }

    // Keep CPU awake for the entire server lifetime so the HTTP server stays reachable
    // on locked/idle devices (e.g. dedicated "closet server" use case).
    wakeLock?.acquire()
    wifiLock?.acquire()

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

    val resolvedModelName = requestedModelName
    val startSource = intent.getStringExtra(EXTRA_START_SOURCE)
    val model = pickModelByName(resolvedModelName)
    if (model == null) {
      // Include the trigger source in the error message so users understand what happened
      // (e.g. "Auto-start failed" vs just "Model not found").
      val sourcePrefix = when (startSource) {
        SOURCE_BOOT -> getString(R.string.error_autostart_boot_prefix)
        SOURCE_LAUNCH -> getString(R.string.error_autostart_launch_prefix)
        else -> ""
      }
      val msg = sourcePrefix + getString(R.string.error_model_not_found, resolvedModelName)
      Log.e(TAG, "Model '$resolvedModelName' not found — cannot start server (source=$startSource)")
      ServerMetrics.onServerError(msg)
      ServerMetrics.incrementErrorCount(ErrorCategory.MODEL_LOAD)
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = resolvedModelName, category = EventCategory.MODEL)
      pendingConfigOverrides.set(null)
      stopSelf()
      return START_NOT_STICKY
    }
    // Apply pending config overrides from the reload caller (e.g. InferenceSettingsSheet).
    // getAndSet(null) is atomic — prevents a concurrent reload's write from being lost.
    pendingConfigOverrides.getAndSet(null)?.let { overrides ->
      model.configValues = overrides.toMap()
      Log.i(TAG, "Applied ${overrides.size} config overrides from reload caller")
    }
    // Verify model files actually exist on disk.
    val modelPath = model.getPath(context = this)
    if (!java.io.File(modelPath).exists()) {
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
      return START_NOT_STICKY
    }
    // Cancel any in-flight warmup by bumping the generation counter
    val thisGeneration = loadGeneration.incrementAndGet()

    modelCache[model.name] = model

    ServerMetrics.onServerStarting(port, model.name)
    ServerMetrics.setActiveModelSize(model.totalBytes)
    RequestLogStore.addEvent("Loading model: ${model.name}", modelName = model.name, category = EventCategory.MODEL)

    val wifiIp = getWifiIpAddress(this)
    val displayAddress = wifiIp ?: "localhost"

    // Content intent: tap notification → open app
    val openAppIntent = Intent(this, MainActivity::class.java)
    openAppIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    val contentIntent = PendingIntent.getActivity(
      this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE,
    )

    // Stop action
    val stopIntent = PendingIntent.getService(
      this, 1,
      Intent(this, ServerService::class.java).apply { action = ACTION_STOP },
      PendingIntent.FLAG_IMMUTABLE,
    )

    // Copy URL action
    val endpointUrl = "http://$displayAddress:$port/v1"
    val copyIntent = PendingIntent.getBroadcast(
      this, 2,
      Intent(this, CopyUrlReceiver::class.java).apply {
        putExtra(CopyUrlReceiver.EXTRA_URL, endpointUrl)
      },
      PendingIntent.FLAG_IMMUTABLE,
    )

    // Replace the placeholder notification with the full loading notification
    NotificationHelper.update(
      context = this,
      title = getString(R.string.notif_loading_model_title, model.name),
      text = getString(R.string.notif_loading_model_body),
      contentIntent = contentIntent,
      showProgress = true,
    )

    server?.stop()
    inferenceExecutor?.shutdownNow()
    val executor = Executors.newSingleThreadExecutor()
    inferenceExecutor = executor
    val runner = InferenceRunner(
      context = this,
      executor = executor,
      inferenceLock = inferenceLock,
      logEvent = { msg -> logEvent(msg) },
      emitDebugStackTrace = { t, src, name -> emitDebugStackTrace(t, src, name) },
      buildSystemInstruction = { name -> buildSystemInstruction(name) },
    )
    inferenceRunner = runner
    val handlers = EndpointHandlers(
      context = this,
      json = json,
      inferenceRunner = runner,
      modelLifecycle = modelLifecycle,
      logEvent = { msg -> logEvent(msg) },
      nextRequestId = { nextRequestId() },
    )
    val audioTranscriptionHandler = AudioTranscriptionHandler(
      context = this,
      inferenceRunner = runner,
      modelLifecycle = modelLifecycle,
    )
    server = KtorServer(
      port = port,
      serviceContext = this,
      endpointHandlers = handlers,
      modelLifecycle = modelLifecycle,
      json = json,
      nextRequestId = { nextRequestId() },
      emitDebugStackTrace = { t, src, name -> emitDebugStackTrace(t, src, name) },
      audioTranscriptionHandler = audioTranscriptionHandler,
      inferenceLock = inferenceLock,
    )
    try {
      server?.start()
    } catch (e: Exception) {
      // Java's BindException says "Address already in use" — rewrite to mention the port explicitly
      val reason = if (e is java.net.BindException || e.message?.contains("Address already in use") == true)
        getString(R.string.error_port_in_use, port) else (e.message?.take(120) ?: getString(R.string.error_unknown))
      val msg = getString(R.string.error_server_failed_to_start, reason)
      Log.e(TAG, msg, e)
      ServerMetrics.onServerError(msg)
      ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = model.name, category = EventCategory.SERVER)
      stopSelf()
      return START_NOT_STICKY
    }

    synchronized(modelLifecycle.keepAliveLock) { defaultModel = model }

    val notifState = LoadNotificationState(
      contentIntent = contentIntent,
      stopIntent = stopIntent,
      copyIntent = copyIntent,
      endpointUrl = endpointUrl,
    )
    loadJob?.cancel()
    loadJob = serviceScope.launch {
      loadModelOnThread(model, thisGeneration, wifiIp, notifState)
    }

    return START_STICKY
  }

  /**
   * Cleans up the current model and server state before a reload.
   * Called from [onStartCommand] when [ACTION_RELOAD] is received.
   */
  private fun handleReloadCleanup() {
    cancelKeepAliveTimer()
    keepAliveUnloadedModelName = null
    val previousModelName = defaultModel?.name
    Log.i(TAG, "Reload requested — cleaning up current model before restart")
    // Bump generation FIRST so any in-flight load thread sees the stale generation
    // and cleans up its own Engine when it finishes (see loadGeneration guard below).
    loadGeneration.incrementAndGet()
    RequestLogStore.addEvent(
      "Model restart requested",
      modelName = previousModelName,
      category = EventCategory.MODEL,
    )
    server?.stop()
    defaultModel?.let { model ->
      RequestLogStore.addEvent(
        "Unloading model: ${model.name}",
        modelName = model.name,
        category = EventCategory.MODEL,
      )
      // Null defaultModel inside the lock so selectModel() sees it as unavailable immediately.
      // Keep model.instance non-null so cleanUp() can close the native Engine/Conversation.
      // Native cleanup runs outside the lock — Engine.close() can take seconds for large models.
      synchronized(modelLifecycle.keepAliveLock) {
        defaultModel = null
      }
      try {
        ServerLlmModelHelper.cleanUp(model) {}
      } catch (e: Exception) {
        Log.w(TAG, "Error cleaning up model during reload: ${e.message}")
      }
      model.instance = null
    }
    // Close any secondary models' native Engines before dropping references.
    // Without this, modelCache.clear() orphans Engine instances with GB-scale native memory.
    for ((_, cachedModel) in modelCache) {
      if (cachedModel.instance != null) {
        ServerLlmModelHelper.safeCleanup(cachedModel)
      }
    }
    modelCache.clear()
    // Cancel any in-flight requests so pending log cards resolve before the reload.
    RequestLogStore.cancelAllPending()
    // Reset metrics without emitting "Server stopped" log — we're restarting, not stopping
    ServerMetrics.onServerStopped()
    // Hint GC to reclaim native memory from the closed Engine/Conversation.
    // LiteRT Engine allocates large native buffers (hundreds of MB) that are only
    // freed when the Java wrapper is finalized. Without this hint, the old Engine's
    // native memory may persist until the new model's allocation triggers OOM.
    System.gc()
  }

  /**
   * Loads a model on a background thread: storage check, cleanup latch wait,
   * warmup/initialize, metrics, notification update.
   *
   * Called from [serviceScope] (Dispatchers.IO) in [onStartCommand].
   * Must NOT be called from the main thread — contains blocking I/O and native SDK calls.
   */
  private fun loadModelOnThread(
    model: Model,
    thisGeneration: Long,
    wifiIp: String?,
    notifState: LoadNotificationState,
  ) {
    try {
      checkStorageBeforeLoad()
      awaitPreviousCleanup()

      val loadStart = SystemClock.elapsedRealtime()
      initializeOrWarmUp(model)

      // If another model load was initiated while we were warming up, discard this result
      if (loadGeneration.get() != thisGeneration) {
        Log.w(TAG, "Warmup for ${model.name} completed but a newer load was initiated — discarding")
        ServerLlmModelHelper.safeCleanup(model)
        return
      }
      ServerMetrics.recordModelLoadTime(SystemClock.elapsedRealtime() - loadStart)
      ServerMetrics.setActiveAccelerator(
        model.configValues[com.ollitert.llm.server.data.ConfigKeys.ACCELERATOR.label]?.toString()
      )
      ServerMetrics.setThinkingEnabled(model.isThinkingEnabled)
      ServerMetrics.onServerRunning(wifiIp)
      resetKeepAliveTimer()
      RequestLogStore.addEvent("Model ready: ${model.name} (${SystemClock.elapsedRealtime() - loadStart}ms)", modelName = model.name, category = EventCategory.MODEL)
      logVerboseModelConfig(model)
      if (handleQueuedReload(model)) return
      logActiveSystemPrompt(model)
      updateNotificationToRunning(model, notifState)
    } catch (t: Throwable) {
      handleModelLoadFailure(t, model, thisGeneration, notifState)
    }
  }

  /** Checks available storage before native model init to avoid SIGABRT from LiteRT. */
  private fun checkStorageBeforeLoad() {
    try {
      val stat = StatFs(Environment.getDataDirectory().path)
      if (stat.availableBytes < MIN_STORAGE_FOR_MODEL_INIT_BYTES) {
        val availMb = stat.availableBytes.bytesToMb()
        throw RuntimeException(
          getString(R.string.error_storage_low_model_init,
            availMb.toString(), MIN_STORAGE_FOR_MODEL_INIT_BYTES.bytesToMb().toString())
        )
      }
    } catch (e: RuntimeException) { throw e }
    catch (_: Exception) { /* StatFs failed — proceed and let native code decide */ }
  }

  /** Waits for the previous service instance's native cleanup to finish. */
  private fun awaitPreviousCleanup() {
    cleanupLatch.get()?.let { latch ->
      if (latch.count > 0) {
        Log.i(TAG, "Waiting for previous model cleanup to finish...")
        val cleanedUp = latch.await(CLEANUP_AWAIT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        if (cleanedUp) {
          Log.i(TAG, "Previous cleanup finished, proceeding with model load")
        } else {
          Log.w(TAG, "Previous cleanup did not finish within ${CLEANUP_AWAIT_TIMEOUT_SECONDS}s, proceeding anyway — native resource race possible")
        }
      }
    }
  }

  /** Initializes the model engine, with or without warmup depending on user settings. */
  private fun initializeOrWarmUp(model: Model) {
    val eagerVision = ServerPrefs.isEagerVisionInit(this)
    val supportImage = model.llmSupportImage && eagerVision
    val supportAudio = model.llmSupportAudio
    if (ServerPrefs.isWarmupEnabled(this)) {
      inferenceRunner?.warmUpModel(model)
    } else {
      var initErr = ""
      ServerLlmModelHelper.initialize(
        context = this,
        model = model,
        supportImage = supportImage,
        supportAudio = supportAudio,
        onDone = { initErr = it },
        systemInstruction = buildSystemInstruction(model.prefsKey),
      )
      if (initErr.isNotEmpty()) {
        throw RuntimeException(getString(R.string.error_model_init_failed, initErr))
      }
      model.initializedWithVision = supportImage
      RequestLogStore.addEvent(
        "Warmup skipped — Model loaded without test inference (disabled in Settings)",
        modelName = model.name,
        category = EventCategory.MODEL,
      )
    }
  }

  /** Logs model config dump when verbose debug is enabled. */
  private fun logVerboseModelConfig(model: Model) {
    if (!ServerPrefs.isVerboseDebugEnabled(this)) return
    val sizeMb = String.format(java.util.Locale.US, "%.1f", model.totalBytes / (1024.0 * 1024.0))
    val debugText = buildString {
      appendLine("Name: ${model.name}")
      appendLine("Path: ${model.getPath(this@ServerService)}")
      appendLine("Size: ${sizeMb}MB (${model.totalBytes} bytes)")
      appendLine("Capabilities: vision=${model.llmSupportImage}, audio=${model.llmSupportAudio}, thinking=${model.llmSupportThinking}")
      if (model.configValues.isNotEmpty()) {
        appendLine("Config:")
        model.configValues.forEach { (k, v) -> appendLine("  $k: $v") }
      }
    }.trimEnd()
    RequestLogStore.addEvent("Loaded model configuration", level = LogLevel.DEBUG, modelName = model.name, category = EventCategory.MODEL, body = debugText)
  }

  /**
   * Checks for a queued reload (user changed reinit settings while model was loading).
   * @return true if a reload was triggered and the caller should return immediately.
   */
  private fun handleQueuedReload(model: Model): Boolean {
    val queued = pendingReloadAfterLoad.getAndSet(null) ?: return false
    if (queued.modelName == model.name) {
      Log.i(TAG, "Executing queued reload for ${queued.modelName}")
      RequestLogStore.addEvent("Applying queued settings change — reloading model", modelName = queued.modelName, category = EventCategory.SETTINGS)
      reload(this, queued.port, queued.modelName, queued.configValues)
      return true
    }
    Log.w(TAG, "Discarding stale queued reload for ${queued.modelName} — loaded model is ${model.name}")
    return false
  }

  /** Logs the active system prompt if custom prompts are enabled. */
  private fun logActiveSystemPrompt(model: Model) {
    val sysPrompt = if (ServerPrefs.isCustomPromptsEnabled(this))
      ServerPrefs.getSystemPrompt(this, model.prefsKey) else ""
    if (sysPrompt.isNotBlank()) {
      RequestLogStore.addEvent(
        "System prompt active: \"${sysPrompt.take(120)}\"${if (sysPrompt.length > 120) "…" else ""}",
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

  /** Updates notification intents and displays the running notification. */
  private fun updateNotificationToRunning(model: Model, notifState: LoadNotificationState) {
    notifContentIntent = notifState.contentIntent
    notifStopIntent = notifState.stopIntent
    notifCopyIntent = notifState.copyIntent
    notifEndpointUrl = notifState.endpointUrl
    notifModelName = model.name
    val initialText = buildString {
      if (ServerPrefs.isNotifShowRequestCount(this@ServerService)) {
        append(resources.getQuantityString(R.plurals.notif_server_body_requests, 0, 0))
        append("\n")
      }
      append(getString(R.string.notif_server_body_model, model.name))
      append("\n")
      append(getString(R.string.notif_server_body_url, notifState.endpointUrl))
    }
    NotificationHelper.update(
      context = this,
      title = getString(R.string.notif_server_running_title),
      text = initialText,
      contentIntent = notifState.contentIntent,
      stopIntent = notifState.stopIntent,
      copyIntent = notifState.copyIntent,
    )
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
      try { ServerLlmModelHelper.cleanUp(model) {} } catch (e: Exception) { Log.w(TAG, "cleanUp() failed during OOM recovery", e) }
      defaultModel?.instance = null
      System.gc()
    }
    Log.e(TAG, "Failed to load model ${model.name}", t)
    emitDebugStackTrace(t, "model_load", model.name)
    pendingReloadAfterLoad.set(null)
    val msg = t.message?.take(120) ?: getString(R.string.error_model_init_unknown)
    val category = if (t is OutOfMemoryError) ErrorCategory.SYSTEM else ErrorCategory.MODEL_LOAD
    ServerMetrics.onServerError(msg)
    ServerMetrics.incrementErrorCount(category)
    RequestLogStore.addEvent("Model load failed: $msg", level = LogLevel.ERROR, modelName = model.name, category = EventCategory.MODEL)
    NotificationHelper.update(
      context = this,
      title = getString(R.string.notif_model_load_failed_title),
      text = msg,
      contentIntent = notifState.contentIntent,
      stopIntent = notifState.stopIntent,
    )
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
    loadJob?.cancel()
    loadJob = null
    server?.stop()
    // Cancel any in-flight inference so the native JNI call returns quickly.
    // Without this, shutdownNow() only calls Thread.interrupt() which has no
    // effect on blocking native code — the 5s await can expire with the thread
    // still inside LiteRT SDK.
    defaultModel?.let { ServerLlmModelHelper.stopResponse(it) }
    val executor = inferenceExecutor
    executor?.shutdownNow()
    try { executor?.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS) } catch (_: InterruptedException) {}
    inferenceExecutor = null
    val modelName = defaultModel?.name

    // Collect models that need native cleanup (Engine + Conversation close).
    // These operations can take seconds for multi-GB models and must NOT run on the main
    // thread — doing so causes an ANR ("Input dispatching timed out") when the user taps
    // Stop Server, because onDestroy runs on the main thread.
    val modelsToCleanUp = mutableListOf<Model>()
    defaultModel?.let { model ->
      modelsToCleanUp.add(model)
    }
    synchronized(modelLifecycle.keepAliveLock) { defaultModel = null }
    for ((_, cachedModel) in modelCache) {
      if (cachedModel.instance != null) {
        modelsToCleanUp.add(cachedModel)
      }
    }
    modelCache.clear()

    // Dispatch native memory release to a background thread to avoid ANR.
    // Set a static latch so the next service instance's load thread can wait for cleanup
    // to finish before initializing — prevents racing on native LiteRT resources.
    if (modelsToCleanUp.isNotEmpty()) {
      val latch = java.util.concurrent.CountDownLatch(1)
      cleanupLatch.set(latch)
      Thread({
        try {
          // Wait for the inference executor thread to fully exit before closing
          // native resources. The initial 5s await in onDestroy may have expired
          // if a native JNI call was still in progress. stopResponse() above
          // should have made it return, but give it another 10s as a safety net.
          if (executor?.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS) == false) {
            Log.w(TAG, "Inference executor did not terminate within 15s total — proceeding with native cleanup (potential use-after-free)")
          }
          for (model in modelsToCleanUp) {
            try {
              ServerLlmModelHelper.cleanUp(model) {}
            } catch (e: Exception) {
              Log.w(TAG, "Error cleaning up model during destroy: ${e.message}")
            }
            model.instance = null
          }
          // GC hint after releasing large native allocations
          System.gc()
        } finally {
          // Count down but do NOT null the reference — the next service instance reads
          // the latch and if count==0, await() returns immediately. Nulling it creates a
          // race where the new instance misses the latch entirely.
          latch.countDown()
        }
      }, "OlliteRT-ModelCleanup").start()
    }

    notifContentIntent = null
    notifStopIntent = null
    notifCopyIntent = null
    notifEndpointUrl = null
    notifModelName = null
    pendingReloadAfterLoad.set(null)
    // Cancel any in-flight requests so pending log cards resolve when the service is destroyed.
    RequestLogStore.cancelAllPending()
    ServerMetrics.onServerStopped()
    if (modelName != null) {
      RequestLogStore.addEvent("Server stopped", modelName = modelName, category = EventCategory.SERVER)
    }
    if (ServerPrefs.isClearLogsOnStop(this)) {
      RequestLogStore.clear()
    }
    // Release wake lock if still held (e.g. service killed mid-inference)
    if (wifiLock?.isHeld == true) wifiLock?.release()
    wifiLock = null
    if (wakeLock?.isHeld == true) wakeLock?.release()
    wakeLock = null
    try {
      val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        applicationContext, OlliteRTApplication.PersistenceEntryPoint::class.java
      )
      entryPoint.requestLogPersistence().shutdown()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to shut down RequestLogPersistence", e)
    }
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
      refreshRunningNotification()
    }
    return "r${requestCounter.incrementAndGet()}"
  }

  /** Update the foreground notification with the current request count and optional update badge. */
  private fun refreshRunningNotification() {
    val ci = notifContentIntent ?: return
    val name = notifModelName ?: return
    val url = notifEndpointUrl ?: return
    NotificationHelper.refreshRunning(
      context = this,
      modelName = name,
      endpointUrl = url,
      contentIntent = ci,
      stopIntent = notifStopIntent,
      copyIntent = notifCopyIntent,
      cachedUpdateVersion = ServerMetrics.availableUpdateVersion.value,
    )
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
   * @param source Identifier for which catch block produced this (e.g. "model_load", "execute", "serve_catch_all")
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

  /**
   * Checks for DataStore corruption that was detected during lazy initialization
   * (flagged via SharedPreferences by the ReplaceFileCorruptionHandler in AppModule).
   * Logs a WARNING event to the in-app log and posts a system notification so the
   * user knows their settings/data were reset.
   */
  private fun checkCorruptedDataStores() {
    val corrupted = ServerPrefs.getCorruptedDataStores(this)
    if (corrupted.isEmpty()) return

    val names = corrupted.sorted().joinToString(", ")
    Log.w(TAG, "DataStore corruption recovered on previous run: $names")
    RequestLogStore.addEvent(
      getString(R.string.log_corruption_recovered, names),
      level = LogLevel.WARNING,
      category = EventCategory.SERVER,
    )

    val channelId = "ollitert-corruption"
    val mgr = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
    if (mgr != null) {
      mgr.createNotificationChannel(
        android.app.NotificationChannel(
          channelId,
          getString(R.string.notif_channel_corruption_name),
          android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = getString(R.string.notif_channel_corruption_desc) }
      )
      val openIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_IMMUTABLE,
      )
      val text = if (corrupted.size == 1)
        getString(R.string.notif_corruption_text_one, corrupted.first())
      else
        getString(R.string.notif_corruption_text_many, corrupted.size, names)
      val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.mipmap.ic_launcher_monochrome)
        .setContentTitle(getString(R.string.notif_corruption_title))
        .setContentText(text)
        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .build()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "POST_NOTIFICATIONS not granted — corruption notification suppressed")
      } else {
        mgr.notify(NOTIFICATION_ID_CORRUPTION, notification)
      }
    }

    ServerPrefs.clearCorruptedDataStores(this)
  }

  /** Notification intents + metadata passed to the model load thread. */
  private class LoadNotificationState(
    val contentIntent: PendingIntent,
    val stopIntent: PendingIntent,
    val copyIntent: PendingIntent,
    val endpointUrl: String,
  )

  companion object {
    private const val TAG = "OlliteRT.Service"
    const val EXTRA_PORT = "extra_port"
    const val EXTRA_MODEL_NAME = "extra_model_name"
    /** Optional: identifies what triggered the start (e.g. "boot", "launch") for better error messages. */
    const val EXTRA_START_SOURCE = "extra_start_source"
    const val SOURCE_BOOT = "boot"
    const val SOURCE_LAUNCH = "launch"
    const val DEFAULT_PORT = com.ollitert.llm.server.data.DEFAULT_PORT
    private const val NOTIFICATION_ID_CORRUPTION = 44
    const val ACTION_STOP = "com.ollitert.llm.server.STOP_SERVER"
    const val ACTION_RELOAD = "com.ollitert.llm.server.RELOAD_SERVER"
    const val ACTION_RESET_KEEP_ALIVE = "com.ollitert.llm.server.RESET_KEEP_ALIVE"

    /**
     * Latch that the background cleanup thread in onDestroy signals when native memory is released.
     * The next service instance's model load thread waits on this before initializing to avoid
     * racing with the old instance's Engine/Conversation cleanup.
     *
     * Uses AtomicReference instead of @Volatile to avoid race conditions where the latch is
     * nulled out between the new instance's read and wait. The latch is never nulled — once
     * counted down, it stays counted-down and await() returns immediately.
     */
    private val cleanupLatch = java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch?>(null)

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
    private data class PendingReload(val port: Int, val modelName: String, val configValues: Map<String, Any>?)
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

    /**
     * Update config values on the running service's model without reloading.
     * Used for non-reinitialization config changes (temperature, topK, topP, etc.).
     */
    @Volatile
    private var activeInstance: ServerService? = null

    fun updateConfigValues(configValues: Map<String, Any>) {
      val instance = activeInstance ?: return
      synchronized(instance.inferenceLock) {
        instance.defaultModel?.let { model ->
          model.configValues = configValues.toMap()
          // Update thinking state in metrics so the Status screen pill reflects the change
          ServerMetrics.setThinkingEnabled(model.isThinkingEnabled)
        }
      }
    }
  }
}
