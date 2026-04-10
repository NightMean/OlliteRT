package com.ollitert.llm.server.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ollitert.llm.server.MainActivity
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.getWifiIpAddress
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.AllowedModel
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.ui.navigation.ServerStatus
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
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

  private var server: NanoServer? = null
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private var currentPort: Int = DEFAULT_PORT
  private var defaultModel: Model? = null
  private val modelCache: MutableMap<String, Model> = mutableMapOf()
  private val logTag = "LlmHttpService"
  private val maxBodyBytes = 512 * 1024
  private val requestCounter = AtomicLong(0)
  /** Incremented each time a new model load is initiated; stale warmup threads check this to bail out. */
  private val loadGeneration = AtomicLong(0)

  // Notification state — saved after warmup so we can refresh the notification with live request count
  private var notifContentIntent: PendingIntent? = null
  private var notifStopIntent: PendingIntent? = null
  private var notifCopyIntent: PendingIntent? = null
  private var notifEndpointUrl: String? = null
  private var notifModelName: String? = null

  /** Builds the LiteRT systemInstruction from the per-model system prompt stored in prefs. */
  private fun buildSystemInstruction(modelName: String): Contents? {
    if (!LlmHttpPrefs.isCustomPromptsEnabled(this)) return null
    val prompt = LlmHttpPrefs.getSystemPrompt(this, modelName)
    if (prompt.isBlank()) return null
    return Contents.of(listOf(Content.Text(prompt)))
  }

  private lateinit var logger: LlmHttpLogger
  private lateinit var allowlistLoader: LlmHttpAllowlistLoader

  override fun onCreate() {
    super.onCreate()
    activeInstance = this
    logger = LlmHttpLogger(
      logDir = { getExternalFilesDir(null)?.let { File(it, "ollitert") } },
      isEnabled = { LlmHttpPrefs.isPayloadLoggingEnabled(this) },
    )
    allowlistLoader = LlmHttpAllowlistLoader(
      externalFilesDir = getExternalFilesDir(null),
      packageName = packageName,
      assetReader = {
        try { assets.open("model_allowlist.json").reader().readText() } catch (_: Exception) { null }
      },
    )
    val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val ch = NotificationChannel(CHANNEL_ID, getString(R.string.llm_http_channel_name), NotificationManager.IMPORTANCE_LOW)
      mgr.createNotificationChannel(ch)
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Handle stop action from notification
    if (intent?.action == ACTION_STOP) {
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
    startForeground(
      NOTIFICATION_ID,
      buildNotification(
        title = "OlliteRT",
        text = "Starting…",
        contentIntent = placeholderContentIntent,
        showProgress = true,
      ),
    )

    // Handle reload action: clean up current model first, then proceed with normal start
    if (intent?.action == ACTION_RELOAD) {
      Log.i(logTag, "Reload requested — cleaning up current model before restart")
      server?.stop()
      defaultModel?.let { model ->
        try {
          ServerLlmModelHelper.cleanUp(model) {}
        } catch (e: Exception) {
          Log.w(logTag, "Error cleaning up model during reload: ${e.message}")
        }
        model.instance = null
        model.initializing = false
      }
      defaultModel = null
      modelCache.clear()
      ServerMetrics.onServerStopped()
      // Fall through to normal start logic below
    }

    val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: LlmHttpPrefs.getPort(this)
    val requestedModelName = intent?.getStringExtra(EXTRA_MODEL_NAME)
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
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = resolvedModelName, category = EventCategory.MODEL)
      pendingConfigOverrides = null
      stopSelf()
      return START_NOT_STICKY
    }
    // Apply pending config overrides from the reload caller (e.g. InferenceSettingsSheet).
    pendingConfigOverrides?.let { overrides ->
      model.configValues = overrides.toMutableMap()
      Log.i(logTag, "Applied ${overrides.size} config overrides from reload caller")
      pendingConfigOverrides = null
    }
    // Verify model files actually exist on disk.
    val modelPath = model.getPath(context = this)
    if (!java.io.File(modelPath).exists()) {
      val msg = "Model files not found on disk"
      Log.e(logTag, "Model files not found at $modelPath for ${model.name} — cannot start server")
      ServerMetrics.onServerError(msg)
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
    updateNotification(
      title = "Loading model: ${model.name}",
      text = "Please wait, this may take a moment...",
      contentIntent = contentIntent,
      showProgress = true,
    )

    server?.stop()
    server = NanoServer(port)
    try {
      server?.start()
    } catch (e: Exception) {
      val msg = "Server failed to start on port $port: ${e.message?.take(120) ?: "Unknown error"}"
      Log.e(logTag, msg, e)
      ServerMetrics.onServerError(msg)
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = model.name, category = EventCategory.SERVER)
      stopSelf()
      return START_NOT_STICKY
    }

    defaultModel = model

    Thread {
      try {
        val loadStart = SystemClock.elapsedRealtime()
        val eagerVision = LlmHttpPrefs.isEagerVisionInit(this@LlmHttpService)
        val supportImage = model.llmSupportImage && eagerVision
        val supportAudio = model.llmSupportAudio
        if (LlmHttpPrefs.isWarmupEnabled(this@LlmHttpService)) {
          server?.warmUpModel(model)
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
        RequestLogStore.addEvent("Model ready: ${model.name} (${SystemClock.elapsedRealtime() - loadStart}ms)", modelName = model.name, category = EventCategory.MODEL)
        // Check for queued reload (user changed reinit settings while model was loading)
        val queued = pendingReloadAfterLoad
        if (queued != null) {
          pendingReloadAfterLoad = null
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
        updateNotification(
          title = "OlliteRT Server Running",
          text = "Model: ${model.name}\nAPI URL: $endpointUrl",
          contentIntent = contentIntent,
          stopIntent = stopIntent,
          copyIntent = copyIntent,
        )
      } catch (e: Exception) {
        // Only report error if this is still the current load
        if (loadGeneration.get() != thisGeneration) {
          Log.w(logTag, "Warmup for ${model.name} failed but a newer load was initiated — ignoring")
          return@Thread
        }
        Log.e(logTag, "Failed to load model ${model.name}", e)
        pendingReloadAfterLoad = null  // Clear queued reload — don't apply stale config to a future model
        val msg = e.message?.take(120) ?: "Unknown error during model initialization"
        ServerMetrics.onServerError(msg)
        RequestLogStore.addEvent("Model load failed: $msg", level = LogLevel.ERROR, modelName = model.name, category = EventCategory.MODEL)
        // Update notification to show error state
        updateNotification(
          title = "Model Load Failed",
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
    // TRIM_MEMORY_RUNNING_CRITICAL = 15
    if (level >= 15) System.gc()
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    stopSelf()
  }

  override fun onDestroy() {
    activeInstance = null
    // Invalidate any in-flight warmup thread so it won't transition to RUNNING after we stop
    loadGeneration.incrementAndGet()
    server?.stop()
    val modelName = defaultModel?.name
    // Unload the model to free memory
    defaultModel?.let { model ->
      model.instance = null
      model.initializing = false
    }
    defaultModel = null
    notifContentIntent = null
    notifStopIntent = null
    notifCopyIntent = null
    notifEndpointUrl = null
    notifModelName = null
    pendingReloadAfterLoad = null
    ServerMetrics.onServerStopped()
    if (modelName != null) {
      RequestLogStore.addEvent("Server stopped", modelName = modelName, category = EventCategory.SERVER)
    }
    if (LlmHttpPrefs.isClearLogsOnStop(this)) {
      RequestLogStore.clear()
    }
    logger.shutdown()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun pickModelByName(name: String): Model? {
    val allowlist = allowlistLoader.load()
    val importsDir = File(getExternalFilesDir(null), "__imports")
    val match = allowlist.firstOrNull { it.name.equals(name, ignoreCase = true) }
      ?: return null
    val model = LlmHttpModelFactory.buildAllowedModel(match, importsDir)
    model.preProcess()
    // Restore persisted inference config so settings survive app/service restarts
    val savedConfig = com.ollitert.llm.server.data.LlmHttpPrefs.getInferenceConfig(this, model.name)
    if (savedConfig != null) {
      val restored = model.configValues.toMutableMap()
      for ((key, savedValue) in savedConfig) {
        if (key in restored) {
          val config = model.configs.find { it.key.label == key }
          if (config != null) {
            restored[key] = com.ollitert.llm.server.data.convertValueToTargetType(savedValue, config.valueType)
          } else {
            restored[key] = savedValue
          }
        }
      }
      model.configValues = restored
    }
    return model
  }

  private fun selectModel(requestedModel: String?): Model? {
    val requested = requestedModel?.trim().orEmpty()
    if (requested.isEmpty() || requested.equals("local", ignoreCase = true) ||
      requested.equals("default", ignoreCase = true)
    ) {
      return defaultModel
    }
    val allowed = LlmHttpModelResolver.selectAllowedModel(allowlistLoader.load(), requested)
      ?: return defaultModel
    return modelCache.getOrPut(allowed.name) {
      LlmHttpModelFactory.buildAllowedModel(
        allowedModel = allowed,
        importsDir = File(getExternalFilesDir(null), "__imports"),
      )
    }
  }

  private fun nextRequestId(): String {
    ServerMetrics.incrementRequestCount()
    if (LlmHttpPrefs.isNotifShowRequestCount(this)) {
      refreshRunningNotification()
    }
    return "r${requestCounter.incrementAndGet()}"
  }

  /** Update the foreground notification with the current request count. */
  private fun refreshRunningNotification() {
    val ci = notifContentIntent ?: return
    val name = notifModelName ?: return
    val url = notifEndpointUrl ?: return
    val count = ServerMetrics.requestCount.value
    val reqLabel = if (count == 1L) "1 request" else "$count requests"
    updateNotification(
      title = "OlliteRT Server Running",
      text = "Model: $name\nRequests: $reqLabel\nAPI URL: $url",
      contentIntent = ci,
      stopIntent = notifStopIntent,
      copyIntent = notifCopyIntent,
    )
  }

  private fun logEvent(message: String) {
    Log.i(logTag, "LLM_HTTP $message")
    logger.logEvent(message)
  }

  private fun logPayload(label: String, body: String, requestId: String) {
    logger.logPayload(label, body, requestId)
  }

  private inner class NanoServer(port: Int) : NanoHTTPD("0.0.0.0", port) {
    private val executor = Executors.newSingleThreadExecutor()
    private val inferenceLock = Any()

    /**
     * Builds a config snapshot with per-request overrides applied.
     * Returns null if no overrides are needed. Used for streaming requests
     * where the config must be applied on the executor thread, not the NanoHTTPD thread.
     */
    private fun buildPerRequestConfig(
      model: Model,
      temperature: Double? = null,
      topP: Double? = null,
      topK: Int? = null,
      maxTokens: Int? = null,
    ): Map<String, Any>? {
      if (temperature == null && topP == null && topK == null && maxTokens == null) return null
      val overridden = model.configValues.toMutableMap()
      temperature?.let { overridden[com.ollitert.llm.server.data.ConfigKeys.TEMPERATURE.label] = it.toFloat() }
      topP?.let { overridden[com.ollitert.llm.server.data.ConfigKeys.TOPP.label] = it.toFloat() }
      topK?.let { overridden[com.ollitert.llm.server.data.ConfigKeys.TOPK.label] = it }
      maxTokens?.let {
        val engineMax = (model.configValues[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
        if (engineMax != null) {
          overridden[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] = it.coerceAtMost(engineMax)
        } else {
          overridden[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] = it
        }
      }
      return overridden
    }

    /**
     * Temporarily overrides model sampler config for the duration of a BLOCKING request.
     * For streaming requests, use [buildPerRequestConfig] + configSnapshot parameter instead,
     * since the config must be applied on the executor thread (not the NanoHTTPD thread).
     */
    private inline fun <R> withPerRequestConfig(
      model: Model,
      temperature: Double? = null,
      topP: Double? = null,
      topK: Int? = null,
      maxTokens: Int? = null,
      block: () -> R,
    ): R {
      if (temperature == null && topP == null && topK == null && maxTokens == null) return block()
      val originalConfig = model.configValues
      try {
        val overridden = originalConfig.toMutableMap()
        temperature?.let { overridden[com.ollitert.llm.server.data.ConfigKeys.TEMPERATURE.label] = it.toFloat() }
        topP?.let { overridden[com.ollitert.llm.server.data.ConfigKeys.TOPP.label] = it.toFloat() }
        topK?.let { overridden[com.ollitert.llm.server.data.ConfigKeys.TOPK.label] = it }
        maxTokens?.let {
          // Cap to engine's configured max to avoid exceeding EngineConfig.maxNumTokens
          val engineMax = (originalConfig[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
          if (engineMax != null) {
            overridden[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] = it.coerceAtMost(engineMax)
          } else {
            overridden[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] = it
          }
        }
        model.configValues = overridden
        return block()
      } finally {
        model.configValues = originalConfig
      }
    }

    /**
     * Truncates model output at the first occurrence of any stop sequence.
     * Returns the truncated text (or original if no stop sequence found).
     */
    private fun applyStopSequences(text: String, stopSequences: List<String>?): Pair<String, Boolean> {
      if (stopSequences.isNullOrEmpty()) return text to false
      var earliest = text.length
      for (stop in stopSequences) {
        val idx = text.indexOf(stop)
        if (idx in 0 until earliest) earliest = idx
      }
      return if (earliest < text.length) text.substring(0, earliest) to true
      else text to false
    }

    /**
     * Injects a JSON mode instruction into the prompt when response_format is requested.
     */
    private fun applyResponseFormat(prompt: String, responseFormat: ResponseFormat?): String {
      if (responseFormat == null || responseFormat.type == "text") return prompt
      val instruction = when (responseFormat.type) {
        "json_object" -> "Respond with valid JSON only. Do not include any text, explanation, or markdown outside the JSON object.\n\n"
        "json_schema" -> "Respond with valid JSON only. Output only the JSON object, nothing else.\n\n"
        else -> return prompt
      }
      return instruction + prompt
    }

    override fun serve(session: IHTTPSession): Response {
      val startMs = SystemClock.elapsedRealtime()
      val method = session.method.name
      val path = session.uri
      val clientIp = session.remoteIpAddress
      // NanoHTTPD lowercases all header names
      val requestOrigin = session.headers["origin"]
      // Handle CORS preflight (no logging needed)
      if (session.method == NanoHTTPD.Method.OPTIONS) {
        return corsOk(requestOrigin)
      }

      // Add a pending log entry immediately so it appears in the Logs tab
      val logId = "log-${System.currentTimeMillis()}-${requestCounter.get()}"
      RequestLogStore.add(
        RequestLogEntry(
          id = logId,
          method = method,
          path = path,
          modelName = defaultModel?.name,
          clientIp = clientIp,
          isPending = true,
        )
      )

      var requestBodySnapshot: String? = null
      var responseBodySnapshot: String? = null
      val response = try {
        if (!LlmHttpRouteResolver.isSupportedMethod(session.method)) {
          methodNotAllowed()
        } else {
          val route = LlmHttpRouteResolver.resolve(session.method, session.uri)
          val authError = if (route?.requiresAuth == true) requireAuth(session) else null
          if (route == null) {
            // Check if it's a known OpenAI endpoint we don't support
            val unsupportedMsg = LlmHttpRouteResolver.getUnsupportedEndpointMessage(session.uri)
            if (unsupportedMsg != null) jsonError(Response.Status.NOT_FOUND, unsupportedMsg)
            else notFound()
          } else if (authError != null) {
            authError
          } else {
            // Update the pending entry with the request body once parsed
            val captureBody = { body: String ->
              requestBodySnapshot = body
              RequestLogStore.update(logId) { it.copy(requestBody = body) }
            }
            when (route.handler) {
              LlmHttpRouteHandler.PING -> {
                responseBodySnapshot = "{\"status\":\"ok\"}"
                okJsonText(responseBodySnapshot!!)
              }
              LlmHttpRouteHandler.HEALTH -> {
                responseBodySnapshot = healthPayload()
                okJsonText(responseBodySnapshot!!)
              }
              LlmHttpRouteHandler.SERVER_INFO -> {
                val body = serverInfoPayload()
                responseBodySnapshot = body
                okJsonText(body)
              }
              LlmHttpRouteHandler.METRICS -> {
                val body = LlmHttpPrometheusRenderer.render()
                responseBodySnapshot = body
                newFixedLengthResponse(
                  Response.Status.OK,
                  LlmHttpPrometheusRenderer.CONTENT_TYPE,
                  body,
                )
              }
              LlmHttpRouteHandler.MODELS -> {
                val body = modelsPayload()
                responseBodySnapshot = body
                okJsonText(body)
              }
              LlmHttpRouteHandler.MODEL_DETAIL -> {
                val body = modelDetailPayload(session.uri)
                if (body != null) {
                  responseBodySnapshot = body
                  okJsonText(body)
                } else {
                  notFound("model_not_found")
                }
              }
              LlmHttpRouteHandler.GENERATE -> handleGenerate(session, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
              LlmHttpRouteHandler.COMPLETIONS -> handleCompletions(session, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
              LlmHttpRouteHandler.CHAT_COMPLETIONS -> handleChatCompletion(session, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
              LlmHttpRouteHandler.RESPONSES -> handleResponses(session, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
            }
          }
        }
      } catch (t: Throwable) {
        responseBodySnapshot = t.message
        jsonError(Response.Status.INTERNAL_ERROR, t.message ?: "internal_error")
      }

      // Finalize the log entry with response data.
      // For streaming responses (SSE), the streaming callbacks in streamLlm/streamChatLlm
      // handle their own log updates (partialText during generation, responseBody on done),
      // so we only update metadata here and leave isPending = true for them.
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      val statusCode = response.status?.requestStatus ?: 200
      val isStreaming = response.mimeType == "text/event-stream"
      val isThinking = responseBodySnapshot?.contains("<think>") == true
      RequestLogStore.update(logId) {
        // If the cancel handler already finalized this entry (user tapped Stop), don't overwrite it.
        if (it.isCancelled) return@update it.copy(
          requestBody = requestBodySnapshot ?: it.requestBody,
        )
        val level = when {
          statusCode !in 200..299 -> LogLevel.ERROR
          it.isCompacted -> LogLevel.WARNING
          else -> LogLevel.INFO
        }
        // For non-streaming error responses, the handler already set responseBody with the
        // detailed error JSON (e.g. from LiteRT). Preserve it if responseBodySnapshot is null
        // (captureResponse is only called for success responses).
        val finalResponseBody = if (isStreaming) it.responseBody
          else (responseBodySnapshot ?: it.responseBody)
        // Extract actual token counts from LiteRT error messages (e.g. "6579 >= 4000")
        // to replace our rough charLen/4 estimate with exact numbers from the engine.
        val actualTokens = finalResponseBody?.let { body -> extractActualTokenCounts(body) }
        it.copy(
          requestBody = requestBodySnapshot ?: it.requestBody,
          responseBody = finalResponseBody,
          statusCode = statusCode,
          latencyMs = if (isStreaming) it.latencyMs else elapsedMs,
          isStreaming = isStreaming,
          isThinking = isThinking,
          modelName = defaultModel?.name,
          level = level,
          isPending = if (isStreaming) it.isPending else false,
          inputTokenEstimate = actualTokens?.first ?: it.inputTokenEstimate,
          maxContextTokens = actualTokens?.second ?: it.maxContextTokens,
          isExactTokenCount = actualTokens != null || it.isExactTokenCount,
        )
      }
      // x-request-id: standard request tracing header used by Open WebUI and other clients
      response.addHeader("x-request-id", logId)
      return addCorsHeaders(response, requestOrigin)
    }

    private fun handleGenerate(session: IHTTPSession, captureBody: (String) -> Unit = {}, captureResponse: (String) -> Unit = {}, logId: String? = null): Response {
      val requestId = nextRequestId()
      val payload = HashMap<String, String>()
      session.parseBody(payload)
      val raw = payload["postData"] ?: return badRequest("empty body")
      val parsed = LlmHttpBodyParser.parse(raw, maxBodyBytes)
        ?: run {
          logEvent("request_rejected id=$requestId endpoint=/generate reason=payload_too_large bytes=${LlmHttpBodyParser.bodySizeBytes(raw)}")
          return payloadTooLarge()
        }
      captureBody(parsed.body)
      logPayload("POST /generate raw", parsed.body, requestId)
      val req = json.decodeFromString<GenReq>(parsed.body)
      val model = selectModel(null) ?: return badRequest("llm error")
      // Apply prompt compaction for raw prompts (only trimming is possible)
      val trimPromptsGen = LlmHttpPrefs.isAutoTrimPrompts(this@LlmHttpService)
      val maxContextGen = (model.configValues[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
      val compactionResultGen = LlmHttpPromptCompactor.compactRawPrompt(req.prompt, maxContextGen, trimPromptsGen)
      if (compactionResultGen.compacted) {
        val details = compactionResultGen.strategies.joinToString(", ")
        logEvent("prompt_compacted id=$requestId endpoint=/generate strategies=[$details]")
        if (logId != null) {
          RequestLogStore.update(logId) { it.copy(isCompacted = true, compactionDetails = details, compactedPrompt = compactionResultGen.prompt) }
        }
      }
      val prompt = compactionResultGen.prompt
      // Store context utilization data in the log entry for per-request display
      val maxCtxGen = (maxContextGen ?: 0).toLong()
      if (logId != null) {
        val inputEst = (prompt.length / 4).toLong().coerceAtLeast(if (prompt.isNotEmpty()) 1L else 0L)
        RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtxGen) }
      }
      logPayload("POST /generate prompt", prompt, requestId)
      logEvent("request_start id=$requestId endpoint=/generate bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} model=default")
      ServerMetrics.onInferenceStarted()
      val (text, llmError) = runLlm(model, prompt, requestId, "/generate", logId = logId)
      ServerMetrics.onInferenceCompleted()
      if (text == null) return badRequest(enrichLlmError(llmError ?: "llm error"))
      // Token counts are estimates (charLen / 4) — LiteRT SDK has no standalone tokenizer API
      val promptTokens = (prompt.length / 4).coerceAtLeast(if (prompt.isNotEmpty()) 1 else 0)
      val completionTokens = (text.length / 4).coerceAtLeast(if (text.isNotEmpty()) 1 else 0)
      val timings = buildTimings(promptTokens, completionTokens)
      val responseJson = json.encodeToString(GenRes(text = text, usage = Usage(promptTokens, completionTokens), timings = timings))
      captureResponse(responseJson)
      return okJsonText(responseJson)
    }

    private fun handleChatCompletion(session: IHTTPSession, captureBody: (String) -> Unit = {}, captureResponse: (String) -> Unit = {}, logId: String? = null): Response {
      val requestId = nextRequestId()
      val payload = HashMap<String, String>()
      session.parseBody(payload)
      val raw = payload["postData"] ?: return badRequest("empty body")
      val parsed = LlmHttpBodyParser.parse(raw, maxBodyBytes)
        ?: run {
          logEvent("request_rejected id=$requestId endpoint=/v1/chat/completions reason=payload_too_large bytes=${LlmHttpBodyParser.bodySizeBytes(raw)}")
          return payloadTooLarge()
        }
      captureBody(parsed.body)
      logPayload("POST /v1/chat/completions raw", parsed.body, requestId)
      val req = json.decodeFromString<ChatRequest>(parsed.body)
      val toolChoiceStr = LlmHttpRequestAdapter.resolveToolChoice(req.tool_choice)
      if (req.tools.isNullOrEmpty() && toolChoiceStr == "required")
        return badRequest("tool_choice required but tools empty")
      val requestedId = LlmHttpBridgeUtils.resolveRequestedModelId(req.model)
      val model = selectModel(req.model) ?: return notFound("model_not_found")
      val chatTemplate = if (LlmHttpPrefs.isCustomPromptsEnabled(this@LlmHttpService))
        LlmHttpPrefs.getChatTemplate(this@LlmHttpService, model.name).ifBlank { null } else null

      // Build prompt with progressive compaction if context window is exceeded.
      // Three independent toggles for progressive prompt compaction:
      // "Truncate History" (drop older messages), "Compact Tool Schemas" (reduce tool definitions,
      // useful for Home Assistant), "Trim Prompt" (hard-cut as last resort).
      val hasTools = !req.tools.isNullOrEmpty() && toolChoiceStr != "none"
      val truncateHistory = LlmHttpPrefs.isAutoTruncateHistory(this@LlmHttpService)
      val compactToolSchemas = LlmHttpPrefs.isCompactToolSchemas(this@LlmHttpService)
      val trimPrompts = LlmHttpPrefs.isAutoTrimPrompts(this@LlmHttpService)
      val maxContext = (model.configValues[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()

      val compactionResult = LlmHttpPromptCompactor.compactChatPrompt(
        messages = req.messages,
        tools = if (hasTools) req.tools else null,
        toolChoice = toolChoiceStr,
        chatTemplate = chatTemplate,
        maxContext = maxContext,
        truncateHistory = truncateHistory,
        compactToolSchemas = compactToolSchemas,
        trimPrompts = trimPrompts,
      )

      if (compactionResult.compacted) {
        val details = compactionResult.strategies.joinToString(", ")
        logEvent("prompt_compacted id=$requestId endpoint=/v1/chat/completions strategies=[$details] estimatedTokens=${LlmHttpPromptCompactor.estimateTokens(compactionResult.prompt)} maxContext=$maxContext")
        if (logId != null) {
          RequestLogStore.update(logId) { it.copy(isCompacted = true, compactionDetails = details, compactedPrompt = compactionResult.prompt) }
        }
      }

      // Apply response_format JSON mode prompt injection
      var prompt = applyResponseFormat(compactionResult.prompt, req.response_format)
      // Store context utilization data in the log entry for per-request display
      val maxCtxChat = (maxContext ?: 0).toLong()
      if (logId != null) {
        val inputEst = (prompt.length / 4).toLong().coerceAtLeast(if (prompt.isNotEmpty()) 1L else 0L)
        RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtxChat) }
      }
      logPayload("POST /v1/chat/completions prompt", prompt, requestId)
      // Extract images for multimodal models (before blank-prompt check so image-only requests work).
      val images = if (model.llmSupportImage) decodeImageDataUris(req.messages) else emptyList()

      logEvent("request_start id=$requestId endpoint=/v1/chat/completions bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} images=${images.size} model=$requestedId resolved=${model.name}")

      if (prompt.isBlank() && images.isEmpty()) {
        logEvent("request_empty id=$requestId endpoint=/v1/chat/completions")
        return okJsonText(json.encodeToString(emptyChatResponse(model.name)))
      }

      val includeUsage = req.stream_options?.include_usage == true
      val effectiveMaxTokens = req.max_completion_tokens ?: req.max_tokens

      // Apply per-request sampler overrides (temperature, top_p, top_k, max_tokens).
      // These are picked up by resetConversation() before inference — no model reload needed.
      // For streaming: config is applied inside the executor thread (via configSnapshot) to avoid
      // a race where the NanoHTTPD thread restores config before the executor reads it.
      // For non-streaming: withPerRequestConfig wraps the blocking call safely.
      val stopSeqs = req.stop.ifEmpty { null }
      return if (req.stream == true) {
        val configSnapshot = buildPerRequestConfig(model, req.temperature, req.top_p, req.top_k, effectiveMaxTokens)
        ServerMetrics.onInferenceStarted()
        streamChatLlm(model, prompt, requestId, "/v1/chat/completions", timeoutSeconds = 120, images = images, logId = logId, includeUsage = includeUsage, stopSequences = stopSeqs, tools = if (hasTools) req.tools else null, configSnapshot = configSnapshot)
      } else {
        withPerRequestConfig(model, req.temperature, req.top_p, req.top_k, effectiveMaxTokens) {
          ServerMetrics.onInferenceStarted()
          val (rawText, llmError) = runLlm(model, prompt, requestId, "/v1/chat/completions", timeoutSeconds = 120, images = images, logId = logId)
          ServerMetrics.onInferenceCompleted()
          if (rawText == null) {
            val errorMsg = enrichLlmError(llmError ?: "llm error")
            if (logId != null) {
              val errorJson = LlmHttpResponseRenderer.renderJsonError(errorMsg)
              RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR) }
            }
            return@withPerRequestConfig badRequest(errorMsg)
          }
          val (text, _) = applyStopSequences(rawText, stopSeqs)

          val promptTokens = (prompt.length / 4).coerceAtLeast(1)

          // Check if the model output contains tool call(s) — supports parallel calls
          if (hasTools) {
            val toolCalls = LlmHttpToolCallParser.parseAll(text, req.tools!!)
            if (toolCalls.isNotEmpty()) {
              logEvent("request_tool_calls id=$requestId endpoint=/v1/chat/completions tools=${toolCalls.joinToString(",") { it.function.name }} count=${toolCalls.size}")
              val completionTokens = (toolCalls.sumOf { it.function.arguments.length } / 4).coerceAtLeast(1)
              val timings = buildTimings(promptTokens, completionTokens)
              val responseJson = json.encodeToString(chatResponseWithToolCalls(model.name, toolCalls, promptLen = prompt.length, timings = timings))
              captureResponse(responseJson)
              return@withPerRequestConfig okJsonText(responseJson)
            }
          }

          val completionTokens = (text.length / 4).coerceAtLeast(if (text.isNotEmpty()) 1 else 0)
          val timings = buildTimings(promptTokens, completionTokens)
          val responseJson = json.encodeToString(chatResponseWithText(model.name, text, promptLen = prompt.length, timings = timings))
          captureResponse(responseJson)
          okJsonText(responseJson)
        }
      }
    }

    private fun handleCompletions(session: IHTTPSession, captureBody: (String) -> Unit = {}, captureResponse: (String) -> Unit = {}, logId: String? = null): Response {
      val requestId = nextRequestId()
      val payload = HashMap<String, String>()
      session.parseBody(payload)
      val raw = payload["postData"] ?: return badRequest("empty body")
      val parsed = LlmHttpBodyParser.parse(raw, maxBodyBytes)
        ?: run {
          logEvent("request_rejected id=$requestId endpoint=/v1/completions reason=payload_too_large bytes=${LlmHttpBodyParser.bodySizeBytes(raw)}")
          return payloadTooLarge()
        }
      captureBody(parsed.body)
      logPayload("POST /v1/completions raw", parsed.body, requestId)
      val req = json.decodeFromString<CompletionRequest>(parsed.body)
      val model = selectModel(req.model) ?: return notFound("model_not_found")
      // Apply prompt compaction for raw prompts (only trimming is possible)
      val trimPromptsCompl = LlmHttpPrefs.isAutoTrimPrompts(this@LlmHttpService)
      val maxContextCompl = (model.configValues[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
      val compactionResultCompl = LlmHttpPromptCompactor.compactRawPrompt(req.prompt, maxContextCompl, trimPromptsCompl)
      if (compactionResultCompl.compacted) {
        val details = compactionResultCompl.strategies.joinToString(", ")
        logEvent("prompt_compacted id=$requestId endpoint=/v1/completions strategies=[$details] estimatedTokens=${LlmHttpPromptCompactor.estimateTokens(compactionResultCompl.prompt)} maxContext=$maxContextCompl")
        if (logId != null) {
          RequestLogStore.update(logId) { it.copy(isCompacted = true, compactionDetails = details, compactedPrompt = compactionResultCompl.prompt) }
        }
      }
      val prompt = compactionResultCompl.prompt
      // Store context utilization data in the log entry for per-request display
      val maxCtxCompl = (maxContextCompl ?: 0).toLong()
      if (logId != null) {
        val inputEst = (prompt.length / 4).toLong().coerceAtLeast(if (prompt.isNotEmpty()) 1L else 0L)
        RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtxCompl) }
      }
      logEvent("request_start id=$requestId endpoint=/v1/completions bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} model=${model.name}")

      if (prompt.isBlank()) {
        val responseJson = json.encodeToString(CompletionResponse(
          id = "cmpl-${java.util.UUID.randomUUID()}",
          created = System.currentTimeMillis() / 1000,
          model = model.name,
          choices = listOf(CompletionChoice(text = "", index = 0, finish_reason = "stop")),
          usage = Usage(0, 0),
        ))
        captureResponse(responseJson)
        return okJsonText(responseJson)
      }

      // Parse stop sequences from JsonElement (can be String or List<String>)
      val stopSequences: List<String>? = when (req.stop) {
        is JsonNull -> null
        is JsonPrimitive -> req.stop.jsonPrimitive.content.takeIf { it.isNotBlank() }?.let { listOf(it) }
        is JsonArray -> req.stop.jsonArray.map { it.jsonPrimitive.content }
        else -> null
      }

      // Apply per-request sampler overrides
      return withPerRequestConfig(model, req.temperature, req.top_p, topK = null, req.max_tokens) {
        // Streaming completions: fall through to non-streaming for now (TODO: implement streaming)
        ServerMetrics.onInferenceStarted()
        val (rawText, llmError) = runLlm(model, prompt, requestId, "/v1/completions", timeoutSeconds = 120, logId = logId)
        ServerMetrics.onInferenceCompleted()
        if (rawText == null) {
          val errorMsg = enrichLlmError(llmError ?: "llm error")
          if (logId != null) {
            val errorJson = LlmHttpResponseRenderer.renderJsonError(errorMsg)
            RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR) }
          }
          return@withPerRequestConfig badRequest(errorMsg)
        }

        val (text, _) = applyStopSequences(rawText, stopSequences?.ifEmpty { null })
        val promptTokens = (prompt.length / 4).coerceAtLeast(1)
        val completionTokens = (text.length / 4).coerceAtLeast(if (text.isNotEmpty()) 1 else 0)
        val timings = buildTimings(promptTokens, completionTokens)
        val responseJson = json.encodeToString(CompletionResponse(
          id = "cmpl-${java.util.UUID.randomUUID()}",
          created = System.currentTimeMillis() / 1000,
          model = model.name,
          choices = listOf(CompletionChoice(text = text, index = 0, finish_reason = "stop")),
          usage = Usage(promptTokens, completionTokens),
          timings = timings,
        ))
        captureResponse(responseJson)
        okJsonText(responseJson)
      }
    }

    private fun handleResponses(session: IHTTPSession, captureBody: (String) -> Unit = {}, captureResponse: (String) -> Unit = {}, logId: String? = null): Response {
      val requestId = nextRequestId()
      val payload = HashMap<String, String>()
      session.parseBody(payload)
      val raw = payload["postData"] ?: return badRequest("empty body")
      val parsed = LlmHttpBodyParser.parse(raw, maxBodyBytes)
        ?: run {
          logEvent("request_rejected id=$requestId endpoint=/v1/responses reason=payload_too_large bytes=${LlmHttpBodyParser.bodySizeBytes(raw)}")
          return payloadTooLarge()
        }
      captureBody(parsed.body)
      logPayload("POST /v1/responses raw", parsed.body, requestId)
      val req = json.decodeFromString<ResponsesRequest>(parsed.body)
      val toolChoiceStr = LlmHttpRequestAdapter.resolveToolChoice(req.tool_choice)
      if (req.tools.isNullOrEmpty() && toolChoiceStr == "required")
        return badRequest("tool_choice required but tools empty")
      val requestedId = LlmHttpBridgeUtils.resolveRequestedModelId(req.model)
      val model = selectModel(req.model) ?: return notFound("model_not_found")
      val chatTemplateResp = if (LlmHttpPrefs.isCustomPromptsEnabled(this@LlmHttpService))
        LlmHttpPrefs.getChatTemplate(this@LlmHttpService, model.name).ifBlank { null } else null
      // Build prompt with progressive compaction if context window is exceeded
      val truncateHistoryResp = LlmHttpPrefs.isAutoTruncateHistory(this@LlmHttpService)
      val trimPromptsResp = LlmHttpPrefs.isAutoTrimPrompts(this@LlmHttpService)
      val maxContextResp = (model.configValues[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
      val compactionResultResp = LlmHttpPromptCompactor.compactConversationPrompt(
        messages = req.messages ?: req.input,
        chatTemplate = chatTemplateResp,
        maxContext = maxContextResp,
        truncateHistory = truncateHistoryResp,
        trimPrompts = trimPromptsResp,
      )
      if (compactionResultResp.compacted) {
        val details = compactionResultResp.strategies.joinToString(", ")
        logEvent("prompt_compacted id=$requestId endpoint=/v1/responses strategies=[$details] estimatedTokens=${LlmHttpPromptCompactor.estimateTokens(compactionResultResp.prompt)} maxContext=$maxContextResp")
        if (logId != null) {
          RequestLogStore.update(logId) { it.copy(isCompacted = true, compactionDetails = details, compactedPrompt = compactionResultResp.prompt) }
        }
      }
      val prompt = compactionResultResp.prompt
      // Store context utilization data in the log entry for per-request display
      val maxCtxResp = (maxContextResp ?: 0).toLong()
      if (logId != null) {
        val inputEst = (prompt.length / 4).toLong().coerceAtLeast(if (prompt.isNotEmpty()) 1L else 0L)
        RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtxResp) }
      }
      logPayload("POST /v1/responses prompt", prompt, requestId)
      logEvent("request_start id=$requestId endpoint=/v1/responses bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} model=$requestedId resolved=${model.name}")

      if (prompt.isBlank()) {
        logEvent("request_empty id=$requestId endpoint=/v1/responses")
        return emptyResponse(model.name, stream = req.stream == true)
      }

      val hasTools = !req.tools.isNullOrEmpty() && toolChoiceStr != "none"

      // Apply per-request sampler overrides (temperature, top_p, top_k, max_tokens).
      // For streaming: config is applied inside the executor thread (via configSnapshot) to avoid
      // a race where the NanoHTTPD thread restores config before the executor reads it.
      // For non-streaming: withPerRequestConfig wraps the blocking call safely.
      return if (req.stream == true) {
        val configSnapshot = buildPerRequestConfig(model, req.temperature, req.top_p, req.top_k, req.max_output_tokens)
        ServerMetrics.onInferenceStarted()
        streamLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = 90, logId = logId, promptLen = prompt.length, configSnapshot = configSnapshot)
      } else {
        withPerRequestConfig(model, req.temperature, req.top_p, req.top_k, req.max_output_tokens) {
          ServerMetrics.onInferenceStarted()
          val (text, llmError) = runLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = 90, logId = logId)
          ServerMetrics.onInferenceCompleted()
          if (text == null) {
            val errorMsg = enrichLlmError(llmError ?: "llm error")
            if (logId != null) {
              val errorJson = LlmHttpResponseRenderer.renderJsonError(errorMsg)
              RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR) }
            }
            return@withPerRequestConfig badRequest(errorMsg)
          }

          // Check if the model output contains tool call(s)
          if (hasTools) {
            val toolCalls = LlmHttpToolCallParser.parseAll(text, req.tools!!)
            if (toolCalls.isNotEmpty()) {
              // Responses API: use first tool call (Responses API doesn't batch tool calls the same way)
              val responseJson = json.encodeToString(responsesResponseWithToolCall(model.name, toolCalls.first(), promptLen = prompt.length))
              captureResponse(responseJson)
              return@withPerRequestConfig okJsonText(responseJson)
            }
          }

          val responseJson = json.encodeToString(responsesResponseWithText(model.name, text, promptLen = prompt.length))
          captureResponse(responseJson)
          okJsonText(responseJson)
        }
      }
    }

    fun warmUpModel(model: Model) {
      val startMs = SystemClock.elapsedRealtime()
      val eagerVision = LlmHttpPrefs.isEagerVisionInit(this@LlmHttpService)
      val (result, _) = runLlm(model, "Hola", "warmup", "warmup", timeoutSeconds = 10, eagerVisionInit = eagerVision)
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      val snippet = result?.take(80)?.replace("\n", " ") ?: "no response"
      RequestLogStore.addEvent(
        "Sending a warmup message: \"Hola\" → \"$snippet\" (${elapsedMs}ms)",
        modelName = model.name,
        category = EventCategory.MODEL,
      )
    }

    private fun runLlm(
      model: Model,
      prompt: String,
      requestId: String,
      endpoint: String,
      timeoutSeconds: Long = 30,
      images: List<Bitmap> = emptyList(),
      eagerVisionInit: Boolean = false,
      logId: String? = null,
    ): Pair<String?, String?> {
      // Track input tokens (rough estimate: ~4 chars per token)
      ServerMetrics.addTokensIn((prompt.length / 4).toLong().coerceAtLeast(1))
      // Track request modality
      ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = false)

      val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVisionInit)
      val supportAudio = model.llmSupportAudio
      synchronized(this) {
        // Re-initialize if images are requested but engine lacks vision support.
        val needsReinit = model.instance == null ||
          (supportImage && !model.initializedWithVision)
        if (needsReinit) {
          if (model.instance != null) {
            Log.i(logTag, "Re-initializing model with vision support")
            ServerLlmModelHelper.cleanUp(model) {}
          }
          var err = ""
          ServerLlmModelHelper.initialize(
            context = this@LlmHttpService,
            model = model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            onDone = { err = it },
            systemInstruction = buildSystemInstruction(model.name),
          )
          if (err.isNotEmpty()) return null to "Model initialization failed: $err"
          model.initializedWithVision = supportImage
        }
      }
      val enableThinking = model.llmSupportThinking &&
        (model.configValues[com.ollitert.llm.server.data.ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
      val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

      // Register a cancellation callback so the user can stop this request from the Logs screen.
      // For non-streaming requests, calling stopResponse triggers CancellationException in the
      // LiteRT SDK which completes inference early — we then check the flag and return an error.
      val userCancelFlag = java.util.concurrent.atomic.AtomicBoolean(false)
      if (logId != null) {
        RequestLogStore.registerCancellation(logId) {
          userCancelFlag.set(true)
          ServerLlmModelHelper.stopResponse(model)
        }
      }

      val result = LlmHttpInferenceGateway.execute(
        prompt = prompt,
        timeoutSeconds = timeoutSeconds,
        executor = executor,
        inferenceLock = inferenceLock,
        resetConversation = {
          ServerLlmModelHelper.resetConversation(model, supportImage = supportImage, supportAudio = supportAudio, systemInstruction = buildSystemInstruction(model.name))
        },
        runInference = { input, onPartial, onError ->
          ServerLlmModelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partial, done, thought -> onPartial(partial, done, thought) },
            cleanUpListener = {},
            onError = onError,
            images = images,
            extraContext = extraContext,
          )
        },
        cancelInference = { ServerLlmModelHelper.stopResponse(model) },
        elapsedMs = { SystemClock.elapsedRealtime() },
      )
      if (logId != null) RequestLogStore.unregisterCancellation(logId)

      // If the user tapped Stop in the Logs screen, return a cancellation error
      // instead of the (potentially partial) inference output.
      if (userCancelFlag.get()) {
        val keepPartial = LlmHttpPrefs.isKeepPartialResponse(this@LlmHttpService)
        val partial = if (keepPartial && !result.output.isNullOrEmpty()) result.output else null
        if (logId != null) {
          RequestLogStore.update(logId) {
            it.copy(partialText = partial, isPending = false, isCancelled = true, latencyMs = result.totalMs)
          }
        }
        logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=false user_stopped=true outputChars=${result.output?.length ?: 0}")
        return null to "Generation stopped by user in OlliteRT"
      }

      return if (result.error != null) {
        ServerMetrics.incrementErrorCount()
        logEvent("request_error id=$requestId endpoint=$endpoint error=${result.error} totalMs=${result.totalMs} ttfbMs=${result.ttfbMs} outputChars=${result.output?.length ?: 0}")
        null to result.error
      } else {
        val outputLen = result.output?.length ?: 0
        // Rough token estimate: ~4 chars per token
        val inputTokens = (prompt.length / 4).toLong().coerceAtLeast(1)
        val outputTokens = (outputLen / 4).toLong().coerceAtLeast(1)
        val maxCtx = (model.configValues[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] as? Number)?.toLong() ?: 0L
        ServerMetrics.addTokens(outputTokens)
        ServerMetrics.recordLatency(result.totalMs)
        ServerMetrics.recordTtfb(result.ttfbMs)
        if (result.ttfbMs > 0) {
          ServerMetrics.recordInferenceMetrics(inputTokens, outputTokens, result.ttfbMs, result.totalMs - result.ttfbMs, maxCtx)
        }
        logEvent("request_done id=$requestId endpoint=$endpoint totalMs=${result.totalMs} ttfbMs=${result.ttfbMs} outputChars=$outputLen")
        // Prepend thinking content wrapped in <think> tags if present
        val output = if (!result.thinking.isNullOrEmpty()) {
          "<think>${result.thinking}</think>${result.output.orEmpty()}"
        } else {
          result.output
        }
        output to null
      }
    }

    private fun streamLlm(
      model: Model,
      prompt: String,
      requestId: String,
      endpoint: String,
      timeoutSeconds: Long = 90,
      images: List<Bitmap> = emptyList(),
      logId: String? = null,
      promptLen: Int = 0,
      configSnapshot: Map<String, Any>? = null,
    ): Response {
      val streamStartMs = SystemClock.elapsedRealtime()
      // Track input tokens (rough estimate: ~4 chars per token)
      ServerMetrics.addTokensIn((prompt.length / 4).toLong().coerceAtLeast(1))
      // Track request modality
      ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = false)

      val eagerVision = LlmHttpPrefs.isEagerVisionInit(this@LlmHttpService)
      val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVision)
      val supportAudio = model.llmSupportAudio
      synchronized(this) {
        val needsReinit = model.instance == null ||
          (supportImage && !model.initializedWithVision)
        if (needsReinit) {
          if (model.instance != null) {
            Log.i(logTag, "Re-initializing model with vision support (stream)")
            ServerLlmModelHelper.cleanUp(model) {}
          }
          var err = ""
          ServerLlmModelHelper.initialize(
            context = this@LlmHttpService,
            model = model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            onDone = { err = it },
            systemInstruction = buildSystemInstruction(model.name),
          )
          if (err.isNotEmpty()) {
            if (logId != null) {
              val errorJson = LlmHttpResponseRenderer.renderJsonError("model_init_failed: $err")
              RequestLogStore.update(logId) { it.copy(responseBody = errorJson, isPending = false, level = LogLevel.ERROR) }
            }
            return jsonError(Response.Status.INTERNAL_ERROR, "model_init_failed")
          }
          model.initializedWithVision = supportImage
        }
      }

      val enableThinking = model.llmSupportThinking &&
        (model.configValues[com.ollitert.llm.server.data.ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
      val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

      val now = System.currentTimeMillis() / 1000
      val respId = "resp-${java.util.UUID.randomUUID()}"
      val msgId = "msg-${java.util.UUID.randomUUID()}"
      val fullText = StringBuilder()
      val fullThinking = StringBuilder()
      var headerWritten = false
      var thinkingTagOpened = false
      var lastLogUpdateMs = 0L
      // Track time of first content/thinking token for TTFB and decode speed calculations
      var firstTokenMs = 0L
      val streamPreview = LlmHttpPrefs.isStreamLogsPreview(this@LlmHttpService)
      val keepPartial = LlmHttpPrefs.isKeepPartialResponse(this@LlmHttpService)

      val stream = BlockingQueueInputStream()

      // Allow the user to stop this streaming request from the Logs screen.
      if (logId != null) {
        RequestLogStore.registerCancellation(logId) { stream.cancel() }
      }

      // Capture original config so we can restore after streaming completes.
      val originalConfig = if (configSnapshot != null) model.configValues else null

      LlmHttpInferenceGateway.executeStreaming(
        prompt = prompt,
        timeoutSeconds = timeoutSeconds,
        executor = executor,
        inferenceLock = inferenceLock,
        resetConversation = {
          if (configSnapshot != null) model.configValues = configSnapshot
          ServerLlmModelHelper.resetConversation(model, supportImage = supportImage, supportAudio = supportAudio, systemInstruction = buildSystemInstruction(model.name))
        },
        runInference = { input, onPartial, onError ->
          ServerLlmModelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partial, done, thought -> onPartial(partial, done, thought) },
            cleanUpListener = {},
            onError = onError,
            images = images,
            extraContext = extraContext,
          )
        },
        cancelInference = { ServerLlmModelHelper.stopResponse(model) },
        elapsedMs = { SystemClock.elapsedRealtime() },
        onToken = { partial, done, thought ->
          if (stream.isCancelled) {
            if (logId != null) RequestLogStore.unregisterCancellation(logId)
            if (originalConfig != null) model.configValues = originalConfig
            ServerLlmModelHelper.stopResponse(model)
            ServerMetrics.onInferenceCompleted()
            if (logId != null) {
              val cancelledPartial = if (keepPartial && fullText.isNotEmpty()) fullText.toString() else null
              RequestLogStore.update(logId) {
                it.copy(partialText = cancelledPartial, isPending = false, isCancelled = true, latencyMs = SystemClock.elapsedRealtime() - streamStartMs)
              }
            }
            logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=true outputChars=${fullText.length}")
            return@executeStreaming
          }
          try {
            // Capture first token time for TTFB calculation
            if (firstTokenMs == 0L && (partial.isNotEmpty() || !thought.isNullOrEmpty())) {
              firstTokenMs = SystemClock.elapsedRealtime()
            }
            if (!headerWritten) {
              headerWritten = true
              stream.enqueue(LlmHttpResponseRenderer.buildStreamingHeader(model.name, respId, msgId, now))
            }
            // Emit thinking content wrapped in <think> tags
            if (!thought.isNullOrEmpty()) {
              fullThinking.append(thought)
              val thinkText = if (!thinkingTagOpened) {
                thinkingTagOpened = true
                "<think>$thought"
              } else {
                thought
              }
              val esc = LlmHttpBridgeUtils.escapeSseText(thinkText)
              stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
            }
            if (partial.isNotEmpty()) {
              // Close thinking tag before first regular content
              val text = if (thinkingTagOpened) {
                thinkingTagOpened = false
                "</think>$partial"
              } else {
                partial
              }
              fullText.append(partial)
              val esc = LlmHttpBridgeUtils.escapeSseText(text)
              stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
            }
            // Update log with partial text via lightweight flow (debounced to ~300ms).
            // Uses updatePartialText() which emits via a dedicated StateFlow, avoiding
            // full entries-list replacement that would cause entire LazyColumn recomposition.
            if (streamPreview && logId != null && !done) {
              val nowMs = SystemClock.elapsedRealtime()
              if (nowMs - lastLogUpdateMs >= 300) {
                lastLogUpdateMs = nowMs
                RequestLogStore.updatePartialText(logId, fullText.toString())
              }
            }
            if (done) {
              if (logId != null) RequestLogStore.unregisterCancellation(logId)
              if (originalConfig != null) model.configValues = originalConfig
              // Close thinking tag if still open (thinking-only response with no regular content)
              if (thinkingTagOpened) {
                thinkingTagOpened = false
                val esc = LlmHttpBridgeUtils.escapeSseText("</think>")
                stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
              }
              val outputLen = fullText.length
              val inputTokens = (promptLen / 4).toLong().coerceAtLeast(if (promptLen > 0) 1L else 0L)
              val outputTokens = (outputLen / 4).toLong().coerceAtLeast(1)
              val totalLatencyMs = SystemClock.elapsedRealtime() - streamStartMs
              val ttfbMs = if (firstTokenMs > 0) firstTokenMs - streamStartMs else 0L
              val maxCtx = (model.configValues[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] as? Number)?.toLong() ?: 0L
              ServerMetrics.addTokens(outputTokens)
              ServerMetrics.recordLatency(totalLatencyMs)
              ServerMetrics.recordTtfb(ttfbMs)
              if (firstTokenMs > 0) {
                ServerMetrics.recordInferenceMetrics(inputTokens, outputTokens, ttfbMs, totalLatencyMs - ttfbMs, maxCtx)
              }
              ServerMetrics.onInferenceCompleted()
              // Include thinking in the full output for footer/log
              val combinedText = if (fullThinking.isNotEmpty()) {
                "<think>${fullThinking}</think>${fullText}"
              } else {
                fullText.toString()
              }
              val promptTokens = (promptLen / 4).coerceAtLeast(if (promptLen > 0) 1 else 0)
              val completionTokens = (outputLen / 4).coerceAtLeast(if (outputLen > 0) 1 else 0)
              val esc = LlmHttpBridgeUtils.escapeSseText(combinedText)
              stream.enqueue(LlmHttpResponseRenderer.buildStreamingFooter(model.name, respId, msgId, now, esc, inputTokens = promptTokens, outputTokens = completionTokens))
              stream.finish()
              if (logId != null) {
                val responseJson = json.encodeToString(responsesResponseWithText(model.name, combinedText, promptLen = promptLen))
                RequestLogStore.update(logId) {
                  it.copy(
                    responseBody = responseJson,
                    partialText = null,
                    isPending = false,
                    latencyMs = totalLatencyMs,
                    isThinking = fullThinking.isNotEmpty(),
                  )
                }
              }
              logEvent("request_done id=$requestId endpoint=$endpoint streaming=true totalMs=$totalLatencyMs ttfbMs=$ttfbMs outputChars=$outputLen")
            }
          } catch (e: Exception) {
            if (logId != null) RequestLogStore.unregisterCancellation(logId)
            if (originalConfig != null) model.configValues = originalConfig
            ServerMetrics.onInferenceCompleted()
            logEvent("request_error id=$requestId endpoint=$endpoint error=stream_write_failed msg=${e.message} streaming=true")
            if (logId != null) {
              val errorJson = LlmHttpResponseRenderer.renderJsonError("stream_write_failed: ${e.message}")
              RequestLogStore.update(logId) { it.copy(partialText = null, responseBody = errorJson, isPending = false, latencyMs = SystemClock.elapsedRealtime() - streamStartMs, level = LogLevel.ERROR) }
            }
            try { stream.finish() } catch (_: Exception) {}
          }
        },
        onError = { error ->
          if (logId != null) RequestLogStore.unregisterCancellation(logId)
          if (originalConfig != null) model.configValues = originalConfig
          ServerMetrics.onInferenceCompleted()
          ServerMetrics.incrementErrorCount()
          val enrichedError = enrichLlmError(error)
          logEvent("request_error id=$requestId endpoint=$endpoint error=$error streaming=true")
          if (logId != null) {
            val errorJson = LlmHttpResponseRenderer.renderJsonError(enrichedError)
            // Extract actual token counts from LiteRT error (e.g. "4467 >= 4000") to replace charLen/4 estimate
            val actualTokens = extractActualTokenCounts(error)
            RequestLogStore.update(logId) {
              it.copy(
                partialText = null,
                responseBody = errorJson,
                isPending = false,
                latencyMs = SystemClock.elapsedRealtime() - streamStartMs,
                level = LogLevel.ERROR,
                inputTokenEstimate = actualTokens?.first ?: it.inputTokenEstimate,
                maxContextTokens = actualTokens?.second ?: it.maxContextTokens,
                isExactTokenCount = actualTokens != null || it.isExactTokenCount,
              )
            }
          }
          try {
            stream.enqueue("data: ${LlmHttpResponseRenderer.renderJsonError(enrichedError)}\n\n")
            stream.enqueue(LlmHttpResponseRenderer.SSE_DONE)
            stream.finish()
          } catch (_: Exception) {}
        },
      )

      return chunkedSseResponse(stream)
    }

    /** True per-token streaming for /v1/chat/completions using chat.completion.chunk SSE format. */
    private fun streamChatLlm(
      model: Model,
      prompt: String,
      requestId: String,
      endpoint: String,
      timeoutSeconds: Long = 120,
      images: List<Bitmap> = emptyList(),
      logId: String? = null,
      @Suppress("UNUSED_PARAMETER") includeUsage: Boolean = false, // Usage+timings are always sent for client compatibility
      stopSequences: List<String>? = null,
      tools: List<ToolSpec>? = null,
      configSnapshot: Map<String, Any>? = null,
    ): Response {
      val streamStartMs = SystemClock.elapsedRealtime()
      ServerMetrics.addTokensIn((prompt.length / 4).toLong().coerceAtLeast(1))
      ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = false)

      val eagerVision = LlmHttpPrefs.isEagerVisionInit(this@LlmHttpService)
      val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVision)
      val supportAudio = model.llmSupportAudio
      synchronized(this) {
        val needsReinit = model.instance == null ||
          (supportImage && !model.initializedWithVision)
        if (needsReinit) {
          if (model.instance != null) {
            Log.i(logTag, "Re-initializing model with vision support (stream-chat)")
            ServerLlmModelHelper.cleanUp(model) {}
          }
          var err = ""
          ServerLlmModelHelper.initialize(
            context = this@LlmHttpService,
            model = model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            onDone = { err = it },
            systemInstruction = buildSystemInstruction(model.name),
          )
          if (err.isNotEmpty()) {
            if (logId != null) {
              val errorJson = LlmHttpResponseRenderer.renderJsonError("model_init_failed: $err")
              RequestLogStore.update(logId) { it.copy(responseBody = errorJson, isPending = false, level = LogLevel.ERROR) }
            }
            return jsonError(Response.Status.INTERNAL_ERROR, "model_init_failed")
          }
          model.initializedWithVision = supportImage
        }
      }

      val enableThinking = model.llmSupportThinking &&
        (model.configValues[com.ollitert.llm.server.data.ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
      val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

      val now = System.currentTimeMillis() / 1000
      val chatId = "chatcmpl-${java.util.UUID.randomUUID()}"
      val fullText = StringBuilder()
      val fullThinking = StringBuilder()
      var headerWritten = false
      var thinkingTagOpened = false
      var lastLogUpdateMs = 0L
      // Track time of first content/thinking token for TTFB and decode speed calculations
      var firstTokenMs = 0L
      val streamPreview = LlmHttpPrefs.isStreamLogsPreview(this@LlmHttpService)
      val keepPartial = LlmHttpPrefs.isKeepPartialResponse(this@LlmHttpService)

      val stream = BlockingQueueInputStream()
      // When tools are present, buffer all tokens instead of streaming them.
      // We can't know if the output is a tool call until generation completes,
      // so we must buffer first, then emit either tool_calls or content.
      val bufferForTools = tools != null

      // Allow the user to stop this streaming request from the Logs screen.
      if (logId != null) {
        RequestLogStore.registerCancellation(logId) { stream.cancel() }
      }

      // Capture original config so we can restore after streaming completes.
      // configSnapshot (if non-null) is applied in resetConversation on the executor thread
      // to avoid a race where config is restored before inference reads it.
      val originalConfig = if (configSnapshot != null) model.configValues else null

      LlmHttpInferenceGateway.executeStreaming(
        prompt = prompt,
        timeoutSeconds = timeoutSeconds,
        executor = executor,
        inferenceLock = inferenceLock,
        resetConversation = {
          if (configSnapshot != null) model.configValues = configSnapshot
          ServerLlmModelHelper.resetConversation(model, supportImage = supportImage, supportAudio = supportAudio, systemInstruction = buildSystemInstruction(model.name))
        },
        runInference = { input, onPartial, onError ->
          ServerLlmModelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partial, done, thought -> onPartial(partial, done, thought) },
            cleanUpListener = {},
            onError = onError,
            images = images,
            extraContext = extraContext,
          )
        },
        cancelInference = { ServerLlmModelHelper.stopResponse(model) },
        elapsedMs = { SystemClock.elapsedRealtime() },
        onToken = { partial, done, thought ->
          if (stream.isCancelled) {
            if (logId != null) RequestLogStore.unregisterCancellation(logId)
            if (originalConfig != null) model.configValues = originalConfig
            ServerLlmModelHelper.stopResponse(model)
            ServerMetrics.onInferenceCompleted()
            if (logId != null) {
              val cancelledPartial = if (keepPartial && fullText.isNotEmpty()) fullText.toString() else null
              RequestLogStore.update(logId) {
                it.copy(partialText = cancelledPartial, isPending = false, isCancelled = true, latencyMs = SystemClock.elapsedRealtime() - streamStartMs)
              }
            }
            logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=true outputChars=${fullText.length}")
            return@executeStreaming
          }
          try {
            // Capture first token time for TTFB calculation
            if (firstTokenMs == 0L && (partial.isNotEmpty() || !thought.isNullOrEmpty())) {
              firstTokenMs = SystemClock.elapsedRealtime()
            }
            if (!bufferForTools) {
              if (!headerWritten) {
                headerWritten = true
                stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFirstChunk(chatId, model.name, now))
              }
              // Emit thinking content wrapped in <think> tags
              if (!thought.isNullOrEmpty()) {
                fullThinking.append(thought)
                val thinkText = if (!thinkingTagOpened) {
                  thinkingTagOpened = true
                  "<think>$thought"
                } else {
                  thought
                }
                stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, thinkText))
              }
            } else {
              // Still accumulate thinking text when buffering for tools
              if (!thought.isNullOrEmpty()) fullThinking.append(thought)
            }
            if (partial.isNotEmpty()) {
              fullText.append(partial)
              // Check for stop sequences in accumulated text
              if (!stopSequences.isNullOrEmpty()) {
                val currentText = fullText.toString()
                var stopIdx = currentText.length
                for (stop in stopSequences) {
                  val idx = currentText.indexOf(stop)
                  if (idx in 0 until stopIdx) stopIdx = idx
                }
                if (stopIdx < currentText.length) {
                  // Stop sequence found — truncate and finish streaming
                  fullText.clear()
                  fullText.append(currentText.substring(0, stopIdx))
                  ServerLlmModelHelper.stopResponse(model)
                  // Don't emit the stop-triggering token; fall through to done block below
                }
              }
              if (!bufferForTools) {
                val text = if (thinkingTagOpened) {
                  thinkingTagOpened = false
                  "</think>$partial"
                } else {
                  partial
                }
                stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, text))
              }
            }
            // Update log with partial text via lightweight flow (debounced to ~300ms).
            // Uses updatePartialText() which emits via a dedicated StateFlow, avoiding
            // full entries-list replacement that would cause entire LazyColumn recomposition.
            if (streamPreview && logId != null && !done) {
              val nowMs = SystemClock.elapsedRealtime()
              if (nowMs - lastLogUpdateMs >= 300) {
                lastLogUpdateMs = nowMs
                RequestLogStore.updatePartialText(logId, fullText.toString())
              }
            }
            if (done) {
              if (logId != null) RequestLogStore.unregisterCancellation(logId)
              if (originalConfig != null) model.configValues = originalConfig
              val outputLen = fullText.length
              val inputTokens = (prompt.length / 4).toLong().coerceAtLeast(1)
              val outputTokens = (outputLen / 4).toLong().coerceAtLeast(1)
              val totalLatencyMs = SystemClock.elapsedRealtime() - streamStartMs
              val ttfbMs = if (firstTokenMs > 0) firstTokenMs - streamStartMs else 0L
              val maxCtx = (model.configValues[com.ollitert.llm.server.data.ConfigKeys.MAX_TOKENS.label] as? Number)?.toLong() ?: 0L
              ServerMetrics.addTokens(outputTokens)
              ServerMetrics.recordLatency(totalLatencyMs)
              ServerMetrics.recordTtfb(ttfbMs)
              if (firstTokenMs > 0) {
                ServerMetrics.recordInferenceMetrics(inputTokens, outputTokens, ttfbMs, totalLatencyMs - ttfbMs, maxCtx)
              }
              ServerMetrics.onInferenceCompleted()
              val promptTokens = (prompt.length / 4).coerceAtLeast(1)
              val completionTokens = (outputLen / 4).coerceAtLeast(if (outputLen > 0) 1 else 0)

              // Check for tool call(s) in completed output — supports parallel calls
              val parsedToolCalls = if (tools != null) LlmHttpToolCallParser.parseAll(fullText.toString(), tools) else emptyList()

              if (bufferForTools && parsedToolCalls.isNotEmpty()) {
                // Emit proper OpenAI streaming tool_calls format with per-call indexing
                stream.enqueue(LlmHttpResponseRenderer.buildChatStreamToolCallChunks(chatId, model.name, now, parsedToolCalls))
              } else {
                // Emit buffered content (if we were buffering) or close the stream normally
                if (bufferForTools) {
                  // Was buffering but no tool call — emit all content at once
                  stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFirstChunk(chatId, model.name, now))
                  if (fullThinking.isNotEmpty()) {
                    stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, "<think>${fullThinking}</think>"))
                  }
                  if (fullText.isNotEmpty()) {
                    stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, fullText.toString()))
                  }
                } else {
                  if (thinkingTagOpened) {
                    thinkingTagOpened = false
                    stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, "</think>"))
                  }
                }
                stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFinalChunk(chatId, model.name, now, "stop"))
              }
              // Build non-standard performance timings (used by Open WebUI and other local LLM clients)
              val timings = buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
              val timingsJson = if (timings != null) json.encodeToString(timings) else null
              // Always send usage+timings chunk, not just when stream_options.include_usage is set.
              // Most local LLM clients (Open WebUI, etc.) expect usage data in every streaming
              // response for token tracking and performance display.
              stream.enqueue(LlmHttpResponseRenderer.buildChatStreamUsageChunk(chatId, model.name, now, promptTokens, completionTokens, timingsJson))
              stream.enqueue(LlmHttpResponseRenderer.SSE_DONE)
              stream.finish()
              if (logId != null) {
                val combinedText = if (fullThinking.isNotEmpty()) {
                  "<think>${fullThinking}</think>${fullText}"
                } else {
                  fullText.toString()
                }
                val responseJson = if (parsedToolCalls.isNotEmpty()) {
                  json.encodeToString(chatResponseWithToolCalls(model.name, parsedToolCalls, promptLen = prompt.length, timings = timings))
                } else {
                  json.encodeToString(chatResponseWithText(model.name, combinedText, promptLen = prompt.length, timings = timings))
                }
                RequestLogStore.update(logId) {
                  it.copy(
                    responseBody = responseJson,
                    partialText = null,
                    isPending = false,
                    latencyMs = totalLatencyMs,
                    isThinking = fullThinking.isNotEmpty(),
                  )
                }
              }
              logEvent("request_done id=$requestId endpoint=$endpoint streaming=true totalMs=$totalLatencyMs ttfbMs=$ttfbMs outputChars=$outputLen${if (parsedToolCalls.isNotEmpty()) " tool_calls=${parsedToolCalls.joinToString(",") { it.function.name }} count=${parsedToolCalls.size}" else ""}")
            }
          } catch (e: Exception) {
            if (logId != null) RequestLogStore.unregisterCancellation(logId)
            if (originalConfig != null) model.configValues = originalConfig
            ServerMetrics.onInferenceCompleted()
            logEvent("request_error id=$requestId endpoint=$endpoint error=stream_write_failed msg=${e.message} streaming=true")
            if (logId != null) {
              val errorJson = LlmHttpResponseRenderer.renderJsonError("stream_write_failed: ${e.message}")
              RequestLogStore.update(logId) { it.copy(partialText = null, responseBody = errorJson, isPending = false, latencyMs = SystemClock.elapsedRealtime() - streamStartMs, level = LogLevel.ERROR) }
            }
            try { stream.finish() } catch (_: Exception) {}
          }
        },
        onError = { error ->
          if (logId != null) RequestLogStore.unregisterCancellation(logId)
          if (originalConfig != null) model.configValues = originalConfig
          ServerMetrics.onInferenceCompleted()
          ServerMetrics.incrementErrorCount()
          val enrichedError = enrichLlmError(error)
          logEvent("request_error id=$requestId endpoint=$endpoint error=$error streaming=true")
          if (logId != null) {
            val errorJson = LlmHttpResponseRenderer.renderJsonError(enrichedError)
            // Extract actual token counts from LiteRT error (e.g. "4467 >= 4000") to replace charLen/4 estimate
            val actualTokens = extractActualTokenCounts(error)
            RequestLogStore.update(logId) {
              it.copy(
                partialText = null,
                responseBody = errorJson,
                isPending = false,
                latencyMs = SystemClock.elapsedRealtime() - streamStartMs,
                level = LogLevel.ERROR,
                inputTokenEstimate = actualTokens?.first ?: it.inputTokenEstimate,
                maxContextTokens = actualTokens?.second ?: it.maxContextTokens,
                isExactTokenCount = actualTokens != null || it.isExactTokenCount,
              )
            }
          }
          try {
            stream.enqueue("data: ${LlmHttpResponseRenderer.renderJsonError(enrichedError)}\n\n")
            stream.enqueue(LlmHttpResponseRenderer.SSE_DONE)
            stream.finish()
          } catch (_: Exception) {}
        },
      )

      return chunkedSseResponse(stream)
    }

    // ── Response helpers ──────────────────────────────────────────────────────

    /** Returns the full text wrapped in OpenAI chat.completion.chunk SSE events. */
    private fun chatSseResponse(modelId: String, text: String): Response {
      val now = System.currentTimeMillis() / 1000
      val chatId = "chatcmpl-${java.util.UUID.randomUUID()}"
      val payload = buildString {
        append(LlmHttpResponseRenderer.buildChatStreamFirstChunk(chatId, modelId, now))
        append(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, modelId, now, text))
        append(LlmHttpResponseRenderer.buildChatStreamFinalChunk(chatId, modelId, now))
        append(LlmHttpResponseRenderer.SSE_DONE)
      }
      return chunkedSseResponse(payload)
    }

    private fun sseResponse(modelId: String, text: String): Response =
      chunkedSseResponse(LlmHttpResponseRenderer.buildTextSsePayload(modelId, text))

    private fun sseResponseToolCall(modelId: String, toolCall: ToolCall, promptLen: Int = 0): Response {
      // Token counts are estimates (charLen / 4) — LiteRT SDK has no standalone tokenizer API
      val inputTokens = (promptLen / 4).coerceAtLeast(if (promptLen > 0) 1 else 0)
      val outputTokens = (toolCall.function.arguments.length / 4).coerceAtLeast(1)
      return chunkedSseResponse(LlmHttpResponseRenderer.buildToolCallSsePayload(modelId, toolCall, inputTokens, outputTokens))
    }

    private fun emptyResponse(modelId: String, stream: Boolean): Response {
      val body = responsesResponseWithText(modelId, "")
      return if (stream) sseResponse(modelId, "") else okJsonText(json.encodeToString(body))
    }

    private fun chunkedSseResponse(stream: BlockingQueueInputStream): Response =
      FlushingSseResponse(stream)

    private fun chunkedSseResponse(payload: String): Response {
      val resp = newChunkedResponse(Response.Status.OK, "text/event-stream", ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)))
      resp.addHeader("Cache-Control", "no-cache")
      resp.addHeader("Connection", "keep-alive")
      return resp
    }

    private fun okJsonText(body: String): Response =
      newFixedLengthResponse(Response.Status.OK, "application/json", body)

    private fun requireAuth(session: IHTTPSession): Response? {
      val expected = LlmHttpPrefs.getBearerToken(this@LlmHttpService)
      if (expected.isBlank()) return null
      val header = session.headers["authorization"] ?: session.headers["Authorization"] ?: ""
      return if (LlmHttpBridgeUtils.isBearerAuthorized(expected, header)) null else unauthorized("unauthorized")
    }

    private fun jsonError(status: Response.Status, error: String): Response =
      newFixedLengthResponse(status, "application/json", LlmHttpResponseRenderer.renderJsonError(error))

    private fun badRequest(msg: String) = jsonError(Response.Status.BAD_REQUEST, msg)
    private fun notFound(error: String = "not_found") = jsonError(Response.Status.NOT_FOUND, error)
    private fun unauthorized(error: String) =
      jsonError(Response.Status.UNAUTHORIZED, error).also { it.addHeader("WWW-Authenticate", "Bearer") }
    private fun methodNotAllowed() = jsonError(Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed")
    private fun payloadTooLarge() = jsonError(Response.Status.BAD_REQUEST, "payload_too_large")

    /**
     * Enrich LLM error messages with actionable hints for known error patterns.
     * Context overflow errors get a note about OlliteRT compaction settings.
     */
    private fun enrichLlmError(error: String): String {
      val lower = error.lowercase()
      val isContextOverflow = lower.contains("too long") || lower.contains("exceed") ||
        lower.contains(">=") || lower.contains("context") || lower.contains("too many tokens")
      return if (isContextOverflow) {
        "$error — Try increasing Max Tokens in the model's Inference Settings within OlliteRT. If the model doesn't support a larger context window, you can enable prompt compaction (Truncate History, Compact Tool Schemas, or Trim Prompt) in OlliteRT Settings as a fallback, though this may reduce response quality."
      } else {
        error
      }
    }

    /**
     * Extract actual token counts from LiteRT error messages.
     * LiteRT reports context overflow as "N >= M" (e.g. "6579 >= 4000").
     * Returns (actualInputTokens, maxContextTokens) or null if not a context overflow error.
     */
    private fun extractActualTokenCounts(responseBody: String): Pair<Long, Long>? {
      // Pattern: "6579 >= 4000" — actual input tokens exceeding max context
      val match = Regex("(\\d+)\\s*>=\\s*(\\d+)").find(responseBody) ?: return null
      val actual = match.groupValues[1].toLongOrNull() ?: return null
      val max = match.groupValues[2].toLongOrNull() ?: return null
      if (actual <= 0 || max <= 0) return null
      return actual to max
    }

    /**
     * Adds CORS headers to a response based on the configured allowed origins
     * and the request's Origin header. Uses [LlmHttpCorsHelper] for origin matching.
     */
    private fun addCorsHeaders(response: Response, requestOrigin: String?): Response {
      val allowedOrigins = LlmHttpPrefs.getCorsAllowedOrigins(this@LlmHttpService)
      val headers = LlmHttpCorsHelper.buildCorsHeaders(allowedOrigins, requestOrigin)
      for ((key, value) in headers) {
        response.addHeader(key, value)
      }
      return response
    }

    private fun corsOk(requestOrigin: String?): Response {
      val resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
      return addCorsHeaders(resp, requestOrigin)
    }
  }

  // ── Notification helpers ────────────────────────────────────────────────────

  private fun buildNotification(
    title: String,
    text: String,
    contentIntent: PendingIntent,
    stopIntent: PendingIntent? = null,
    copyIntent: PendingIntent? = null,
    showProgress: Boolean = false,
  ): Notification {
    val builder = NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setSmallIcon(R.mipmap.ic_launcher_foreground)
      .setContentIntent(contentIntent)
      .setOngoing(true)
    if (stopIntent != null) {
      builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopIntent)
    }
    if (copyIntent != null) {
      builder.addAction(android.R.drawable.ic_menu_share, "Copy URL", copyIntent)
    }
    if (showProgress) {
      builder.setProgress(0, 0, true) // indeterminate progress bar
    }
    return builder.build()
  }

  private fun updateNotification(
    title: String,
    text: String,
    contentIntent: PendingIntent,
    stopIntent: PendingIntent? = null,
    copyIntent: PendingIntent? = null,
    showProgress: Boolean = false,
  ) {
    val notification = buildNotification(title, text, contentIntent, stopIntent, copyIntent, showProgress)
    val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    mgr.notify(NOTIFICATION_ID, notification)
  }

  // ── Image helpers ──────────────────────────────────────────────────────────

  private fun decodeImageDataUris(messages: List<ChatMessage>): List<Bitmap> {
    val uris = LlmHttpRequestAdapter.extractImageDataUris(messages)
    return uris.mapNotNull { uri ->
      try {
        // Expected format: data:image/jpeg;base64,/9j/4AAQ...
        val base64Data = if (uri.contains(",")) uri.substringAfter(",") else uri
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      } catch (e: Exception) {
        Log.w(logTag, "Failed to decode image data URI", e)
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

  // ── Payload builders ─────────────────────────────────────────────────────────

  private fun serverInfoPayload(): String {
    val status = ServerMetrics.status.value
    val uptimeSeconds = if (ServerMetrics.startedAtMs.value > 0L)
      (System.currentTimeMillis() - ServerMetrics.startedAtMs.value) / 1000 else null
    val info = buildMap {
      put("name", JsonPrimitive("OlliteRT"))
      put("version", JsonPrimitive(com.ollitert.llm.server.BuildConfig.VERSION_NAME))
      put("status", JsonPrimitive(status.name.lowercase()))
      defaultModel?.let { put("model", JsonPrimitive(it.name)) }
      uptimeSeconds?.let { put("uptime_seconds", JsonPrimitive(it)) }
      put("compatibility", JsonPrimitive("openai"))
      put("endpoints", JsonArray(listOf(
        JsonPrimitive("/v1/models"),
        JsonPrimitive("/v1/completions"),
        JsonPrimitive("/v1/chat/completions"),
        JsonPrimitive("/v1/responses"),
        JsonPrimitive("/health"),
        JsonPrimitive("/metrics"),
      )))
    }
    return JsonObject(info).toString()
  }

  private fun healthPayload(): String {
    val status = ServerMetrics.status.value
    val uptimeSeconds = if (ServerMetrics.startedAtMs.value > 0L)
      (System.currentTimeMillis() - ServerMetrics.startedAtMs.value) / 1000 else null
    val info = buildMap {
      put("status", JsonPrimitive(if (status == ServerStatus.RUNNING) "ok" else status.name.lowercase()))
      defaultModel?.let { put("model", JsonPrimitive(it.name)) }
      uptimeSeconds?.let { put("uptime_seconds", JsonPrimitive(it)) }
    }
    return JsonObject(info).toString()
  }

  private fun modelDetailPayload(uri: String): String? {
    val modelId = uri.removePrefix("/v1/models/")
    if (modelId.isBlank()) return null
    val model = defaultModel ?: return null
    // Match against the currently loaded model
    if (!model.name.equals(modelId, ignoreCase = true)) return null
    val item = LlmHttpModelItem(
      id = model.name,
      capabilities = LlmHttpModelCapabilities(
        image = model.llmSupportImage,
        audio = model.llmSupportAudio,
        thinking = model.llmSupportThinking && (model.configValues[com.ollitert.llm.server.data.ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false,
      ),
    )
    return json.encodeToString(LlmHttpModelItem.serializer(), item)
  }

  private fun modelsPayload(): String {
    val model = defaultModel
    if (model == null) {
      Log.i(logTag, "Models list: no model loaded")
      return json.encodeToString(LlmHttpModelList(data = emptyList()))
    }
    Log.i(logTag, "Models list: active model=${model.name}")
    val item = LlmHttpModelItem(
      id = model.name,
      capabilities = LlmHttpModelCapabilities(
        image = model.llmSupportImage,
        audio = model.llmSupportAudio,
        thinking = model.llmSupportThinking && (model.configValues[com.ollitert.llm.server.data.ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false,
      ),
    )
    return json.encodeToString(LlmHttpModelList(data = listOf(item)))
  }

  // ── Response builders ──────────────────────────────────────────────────
  // Token counts in all response builders below are **estimates** (charLen / 4).
  // LiteRT LM SDK has no standalone tokenizer API — see Usage class doc for details.

  /**
   * Build performance timings from the most recent inference metrics.
   * Safe to call right after runLlm() completes — inference is serialized via [inferenceLock],
   * so the ServerMetrics "last" values are guaranteed to be from the current request.
   *
   * Returns null if no valid timing data is available (e.g. TTFB was 0).
   */
  private fun buildTimings(promptTokens: Int, completionTokens: Int): InferenceTimings? {
    val ttfbMs = ServerMetrics.lastTtfbMs.value
    val totalMs = ServerMetrics.lastLatencyMs.value
    if (ttfbMs <= 0 || totalMs <= 0) return null
    val promptMs = ttfbMs.toDouble()
    val predictedMs = (totalMs - ttfbMs).toDouble()
    return InferenceTimings(
      prompt_n = promptTokens,
      prompt_ms = promptMs,
      prompt_per_token_ms = if (promptTokens > 0) promptMs / promptTokens else 0.0,
      prompt_per_second = if (promptMs > 0) promptTokens * 1000.0 / promptMs else 0.0,
      predicted_n = completionTokens,
      predicted_ms = predictedMs,
      predicted_per_token_ms = if (completionTokens > 0) predictedMs / completionTokens else 0.0,
      predicted_per_second = if (predictedMs > 0) completionTokens * 1000.0 / predictedMs else 0.0,
    )
  }

  /**
   * Build performance timings from explicit timing values (for streaming paths
   * where timing data is computed locally, not read from ServerMetrics).
   */
  private fun buildTimingsFromValues(promptTokens: Int, completionTokens: Int, ttfbMs: Long, totalMs: Long): InferenceTimings? {
    if (ttfbMs <= 0 || totalMs <= 0) return null
    val promptMs = ttfbMs.toDouble()
    val predictedMs = (totalMs - ttfbMs).toDouble()
    return InferenceTimings(
      prompt_n = promptTokens,
      prompt_ms = promptMs,
      prompt_per_token_ms = if (promptTokens > 0) promptMs / promptTokens else 0.0,
      prompt_per_second = if (promptMs > 0) promptTokens * 1000.0 / promptMs else 0.0,
      predicted_n = completionTokens,
      predicted_ms = predictedMs,
      predicted_per_token_ms = if (completionTokens > 0) predictedMs / completionTokens else 0.0,
      predicted_per_second = if (predictedMs > 0) completionTokens * 1000.0 / predictedMs else 0.0,
    )
  }

  private fun emptyChatResponse(modelName: String) = ChatResponse(
    id = "chatcmpl-${java.util.UUID.randomUUID()}", created = System.currentTimeMillis() / 1000, model = modelName,
    choices = listOf(ChatChoice(0, ChatMessage("assistant", ChatContent("")), "stop")),
    usage = Usage(0, 0),
  )

  private fun chatResponseWithText(modelName: String, text: String, promptLen: Int = 0, finishReason: String = "stop", timings: InferenceTimings? = null): ChatResponse {
    val promptTokens = (promptLen / 4).coerceAtLeast(if (promptLen > 0) 1 else 0)
    val completionTokens = (text.length / 4).coerceAtLeast(if (text.isNotEmpty()) 1 else 0)
    return ChatResponse(
      id = "chatcmpl-${java.util.UUID.randomUUID()}", created = System.currentTimeMillis() / 1000, model = modelName,
      choices = listOf(ChatChoice(0, ChatMessage("assistant", ChatContent(text)), finishReason)),
      usage = Usage(promptTokens, completionTokens),
      timings = timings,
    )
  }

  private fun chatResponseWithToolCalls(modelName: String, toolCalls: List<ToolCall>, promptLen: Int = 0, timings: InferenceTimings? = null): ChatResponse {
    val promptTokens = (promptLen / 4).coerceAtLeast(if (promptLen > 0) 1 else 0)
    val completionTokens = (toolCalls.sumOf { it.function.arguments.length } / 4).coerceAtLeast(1)
    return ChatResponse(
      id = "chatcmpl-${java.util.UUID.randomUUID()}", created = System.currentTimeMillis() / 1000, model = modelName,
      choices = listOf(ChatChoice(0, ChatMessage("assistant", ChatContent(""), tool_calls = toolCalls), "tool_calls")),
      usage = Usage(promptTokens, completionTokens),
      timings = timings,
    )
  }

  private fun responsesResponseWithText(modelName: String, text: String, promptLen: Int = 0) = ResponsesResponse(
    id = "resp-${java.util.UUID.randomUUID()}", created = System.currentTimeMillis() / 1000, model = modelName,
    output = listOf(RespMessage(content = listOf(RespContent(text = text)))),
    usage = Usage(
      prompt_tokens = (promptLen / 4).coerceAtLeast(if (promptLen > 0) 1 else 0),
      completion_tokens = (text.length / 4).coerceAtLeast(if (text.isNotEmpty()) 1 else 0),
    ),
  )

  private fun responsesResponseWithToolCall(modelName: String, toolCall: ToolCall, promptLen: Int = 0) = ResponsesResponse(
    id = "resp-${java.util.UUID.randomUUID()}", created = System.currentTimeMillis() / 1000, model = modelName,
    output = listOf(RespMessage(content = listOf(RespContent(type = "output_tool_call", text = json.encodeToString(toolCall))), finish_reason = "tool_calls")),
    usage = Usage(
      prompt_tokens = (promptLen / 4).coerceAtLeast(if (promptLen > 0) 1 else 0),
      completion_tokens = (toolCall.function.arguments.length / 4).coerceAtLeast(1),
    ),
  )

  companion object {
    const val EXTRA_PORT = "extra_port"
    const val EXTRA_MODEL_NAME = "extra_model_name"
    const val DEFAULT_PORT = 8000
    const val ACTION_STOP = "com.ollitert.llm.server.STOP_SERVER"
    const val ACTION_RELOAD = "com.ollitert.llm.server.RELOAD_SERVER"
    private const val CHANNEL_ID = "ollitert-server"
    private const val NOTIFICATION_ID = 42

    fun start(context: Context, port: Int = DEFAULT_PORT, modelName: String? = null) {
      val intent = Intent(context, LlmHttpService::class.java).apply {
        putExtra(EXTRA_PORT, port)
        if (modelName != null) putExtra(EXTRA_MODEL_NAME, modelName)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, LlmHttpService::class.java))
    }

    /**
     * Pending config values to apply after the next reload creates a fresh model.
     * Set by [reload] before sending the intent, consumed in [onStartCommand].
     */
    @Volatile
    private var pendingConfigOverrides: Map<String, Any>? = null

    /**
     * Queued reload request to execute after the current model finishes loading.
     * Set by [queueReloadAfterLoad] when the user changes reinit-requiring settings
     * while a model is still loading. Consumed in the warmup thread after [onServerRunning].
     */
    private data class PendingReload(val port: Int, val modelName: String, val configValues: Map<String, Any>?)
    @Volatile
    private var pendingReloadAfterLoad: PendingReload? = null

    /**
     * Queue a reload to execute automatically after the current model finishes loading.
     * If the model is not currently loading, this is a no-op — use [reload] instead.
     */
    fun queueReloadAfterLoad(port: Int, modelName: String, configValues: Map<String, Any>?) {
      pendingReloadAfterLoad = PendingReload(port, modelName, configValues)
    }

    fun reload(context: Context, port: Int = DEFAULT_PORT, modelName: String? = null, configValues: Map<String, Any>? = null) {
      pendingConfigOverrides = configValues
      val intent = Intent(context, LlmHttpService::class.java).apply {
        action = ACTION_RELOAD
        putExtra(EXTRA_PORT, port)
        if (modelName != null) putExtra(EXTRA_MODEL_NAME, modelName)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
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
