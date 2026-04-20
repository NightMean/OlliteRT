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
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.llmSupportAudio
import fi.iki.elonen.NanoHTTPD
import java.io.File

private const val MAX_FILE_SIZE_BYTES = 25_000_000L
private const val LOG_TAG = "AudioTranscription"

/**
 * Handles POST /v1/audio/transcriptions — OpenAI Whisper-compatible endpoint.
 *
 * Accepts multipart/form-data with an audio file and optional fields (model, language,
 * prompt, temperature, response_format). Passes audio directly to the LiteRT SDK via
 * Content.AudioBytes — the model natively processes audio without prompt engineering.
 */
class LlmHttpAudioTranscriptionHandler(
  private val context: Context,
  private val inferenceRunner: LlmHttpInferenceRunner,
  private val modelLifecycle: LlmHttpModelLifecycle,
) {

  fun handle(
    session: NanoHTTPD.IHTTPSession,
    model: Model,
    captureBody: (String) -> Unit,
    captureResponse: (String) -> Unit,
    logId: String?,
  ): NanoHTTPD.Response {
    val startMs = SystemClock.elapsedRealtime()

    // Check Content-Length before parseBody() to reject oversized uploads before
    // NanoHTTPD buffers the entire multipart body into heap memory.
    session.headers["content-length"]?.toLongOrNull()?.let { contentLength ->
      if (contentLength > MAX_FILE_SIZE_BYTES) {
        return openAiError(
          NanoHTTPD.Response.Status.BAD_REQUEST,
          "File too large (${contentLength / 1_000_000}MB). Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.",
          "invalid_request_error",
          "file_too_large",
        )
      }
    }

    // Parse multipart form data — NanoHTTPD writes file uploads to temp files on disk
    val files = HashMap<String, String>()
    try {
      session.parseBody(files)
    } catch (e: Exception) {
      Log.w(LOG_TAG, "Multipart parse failed", e)
      return openAiError(
        NanoHTTPD.Response.Status.BAD_REQUEST,
        "Failed to parse multipart form data: ${e.message}",
        "invalid_request_error",
        "parse_error",
      )
    }

    // Extract form fields
    val audioTempPath = files["file"]
    if (audioTempPath == null) {
      return openAiError(
        NanoHTTPD.Response.Status.BAD_REQUEST,
        "Missing required 'file' field in multipart form data.",
        "invalid_request_error",
        "missing_file",
      )
    }

    val tempFile = File(audioTempPath)
    try {
      if (!tempFile.exists() || tempFile.length() == 0L) {
        return openAiError(
          NanoHTTPD.Response.Status.BAD_REQUEST,
          "Uploaded audio file is empty.",
          "invalid_request_error",
          "empty_file",
        )
      }

      // Secondary size check after parseBody (clients may use chunked encoding without Content-Length)
      if (tempFile.length() > MAX_FILE_SIZE_BYTES) {
        return openAiError(
          NanoHTTPD.Response.Status.BAD_REQUEST,
          "File too large (${tempFile.length() / 1_000_000}MB). Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.",
          "invalid_request_error",
          "file_too_large",
        )
      }

      val language = session.parameters["language"]?.firstOrNull()?.takeIf { it.isNotBlank() }
      val prompt = session.parameters["prompt"]?.firstOrNull()?.takeIf { it.isNotBlank() }
      val temperatureStr = session.parameters["temperature"]?.firstOrNull()?.takeIf { it.isNotBlank() }
      val responseFormat = session.parameters["response_format"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "json"
      val requestedModel = session.parameters["model"]?.firstOrNull()

      if (requestedModel != null) {
        Log.d(LOG_TAG, "Client requested model='$requestedModel', using active model='${model.name}'")
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
        return openAiError(
          NanoHTTPD.Response.Status.BAD_REQUEST,
          "The active model '${model.name}' does not support audio input. Load a model with audio capability (e.g. Gemma 4).",
          "invalid_request_error",
          "model_not_supported",
        )
      }

      // Read and preprocess audio
      val preprocessStart = SystemClock.elapsedRealtime()
      val audioBytes: ByteArray
      val format: AudioFormat
      val rawSize: Long = tempFile.length()
      var wavInfo: LlmHttpAudioPreprocessor.WavInfo? = null
      var downmixed = false
      try {
        val rawBytes = tempFile.readBytes()
        format = LlmHttpAudioPreprocessor.detectFormat(rawBytes)
        if (format == AudioFormat.UNKNOWN) {
          return openAiError(
            NanoHTTPD.Response.Status.BAD_REQUEST,
            "Unsupported audio format. Supported: wav, mp3, ogg, flac.",
            "invalid_request_error",
            "unsupported_format",
          )
        }
        if (format == AudioFormat.WAV) {
          wavInfo = LlmHttpAudioPreprocessor.inspectWav(rawBytes)
          downmixed = (wavInfo?.channels ?: 0) > 1
        }
        audioBytes = LlmHttpAudioPreprocessor.ensureMono(rawBytes, format)
      } catch (e: IllegalArgumentException) {
        return openAiError(
          NanoHTTPD.Response.Status.BAD_REQUEST,
          e.message ?: "Audio preprocessing failed.",
          "invalid_request_error",
          "unsupported_format",
        )
      }
      val preprocessMs = SystemClock.elapsedRealtime() - preprocessStart

      val useTranscriptionPrompt = LlmHttpPrefs.isSttTranscriptionPromptEnabled(context)
      val hintText = buildString {
        if (useTranscriptionPrompt) {
          append("Transcribe the audio exactly as spoken. Output only the transcribed text, nothing else.")
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

      // Parse temperature
      val temperature = temperatureStr?.toDoubleOrNull()
      val configSnapshot = buildPerRequestConfig(model, temperature)

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
        val (errorMsg, kind) = LlmHttpInferenceRunner.enrichLlmError(llmError ?: "llm error", context)
        ServerMetrics.incrementErrorCount(kind.category)
        return openAiError(
          NanoHTTPD.Response.Status.INTERNAL_ERROR,
          errorMsg,
          "server_error",
          "inference_error",
        )
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
      val eventBody = org.json.JSONObject().apply {
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

      if (LlmHttpPrefs.isVerboseDebugEnabled(context)) {
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

      // Build response
      val responseBody = if (responseFormat == "text") {
        text
      } else {
        """{"text":${escapeJsonString(text)}}"""
      }

      captureResponse(responseBody)

      return if (responseFormat == "text") {
        NanoHTTPD.newFixedLengthResponse(
          NanoHTTPD.Response.Status.OK,
          "text/plain; charset=utf-8",
          responseBody,
        )
      } else {
        okJsonText(responseBody)
      }
    } finally {
      tempFile.delete()
    }
  }

  companion object {
    private val THINKING_TAG_REGEX = Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL)

    private fun stripThinkingTags(text: String): String =
      text.replace(THINKING_TAG_REGEX, "")

    private fun escapeJsonString(value: String): String {
      val sb = StringBuilder(value.length + 2)
      sb.append('"')
      for (ch in value) {
        when (ch) {
          '"' -> sb.append("\\\"")
          '\\' -> sb.append("\\\\")
          '\n' -> sb.append("\\n")
          '\r' -> sb.append("\\r")
          '\t' -> sb.append("\\t")
          else -> {
            if (ch.code < 0x20) {
              sb.append("\\u")
              sb.append(String.format("%04x", ch.code))
            } else {
              sb.append(ch)
            }
          }
        }
      }
      sb.append('"')
      return sb.toString()
    }

    private fun openAiError(
      status: NanoHTTPD.Response.Status,
      message: String,
      type: String,
      code: String,
    ): NanoHTTPD.Response {
      val escaped = escapeJsonString(message)
      val body = """{"error":{"message":$escaped,"type":"$type","code":"$code"}}"""
      return NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", body)
    }
  }
}
