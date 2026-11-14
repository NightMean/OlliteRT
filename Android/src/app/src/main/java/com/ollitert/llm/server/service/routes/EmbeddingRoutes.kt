/*
 * Copyright 2026 @NightMean (https://github.com/NightMean) — embedding routes added by the
 * OlliteRT-embed fork (Vasyl) for on-device OpenAI-compatible /v1/embeddings.
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

import android.util.Log
import com.google.ai.edge.litertlm.EmbeddingCallback
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.EmbeddingResponse
import com.google.ai.edge.litertlm.InputData
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.runtime.EmbeddingInstancePool
import com.ollitert.llm.server.service.http.HttpResponse
import com.ollitert.llm.server.service.http.KtorServer
import com.ollitert.llm.server.service.http.httpBadRequest
import com.ollitert.llm.server.service.http.httpInternalError
import com.ollitert.llm.server.service.http.httpJsonError
import com.ollitert.llm.server.service.http.httpOkJson
import com.ollitert.llm.server.service.http.httpServiceUnavailable
import io.ktor.server.application.call
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.ceil

private const val TAG = "OlliteRT.Embeddings"

private const val EMBEDDING_COMPUTE_TIMEOUT_MS = 120_000L
private val EMBEDDING_DIMENSION_CHOICES = setOf(128, 256, 512, 768)

/**
 * OpenAI-compatible text-embedding route. Bridges to the on-device LiteRT-LM
 * [EmbeddingEngine] created by LiteRtEngineFactory for embedding models
 * (e.g. EmbeddingGemma 2). Request/response shapes follow the OpenAI
 * /v1/embeddings contract so any OpenAI client can point at this server.
 */
internal fun Routing.embeddingRoutes(server: KtorServer) {
  with(server) {
    post("/v1/embeddings") {
      if (!requireInferenceRequest(call)) return@post
      withRequestLogging(call, admitModelRequest = true, inferenceRequest = true) { body, captureBody, captureResponse, logId, _, _ ->
        captureBody(body)
        val response = handleEmbeddingsRequest(body, logId)
        if (response is HttpResponse.Json) captureResponse(response.body)
        response
      }
    }
  }
}

internal suspend fun KtorServer.handleEmbeddingsRequest(body: String, logId: String): HttpResponse {
  val parsed = try {
    json.parseToJsonElement(body) as? JsonObject
  } catch (e: Exception) {
    return httpBadRequest("Invalid JSON body: ${e.message}")
  }
  if (parsed == null) {
    return httpBadRequest("Request body must be a JSON object")
  }

  val inputElement = parsed["input"]
  val texts: List<String> = when (inputElement) {
    null -> return httpBadRequest("Missing required field: input")
    is kotlinx.serialization.json.JsonPrimitive ->
      listOf(inputElement.jsonPrimitive.content)
    is JsonArray -> inputElement.jsonArray
      .map { it.jsonPrimitive.content }
    else -> return httpBadRequest("input must be a string or an array of strings")
  }
  if (texts.isEmpty()) {
    return httpBadRequest("input must not be empty")
  }

  // Matryoshka truncation: EmbeddingGemma 2 supports full 768 plus 512/256/128.
  val dimensions = parsed["dimensions"]?.jsonPrimitive?.content?.toIntOrNull()
  if (dimensions != null && dimensions !in EMBEDDING_DIMENSION_CHOICES) {
    return httpBadRequest(
      "Unsupported dimensions: $dimensions. Supported values: ${EMBEDDING_DIMENSION_CHOICES.sorted()}"
    )
  }
  val normalize = parsed["normalize"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true

  val model = when (val sel = modelLifecycle.selectEmbeddingModel(
    parsed["model"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null }
  )) {
    is com.ollitert.llm.server.service.inference.ModelLifecycle.ModelSelection.Ok -> sel.model
    is com.ollitert.llm.server.service.inference.ModelLifecycle.ModelSelection.Error ->
      return httpJsonError(sel.statusCode, sel.message)
    else -> return httpInternalError("Model selection failed")
  }
  // Name the model that actually serves this request in the request log
  // (the second/embedding model, not the default chat model).
  RequestLogStore.update(logId) { it.copy(modelName = model.name) }
  // Multi-instance pool: round-robin across the loaded engines so concurrent
  // requests overlap on independent native handles. The per-engine gate
  // serializes requests that land on the SAME engine (the native SDK is not
  // safe for two concurrent computes on one engine).
  val slot = EmbeddingInstancePool.nextFor(model.name)
  val engine = slot?.engine ?: (model.instance as? EmbeddingEngine)
  if (engine == null) {
    return httpJsonError(
      400,
      "The resolved model ('${model.name}') does not support embeddings. " +
        "Load an embedding model (e.g. EmbeddingGemma 2) first."
    )
  }

  slot?.gate?.acquire()
  try {
    val options = EmbeddingOptions(normalize = normalize, outputSize = dimensions)
    val dataEntries = buildJsonArray {
      texts.forEachIndexed { index, text ->
        val embedding = try {
          computeSingleEmbedding(engine, text, options)
        } catch (e: Exception) {
          Log.e(TAG, "computeEmbedding failed for index $index: ${e.message}", e)
          return httpInternalError("Embedding computation failed: ${e.message}")
        }
        add(
          buildJsonObject {
            put("object", "embedding")
            putJsonArray("embedding") {
              embedding.forEach { value -> add(value.toDouble()) }
            }
            put("index", index)
          }
        )
      }
    }

    val promptTokens = texts.sumOf { estimateTokens(it) }
    return httpOkJson(
      buildJsonObject {
        put("object", "list")
        put("model", model.name)
        putJsonArray("data") { dataEntries.forEach { add(it) } }
        put(
          "usage",
          buildJsonObject {
            put("prompt_tokens", promptTokens)
            put("total_tokens", promptTokens)
          }
        )
      }.toString()
    )
  } finally {
    slot?.gate?.release()
  }
}

/**
 * Runs one embedding via the async callback API so the CIO event loop is never
 * blocked; fails fast on engine errors with a hard timeout.
 */
private suspend fun computeSingleEmbedding(
  engine: EmbeddingEngine,
  text: String,
  options: EmbeddingOptions,
): FloatArray {
  val deferred = CompletableDeferred<EmbeddingResponse>()
  engine.computeEmbeddingAsync(
    listOf(InputData.Text(text)),
    object : EmbeddingCallback<EmbeddingResponse> {
      override fun onSuccess(result: EmbeddingResponse) {
        deferred.complete(result)
      }

      override fun onError(t: Throwable) {
        deferred.completeExceptionally(t)
      }
    },
    options,
  )
  return withTimeout(EMBEDDING_COMPUTE_TIMEOUT_MS) {
    deferred.await().embedding
  }
}

/** Rough token estimate for usage accounting (LiteRT does not expose embedding token counts). */
private fun estimateTokens(text: String): Int = ceil(text.length / 4.0).toInt().coerceAtLeast(1)
