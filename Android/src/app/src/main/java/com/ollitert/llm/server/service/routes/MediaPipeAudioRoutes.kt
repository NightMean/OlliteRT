/*
 * Copyright 2026 @NightMean (https://github.com/NightMean)
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

import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.ServerPrefs
import io.ktor.server.application.call
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "OlliteRT.AudioASRRoute"

/**
 * MediaPipe ASR route definitions (/v1/audio/asr).
 *
 * This endpoint uses the on-device MediaPipe AudioTranscriber engine — a separate, self-contained
 * engine that does NOT require a loaded LLM model. The LLM-backed endpoint lives at
 * /v1/audio/transcriptions; this one is for the gallery-parity MediaPipe path.
 */
internal fun Routing.mediaPipeAudioAsrRoutes(server: KtorServer) {
  with(server) {
    route("/v1/audio/asr") {
      installAudioBodyLimit()
      post {
        if (!requireInferenceRequest(call, json = false)) return@post
        val admission = requestBodyAdmission.tryAcquire() ?: run {
          withGetLogging(call) { httpServiceUnavailable("Too many in-flight requests. Retry later.") }
          return@post
        }
        try {
          val prefs = ServerPrefs.captureRequestSnapshot(serviceContext)
          val startMs = SystemClock.elapsedRealtime()
          val logId = nextLogId()
          RequestLogStore.add(
            RequestLogEntry(
              id = logId,
              method = "POST",
              path = "/v1/audio/asr",
              modelName = "mediapipe-asr",
              clientIp = call.clientIp(prefs.resolveClientHostnames),
              isPending = true,
            ),
          )

          val contentLengthHeader = call.request.headers["Content-Length"]?.toLongOrNull()

          if (contentLengthHeader != null && contentLengthHeader > MAX_FILE_SIZE_BYTES) {
            val response = httpPayloadTooLarge("File too large (${contentLengthHeader / 1_000_000}MB). Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
            finalizeLogEntry(logId, startMs, response, null, responseBody(response))
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          }

          val upload = try {
            val received = withTimeoutOrNull(REQUEST_BODY_READ_TIMEOUT_MS) { readAudioMultipart(call) }
            if (received == null) {
              val response = httpJsonError(408, "Request body took too long to receive")
              finalizeLogEntry(logId, startMs, response, "[Multipart body read timed out]", responseBody(response))
              call.response.headers.append("x-request-id", logId)
              call.respondHttpResponse(response)
              return@post
            }
            received
          } catch (e: PayloadTooLargeException) {
            val response = httpPayloadTooLarge("Multipart request too large. Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
            finalizeLogEntry(logId, startMs, response, "[multipart audio — rejected: too large]", responseBody(response))
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          } catch (e: AudioMultipartLimitException) {
            Log.w(TAG, "Audio multipart request exceeded a limit", e)
            val response = httpPayloadTooLarge("Multipart request exceeds a size or part limit, or has multiple files.")
            finalizeLogEntry(logId, startMs, response, "[multipart audio — rejected: too large]", responseBody(response))
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          } catch (e: CancellationException) {
            RequestLogStore.update(logId) {
              it.copy(isPending = false, isCancelled = true, statusCode = 499, latencyMs = SystemClock.elapsedRealtime() - startMs)
            }
            throw e
          } catch (e: Exception) {
            Log.w(TAG, "Failed to read audio multipart request", e)
            val response = httpBadRequest("Invalid or interrupted multipart audio request")
            finalizeLogEntry(logId, startMs, response, "[multipart audio — invalid]", responseBody(response))
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          }

          val actualSize = upload.fileBytes?.size?.toLong() ?: contentLengthHeader ?: 0L

          val response = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            audioHandler.handle(
              fileBytes = upload.fileBytes,
              fields = upload.fields,
              contentLength = actualSize,
              logId = logId,
              prefs = prefs,
            )
          }
          finalizeLogEntry(logId, startMs, response, "[multipart audio $actualSize bytes]", responseBody(response))
          call.response.headers.append("x-request-id", logId)
          call.respondHttpResponse(response)
        } finally {
          admission.close()
        }
      }
    }
  }
}

private fun responseBody(response: HttpResponse): String? = when (response) {
  is HttpResponse.Json -> response.body
  is HttpResponse.PlainText -> response.body
  else -> null
}

/**
 * MediaPipe TTS route definitions (/v1/audio/speech, /v1/audio/voices).
 *
 * Gallery-parity on-device speech synthesis: a separate MediaPipe TextToSpeech engine that does
 * NOT require a loaded LLM model (independent of the chat model's state). Request logging is
 * hand-rolled like the ASR route so TTS is not gated on LLM busy/recovery state.
 */
internal fun Routing.mediaPipeAudioTtsRoutes(server: KtorServer) {
  with(server) {
    route("/v1/audio/speech") {
      installAudioBodyLimit()
      post {
        if (!requireInferenceRequest(call, json = true)) return@post
        val admission = requestBodyAdmission.tryAcquire() ?: run {
          withGetLogging(call) { httpServiceUnavailable("Too many in-flight requests. Retry later.") }
          return@post
        }
        try {
          val prefs = ServerPrefs.captureRequestSnapshot(serviceContext)
          val startMs = SystemClock.elapsedRealtime()
          val logId = nextLogId()
          RequestLogStore.add(
            RequestLogEntry(
              id = logId,
              method = "POST",
              path = "/v1/audio/speech",
              modelName = "mediapipe-tts",
              clientIp = call.clientIp(prefs.resolveClientHostnames),
              isPending = true,
            ),
          )

          val contentLengthHeader = call.request.headers["Content-Length"]?.toLongOrNull()
          if (contentLengthHeader == null) {
            val response = httpPayloadTooLarge("Request must declare Content-Length.")
            finalizeLogEntry(logId, startMs, response, "[No Content-Length declared]", responseBody(response))
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          }
          if (contentLengthHeader > MAX_FILE_SIZE_BYTES) {
            val response = httpPayloadTooLarge("Request body too large. Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
            finalizeLogEntry(logId, startMs, response, "[Content-Length exceeded: $contentLengthHeader]", responseBody(response))
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          }

          val body = try {
            val received = withTimeoutOrNull(REQUEST_BODY_READ_TIMEOUT_MS) {
              withContext(kotlinx.coroutines.Dispatchers.IO) { call.receiveText() }
            }
            if (received == null) {
              val response = httpJsonError(408, "Request body took too long to receive")
              finalizeLogEntry(logId, startMs, response, "[body read timed out]", responseBody(response))
              call.response.headers.append("x-request-id", logId)
              call.respondHttpResponse(response)
              return@post
            }
            received
          } catch (e: CancellationException) {
            RequestLogStore.update(logId) {
              it.copy(isPending = false, isCancelled = true, statusCode = 499, latencyMs = SystemClock.elapsedRealtime() - startMs)
            }
            throw e
          } catch (e: Exception) {
            Log.w(TAG, "Failed to read speech request body", e)
            val response = httpBadRequest("Invalid or interrupted request body")
            finalizeLogEntry(logId, startMs, response, "[body read failed]", responseBody(response))
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          }

          val response = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            audioHandler.handleSpeech(
              body = body,
              captureBody = { captured ->
                RequestLogStore.update(logId) { it.copy(requestBody = captured) }
              },
              logId = logId,
              prefs = prefs,
            )
          }
          val responseSnapshot = when (response) {
            is HttpResponse.Binary -> "[audio ${response.bytes.size} bytes]"
            else -> responseBody(response)
          }
          finalizeLogEntry(logId, startMs, response, null, responseSnapshot)
          call.response.headers.append("x-request-id", logId)
          call.respondHttpResponse(response)
        } finally {
          admission.close()
        }
      }
    }

    route("/v1/audio/voices") {
      get {
        if (!requireInferenceRequest(call, json = false)) return@get
        withGetLogging(call) {
          audioHandler.handleVoices(call.request.queryParameters["model"])
        }
      }
    }
  }
}
