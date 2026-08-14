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

import com.ollitert.llm.server.data.DEFAULT_SSE_OUTER_TIMEOUT_MS
import com.ollitert.llm.server.data.ErrorKind

sealed class HttpResponse {
  abstract val statusCode: Int

  data class Json(
    override val statusCode: Int,
    val body: String,
    val extraHeaders: Map<String, String> = emptyMap(),
  ) : HttpResponse()

  data class Binary(
    override val statusCode: Int,
    val contentType: String,
    val bytes: ByteArray,
  ) : HttpResponse()

  data class PlainText(
    override val statusCode: Int,
    val contentType: String,
    val body: String,
  ) : HttpResponse()

  data class Sse(
    override val statusCode: Int = 200,
    // Outer safety-net timeout (ms) for the SSE writer coroutine. Must stay strictly
    // above the inner streaming timeout (InferenceRunner.streamInference) so it only
    // fires when the inner timeout + cleanup fails to unwind. Streaming handlers set
    // this from the user's configurable per-endpoint timeout; trivial empty-stream
    // responses keep the default.
    val outerTimeoutMs: Long = DEFAULT_SSE_OUTER_TIMEOUT_MS,
    val writer: suspend (SseWriter) -> Unit,
  ) : HttpResponse()
}

fun httpOkJson(body: String) = HttpResponse.Json(200, body)

fun httpJsonError(statusCode: Int, error: String, suggestion: String? = null, kind: ErrorKind? = null) =
  HttpResponse.Json(statusCode, ResponseRenderer.renderJsonError(error, suggestion, kind))

fun httpBadRequest(msg: String) = httpJsonError(400, msg)
fun httpNotFound(error: String = "not_found") = httpJsonError(404, error)
fun httpUnauthorized(error: String) = HttpResponse.Json(
  401, ResponseRenderer.renderJsonError(error),
  extraHeaders = mapOf("WWW-Authenticate" to "Bearer"),
)
fun httpMethodNotAllowed() = httpJsonError(405, "method_not_allowed")
fun httpPayloadTooLarge(error: String) = httpJsonError(413, error)
fun httpInternalError(error: String, suggestion: String? = null, kind: ErrorKind? = null) = httpJsonError(500, error, suggestion, kind)
fun httpServiceUnavailable(error: String) = HttpResponse.Json(
  503, ResponseRenderer.renderJsonError(error),
  extraHeaders = mapOf("Retry-After" to "5"),
)

fun ModelLifecycle.ModelSelection.Error.toHttpResponse() = HttpResponse.Json(
  statusCode = statusCode,
  body = ResponseRenderer.renderJsonError(message),
  extraHeaders = buildMap { retryAfterSeconds?.let { put("Retry-After", it.toString()) } },
)

/**
 * Build an Anthropic-shaped error response: `{type:"error", error:{type, message}}`.
 *
 * Distinct from [httpJsonError] which produces the OpenAI shape. Anthropic SDKs
 * expect this exact envelope and parse [errorType] strings like
 * `invalid_request_error`, `authentication_error`, `not_found_error`,
 * `request_too_large`, `api_error`, `overloaded_error`.
 */
fun httpAnthropicError(statusCode: Int, errorType: String, message: String): HttpResponse.Json =
  HttpResponse.Json(
    statusCode = statusCode,
    body = ResponseRenderer.renderAnthropicError(errorType, message),
    extraHeaders = if (statusCode == 401) mapOf("WWW-Authenticate" to "Bearer") else emptyMap(),
  )

/** Re-shape a [ModelLifecycle.ModelSelection.Error] into the Anthropic envelope. */
fun ModelLifecycle.ModelSelection.Error.toAnthropicHttpResponse(): HttpResponse.Json {
  val errorType = when (statusCode) {
    404 -> "not_found_error"
    503 -> "overloaded_error"
    else -> "api_error"
  }
  val base = httpAnthropicError(statusCode, errorType, message)
  return retryAfterSeconds?.let {
    base.copy(extraHeaders = base.extraHeaders + ("Retry-After" to it.toString()))
  } ?: base
}
