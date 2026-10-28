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

import com.ollitert.llm.server.service.inference.MAX_FILE_SIZE_BYTES
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray

private const val MAX_AUDIO_MULTIPART_PARTS = 32
private const val MAX_AUDIO_FORM_FIELD_CHARS = 65_536

internal class AudioMultipartLimitException(message: String) : java.io.IOException(message)
internal data class AudioMultipart(val fileBytes: ByteArray?, val fields: Map<String, String>)

internal suspend fun readAudioMultipart(call: ApplicationCall): AudioMultipart {
  var fileBytes: ByteArray? = null
  val fields = mutableMapOf<String, String>()
  var partCount = 0
  call.receiveMultipart(formFieldLimit = MAX_FILE_SIZE_BYTES).forEachPart { part ->
    var rejection: AudioMultipartLimitException? = null
    try {
      if (++partCount > MAX_AUDIO_MULTIPART_PARTS) throw AudioMultipartLimitException("Too many multipart parts")
      when (part) {
        is PartData.FileItem -> {
          if (fileBytes != null) throw AudioMultipartLimitException("Multiple file parts are not supported")
          val bytes = part.provider().readRemaining(MAX_FILE_SIZE_BYTES + 1).readByteArray()
          if (bytes.size > MAX_FILE_SIZE_BYTES) throw AudioMultipartLimitException("File too large")
          fileBytes = bytes
        }
        is PartData.FormItem -> {
          if (part.value.length > MAX_AUDIO_FORM_FIELD_CHARS) throw AudioMultipartLimitException("Form field too large")
          fields[part.name ?: ""] = part.value
        }
        else -> {}
      }
    } catch (e: AudioMultipartLimitException) {
      rejection = e
      when (part) {
        is PartData.FileItem -> part.provider().cancel(e)
        is PartData.BinaryChannelItem -> part.provider().cancel(e)
        else -> {}
      }
      throw e
    } finally {
      // Suspending release is cancellable for a stalled upload; dispose blocks a thread.
      try {
        part.release()
      } catch (e: Exception) {
        if (rejection == null || e is CancellationException) throw e
        rejection.addSuppressed(e)
      }
    }
  }
  return AudioMultipart(fileBytes, fields.toMap())
}
