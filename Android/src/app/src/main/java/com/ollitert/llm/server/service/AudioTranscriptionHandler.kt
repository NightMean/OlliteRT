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
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.data.DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.llmSupportAudio
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

internal const val MAX_FILE_SIZE_BYTES = 50_000_000L
private const val TAG = "OlliteRT.AudioSTT"

/**
 * Handles POST /v1/audio/transcriptions — OpenAI Whisper-compatible endpoint.
 *
 * Accepts pre-parsed multipart data: fileBytes from the "file" part, fields for
 * all other form fields (model, language, prompt, temperature, response_format).
 * Content-Length checking and multipart parsing happen in the caller (KtorServer)
 * before this method is invoked. Passes audio directly to the LiteRT SDK via
 * Content.AudioBytes — the model natively processes audio without prompt engineering.
 */
class AudioTranscriptionHandler(
  private val context: Context,
  private val inferenceRunner: InferenceRunner,
  private val modelLifecycle: ModelLifecycle,
) {

  suspend fun handle(
    fileBytes: ByteArray?,
    fields: Map<String, String>,
    contentLength: Long,
    model: Model,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse {
    val startMs = SystemClock.elapsedRealtime()

    if (fileBytes == null) {
      return httpBadRequest("Missing required 'file' field in multipart form data.")
    }

    if (fileBytes.isEmpty()) {
      return httpBadRequest("Uploaded audio file is empty.")
    }

    if (fileBytes.size > MAX_FILE_SIZE_BYTES) {
      return httpBadRequest("File too large (${fileBytes.size / 1_000_000}MB). Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
    }

    val tempFile = File(context.cacheDir, "audio_upload_${System.currentTimeMillis()}.tmp")
    try {
      try {
        tempFile.writeBytes(fileBytes)
      } catch (e: java.io.IOException) {
        Log.e(TAG, "Failed to write audio to temp file", e)
        return httpInternalError("Failed to write audio to temp file")
      }
      val language = fields["language"]?.takeIf { it.isNotBlank() }
      val prompt = fields["prompt"]?.takeIf { it.isNotBlank() }
      val temperatureStr = fields["temperature"]?.takeIf { it.isNotBlank() }
      val responseFormat = fields["response_format"]?.takeIf { it.isNotBlank() } ?: "json"

      // srt/vtt require word-level timestamps — LiteRT returns raw text without timing data
      if (responseFormat in TranscriptionFormatter.UNSUPPORTED_FORMATS) {
        return httpBadRequest("response_format '$responseFormat' requires word-level timing which the LiteRT runtime does not provide. Use json, text, or verbose_json.")
      }

      if (responseFormat !in TranscriptionFormatter.VALID_FORMATS) {
        return httpBadRequest("Invalid response_format '$responseFormat'. Supported: json, text, verbose_json.")
      }

      val requestedModel = fields["model"]

      if (requestedModel != null) {
        Log.d(TAG, "Client requested model='$requestedModel', using active model='${model.name}'")
      }

      // Log the request body summary for the Logs tab
      captureBody(buildString {
        append("multipart/form-data: file=${tempFile.length()} bytes")
        if (language != null) append(", language=$language")
        if (prompt != null) append(", prompt=${prompt.take(50)}")
        if (temperatureStr != null) append(", temperature=$temperatureStr")
        append(", response_format=$responseFormat")
        if (requestedModel != null) append(", model=$requestedModel")
      })

      // Validate model supports audio
      if (!model.llmSupportAudio) {
        return httpBadRequest("The active model '${model.name}' does not support audio input. Load a model with audio capability (e.g. Gemma 4).")
      }

      // Read and preprocess audio
      val preprocessStart = SystemClock.elapsedRealtime()
      val audioBytes: ByteArray
      val format: AudioFormat
      val rawSize: Long = tempFile.length()
      var wavInfo: AudioPreprocessor.WavInfo? = null
      var downmixed = false
      try {
        val rawBytes = tempFile.readBytes()
        format = AudioPreprocessor.detectFormat(rawBytes)
        if (format == AudioFormat.UNKNOWN) {
          return httpBadRequest("Unsupported audio format. Supported: wav, mp3, ogg, flac.")
        }
        if (format == AudioFormat.WAV) {
          wavInfo = AudioPreprocessor.inspectWav(rawBytes)
          downmixed = (wavInfo?.channels ?: 0) > 1
        }
        audioBytes = AudioPreprocessor.ensureMono(rawBytes, format)
      } catch (e: IllegalArgumentException) {
        return httpBadRequest(e.message ?: "Audio preprocessing failed.")
      }
      val preprocessMs = SystemClock.elapsedRealtime() - preprocessStart

      val useTranscriptionPrompt = prefs.sttTranscriptionPromptEnabled
      val hintText = buildString {
        if (useTranscriptionPrompt) {
          val customPrompt = prefs.sttTranscriptionPromptText
          append(customPrompt.ifBlank { DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT })
        }
        if (language != null) {
          if (isNotEmpty()) append("\n")
          append("Language: $language")
        }
        if (prompt != null) {
          if (isNotEmpty()) append("\n")
          append("Context: $prompt")
        }
      }

      val temperature = temperatureStr?.toDoubleOrNull()
      val sampler = resolveSamplerOverrides(model, prefs, temperature, topP = null, topK = null, maxTokens = null, logId)
      val configSnapshot = sampler.configSnapshot

      // Run inference
      val inferenceStart = SystemClock.elapsedRealtime()
      ServerMetrics.onInferenceStarted()
      val (rawOutput, llmError) = inferenceRunner.runLlm(
        model = model,
        prompt = hintText,
        requestId = "transcription-${System.currentTimeMillis()}",
        endpoint = "/v1/audio/transcriptions",
        audioClips = listOf(audioBytes),
        logId = logId,
        configSnapshot = configSnapshot,
      )
      ServerMetrics.onInferenceCompleted()
      val inferenceMs = SystemClock.elapsedRealtime() - inferenceStart

      val elapsedMs = SystemClock.elapsedRealtime() - startMs

      if (rawOutput == null) {
        val (errorMsg, kind) = InferenceRunner.enrichLlmError(llmError ?: "llm error", context)
        ServerMetrics.incrementErrorCount(kind.category)
        return httpInternalError(errorMsg, kind = kind)
      }

      // Strip thinking tags if present, trim whitespace
      val text = stripThinkingTags(rawOutput).trim()

      // Log transcription event
      val formatLabel = format.name.lowercase()
      val fileSizeKb = tempFile.length() / 1024
      val sizeLabel = if (fileSizeKb >= 1024) {
        String.format(java.util.Locale.US, "%.1fMB", fileSizeKb / 1024.0)
      } else {
        "${fileSizeKb}KB"
      }
      val durationSec = String.format(java.util.Locale.US, "%.1f", elapsedMs / 1000.0)
      val forcedTag = if (useTranscriptionPrompt) ", forced" else ""
      val eventMessage = if (language != null) {
        "Audio transcription: ${model.name} (lang=$language, $formatLabel, $sizeLabel, ${durationSec}s$forcedTag)"
      } else {
        "Audio transcription: ${model.name} ($formatLabel, $sizeLabel, ${durationSec}s$forcedTag)"
      }
      val eventBody = buildJsonObject {
        put("type", "audio_transcription")
        if (hintText.isNotEmpty()) put("instruction", hintText)
        put("transcription", text)
      }.toString()
      RequestLogStore.addEvent(
        eventMessage,
        modelName = model.name,
        category = EventCategory.MODEL,
        body = eventBody,
      )

      if (prefs.verboseDebug) {
        val debugText = buildString {
          appendLine("Format: ${formatLabel.uppercase()}, ${rawSize} bytes → ${audioBytes.size} bytes")
          if (wavInfo != null) {
            appendLine("WAV: ${wavInfo.channels}ch, ${wavInfo.sampleRate}Hz, ${wavInfo.bitsPerSample}-bit")
          }
          if (downmixed) appendLine("Stereo → mono downmix applied")
          appendLine("Force transcription: ${if (useTranscriptionPrompt) "on" else "off"}")
          appendLine("Response format: $responseFormat")
          if (language != null) appendLine("Language: $language")
          if (prompt != null) appendLine("Client prompt: $prompt")
          if (temperature != null) appendLine("Temperature: $temperature")
          appendLine("Hint text: ${hintText.ifEmpty { "(none)" }}")
          append("Timing: prep ${preprocessMs}ms, inference ${inferenceMs}ms, total ${elapsedMs}ms")
        }
        RequestLogStore.addEvent(
          "Audio transcription debug",
          level = LogLevel.DEBUG,
          modelName = model.name,
          category = EventCategory.MODEL,
          body = debugText,
        )
      }

      val inferenceSeconds = inferenceMs / 1000.0

      val responseBody = when (responseFormat) {
        "text" -> TranscriptionFormatter.toText(text)
        "verbose_json" -> TranscriptionFormatter.toVerboseJson(text, language, inferenceSeconds)
        else -> TranscriptionFormatter.toJson(text)
      }

      captureResponse(responseBody)

      return when (responseFormat) {
        "text" -> HttpResponse.PlainText(200, "text/plain; charset=utf-8", responseBody)
        else -> httpOkJson(responseBody)
      }
    } finally {
      tempFile.delete()
    }
  }

  companion object {
    private val THINKING_TAG_REGEX = Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL)

    private fun stripThinkingTags(text: String): String =
      text.replace(THINKING_TAG_REGEX, "")

  }
}
