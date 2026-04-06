package com.ollite.llm.server.service

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
import com.ollite.llm.server.MainActivity
import com.ollite.llm.server.R
import com.ollite.llm.server.common.getWifiIpAddress
import com.ollite.llm.server.data.LlmHttpPrefs
import com.ollite.llm.server.data.AllowedModel
import com.ollite.llm.server.data.Model
import com.ollite.llm.server.runtime.ServerLlmModelHelper
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
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

  private lateinit var logger: LlmHttpLogger
  private lateinit var allowlistLoader: LlmHttpAllowlistLoader

  override fun onCreate() {
    super.onCreate()
    logger = LlmHttpLogger(
      logDir = { getExternalFilesDir(null)?.let { File(it, "edgegallery") } },
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

    val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: LlmHttpPrefs.getPort(this)
    currentPort = port

    val model = pickDefaultLlmModel()
    if (model == null) {
      ServerMetrics.onServerError()
      stopSelf()
      return START_NOT_STICKY
    }
    defaultModel = model
    modelCache[model.name] = model

    ServerMetrics.onServerStarting(port, model.name)

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

    val notification: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Ollite Server")
        .setContentText("Serving ${model.name} on $displayAddress:$port")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentIntent(contentIntent)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopIntent)
        .setOngoing(true)
        .build()
    startForeground(NOTIFICATION_ID, notification)

    server?.stop()
    server = NanoServer(port)
    server?.start()

    ServerMetrics.onServerRunning(wifiIp)

    Thread { server?.warmUpModel(model) }.start()

    return START_STICKY
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    // TRIM_MEMORY_RUNNING_CRITICAL = 15
    if (level >= 15) System.gc()
  }

  override fun onDestroy() {
    server?.stop()
    ServerMetrics.onServerStopped()
    logger.shutdown()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun pickDefaultLlmModel(): Model? =
    LlmHttpModelFactory.buildDefaultModel(
      allowlist = allowlistLoader.load(),
      importsDir = File(getExternalFilesDir(null), "__imports"),
    )

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
    return "r${requestCounter.incrementAndGet()}"
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
      // Read body for logging (only for POST)
      var requestBodySnapshot: String? = null
      val response = try {
        if (!LlmHttpRouteResolver.isSupportedMethod(session.method)) return methodNotAllowed()
        val route = LlmHttpRouteResolver.resolve(session.method, session.uri) ?: return notFound()
        if (route.requiresAuth) requireAuth(session)?.let { return it }
        when (route.handler) {
          LlmHttpRouteHandler.PING -> okJsonText("{\"status\":\"ok\"}")
          LlmHttpRouteHandler.MODELS -> okJsonText(modelsPayload())
          LlmHttpRouteHandler.GENERATE -> handleGenerate(session) { requestBodySnapshot = it }
          LlmHttpRouteHandler.CHAT_COMPLETIONS -> handleChatCompletion(session) { requestBodySnapshot = it }
          LlmHttpRouteHandler.RESPONSES -> handleResponses(session) { requestBodySnapshot = it }
        }
      } catch (t: Throwable) {
        jsonError(Response.Status.INTERNAL_ERROR, t.message ?: "internal_error")
      }
      val elapsedMs = SystemClock.elapsedRealtime() - startMs
      val statusCode = response.status?.requestStatus ?: 200
      val isStreaming = response.mimeType == "text/event-stream"
      RequestLogStore.add(
        RequestLogEntry(
          id = "log-${System.currentTimeMillis()}",
          method = method,
          path = path,
          requestBody = requestBodySnapshot?.take(2000),
          responseBody = null, // response body not easily extractable from NanoHTTPD Response
          statusCode = statusCode,
          latencyMs = elapsedMs,
          isStreaming = isStreaming,
          modelName = defaultModel?.name,
        )
      )
      return response
    }

    private fun handleGenerate(session: IHTTPSession, captureBody: (String) -> Unit = {}): Response {
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
      val text = runLlm(model, req.prompt, requestId, "/generate") ?: return badRequest("llm error")
      return okJsonText(json.encodeToString(GenRes(text = text, usage = Usage(0, 0))))
    }

    private fun handleChatCompletion(session: IHTTPSession, captureBody: (String) -> Unit = {}): Response {
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
      val prompt = LlmHttpRequestAdapter.buildChatPrompt(req.messages)
      logPayload("POST /v1/chat/completions prompt", prompt, requestId)
      val requestedId = LlmHttpBridgeUtils.resolveRequestedModelId(req.model)
      val model = selectModel(req.model) ?: return notFound("model_not_found")
      logEvent("request_start id=$requestId endpoint=/v1/chat/completions bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} model=$requestedId resolved=${model.name}")

      if (prompt.isBlank()) {
        logEvent("request_empty id=$requestId endpoint=/v1/chat/completions")
        return okJsonText(json.encodeToString(emptyChatResponse(model.name)))
      }
      if (!req.tools.isNullOrEmpty() && req.tool_choice != "none") {
        val toolCall = LlmHttpRequestAdapter.synthesizeToolCall(req.tools!!.first(), prompt, "call_${System.currentTimeMillis()}")
        logEvent("request_tool_call id=$requestId endpoint=/v1/chat/completions tool=${toolCall.function.name}")
        return okJsonText(json.encodeToString(chatResponseWithToolCall(model.name, toolCall)))
      }
      val text = runLlm(model, prompt, requestId, "/v1/chat/completions", timeoutSeconds = 30)
        ?: return badRequest("llm error")
      return okJsonText(json.encodeToString(chatResponseWithText(model.name, text)))
    }

    private fun handleResponses(session: IHTTPSession, captureBody: (String) -> Unit = {}): Response {
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
      val prompt = LlmHttpRequestAdapter.buildConversationPrompt(req.messages ?: req.input)
      logPayload("POST /v1/responses prompt", prompt, requestId)
      logEvent("request_start id=$requestId endpoint=/v1/responses bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} model=$requestedId resolved=${model.name}")

      if (prompt.isBlank()) {
        logEvent("request_empty id=$requestId endpoint=/v1/responses")
        return emptyResponse(model.name, stream = req.stream == true)
      }
      if (!req.tools.isNullOrEmpty() && req.tool_choice != "none") {
        val toolCall = LlmHttpRequestAdapter.synthesizeToolCall(req.tools!!.first(), prompt, "call_${System.currentTimeMillis()}")
        return if (req.stream == true) sseResponseToolCall(model.name, toolCall)
        else okJsonText(json.encodeToString(responsesResponseWithToolCall(model.name, toolCall)))
      }
      return if (req.stream == true) {
        streamLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = 90)
      } else {
        val text = runLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = 90)
          ?: return badRequest("llm error")
        okJsonText(json.encodeToString(responsesResponseWithText(model.name, text)))
      }
    }

    fun warmUpModel(model: Model) {
      runLlm(model, "Hola", "warmup", "warmup", timeoutSeconds = 10)
    }

    private fun runLlm(
      model: Model,
      prompt: String,
      requestId: String,
      endpoint: String,
      timeoutSeconds: Long = 30,
    ): String? {
      synchronized(this) {
        if (model.instance == null) {
          var err = ""
          ServerLlmModelHelper.initialize(
            context = this@LlmHttpService,
            model = model,
            supportImage = false,
            supportAudio = false,
            onDone = { err = it },
            systemInstruction = null,
          )
          if (err.isNotEmpty()) return null
        }
      }
      val result = LlmHttpInferenceGateway.execute(
        prompt = prompt,
        timeoutSeconds = timeoutSeconds,
        executor = executor,
        inferenceLock = inferenceLock,
        resetConversation = {
          ServerLlmModelHelper.resetConversation(model, supportImage = false, supportAudio = false, systemInstruction = null)
        },
        runInference = { input, onPartial, onError ->
          ServerLlmModelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partial, done, _ -> onPartial(partial, done) },
            cleanUpListener = {},
            onError = onError,
          )
        },
        cancelInference = { ServerLlmModelHelper.stopResponse(model) },
        elapsedMs = { SystemClock.elapsedRealtime() },
      )
      return if (result.error != null) {
        logEvent("request_error id=$requestId endpoint=$endpoint error=${result.error} totalMs=${result.totalMs} ttfbMs=${result.ttfbMs} outputChars=${result.output?.length ?: 0}")
        null
      } else {
        val outputLen = result.output?.length ?: 0
        // Rough token estimate: ~4 chars per token
        ServerMetrics.addTokens((outputLen / 4).toLong().coerceAtLeast(1))
        ServerMetrics.recordLatency(result.totalMs)
        logEvent("request_done id=$requestId endpoint=$endpoint totalMs=${result.totalMs} ttfbMs=${result.ttfbMs} outputChars=$outputLen")
        result.output
      }
    }

    private fun streamLlm(
      model: Model,
      prompt: String,
      requestId: String,
      endpoint: String,
      timeoutSeconds: Long = 90,
    ): Response {
      synchronized(this) {
        if (model.instance == null) {
          var err = ""
          ServerLlmModelHelper.initialize(
            context = this@LlmHttpService,
            model = model,
            supportImage = false,
            supportAudio = false,
            onDone = { err = it },
            systemInstruction = null,
          )
          if (err.isNotEmpty()) return jsonError(Response.Status.INTERNAL_ERROR, "model_init_failed")
        }
      }

      val now = System.currentTimeMillis() / 1000
      val respId = "resp-$now"
      val msgId = "msg-$now"
      val fullText = StringBuilder()
      var headerWritten = false

      val pipedOut = PipedOutputStream()
      val pipedIn = PipedInputStream(pipedOut, 16 * 1024)
      val writer = pipedOut.writer(Charsets.UTF_8)

      LlmHttpInferenceGateway.executeStreaming(
        prompt = prompt,
        timeoutSeconds = timeoutSeconds,
        executor = executor,
        inferenceLock = inferenceLock,
        resetConversation = {
          ServerLlmModelHelper.resetConversation(model, supportImage = false, supportAudio = false, systemInstruction = null)
        },
        runInference = { input, onPartial, onError ->
          ServerLlmModelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partial, done, _ -> onPartial(partial, done) },
            cleanUpListener = {},
            onError = onError,
          )
        },
        cancelInference = { ServerLlmModelHelper.stopResponse(model) },
        elapsedMs = { SystemClock.elapsedRealtime() },
        onToken = { partial, done ->
          try {
            if (!headerWritten) {
              headerWritten = true
              writer.write(LlmHttpResponseRenderer.buildStreamingHeader(model.name, respId, msgId, now))
              writer.flush()
            }
            if (partial.isNotEmpty()) {
              fullText.append(partial)
              val esc = LlmHttpBridgeUtils.escapeSseText(partial)
              writer.write(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
              writer.flush()
            }
            if (done) {
              val esc = LlmHttpBridgeUtils.escapeSseText(fullText.toString())
              writer.write(LlmHttpResponseRenderer.buildStreamingFooter(model.name, respId, msgId, now, esc))
              writer.flush()
              writer.close()
              logEvent("request_done id=$requestId endpoint=$endpoint streaming=true outputChars=${fullText.length}")
            }
          } catch (e: Exception) {
            logEvent("request_error id=$requestId endpoint=$endpoint error=pipe_write_failed streaming=true")
            try { writer.close() } catch (_: Exception) {}
          }
        },
        onError = { error ->
          logEvent("request_error id=$requestId endpoint=$endpoint error=$error streaming=true")
          try {
            writer.write("data: {\"error\":\"$error\"}\n\n")
            writer.flush()
            writer.close()
          } catch (_: Exception) {}
        },
      )

      return chunkedSseResponse(pipedIn)
    }

    // ── Response helpers ──────────────────────────────────────────────────────

    private fun sseResponse(modelId: String, text: String): Response =
      chunkedSseResponse(LlmHttpResponseRenderer.buildTextSsePayload(modelId, text))

    private fun sseResponseToolCall(modelId: String, toolCall: ToolCall): Response =
      chunkedSseResponse(LlmHttpResponseRenderer.buildToolCallSsePayload(modelId, toolCall))

    private fun emptyResponse(modelId: String, stream: Boolean): Response {
      val body = responsesResponseWithText(modelId, "")
      return if (stream) sseResponse(modelId, "") else okJsonText(json.encodeToString(body))
    }

    private fun chunkedSseResponse(stream: InputStream): Response {
      val resp = newChunkedResponse(Response.Status.OK, "text/event-stream", stream)
      resp.addHeader("Cache-Control", "no-cache")
      resp.addHeader("Connection", "keep-alive")
      return resp
    }

    private fun chunkedSseResponse(payload: String): Response =
      chunkedSseResponse(ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)))

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
  }

  // ── Payload builders ─────────────────────────────────────────────────────────

  private fun modelsPayload(): String {
    val allowed = allowlistLoader.load()
    val fallbackId = defaultModel?.name ?: "local"
    Log.i(logTag, "Models list size=${allowed.size} source=${allowlistLoader.lastSource} fallback=$fallbackId")
    return LlmHttpResponseRenderer.renderModelListPayload(json, allowed.map { it.name }, fallbackId)
  }

  private fun emptyChatResponse(modelName: String) = ChatResponse(
    id = "chatcmpl-local", created = System.currentTimeMillis() / 1000, model = modelName,
    choices = listOf(ChatChoice(0, ChatMessage("assistant", "Hola desde Edge (fallback)"), "stop")),
    usage = Usage(0, 0),
  )

  private fun chatResponseWithText(modelName: String, text: String) = ChatResponse(
    id = "chatcmpl-local", created = System.currentTimeMillis() / 1000, model = modelName,
    choices = listOf(ChatChoice(0, ChatMessage("assistant", text), "stop")),
    usage = Usage(0, 0),
  )

  private fun chatResponseWithToolCall(modelName: String, toolCall: ToolCall) = ChatResponse(
    id = "chatcmpl-local", created = System.currentTimeMillis() / 1000, model = modelName,
    choices = listOf(ChatChoice(0, ChatMessage("assistant", "", tool_calls = listOf(toolCall)), "tool_calls")),
    usage = Usage(0, 0),
  )

  private fun responsesResponseWithText(modelName: String, text: String) = ResponsesResponse(
    id = "resp-local", created = System.currentTimeMillis() / 1000, model = modelName,
    output = listOf(RespMessage(content = listOf(RespContent(text = text)))),
    usage = Usage(0, 0),
  )

  private fun responsesResponseWithToolCall(modelName: String, toolCall: ToolCall) = ResponsesResponse(
    id = "resp-local", created = System.currentTimeMillis() / 1000, model = modelName,
    output = listOf(RespMessage(content = listOf(RespContent(type = "output_tool_call", text = json.encodeToString(toolCall))), finish_reason = "tool_calls")),
    usage = Usage(0, 0),
  )

  companion object {
    const val EXTRA_PORT = "extra_port"
    const val DEFAULT_PORT = 11434
    const val ACTION_STOP = "com.ollite.llm.server.STOP_SERVER"
    private const val CHANNEL_ID = "ollite-server"
    private const val NOTIFICATION_ID = 42

    fun start(context: Context, port: Int = DEFAULT_PORT) {
      val intent = Intent(context, LlmHttpService::class.java).apply {
        putExtra(EXTRA_PORT, port)
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
  }
}
