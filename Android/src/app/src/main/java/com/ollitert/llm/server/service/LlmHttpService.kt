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
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    val model = if (resolvedModelName != null) {
      pickModelByName(resolvedModelName)
    } else {
      null
    }
    if (model == null) {
      val msg = "Model '${resolvedModelName ?: "unknown"}' not found"
      Log.e(logTag, "No model specified or model '${resolvedModelName}' not found — cannot start server")
      ServerMetrics.onServerError(msg)
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = resolvedModelName)
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
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = model.name)
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
    RequestLogStore.addEvent("Loading model: ${model.name}", modelName = model.name)

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

    // Show loading notification — no stop button since loading can't be interrupted
    val loadingNotification = buildNotification(
      title = "Loading model: ${model.name}",
      text = "Please wait, this may take a moment...",
      contentIntent = contentIntent,
      showProgress = true,
    )
    startForeground(NOTIFICATION_ID, loadingNotification)

    server?.stop()
    server = NanoServer(port)
    try {
      server?.start()
    } catch (e: Exception) {
      val msg = "Server failed to start on port $port: ${e.message?.take(120) ?: "Unknown error"}"
      Log.e(logTag, msg, e)
      ServerMetrics.onServerError(msg)
      RequestLogStore.addEvent(msg, level = LogLevel.ERROR, modelName = model.name)
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
            "Warmup skipped (disabled in Advanced Settings) — model loaded without test inference",
            modelName = model.name,
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
        ServerMetrics.onServerRunning(wifiIp)
        RequestLogStore.addEvent("Model ready: ${model.name} (${SystemClock.elapsedRealtime() - loadStart}ms)", modelName = model.name)
        val sysPrompt = if (LlmHttpPrefs.isCustomPromptsEnabled(this@LlmHttpService))
          LlmHttpPrefs.getSystemPrompt(this@LlmHttpService, model.name) else ""
        val chatTpl = if (LlmHttpPrefs.isCustomPromptsEnabled(this@LlmHttpService))
          LlmHttpPrefs.getChatTemplate(this@LlmHttpService, model.name) else ""
        if (sysPrompt.isNotBlank()) {
          RequestLogStore.addEvent(
            "System prompt active: \"${sysPrompt.take(120)}\"${if (sysPrompt.length > 120) "…" else ""}",
            modelName = model.name,
          )
        }
        if (chatTpl.isNotBlank()) {
          RequestLogStore.addEvent(
            "Chat template active: \"${chatTpl.take(120)}\"${if (chatTpl.length > 120) "…" else ""}",
            modelName = model.name,
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
        val msg = e.message?.take(120) ?: "Unknown error during model initialization"
        ServerMetrics.onServerError(msg)
        RequestLogStore.addEvent("Model load failed: $msg", level = LogLevel.ERROR, modelName = model.name)
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
    ServerMetrics.onServerStopped()
    if (modelName != null) {
      RequestLogStore.addEvent("Server stopped", modelName = modelName)
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

    override fun serve(session: IHTTPSession): Response {
      val startMs = SystemClock.elapsedRealtime()
      val method = session.method.name
      val path = session.uri
      val clientIp = session.remoteIpAddress
      // Handle CORS preflight (no logging needed)
      if (session.method == NanoHTTPD.Method.OPTIONS) {
        return corsOk()
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
            notFound()
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
              LlmHttpRouteHandler.SERVER_INFO -> {
                val body = serverInfoPayload()
                responseBodySnapshot = body
                okJsonText(body)
              }
              LlmHttpRouteHandler.MODELS -> {
                val body = modelsPayload()
                responseBodySnapshot = body
                okJsonText(body)
              }
              LlmHttpRouteHandler.GENERATE -> handleGenerate(session, captureBody = captureBody, captureResponse = { responseBodySnapshot = it })
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
      val level = if (statusCode in 200..299) LogLevel.INFO else LogLevel.ERROR
      RequestLogStore.update(logId) {
        it.copy(
          requestBody = requestBodySnapshot ?: it.requestBody,
          responseBody = if (isStreaming) it.responseBody else responseBodySnapshot,
          statusCode = statusCode,
          latencyMs = if (isStreaming) it.latencyMs else elapsedMs,
          isStreaming = isStreaming,
          isThinking = isThinking,
          modelName = defaultModel?.name,
          level = level,
          isPending = if (isStreaming) it.isPending else false,
        )
      }
      return addCorsHeaders(response)
    }

    private fun handleGenerate(session: IHTTPSession, captureBody: (String) -> Unit = {}, captureResponse: (String) -> Unit = {}): Response {
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
      logPayload("POST /generate prompt", req.prompt, requestId)
      val model = selectModel(null) ?: return badRequest("llm error")
      logEvent("request_start id=$requestId endpoint=/generate bodyBytes=${parsed.bodyBytes} promptChars=${req.prompt.length} model=default")
      ServerMetrics.onInferenceStarted()
      val (text, llmError) = runLlm(model, req.prompt, requestId, "/generate")
      ServerMetrics.onInferenceCompleted()
      if (text == null) return badRequest(llmError ?: "llm error")
      val responseJson = json.encodeToString(GenRes(text = text, usage = Usage(0, 0)))
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
      if (req.tools.isNullOrEmpty() && req.tool_choice == "required")
        return badRequest("tool_choice required but tools empty")
      val requestedId = LlmHttpBridgeUtils.resolveRequestedModelId(req.model)
      val model = selectModel(req.model) ?: return notFound("model_not_found")
      val chatTemplate = if (LlmHttpPrefs.isCustomPromptsEnabled(this@LlmHttpService))
        LlmHttpPrefs.getChatTemplate(this@LlmHttpService, model.name).ifBlank { null } else null
      val prompt = LlmHttpRequestAdapter.buildChatPrompt(req.messages, chatTemplate)
      logPayload("POST /v1/chat/completions prompt", prompt, requestId)
      // Extract images for multimodal models (before blank-prompt check so image-only requests work).
      val images = if (model.llmSupportImage) decodeImageDataUris(req.messages) else emptyList()

      logEvent("request_start id=$requestId endpoint=/v1/chat/completions bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} images=${images.size} model=$requestedId resolved=${model.name}")

      if (prompt.isBlank() && images.isEmpty()) {
        logEvent("request_empty id=$requestId endpoint=/v1/chat/completions")
        return okJsonText(json.encodeToString(emptyChatResponse(model.name)))
      }
      if (!req.tools.isNullOrEmpty() && req.tool_choice != "none") {
        val toolCall = LlmHttpRequestAdapter.synthesizeToolCall(req.tools!!.first(), prompt, "call_${System.currentTimeMillis()}")
        logEvent("request_tool_call id=$requestId endpoint=/v1/chat/completions tool=${toolCall.function.name}")
        return okJsonText(json.encodeToString(chatResponseWithToolCall(model.name, toolCall, promptLen = prompt.length)))
      }

      val includeUsage = req.stream_options?.include_usage == true

      return if (req.stream == true) {
        ServerMetrics.onInferenceStarted()
        streamChatLlm(model, prompt, requestId, "/v1/chat/completions", timeoutSeconds = 120, images = images, logId = logId, includeUsage = includeUsage)
      } else {
        ServerMetrics.onInferenceStarted()
        val (text, llmError) = runLlm(model, prompt, requestId, "/v1/chat/completions", timeoutSeconds = 120, images = images)
        ServerMetrics.onInferenceCompleted()
        if (text == null) return badRequest(llmError ?: "llm error")
        val responseJson = json.encodeToString(chatResponseWithText(model.name, text, promptLen = prompt.length))
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
      if (req.tools.isNullOrEmpty() && req.tool_choice == "required")
        return badRequest("tool_choice required but tools empty")
      val requestedId = LlmHttpBridgeUtils.resolveRequestedModelId(req.model)
      val model = selectModel(req.model) ?: return notFound("model_not_found")
      val chatTemplateResp = if (LlmHttpPrefs.isCustomPromptsEnabled(this@LlmHttpService))
        LlmHttpPrefs.getChatTemplate(this@LlmHttpService, model.name).ifBlank { null } else null
      val prompt = LlmHttpRequestAdapter.buildConversationPrompt(req.messages ?: req.input, chatTemplateResp)
      logPayload("POST /v1/responses prompt", prompt, requestId)
      logEvent("request_start id=$requestId endpoint=/v1/responses bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} model=$requestedId resolved=${model.name}")

      if (prompt.isBlank()) {
        logEvent("request_empty id=$requestId endpoint=/v1/responses")
        return emptyResponse(model.name, stream = req.stream == true)
      }
      if (!req.tools.isNullOrEmpty() && req.tool_choice != "none") {
        val toolCall = LlmHttpRequestAdapter.synthesizeToolCall(req.tools!!.first(), prompt, "call_${System.currentTimeMillis()}")
        return if (req.stream == true) sseResponseToolCall(model.name, toolCall)
        else okJsonText(json.encodeToString(responsesResponseWithToolCall(model.name, toolCall, promptLen = prompt.length)))
      }
      return if (req.stream == true) {
        ServerMetrics.onInferenceStarted()
        val resp = streamLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = 90, logId = logId, promptLen = prompt.length)
        // Note: for streaming, inference completion is signaled inside streamLlm's onToken(done=true)
        resp
      } else {
        ServerMetrics.onInferenceStarted()
        val (text, llmError) = runLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = 90)
        ServerMetrics.onInferenceCompleted()
        if (text == null) return badRequest(llmError ?: "llm error")
        val responseJson = json.encodeToString(responsesResponseWithText(model.name, text, promptLen = prompt.length))
        captureResponse(responseJson)
        okJsonText(responseJson)
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
      return if (result.error != null) {
        ServerMetrics.incrementErrorCount()
        logEvent("request_error id=$requestId endpoint=$endpoint error=${result.error} totalMs=${result.totalMs} ttfbMs=${result.ttfbMs} outputChars=${result.output?.length ?: 0}")
        null to result.error
      } else {
        val outputLen = result.output?.length ?: 0
        // Rough token estimate: ~4 chars per token
        ServerMetrics.addTokens((outputLen / 4).toLong().coerceAtLeast(1))
        ServerMetrics.recordLatency(result.totalMs)
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
          if (err.isNotEmpty()) return jsonError(Response.Status.INTERNAL_ERROR, "model_init_failed")
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
      val streamPreview = LlmHttpPrefs.isStreamLogsPreview(this@LlmHttpService)
      val keepPartial = LlmHttpPrefs.isKeepPartialResponse(this@LlmHttpService)

      val stream = BlockingQueueInputStream()

      LlmHttpInferenceGateway.executeStreaming(
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
        onToken = { partial, done, thought ->
          if (stream.isCancelled) {
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
            // Update log with partial text (debounced to ~150ms)
            if (streamPreview && logId != null && !done) {
              val nowMs = SystemClock.elapsedRealtime()
              if (nowMs - lastLogUpdateMs >= 150) {
                lastLogUpdateMs = nowMs
                val snapshot = fullText.toString()
                RequestLogStore.update(logId) { it.copy(partialText = snapshot) }
              }
            }
            if (done) {
              // Close thinking tag if still open (thinking-only response with no regular content)
              if (thinkingTagOpened) {
                thinkingTagOpened = false
                val esc = LlmHttpBridgeUtils.escapeSseText("</think>")
                stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
              }
              val outputLen = fullText.length
              ServerMetrics.addTokens((outputLen / 4).toLong().coerceAtLeast(1))
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
                    latencyMs = SystemClock.elapsedRealtime() - streamStartMs,
                    isThinking = fullThinking.isNotEmpty(),
                  )
                }
              }
              logEvent("request_done id=$requestId endpoint=$endpoint streaming=true outputChars=$outputLen")
            }
          } catch (e: Exception) {
            ServerMetrics.onInferenceCompleted()
            logEvent("request_error id=$requestId endpoint=$endpoint error=stream_write_failed streaming=true")
            if (logId != null) {
              RequestLogStore.update(logId) { it.copy(partialText = null, isPending = false, latencyMs = SystemClock.elapsedRealtime() - streamStartMs) }
            }
            try { stream.finish() } catch (_: Exception) {}
          }
        },
        onError = { error ->
          ServerMetrics.onInferenceCompleted()
          ServerMetrics.incrementErrorCount()
          logEvent("request_error id=$requestId endpoint=$endpoint error=$error streaming=true")
          if (logId != null) {
            RequestLogStore.update(logId) {
              it.copy(partialText = null, isPending = false, latencyMs = SystemClock.elapsedRealtime() - streamStartMs, level = LogLevel.ERROR)
            }
          }
          try {
            stream.enqueue("data: {\"error\":\"$error\"}\n\n")
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
      includeUsage: Boolean = false,
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
          if (err.isNotEmpty()) return jsonError(Response.Status.INTERNAL_ERROR, "model_init_failed")
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
      val streamPreview = LlmHttpPrefs.isStreamLogsPreview(this@LlmHttpService)
      val keepPartial = LlmHttpPrefs.isKeepPartialResponse(this@LlmHttpService)

      val stream = BlockingQueueInputStream()

      LlmHttpInferenceGateway.executeStreaming(
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
        onToken = { partial, done, thought ->
          if (stream.isCancelled) {
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
            if (partial.isNotEmpty()) {
              val text = if (thinkingTagOpened) {
                thinkingTagOpened = false
                "</think>$partial"
              } else {
                partial
              }
              fullText.append(partial)
              stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, text))
            }
            // Update log with partial text (debounced to ~150ms)
            if (streamPreview && logId != null && !done) {
              val nowMs = SystemClock.elapsedRealtime()
              if (nowMs - lastLogUpdateMs >= 150) {
                lastLogUpdateMs = nowMs
                val snapshot = fullText.toString()
                RequestLogStore.update(logId) { it.copy(partialText = snapshot) }
              }
            }
            if (done) {
              if (thinkingTagOpened) {
                thinkingTagOpened = false
                stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, "</think>"))
              }
              val outputLen = fullText.length
              ServerMetrics.addTokens((outputLen / 4).toLong().coerceAtLeast(1))
              ServerMetrics.onInferenceCompleted()
              val promptTokens = (prompt.length / 4).coerceAtLeast(1)
              val completionTokens = (outputLen / 4).coerceAtLeast(if (outputLen > 0) 1 else 0)
              stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFinalChunk(chatId, model.name, now))
              if (includeUsage) {
                stream.enqueue(LlmHttpResponseRenderer.buildChatStreamUsageChunk(chatId, model.name, now, promptTokens, completionTokens))
              }
              stream.enqueue(LlmHttpResponseRenderer.SSE_DONE)
              stream.finish()
              if (logId != null) {
                val combinedText = if (fullThinking.isNotEmpty()) {
                  "<think>${fullThinking}</think>${fullText}"
                } else {
                  fullText.toString()
                }
                val responseJson = json.encodeToString(chatResponseWithText(model.name, combinedText, promptLen = prompt.length))
                RequestLogStore.update(logId) {
                  it.copy(
                    responseBody = responseJson,
                    partialText = null,
                    isPending = false,
                    latencyMs = SystemClock.elapsedRealtime() - streamStartMs,
                    isThinking = fullThinking.isNotEmpty(),
                  )
                }
              }
              logEvent("request_done id=$requestId endpoint=$endpoint streaming=true outputChars=$outputLen")
            }
          } catch (e: Exception) {
            ServerMetrics.onInferenceCompleted()
            logEvent("request_error id=$requestId endpoint=$endpoint error=stream_write_failed streaming=true")
            if (logId != null) {
              RequestLogStore.update(logId) { it.copy(partialText = null, isPending = false, latencyMs = SystemClock.elapsedRealtime() - streamStartMs) }
            }
            try { stream.finish() } catch (_: Exception) {}
          }
        },
        onError = { error ->
          ServerMetrics.onInferenceCompleted()
          ServerMetrics.incrementErrorCount()
          logEvent("request_error id=$requestId endpoint=$endpoint error=$error streaming=true")
          if (logId != null) {
            RequestLogStore.update(logId) {
              it.copy(partialText = null, isPending = false, latencyMs = SystemClock.elapsedRealtime() - streamStartMs, level = LogLevel.ERROR)
            }
          }
          try {
            stream.enqueue("data: {\"error\":\"$error\"}\n\n")
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

    private fun sseResponseToolCall(modelId: String, toolCall: ToolCall): Response =
      chunkedSseResponse(LlmHttpResponseRenderer.buildToolCallSsePayload(modelId, toolCall))

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

    private fun addCorsHeaders(response: Response): Response {
      response.addHeader("Access-Control-Allow-Origin", "*")
      response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
      response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
      response.addHeader("Access-Control-Max-Age", "86400")
      return response
    }

    private fun corsOk(): Response {
      val resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
      return addCorsHeaders(resp)
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
        JsonPrimitive("/v1/chat/completions"),
        JsonPrimitive("/v1/responses"),
      )))
    }
    return JsonObject(info).toString()
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

  private fun emptyChatResponse(modelName: String) = ChatResponse(
    id = "chatcmpl-${java.util.UUID.randomUUID()}", created = System.currentTimeMillis() / 1000, model = modelName,
    choices = listOf(ChatChoice(0, ChatMessage("assistant", ChatContent("")), "stop")),
    usage = Usage(0, 0),
  )

  private fun chatResponseWithText(modelName: String, text: String, promptLen: Int = 0, finishReason: String = "stop") = ChatResponse(
    id = "chatcmpl-${java.util.UUID.randomUUID()}", created = System.currentTimeMillis() / 1000, model = modelName,
    choices = listOf(ChatChoice(0, ChatMessage("assistant", ChatContent(text)), finishReason)),
    usage = Usage(
      prompt_tokens = (promptLen / 4).coerceAtLeast(if (promptLen > 0) 1 else 0),
      completion_tokens = (text.length / 4).coerceAtLeast(if (text.isNotEmpty()) 1 else 0),
    ),
  )

  private fun chatResponseWithToolCall(modelName: String, toolCall: ToolCall, promptLen: Int = 0) = ChatResponse(
    id = "chatcmpl-${java.util.UUID.randomUUID()}", created = System.currentTimeMillis() / 1000, model = modelName,
    choices = listOf(ChatChoice(0, ChatMessage("assistant", ChatContent(""), tool_calls = listOf(toolCall)), "tool_calls")),
    usage = Usage(
      prompt_tokens = (promptLen / 4).coerceAtLeast(if (promptLen > 0) 1 else 0),
      completion_tokens = (toolCall.function.arguments.length / 4).coerceAtLeast(1),
    ),
  )

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
      }
    }
  }
}
