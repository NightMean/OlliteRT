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

import android.content.Context
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.ClientIpAccessPolicy
import com.ollitert.llm.server.service.http.AnthropicEndpointHandlers
import com.ollitert.llm.server.service.http.EndpointHandlers
import com.ollitert.llm.server.service.http.KtorServer
import com.ollitert.llm.server.service.inference.AudioTranscriptionHandler
import com.ollitert.llm.server.service.inference.InferenceRunner
import com.ollitert.llm.server.service.inference.ModelLifecycle
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class ServerPipeline(
  val executor: ExecutorService,
  val runner: InferenceRunner,
  val server: KtorServer,
)

/**
 * Factory creating and wiring the HTTP server, inference runner, and protocol endpoint handlers.
 */
object ServerServicePipelineFactory {

  fun createPipeline(
    context: Context,
    port: Int,
    bindHost: String,
    clientIpAccessPolicy: ClientIpAccessPolicy,
    modelLifecycle: ModelLifecycle,
    json: Json,
    inferenceLock: Any,
    nextRequestId: () -> String,
    logEvent: (String) -> Unit,
    emitDebugStackTrace: (Throwable, String, String?) -> Unit,
    buildSystemInstruction: (String) -> Contents?,
  ): ServerPipeline {
    val executor = Executors.newSingleThreadExecutor()
    val runner = InferenceRunner(
      context = context,
      executor = executor,
      inferenceLock = inferenceLock,
      logEvent = logEvent,
      emitDebugStackTrace = emitDebugStackTrace,
      buildSystemInstruction = buildSystemInstruction,
    )
    val handlers = EndpointHandlers(
      context = context,
      json = json,
      inferenceRunner = runner,
      modelLifecycle = modelLifecycle,
      logEvent = logEvent,
      nextRequestId = nextRequestId,
    )
    val audioTranscriptionHandler = AudioTranscriptionHandler(
      context = context,
      inferenceRunner = runner,
      modelLifecycle = modelLifecycle,
    )
    val anthropicEndpointHandlers = AnthropicEndpointHandlers(
      json = json,
      endpointHandlers = handlers,
      nextRequestId = nextRequestId,
    )
    val server = KtorServer(
      port = port,
      bindHost = bindHost,
      initialClientIpAccessPolicy = clientIpAccessPolicy,
      serviceContext = context,
      endpointHandlers = handlers,
      modelLifecycle = modelLifecycle,
      json = json,
      nextRequestId = nextRequestId,
      emitDebugStackTrace = emitDebugStackTrace,
      audioTranscriptionHandler = audioTranscriptionHandler,
      anthropicEndpointHandlers = anthropicEndpointHandlers,
      inferenceLock = inferenceLock,
    )
    return ServerPipeline(executor = executor, runner = runner, server = server)
  }
}
