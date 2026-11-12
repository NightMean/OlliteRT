/*
 * Copyright 2026 @NightMean (https://github.com/NightMean) — model-slot routes added by
 * the OlliteRT fork (Vasyl): stable per-model URL paths so generic clients can route
 * by slot (model 1 / model 2) without knowing model names.
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

package com.ollitert.llm.server.service.routes

import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Model-slot routes: stable URL paths bound to a resident model slot, in the
 * spirit of running one server instance per model (llama.cpp style) — but on a
 * single port so one process, one admission gate, one request log:
 *
 *   /v1/model/1/{endpoint}  → primary (active) model
 *   /v1/model/2/{endpoint}  → secondary (pinned second) model
 *
 * The request's `model` field is rewritten to the slot's model name before the
 * canonical handler runs, so any generic client (default model name, a stale
 * name, anything) reaches the slot's model. Pointing an OpenAI client's base
 * URL at `http://<host>:<port>/v1/model/1` just works. Name-based routing on
 * the canonical paths keeps working unchanged.
 */
internal fun Routing.modelSlotRoutes(server: KtorServer) {
  for (slot in 1..2) {
    val base = "/v1/model/$slot"

    // Slot identity probe: which model currently occupies the slot.
    get(base) {
      server.withGetLogging(call) {
        val name = server.slotModelName(slot)
        if (name == null) httpNotFound()
        else httpOkJson(
          buildJsonObject {
            put("slot", slot)
            put("model", name)
          }.toString()
        )
      }
    }

    post("$base/chat/completions") {
      if (!server.requireInferenceRequest(call)) return@post
      server.withSlotBody(call, slot) { body, captureBody, captureResponse, logId, _, prefs ->
        server.endpointHandlers.handleChatCompletion(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("$base/completions") {
      if (!server.requireInferenceRequest(call)) return@post
      server.withSlotBody(call, slot) { body, captureBody, captureResponse, logId, _, prefs ->
        server.endpointHandlers.handleCompletions(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("$base/responses") {
      if (!server.requireInferenceRequest(call)) return@post
      server.withSlotBody(call, slot) { body, captureBody, captureResponse, logId, _, prefs ->
        server.endpointHandlers.handleResponses(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("$base/messages") {
      if (!server.requireInferenceRequest(call, anthropic = true)) return@post
      server.withSlotBody(call, slot, anthropicRequest = true) { body, captureBody, captureResponse, logId, _, prefs ->
        server.anthropicEndpointHandlers.handleMessages(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("$base/embeddings") {
      if (!server.requireInferenceRequest(call)) return@post
      server.withSlotBody(call, slot) { body, captureBody, captureResponse, logId, _, _ ->
        server.handleEmbeddingsRequest(body, logId).also { response ->
          if (response is HttpResponse.Json) captureResponse(response.body)
        }
      }
    }
  }
}

/**
 * Name of the model currently occupying [slot] (1 = primary/active,
 * 2 = secondary). Slot 1 falls back to the keep-alive idle-unloaded name so
 * an idle-unloaded primary still resolves (it auto-reloads on first use).
 * Null when the slot is empty.
 */
internal fun KtorServer.slotModelName(slot: Int): String? = when (slot) {
  1 -> modelLifecycle.defaultModel?.name ?: modelLifecycle.keepAliveUnloadedModelName
  2 -> modelLifecycle.secondaryModel?.name
  else -> null
}

/**
 * Runs a slot inference request: resolves the slot's model, pins the request's
 * `model` field to it via bodyTransform, and delegates to the canonical
 * handler pipeline (same admission, logging, SSE, and busy handling as the
 * non-slot routes).
 */
private suspend fun KtorServer.withSlotBody(
  call: ApplicationCall,
  slot: Int,
  anthropicRequest: Boolean = false,
  handler: suspend (
    body: String,
    captureBody: (String) -> Unit,
    captureResponse: (String) -> Unit,
    logId: String,
    sseExtraHeaders: Map<String, String>,
    prefs: com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot,
  ) -> HttpResponse,
) {
  val modelName = slotModelName(slot)
  if (modelName == null) {
    withGetLogging(call) {
      httpJsonError(
        400,
        when (slot) {
          1 -> "Model slot 1 is empty — no primary model is loaded. Load a model and start the server."
          else -> "Model slot 2 is empty — no second model is loaded. Tap the model card and choose 'Load as Second Model'."
        },
      )
    }
    return
  }
  withRequestLogging(
    call = call,
    admitModelRequest = true,
    inferenceRequest = true,
    anthropicRequest = anthropicRequest,
    bodyTransform = { raw -> rewriteModelField(json, raw, modelName) },
    handler = handler,
  )
}

/**
 * Sets the top-level `model` field of a JSON request body to [modelName].
 * Non-object bodies are returned unchanged so the canonical handler produces
 * its normal 400 for malformed input.
 */
private fun rewriteModelField(json: kotlinx.serialization.json.Json, body: String, modelName: String): String {
  return try {
    val obj = json.parseToJsonElement(body).jsonObject
    obj.toMutableMap().apply { put("model", kotlinx.serialization.json.JsonPrimitive(modelName)) }
      .let { kotlinx.serialization.json.JsonObject(it) }.toString()
  } catch (_: Exception) {
    body
  }
}
