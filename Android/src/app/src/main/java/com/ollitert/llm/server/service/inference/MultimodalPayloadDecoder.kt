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

package com.ollitert.llm.server.service.inference

import android.content.Context
import android.util.Base64
import android.util.Log
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_SHORT_CHARS
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.service.http.ChatMessage

/**
 * Decodes multimodal request payloads (base64 image data URIs and audio clips from
 * chat messages) into raw bytes for multimodal inference.
 *
 * Extracted from [ModelLifecycle] — payload decoding is per-request preparation,
 * not engine/model lifecycle state.
 */
internal class MultimodalPayloadDecoder(
  private val context: Context,
  /** Resolves the active model name for diagnostic log events at decode time. */
  private val activeModelName: () -> String?,
) {

  /**
   * Decodes base64 image data URIs from chat messages into raw byte arrays for multimodal
   * inference. The LiteRT SDK's Content.ImageBytes accepts raw bytes and detects the format
   * (JPEG, PNG, WebP) from magic bytes in the native layer — no Bitmap intermediate needed.
   * Expected format: `data:image/jpeg;base64,/9j/4AAQ...`
   */
  fun decodeImageDataUris(messages: List<ChatMessage>): List<ByteArray> {
    val uris = PromptBuilder.extractImageDataUris(messages)
    return uris.mapNotNull { uri ->
      try {
        val base64Data = if (uri.contains(",")) uri.substringAfter(",") else uri
        Base64.decode(base64Data, Base64.DEFAULT)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to decode image data URI", e)
        RequestLogStore.addEvent(
          "Failed to decode image: ${e.message?.take(LOG_ERROR_PREVIEW_SHORT_CHARS) ?: context.getString(R.string.error_unknown)}",
          level = LogLevel.ERROR,
          modelName = activeModelName(),
          category = EventCategory.SERVER,
        )
        null
      }
    }
  }

  /**
   * Decodes base64 audio data strings from `input_audio` content parts into raw byte arrays,
   * then ensures each clip is mono PCM (required by the LiteRT audio API).
   * Silently drops any clip that fails to decode or preprocess — same error-resilience
   * pattern used by [decodeImageDataUris].
   */
  fun decodeAudioData(dataStrings: List<String>): List<ByteArray> {
    return dataStrings.mapNotNull { base64Data ->
      try {
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        val format = AudioPreprocessor.detectFormat(bytes)
        AudioPreprocessor.ensureMono(bytes, format)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to decode audio data", e)
        RequestLogStore.addEvent(
          "Failed to decode audio: ${e.message?.take(LOG_ERROR_PREVIEW_SHORT_CHARS) ?: context.getString(R.string.error_unknown)}",
          level = LogLevel.ERROR,
          modelName = activeModelName(),
          category = EventCategory.SERVER,
        )
        null
      }
    }
  }

  companion object {
    private const val TAG = "OlliteRT.Multimodal"
  }
}
