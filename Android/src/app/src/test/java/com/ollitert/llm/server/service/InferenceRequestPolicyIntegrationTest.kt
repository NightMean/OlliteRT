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

import com.ollitert.llm.server.service.http.requireInferenceRequestForSettings
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

class InferenceRequestPolicyIntegrationTest {
  @Test
  fun enforcesContentTypeAndAnonymousBrowserOrigins() {
    val token = AtomicReference("")
    val origins = AtomicReference("*")
    val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
      install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
      }
      routing {
        post("/inference") {
          if (requireInferenceRequestForSettings(call, token.get(), origins.get(), true, false)) call.respondText("ok")
        }
        post("/v1/messages") {
          if (requireInferenceRequestForSettings(call, token.get(), origins.get(), true, true)) call.respondText("ok")
        }
        post("/audio") {
          if (requireInferenceRequestForSettings(call, token.get(), origins.get(), false, false)) call.respondText("ok")
        }
      }
    }.start(wait = false)
    try {
      val port = runBlocking { server.engine.resolvedConnectors().first().port }
      fun request(method: String, path: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        Socket("127.0.0.1", port).use { socket ->
          socket.soTimeout = 5_000
          val request = buildString {
            append("$method $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\n")
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Content-Length: ${body.toByteArray().size}\r\n\r\n$body")
          }
          socket.getOutputStream().write(request.toByteArray())
          socket.getOutputStream().flush()
          val reader = socket.getInputStream().bufferedReader()
          val status = reader.readLine().split(' ')[1].toInt()
          while (!reader.readLine().isNullOrEmpty()) { }
          return status to reader.readText()
        }
      }
      fun send(path: String = "/inference", type: String = "application/json", origin: String? = null, auth: String? = null): Pair<Int, String> {
        val headers = mutableMapOf("Content-Type" to type)
        if (origin != null) headers["Origin"] = origin
        if (auth != null) headers["Authorization"] = auth
        return request("POST", path, headers, "{}")
      }
      assertEquals(200, send().first)
      assertEquals(415, send(type = "text/plain").first)
      val preflight = request("OPTIONS", "/inference", mapOf(
        "Origin" to "https://evil.example",
        "Access-Control-Request-Method" to "POST",
        "Access-Control-Request-Headers" to "content-type",
      ), "")
      assertTrue(preflight.first in 200..299)
      assertEquals(403, send(origin = "https://evil.example").first)
      assertEquals(200, send(origin = "http://127.0.0.1:$port").first)
      assertEquals(403, send(path = "/audio", type = "multipart/form-data; boundary=x", origin = "https://evil.example").first)
      val anthropic = send(path = "/v1/messages", type = "text/plain")
      assertEquals(415, anthropic.first)
      assertTrue(anthropic.second.contains("invalid_request_error"))
      origins.set("https://trusted.example")
      assertEquals(200, send(origin = "https://trusted.example").first)
      assertEquals(403, send(origin = "https://evil.example").first)
      token.set("secret-token")
      assertEquals(401, send(origin = "https://evil.example").first)
      assertEquals(200, send(origin = "https://evil.example", auth = "Bearer secret-token").first)
    } finally {
      server.stop(100, 1000)
    }
  }
}
