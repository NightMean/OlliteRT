package com.ollitert.llm.server.service

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
) : NanoHTTPD.Response(Status.OK, "text/event-stream", stream, -1) {

  override fun send(outputStream: OutputStream) {
    // ── Status line + headers ────────────────────────────────────────────
    val header = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: text/event-stream\r\n" +
      "Cache-Control: no-cache\r\n" +
      "Connection: keep-alive\r\n" +
      "Transfer-Encoding: chunked\r\n" +
      "\r\n"
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
      try { stream.close() } catch (_: Exception) {}
    }
  }
}
