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
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelCapability
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioTranscriptionHandlerTest {

  private val context: Context = mockk(relaxed = true)
  private val inferenceRunner: InferenceRunner = mockk(relaxed = true)
  private val modelLifecycle: ModelLifecycle = mockk(relaxed = true)

  private val handler = AudioTranscriptionHandler(context, inferenceRunner, modelLifecycle)

  private fun audioModel(): Model = Model(
    name = "test-audio-model",
    capabilities = setOf(ModelCapability.AUDIO),
  )

  private fun nonAudioModel(): Model = Model(
    name = "test-text-model",
    capabilities = emptySet(),
  )

  @Before
  fun setUp() {
    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.i(any(), any()) } returns 0
    every { Log.w(any<String>(), any<String>()) } returns 0
    every { Log.e(any(), any()) } returns 0
    every { Log.e(any(), any(), any()) } returns 0

    mockkStatic(SystemClock::class)
    every { SystemClock.elapsedRealtime() } returns 1000L

    mockkObject(ServerMetrics)
    every { ServerMetrics.onInferenceStarted() } returns Unit
    every { ServerMetrics.onInferenceCompleted() } returns Unit

    mockkObject(RequestLogStore)
    every { RequestLogStore.addEvent(any(), any(), any(), any(), any()) } returns Unit
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  // ── Missing file ──────────────────────────────────────────────────────────

  @Test
  fun missingFileReturns400() = runBlocking {
    val result = handler.handle(
      fileBytes = null,
      fields = emptyMap(),
      contentLength = 0,
      model = audioModel(),
    )
    assertBadRequest(result, "Missing required 'file' field")
  }

  // ── Empty file ────────────────────────────────────────────────────────────

  @Test
  fun emptyFileReturns400() = runBlocking {
    val result = handler.handle(
      fileBytes = ByteArray(0),
      fields = emptyMap(),
      contentLength = 0,
      model = audioModel(),
    )
    assertBadRequest(result, "empty")
  }

  // ── File too large ────────────────────────────────────────────────────────

  @Test
  fun fileTooLargeReturns400() = runBlocking {
    val oversized = ByteArray(MAX_FILE_SIZE_BYTES.toInt() + 1)
    val result = handler.handle(
      fileBytes = oversized,
      fields = emptyMap(),
      contentLength = oversized.size.toLong(),
      model = audioModel(),
    )
    assertBadRequest(result, "File too large")
  }

  // ── Unsupported response_format ───────────────────────────────────────────

  @Test
  fun unsupportedSrtFormatReturns400() = runBlocking {
    val wavBytes = buildMinimalWav()
    val result = handler.handle(
      fileBytes = wavBytes,
      fields = mapOf("response_format" to "srt"),
      contentLength = wavBytes.size.toLong(),
      model = audioModel(),
    )
    assertBadRequest(result, "srt")
  }

  @Test
  fun unsupportedVttFormatReturns400() = runBlocking {
    val wavBytes = buildMinimalWav()
    val result = handler.handle(
      fileBytes = wavBytes,
      fields = mapOf("response_format" to "vtt"),
      contentLength = wavBytes.size.toLong(),
      model = audioModel(),
    )
    assertBadRequest(result, "vtt")
  }

  // ── Invalid response_format ───────────────────────────────────────────────

  @Test
  fun invalidResponseFormatReturns400() = runBlocking {
    val wavBytes = buildMinimalWav()
    val result = handler.handle(
      fileBytes = wavBytes,
      fields = mapOf("response_format" to "xml"),
      contentLength = wavBytes.size.toLong(),
      model = audioModel(),
    )
    assertBadRequest(result, "Invalid response_format")
  }

  // ── Model without audio support ───────────────────────────────────────────

  @Test
  fun nonAudioModelReturns400() = runBlocking {
    val wavBytes = buildMinimalWav()
    val result = handler.handle(
      fileBytes = wavBytes,
      fields = emptyMap(),
      contentLength = wavBytes.size.toLong(),
      model = nonAudioModel(),
    )
    assertBadRequest(result, "does not support audio")
  }

  // ── Unsupported audio format ──────────────────────────────────────────────

  @Test
  fun unknownAudioFormatReturns400() = runBlocking {
    val garbage = ByteArray(100) { 0x42 }
    val result = handler.handle(
      fileBytes = garbage,
      fields = emptyMap(),
      contentLength = garbage.size.toLong(),
      model = audioModel(),
    )
    assertBadRequest(result, "Unsupported audio format")
  }

  // ── Successful transcription (json format) ────────────────────────────────

  @Test
  fun successfulTranscriptionReturnsJsonResponse() = runBlocking {
    val wavBytes = buildMinimalWav()
    coEvery { inferenceRunner.runLlm(
      model = any(),
      prompt = any(),
      requestId = any(),
      endpoint = any(),
      timeoutSeconds = any(),
      images = any(),
      audioClips = any(),
      eagerVisionInit = any(),
      logId = any(),
      configSnapshot = any(),
      prefs = any(),
    ) } returns ("Hello world" to null)

    val result = handler.handle(
      fileBytes = wavBytes,
      fields = emptyMap(),
      contentLength = wavBytes.size.toLong(),
      model = audioModel(),
      prefs = RequestPrefsSnapshot(),
    )
    assertEquals(200, result.statusCode)
    val json = (result as HttpResponse.Json).body
    assertTrue(json.contains("Hello world"))
  }

  // ── Successful transcription strips thinking tags ─────────────────────────

  @Test
  fun successfulTranscriptionStripsThinkingTags() = runBlocking {
    val wavBytes = buildMinimalWav()
    coEvery { inferenceRunner.runLlm(
      model = any(),
      prompt = any(),
      requestId = any(),
      endpoint = any(),
      timeoutSeconds = any(),
      images = any(),
      audioClips = any(),
      eagerVisionInit = any(),
      logId = any(),
      configSnapshot = any(),
      prefs = any(),
    ) } returns ("<think>internal reasoning</think>Actual output" to null)

    val result = handler.handle(
      fileBytes = wavBytes,
      fields = emptyMap(),
      contentLength = wavBytes.size.toLong(),
      model = audioModel(),
      prefs = RequestPrefsSnapshot(),
    )
    assertEquals(200, result.statusCode)
    val json = (result as HttpResponse.Json).body
    assertTrue(json.contains("Actual output"))
    assertFalse(json.contains("<think>"))
  }

  // ── Text response format ──────────────────────────────────────────────────

  @Test
  fun textResponseFormatReturnsPlainText() = runBlocking {
    val wavBytes = buildMinimalWav()
    coEvery { inferenceRunner.runLlm(
      model = any(),
      prompt = any(),
      requestId = any(),
      endpoint = any(),
      timeoutSeconds = any(),
      images = any(),
      audioClips = any(),
      eagerVisionInit = any(),
      logId = any(),
      configSnapshot = any(),
      prefs = any(),
    ) } returns ("Plain text output" to null)

    val result = handler.handle(
      fileBytes = wavBytes,
      fields = mapOf("response_format" to "text"),
      contentLength = wavBytes.size.toLong(),
      model = audioModel(),
      prefs = RequestPrefsSnapshot(),
    )
    assertEquals(200, result.statusCode)
    assertTrue(result is HttpResponse.PlainText)
    assertEquals("Plain text output", (result as HttpResponse.PlainText).body)
  }

  // ── Inference error ───────────────────────────────────────────────────────

  @Test
  fun inferenceErrorReturnsErrorResponse() = runBlocking {
    val wavBytes = buildMinimalWav()

    mockkStatic("com.ollitert.llm.server.service.EndpointHandlersKt")
    every { handleBlockingInferenceError(any(), any(), any()) } returns
      httpInternalError("Model inference failed")

    coEvery { inferenceRunner.runLlm(
      model = any(),
      prompt = any(),
      requestId = any(),
      endpoint = any(),
      timeoutSeconds = any(),
      images = any(),
      audioClips = any(),
      eagerVisionInit = any(),
      logId = any(),
      configSnapshot = any(),
      prefs = any(),
    ) } returns (null to "timeout")

    val result = handler.handle(
      fileBytes = wavBytes,
      fields = emptyMap(),
      contentLength = wavBytes.size.toLong(),
      model = audioModel(),
      prefs = RequestPrefsSnapshot(),
    )
    assertEquals(500, result.statusCode)
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private fun assertBadRequest(response: HttpResponse, expectedSubstring: String) {
    assertEquals(400, response.statusCode)
    val body = (response as HttpResponse.Json).body
    assertTrue(
      "Expected body to contain '$expectedSubstring' but was: $body",
      body.contains(expectedSubstring),
    )
  }

  private fun buildMinimalWav(): ByteArray {
    val dataSize = 2
    val fileSize = 36 + dataSize
    val buffer = java.nio.ByteBuffer.allocate(44 + dataSize)
      .order(java.nio.ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray())
    buffer.putInt(fileSize)
    buffer.put("WAVE".toByteArray())
    buffer.put("fmt ".toByteArray())
    buffer.putInt(16)
    buffer.putShort(1) // PCM
    buffer.putShort(1) // mono
    buffer.putInt(16000) // sample rate
    buffer.putInt(32000) // byte rate
    buffer.putShort(2) // block align
    buffer.putShort(16) // bits per sample
    buffer.put("data".toByteArray())
    buffer.putInt(dataSize)
    buffer.putShort(0)
    return buffer.array()
  }
}
