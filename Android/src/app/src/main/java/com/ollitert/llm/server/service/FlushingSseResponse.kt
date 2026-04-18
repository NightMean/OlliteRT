package com.ollitert.llm.server.service

import android.util.Log
import com.ollitert.llm.server.data.SSE_BUFFER_SIZE_BYTES
import fi.iki.elonen.NanoHTTPD
import java.io.OutputStream

/**
 * A NanoHTTPD [NanoHTTPD.Response] that sends SSE data with an explicit flush
 * after every chunk read from the backing [BlockingQueueInputStream].
 *
 * NanoHTTPD 2.3.1's default chunked-response path only flushes the socket
 * once at the very end of [send], which means SSE events sit in buffers until
 * the stream closes — defeating the purpose of streaming. This subclass
 * overrides [send] to write chunked-transfer frames and flush the socket
 * after every chunk so clients see tokens as they arrive.
 *
 * When the client disconnects (write/flush throws), the stream is cancelled
 * so the inference thread can detect the disconnect and stop generating.
 */
class FlushingSseResponse(
  private val stream: BlockingQueueInputStream,
  private val extraHeaders: Map<String, String> = emptyMap(),
) : NanoHTTPD.Response(Status.OK, "text/event-stream", stream, -1) {

  override fun send(outputStream: OutputStream) {
    // ── Status line + headers ────────────────────────────────────────────
    // extraHeaders carries CORS + x-request-id headers that would otherwise be
    // lost because this override bypasses NanoHTTPD's header storage.
    val header = buildString {
      append("HTTP/1.1 200 OK\r\n")
      append("Content-Type: text/event-stream\r\n")
      append("Cache-Control: no-cache\r\n")
      append("Connection: keep-alive\r\n")
      append("Transfer-Encoding: chunked\r\n")
      for ((key, value) in extraHeaders) {
        append("$key: $value\r\n")
      }
      append("\r\n")
    }
    outputStream.write(header.toByteArray(Charsets.UTF_8))
    outputStream.flush()

    // ── Stream body with per-chunk flushing ──────────────────────────────
    val buf = ByteArray(SSE_BUFFER_SIZE_BYTES)
    try {
      while (true) {
        val n = stream.read(buf, 0, buf.size)
        if (n <= 0) break
        // Chunked-transfer frame: hex-length CRLF data CRLF
        outputStream.write("${Integer.toHexString(n)}\r\n".toByteArray(Charsets.UTF_8))
        outputStream.write(buf, 0, n)
        outputStream.write("\r\n".toByteArray(Charsets.UTF_8))
        outputStream.flush()
      }
      // Terminating chunk
      outputStream.write("0\r\n\r\n".toByteArray(Charsets.UTF_8))
      outputStream.flush()
    } catch (_: Exception) {
      // Client disconnected — signal the inference thread to stop
      stream.cancel()
    } finally {
      try { stream.close() } catch (e: Exception) { Log.w("FlushingSseResponse", "stream.close() failed", e) }
    }
  }
}
