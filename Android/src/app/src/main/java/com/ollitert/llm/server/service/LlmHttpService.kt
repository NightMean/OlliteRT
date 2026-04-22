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
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.MIN_STORAGE_FOR_MODEL_INIT_BYTES
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.bytesToMb
import com.ollitert.llm.server.data.llmSupportAudio
import com.ollitert.llm.server.data.llmSupportImage
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.LlmHttpService.Companion.queueReloadAfterLoad
import com.ollitert.llm.server.service.LlmHttpService.Companion.reload
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service exposing a minimal HTTP API for local LLM inference.
 * GET  /ping                  -> {status:"ok"}
 * GET  /v1/models             -> OpenAI-compatible model list
 * POST /generate              -> {text, usage}
 * POST /v1/chat/completions   -> OpenAI chat completions
 * POST /v1/responses          -> OpenAI responses API
 */
class LlmHttpService : Service() {

  private var server: LlmHttpServer? = null
  private var inferenceRunner: LlmHttpInferenceRunner? = null
  private var inferenceExecutor: java.util.concurrent.ExecutorService? = null
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private var currentPort: Int = DEFAULT_PORT
  private val logTag = "LlmHttpService"
  private val requestCounter = AtomicLong(0)
  /** Incremented each time a new model load is initiated; stale warmup threads check this to bail out. */
  private val loadGeneration = AtomicLong(0)
  /** Shared lock for serializing inference and config writes — passed to InferenceRunner and Server.
   *  Must always be acquired AFTER keepAliveLock (in LlmHttpModelLifecycle), never before it. */
  private val inferenceLock = Any()
  @Volatile private var loadThread: Thread? = null

  // Notification state — saved after warmup so we can refresh the notification with live request count.
  // @Volatile: written from background load thread, read from main thread for notification refresh.
  @Volatile private var notifContentIntent: PendingIntent? = null
  @Volatile private var notifStopIntent: PendingIntent? = null
  @Volatile private var notifCopyIntent: PendingIntent? = null
  @Volatile private var notifEndpointUrl: String? = null
  @Volatile private var notifModelName: String? = null

  // Model lifecycle: keep-alive, model selection, image decoding — see LlmHttpModelLifecycle.kt
  private lateinit var modelLifecycle: LlmHttpModelLifecycle

  // Convenience accessors for model state (delegates to modelLifecycle)
  private inline var defaultModel: Model?
    get() = modelLifecycle.defaultModel
    set(value) { modelLifecycle.defaultModel = value }
  private inline val modelCache get() = modelLifecycle.modelCache
  private inline var keepAliveUnloadedModelName: String?
    get() = modelLifecycle.keepAliveUnloadedModelName
    set(value) { modelLifecycle.setKeepAliveUnloadedModelName(value) }

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

  private lateinit var logger: LlmHttpLogger
  private lateinit var allowlistLoader: LlmHttpAllowlistLoader

  override fun onCreate() {
    super.onCreate()
    activeInstance = this
    try {
      logger = LlmHttpLogger(
        logDir = { getExternalFilesDir(null)?.let { File(it, "ollitert") } },
        isEnabled = { LlmHttpPrefs.isPayloadLoggingEnabled(this) },
        isVerboseDebug = { LlmHttpPrefs.isVerboseDebugEnabled(this) },
      )
      allowlistLoader = LlmHttpAllowlistLoader(
        externalFilesDir = getExternalFilesDir(null),
        appVersionName = BuildConfig.VERSION_NAME,
        assetReader = {
          try { assets.open("model_allowlist.json").reader().readText() } catch (e: Exception) { Log.w(logTag, "Failed to read bundled model_allowlist.json", e); null }
        },
      )
      // Access DataStoreRepository via Hilt EntryPoint so imported models can be resolved
      // when starting the server. The DataStore singleton is managed by Hilt; creating a
      // second instance would corrupt the protobuf file.
      val dataStoreRepo = try {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
          applicationContext, OlliteRTApplication.DataStoreEntryPoint::class.java
        )
        entryPoint.dataStoreRepository()
      } catch (e: Exception) {
        Log.w(logTag, "Failed to access DataStoreRepository — imported models won't be loadable", e)
        null
      }
      modelLifecycle = LlmHttpModelLifecycle(
        context = this,
        allowlistLoader = allowlistLoader,
        readImportedModels = { kotlinx.coroutines.runBlocking { dataStoreRepo?.readImportedModels() ?: emptyList() } },
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
      LlmHttpNotificationHelper.createChannel(this)
      checkCorruptedDataStores()
    } catch (e: Exception) {
      Log.e(logTag, "Service initialization failed — stopping immediately", e)
      stopSelf()
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Guard: if onCreate() failed partway through, the service is in a zombie state.
    // Stop immediately to prevent UninitializedPropertyAccessException crashes.
    if (!::modelLifecycle.isInitialized) {
      Log.e(logTag, "Service not initialized — stopping")
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
      Log.i(logTag, "No intent or model specified — stopping to avoid crash loop")
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
    val placeholderNotification = LlmHttpNotificationHelper.build(
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
        LlmHttpNotificationHelper.NOTIFICATION_ID,
        placeholderNotification,
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
      )
    } else {
      startForeground(LlmHttpNotificationHelper.NOTIFICATION_ID, placeholderNotification)
    }

    // Keep CPU awake for the entire server lifetime so the HTTP server stays reachable
    // on locked/idle devices (e.g. dedicated "closet server" use case).
    wakeLock?.acquire()
    wifiLock?.acquire()

    // Handle reload action: clean up current model first, then proceed with normal start.
    // Unlike a full stop, reload emits "Model restart requested" + "Unloading model" instead
    // of "Server stopped", because the server will immediately start again.
    if (intent.action == ACTION_RELOAD) {
      cancelKeepAliveTimer()
      keepAliveUnloadedModelName = null
      val previousModelName = defaultModel?.name
      Log.i(logTag, "Reload requested — cleaning up current model before restart")
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
          Log.w(logTag, "Error cleaning up model during reload: ${e.message}")
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
      // Fall through to normal start logic below (which emits "Loading model: X")
    }

    val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
    val requestedModelName = intent.getStringExtra(EXTRA_MODEL_NAME)
    currentPort = port

    // If no explicit model was requested, this is likely a system restart after a crash.
    // Don't auto-load the last model to avoid crash loops (e.g. from OOM).
    // Auto-start on boot is handled separately by BootReceiver which passes EXTRA_MODEL_NAME.
    if (requestedModelName == null) {
      Log.i(logTag, "No model specified in intent — not auto-loading to avoid potential crash loop")
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
      Log.e(logTag, "Model '$resolvedModelName' not found — cannot start server (source=$startSource)")
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
      Log.i(logTag, "Applied ${overrides.size} config overrides from reload caller")
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
      Log.e(logTag, "Model files not found at $modelPath for ${model.name} — cannot start server (source=$startSource)")
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
      Intent(this, LlmHttpService::class.java).apply { action = ACTION_STOP },
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
    LlmHttpNotificationHelper.update(
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
    val runner = LlmHttpInferenceRunner(
      context = this,
      executor = executor,
      inferenceLock = inferenceLock,
      logEvent = { msg -> logEvent(msg) },
      logPayload = { label, body, id -> logPayload(label, body, id) },
      emitDebugStackTrace = { t, src, name -> emitDebugStackTrace(t, src, name) },
      buildSystemInstruction = { name -> buildSystemInstruction(name) },
    )
    inferenceRunner = runner
    val handlers = LlmHttpEndpointHandlers(
      context = this,
      json = json,
      inferenceRunner = runner,
      modelLifecycle = modelLifecycle,
      logEvent = { msg -> logEvent(msg) },
      logPayload = { label, body, id -> logPayload(label, body, id) },
      nextRequestId = { nextRequestId() },
    )
    val audioTranscriptionHandler = LlmHttpAudioTranscriptionHandler(
      context = this,
      inferenceRunner = runner,
      modelLifecycle = modelLifecycle,
    )
    server = LlmHttpServer(
      port = port,
      serviceContext = this,
      endpointHandlers = handlers,
      modelLifecycle = modelLifecycle,
      json = json,
      nextRequestId = { nextRequestId() },
      getRequestCount = { requestCounter.get() },
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
      Log.e(logTag, msg, e)
      ServerMetrics.onServerError(msg)
      ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = model.name, category = EventCategory.SERVER)
      stopSelf()
      return START_NOT_STICKY
    }

    synchronized(modelLifecycle.keepAliveLock) { defaultModel = model }

    Thread {
      loadThread = Thread.currentThread()
      try {
        // Guard against native SIGABRT: LiteRT's Engine.initialize() calls
        // abort() (not a catchable exception) when it can't allocate memory or
        // create temp/cache files on a nearly-full disk. Check available storage
        // before entering native code so we fail gracefully instead of crashing
        // the entire process. 500 MB is a conservative minimum — models create
        // XNNPack weight caches that can be hundreds of MB.
        try {
          val stat = StatFs(Environment.getDataDirectory().path)
          if (stat.availableBytes < MIN_STORAGE_FOR_MODEL_INIT_BYTES) {
            val availMb = stat.availableBytes.bytesToMb()
            throw RuntimeException(
              getString(R.string.error_storage_low_model_init,
                availMb.toString(), MIN_STORAGE_FOR_MODEL_INIT_BYTES.bytesToMb().toString())
            )
          }
        } catch (e: RuntimeException) { throw e } // re-throw our own RuntimeException
        catch (_: Exception) { /* StatFs failed — proceed and let native code decide */ }

        // Wait for the previous service instance's background cleanup to finish before
        // initializing. Without this, the new Engine.initialize() races with the old
        // Engine/Conversation.close() on native LiteRT resources, causing the load to hang.
        cleanupLatch.get()?.let { latch ->
          if (latch.count > 0) {
            Log.i(logTag, "Waiting for previous model cleanup to finish...")
            val cleanedUp = latch.await(CLEANUP_AWAIT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            if (cleanedUp) {
              Log.i(logTag, "Previous cleanup finished, proceeding with model load")
            } else {
              // Native cleanup is still running — Thread.interrupt() can't break JNI calls,
              // and refusing to load would leave the server permanently stuck. Proceed and
              // accept the small risk of a native resource race (SIGSEGV). In practice this
              // only happens with severely buggy GPU drivers that deadlock in Engine.close().
              Log.w(logTag, "Previous cleanup did not finish within ${CLEANUP_AWAIT_TIMEOUT_SECONDS}s, proceeding anyway — native resource race possible")
            }
          }
        }

        val loadStart = SystemClock.elapsedRealtime()
        val eagerVision = LlmHttpPrefs.isEagerVisionInit(this@LlmHttpService)
        val supportImage = model.llmSupportImage && eagerVision
        val supportAudio = model.llmSupportAudio
        if (LlmHttpPrefs.isWarmupEnabled(this@LlmHttpService)) {
          inferenceRunner?.warmUpModel(model)
        } else {
          // Still initialize the model engine so it's ready for requests;
          // only the test inference message is skipped.
          var initErr = ""
          ServerLlmModelHelper.initialize(
            context = this@LlmHttpService,
            model = model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            onDone = { initErr = it },
            systemInstruction = buildSystemInstruction(model.name),
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
        // If another model load was initiated while we were warming up, discard this result
        if (loadGeneration.get() != thisGeneration) {
          Log.w(logTag, "Warmup for ${model.name} completed but a newer load was initiated — discarding")
          ServerLlmModelHelper.safeCleanup(model)
          return@Thread
        }
        ServerMetrics.recordModelLoadTime(SystemClock.elapsedRealtime() - loadStart)
        ServerMetrics.setActiveAccelerator(
          model.configValues[com.ollitert.llm.server.data.ConfigKeys.ACCELERATOR.label]?.toString()
        )
        ServerMetrics.setThinkingEnabled(
          model.llmSupportThinking && (model.configValues[com.ollitert.llm.server.data.ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
        )
        ServerMetrics.onServerRunning(wifiIp)
        // Start keep-alive idle timer if enabled — model will auto-unload after the configured
        // idle duration to free RAM. Timer is reset after each inference request.
        resetKeepAliveTimer()
        RequestLogStore.addEvent("Model ready: ${model.name} (${SystemClock.elapsedRealtime() - loadStart}ms)", modelName = model.name, category = EventCategory.MODEL)
        // Verbose debug: log model config dump on successful load
        if (LlmHttpPrefs.isVerboseDebugEnabled(this@LlmHttpService)) {
          val sizeMb = String.format(java.util.Locale.US, "%.1f", model.totalBytes / (1024.0 * 1024.0))
          val debugText = buildString {
            appendLine("Name: ${model.name}")
            appendLine("Path: ${model.getPath(this@LlmHttpService)}")
            appendLine("Size: ${sizeMb}MB (${model.totalBytes} bytes)")
            appendLine("Capabilities: vision=${model.llmSupportImage}, audio=${model.llmSupportAudio}, thinking=${model.llmSupportThinking}")
            if (model.configValues.isNotEmpty()) {
              appendLine("Config:")
              model.configValues.forEach { (k, v) -> appendLine("  $k: $v") }
            }
          }.trimEnd()
          RequestLogStore.addEvent("Loaded model configuration", level = LogLevel.DEBUG, modelName = model.name, category = EventCategory.MODEL, body = debugText)
        }
        // Check for queued reload (user changed reinit settings while model was loading).
        // getAndSet(null) is atomic — prevents the UI thread's write from being lost between read and clear.
        val queued = pendingReloadAfterLoad.getAndSet(null)
        if (queued != null) {
          if (queued.modelName == model.name) {
            Log.i(logTag, "Executing queued reload for ${queued.modelName}")
            RequestLogStore.addEvent("Applying queued settings change — reloading model", modelName = queued.modelName, category = EventCategory.SETTINGS)
            reload(this@LlmHttpService, queued.port, queued.modelName, queued.configValues)
            return@Thread
          } else {
            Log.w(logTag, "Discarding stale queued reload for ${queued.modelName} — loaded model is ${model.name}")
          }
        }
        val sysPrompt = if (LlmHttpPrefs.isCustomPromptsEnabled(this@LlmHttpService))
          LlmHttpPrefs.getSystemPrompt(this@LlmHttpService, model.name) else ""
        if (sysPrompt.isNotBlank()) {
          RequestLogStore.addEvent(
            "System prompt active: \"${sysPrompt.take(120)}\"${if (sysPrompt.length > 120) "…" else ""}",
            modelName = model.name,
            category = EventCategory.PROMPT,
            // Structured JSON body — full prompt text for the log card's expandable text box.
            // Schema: {"type":"prompt_active","prompt_type":"system_prompt","text":"..."}
            body = org.json.JSONObject().apply {
              put("type", "prompt_active")
              put("prompt_type", "system_prompt")
              put("text", sysPrompt)
            }.toString(),
          )
        }
        // Save notification state for live updates on each request
        notifContentIntent = contentIntent
        notifStopIntent = stopIntent
        notifCopyIntent = copyIntent
        notifEndpointUrl = endpointUrl
        notifModelName = model.name
        // Update notification to show running state with full actions
        LlmHttpNotificationHelper.update(
          context = this@LlmHttpService,
          title = getString(R.string.notif_server_running_title),
          text = "${getString(R.string.notif_server_body_model, model.name)}\n${getString(R.string.notif_server_body_url, endpointUrl)}",
          contentIntent = contentIntent,
          stopIntent = stopIntent,
          copyIntent = copyIntent,
        )
      } catch (t: Throwable) {
        // Only report error if this is still the current load
        if (loadGeneration.get() != thisGeneration) {
          Log.w(logTag, "Warmup for ${model.name} failed but a newer load was initiated — ignoring")
          // Clean up any partially-created Engine (e.g. Engine initialized but Conversation
          // creation failed). Without this, the orphaned Engine's native memory is leaked.
          ServerLlmModelHelper.safeCleanup(model)
          return@Thread
        }
        // OOM during model load: close the native Engine/Conversation to release memory.
        // Just nullifying the instance pointer leaks GB-scale native memory until GC
        // finalizes the Java wrapper — which may never happen if heap pressure is low.
        if (t is OutOfMemoryError) {
          try { ServerLlmModelHelper.cleanUp(model) {} } catch (e: Exception) { Log.w(logTag, "cleanUp() failed during OOM recovery", e) }
          defaultModel?.instance = null
          System.gc()
        }
        Log.e(logTag, "Failed to load model ${model.name}", t)
        emitDebugStackTrace(t, "model_load", model.name)
        pendingReloadAfterLoad.set(null)  // Clear queued reload — don't apply stale config to a future model
        val msg = t.message?.take(120) ?: getString(R.string.error_model_init_unknown)
        val category = if (t is OutOfMemoryError) ErrorCategory.SYSTEM else ErrorCategory.MODEL_LOAD
        ServerMetrics.onServerError(msg)
        ServerMetrics.incrementErrorCount(category)
        RequestLogStore.addEvent("Model load failed: $msg", level = LogLevel.ERROR, modelName = model.name, category = EventCategory.MODEL)
        // Update notification to show error state
        LlmHttpNotificationHelper.update(
          context = this@LlmHttpService,
          title = getString(R.string.notif_model_load_failed_title),
          text = msg,
          contentIntent = contentIntent,
          stopIntent = stopIntent,
        )
      } finally {
        loadThread = null
      }
    }.start()

    return START_STICKY
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
    loadThread?.interrupt()
    loadThread = null
    server?.stop()
    inferenceExecutor?.shutdownNow()
    try { inferenceExecutor?.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS) } catch (e: InterruptedException) {
      Log.w(logTag, "Interrupted waiting for inference executor shutdown", e)
    }
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
      Thread {
        try {
          for (model in modelsToCleanUp) {
            try {
              ServerLlmModelHelper.cleanUp(model) {}
            } catch (e: Exception) {
              Log.w(logTag, "Error cleaning up model during destroy: ${e.message}")
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
      }.start()
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
    if (LlmHttpPrefs.isClearLogsOnStop(this)) {
      RequestLogStore.clear()
    }
    // Release wake lock if still held (e.g. service killed mid-inference)
    if (wifiLock?.isHeld == true) wifiLock?.release()
    wifiLock = null
    if (wakeLock?.isHeld == true) wakeLock?.release()
    wakeLock = null
    logger.shutdown()
    try {
      val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        applicationContext, OlliteRTApplication.PersistenceEntryPoint::class.java
      )
      entryPoint.requestLogPersistence().shutdown()
    } catch (e: Exception) {
      Log.w(logTag, "Failed to shut down RequestLogPersistence", e)
    }
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  // Model lifecycle methods — delegated to LlmHttpModelLifecycle.kt
  private fun pickModelByName(name: String) = modelLifecycle.pickModelByName(name)
  private fun cancelKeepAliveTimer() = modelLifecycle.cancelKeepAliveTimer()
  private fun resetKeepAliveTimer() = modelLifecycle.resetKeepAliveTimer()
  private fun buildSystemInstruction(modelName: String) = modelLifecycle.buildSystemInstruction(modelName)

  private fun nextRequestId(): String {
    ServerMetrics.incrementRequestCount()
    if (LlmHttpPrefs.isNotifShowRequestCount(this)) {
      refreshRunningNotification()
    }
    return "r${requestCounter.incrementAndGet()}"
  }

  /** Update the foreground notification with the current request count and optional update badge. */
  private fun refreshRunningNotification() {
    val ci = notifContentIntent ?: return
    val name = notifModelName ?: return
    val url = notifEndpointUrl ?: return
    LlmHttpNotificationHelper.refreshRunning(
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
    Log.i(logTag, "LLM_HTTP $message")
    logger.logEvent(message)
  }

  private fun logPayload(label: String, body: String, requestId: String) {
    logger.logPayload(label, body, requestId)
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
    if (!LlmHttpPrefs.isVerboseDebugEnabled(this)) return
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
    val corrupted = LlmHttpPrefs.getCorruptedDataStores(this)
    if (corrupted.isEmpty()) return

    val names = corrupted.sorted().joinToString(", ")
    Log.w(logTag, "DataStore corruption recovered on previous run: $names")
    RequestLogStore.addEvent(
      getString(R.string.log_corruption_recovered, names),
      level = LogLevel.WARNING,
      category = EventCategory.SERVER,
    )

    val channelId = "ollitert-corruption"
    val mgr = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
    if (mgr != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        mgr.createNotificationChannel(
          android.app.NotificationChannel(
            channelId,
            getString(R.string.notif_channel_corruption_name),
            android.app.NotificationManager.IMPORTANCE_HIGH,
          ).apply { description = getString(R.string.notif_channel_corruption_desc) }
        )
      }
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
        Log.w(logTag, "POST_NOTIFICATIONS not granted — corruption notification suppressed")
      } else {
        mgr.notify(NOTIFICATION_ID_CORRUPTION, notification)
      }
    }

    LlmHttpPrefs.clearCorruptedDataStores(this)
  }

  companion object {
    private const val TAG = "LlmHttpService"
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
      val intent = Intent(context, LlmHttpService::class.java).apply {
        putExtra(EXTRA_PORT, port)
        if (modelName != null) putExtra(EXTRA_MODEL_NAME, modelName)
        if (source != null) putExtra(EXTRA_START_SOURCE, source)
      }
      return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start service", e)
        false
      }
    }

    fun stop(context: Context) {
      try {
        context.stopService(Intent(context, LlmHttpService::class.java))
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
      val intent = Intent(context, LlmHttpService::class.java).apply {
        action = ACTION_RELOAD
        putExtra(EXTRA_PORT, port)
        if (modelName != null) putExtra(EXTRA_MODEL_NAME, modelName)
      }
      return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
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
          Intent(context, LlmHttpService::class.java).apply { action = ACTION_RESET_KEEP_ALIVE }
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
    private var activeInstance: LlmHttpService? = null

    fun updateConfigValues(configValues: Map<String, Any>) {
      val instance = activeInstance ?: return
      synchronized(instance.inferenceLock) {
        instance.defaultModel?.let { model ->
          model.configValues = configValues.toMap()
          // Update thinking state in metrics so the Status screen pill reflects the change
          ServerMetrics.setThinkingEnabled(
            model.llmSupportThinking && (configValues[com.ollitert.llm.server.data.ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
          )
        }
      }
    }
  }
}
