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

package com.ollitert.llm.server.service.audio

import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import com.ollitert.llm.server.service.http.HttpResponse
import com.ollitert.llm.server.service.http.httpBadRequest
import com.ollitert.llm.server.service.http.httpInternalError
import com.ollitert.llm.server.service.http.httpOkJson
import com.ollitert.llm.server.service.http.httpServiceUnavailable
import com.ollitert.llm.server.service.inference.AudioFormat
import com.ollitert.llm.server.service.inference.AudioPreprocessor
import com.ollitert.llm.server.service.inference.MAX_FILE_SIZE_BYTES
import com.ollitert.llm.server.service.inference.TranscriptionFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "OlliteRT.AudioASR"

/**
 * OpenAI-shaped /v1/audio/speech request body.
 *
 * [speed] maps to MediaPipe's engine-level speakingRate: it is applied at engine (re)load time,
 * so a request with a new speed re-creates the engine. Values are clamped to MediaPipe's 0.25-4.0.
 */
@Serializable
internal data class SpeechRequest(
  val input: String? = null,
  val model: String? = null,
  val voice: String? = null,
  val response_format: String? = null,
  val speed: Double? = null,
)

/**
 * Handles POST /v1/audio/asr — MediaPipe AudioTranscriber (parakeet-style) batch transcription.
 *
 * Scoped to WAV input: the MediaPipe engine needs mono 16 kHz Float PCM, which we can only
 * reliably produce from 16-bit PCM WAV (the decoder downmixes stereo and resamples). Other
 * container formats get a clear 400 instead of a silently wrong decode. Returns plain text in
 * the JSON envelope; supports json/text/verbose_json response formats like the LLM endpoint.
 */
internal class MediaPipeAudioHandler(
  private val audioManager: AudioServiceManager,
  private val json: Json,
) {

  suspend fun handle(
    fileBytes: ByteArray?,
    fields: Map<String, String>,
    contentLength: Long,
    logId: String?,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse {
    val startMs = SystemClock.elapsedRealtime()
    ServerMetrics.incrementRequestCount()

    if (fileBytes == null) {
      return httpBadRequest("Missing required 'file' field in multipart form data.")
    }
    if (fileBytes.isEmpty()) {
      return httpBadRequest("Uploaded audio file is empty.")
    }
    if (fileBytes.size > MAX_FILE_SIZE_BYTES) {
      return httpBadRequest("File too large (${fileBytes.size / 1_000_000}MB). Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
    }

    val format = try {
      AudioPreprocessor.detectFormat(fileBytes)
    } catch (e: IllegalArgumentException) {
      return httpBadRequest(e.message ?: "Unsupported audio format.")
    }
    if (format != AudioFormat.WAV) {
      return httpBadRequest(
        "MediaPipe ASR requires a 16-bit PCM WAV file. Detected format: ${format.name.lowercase()}. " +
          "Convert with ffmpeg -i audio.mp3 -c:a pcm_s16le -ar 16000 audio.wav before sending.",
      )
    }

    val requestedModel = fields["model"]?.takeIf { it.isNotBlank() }
    if (requestedModel != null && AudioModelSpecs.byId(requestedModel)?.kind != AudioModelKind.ASR) {
      return httpBadRequest("Unknown ASR model id: '$requestedModel'. Available: ${AudioModelSpecs.asList().filter { it.kind == AudioModelKind.ASR }.joinToString(", ") { it.modelId }}")
    }

    val responseFormat = fields["response_format"]?.takeIf { it.isNotBlank() } ?: "json"
    if (responseFormat in TranscriptionFormatter.UNSUPPORTED_FORMATS) {
      return httpBadRequest("response_format '$responseFormat' requires word-level timing which the MediaPipe ASR engine does not provide. Use json, text, or verbose_json.")
    }
    if (responseFormat !in TranscriptionFormatter.VALID_FORMATS) {
      return httpBadRequest("Invalid response_format '$responseFormat'. Supported: json, text, verbose_json.")
    }

    val language = fields["language"]?.takeIf { it.isNotBlank() }

    val modelId = requestedModel ?: AudioModelSpecs.PARAKEET_TDT_06B_V3_ID

    // Run inference
    val transcriptionStart = SystemClock.elapsedRealtime()
    val text = try {
      audioManager.transcribe(fileBytes, modelId)
    } catch (e: AudioServiceManager.ModelNotFoundException) {
      Log.w(TAG, "ASR model unavailable", e)
      return httpBadRequest(e.message ?: "ASR model unavailable.")
    } catch (e: IllegalArgumentException) {
      return httpBadRequest(e.message ?: "Audio decoding failed.")
    } catch (e: IllegalStateException) {
      Log.w(TAG, "ASR engine busy", e)
      return httpServiceUnavailable("ASR engine is busy processing another request. Retry later.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "ASR inference failed", e)
      return httpInternalError("ASR transcription failed.")
    }
    val inferenceMs = SystemClock.elapsedRealtime() - transcriptionStart
    val elapsedMs = SystemClock.elapsedRealtime() - startMs

    val durationSec = String.format("%.1f", elapsedMs / 1000.0)

    // Log the transcription event
    RequestLogStore.addEvent(
      "ASR transcription: $modelId (${fileBytes.size / 1024}KB, ${durationSec}s)",
      modelName = modelId,
      category = EventCategory.MODEL,
      body = buildString {
        if (language != null) append("Language: $language\n")
        append("Transcript: $text")
      },
    )

    val responseBody = when (responseFormat) {
      "text" -> TranscriptionFormatter.toText(text)
      "verbose_json" -> TranscriptionFormatter.toVerboseJson(text, language, inferenceMs / 1000.0)
      else -> TranscriptionFormatter.toJson(text)
    }

    return when (responseFormat) {
      "text" -> HttpResponse.PlainText(200, "text/plain; charset=utf-8", responseBody)
      else -> httpOkJson(responseBody)
    }
  }

  /**
   * Handles POST /v1/audio/speech — MediaPipe TextToSpeech batch synthesis (gallery parity).
   *
   * OpenAI-compatible request shape: `{input, model, voice, response_format, speed}`. Returns
   * raw audio bytes: 16-bit PCM WAV (default) or raw 16-bit little-endian PCM. The MediaPipe
   * engine has no mp3/opus/aac encoders, so those formats get a clear 400.
   */
  suspend fun handleSpeech(
    body: String,
    captureBody: (String) -> Unit,
    logId: String?,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse {
    val startMs = SystemClock.elapsedRealtime()
    ServerMetrics.incrementRequestCount()
    captureBody(body)

    val request = try {
      json.decodeFromString(SpeechRequest.serializer(), body)
    } catch (e: Exception) {
      return httpBadRequest("Invalid JSON body: ${e.message ?: "malformed request"}.")
    }

    val input = request.input?.trim()
    if (input.isNullOrEmpty()) {
      return httpBadRequest("Missing required 'input' field (the text to synthesize).")
    }

    val modelId = request.model?.takeIf { it.isNotBlank() } ?: AudioModelSpecs.KOKORO_82M_ID
    val spec = AudioModelSpecs.byId(modelId)
      ?: return httpBadRequest(
        "Unknown TTS model id: '$modelId'. Available: " +
          AudioModelSpecs.asList().filter { it.kind == AudioModelKind.TTS }.joinToString(", ") { it.modelId },
      )

    val responseFormat = request.response_format?.takeIf { it.isNotBlank() }?.lowercase() ?: "wav"
    if (responseFormat != "wav" && responseFormat != "pcm") {
      return httpBadRequest(
        "response_format '$responseFormat' is not supported by the on-device MediaPipe TTS engine. Supported: wav, pcm.",
      )
    }

    val speed = (request.speed ?: 1.0).toFloat()
    if (speed < 0.25f || speed > 4.0f) {
      return httpBadRequest("speed must be between 0.25 and 4.0 (got $speed).")
    }

    val voiceId = request.voice?.takeIf { it.isNotBlank() }

    val synthesisStart = SystemClock.elapsedRealtime()
    val result = try {
      val engine = audioManager.loadTts(spec.modelId, speakingRate = speed)
      if (voiceId != null) {
        val available = engine.voices()
        if (available.isNotEmpty() && voiceId !in available) {
          return httpBadRequest("Unknown voice '$voiceId' for model '$modelId'. Available: ${available.joinToString(", ")}")
        }
      }
      engine.synthesize(input, voiceId, null)
    } catch (e: AudioServiceManager.ModelNotFoundException) {
      Log.w(TAG_TTS, "TTS model unavailable", e)
      return httpBadRequest(e.message ?: "TTS model unavailable.")
    } catch (e: IllegalStateException) {
      Log.w(TAG_TTS, "TTS engine busy", e)
      return httpServiceUnavailable("TTS engine is busy processing another request. Retry later.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG_TTS, "TTS synthesis failed", e)
      return httpInternalError("TTS synthesis failed.")
    }
    val inferenceMs = SystemClock.elapsedRealtime() - synthesisStart
    val elapsedMs = SystemClock.elapsedRealtime() - startMs

    if (result.samples.isEmpty()) {
      return httpBadRequest("The TTS engine produced no audio for the given input.")
    }

    val bytes = if (responseFormat == "pcm") {
      floatArrayToPcm16(result.samples)
    } else {
      AudioWavEncoder.encode(result.samples, result.sampleRateHz)
    }
    val contentType = if (responseFormat == "pcm") "audio/L16;rate=${result.sampleRateHz}" else "audio/wav"

    RequestLogStore.addEvent(
      "TTS synthesis: $modelId voice=${voiceId ?: "default"} (${input.length} chars, ${"%.1f".format(elapsedMs / 1000.0)}s, inference ${inferenceMs}ms)",
      modelName = modelId,
      category = EventCategory.MODEL,
      body = buildString {
        append("Text: ")
        appendLine(input.take(200))
        append("Audio: ${bytes.size / 1024}KB @ ${result.sampleRateHz}Hz (${responseFormat})")
      },
    )

    return HttpResponse.Binary(200, contentType, bytes)
  }

  /**
   * Handles GET /v1/audio/voices — lists the voices of a TTS model without loading it.
   *
   * When the model's engine is already resident the full MediaPipe voice metadata (id, display
   * name, locale) is returned; otherwise the allowlist spec's default voices are listed and the
   * response is flagged `loaded: false`.
   */
  suspend fun handleVoices(modelId: String?): HttpResponse {
    val id = modelId?.takeIf { it.isNotBlank() }
      ?: audioManager.currentTtsModelId()
      ?: AudioModelSpecs.KOKORO_82M_ID
    val spec = AudioModelSpecs.byId(id)
      ?: return httpBadRequest(
        "Unknown TTS model id: '$id'. Available: " +
          AudioModelSpecs.asList().filter { it.kind == AudioModelKind.TTS }.joinToString(", ") { it.modelId },
      )

    val engine = audioManager.ttsEngineIfLoaded(id)
    val infos = engine?.voiceInfos().orEmpty()
    val data = buildJsonArray {
      if (infos.isNotEmpty()) {
        for (info in infos) {
          add(
            buildJsonObject {
              put("id", info.voiceId)
              put("name", info.displayName.ifEmpty { info.voiceId })
              put("locale", info.locale)
              put("sample_rate", info.sampleRateHz)
            },
          )
        }
      } else {
        for (defaultId in spec.defaultVoiceIds) {
          add(
            buildJsonObject {
              put("id", defaultId)
              put("name", defaultId)
              put("locale", spec.defaultLanguages.firstOrNull() ?: "en-US")
            },
          )
        }
      }
    }

    val payload = buildJsonObject {
      put("object", "list")
      put("model", id)
      put("loaded", engine != null)
      put("data", data)
      if (engine == null) {
        put(
          "note",
          "Model not loaded; voices are the model spec defaults. Any POST /v1/audio/speech request loads it (first call is slow).",
        )
      }
    }
    return httpOkJson(payload.toString())
  }

  companion object {
    private const val TAG_TTS = "OlliteRT.AudioTTS"

    /** Packs mono Float PCM as raw 16-bit little-endian (OpenAI `pcm` response format). */
    private fun floatArrayToPcm16(samples: FloatArray): ByteArray {
      val out = ByteArray(samples.size * 2)
      var i = 0
      for (sample in samples) {
        val clamped = sample.coerceIn(-1f, 1f)
        val value = (clamped * 32767.0f).toInt()
        out[i] = (value and 0xFF).toByte()
        out[i + 1] = ((value shr 8) and 0xFF).toByte()
        i += 2
      }
      return out
    }
  }
}
