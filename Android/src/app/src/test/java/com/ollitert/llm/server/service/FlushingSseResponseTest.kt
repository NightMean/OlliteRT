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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class FlushingSseResponseTest {

  /** Calls the protected send() via reflection. */
  private fun sendToBytes(response: FlushingSseResponse): String {
    val output = ByteArrayOutputStream()
    val sendMethod = response.javaClass.getDeclaredMethod("send", java.io.OutputStream::class.java)
    sendMethod.isAccessible = true
    sendMethod.invoke(response, output)
    return output.toString(Charsets.UTF_8.name())
  }

  @Test
  fun sendIncludesExtraHeadersInRawOutput() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("data: hello\n\n")
    stream.finish()

    val extraHeaders = mapOf(
      "Access-Control-Allow-Origin" to "*",
      "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
      "x-request-id" to "log-123",
    )
    val response = FlushingSseResponse(stream, extraHeaders)
    val raw = sendToBytes(response)

    assertTrue(raw.contains("Access-Control-Allow-Origin: *\r\n"))
    assertTrue(raw.contains("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"))
    assertTrue(raw.contains("x-request-id: log-123\r\n"))
  }

  @Test
  fun sendWithoutExtraHeadersOmitsCorsLines() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("data: hello\n\n")
    stream.finish()

    val response = FlushingSseResponse(stream)
    val raw = sendToBytes(response)

    assertFalse(raw.contains("Access-Control-Allow-Origin"))
    assertTrue(raw.contains("Content-Type: text/event-stream\r\n"))
    assertTrue(raw.contains("Transfer-Encoding: chunked\r\n"))
  }

  @Test
  fun sendPreservesStandardHeaders() {
    val stream = BlockingQueueInputStream()
    stream.finish()

    val response = FlushingSseResponse(stream, mapOf("X-Custom" to "value"))
    val raw = sendToBytes(response)

    assertTrue(raw.startsWith("HTTP/1.1 200 OK\r\n"))
    assertTrue(raw.contains("Content-Type: text/event-stream\r\n"))
    assertTrue(raw.contains("Cache-Control: no-cache\r\n"))
    assertTrue(raw.contains("Connection: keep-alive\r\n"))
    assertTrue(raw.contains("Transfer-Encoding: chunked\r\n"))
    assertTrue(raw.contains("X-Custom: value\r\n"))
  }

  @Test
  fun onlyRequestIdWhenCorsDisabled() {
    val stream = BlockingQueueInputStream()
    stream.finish()

    val response = FlushingSseResponse(stream, mapOf("x-request-id" to "log-456"))
    val raw = sendToBytes(response)

    assertTrue(raw.contains("x-request-id: log-456\r\n"))
    assertFalse(raw.contains("Access-Control-Allow-Origin"))
  }

  @Test
  fun bodyDataAppearsInChunkedFormat() {
    val stream = BlockingQueueInputStream()
    stream.enqueue("data: test\n\n")
    stream.finish()

    val response = FlushingSseResponse(stream)
    val raw = sendToBytes(response)

    assertTrue(raw.contains("data: test"))
    assertTrue(raw.endsWith("0\r\n\r\n"))
  }
}
