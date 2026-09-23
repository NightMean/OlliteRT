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

package com.ollitert.llm.server.ui.modelmanager

import com.ollitert.llm.server.data.prefs.HTTP_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.prefs.HTTP_READ_TIMEOUT_MS
import com.ollitert.llm.server.data.allowlist.ModelUrlResult
import com.ollitert.llm.server.data.allowlist.probeModelUrl
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.HttpURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelUrlProbeTest {

  @Test
  fun probeDoesNotAttachCredentialsToCleartextHuggingFace() {
    val connection = mockk<HttpURLConnection>(relaxed = true)
    every { connection.responseCode } returns 200
    probeModelUrl("http://huggingface.co/org/repo", "test-token") { connection }
    verify(exactly = 0) { connection.setRequestProperty("Authorization", any()) }
  }

  @Test
  fun authenticatedProbeUsesTheTokenButDoesNotForwardItToTheCdn() {
    val initial = mockk<HttpURLConnection>(relaxed = true)
    val cdn = mockk<HttpURLConnection>(relaxed = true)
    every { initial.responseCode } returns 302
    every { initial.getHeaderField("Location") } returns "https://cdn.example.test/model.litertlm"
    every { cdn.responseCode } returns 200
    every { cdn.contentType } returns "application/octet-stream"

    val result = probeModelUrl("https://huggingface.co/org/repo", "test-token") { url ->
      if (url.startsWith("https://huggingface.co/")) initial else cdn
    }

    assertEquals(ModelUrlResult.Success(200), result)
    verify { initial.setRequestProperty("Authorization", "Bearer test-token") }
    verify(exactly = 0) { cdn.setRequestProperty("Authorization", any()) }
  }

  @Test
  fun actualNetworkExceptionsAreClassifiedWithoutParsingTheirMessages() {
    val result = probeModelUrl("https://huggingface.co/model", null) {
      throw java.net.UnknownHostException("localized error")
    }
    assertTrue((result as ModelUrlResult.Error).retryable)
    val tls = probeModelUrl("https://huggingface.co/model", null) {
      throw javax.net.ssl.SSLHandshakeException("localized error")
    }
    assertFalse((tls as ModelUrlResult.Error).retryable)
  }

  @Test
  fun cancellationIsNotConvertedIntoAnOrdinaryNetworkError() {
    try {
      probeModelUrl("https://huggingface.co/model", null) {
        throw kotlinx.coroutines.CancellationException()
      }
      org.junit.Assert.fail("Expected cancellation")
    } catch (_: kotlinx.coroutines.CancellationException) { }
  }

  @Test
  fun redirectProbeBoundsBothConnections() {
    val initialConnection = mockk<HttpURLConnection>(relaxed = true)
    val redirectConnection = mockk<HttpURLConnection>(relaxed = true)
    every { initialConnection.responseCode } returns HttpURLConnection.HTTP_MOVED_TEMP
    every { initialConnection.getHeaderField("Location") } returns "https://cdn.example/model.bin"
    every { redirectConnection.contentType } returns "application/octet-stream"
    every { redirectConnection.responseCode } returns HttpURLConnection.HTTP_OK

    val result = probeModelUrl(
      modelUrl = "https://example.com/model.bin",
      accessToken = "token",
      openConnection = { url ->
        if (url.contains("cdn.example")) redirectConnection else initialConnection
      },
    )

    assertEquals(ModelUrlResult.Success(HttpURLConnection.HTTP_OK), result)
    verify { initialConnection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS }
    verify { initialConnection.readTimeout = HTTP_READ_TIMEOUT_MS }
    verify { redirectConnection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS }
    verify { redirectConnection.readTimeout = HTTP_READ_TIMEOUT_MS }
    verify { initialConnection.disconnect() }
    verify { redirectConnection.disconnect() }
  }
}
