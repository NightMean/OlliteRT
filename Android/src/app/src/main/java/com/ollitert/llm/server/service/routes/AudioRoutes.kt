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

package com.ollitert.llm.server.service.routes

import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*

import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.ServerPrefs
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.routing.Routing
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

internal fun Route.installAudioBodyLimit(maxBytes: Long = MAX_FILE_SIZE_BYTES) {
  install(RequestBodyLimit) { bodyLimit { maxBytes } }
}

/**
 * Audio transcription route definitions (/v1/audio/transcriptions).
 */
internal fun Routing.audioRoutes(server: KtorServer) {
  with(server) {
    route("/v1/audio/transcriptions") {
      installAudioBodyLimit()
      post {
        if (!requireAuth(call)) return@post
        val bodyAdmission = requestBodyAdmission.tryAcquire() ?: run {
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
              path = "/v1/audio/transcriptions",
              modelName = defaultModel?.name ?: keepAliveUnloadedModelName,
              clientIp = call.clientIp(prefs.resolveClientHostnames),
              isPending = true,
            ),
          )

          val contentLengthHeader = call.request.headers["Content-Length"]?.toLongOrNull()

          if (contentLengthHeader != null && contentLengthHeader > MAX_FILE_SIZE_BYTES) {
            val response = httpPayloadTooLarge("File too large (${contentLengthHeader / 1_000_000}MB). Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
            finalizeLogEntry(logId, startMs, response, null, response.body)
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          }

          val upload = try {
            val received = withTimeoutOrNull(REQUEST_BODY_READ_TIMEOUT_MS) { readAudioMultipart(call) }
            if (received == null) {
              val response = httpJsonError(408, "Request body took too long to receive")
              finalizeLogEntry(logId, startMs, response, "[Multipart body read timed out]", response.body)
              call.response.headers.append("x-request-id", logId)
              call.respondHttpResponse(response)
              return@post
            }
            received
          } catch (e: PayloadTooLargeException) {
            val response = httpPayloadTooLarge("Multipart request too large. Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
            finalizeLogEntry(logId, startMs, response, "[multipart audio — rejected: too large]", response.body)
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          } catch (e: AudioMultipartLimitException) {
            Log.w("OlliteRT.Server", "Audio multipart request exceeded a limit", e)
            val response = httpPayloadTooLarge("Multipart request exceeds a size or part limit, or has multiple files.")
            finalizeLogEntry(logId, startMs, response, "[multipart audio — rejected: too large]", response.body)
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          } catch (e: CancellationException) {
            RequestLogStore.update(logId) {
              it.copy(isPending = false, isCancelled = true, statusCode = 499, latencyMs = SystemClock.elapsedRealtime() - startMs)
            }
            throw e
          } catch (e: Exception) {
            Log.w("OlliteRT.Server", "Failed to read audio multipart request", e)
            val response = httpBadRequest("Invalid or interrupted multipart audio request")
            finalizeLogEntry(logId, startMs, response, "[multipart audio — invalid]", response.body)
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          }

          val actualSize = upload.fileBytes?.size?.toLong() ?: contentLengthHeader ?: 0L

          val admission = modelLifecycle.tryAcquireRequestAdmission()
          if (admission == null) {
            val response = httpServiceUnavailable(MODEL_RECOVERY_IN_PROGRESS_MESSAGE)
            finalizeLogEntry(logId, startMs, response, null, response.body)
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
            return@post
          }
          try {
            val model = when (val sel = modelLifecycle.selectModel(null)) {
              is ModelLifecycle.ModelSelection.Ok -> sel.model
              is ModelLifecycle.ModelSelection.Error -> {
                val response = sel.toHttpResponse()
                finalizeLogEntry(logId, startMs, response, null, response.body)
                call.response.headers.append("x-request-id", logId)
                call.respondHttpResponse(response)
                return@post
              }
            }

            if (prefs.rejectWhenBusy && ServerMetrics.isInferring.value) {
              val busyResponse = httpServiceUnavailable(
                "Server is busy processing another request. Disable \"Reject Requests When Busy\" in settings to queue instead.",
              )
              finalizeLogEntry(logId, startMs, busyResponse, "[multipart audio — rejected: busy]", busyResponse.body)
              call.response.headers.append("x-request-id", logId)
              call.respondHttpResponse(busyResponse)
              return@post
            }

            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
              audioTranscriptionHandler.handle(upload.fileBytes, upload.fields, actualSize, model, logId = logId, prefs = prefs)
            }
            val responseBody = when (response) {
              is HttpResponse.Json -> response.body
              is HttpResponse.PlainText -> response.body
              else -> null
            }
            finalizeLogEntry(logId, startMs, response, "[multipart audio $actualSize bytes]", responseBody)
            call.response.headers.append("x-request-id", logId)
            call.respondHttpResponse(response)
          } finally {
            admission.close()
          }
        } finally {
          bodyAdmission.close()
        }
      }
    }
  }
}
