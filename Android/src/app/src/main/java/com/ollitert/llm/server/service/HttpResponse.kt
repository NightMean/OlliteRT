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

sealed class HttpResponse {
  data class Json(
    val statusCode: Int,
    val body: String,
    val extraHeaders: Map<String, String> = emptyMap(),
  ) : HttpResponse()

  data class Binary(
    val statusCode: Int,
    val contentType: String,
    val bytes: ByteArray,
  ) : HttpResponse()

  data class PlainText(
    val statusCode: Int,
    val contentType: String,
    val body: String,
  ) : HttpResponse()

  data class Sse(
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

fun ModelLifecycle.ModelSelection.Error.toHttpResponse() = HttpResponse.Json(
  statusCode = statusCode,
  body = ResponseRenderer.renderJsonError(message),
  extraHeaders = buildMap { retryAfterSeconds?.let { put("Retry-After", it.toString()) } },
)
