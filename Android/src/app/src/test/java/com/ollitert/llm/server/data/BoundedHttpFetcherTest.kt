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

package com.ollitert.llm.server.data

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BoundedHttpFetcherTest {

  private lateinit var mockConnection: HttpURLConnection
  private lateinit var openConnection: (String) -> HttpURLConnection

  @Before
  fun setUp() {
    mockkStatic(Log::class)
    every { Log.w(any<String>(), any<String>()) } returns 0
    every { Log.w(any<String>(), any<String>(), any()) } returns 0

    mockConnection = mockk(relaxed = true)
    openConnection = { mockConnection }
  }

  @After
  fun tearDown() {
    unmockkStatic(Log::class)
  }

  private fun stubSuccessResponse(body: String, contentLength: Long? = null) {
    every { mockConnection.responseCode } returns 200
    every { mockConnection.getHeaderField("Content-Length") } returns contentLength?.toString()
    every { mockConnection.inputStream } returns ByteArrayInputStream(body.toByteArray())
  }

  // ── Success path ───────────────────────────────────────────────────────

  @Test
  fun `fetchBoundedResult - success returns body`() {
    stubSuccessResponse("hello world")
    val result = fetchBoundedResult("https://example.com/file.json", "TestAgent", openConnection = openConnection)
    assertTrue(result is FetchResult.Success)
    assertEquals("hello world", (result as FetchResult.Success).body)
  }

  @Test
  fun `fetchBounded - success returns body string`() {
    stubSuccessResponse("data")
    val result = fetchBounded("https://example.com/data", "TestAgent", openConnection = openConnection)
    assertEquals("data", result)
  }

  @Test
  fun `fetchBounded - failure returns null`() {
    every { mockConnection.responseCode } returns 500
    val result = fetchBounded("https://example.com/fail", "TestAgent", openConnection = openConnection)
    assertNull(result)
  }

  // ── HTTP error codes ───────────────────────────────────────────────────

  @Test
  fun `fetchBoundedResult - non-2xx returns HttpError`() {
    every { mockConnection.responseCode } returns 404
    val result = fetchBoundedResult("https://example.com/missing", "TestAgent", openConnection = openConnection)
    assertTrue(result is FetchResult.HttpError)
    assertEquals(404, (result as FetchResult.HttpError).code)
  }

  @Test
  fun `fetchBoundedResult - 500 returns HttpError`() {
    every { mockConnection.responseCode } returns 500
    val result = fetchBoundedResult("https://example.com/error", "TestAgent", openConnection = openConnection)
    assertTrue(result is FetchResult.HttpError)
    assertEquals(500, (result as FetchResult.HttpError).code)
  }

  // ── Redirect handling ──────────────────────────────────────────────────

  @Test
  fun `fetchBoundedResult - too many redirects returns NetworkError`() {
    val result = fetchBoundedResult(
      "https://example.com", "TestAgent",
      redirectCount = MAX_REDIRECTS + 1,
      openConnection = openConnection,
    )
    assertTrue(result is FetchResult.NetworkError)
    assertTrue((result as FetchResult.NetworkError).message.contains("Too many redirects"))
  }

  @Test
  fun `fetchBoundedResult - redirect without Location header returns NetworkError`() {
    every { mockConnection.responseCode } returns 301
    every { mockConnection.getHeaderField("Location") } returns null
    val result = fetchBoundedResult("https://example.com/old", "TestAgent", openConnection = openConnection)
    assertTrue(result is FetchResult.NetworkError)
    assertTrue((result as FetchResult.NetworkError).message.contains("no Location"))
  }

  @Test
  fun `fetchBoundedResult - HTTPS to HTTP redirect rejected`() {
    every { mockConnection.responseCode } returns 302
    every { mockConnection.getHeaderField("Location") } returns "http://example.com/insecure"
    val result = fetchBoundedResult("https://example.com/secure", "TestAgent", openConnection = openConnection)
    assertTrue(result is FetchResult.NetworkError)
    assertTrue((result as FetchResult.NetworkError).message.contains("HTTPS to HTTP"))
  }

  @Test
  fun `fetchBoundedResult - redirect to unsupported protocol rejected`() {
    every { mockConnection.responseCode } returns 307
    every { mockConnection.getHeaderField("Location") } returns "ftp://example.com/file"
    val result = fetchBoundedResult("https://example.com/redirect", "TestAgent", openConnection = openConnection)
    assertTrue(result is FetchResult.NetworkError)
    assertTrue((result as FetchResult.NetworkError).message.contains("unsupported protocol"))
  }

  @Test
  fun `fetchBoundedResult - valid HTTPS redirect is followed`() {
    var callCount = 0
    val redirectConnection: HttpURLConnection = mockk(relaxed = true)
    val factory: (String) -> HttpURLConnection = { url ->
      callCount++
      if (url == "https://example.com/old") mockConnection else redirectConnection
    }
    every { mockConnection.responseCode } returns 301
    every { mockConnection.getHeaderField("Location") } returns "https://example.com/new"
    every { redirectConnection.responseCode } returns 200
    every { redirectConnection.getHeaderField("Content-Length") } returns null
    every { redirectConnection.inputStream } returns ByteArrayInputStream("redirected".toByteArray())

    val result = fetchBoundedResult("https://example.com/old", "TestAgent", openConnection = factory)
    assertTrue(result is FetchResult.Success)
    assertEquals("redirected", (result as FetchResult.Success).body)
    assertEquals(2, callCount)
  }

  // ── Size limits ────────────────────────────────────────────────────────

  @Test
  fun `fetchBoundedResult - Content-Length exceeding limit returns NetworkError`() {
    every { mockConnection.responseCode } returns 200
    every { mockConnection.getHeaderField("Content-Length") } returns "${MAX_ALLOWLIST_RESPONSE_BYTES + 1}"
    val result = fetchBoundedResult("https://example.com/huge", "TestAgent", openConnection = openConnection)
    assertTrue(result is FetchResult.NetworkError)
    assertTrue((result as FetchResult.NetworkError).message.contains("too large"))
  }

  @Test
  fun `fetchBoundedResult - body within Content-Length limit succeeds`() {
    stubSuccessResponse("small body", contentLength = 10L)
    val result = fetchBoundedResult("https://example.com/ok", "TestAgent", openConnection = openConnection)
    assertTrue(result is FetchResult.Success)
    assertEquals("small body", (result as FetchResult.Success).body)
  }

  // ── Network errors ─────────────────────────────────────────────────────

  @Test
  fun `fetchBoundedResult - IOException returns NetworkError`() {
    every { mockConnection.responseCode } throws IOException("Connection refused")
    val result = fetchBoundedResult("https://example.com/down", "TestAgent", openConnection = openConnection)
    assertTrue(result is FetchResult.NetworkError)
    assertTrue((result as FetchResult.NetworkError).message.contains("Connection refused"))
  }

  @Test
  fun `fetchBoundedResult - openConnection exception returns NetworkError`() {
    val throwingFactory: (String) -> HttpURLConnection = { throw IllegalArgumentException("bad url") }
    val result = fetchBoundedResult("not-a-url", "TestAgent", openConnection = throwingFactory)
    assertTrue(result is FetchResult.NetworkError)
    assertTrue((result as FetchResult.NetworkError).message.contains("Invalid URL"))
  }

  // ── Connection configuration ───────────────────────────────────────────

  @Test
  fun `fetchBoundedResult - sets User-Agent header`() {
    stubSuccessResponse("ok")
    fetchBoundedResult("https://example.com", "MyCustomAgent/1.0", openConnection = openConnection)
    verify { mockConnection.setRequestProperty("User-Agent", "MyCustomAgent/1.0") }
  }

  @Test
  fun `fetchBoundedResult - disables automatic redirect following`() {
    stubSuccessResponse("ok")
    fetchBoundedResult("https://example.com", "TestAgent", openConnection = openConnection)
    verify { mockConnection.instanceFollowRedirects = false }
  }

  @Test
  fun `fetchBoundedResult - sets connect and read timeouts`() {
    stubSuccessResponse("ok")
    fetchBoundedResult("https://example.com", "TestAgent", openConnection = openConnection)
    verify { mockConnection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS }
    verify { mockConnection.readTimeout = HTTP_READ_TIMEOUT_MS }
  }

  @Test
  fun `fetchBoundedResult - disconnects connection after success`() {
    stubSuccessResponse("ok")
    fetchBoundedResult("https://example.com", "TestAgent", openConnection = openConnection)
    verify { mockConnection.disconnect() }
  }

  @Test
  fun `fetchBoundedResult - disconnects connection after error`() {
    every { mockConnection.responseCode } returns 404
    fetchBoundedResult("https://example.com/missing", "TestAgent", openConnection = openConnection)
    verify { mockConnection.disconnect() }
  }
}
