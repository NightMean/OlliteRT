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
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining

/**
 * Audio transcription route definitions (/v1/audio/transcriptions).
 */
internal fun Routing.audioRoutes(server: KtorServer) {
  with(server) {
    post("/v1/audio/transcriptions") {
      if (!requireAuth(call)) return@post
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

      val multipart = call.receiveMultipart(formFieldLimit = MAX_FILE_SIZE_BYTES)
      var fileBytes: ByteArray? = null
      val fields = mutableMapOf<String, String>()
      try {
        multipart.forEachPart { part ->
          when (part) {
            is PartData.FileItem -> fileBytes = readBytesWithLimit(part.provider().readRemaining(), MAX_FILE_SIZE_BYTES)
            is PartData.FormItem -> fields[part.name ?: ""] = part.value
            else -> {}
          }
          part.dispose()
        }
      } catch (e: java.io.IOException) {
        Log.w("OlliteRT.Server", "Audio upload exceeded ${MAX_FILE_SIZE_BYTES / 1_000_000}MB limit: ${e.message}")
        val response = httpPayloadTooLarge("File too large. Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
        finalizeLogEntry(logId, startMs, response, "[multipart audio — rejected: too large]", response.body)
        call.response.headers.append("x-request-id", logId)
        call.respondHttpResponse(response)
        return@post
      }

      val actualSize = fileBytes?.size?.toLong() ?: contentLengthHeader ?: 0L

      val admission = modelLifecycle.acquireRequestAdmission()
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
          audioTranscriptionHandler.handle(fileBytes, fields, actualSize, model, logId = logId, prefs = prefs)
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
    }
  }
}
