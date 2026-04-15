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
import com.ollitert.llm.server.R
import com.ollitert.llm.server.MainActivity
import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.common.getWifiIpAddress
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
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
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private var currentPort: Int = DEFAULT_PORT
  private val logTag = "LlmHttpService"
  private val requestCounter = AtomicLong(0)
  /** Incremented each time a new model load is initiated; stale warmup threads check this to bail out. */
  private val loadGeneration = AtomicLong(0)

  // Notification state — saved after warmup so we can refresh the notification with live request count
  private var notifContentIntent: PendingIntent? = null
  private var notifStopIntent: PendingIntent? = null
  private var notifCopyIntent: PendingIntent? = null
  private var notifEndpointUrl: String? = null
  private var notifModelName: String? = null

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
   * sits idle with the screen off. Acquired when the server starts, released in onDestroy().
   */
  private var wakeLock: android.os.PowerManager.WakeLock? = null

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
        packageName = packageName,
        assetReader = {
          try { assets.open("model_allowlist.json").reader().readText() } catch (e: Exception) { Log.w(logTag, "Failed to read bundled model_allowlist.json", e); null }
        },
      )
      modelLifecycle = LlmHttpModelLifecycle(context = this, allowlistLoader = allowlistLoader)
      // Create a partial wake lock to keep the CPU awake while the server is running.
      // Acquired in onStartCommand once the server starts, released in onDestroy.
      val pm = getSystemService(POWER_SERVICE) as? android.os.PowerManager
      wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "OlliteRT::Server")?.apply {
        setReferenceCounted(false)
      }
      LlmHttpNotificationHelper.createChannel(this)
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
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      startForeground(LlmHttpNotificationHelper.NOTIFICATION_ID, placeholderNotification)
    }

    // Keep CPU awake for the entire server lifetime so the HTTP server stays reachable
    // on locked/idle devices (e.g. dedicated "closet server" use case).
    wakeLock?.acquire()

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
          model.initializing = false
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
          try { ServerLlmModelHelper.cleanUp(cachedModel) {} } catch (_: Exception) {}
          cachedModel.instance = null
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
    val model = pickModelByName(resolvedModelName)
    if (model == null) {
      val msg = "Model '$resolvedModelName' not found"
      Log.e(logTag, "Model '$resolvedModelName' not found — cannot start server")
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
      model.configValues = overrides.toMutableMap()
      Log.i(logTag, "Applied ${overrides.size} config overrides from reload caller")
    }
    // Verify model files actually exist on disk.
    val modelPath = model.getPath(context = this)
    if (!java.io.File(modelPath).exists()) {
      val msg = "Model files not found on disk"
      Log.e(logTag, "Model files not found at $modelPath for ${model.name} — cannot start server")
      ServerMetrics.onServerError(msg)
      ServerMetrics.incrementErrorCount(ErrorCategory.MODEL_LOAD)
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = model.name, category = EventCategory.MODEL)
      stopSelf()
      return START_NOT_STICKY
    }
    // Cancel any in-flight warmup by bumping the generation counter
    val thisGeneration = loadGeneration.incrementAndGet()

    // Persist for recovery after process death.
    LlmHttpPrefs.setLastModelName(this, model.name)
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
    val executor = Executors.newSingleThreadExecutor()
    val inferenceLock = Any()
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
    server = LlmHttpServer(
      port = port,
      serviceContext = this,
      endpointHandlers = handlers,
      modelLifecycle = modelLifecycle,
      json = json,
      nextRequestId = { nextRequestId() },
      getRequestCount = { requestCounter.get() },
      emitDebugStackTrace = { t, src, name -> emitDebugStackTrace(t, src, name) },
    )
    try {
      server?.start()
    } catch (e: Exception) {
      // Java's BindException says "Address already in use" — rewrite to mention the port explicitly
      val reason = if (e is java.net.BindException || e.message?.contains("Address already in use") == true)
        "Port $port is already in use" else (e.message?.take(120) ?: "Unknown error")
      val msg = "Server failed to start: $reason"
      Log.e(logTag, msg, e)
      ServerMetrics.onServerError(msg)
      ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = model.name, category = EventCategory.SERVER)
      stopSelf()
      return START_NOT_STICKY
    }

    synchronized(modelLifecycle.keepAliveLock) { defaultModel = model }

    Thread {
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
            val availMb = stat.availableBytes / (1024 * 1024)
            throw RuntimeException(
              "Not enough storage to load model (${availMb}MB available, " +
              "need at least ${MIN_STORAGE_FOR_MODEL_INIT_BYTES / (1024 * 1024)}MB). " +
              "Free up space and try again."
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
            latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
            Log.i(logTag, "Previous cleanup finished, proceeding with model load")
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
          // only the test inference message ("Hola") is skipped.
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
            throw RuntimeException("Model initialization failed: $initErr")
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
          try { ServerLlmModelHelper.cleanUp(model) {} } catch (_: Exception) {}
          model.instance = null
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
          val debugBody = org.json.JSONObject().apply {
            put("type", "debug_model_config")
            put("name", model.name)
            put("path", model.getPath(this@LlmHttpService))
            put("size_bytes", model.totalBytes)
            put("config", org.json.JSONObject(model.configValues.mapValues { it.value.toString() }))
            put("capabilities", org.json.JSONObject().apply {
              put("vision", model.llmSupportImage)
              put("audio", model.llmSupportAudio)
              put("thinking", model.llmSupportThinking)
            })
          }.toString()
          RequestLogStore.addEvent("Loaded model configuration", level = LogLevel.DEBUG, modelName = model.name, category = EventCategory.MODEL, body = debugBody)
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
        val chatTpl = if (LlmHttpPrefs.isCustomPromptsEnabled(this@LlmHttpService))
          LlmHttpPrefs.getChatTemplate(this@LlmHttpService, model.name) else ""
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
        if (chatTpl.isNotBlank()) {
          RequestLogStore.addEvent(
            "Chat template active: \"${chatTpl.take(120)}\"${if (chatTpl.length > 120) "…" else ""}",
            modelName = model.name,
            category = EventCategory.PROMPT,
            // Structured JSON body — full prompt text for the log card's expandable text box.
            // Schema: {"type":"prompt_active","prompt_type":"chat_template","text":"..."}
            body = org.json.JSONObject().apply {
              put("type", "prompt_active")
              put("prompt_type", "chat_template")
              put("text", chatTpl)
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
          try { ServerLlmModelHelper.cleanUp(model) {} } catch (_: Exception) {}
          model.instance = null
          return@Thread
        }
        // OOM during model load: close the native Engine/Conversation to release memory.
        // Just nullifying the instance pointer leaks GB-scale native memory until GC
        // finalizes the Java wrapper — which may never happen if heap pressure is low.
        if (t is OutOfMemoryError) {
          try { ServerLlmModelHelper.cleanUp(model) {} } catch (_: Exception) {}
          defaultModel?.instance = null
          System.gc()
        }
        Log.e(logTag, "Failed to load model ${model.name}", t)
        emitDebugStackTrace(t, "model_load", model.name)
        pendingReloadAfterLoad.set(null)  // Clear queued reload — don't apply stale config to a future model
        val msg = t.message?.take(120) ?: "Unknown error during model initialization"
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
      }
    }.start()

    return START_STICKY
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    // TRIM_MEMORY_RUNNING_CRITICAL = 15: the system is critically low on memory and the process
    // is running. This fires just before the OOM killer would kill the process. Log it so users
    // can see "System memory pressure" in the Logs tab before a crash, rather than the app dying
    // silently. The GC hint doesn't free the model's native memory (which is the bulk of our
    // footprint) but helps release JVM wrapper objects sooner.
    @Suppress("DEPRECATION")
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
    server?.stop()
    val modelName = defaultModel?.name

    // Collect models that need native cleanup (Engine + Conversation close).
    // These operations can take seconds for multi-GB models and must NOT run on the main
    // thread — doing so causes an ANR ("Input dispatching timed out") when the user taps
    // Stop Server, because onDestroy runs on the main thread.
    val modelsToCleanUp = mutableListOf<Model>()
    defaultModel?.let { model ->
      modelsToCleanUp.add(model)
      model.initializing = false
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
    if (wakeLock?.isHeld == true) wakeLock?.release()
    wakeLock = null
    logger.shutdown()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  // Model lifecycle methods — delegated to LlmHttpModelLifecycle.kt
  private fun pickModelByName(name: String) = modelLifecycle.pickModelByName(name)
  private fun selectModel(requestedModel: String?) = modelLifecycle.selectModel(requestedModel)
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
    val traceBody = org.json.JSONObject().apply {
      put("type", "debug_stacktrace")
      put("source", source)
      put("trace", t.stackTraceToString())
    }.toString()
    RequestLogStore.addEvent(
      "Exception in $source — stack trace",
      level = LogLevel.DEBUG,
      modelName = modelName,
      category = EventCategory.SERVER,
      body = traceBody,
    )
  }

  companion object {
    private const val TAG = "LlmHttpService"
    const val EXTRA_PORT = "extra_port"
    const val EXTRA_MODEL_NAME = "extra_model_name"
    const val DEFAULT_PORT = 8000
    const val ACTION_STOP = "com.ollitert.llm.server.STOP_SERVER"
    const val ACTION_RELOAD = "com.ollitert.llm.server.RELOAD_SERVER"
    const val ACTION_RESET_KEEP_ALIVE = "com.ollitert.llm.server.RESET_KEEP_ALIVE"
    /** Conservative minimum free storage before attempting model init.
     *  LiteRT's Engine creates XNNPack weight caches that can be hundreds of MB. */
    private const val MIN_STORAGE_FOR_MODEL_INIT_BYTES = 500L * 1024 * 1024

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

    fun start(context: Context, port: Int = DEFAULT_PORT, modelName: String? = null): Boolean {
      val intent = Intent(context, LlmHttpService::class.java).apply {
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
      activeInstance?.defaultModel?.let { model ->
        model.configValues = configValues.toMutableMap()
        // Update thinking state in metrics so the Status screen pill reflects the change
        ServerMetrics.setThinkingEnabled(
          model.llmSupportThinking && (configValues[com.ollitert.llm.server.data.ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
        )
      }
    }
  }
}
