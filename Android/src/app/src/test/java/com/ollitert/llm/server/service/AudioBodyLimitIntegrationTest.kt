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

import com.ollitert.llm.server.service.routes.installAudioBodyLimit
import com.ollitert.llm.server.service.routes.readAudioMultipart
import com.ollitert.llm.server.service.routes.AudioMultipartLimitException
import com.ollitert.llm.server.service.http.RequestBodyAdmission
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.Socket

class AudioBodyLimitIntegrationTest {
  @Test
  fun limitsDeclaredAndChunkedMultipartBodies() {
    val admission = RequestBodyAdmission(1)
    val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
      install(StatusPages) {
        exception<PayloadTooLargeException> { call, _ ->
          call.respondText("too large", status = HttpStatusCode.PayloadTooLarge)
        }
        exception<AudioMultipartLimitException> { call, _ ->
          call.respondText("too many parts", status = HttpStatusCode.PayloadTooLarge)
        }
      }
      routing {
        route("/upload") {
          installAudioBodyLimit(100_000)
          post {
            val lease = admission.tryAcquire()
            if (lease == null) {
              call.respondText("busy", status = HttpStatusCode.ServiceUnavailable)
              return@post
            }
            try {
              val received = withTimeoutOrNull(800) {
                readAudioMultipart(call)
                true
              } ?: false
              if (received) call.respondText("ok")
              else call.respondText("timeout", status = HttpStatusCode.RequestTimeout)
            } finally { lease.close() }
          }
        }
      }
    }.start(wait = false)
    try {
      val port = runBlocking { server.engine.resolvedConnectors().first().port }
      fun send(size: Int, chunked: Boolean): Int {
        val prefix = "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\nContent-Type: audio/wav\r\n\r\n"
        val bytes = (prefix + "x".repeat(size) + "\r\n--boundary--\r\n").toByteArray()
        val connection = URL("http://127.0.0.1:$port/upload").openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=boundary")
        if (chunked) connection.setChunkedStreamingMode(128) else connection.setFixedLengthStreamingMode(bytes.size)
        return try {
          connection.outputStream.use { it.write(bytes) }
          connection.responseCode
        } finally { connection.disconnect() }
      }
      assertEquals(200, send(100, false))
      assertEquals(200, send(100, true))
      assertEquals(413, send(120_000, false))
      assertEquals(413, send(120_000, true))
      for (disposition in listOf("name=\"prompt\"", "name=\"file\"; filename=\"a.wav\"")) {
        Socket("127.0.0.1", port).use { socket ->
          socket.soTimeout = 5_000
          val partial = "--boundary\r\nContent-Disposition: form-data; $disposition\r\n\r\nunfinished"
          val request = "POST /upload HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n" +
            "Content-Type: multipart/form-data; boundary=boundary\r\nTransfer-Encoding: chunked\r\n\r\n" +
            "${partial.toByteArray().size.toString(16)}\r\n$partial\r\n"
          socket.getOutputStream().write(request.toByteArray())
          socket.getOutputStream().flush()
          val status = socket.getInputStream().bufferedReader().readLine()
          org.junit.Assert.assertTrue("Expected timeout, got $status", status.contains("408"))
          assertEquals(200, send(100, false))
        }
      }
      val duplicate = "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\n\r\nx\r\n" +
        "--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"b.wav\"\r\n\r\n" + "x".repeat(5000)
      Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 5_000
        val request = "POST /upload HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n" +
          "Content-Type: multipart/form-data; boundary=boundary\r\nTransfer-Encoding: chunked\r\n\r\n" +
          "${duplicate.toByteArray().size.toString(16)}\r\n$duplicate\r\n"
        socket.getOutputStream().write(request.toByteArray())
        socket.getOutputStream().flush()
        val status = socket.getInputStream().bufferedReader().readLine()
        org.junit.Assert.assertTrue("Expected timeout or rejection, got $status", status.contains("408") || status.contains("413"))
        assertEquals(200, send(100, false))
      }
      val completeDuplicate = (duplicate + "\r\n--boundary--\r\n").toByteArray()
      val connection = URL("http://127.0.0.1:$port/upload").openConnection() as HttpURLConnection
      connection.connectTimeout = 5_000
      connection.readTimeout = 5_000
      connection.requestMethod = "POST"
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=boundary")
      connection.setFixedLengthStreamingMode(completeDuplicate.size)
      try {
        connection.outputStream.use { it.write(completeDuplicate) }
        assertEquals(413, connection.responseCode)
      } finally { connection.disconnect() }
      fun sendFields(body: String): Int {
        val bytes = (body + "--boundary--\r\n").toByteArray()
        val client = URL("http://127.0.0.1:$port/upload").openConnection() as HttpURLConnection
        client.connectTimeout = 5_000
        client.readTimeout = 5_000
        client.requestMethod = "POST"
        client.doOutput = true
        client.setRequestProperty("Content-Type", "multipart/form-data; boundary=boundary")
        client.setFixedLengthStreamingMode(bytes.size)
        return try {
          client.outputStream.use { it.write(bytes) }
          client.responseCode
        } finally { client.disconnect() }
      }
      val prefix = "--boundary\r\nContent-Disposition: form-data; name=\"prompt\"\r\n\r\n"
      assertEquals(413, sendFields((prefix + "x\r\n").repeat(33)))
      assertEquals(413, sendFields(prefix + "x".repeat(65_537) + "\r\n"))
    } finally {
      server.stop(100, 1000)
    }
  }
}
